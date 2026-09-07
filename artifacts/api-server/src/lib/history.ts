import os from "node:os";
import path from "node:path";
import { mkdir, writeFile, readFile, rm } from "node:fs/promises";
import { logger } from "./logger";
import { publicJob, type DownloadJob, type PublicJob } from "./types";
import { broadcastProgress } from "./sse";

export const jobs = new Map<string, DownloadJob>();
export const downloadsRoot = path.join(os.tmpdir(), "social-downloader");
export const maxRecentJobs = 50;
const historyPath = path.join(os.tmpdir(), "social-downloader", "download-history.json");

export async function persistHistory(): Promise<void> {
  try {
    const entries = [...jobs.values()]
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .slice(0, maxRecentJobs)
      .map(publicJob);
    await mkdir(path.dirname(historyPath), { recursive: true });
    await writeFile(historyPath, JSON.stringify(entries, null, 2), "utf-8");
  } catch (error) {
    logger.warn({ err: error }, "Failed to persist download history");
  }
}

export async function loadHistory(): Promise<void> {
  try {
    const raw = await readFile(historyPath, "utf-8");
    const entries = JSON.parse(raw) as Array<PublicJob>;
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
  } catch (error) {
    if (error instanceof SyntaxError || (error as NodeJS.ErrnoException)?.code !== "ENOENT") {
      logger.warn({ err: error }, "Failed to load download history");
    }
  }
}

export function rememberJob(job: DownloadJob) {
  jobs.set(job.id, job);
  broadcastProgress(job);
  const entries = [...jobs.values()].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  for (const oldJob of entries.slice(maxRecentJobs)) {
    jobs.delete(oldJob.id);
    void rm(oldJob.directory, { recursive: true, force: true });
  }
  if (jobs.size % 5 === 0) void persistHistory();
}
