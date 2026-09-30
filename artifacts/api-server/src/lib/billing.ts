import { createHash, randomUUID } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { logger } from "./logger";
import { makeLicenseKey } from "./license-keys";

/**
 * Billing otomatis via Midtrans Snap (QRIS, e-wallet, VA, gerai, kartu
 * domestik + internasional) untuk distribusi sideload.
 *
 * Alur:
 *  1. App → POST /billing/create {months} → server buat transaksi Snap →
 *     balas {orderId, redirectUrl}.
 *  2. User bayar di halaman Snap (Custom Tab).
 *  3. Midtrans → POST /billing/webhook (signature sha512 diverifikasi) →
 *     order lunas → kode lisensi TN1 diterbitkan otomatis.
 *  4. App polling GET /billing/status/:orderId → terima licenseKey →
 *     redeem lokal via LicenseManager.
 *  5. (Lapis 2 anti-bajak) licenseKey di-ACTIVATE ke server: 1 license
 *     = 1 device fingerprint. App panggil /api/license/activate setelah
 *     redeem HMAC offline. Tanpa internet → tetap jalan (graceful degrade).
 *
 * Env:
 *   MIDTRANS_SERVER_KEY  (wajib; pakai SANDBOX key saat testing)
 *   MIDTRANS_SANDBOX     "true"/"false" (default true = aman)
 *   LICENSE_HMAC_SECRET  (wajib — sama dengan secret aplikasi)
 *   BILLING_MONTHLY_IDR  (default 15000)
 *   LICENSE_MAX_DEVICES  (default 1; naikkan ke 2/3 bila mau toleransi ganti HP)
 */

const SANDBOX = (process.env.MIDTRANS_SANDBOX ?? "true") !== "false";
const SNAP_HOST = SANDBOX
  ? "https://app.sandbox.midtrans.com/snap/v1/transactions"
  : "https://app.midtrans.com/snap/v1/transactions";
const STATUS_HOST = SANDBOX
  ? "https://api.sandbox.midtrans.com/v2"
  : "https://api.midtrans.com/v2";

export function billingConfigured(): boolean {
  return (process.env.MIDTRANS_SERVER_KEY ?? "").length > 10 &&
    (process.env.LICENSE_HMAC_SECRET ?? "").length >= 16;
}

type OrderStatus = "pending" | "paid" | "failed" | "expired";

type Order = {
  orderId: string;
  months: number;
  amountIdr: number;
  status: OrderStatus;
  licenseKey: string | null;
  licenseExpiry: string | null;
  createdAt: string;
  paidAt: string | null;
};

const ordersPath = path.join(os.tmpdir(), "social-downloader", "billing-orders.json");
const orders = new Map<string, Order>();

async function persistOrders(): Promise<void> {
  try {
    await mkdir(path.dirname(ordersPath), { recursive: true });
    await writeFile(ordersPath, JSON.stringify([...orders.values()], null, 2), "utf-8");
  } catch (error) {
    logger.warn({ err: error }, "Failed to persist billing orders");
  }
}

async function loadOrders(): Promise<void> {
  try {
    const raw = await readFile(ordersPath, "utf-8");
    for (const o of JSON.parse(raw) as Order[]) orders.set(o.orderId, o);
  } catch (error) {
    if ((error as NodeJS.ErrnoException)?.code !== "ENOENT") {
      logger.warn({ err: error }, "Failed to load billing orders");
    }
  }
}

function monthlyIdr(): number {
  const v = Number(process.env.BILLING_MONTHLY_IDR ?? "15000");
  return Number.isFinite(v) && v > 0 ? Math.floor(v) : 15000;
}

function snapAuth(): string {
  return `Basic ${Buffer.from(`${process.env.MIDTRANS_SERVER_KEY}:`).toString("base64")}`;
}

