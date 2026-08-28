# Google AdMob - Suppress missing class warnings
-dontwarn android.media.LoudnessCodecController**
-dontwarn android.media.LoudnessCodecController$**
-dontwarn com.google.android.gms.internal.ads.**
-keep class com.google.android.gms.internal.ads.** { *; }
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.common.** { *; }

# FFmpeg-kit
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**
-dontwarn com.arthenica.smartexception.**
-keep class com.arthenica.smartexception.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep data classes for serialization
-keep class com.snapsave.app.** { *; }
-keep class com.snapsave.app.PlatformInfo { *; }
-keep class com.snapsave.app.FormatChoice { *; }
-keep class com.snapsave.app.DownloadState { *; }
-keep class com.snapsave.app.InstagramExtractor { *; }
-keep class com.snapsave.app.ThreadsExtractor { *; }
-keep class com.snapsave.app.InstagramExtractor$InstagramInfo { *; }
-keep class com.snapsave.app.ThreadsExtractor$ThreadsInfo { *; }

# Chaquopy (Python)
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# yt-dlp
-keep class com.snapsave.app.YtDlpRunner { *; }

# Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**

# Material Design
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
