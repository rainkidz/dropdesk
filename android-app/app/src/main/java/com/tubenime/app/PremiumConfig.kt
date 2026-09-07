package com.tubenime.app

/**
 * Configuration for the server-side premium enforcement layer.
 *
 * Out of the box the app runs fully offline: premium = a live Play
 * subscription (queried locally) + a valid release signature (SecurityGuard).
 * That already stops casually re-signed "mod premium" APKs.
 *
 * For strong protection against source-recompiled clones you must ALSO point
 * the app at an enforcement server you control:
 *
 * 1. Deploy the api-server (artifacts/api-server) with the Google service
 *    account env vars (see routes/premium.ts) and DON'T share that server.
 * 2. Set [ENFORCEMENT_BASE_URL] to it in your private build.
 * 3. Then every purchase is re-validated against Google Play Developer API and
 *    the install is attested with Play Integrity before premium is granted.
 *
 * Keep the enforcement build private — the repo published here stays at the
 * default (verification disabled).
 */
object PremiumConfig {

    /**
     * Base URL of your entitlement server, e.g. "https://api.example.com".
     * Empty string = server-side purchase verification & attestation disabled.
     */
    const val ENFORCEMENT_BASE_URL = ""

    /** The Android package that the server must confirm purchases belong to. */
    const val ENFORCEMENT_PACKAGE = "com.tubenime.app"

    fun isServerVerificationEnabled(): Boolean = ENFORCEMENT_BASE_URL.isNotBlank()

    fun isAttestationEnabled(): Boolean = ENFORCEMENT_BASE_URL.isNotBlank()

    fun verifyEndpoint(): String = "$ENFORCEMENT_BASE_URL/api/premium/verify"

    fun attestEndpoint(): String = "$ENFORCEMENT_BASE_URL/api/premium/attest"
}
