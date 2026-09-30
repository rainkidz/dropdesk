import { createHmac } from "node:crypto";

/**
 * Verifikasi lisensi TN1 di server (sama format dengan Android
 * LicenseManager.kt / tools/make_license.py).
 */

const ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

function mac40(secret: string, ver: string, expiry: string, rand: string): string {
  const digest = createHmac("sha256", secret).update(`${ver}|${expiry}|${rand}`).digest();
  let acc = 0;
  let bits = 0;
  let out = "";
  for (const byte of digest) {
    acc = (acc << 8) | byte;
    bits += 8;
    while (bits >= 5 && out.length < 8) {
      bits -= 5;
      out += ALPHABET[(acc >>> bits) & 0x1f];
    }
    if (out.length === 8) break;
  }
  return out;
}

function currentYearMonth(): number {
  const now = new Date();
  return now.getFullYear() * 100 + (now.getMonth() + 1);
}

export type VerifyResult =
  | { ok: true; expiryYyyymm: number }
  | { ok: false; reason: "not_configured" | "bad_format" | "bad_signature" | "expired" };

export function verifyLicenseKey(rawKey: string): VerifyResult {
  const secret = process.env.LICENSE_HMAC_SECRET ?? "";
  if (secret.length < 16) return { ok: false, reason: "not_configured" };
  const key = rawKey.trim().toUpperCase().replace(/[\s_]+/g, "").replace(/[—–]/g, "-");
  const parts = key.split("-");
  if (parts.length !== 4 || parts[0] !== "TN1") return { ok: false, reason: "bad_format" };
  const [ver, expiry, rand, mac] = parts;
  if (expiry.length !== 6 || !/^\d+$/.test(expiry)) return { ok: false, reason: "bad_format" };
  if (rand.length !== 4 || ![...rand].every((c) => ALPHABET.includes(c))) {
    return { ok: false, reason: "bad_format" };
  }
  if (mac.length !== 8 || ![...mac].every((c) => ALPHABET.includes(c))) {
    return { ok: false, reason: "bad_format" };
  }
  const expected = mac40(secret, ver, expiry, rand);
  let diff = 0;
  for (let i = 0; i < expected.length; i++) diff |= expected.charCodeAt(i) ^ mac.charCodeAt(i);
  if (diff !== 0) return { ok: false, reason: "bad_signature" };
  const expiryNum = Number(expiry);
  if (expiryNum < currentYearMonth()) return { ok: false, reason: "expired" };
  return { ok: true, expiryYyyymm: expiryNum };
}

export function maskLicenseKey(key: string): string {
  if (key.length < 8) return "***";
  return `${key.slice(0, 4)}…${key.slice(-4)}`;
}
