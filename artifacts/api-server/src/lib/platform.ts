import type { DownloadPlatform } from "./types";

export function platformForUrl(rawUrl: string): DownloadPlatform {
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

export function normalizeSocialUrl(rawUrl: string) {
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

export function errorMessage(error: unknown): string {
  if (error && typeof error === "object" && "stderr" in error && typeof error.stderr === "string") {
    return error.stderr.trim().split("\n").filter(Boolean).at(-1) ?? "Media tidak dapat diproses.";
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Media tidak dapat diproses.";
}

export function userFacingInspectionError(error: unknown) {
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

export function sanitizeFilename(name: string): string {
  return name
    .replace(/[\/:*?"<>|]/g, "")
    .replace(/\s+/g, "_")
    .replace(/_{2,}/g, "_")
    .slice(0, 120)
    .replace(/_+$/, "");
}

export function formatSizeLabel(bytes: number | undefined): string | null {
  if (!bytes) return null;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export const supportedFormatIds = new Set([
  "best", "bestvideo+bestaudio", "bestaudio", "worst",
  "m4a", "webm-audio", "mp4-360p", "mp4-480p", "mp4-720p", "mp4-1080p",
]);
