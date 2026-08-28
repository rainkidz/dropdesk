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
-keep class com.snapsave.app.** { *; }
-keep class com.google.gson.** { *; }

# Keep data classes
-keep class com.snapsave.app.PlatformInfo { *; }
-keep class com.snapsave.app.FormatChoice { *; }
-keep class com.snapsave.app.DownloadState { *; }
