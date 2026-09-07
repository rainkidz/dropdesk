package com.tubenime.app

import android.content.Context
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Requests a Play Integrity token for the current install and reports it to the
 * entitlement server for decoding/verification.
 *
 * The token can ONLY be decoded by Google's server-side Play Integrity API, so
 * the client never inspects the verdict itself — the server's response decides
 * whether the install is trusted. When the entitlement server is not configured
 * (PremiumConfig.ENFORCEMENT_BASE_URL empty) this module is never invoked.
 */
object PlayIntegrityReporter {

    private const val TAG = "PlayIntegrityReporter"

    private val executor = Executors.newSingleThreadExecutor()

    interface Callback {
        fun onResult(outcome: RemotePremiumVerifier.AttestOutcome)
    }

    fun requestAndReport(context: Context, packageName: String, callback: Callback) {
        if (!PremiumConfig.isAttestationEnabled()) {
            callback.onResult(RemotePremiumVerifier.AttestOutcome.Unavailable("attestation disabled"))
            return
        }
        executor.execute {
            try {
                val integrityManager = IntegrityManagerFactory.create(context)

                // Fresh random nonce per request — correlated server-side with the
                // decoded token so replaying an old token is pointless.
                val nonce = UUID.randomUUID().toString().replace("-", "")

                val request = IntegrityTokenRequest.builder()
                    .setNonce(nonce)
                    .build()

                integrityManager.requestIntegrityToken(request)
                    .addOnSuccessListener { response ->
                        val outcome = RemotePremiumVerifier.reportAttestationBlocking(
                            packageName = packageName,
                            integrityToken = response.token(),
                            nonce = nonce
                        )
                        Log.i(TAG, "Attestation outcome: $outcome")
                        callback.onResult(outcome)
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Integrity token request failed: ${e.message}", e)
                        callback.onResult(
                            RemotePremiumVerifier.AttestOutcome.Unavailable(e.message ?: "token request failed")
                        )
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Play Integrity unavailable: ${e.message}", e)
                callback.onResult(
                    RemotePremiumVerifier.AttestOutcome.Unavailable(e.message ?: "integrity unavailable")
                )
            }
        }
    }
}
