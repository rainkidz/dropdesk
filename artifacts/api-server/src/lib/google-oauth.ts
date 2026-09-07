import { createSign } from "node:crypto";
import { readFileSync } from "node:fs";

/**
 * Minimal Google service-account OAuth client (no npm deps).
 *
 * A service account JSON looks like:
 * {
 *   "type": "service_account",
 *   "project_id": "...",
 *   "private_key_id": "...",
 *   "private_key": "-----BEGIN PRIVATE KEY-----...",
 *   "client_email": "...@....iam.gserviceaccount.com",
 *   ...
 * }
 *
 * Provide it to the server via env:
 *   SERVICE_ACCOUNT_JSON   (inline JSON)   — recommended for managed hosts
 *   SERVICE_ACCOUNT_FILE   (path to JSON)  — local/container use
 *
 * Scopes used:
 *   - androidpublisher  → validate in-app subscription purchases
 *     (Play Developer API: purchases.subscriptions.get)
 *   - cloud-platform    → decode Play Integrity tokens
 *     (Google Cloud: integrity.decodeIntegrityToken)
 */

export interface ServiceAccount {
  client_email: string;
  private_key: string;
  project_id?: string;
  client_id?: string;
}

interface CachedToken {
  value: string;
  expiresAt: number; // epoch ms
}

function loadCredentials(): ServiceAccount | null {
  const inline = process.env["SERVICE_ACCOUNT_JSON"];
  if (inline) {
    try {
      return JSON.parse(inline) as ServiceAccount;
    } catch {
      return null;
    }
  }
  const file = process.env["SERVICE_ACCOUNT_FILE"];
  if (file) {
    try {
      return JSON.parse(readFileSync(file, "utf8")) as ServiceAccount;
    } catch {
      return null;
    }
  }
  return null;
}

const credentials: ServiceAccount | null = loadCredentials();
const tokenCache = new Map<string, CachedToken>();

export function isConfigured(): boolean {
  return credentials !== null;
}

export function configuredPackageHint(): string | null {
  return credentials?.project_id ?? null;
}

function toBase64Url(input: string | Buffer): string {
  return Buffer.from(input)
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function signJwt(scope: string): string {
  const creds = credentials;
  if (!creds) throw new Error("Service account not configured");

  const now = Math.floor(Date.now() / 1000);
  const header = toBase64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = toBase64Url(
    JSON.stringify({
      iss: creds.client_email,
      scope,
      aud: "https://oauth2.googleapis.com/token",
      iat: now - 30,
      exp: now + 3600,
    }),
  );
  const unsigned = `${header}.${payload}`;
  const signer = createSign("RSA-SHA256");
  signer.update(unsigned);
  signer.end();
  const signature = signer
    .sign(creds.private_key, "base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
  return `${unsigned}.${signature}`;
}

async function getAccessToken(scope: string): Promise<string> {
  const cached = tokenCache.get(scope);
  if (cached && cached.expiresAt > Date.now() + 60_000) {
    return cached.value;
  }

  const assertion = signJwt(scope);
  const form = new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    assertion,
  });

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: form.toString(),
  });

  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(`Google OAuth failed (${res.status}): ${text.slice(0, 300)}`);
  }

  const data = (await res.json()) as { access_token?: string; expires_in?: number };
  if (!data.access_token) {
    throw new Error("Google OAuth returned no access token");
  }

  tokenCache.set(scope, {
    value: data.access_token,
    expiresAt: Date.now() + (data.expires_in ?? 3600) * 1000,
  });
  return data.access_token;
}

export const SCOPE_ANDROID_PUBLISHER = "https://www.googleapis.com/auth/androidpublisher";
export const SCOPE_CLOUD_PLATFORM = "https://www.googleapis.com/auth/cloud-platform";

export async function androidPublisherToken(): Promise<string> {
  return getAccessToken(SCOPE_ANDROID_PUBLISHER);
}

export async function cloudPlatformToken(): Promise<string> {
  return getAccessToken(SCOPE_CLOUD_PLATFORM);
}

// ── Google Play Developer API — verify a subscription purchase ────────────────────

