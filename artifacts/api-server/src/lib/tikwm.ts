import { createWriteStream } from "node:fs";
import { stat } from "node:fs/promises";
import { pipeline } from "node:stream/promises";

export type TikwmData = {
  title: string;
  author: { unique_id: string };
  duration: number;
  play: string;
  hdplay: string;
  music: string;
  cover: string;
  size: number;
  hd_size: number | null;
  width: number;
  height: number;
};

export async function inspectWithTikwm(url: string): Promise<TikwmData> {
  const formData = new URLSearchParams();
  formData.append("url", url);
  const resp = await fetch("https://www.tikwm.com/api/", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: formData.toString(),
    signal: AbortSignal.timeout(15_000),
  });
  const data = await resp.json() as { code: number; data?: TikwmData };
  if (data.code !== 0 || !data.data) {
    throw new Error("TikTok media tidak dapat diproses.");
  }
  return data.data;
}

export async function downloadTiktokFile(url: string, destPath: string): Promise<number> {
  const resp = await fetch(url, { signal: AbortSignal.timeout(120_000) });
  if (!resp.ok) throw new Error(`Download failed: ${resp.status}`);
  if (!resp.body) throw new Error("No response body");
  const fileStream = createWriteStream(destPath);
  await pipeline(resp.body, fileStream);
  const stats = await stat(destPath);
  return stats.size;
}
