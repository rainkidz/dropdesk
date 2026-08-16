import { Router, type IRouter } from "express";
import {
  CreateDownloadBody,
  CreateDownloadResponse,
  DownloadFileParams,
  GetDownloadParams,
  GetDownloadResponse,
  InspectDownloadBody,
  InspectDownloadResponse,
  ListRecentDownloadsResponse,
} from "@workspace/api-zod";
import { randomUUID } from "node:crypto";
import { execFile, spawn, type ChildProcess } from "node:child_process";
import { promisify } from "node:util";
import { createReadStream } from "node:fs";
import { mkdir, readdir, rm, stat } from "node:fs/promises";
import os from "node:os";
import path from "node:path";

const router: IRouter = Router();
const execFileAsync = promisify(execFile);
const jobs = new Map<string, DownloadJob>();
const downloadsRoot = path.join(os.tmpdir(), "social-downloader");
const maxRecentJobs = 50;
const supportedFormatIds = new Set(["best", "bestvideo+bestaudio", "bestaudio", "worst"]);

type DownloadPlatform = "youtube" | "instagram" | "threads" | "tiktok" | "unknown";
type DownloadStatus = "queued" | "downloading" | "completed" | "failed";

type DownloadJob = {
  id: string;
  url: string;
  platform: DownloadPlatform;
  title: string | null;
  status: DownloadStatus;
  progress: number;
  mediaType: "video" | "audio";
  filename: string | null;
  createdAt: string;
  downloadUrl: string | null;
  error: string | null;
  filePath: string | null;
  directory: string;
  process?: ChildProcess;
};

function platformForUrl(rawUrl: string): DownloadPlatform {
  let parsed: URL;
  try {
    parsed = new URL(rawUrl);
  } catch {
    return "unknown";
  }

  if (!["http:", "https:"].includes(parsed.protocol)) {
    return "unknown";
  }

  const host = parsed.hostname.toLowerCase().replace(/^www\./, "");
  if (host === "youtube.com" || host === "m.youtube.com" || host === "youtu.be") {
    return "youtube";
  }
  if (host === "instagram.com" || host.endsWith(".instagram.com")) {
    return "instagram";
  }
  if (host === "threads.net" || host.endsWith(".threads.net")) {
    return "threads";
  }
  if (host === "tiktok.com" || host.endsWith(".tiktok.com")) {
    return "tiktok";
  }
  return "unknown";
}

function errorMessage(error: unknown): string {
  if (error && typeof error === "object" && "stderr" in error && typeof error.stderr === "string") {
    return error.stderr.trim().split("\n").filter(Boolean).at(-1) ?? "Media tidak dapat diproses.";
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Media tidak dapat diproses.";
}

function publicJob(job: DownloadJob) {
  return {
    id: job.id,
    url: job.url,
    platform: job.platform,
    title: job.title,
    status: job.status,
    progress: job.progress,
    mediaType: job.mediaType,
    filename: job.filename,
    createdAt: job.createdAt,
    downloadUrl: job.downloadUrl,
    error: job.error,
  };
}

function rememberJob(job: DownloadJob) {
  jobs.set(job.id, job);
  const entries = [...jobs.values()].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  for (const oldJob of entries.slice(maxRecentJobs)) {
    jobs.delete(oldJob.id);
    void rm(oldJob.directory, { recursive: true, force: true });
  }
}

async function inspectWithYtDlp(url: string) {
  const result = await execFileAsync(
    "yt-dlp",
    ["--dump-single-json", "--skip-download", "--no-playlist", "--no-warnings", url],
    { timeout: 25_000, maxBuffer: 4 * 1024 * 1024 },
  );
  return JSON.parse(result.stdout) as {
    title?: string;
    thumbnail?: string;
    duration?: number;
  };
}

function startProcess(job: DownloadJob, formatId: string) {
  const outputTemplate = path.join(job.directory, `${job.id}.%(ext)s`);
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

  if (job.mediaType === "audio") {
    args.push("-x", "--audio-format", "mp3");
  } else {
    args.push("--merge-output-format", "mp4");
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

  job.filename = outputFile;
  job.filePath = path.join(job.directory, outputFile);
  job.status = "completed";
  job.progress = 100;
  job.downloadUrl = `/api/downloads/${job.id}/file`;
}

router.post("/downloads/inspect", async (req, res) => {
  const parsed = InspectDownloadBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: "Masukkan URL yang valid." });
    return;
  }

  const { url } = parsed.data;
  const platform = platformForUrl(url);
  if (platform === "unknown") {
    res.status(400).json({ error: "URL harus berasal dari YouTube, Instagram, Threads, atau TikTok." });
    return;
  }

  try {
    const metadata = await inspectWithYtDlp(url);
    const data = InspectDownloadResponse.parse({
      url,
      platform,
      isSupported: true,
      title: metadata.title ?? null,
      thumbnailUrl: metadata.thumbnail ?? null,
      durationSeconds: metadata.duration ?? null,
      formats: [
        { id: "best", label: "Kualitas terbaik", extension: "mp4", kind: "video", sizeLabel: null },
        { id: "bestvideo+bestaudio", label: "Video + audio terbaik", extension: "mp4", kind: "video", sizeLabel: null },
        { id: "bestaudio", label: "Audio saja", extension: "mp3", kind: "audio", sizeLabel: null },
      ],
    });
    res.json(data);
  } catch (error) {
    req.log.warn({ err: error, url, platform }, "Could not inspect media URL");
    res.status(502).json({ error: "Media tidak bisa dibaca. Pastikan URL publik dan tidak dibatasi akun." });
  }
});

