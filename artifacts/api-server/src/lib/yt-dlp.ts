import { execFile, spawn } from "node:child_process";
import { promisify } from "node:util";
import { mkdir, readdir, rename, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { logger } from "./logger";
import { broadcastProgress } from "./sse";
import { persistHistory } from "./history";
import { errorMessage, sanitizeFilename, formatSizeLabel } from "./platform";
import type { DownloadJob, FormatKind } from "./types";

const execFileAsync = promisify(execFile);

type YtDlpMetadata = {
  title?: string;
  thumbnail?: string;
  duration?: number;
  formats?: Array<{
    format_id: string;
    ext: string;
    acodec: string;
    vcodec: string;
    filesize?: number;
    height?: number;
    width?: number;
    tbr?: number;
    format_note?: string;
  }>;
};

export async function inspectWithYtDlp(url: string, cookies?: string): Promise<YtDlpMetadata> {
  const args = ["--dump-single-json", "--skip-download", "--no-playlist", "--no-warnings"];
  if (cookies) {
    const cookiesPath = path.join(os.tmpdir(), "social-downloader", "ig-cookies.txt");
    await mkdir(path.dirname(cookiesPath), { recursive: true });
    await writeFile(cookiesPath, cookies, "utf-8");
    args.push("--cookies", cookiesPath);
  }
  const result = await execFileAsync(
    "yt-dlp",
    [...args, url],
    { timeout: 25_000, maxBuffer: 4 * 1024 * 1024 },
  );
  return JSON.parse(result.stdout) as YtDlpMetadata;
}

export function buildFormatsFromMetadata(formats: YtDlpMetadata["formats"]) {
  if (!formats || formats.length === 0) {
    return [
      { id: "bestaudio", label: "Audio terbaik (WebM)", extension: "webm", kind: "audio" as FormatKind, sizeLabel: null },
      { id: "bestvideo+bestaudio/best", label: "Video 720p + Audio (MP4)", extension: "mp4", kind: "premium" as FormatKind, sizeLabel: null },
    ];
  }

  const result: Array<{ id: string; label: string; extension: string; kind: FormatKind; sizeLabel: string | null }> = [];

  const facebookFormats = formats.filter((f) => (f.format_id === "sd" || f.format_id === "hd") && f.ext === "mp4");
  if (facebookFormats.length > 0) {
    for (const ff of facebookFormats) {
      const label = ff.format_id === "hd" ? "Video HD (MP4)" : "Video SD (MP4)";
      result.push({ id: ff.format_id, label, extension: "mp4", kind: "premium", sizeLabel: formatSizeLabel(ff.filesize) });
    }
    return result;
  }

  const audioFormats = formats.filter((f) => f.vcodec === "none" && f.acodec !== "none" && f.ext !== "m3u8");
  const videoFormats = formats.filter((f) => f.vcodec !== "none" && f.acodec === "none" && f.ext !== "m3u8");

  const m4aBest = audioFormats.filter((f) => f.ext === "m4a").sort((a, b) => (b.tbr ?? 0) - (a.tbr ?? 0))[0];
  const webmBest = audioFormats.filter((f) => f.ext === "webm").sort((a, b) => (b.tbr ?? 0) - (a.tbr ?? 0))[0];

  if (m4aBest) {
    result.push({ id: m4aBest.format_id, label: "Audio (AAC/M4A)", extension: "m4a", kind: "audio", sizeLabel: formatSizeLabel(m4aBest.filesize) });
  }
  if (webmBest) {
    result.push({ id: webmBest.format_id, label: "Audio (Opus/WebM)", extension: "webm", kind: "audio", sizeLabel: formatSizeLabel(webmBest.filesize) });
  }

  const mp4Video = videoFormats.filter((f) => f.ext === "mp4" && f.height).sort((a, b) => (a.height ?? 0) - (b.height ?? 0));
  const seenResolutions = new Set<string>();
  for (const vf of mp4Video) {
    const res = `${vf.height}p`;
    if (seenResolutions.has(res)) continue;
    seenResolutions.add(res);
    result.push({ id: vf.format_id, label: `Video ${res} (MP4)`, extension: "mp4", kind: "video", sizeLabel: formatSizeLabel(vf.filesize) });
  }

  if (result.filter((r) => r.kind === "video").length === 0) {
    const webmVideo = videoFormats.filter((f) => f.ext === "webm" && f.height).sort((a, b) => (a.height ?? 0) - (b.height ?? 0));
    for (const vf of webmVideo) {
      const res = `${vf.height}p`;
      if (seenResolutions.has(res)) continue;
      seenResolutions.add(res);
      result.push({ id: vf.format_id, label: `Video ${res} (WebM)`, extension: "webm", kind: "video", sizeLabel: formatSizeLabel(vf.filesize) });
    }
  }

  const premiumResolutions = [144, 240, 360, 480, 720, 1080];
  const availableHeights = new Set(videoFormats.filter((f) => f.height).map((f) => f.height!));

  for (const h of premiumResolutions) {
    const formatStr = `bestvideo[height<=${h}]+bestaudio/best[height<=${h}]/best`;
    const label = `Video ${h}p + Audio (MP4)`;
    const hasAtLeastH = [...availableHeights].some((ah) => ah >= h);
    if (hasAtLeastH || h === 144) {
      result.push({ id: formatStr, label, extension: "mp4", kind: "premium", sizeLabel: null });
    }
  }

  if (result.length === 0) {
    result.push(
      { id: "bestaudio", label: "Audio terbaik (WebM)", extension: "webm", kind: "audio", sizeLabel: null },
      { id: "bestvideo+bestaudio/best", label: "Video 720p + Audio (MP4)", extension: "mp4", kind: "premium", sizeLabel: null },
    );
  }

  return result;
}

export function startProcess(job: DownloadJob, formatId: string) {
  const outputTemplate = path.join(job.directory, `${job.id}.%(ext)s`);
  const isPremium = formatId.includes("+");
  const args = [
    "--no-playlist",
    "--newline",
    "--progress",
    "--no-warnings",
    "--restrict-filenames",
    "-f",
    formatId,
    "-o",
    outputTemplate,
  ];

  if (isPremium) {
    args.push("--merge-output-format", "mp4");
  }

  const ffmpegDir = process.env.FFMPEG_DIR;
  if (ffmpegDir) {
    args.push("--ffmpeg-location", ffmpegDir);
  }

  const cookiesFile = path.join(os.tmpdir(), "social-downloader", "ig-cookies.txt");
  try {
    const { existsSync } = require("fs");
    if (existsSync(cookiesFile)) {
      args.push("--cookies", cookiesFile);
    }
  } catch (error) {
    logger.warn({ err: error }, "Failed to check cookies file for yt-dlp");
  }

  args.push(job.url);

  const child = spawn("yt-dlp", args, { stdio: ["ignore", "pipe", "pipe"] });
  job.process = child;
  job.status = "downloading";

  let stderr = "";
  child.stderr.on("data", (chunk: Buffer) => {
    stderr += chunk.toString();
  });
  child.stdout.on("data", (chunk: Buffer) => {
    const text = chunk.toString();
    const percent = text.match(/(\d+(?:\.\d+)?)%/);
    if (percent) {
      job.progress = Math.min(99, Math.max(job.progress, Math.round(Number(percent[1]))));
    }
    const title = text.match(/\[download\] Destination: .+\/([^/]+)$/m);
    if (title?.[1] && !job.filename) {
      job.filename = title[1].trim();
    }
  });

  child.on("error", (error) => {
    job.status = "failed";
    job.error = errorMessage(error);
    job.process = undefined;
  });

  child.on("close", (code) => {
    void finishProcess(job, code ?? 1, stderr);
  });
}

async function finishProcess(job: DownloadJob, code: number, stderr: string) {
  job.process = undefined;
  if (code !== 0) {
    job.status = "failed";
    job.error = errorMessage({ stderr });
    return;
  }

  const files = (await readdir(job.directory)).filter((file) => !file.endsWith(".part"));
  const outputFile = files.find((file) => file.startsWith(`${job.id}.`));
  if (!outputFile) {
    job.status = "failed";
    job.error = "File hasil unduhan tidak ditemukan.";
    return;
  }

  const originalPath = path.join(job.directory, outputFile);
  job.filePath = originalPath;

  const title = job.title ? sanitizeFilename(job.title) : null;
  const ext = path.extname(outputFile);
  const desiredFilename = title ? `${title}${ext}` : outputFile;
  const desiredPath = path.join(job.directory, desiredFilename);
  if (title && originalPath !== desiredPath) {
    try {
      await rename(originalPath, desiredPath);
      job.filename = desiredFilename;
      job.filePath = desiredPath;
    } catch {
      job.filename = outputFile;
    }
  } else {
    job.filename = outputFile;
  }
  job.status = "completed";
  job.progress = 100;
  job.downloadUrl = `/api/downloads/${job.id}/file`;
  broadcastProgress(job);
  void persistHistory();
}
