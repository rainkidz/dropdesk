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
import { createReadStream } from "node:fs";
import { mkdir, rename, stat } from "node:fs/promises";
import path from "node:path";

import { checkRateLimit } from "../lib/rate-limit";
import { getCachedInspect, setCachedInspect } from "../lib/inspect-cache";
import { sseClients, broadcastProgress } from "../lib/sse";
import { publicJob, type DownloadJob } from "../lib/types";
import { jobs, downloadsRoot, maxRecentJobs, rememberJob, persistHistory, loadHistory } from "../lib/history";
import { loadIgCookies, saveIgCookies, deleteIgCookies } from "../lib/cookies";
import {
  platformForUrl,
  normalizeSocialUrl,
  errorMessage,
  userFacingInspectionError,
  sanitizeFilename,
  formatSizeLabel,
  supportedFormatIds,
} from "../lib/platform";
import { inspectWithYtDlp, buildFormatsFromMetadata, startProcess } from "../lib/yt-dlp";
import { inspectWithTikwm, downloadTiktokFile } from "../lib/tikwm";

const router: IRouter = Router();

router.post("/downloads/inspect", async (req, res) => {
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
  const cacheKey = `${platform}:${url}`;
  const cached = getCachedInspect(cacheKey);
  if (cached) {
    res.json(cached);
    return;
  }
  try {
    if (platform === "tiktok") {
      const tikwmData = await inspectWithTikwm(url);
      const formats: Array<{ id: string; label: string; extension: string; kind: "video" | "audio" | "premium"; sizeLabel: string | null }> = [];
      if (tikwmData.play) {
        formats.push({ id: `tikwm:sd:${encodeURIComponent(tikwmData.play)}`, label: "Video (SD)", extension: "mp4", kind: "video", sizeLabel: formatSizeLabel(tikwmData.size) });
      }
      if (tikwmData.hdplay) {
        formats.push({ id: `tikwm:hd:${encodeURIComponent(tikwmData.hdplay)}`, label: "Video (HD)", extension: "mp4", kind: "video", sizeLabel: formatSizeLabel(tikwmData.hd_size ?? undefined) });
      }
      if (tikwmData.music) {
        formats.push({ id: `tikwm:music:${encodeURIComponent(tikwmData.music)}`, label: "Audio (Music)", extension: "mp3", kind: "audio", sizeLabel: null });
      }
      const data = InspectDownloadResponse.parse({ url, platform, isSupported: true, title: tikwmData.title || null, thumbnailUrl: tikwmData.cover || null, durationSeconds: tikwmData.duration || null, formats });
      setCachedInspect(cacheKey, data);
      res.json(data);
    } else if (platform === "instagram" || platform === "threads") {
      const cookies = await loadIgCookies();
      if (!cookies) {
        const platformName = platform === "instagram" ? "Instagram" : "Threads";
        res.status(400).json({ error: `${platformName} membutuhkan login. Silakan masukkan Instagram cookies terlebih dahulu.` });
        return;
      }
      try {
        const metadata = await inspectWithYtDlp(url, cookies);
        const data = InspectDownloadResponse.parse({ url, platform, isSupported: true, title: metadata.title ?? null, thumbnailUrl: metadata.thumbnail ?? null, durationSeconds: metadata.duration ?? null, formats: buildFormatsFromMetadata(metadata.formats) });
        setCachedInspect(cacheKey, data);
        res.json(data);
      } catch (error) {
        const platformName = platform === "instagram" ? "Instagram" : "Threads";
        req.log.warn({ err: error, url, platform }, `${platformName} inspect failed`);
        res.status(502).json({ error: `${platformName} cookies mungkin expired. Silakan masukkan ulang cookies.` });
      }
    } else {
      const metadata = await inspectWithYtDlp(url);
      const data = InspectDownloadResponse.parse({ url, platform, isSupported: true, title: metadata.title ?? null, thumbnailUrl: metadata.thumbnail ?? null, durationSeconds: metadata.duration ?? null, formats: buildFormatsFromMetadata(metadata.formats) });
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
    id, url, platform,
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
    const parts = formatId.split(":");
    const kind = parts[1];
    const directUrl = decodeURIComponent(parts.slice(2).join(":"));
    const ext = kind === "music" ? "mp3" : "mp4";
    const outputFilename = `${job.id}.${ext}`;
    const outputPath = path.join(job.directory, outputFilename);
    job.status = "downloading";
    job.filename = outputFilename;
    job.filePath = outputPath;
    downloadTiktokFile(directUrl, outputPath).then(() => {
      job.status = "completed";
      job.progress = 100;
      job.downloadUrl = `/api/downloads/${job.id}/file`;
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
  } else { startProcess(job, formatId); }
});

router.get("/downloads/recent", (_req, res) => {
  const entries = [...jobs.values()]
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .slice(0, maxRecentJobs)
    .map(publicJob);
  res.json(ListRecentDownloadsResponse.parse(entries));
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
      ".m4a": "audio/mp4", ".mp4": "video/mp4", ".webm": "audio/webm",
      ".mp3": "audio/mpeg", ".ogg": "audio/ogg", ".opus": "audio/opus", ".mkv": "video/x-matroska",
    };
    const contentType = contentTypes[ext] ?? "application/octet-stream";
    res.setHeader("Content-Type", contentType);
    res.setHeader("Content-Disposition", `attachment; filename="${job.filename.replace(/[\\]/g, "")}"`);
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
  } catch {
    res.status(500).json({ error: "Gagal menyimpan cookies." });
  }
});

router.get("/instagram/cookies/status", async (_req, res) => {
  const cookies = await loadIgCookies();
  res.json({ hasCookies: cookies !== null && cookies.length > 0 });
});

router.delete("/instagram/cookies", async (_req, res) => {
  try {
    await deleteIgCookies();
    res.json({ status: "ok", message: "Instagram cookies dihapus." });
  } catch {
    res.json({ status: "ok" });
  }
});

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

void loadHistory();

export default router;
