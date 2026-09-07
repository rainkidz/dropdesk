import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

// ── Release signing (never hardcoded: env vars → local.properties) ─────────
// CI passes RELEASE_KEYSTORE_* env vars (populated from GitHub Secrets); local
// dev can put release.* keys in android-app/local.properties (gitignored).
// Keystore paths are relative to android-app/ (like sdk.dir in local.properties)
// or absolute.
fun localProperty(name: String): String? {
    val props = Properties()
    val f = rootProject.file("local.properties")
    if (!f.exists()) return null
    f.inputStream().use { props.load(it) }
    return props.getProperty(name)
}

val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_FILE") ?: localProperty("release.keystore.path")
val releaseKeystorePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: localProperty("release.keystore.password")
val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: localProperty("release.key.alias")
val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: localProperty("release.key.password")
val releaseSigningConfigured = !releaseKeystorePath.isNullOrBlank() &&
    !releaseKeystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

// Relative paths resolve against android-app/ (rootProject), matching the
// sdk.dir convention in local.properties; absolute paths pass through.
fun resolveKeystoreFile(raw: String): File =
    if (raw.startsWith("/") || raw.startsWith("\\") || raw.length >= 2 && raw[1] == ':') {
        file(raw)
    } else {
        rootProject.file(raw)
    }

fun loadReleaseKeystore(): KeyStore {
    val keystoreFile = resolveKeystoreFile(releaseKeystorePath!!)
    if (!keystoreFile.exists()) {
        throw GradleException("Release keystore not found: $keystoreFile — check RELEASE_KEYSTORE_FILE / release.keystore.path")
    }
    var lastError: Exception? = null
    for (type in listOf("PKCS12", "JKS")) {
        try {
            val ks = KeyStore.getInstance(type)
            keystoreFile.inputStream().use { ks.load(it, releaseKeystorePassword!!.toCharArray()) }
            return ks
        } catch (e: Exception) {
            lastError = e
        }
    }
    throw GradleException("Cannot read release keystore $keystoreFile with the configured password", lastError)
}

/**
 * SHA-256 (hex, uppercase) of the release signing certificate, injected into
 * BuildConfig.RELEASE_CERT_SHA256_HEX so SecurityGuard always checks the key
 * that actually signed the APK — rotating the keystore needs no code change.
 * Empty when no signing key is configured (check then disabled, like today).
 */
val releaseCertSha256Hex: String = if (releaseSigningConfigured) {
    val ks = loadReleaseKeystore()
    val cert = ks.getCertificate(releaseKeyAlias!!)
        ?: throw GradleException("Alias '$releaseKeyAlias' not found in release keystore")
    MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        .joinToString("") { "%02X".format(it) }
} else {
    ""
}

android {
    namespace = "com.tubenime.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tubenime.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 9
        versionName = "4.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "RELEASE_CERT_SHA256_HEX", "\"$releaseCertSha256Hex\"")

        ndk {
            // arm64-v8a only: covers 99%+ of real Android devices
            // Removing x86_64 saves ~35MB (native libs duplicated per ABI)
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = resolveKeystoreFile(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Refuse to emit an unsigned "release" APK (it would ship with the integrity
// check disabled) when no signing key is configured.
if (!releaseSigningConfigured) {
    tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
        doFirst {
            throw GradleException(
                "Release signing is not configured. Set RELEASE_KEYSTORE_FILE / " +
                    "RELEASE_KEYSTORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD " +
                    "(env, e.g. GitHub Secrets) or release.keystore.path / release.keystore.password / " +
                    "release.key.alias / release.key.password in android-app/local.properties."
            )
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"
        pip {
            install("yt-dlp")
        }
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Material Design 3
    implementation("com.google.android.material:material:1.11.0")

    // Networking - OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.json:json:20231013")

    // Image Loading - Coil
    implementation("io.coil-kt:coil:2.5.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // FFmpeg-kit (maintained fork) - provides ffmpeg binary for Android
    // Needed by yt-dlp to merge separate video+audio streams for 1080p+
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-min:8.1.7")

    // Google AdMob - for monetization (compatible with Kotlin 1.9)
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    // Google Play Billing - premium subscriptions (compatible with compileSdk 34 / AGP 8.2)
    implementation("com.android.billingclient:billing:6.2.1")

    // Google Play Integrity API - server-side install attestation (anti-mod hardening)
    implementation("com.google.android.play:integrity:1.2.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
