# Keep all data classes annotated with @Serializable so kotlinx-serialization
# can find them after R8 minification.
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault, *Annotation*

-keep,includedescriptorclasses class com.kakeibo.android.**$$serializer { *; }
-keepclassmembers class com.kakeibo.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.kakeibo.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }

# Retrofit
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
