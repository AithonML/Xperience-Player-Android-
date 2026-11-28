# Xperience Player R8 / ProGuard rules

# Google Cast Framework (manifest-declared OptionsProvider instantiated via reflection)
-keep class com.xperience.player.service.cast.CastOptionsProvider { *; }
-keep class * implements com.google.android.gms.cast.framework.OptionsProvider { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.xperience.player.**$$serializer { *; }
-keepclassmembers class com.xperience.player.** {
    *** Companion;
}
-keepclasseswithmembers class com.xperience.player.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# org.json & reflective model/widget classes
-keep class org.json.** { *; }
-keep class com.xperience.player.widget.**WidgetProvider { *; }
-keep class com.xperience.player.widget.WidgetActionReceiver { *; }
-keep class com.xperience.player.XperienceApplication { *; }
# Strip verbose, debug, and info logs in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
