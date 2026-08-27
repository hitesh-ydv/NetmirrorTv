# =======================================================================
# General R8 / Optimization & Debugging Rules
# =======================================================================

# Preserve line numbers and source file names for readable crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve essential annotations, generic signatures and inner classes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# Suppress harmless build-time warnings from standard / optional libraries
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn javax.lang.model.**
-dontwarn java.lang.invoke.**

# =======================================================================
# Gson & Network DTO Models
# =======================================================================

-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# Keep all data models / responses serialized and deserialized by Gson
-keep class com.netmirror.netmirrortv.model.** { *; }
-keep class com.yourapp.data.model.** { *; }
-keep class com.netmirror.netmirrortv.ui.home.HomeResponse { *; }
-keep class com.netmirror.netmirrortv.ui.home.HomePost { *; }
-keep class com.netmirror.netmirrortv.ui.home.SliderItem { *; }
-keep class com.netmirror.netmirrortv.ui.ott.OttItem { *; }
-keep class com.netmirror.netmirrortv.ui.ott.OttResponse { *; }
-keep class com.netmirror.netmirrortv.ui.otp.OtpResponse { *; }
-keep class com.netmirror.netmirrortv.ui.otp.PubMsgImg { *; }

# Keep any fields annotated with @SerializedName across the whole codebase
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep custom Gson adapters and factories if used
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# =======================================================================
# Retrofit 2 & OkHttp 3
# =======================================================================

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# =======================================================================
# Kotlin Coroutines & Flow
# =======================================================================

-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# =======================================================================
# Media3 / ExoPlayer
# =======================================================================

-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# =======================================================================
# Coil Image Loader
# =======================================================================

-dontwarn coil.**
-keep class coil.** { *; }

# =======================================================================
# AndroidX & Compose TV
# =======================================================================

-keep class androidx.tv.** { *; }
-dontwarn androidx.tv.**