import os from "node:os";
import path from "node:path";
import { mkdir, writeFile, readFile, unlink } from "node:fs/promises";
import { logger } from "./logger";

const igCookiesPath = path.join(os.tmpdir(), "social-downloader", "instagram-cookies.txt");

let igCookies: string | null = null;

export function getIgCookiesPath() {
  return igCookiesPath;
}

export async function loadIgCookies(): Promise<string | null> {
  if (igCookies !== null) return igCookies;
  try {
    igCookies = (await readFile(igCookiesPath, "utf-8")).trim();
    return igCookies;
  } catch {
    return null;
  }
}

export async function saveIgCookies(cookies: string): Promise<void> {
  await mkdir(path.dirname(igCookiesPath), { recursive: true });
  await writeFile(igCookiesPath, cookies, "utf-8");
  igCookies = cookies.trim();
}

export async function deleteIgCookies(): Promise<void> {
  await unlink(igCookiesPath).catch(() => {});
  igCookies = null;
}