router.post("/downloads", async (req, res) => {
  const parsed = CreateDownloadBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: "Data unduhan tidak lengkap." });
    return;
  }

  const { url, formatId, mediaType = "video" } = parsed.data;
  const platform = platformForUrl(url);
  if (platform === "unknown" || (!supportedFormatIds.has(formatId) && !/^\d+$/.test(formatId))) {
    res.status(400).json({ error: "URL atau format unduhan tidak didukung." });
    return;
  }

  const activeCount = [...jobs.values()].filter((job) => job.status === "queued" || job.status === "downloading").length;
  if (activeCount >= 3) {
    res.status(429).json({ error: "Tiga unduhan sedang berjalan. Coba lagi sebentar." });
    return;
  }

  const id = randomUUID();
  const directory = path.join(downloadsRoot, id);
  await mkdir(directory, { recursive: true });
  const job: DownloadJob = {
    id,
    url,
    platform,
    title: null,
    status: "queued",
    progress: 0,
    mediaType,
    filename: null,
    createdAt: new Date().toISOString(),
    downloadUrl: null,
    error: null,
    filePath: null,
    directory,
  };
  rememberJob(job);
  startProcess(job, formatId);
  res.status(202).json(CreateDownloadResponse.parse(publicJob(job)));
});

router.get("/downloads/recent", (_req, res) => {
  const recent = [...jobs.values()]
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .slice(0, 12)
    .map(publicJob);
  res.json(ListRecentDownloadsResponse.parse(recent));
});

router.get("/downloads/:id", (req, res) => {
  const parsed = GetDownloadParams.safeParse(req.params);
  if (!parsed.success) {
    res.status(404).json({ error: "Unduhan tidak ditemukan." });
    return;
  }
  const job = jobs.get(parsed.data.id);
  if (!job) {
    res.status(404).json({ error: "Unduhan tidak ditemukan." });
    return;
  }
  res.json(GetDownloadResponse.parse(publicJob(job)));
});

router.get("/downloads/:id/file", async (req, res) => {
  const parsed = DownloadFileParams.safeParse(req.params);
  if (!parsed.success) {
    res.status(404).json({ error: "File tidak ditemukan." });
    return;
  }
  const job = jobs.get(parsed.data.id);
  if (!job?.filePath || job.status !== "completed" || !job.filename) {
    res.status(404).json({ error: "File belum siap diunduh." });
    return;
  }

  try {
    await stat(job.filePath);
    res.setHeader("Content-Disposition", `attachment; filename="${job.filename.replace(/["\\]/g, "")}"`);
    createReadStream(job.filePath).pipe(res);
  } catch (error) {
    req.log.warn({ err: error, id: parsed.data.id }, "Could not serve downloaded file");
    res.status(404).json({ error: "File tidak ditemukan." });
  }
});

export default router;