package com.tubenime.app

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client for the entitlement server (see `artifacts/api-server/src/routes/premium.ts`).
 *
 * Two calls:
 * - [verifySubscription]   POST /api/premium/verify   — server re-validates the
 *   Play purchase token against the Google Play Developer API.
 * - [reportAttestation]    POST /api/premium/attest   — server decodes a Play
 *   Integrity token and reports whether this install is a genuine Play build.
 *
 * All outcomes are explicit so callers can distinguish "server says NO"
 * (Rejected — hard fail) from "server unreachable" (Unreachable — fall back to
 * cached state).
 */
object RemotePremiumVerifier {

    private const val TAG = "RemotePremiumVerifier"
    private const val JSON = "application/json; charset=utf-8"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // ── Verify subscription purchase ──────────────────────────

    sealed class VerifyOutcome {
        /** Purchase is real and active. [expiresAtEpochMs] may be null (no expiry reported). */
        data class Ok(val expiresAtEpochMs: Long?) : VerifyOutcome()

        /** Server inspected the token and refused (invalid / wrong package / expired). */
        data class Rejected(val reason: String) : VerifyOutcome()

        /** Server could not be reached or returned an error — treat as unknown. */
        data class Unreachable(val message: String) : VerifyOutcome()
    }

    fun verifySubscriptionBlocking(
        packageName: String,
        productId: String,
        purchaseToken: String
    ): VerifyOutcome {
        return try {
            val body = JSONObject()
                .put("packageName", packageName)
                .put("productId", productId)
                .put("purchaseToken", purchaseToken)
                .toString()
                .toRequestBody(JSON.toMediaType())

            val request = Request.Builder()
                .url(PremiumConfig.verifyEndpoint())
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "verify HTTP ${response.code}: $text")
                    return VerifyOutcome.Unreachable("HTTP ${response.code}")
                }
                val json = JSONObject(text)
                if (json.optBoolean("ok", false)) {
                    val expiresAt = json.optString("expiresAt", "")
                    val epoch = expiresAt.takeIf { it.isNotBlank() }
                        ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
                    VerifyOutcome.Ok(epoch)
                } else {
                    VerifyOutcome.Rejected(json.optString("reason", "rejected"))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "verify failed", e)
            VerifyOutcome.Unreachable(e.message ?: "network error")
        }
    }

    // ── Play Integrity attestation report ─────────────────────

    sealed class AttestOutcome {
        /** Server decoded the token and this is a genuine Play install. */
        data class Verified(val packageName: String) : AttestOutcome()

        /** Server decoded the token but the install failed Play recognition. */
        data class Rejected(val reason: String) : AttestOutcome()

        /** Attestation could not run (unsupported device / server down). */
        data class Unavailable(val message: String) : AttestOutcome()
    }

    fun reportAttestationBlocking(
        packageName: String,
        integrityToken: String,
        nonce: String
    ): AttestOutcome {
        return try {
            val body = JSONObject()
                .put("packageName", packageName)
                .put("integrityToken", integrityToken)
                .put("nonce", nonce)
                .toString()
                .toRequestBody(JSON.toMediaType())

            val request = Request.Builder()
                .url(PremiumConfig.attestEndpoint())
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "attest HTTP ${response.code}: $text")
                    return AttestOutcome.Unavailable("HTTP ${response.code}")
                }
                val json = JSONObject(text)
                if (json.optBoolean("ok", false)) {
                    AttestOutcome.Verified(json.optString("packageName", packageName))
                } else {
                    AttestOutcome.Rejected(json.optString("reason", "attestation_failed"))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "attest failed", e)
            AttestOutcome.Unavailable(e.message ?: "network error")
        }
    }
}
