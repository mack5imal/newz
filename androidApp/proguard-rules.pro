# R8 rules for the Newz release build (minify + resource shrinking).
# kotlinx-serialization, Ktor, OkHttp, Coil and SQLDelight ship their own consumer
# rules; these cover what those rules leave to the app.

# Keep line numbers so a crash report from the owner's phone can be mapped back.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx-serialization: the app's @Serializable models (prefs, store, backup) ---
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class app.newz.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class app.newz.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.newz.**$$serializer { *; }
-keepclassmembers class app.newz.** {
    *** Companion;
}

# --- SQLDelight: generated database/queries are plain Kotlin; keep the schema entry point ---
-keep class app.newz.db.** { *; }
-keep class app.cash.sqldelight.driver.android.** { *; }

# --- Ktor / OkHttp: optional platform classes referenced but absent on Android.
# The OkHttp engine is created explicitly (net/Engine.android.kt), so no ServiceLoader keep. ---
-dontwarn io.ktor.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn java.lang.invoke.StringConcatFactory

# --- RSS-Parser uses XmlPullParser from the platform ---
-dontwarn org.xmlpull.v1.**
-keep class org.xmlpull.v1.** { *; }
