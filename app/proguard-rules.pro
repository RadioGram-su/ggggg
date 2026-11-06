# Гром: без рефлексии, стандартных правил Media3/Compose достаточно.
-keep class su.radiogram.grom.audio.PlaybackService { *; }
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
