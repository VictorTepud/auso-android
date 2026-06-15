# AUSO Android ProGuard Rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.auso.social.network.model.** { *; }

# Rust JNI
-keepclasseswithmembernames class * {
    native <methods>;
}
