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
  // Clean up legacy path used by older yt-dlp.ts revisions so a stale
  // non-Netscape file can never break future downloads.
  const legacyPath = path.join(os.tmpdir(), "social-downloader", "ig-cookies.txt");
  if (legacyPath !== igCookiesPath) {
    await unlink(legacyPath).catch(() => {});
  }
}

export async function deleteIgCookies(): Promise<void> {
  await unlink(igCookiesPath).catch(() => {});
  const legacyPath = path.join(os.tmpdir(), "social-downloader", "ig-cookies.txt");
  if (legacyPath !== igCookiesPath) {
    await unlink(legacyPath).catch(() => {});
  }
  igCookies = null;
}

/**
 * Convert a raw `Cookie:` header string ("sessionid=abc; csrftoken=xyz")
 * into Netscape cookie-file format required by `yt-dlp --cookies`.
 * If input already looks like Netscape format, it is returned as-is.
 */
export function toNetscapeCookieFile(raw: string, domains = [".instagram.com", ".threads.com", ".threads.net"]): string {
  const trimmed = raw.trim();
  if (trimmed.startsWith("# Netscape") || trimmed.includes("\t")) {
    return raw;
  }
  const pairs = trimmed
    .split(";")
    .map((p) => p.trim())
    .filter(Boolean)
    .map((p) => {
      const eq = p.indexOf("=");
      if (eq <= 0) return null;
      return { name: p.slice(0, eq).trim(), value: p.slice(eq + 1).trim() };
    })
    .filter((x): x is { name: string; value: string } => x !== null && x.name.length > 0);
  const lines = ["# Netscape HTTP Cookie File"];
  for (const domain of domains) {
    for (const { name, value } of pairs) {
      lines.push(`${domain}\tTRUE\t/\tTRUE\t0\t${name}\t${value}`);
    }
  }
  return lines.join("\n") + "\n";
}
