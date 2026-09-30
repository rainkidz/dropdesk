import { Router, type IRouter } from "express";
import {
  billingConfigured,
  createOrder,
  getOrder,
  refreshOrderStatus,
  verifyWebhookSignature,
  applyWebhook,
} from "../lib/billing";
import { activateDevice, heartbeatDevice } from "../lib/license-activations";
import { verifyLicenseKey, maskLicenseKey } from "../lib/license-verify";
import { buildSignedPublicConfig } from "../lib/public-config";

const router: IRouter = Router();

router.get("/billing/config", (_req, res) => {
  res.json({
    enabled: billingConfigured(),
    monthlyIdr: Number(process.env.BILLING_MONTHLY_IDR ?? "15000"),
    sandbox: (process.env.MIDTRANS_SANDBOX ?? "true") !== "false",
  });
});

// App → buat transaksi. Body: { months?: 1|3|6|12 }
router.post("/billing/create", async (req, res) => {
  if (!billingConfigured()) {
    res.status(501).json({ error: "Billing belum dikonfigurasi di server." });
    return;
  }
  const months = Number((req.body as Record<string, unknown> | undefined)?.months ?? 1);
  try {
    res.json(await createOrder(months));
  } catch (error) {
    req.log.warn({ err: error }, "Billing create failed");
    res.status(502).json({ error: "Gagal membuat pembayaran. Coba lagi." });
  }
});

// App polling. → { status, licenseKey?, licenseExpiry? }
router.get("/billing/status/:orderId", async (req, res) => {
  const order = await refreshOrderStatus(req.params.orderId);
  if (!order) {
    res.status(404).json({ error: "Order tidak ditemukan." });
    return;
  }
  res.json({
    orderId: order.orderId,
    status: order.status,
    licenseKey: order.status === "paid" ? order.licenseKey : null,
    licenseExpiry: order.status === "paid" ? order.licenseExpiry : null,
  });
});

// Webhook Midtrans (signature wajib valid).
router.post("/billing/webhook", (req, res) => {
  const body = (req.body ?? {}) as Record<string, string>;
  if (!verifyWebhookSignature(body)) {
    res.status(401).json({ error: "invalid signature" });
    return;
  }
  const order = applyWebhook(body);
  res.json({ ok: true, orderId: order?.orderId ?? body.order_id ?? null });
});

// ── Lapis 2 anti-bajak: registrasi lisensi per device ─────────────────────

router.post("/license/activate", async (req, res) => {
  const body = (req.body ?? {}) as Record<string, unknown>;
  const key = typeof body.key === "string" ? body.key.trim() : "";
  const fingerprint = typeof body.deviceFingerprint === "string" ? body.deviceFingerprint.trim() : "";
  if (!key || !fingerprint || fingerprint.length < 16 || fingerprint.length > 256) {
    res.status(400).json({ ok: false, reason: "missing_fields" });
    return;
  }
  const verify = verifyLicenseKey(key);
  if (!verify.ok) {
    res.status(200).json({ ok: false, reason: verify.reason });
    return;
  }
  const result = await activateDevice(key, fingerprint, verify.expiryYyyymm, true);
  req.log.info({ key: maskLicenseKey(key), reason: result.ok ? "ok" : result.reason }, "license activate");
  res.json(result);
});

router.post("/license/heartbeat", async (req, res) => {
  const body = (req.body ?? {}) as Record<string, unknown>;
  const key = typeof body.key === "string" ? body.key.trim() : "";
  const fingerprint = typeof body.deviceFingerprint === "string" ? body.deviceFingerprint.trim() : "";
  if (!key || !fingerprint) {
    res.status(400).json({ ok: false, reason: "missing_fields" });
    return;
  }
  const ok = await heartbeatDevice(key, fingerprint);
  res.json({ ok });
});

// Konfigurasi publik (AdMob IDs dinamis + signature check).
// Respons ditandatangani HMAC-SHA256(LICENSE_HMAC_SECRET, canonical(body));
// klien memverifikasi sebelum memakai IDs (lihat Android AdsManager).
router.get("/config/public", (_req, res) => {
  res.json(buildSignedPublicConfig());
});

export default router;
