package com.tubenime.app

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Klien backend billing otomatis (Midtrans Snap).
 *
 * Alur: create(months) → buka redirectUrl di Custom Tab → user bayar →
 * polling status(orderId) sampai "paid" → licenseKey → redeem lokal.
 * Base URL dari BuildConfig (kosong = fitur nonaktif).
 */
object BillingRepository {

    private const val TAG = "BillingRepository"
    private const val JSON = "application/json; charset=utf-8"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun isEnabled(): Boolean = BuildConfig.BILLING_BASE_URL.isNotBlank()

    private fun base(): String = BuildConfig.BILLING_BASE_URL.trimEnd('/')

    data class CreatedOrder(val orderId: String, val redirectUrl: String)

    sealed interface OrderState {
        data object Pending : OrderState
        data class Paid(val licenseKey: String) : OrderState
        data class Ended(val status: String) : OrderState // failed / expired
        data class Error(val message: String) : OrderState
    }

    fun create(months: Int): Result<CreatedOrder> {
        if (!isEnabled()) return Result.failure(IllegalStateException("billing_disabled"))
        return try {
            val body = JSONObject().put("months", months).toString()
                .toRequestBody(JSON.toMediaType())
            val req = Request.Builder().url("${base()}/api/billing/create").post(body).build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "create HTTP ${resp.code}: $text")
                    return Result.failure(Exception("HTTP ${resp.code}"))
                }
                val json = JSONObject(text)
                Result.success(
                    CreatedOrder(
                        orderId = json.getString("orderId"),
                        redirectUrl = json.getString("redirectUrl"),
                    ),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "create failed", e)
            Result.failure(e)
        }
    }

    fun status(orderId: String): OrderState {
        if (!isEnabled()) return OrderState.Error("billing_disabled")
        return try {
            val req = Request.Builder()
                .url("${base()}/api/billing/status/$orderId").get().build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return OrderState.Error("HTTP ${resp.code}")
                val json = JSONObject(text)
                when (json.optString("status", "pending")) {
                    "paid" -> {
                        val key = json.optString("licenseKey", "")
                        if (key.isNotEmpty()) OrderState.Paid(key)
                        else OrderState.Error("empty_key")
                    }
                    "pending" -> OrderState.Pending
                    else -> OrderState.Ended(json.optString("status", "failed"))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "status failed", e)
            OrderState.Error(e.message ?: "network_error")
        }
    }
}
