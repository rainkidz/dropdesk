/**
 * Registrasi aktivasi lisensi per device (Lapis 2 anti-bajak).
 *
 * Setiap kode lisensi yang di-redeem di Android didaftarkan ke server
 * dengan device fingerprint. Maksimum device per lisensi dikontrol
 * LICENSE_MAX_DEVICES (default 1 — 1 license = 1 HP, anti share).
 *
 * Penyimpanan: JSON file sederhana di OS temp. Untuk produksi dengan
 * ribuan user, ganti ke Postgres/Drizzle — interface publik tetap sama.
 */
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { createHash } from "node:crypto";
import os from "node:os";
import path from "node:path";
import { logger } from "./logger";

const storageDir = path.join(os.tmpdir(), "social-downloader");
const storagePath = path.join(storageDir, "license-activations.json");

export type Activation = {
  /** Kode TN1-YYYYMM-XXXX-SSSSSSSS. */
  licenseKey: string;
  /** SHA-256 hex dari device fingerprint (Build.FINGERPRINT + ANDROID_ID). */
  deviceFingerprint: string;
  /** Epoch ms aktivasi pertama. */
  firstActivatedMs: number;
  /** Epoch ms terakhir kali heartbeat diterima (untuk deteksi idle). */
  lastSeenMs: number;
};

type Storage = Record<string, Activation[]>; // licenseKey → [activation, ...]

let cache: Storage | null = null;
let writeQueue: Promise<void> = Promise.resolve();

async function load(): Promise<Storage> {
  if (cache) return cache;
  try {
    const raw = await readFile(storagePath, "utf-8");
    cache = JSON.parse(raw) as Storage;
  } catch (error) {
    if ((error as NodeJS.ErrnoException)?.code !== "ENOENT") {
      logger.warn({ err: error }, "load license activations");
    }
    cache = {};
  }
  return cache;
}

async function persist(): Promise<void> {
  const data = cache ?? {};
  // Serialkan agar update file berurutan.
  writeQueue = writeQueue.then(async () => {
    try {
      await mkdir(storageDir, { recursive: true });
      await writeFile(storagePath, JSON.stringify(data, null, 2), "utf-8");
    } catch (error) {
      logger.warn({ err: error }, "persist license activations");
    }
  });
  await writeQueue;
}

function maxDevices(): number {
  const v = Number(process.env.LICENSE_MAX_DEVICES ?? "1");
  return Number.isFinite(v) && v > 0 ? Math.floor(v) : 1;
}

export type ActivateOk = { ok: true; alreadyBound: boolean; firstActivatedMs: number; expiresAt: string | null };
export type ActivateFail = { ok: false; reason: "invalid_key" | "expired" | "limit_reached"; expiresAt?: string | null };

/**
 * Daftarkan device untuk lisensi. Tolak bila device lain sudah melebihi
 * batas. Tidak menyentuh server billing — hanya registrasi aktivasi.
 */
export async function activateDevice(
  licenseKey: string,
  deviceFingerprint: string,
  licenseExpiryYyyymm: number | null,
  isValidLicense: boolean,
): Promise<ActivateOk | ActivateFail> {
  if (!isValidLicense) return { ok: false, reason: "invalid_key" };
  if (licenseExpiryYyyymm !== null) {
    const ym = currentYearMonth();
    if (licenseExpiryYyyymm < ym) return { ok: false, reason: "expired" };
  }
  const data = await load();
  const list = data[licenseKey] ?? [];
  const existing = list.find((a) => a.deviceFingerprint === deviceFingerprint);
  if (existing) {
    existing.lastSeenMs = Date.now();
    data[licenseKey] = list;
    await persist();
    return {
      ok: true,
      alreadyBound: true,
      firstActivatedMs: existing.firstActivatedMs,
      expiresAt: licenseExpiryYyyymm !== null
        ? yyyymmToIso(licenseExpiryYyyymm) : null,
    };
  }
  if (list.length >= maxDevices()) {
    return {
      ok: false,
      reason: "limit_reached",
      expiresAt: licenseExpiryYyyymm !== null
        ? yyyymmToIso(licenseExpiryYyyymm) : null,
    };
  }
  const now = Date.now();
  list.push({ licenseKey, deviceFingerprint, firstActivatedMs: now, lastSeenMs: now });
  data[licenseKey] = list;
  await persist();
  logger.info({ licenseKey: maskKey(licenseKey), devices: list.length }, "license activated");
  return {
    ok: true,
    alreadyBound: false,
    firstActivatedMs: now,
    expiresAt: licenseExpiryYyyymm !== null
      ? yyyymmToIso(licenseExpiryYyyymm) : null,
  };
}

export async function heartbeatDevice(licenseKey: string, deviceFingerprint: string): Promise<boolean> {
  const data = await load();
  const list = data[licenseKey];
  if (!list) return false;
  const a = list.find((x) => x.deviceFingerprint === deviceFingerprint);
  if (!a) return false;
  a.lastSeenMs = Date.now();
  await persist();
  return true;
}

export async function deviceCount(licenseKey: string): Promise<number> {
  const data = await load();
  return (data[licenseKey] ?? []).length;
}

export function currentYearMonth(): number {
  const now = new Date();
  return now.getFullYear() * 100 + (now.getMonth() + 1);
}

function yyyymmToIso(yyyymm: number): string {
  const y = Math.floor(yyyymm / 100);
  const m = yyyymm % 100;
  // Akhir bulan (mis. 202610 → '2026-10-31T23:59:59Z').
  const lastDay = new Date(Date.UTC(y, m, 0)).getUTCDate();
  return `${y}-${String(m).padStart(2, "0")}-${String(lastDay).padStart(2, "0")}T23:59:59Z`;
}

function maskKey(key: string): string {
  if (key.length < 8) return "***";
  return key.slice(0, 4) + "…" + key.slice(-4);
}

/** Hash device fingerprint untuk log (jangan simpan fingerprint asli di log). */
export function fingerprintHash(fingerprint: string): string {
  return createHash("sha256").update(fingerprint).digest("hex").slice(0, 16);
}
