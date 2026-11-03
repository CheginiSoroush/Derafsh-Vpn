## Derafsh release hardening (R8 minify + obfuscation)
## Goal: raise the cost of binary-patching the monetization flow while keeping
## every runtime-reflective surface intact.

# Keep line numbers for crash readability (Play Console / local retrace)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Gson reflection over DTOs ---
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep class com.v2ray.ang.dto.** { *; }
-keep class * implements com.v2ray.ang.dto.** { *; }

# --- JNI: Xray core (libv2ray AAR) + hev-socks5-tunnel ---
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-keep class com.v2ray.ang.service.TProxyService { *; }
-keepclasseswithmembernames class * { native <methods>; }

# --- Share-link format parsers (listed/invoked dynamically) ---
-keep class com.v2ray.ang.fmt.** { *; }

# --- WorkManager + services referenced by name ---
-keep class com.v2ray.ang.service.** { *; }
-keep class androidx.work.** { *; }

# --- MMKV (multiprocess storage) ---
-keep class com.tencent.mmkv.** { *; }

# --- Enums used via valueOf on process handoff ---
-keepclassmembers enum com.v2ray.ang.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Compose / coroutines / okhttp ship consumer rules; silence optional refs ---
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Google Mobile Ads SDK ships its own consumer rules; nothing extra needed.

# --- Release log hygiene: debug/info logs carry strategy breadcrumbs ("AdsManager: ...",
# "TapsellAds: ..."), so R8 removes every d()/i() call from release builds entirely.
# LogUtil is a Kotlin object: its methods are INSTANCE methods on the singleton, so the
# rule must NOT say "static" — that would silently match nothing. warn/error stay for
# field diagnosis through the in-app logcat viewer.
-assumenosideeffects class com.v2ray.ang.util.LogUtil {
    void d(...);
    void i(...);
}

# --- Flatten the obfuscated namespace: renamed classes move to the root package, so
# package-name grepping the dex reveals nothing about the monetization/session classes.
-repackageclasses ''
-allowaccessmodification
