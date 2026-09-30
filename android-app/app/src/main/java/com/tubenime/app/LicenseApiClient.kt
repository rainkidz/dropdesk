package com.tubenime.app

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Klien Lapis 2 anti-bajak: aktivasi license per device ke server.
 * Best-effort — bila gagal, license HMAC offline tetap sah.
 */
object LicenseApiClient {

    private const val JSON = "application/json; charset=utf-8"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    sealed interface ActivateResult {
        data object Ok : ActivateResult
        data object LimitReached : ActivateResult
        data class Failed(val reason: String) : ActivateResult
    }

    fun activate(context: Context, licenseKey: String, deviceFingerprint: String): ActivateResult {
        val base = BuildConfig.BILLING_BASE_URL.trimEnd('/')
        if (base.isBlank()) return ActivateResult.Failed("no_billing_base_url")
        return try {
            val body = JSONObject()
                .put("key", licenseKey)
                .put("deviceFingerprint", deviceFingerprint)
                .toString()
                .toRequestBody(JSON.toMediaType())
            val req = Request.Builder().url("$base/api/license/activate").post(body).build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return ActivateResult.Failed("HTTP ${resp.code}")
                val json = JSONObject(text)
                when {
                    json.optBoolean("ok", false) -> ActivateResult.Ok
                    json.optString("reason") == "limit_reached" -> ActivateResult.LimitReached
                    else -> ActivateResult.Failed(json.optString("reason", "unknown"))
                }
            }
        } catch (e: Exception) {
            ActivateResult.Failed(e.message ?: "network_error")
        }
    }
}
