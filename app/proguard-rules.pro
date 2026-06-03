# ─── GeckoView ────────────────────────────────────────────────────────────────
# GeckoView has a large native (C++) side that calls back into Java via JNI.
# R8/ProGuard MUST NOT rename or strip these classes/methods; if it does, the
# native "launcher" thread dereferences a null JNI method pointer → SIGSEGV
# at address 0x0 immediately after GeckoThread reaches JNI_READY.

# Keep the entire public GeckoView API surface
-keep class org.mozilla.geckoview.** { *; }
-keep interface org.mozilla.geckoview.** { *; }

# Keep GeckoView's internal package (JNI bridge used by libxul.so / libmozglue.so)
-keep class org.mozilla.gecko.** { *; }
-keep interface org.mozilla.gecko.** { *; }

# Preserve annotations used for native method binding
-keepattributes *Annotation*

# Keep all native methods so C++ can call back into Java correctly
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve source/line info for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ─── App classes ──────────────────────────────────────────────────────────────
# Keep our Activity/Preference classes referenced from the manifest and layouts
-keep class aiv.ashivered.safebrowser.** { *; }
-keep class com.ashivered.aiv.jtech.** { *; }

# ─── Suppress warnings from unused transitive deps ────────────────────────────
-dontwarn java.beans.**
-dontwarn org.yaml.snakeyaml.**