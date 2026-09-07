import type { ServerResponse } from "node:http";
import { publicJob, type DownloadJob } from "./types";

export const sseClients = new Set<ServerResponse>();

export function broadcastProgress(job: DownloadJob) {
  const data = JSON.stringify(publicJob(job));
  for (const client of sseClients) {
    client.write(`data: ${data}\n\n`);
  }
}
