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
import { mkdir, readdir, rename, rm, stat } from "node:fs/promises";
import os from "node:os";
import path from "node:path";

const router: IRouter = Router();
const execFileAsync = promisify(execFile);
const jobs = new Map<string, DownloadJob>();
const downloadsRoot = path.join(os.tmpdir(), "social-downloader");
const maxRecentJobs = 50;
const igCookiesPath = path.join(os.tmpdir(), "social-downloader", "instagram-cookies.txt");
const historyPath = path.join(os.tmpdir(), "social-downloader", "download-history.json");

// ── Rate limiting ──
const MAX_CONCURRENT = 3;
const rateLimitWindow = new Map<string, number[]>();
const RATE_LIMIT_MAX = 10; // max 10 inspect requests per minute
const RATE_LIMIT_MS = 60_000;

function checkRateLimit(ip: string): boolean {
  const now = Date.now();
  const timestamps = rateLimitWindow.get(ip) ?? [];
  const recent = timestamps.filter((t) => now - t < RATE_LIMIT_MS);
  if (recent.length >= RATE_LIMIT_MAX) return false;
  recent.push(now);
  rateLimitWindow.set(ip, recent);
  return true;
}

// ── Inspect cache ──
const inspectCache = new Map<string, { data: unknown; expiresAt: number }>();
const CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

function getCachedInspect(key: string): unknown | null {
  const entry = inspectCache.get(key);
  if (!entry || Date.now() > entry.expiresAt) {
    inspectCache.delete(key);
    return null;
  }
  return entry.data;
}

function setCachedInspect(key: string, data: unknown): void {
  inspectCache.set(key, { data, expiresAt: Date.now() + CACHE_TTL_MS });
  // Cleanup old entries
  if (inspectCache.size > 100) {
    const now = Date.now();
    for (const [k, v] of inspectCache) {
      if (now > v.expiresAt) inspectCache.delete(k);
    }
  }
}

// ── SSE clients for progress updates ──
const sseClients = new Set<import("node:http").ServerResponse>();

function broadcastProgress(job: DownloadJob) {
  const data = JSON.stringify(publicJob(job));
  for (const client of sseClients) {
    client.write(`data: ${data}\n\n`);
  }
}

// ── Persist history ──
async function persistHistory(): Promise<void> {
  try {
    const entries = [...jobs.values()]
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .slice(0, maxRecentJobs)
      .map(publicJob);
    await mkdir(path.dirname(historyPath), { recursive: true });
    const { writeFile } = await import("node:fs/promises");
    await writeFile(historyPath, JSON.stringify(entries, null, 2), "utf-8");
  } catch {}
}

async function loadHistory(): Promise<void> {
  try {
    const { readFile } = await import("node:fs/promises");
    const raw = await readFile(historyPath, "utf-8");
    const entries = JSON.parse(raw) as Array<ReturnType<typeof publicJob>>;
    for (const entry of entries) {
      if (!jobs.has(entry.id)) {
        jobs.set(entry.id, {
          ...entry,
          progress: entry.status === "completed" ? 100 : 0,
          filePath: null,
          directory: path.join(downloadsRoot, entry.id),
        } as DownloadJob);
      }
    }
  } catch {}
}

let igCookies: string | null = null;

async function loadIgCookies(): Promise<string | null> {
  if (igCookies !== null) return igCookies;
  try {
    const { readFile } = await import("node:fs/promises");
    igCookies = (await readFile(igCookiesPath, "utf-8")).trim();
    return igCookies;
  } catch {
    return null;
  }
}

async function saveIgCookies(cookies: string): Promise<void> {
  const { writeFile } = await import("node:fs/promises");
  await mkdir(path.dirname(igCookiesPath), { recursive: true });
  await writeFile(igCookiesPath, cookies, "utf-8");
  igCookies = cookies.trim();
}
const supportedFormatIds = new Set(["best", "bestvideo+bestaudio", "bestaudio", "worst", "m4a", "webm-audio", "mp4-360p", "mp4-480p", "mp4-720p", "mp4-1080p"]);

