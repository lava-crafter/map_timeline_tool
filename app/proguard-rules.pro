# Keep osmdroid to avoid reflection/shading issues
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Keep Room generated classes
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep app classes
-keep class com.lavacrafter.maptimelinetool.** { *; }

# Keep raw resources to prevent R8 from obfuscating their names (needed for getIdentifier in OSS Licenses)
-keep class **.R$raw {
    <fields>;
}

# Keep play-services-oss-licenses generated R fields to prevent R8 from obfuscating them
-keep class com.google.android.gms.oss.licenses.R$** { *; }
-keep class com.lavacrafter.maptimelinetool.R$** { *; }
