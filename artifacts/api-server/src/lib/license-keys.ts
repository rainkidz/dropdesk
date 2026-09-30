import { createHmac, randomInt } from "node:crypto";

/**
 * Penerbit kode lisensi TN1 — HARUS identik dengan LicenseManager.kt
 * (Android) dan tools/make_license.py.
 *
 * Format: TN1-YYYYMM-XXXX-SSSSSSSS, MAC = 40 bit pertama
 * HMAC-SHA256(secret, "TN1|YYYYMM|XXXX") dalam alfabet Crockford.
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

/** YYYYMM untuk n bulan dari sekarang (1 = bulan berjalan + n). */
export function expiryYearMonth(months: number): string {
  const now = new Date();
  const total = now.getFullYear() * 12 + now.getMonth() + months;
  const year = Math.floor(total / 12);
  const month = (total % 12) + 1;
  return `${year}${String(month).padStart(2, "0")}`;
}

function randomSuffix(): string {
  let s = "";
  for (let i = 0; i < 4; i++) s += ALPHABET[randomInt(ALPHABET.length)];
  return s;
}

export function makeLicenseKey(secret: string, months: number): { key: string; expiry: string } {
  const expiry = expiryYearMonth(months);
  const rand = randomSuffix();
  return { key: `TN1-${expiry}-${rand}-${mac40(secret, "TN1", expiry, rand)}`, expiry };
}