type DownloadPlatform = "youtube" | "instagram" | "threads" | "tiktok" | "facebook" | "unknown";
type DownloadStatus = "queued" | "downloading" | "completed" | "failed";

type DownloadJob = {
  id: string;
  url: string;
  platform: DownloadPlatform;
  title: string | null;
  status: DownloadStatus;
  progress: number;
  mediaType: "video" | "audio" | "premium";
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
  if (host === "facebook.com" || host === "fb.com" || host === "fb.watch" || host.endsWith(".facebook.com")) {
    return "facebook";
  }
  return "unknown";
}

function normalizeSocialUrl(rawUrl: string) {
  const decoded = rawUrl
    .replace(/&amp;/gi, "&")
    .replace(/&#0*38;/gi, "&")
    .trim();
  const parsed = new URL(decoded);
  const host = parsed.hostname.toLowerCase().replace(/^www\./, "");

  if (host === "youtube.com" || host === "m.youtube.com" || host === "youtu.be") {
    const videoId = host === "youtu.be"
      ? parsed.pathname.split("/").filter(Boolean)[0]
      : parsed.searchParams.get("v");
    if (videoId) {
      return `https://www.youtube.com/watch?v=${encodeURIComponent(videoId)}`;
    }
  }

  return parsed.toString();
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

function userFacingInspectionError(error: unknown) {
  const message = errorMessage(error).toLowerCase();
  if (message.includes("sign in to confirm") || message.includes("not a bot") || message.includes("not available on this app")) {
    return "YouTube memblokir permintaan otomatis dari server ini. Link Anda valid, tetapi YouTube meminta sesi pengguna untuk mengambil file.";
  }
  if (message.includes("private video") || message.includes("members-only")) {
    return "Video ini bersifat privat atau khusus anggota, jadi tidak bisa diambil dari link publik.";
  }
  if (message.includes("video unavailable")) {
    return "Video tidak tersedia atau dibatasi di wilayah server downloader.";
  }
  return "Media tidak bisa dibaca. Pastikan URL publik dan tidak dibatasi akun.";
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
  broadcastProgress(job);
  const entries = [...jobs.values()].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  for (const oldJob of entries.slice(maxRecentJobs)) {
    jobs.delete(oldJob.id);
    void rm(oldJob.directory, { recursive: true, force: true });
  }
  // Persist every 5 jobs
  if (jobs.size % 5 === 0) void persistHistory();
}

async function inspectWithYtDlp(url: string, cookies?: string) {
  const args = ["--dump-single-json", "--skip-download", "--no-playlist", "--no-warnings"];
  if (cookies) {
    const cookiesPath = path.join(os.tmpdir(), "social-downloader", "ig-cookies.txt");
    await mkdir(path.dirname(cookiesPath), { recursive: true });
    const { writeFile } = await import("node:fs/promises");
    await writeFile(cookiesPath, cookies, "utf-8");
    args.push("--cookies", cookiesPath);
  }
  const result = await execFileAsync(
    "yt-dlp",
    [...args, url],
    { timeout: 25_000, maxBuffer: 4 * 1024 * 1024 },
  );
  return JSON.parse(result.stdout) as {
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
}

async function inspectWithTikwm(url: string) {
  const formData = new URLSearchParams();
  formData.append("url", url);
  const resp = await fetch("https://www.tikwm.com/api/", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: formData.toString(),
    signal: AbortSignal.timeout(15_000),
  });
  const data = await resp.json() as { code: number; data?: { title: string; author: { unique_id: string }; duration: number; play: string; hdplay: string; music: string; cover: string; size: number; hd_size: number | null; width: number; height: number; } };
  if (data.code !== 0 || !data.data) {
    throw new Error("TikTok media tidak dapat diproses.");
  }
  return data.data;
}

async function downloadTiktokFile(url: string, destPath: string) {
  const resp = await fetch(url, { signal: AbortSignal.timeout(120_000) });
  if (!resp.ok) throw new Error(`Download failed: ${resp.status}`);
  const { createWriteStream } = await import("node:fs");
  const { pipeline } = await import("node:stream/promises");
  const fileStream = createWriteStream(destPath);
  if (resp.body) {
    const reader = resp.body.getReader();
    let totalBytes = 0;
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      fileStream.write(value);
      totalBytes += value.length;
    }
    fileStream.end();
    await new Promise<void>((resolve, reject) => {
      fileStream.on("finish", resolve);
      fileStream.on("error", reject);
    });
    return totalBytes;
  }
  throw new Error("No response body");
}

function formatSizeLabel(bytes: number | undefined): string | null {
  if (!bytes) return null;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function sanitizeFilename(name: string): string {
  return name
    .replace(/[\\/:*?"<>|]/g, "")
    .replace(/\s+/g, "_")
    .replace(/_{2,}/g, "_")
    .slice(0, 120)
    .replace(/_+$/, "");
}

type FormatKind = "video" | "audio" | "premium";

function buildFormatsFromMetadata(formats: Array<{
  format_id: string;
  ext: string;
  acodec: string;
  vcodec: string;
  filesize?: number;
  height?: number;
  width?: number;
  tbr?: number;
  format_note?: string;
}> | undefined) {
  if (!formats || formats.length === 0) {
    return [
      { id: "bestaudio", label: "Audio terbaik (WebM)", extension: "webm", kind: "audio" as FormatKind, sizeLabel: null },
      { id: "bestvideo+bestaudio/best", label: "Video 720p + Audio (MP4)", extension: "mp4", kind: "premium" as FormatKind, sizeLabel: null },
    ];
  }

  const result: Array<{ id: string; label: string; extension: string; kind: FormatKind; sizeLabel: string | null }> = [];

  // Facebook returns sd/hd format IDs with no codec info — treat them as merged formats
  const facebookFormats = formats.filter((f) => (f.format_id === "sd" || f.format_id === "hd") && f.ext === "mp4");
  if (facebookFormats.length > 0) {
    for (const ff of facebookFormats) {
      const label = ff.format_id === "hd" ? "Video HD (MP4)" : "Video SD (MP4)";
      result.push({
        id: ff.format_id,
        label,
        extension: "mp4",
        kind: "premium",
        sizeLabel: formatSizeLabel(ff.filesize),
      });
    }
    return result;
  }

  const audioFormats = formats.filter((f) => f.vcodec === "none" && f.acodec !== "none" && f.ext !== "m3u8");
  const videoFormats = formats.filter((f) => f.vcodec !== "none" && f.acodec === "none" && f.ext !== "m3u8");
  const mergedFormats = formats.filter((f) => f.vcodec !== "none" && f.acodec !== "none" && f.ext !== "m3u8");

  // ── Audio only ──
  const m4aBest = audioFormats
    .filter((f) => f.ext === "m4a")
    .sort((a, b) => (b.tbr ?? 0) - (a.tbr ?? 0))[0];
  const webmBest = audioFormats
    .filter((f) => f.ext === "webm")
    .sort((a, b) => (b.tbr ?? 0) - (a.tbr ?? 0))[0];

  if (m4aBest) {
    result.push({
      id: m4aBest.format_id,
      label: "Audio (AAC/M4A)",
      extension: "m4a",
      kind: "audio",
      sizeLabel: formatSizeLabel(m4aBest.filesize),
    });
  }
  if (webmBest) {
    result.push({
      id: webmBest.format_id,
      label: "Audio (Opus/WebM)",
      extension: "webm",
      kind: "audio",
      sizeLabel: formatSizeLabel(webmBest.filesize),
    });
  }

  // ── Video only ──
  const mp4Video = videoFormats
    .filter((f) => f.ext === "mp4" && f.height)
    .sort((a, b) => (a.height ?? 0) - (b.height ?? 0));

  const seenResolutions = new Set<string>();
  for (const vf of mp4Video) {
    const res = `${vf.height}p`;
    if (seenResolutions.has(res)) continue;
    seenResolutions.add(res);
    result.push({
      id: vf.format_id,
      label: `Video ${res} (MP4)`,
      extension: "mp4",
      kind: "video",
      sizeLabel: formatSizeLabel(vf.filesize),
    });
  }

  if (result.filter((r) => r.kind === "video").length === 0) {
    const webmVideo = videoFormats
      .filter((f) => f.ext === "webm" && f.height)
      .sort((a, b) => (a.height ?? 0) - (b.height ?? 0));
    for (const vf of webmVideo) {
      const res = `${vf.height}p`;
      if (seenResolutions.has(res)) continue;
      seenResolutions.add(res);
      result.push({
        id: vf.format_id,
        label: `Video ${res} (WebM)`,
        extension: "webm",
        kind: "video",
        sizeLabel: formatSizeLabel(vf.filesize),
      });
    }
  }

  // ── Premium: video + audio merged ──
  const premiumResolutions = [144, 240, 360, 480, 720, 1080];
  const availableHeights = new Set(videoFormats.filter((f) => f.height).map((f) => f.height!));

  for (const h of premiumResolutions) {
    const formatStr = `bestvideo[height<=${h}]+bestaudio/best[height<=${h}]/best`;
    const label = `Video ${h}p + Audio (MP4)`;
    const hasAtLeastH = [...availableHeights].some((ah) => ah >= h);
    if (hasAtLeastH || h === 144) {
      result.push({
        id: formatStr,
        label,
        extension: "mp4",
        kind: "premium",
        sizeLabel: null,
      });
    }
  }

  // Fallback if nothing found
  if (result.length === 0) {
    result.push(
      { id: "bestaudio", label: "Audio terbaik (WebM)", extension: "webm", kind: "audio", sizeLabel: null },
      { id: "bestvideo+bestaudio/best", label: "Video 720p + Audio (MP4)", extension: "mp4", kind: "premium", sizeLabel: null },
    );
  }

  return result;
}

function startProcess(job: DownloadJob, formatId: string) {
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

  // Use ffmpeg from D:/ffmpeg-tools if available
  const ffmpegDir = process.env.FFMPEG_DIR || "D:/ffmpeg-tools";
  args.push("--ffmpeg-location", ffmpegDir);

  // Add cookies for Instagram
  const cookiesFile = path.join(os.tmpdir(), "social-downloader", "ig-cookies.txt");
  try {
    const fs = require("fs");
    if (fs.existsSync(cookiesFile)) {
      args.push("--cookies", cookiesFile);
    }
  } catch {}

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

  // Set filePath to original output first
  const originalPath = path.join(job.directory, outputFile);
  job.filePath = originalPath;

  // Rename file to use the video title
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
      // keep original filename if rename fails
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

router.post("/downloads/inspect", async (req, res) => {
  // Rate limit
  const ip = (req.ip ?? req.socket.remoteAddress ?? "unknown");
  if (!checkRateLimit(ip)) {
    res.status(429).json({ error: "Terlalu banyak permintaan. Coba lagi dalam 1 menit." });
    return;
  }

  const parsed = InspectDownloadBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: "Masukkan URL yang valid." });
    return;
  }

  let url: string;
  try {
    url = normalizeSocialUrl(parsed.data.url);
  } catch {
    res.status(400).json({ error: "Masukkan URL yang valid." });
    return;
  }
  const platform = platformForUrl(url);
  if (platform === "unknown") {
    res.status(400).json({ error: "URL harus berasal dari YouTube, Instagram, Threads, atau TikTok." });
    return;
  }

  // Check cache
  const cacheKey = `${platform}:${url}`;
  const cached = getCachedInspect(cacheKey);
  if (cached) {
    res.json(cached);
    return;
  }

  try {
    if (platform === "tiktok") {
      // Use tikwm API for TikTok (yt-dlp blocked by anti-bot)
      const tikwmData = await inspectWithTikwm(url);
      const formats: Array<{ id: string; label: string; extension: string; kind: "video" | "audio" | "premium"; sizeLabel: string | null }> = [];
      if (tikwmData.play) {
        formats.push({
          id: `tikwm:sd:${encodeURIComponent(tikwmData.play)}`,
          label: "Video (SD)",
          extension: "mp4",
          kind: "video",
          sizeLabel: formatSizeLabel(tikwmData.size),
        });
      }
      if (tikwmData.hdplay) {
        formats.push({
          id: `tikwm:hd:${encodeURIComponent(tikwmData.hdplay)}`,
          label: "Video (HD)",
          extension: "mp4",
          kind: "video",
          sizeLabel: formatSizeLabel(tikwmData.hd_size ?? undefined),
        });
      }
      if (tikwmData.music) {
        formats.push({
          id: `tikwm:music:${encodeURIComponent(tikwmData.music)}`,
          label: "Audio (Music)",
          extension: "mp3",
          kind: "audio",
          sizeLabel: null,
        });
      }
      const data = InspectDownloadResponse.parse({
        url,
        platform,
        isSupported: true,
        title: tikwmData.title || null,
        thumbnailUrl: tikwmData.cover || null,
        durationSeconds: tikwmData.duration || null,
        formats,
      });
      setCachedInspect(cacheKey, data);
      res.json(data);
    } else if (platform === "instagram" || platform === "threads") {
      // Instagram & Threads require cookies for yt-dlp
      const cookies = await loadIgCookies();
      if (!cookies) {
        const platformName = platform === "instagram" ? "Instagram" : "Threads";
        res.status(400).json({ error: `${platformName} membutuhkan login. Silakan masukkan Instagram cookies terlebih dahulu.` });
        return;
      }
      try {
        const metadata = await inspectWithYtDlp(url, cookies);
        const data = InspectDownloadResponse.parse({
          url,
          platform,
          isSupported: true,
          title: metadata.title ?? null,
          thumbnailUrl: metadata.thumbnail ?? null,
          durationSeconds: metadata.duration ?? null,
          formats: buildFormatsFromMetadata(metadata.formats),
        });
        setCachedInspect(cacheKey, data);
        res.json(data);
      } catch (error) {
        const platformName = platform === "instagram" ? "Instagram" : "Threads";
        req.log.warn({ err: error, url, platform }, `${platformName} inspect failed`);
        res.status(502).json({ error: `${platformName} cookies mungkin expired. Silakan masukkan ulang cookies.` });
      }
    } else {
      const metadata = await inspectWithYtDlp(url);
      const data = InspectDownloadResponse.parse({
        url,
        platform,
        isSupported: true,
        title: metadata.title ?? null,
        thumbnailUrl: metadata.thumbnail ?? null,
        durationSeconds: metadata.duration ?? null,
        formats: buildFormatsFromMetadata(metadata.formats),
      });
      setCachedInspect(cacheKey, data);
      res.json(data);
    }
  } catch (error) {
    req.log.warn({ err: error, url, platform }, "Could not inspect media URL");
    res.status(502).json({ error: userFacingInspectionError(error) });
  }
});

router.post("/downloads", async (req, res) => {
  const parsed = CreateDownloadBody.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ error: "Data unduhan tidak lengkap." });
    return;
  }

  let url: string;
  try {
    url = normalizeSocialUrl(parsed.data.url);
  } catch {
    res.status(400).json({ error: "Masukkan URL yang valid." });
    return;
  }
  const { formatId, mediaType: requestedMediaType = "video" } = parsed.data;
  const isPremium = formatId.includes("+");
  const mediaType = isPremium ? "video" : requestedMediaType;
  const platform = platformForUrl(url);
  if (platform === "unknown" || (!supportedFormatIds.has(formatId) && !/^\d+$/.test(formatId) && !isPremium && !formatId.startsWith("tikwm:") && formatId !== "sd" && formatId !== "hd")) {
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
    title: (parsed.data as any).title ?? null,
    status: "queued",
    progress: 0,
    mediaType: mediaType as "video" | "audio" | "premium",
    filename: null,
    createdAt: new Date().toISOString(),
    downloadUrl: null,
    error: null,
    filePath: null,
    directory,
  };
  rememberJob(job);

  if (formatId.startsWith("tikwm:")) {
    // TikTok direct download via tikwm URL
    const parts = formatId.split(":");
    const kind = parts[1]; // sd, hd, music
    const directUrl = decodeURIComponent(parts.slice(2).join(":"));
    const ext = kind === "music" ? "mp3" : "mp4";
    const outputFilename = `${job.id}.${ext}`;
    const outputPath = path.join(job.directory, outputFilename);
    job.status = "downloading";
    job.filename = outputFilename;
    job.filePath = outputPath;
    // Async download
    downloadTiktokFile(directUrl, outputPath).then((bytes) => {
      job.status = "completed";
      job.progress = 100;
      job.downloadUrl = `/api/downloads/${job.id}/file`;
      // Rename to title
      const title = job.title ? sanitizeFilename(job.title) : null;
      if (title) {
        const desiredFilename = `${title}.${ext}`;
        const desiredPath = path.join(job.directory, desiredFilename);
        rename(outputPath, desiredPath).then(() => {
          job.filename = desiredFilename;
          job.filePath = desiredPath;
        }).catch(() => {});
      }
      broadcastProgress(job);
      void persistHistory();
    }).catch((err) => {
      job.status = "failed";
      job.error = errorMessage(err);
      broadcastProgress(job);
    });
  } else {
    startProcess(job, formatId);
  }
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
    const ext = path.extname(job.filename).toLowerCase();
    const contentTypes: Record<string, string> = {
      ".m4a": "audio/mp4",
      ".mp4": "video/mp4",
      ".webm": "audio/webm",
      ".mp3": "audio/mpeg",
      ".ogg": "audio/ogg",
      ".opus": "audio/opus",
      ".mkv": "video/x-matroska",
    };
    const contentType = contentTypes[ext] ?? "application/octet-stream";
    res.setHeader("Content-Type", contentType);
    res.setHeader("Content-Disposition", `attachment; filename="${job.filename.replace(/["\\]/g, "")}"`);
    createReadStream(job.filePath).pipe(res);
  } catch (error) {
    req.log.warn({ err: error, id: parsed.data.id }, "Could not serve downloaded file");
    res.status(404).json({ error: "File tidak ditemukan." });
  }
});

