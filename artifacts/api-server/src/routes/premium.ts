import { Router, type IRouter } from "express";
import { logger } from "../lib/logger";
import * as google from "../lib/google-oauth";

/**
 * Server-side premium enforcement.
 *
 * The Android app (PremiumConfig.ENFORCEMENT_BASE_URL) calls these endpoints
 * when an enforcement server is configured. This server should be deployed
 * privately — do NOT ship it to end users, otherwise the "server you control"
 * property is lost and the whole layer becomes bypassable again.
 *
 * Activation requires a Google service-account credential with:
 *   - Play Developer API enabled   → scope androidpublisher (purchase checks)
 *   - Play Integrity API enabled   → scope cloud-platform (attestation)
 * plus env:
 *   SERVICE_ACCOUNT_JSON | SERVICE_ACCOUNT_FILE
 *   EXPECTED_PACKAGE               (default com.tubenime.app)
 *
 * Without credentials every endpoint answers 501 {ok:false, reason:"server_not_configured"}.
 */

const EXPECTED_PACKAGE = process.env["EXPECTED_PACKAGE"] ?? "com.tubenime.app";
const FRESHNESS_MS = 5 * 60 * 1000; // integrity tokens older than this are rejected

const router: IRouter = Router();

router.get("/premium/config", (_req, res) => {
  res.json({
    enabled: google.isConfigured(),
    packageName: EXPECTED_PACKAGE,
    verificationSupported: google.isConfigured(),
    attestationSupported: google.isConfigured(),
    integrityFreshnessSeconds: FRESHNESS_MS / 1000,
  });
});

// ── POST /api/premium/verify ──────────────────────────────────────────────────────
// Re-validates a Play subscription purchase token with Google Play Developer API.
// Body: { packageName, productId, purchaseToken }
router.post("/premium/verify", async (req, res) => {
  if (!google.isConfigured()) {
    res.status(501).json({ ok: false, reason: "server_not_configured" });
    return;
  }

  const body = (req.body ?? {}) as Record<string, unknown>;
  const packageName = typeof body["packageName"] === "string" ? body["packageName"] : "";
  const productId = typeof body["productId"] === "string" ? body["productId"] : "";
  const purchaseToken = typeof body["purchaseToken"] === "string" ? body["purchaseToken"] : "";

  if (!packageName || !productId || !purchaseToken) {
    res.status(400).json({ ok: false, reason: "missing_fields" });
    return;
  }
  if (packageName !== EXPECTED_PACKAGE) {
    res.status(400).json({ ok: false, reason: "package_mismatch" });
    return;
  }

  try {
    const result = await google.verifySubscriptionPurchase({
      packageName,
      productId,
      purchaseToken,
    });

    if (!result.ok) {
      res.status(200).json({ ok: false, reason: result.reason ?? "invalid_purchase" });
      return;
    }

    res.status(200).json({
      ok: true,
      productId,
      expiresAt: result.expiryTimeMillis
        ? new Date(result.expiryTimeMillis).toISOString()
        : null,
      autoRenewing: result.autoRenewing ?? null,
    });
  } catch (err) {
    logger.warn({ err }, "Premium verify upstream error");
    res.status(502).json({ ok: false, reason: "upstream_error" });
  }
});

// ── POST /api/premium/attest ──────────────────────────────────────────────────────
// Decodes a Play Integrity token and verifies the install is a genuine,
// Play-recognized build of OUR package.
// Body: { packageName, integrityToken, nonce }
router.post("/premium/attest", async (req, res) => {
  if (!google.isConfigured()) {
    res.status(501).json({ ok: false, reason: "server_not_configured" });
    return;
  }

  const body = (req.body ?? {}) as Record<string, unknown>;
  const packageName = typeof body["packageName"] === "string" ? body["packageName"] : "";
  const integrityToken = typeof body["integrityToken"] === "string" ? body["integrityToken"] : "";

  if (!packageName || !integrityToken) {
    res.status(400).json({ ok: false, reason: "missing_fields" });
    return;
  }
  if (packageName !== EXPECTED_PACKAGE) {
    res.status(400).json({ ok: false, reason: "package_mismatch" });
    return;
  }

  try {
    const decoded = await google.decodeIntegrityToken({ packageName, integrityToken });

    const verdict = decoded.appRecognitionVerdict;
    const requestPackage = decoded.requestPackageName;
    const ageMs = decoded.timestampMillis ? Date.now() - decoded.timestampMillis : null;

    if (requestPackage !== EXPECTED_PACKAGE) {
      res.status(200).json({ ok: false, reason: "package_mismatch", verdict });
      return;
    }
    if (ageMs !== null && ageMs > FRESHNESS_MS) {
      res.status(200).json({ ok: false, reason: "stale_token", verdict });
      return;
    }
    if (verdict !== "PLAY_RECOGNIZED") {
      res.status(200).json({
        ok: false,
        reason: verdict ? `unrecognized_version:${verdict}` : "no_verdict",
        verdict,
      });
      return;
    }

    res.status(200).json({
      ok: true,
      packageName: EXPECTED_PACKAGE,
      verdict,
      deviceVerdicts: decoded.deviceRecognitionVerdicts ?? [],
    });
  } catch (err) {
    logger.warn({ err }, "Premium attest upstream error");
    res.status(502).json({ ok: false, reason: "upstream_error" });
  }
});

export default router;
