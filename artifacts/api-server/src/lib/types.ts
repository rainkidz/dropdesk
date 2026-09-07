import type { ChildProcess } from "node:child_process";

export type DownloadPlatform = "youtube" | "instagram" | "threads" | "tiktok" | "facebook" | "unknown";
export type DownloadStatus = "queued" | "downloading" | "completed" | "failed";
export type FormatKind = "video" | "audio" | "premium";

export type DownloadJob = {
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

export type PublicJob = ReturnType<typeof publicJob>;

export function publicJob(job: DownloadJob) {
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