router.post("/instagram/cookies", async (req, res) => {
  const { cookies } = req.body as { cookies?: string };
  if (!cookies || typeof cookies !== "string" || cookies.trim().length < 10) {
    res.status(400).json({ error: "Cookies tidak valid." });
    return;
  }
  try {
    await saveIgCookies(cookies);
    res.json({ status: "ok", message: "Instagram cookies berhasil disimpan." });
  } catch (error) {
    res.status(500).json({ error: "Gagal menyimpan cookies." });
  }
});

router.get("/instagram/cookies/status", async (_req, res) => {
  const cookies = await loadIgCookies();
  res.json({ hasCookies: cookies !== null && cookies.length > 0 });
});

router.delete("/instagram/cookies", async (_req, res) => {
  try {
    const { unlink } = await import("node:fs/promises");
    await unlink(igCookiesPath).catch(() => {});
    igCookies = null;
    res.json({ status: "ok", message: "Instagram cookies dihapus." });
  } catch {
    res.json({ status: "ok" });
  }
});

// ── SSE endpoint for real-time progress ──
router.get("/downloads/stream/progress", (req, res) => {
  res.writeHead(200, {
    "Content-Type": "text/event-stream",
    "Cache-Control": "no-cache",
    "Connection": "keep-alive",
    "Access-Control-Allow-Origin": "*",
  });
  res.write("\n");
  sseClients.add(res);
  req.on("close", () => {
    sseClients.delete(res);
  });
});

// ── Load history on startup ──
void loadHistory();

export default router;