export interface SubscriptionVerification {
  ok: boolean;
  reason?: string;
  /** epoch ms when the subscription expires (null = unknown/auto-renewing). */
  expiryTimeMillis?: number | null;
  purchaseTimeMillis?: number | null;
  autoRenewing?: boolean | null;
  paymentState?: number | null;
}

export async function verifySubscriptionPurchase(args: {
  packageName: string;
  productId: string;
  purchaseToken: string;
}): Promise<SubscriptionVerification> {
  const token = await androidPublisherToken();
  const url =
    "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" +
    `${encodeURIComponent(args.packageName)}/purchases/subscriptions/` +
    `${encodeURIComponent(args.productId)}/tokens/${encodeURIComponent(args.purchaseToken)}`;

  const res = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
  });

  if (!res.ok) {
    // 400 = the purchase token is invalid/not found → hard rejection.
    // 401/403/5xx = our own auth/server trouble → caller treats as upstream error.
    const body = (await res.json().catch(() => null)) as { error?: { message?: string } } | null;
    if (res.status === 400 || res.status === 404) {
      return { ok: false, reason: body?.error?.message ?? "invalid_purchase" };
    }
    throw new Error(
      `Play Developer API error ${res.status}: ${body?.error?.message ?? "unknown"}`,
    );
  }

  const data = (await res.json()) as {
    startTimeMillis?: string;
    expiryTimeMillis?: string;
    autoRenewing?: boolean;
    paymentState?: number;
    cancelReason?: number;
  };

  const expiry = data.expiryTimeMillis ? Number(data.expiryTimeMillis) : null;

  // A subscription that has a stated expiry in the past is not currently active.
  if (expiry !== null && expiry <= Date.now()) {
    return { ok: false, reason: "expired" };
  }

  return {
    ok: true,
    expiryTimeMillis: expiry,
    purchaseTimeMillis: data.startTimeMillis ? Number(data.startTimeMillis) : null,
    autoRenewing: data.autoRenewing ?? null,
    paymentState: data.paymentState ?? null,
  };
}

// ── Google Cloud Play Integrity API — decode an integrity token ───────────────────

export interface IntegrityVerification {
  ok: boolean;
  reason?: string;
  requestPackageName?: string;
  packageName?: string;
  appRecognitionVerdict?: string;
  deviceRecognitionVerdicts?: string[];
  timestampMillis?: number | null;
}

export async function decodeIntegrityToken(args: {
  packageName: string;
  integrityToken: string;
}): Promise<IntegrityVerification> {
  const token = await cloudPlatformToken();
  const url =
    `https://integrity.googleapis.com/v1/${encodeURIComponent(args.packageName)}` +
    ":decodeIntegrityToken";

  const res = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ integrity_token: args.integrityToken }),
  });

  if (!res.ok) {
    const body = (await res.text().catch(() => "")).slice(0, 300);
    throw new Error(`Play Integrity decode error ${res.status}: ${body}`);
  }

  const data = (await res.json()) as {
    tokenPayloadExternal?: {
      requestDetails?: {
        requestPackageName?: string;
        timestampMillis?: string;
      };
      appIntegrity?: {
        appRecognitionVerdict?: string;
        packageName?: string;
        appSigningCertificateSha256?: string[];
        certificateSha256Digest?: string[];
      };
      deviceIntegrity?: {
        deviceRecognitionVerdict?: string[];
      };
    };
  };

  const payload = data.tokenPayloadExternal ?? {};
  const requestPackage = payload.requestDetails?.requestPackageName;
  const verdict = payload.appIntegrity?.appRecognitionVerdict;
  const ts = payload.requestDetails?.timestampMillis
    ? Number(payload.requestDetails.timestampMillis)
    : null;

  return {
    ok: false, // filled in by the caller after policy checks
    reason: "pending",
    requestPackageName: requestPackage,
    packageName: payload.appIntegrity?.packageName,
    appRecognitionVerdict: verdict,
    deviceRecognitionVerdicts: payload.deviceIntegrity?.deviceRecognitionVerdict,
    timestampMillis: ts,
  };
}