export async function createOrder(months: number): Promise<{ orderId: string; redirectUrl: string }> {
  if (!billingConfigured()) throw new Error("billing_not_configured");
  const cleanMonths = [1, 3, 6, 12].includes(months) ? months : 1;
  const amount = monthlyIdr() * cleanMonths;
  const orderId = `TN-${Date.now().toString(36).toUpperCase()}-${randomUUID().slice(0, 6).toUpperCase()}`;

  const resp = await fetch(SNAP_HOST, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: snapAuth() },
    body: JSON.stringify({
      transaction_details: { order_id: orderId, gross_amount: amount },
      item_details: [{ id: `premium-${cleanMonths}m`, price: amount, quantity: 1, name: `TubeNime Premium ${cleanMonths} bulan` }],
      // Batasi ke metode populer agar halaman Snap ringkas.
      enabled_payments: ["qris", "gopay", "shopeepay", "dana", "ovo", "bank_transfer", "echannel", "cstore", "credit_card"],
    }),
    signal: AbortSignal.timeout(20_000),
  });
  if (!resp.ok) {
    const text = await resp.text().catch(() => "");
    logger.warn({ status: resp.status, text: text.slice(0, 300) }, "Midtrans Snap create failed");
    throw new Error("snap_create_failed");
  }
  const data = (await resp.json()) as { token?: string; redirect_url?: string };
  if (!data.redirect_url) throw new Error("snap_create_failed");

  orders.set(orderId, {
    orderId, months: cleanMonths, amountIdr: amount,
    status: "pending", licenseKey: null, licenseExpiry: null,
    createdAt: new Date().toISOString(), paidAt: null,
  });
  void persistOrders();
  return { orderId, redirectUrl: data.redirect_url };
}

export function getOrder(orderId: string): Order | undefined {
  return orders.get(orderId);
}

/** Refresh status dari Midtrans bila masih pending (fallback selain webhook). */
export async function refreshOrderStatus(orderId: string): Promise<Order | undefined> {
  const order = orders.get(orderId);
  if (!order || order.status !== "pending") return order;
  try {
    const resp = await fetch(`${STATUS_HOST}/${encodeURIComponent(orderId)}/status`, {
      headers: { Authorization: snapAuth() },
      signal: AbortSignal.timeout(15_000),
    });
    if (!resp.ok) return order;
    const data = (await resp.json()) as { transaction_status?: string; fraud_status?: string };
    applyGatewayStatus(order, data.transaction_status, data.fraud_status);
    void persistOrders();
  } catch (error) {
    logger.warn({ err: error, orderId }, "Midtrans status check failed");
  }
  return order;
}

function applyGatewayStatus(order: Order, tx?: string, fraud?: string): void {
  if (tx === "capture" && fraud !== "deny" && fraud !== "challenge") return markPaid(order);
  if (tx === "settlement") return markPaid(order);
  if (tx === "deny" || tx === "cancel" || tx === "expire") {
    order.status = tx === "expire" ? "expired" : "failed";
  }
}

function markPaid(order: Order): void {
  if (order.status === "paid") return;
  const secret = process.env.LICENSE_HMAC_SECRET ?? "";
  const { key, expiry } = makeLicenseKey(secret, order.months);
  order.status = "paid";
  order.licenseKey = key;
  order.licenseExpiry = expiry;
  order.paidAt = new Date().toISOString();
  logger.info({ orderId: order.orderId, expiry }, "Order paid, license issued");
}

/** Verifikasi signature webhook Midtrans (sha512 hex). */
export function verifyWebhookSignature(body: Record<string, string>): boolean {
  const { order_id, status_code, gross_amount, signature_key } = body;
  if (!order_id || !status_code || !gross_amount || !signature_key) return false;
  const expected = createHash("sha512")
    .update(`${order_id}${status_code}${gross_amount}${process.env.MIDTRANS_SERVER_KEY ?? ""}`)
    .digest("hex");
  if (expected.length !== signature_key.length) return false;
  let diff = 0;
  for (let i = 0; i < expected.length; i++) diff |= expected.charCodeAt(i) ^ signature_key.charCodeAt(i);
  return diff === 0;
}

export function applyWebhook(body: Record<string, string>): Order | undefined {
  const order = orders.get(body.order_id);
  if (!order) return undefined;
  applyGatewayStatus(order, body.transaction_status, body.fraud_status);
  void persistOrders();
  return order;
}

void loadOrders();
