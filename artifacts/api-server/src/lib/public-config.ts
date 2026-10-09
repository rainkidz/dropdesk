import { createHmac } from "node:crypto";
import { logger } from "./logger";
import { currentYearMonth } from "./license-activations";

/**
 * Konfigurasi publik yang dikirim ke aplikasi — ditandatangani HMAC agar
 * MITM (atau server nakal) tidak bisa menyuntikkan AdMob ID palsu.
 *
 * Package: { body tanpa signature, signature hex }
 * Klien (AdsManager) verifikasi signature → pakai IDs; bila gagal/rusak →
 * fallback ke ID hardcode di BuildConfig.
 *
 * Env override (rotasi tanpa rebuild):
 *   ADMOB_BANNER_ID       default = ID resmi yang tertanam di BuildConfig
 *   ADMOB_INTERSTITIAL_ID default = ID resmi
 *   MIN_APP_VERSION       default = "4.5.0"
 *
 * Catatan keamanan: AdMob ID yang ditaruh di BuildConfig.LICENSE_HMAC_SECRET
 * *bisa* diekstrak bajak ulet (sama keterbatasannya dengan kode lisensi).
 * Bentuk pertahanan ini hanya membuat swap ID tidak bisa dilakukan cuma
 * dengan grep + sed di APK; perlu rekompilasi. Untuk rotasi operasional
 * (ban akun, A/B), cukup set env di server dan rebuild APK tidak perlu.
 */

export type PublicConfig = {
  admobBannerId: string;
  admobInterstitialId: string;
  minVersion: string;
  releasedAt: string;
  yyyymm: number;
};

export type SignedPublicConfig = PublicConfig & { signature: string };

const DEFAULT_BANNER = "ca-app-pub-7452006143730401/8120744119";
const DEFAULT_INTERSTITIAL = "ca-app-pub-7452006143730401/4435368715";
const DEFAULT_MIN_VERSION = "4.5.0";

/** Kunci signature: wajib LICENSE_HMAC_SECRET (sama dengan secret license). */
function signingKey(): string {
  return process.env.LICENSE_HMAC_SECRET ?? "";
}

/** True bila server punya secret cukup untuk menandatangani respons. */
export function publicConfigConfigured(): boolean {
  return signingKey().length >= 16;
}

/** Canonical JSON: sort keys deterministik agar signature konsisten lintas runtime. */
function canonicalJson(body: Omit<PublicConfig, never>): string {
  const keys = Object.keys(body).sort();
  return JSON.stringify(keys.reduce<Record<string, unknown>>((acc, k) => {
    acc[k] = (body as Record<string, unknown>)[k];
    return acc;
  }, {}));
}

/** Bangun konfigurasi publik + signature. */
export function buildSignedPublicConfig(): SignedPublicConfig {
  const body: PublicConfig = {
    admobBannerId: process.env.ADMOB_BANNER_ID?.trim() || DEFAULT_BANNER,
    admobInterstitialId: process.env.ADMOB_INTERSTITIAL_ID?.trim() || DEFAULT_INTERSTITIAL,
    minVersion: process.env.MIN_APP_VERSION?.trim() || DEFAULT_MIN_VERSION,
    releasedAt: new Date().toISOString(),
    yyyymm: currentYearMonth(),
  };
  const sig = createHmac("sha256", signingKey()).update(canonicalJson(body)).digest("hex");
  return { ...body, signature: sig };
}

/**
 * Verifikasi signature respons. Klien (Android) panggil ini untuk menolak
 * konfigurasi yang tidak ditandatangani server dengan benar. Return null
 * bila tidak valid (jangan pakai datanya); return body bersih bila valid.
 */
export function verifySignedPublicConfig(payload: unknown): PublicConfig | null {
  if (!payload || typeof payload !== "object") return null;
  const obj = payload as Record<string, unknown>;
  const sig = typeof obj.signature === "string" ? obj.signature : "";
  if (!/^[0-9a-f]{64}$/i.test(sig)) return null;
  if (signingKey().length < 16) return null;

  const { signature: _drop, ...bodyRaw } = obj;
  const body = bodyRaw as PublicConfig;
  const expected = createHmac("sha256", signingKey()).update(canonicalJson(body)).digest("hex");
  if (expected.length !== sig.length) return null;
  let diff = 0;
  for (let i = 0; i < expected.length; i++) diff |= expected.charCodeAt(i) ^ sig.charCodeAt(i);
  if (diff !== 0) {
    logger.warn({ reason: "bad_signature" }, "public config signature mismatch");
    return null;
  }
  if (typeof body.admobBannerId !== "string" || typeof body.admobInterstitialId !== "string") {
    return null;
  }
  if (!/^ca-app-pub-\d+\/\d+$/.test(body.admobBannerId)) return null;
  if (!/^ca-app-pub-\d+\/\d+$/.test(body.admobInterstitialId)) return null;
  return body;
}
