# Kadb / ADB protocol
-keep class com.flyfishxu.kadb.** { *; }
-dontwarn com.flyfishxu.kadb.**

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
-dontwarn java.lang.management.**

# Conscrypt / HiddenApiBypass / SPAKE2
-dontwarn org.conscrypt.**
-dontwarn org.lsposed.hiddenapibypass.**
-dontwarn com.flyfish233.**
-keep class org.lsposed.hiddenapibypass.** { *; }

# Okio
-dontwarn okio.**
-dontwarn org.codehaus.mojo.animal_sniffer.*

# EzADB core models are plain Kotlin; keep names for readable logs.
-keepnames class dev.ezadb.core.** { *; }
