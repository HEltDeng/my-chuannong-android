# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-dontwarn com.google.re2j.**
-dontwarn org.jsoup.helper.Re2jRegex**

-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class com.mychuannong.app.core.network.campustools.CampusContentBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.google.gson.stream.** { *; }
-keepclassmembers class * {
    <init>();
}
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Production Retrofit request DTOs use Gson reflection. Keep only their JSON
# fields stable under R8; class names and non-field members may still shrink.
-keepclassmembers class com.mychuannong.app.core.network.campustools.**Request {
    <fields>;
}

# Authentication responses are also created by Gson reflection. Keep this
# narrow response surface so successful Release auth can restore token/user.
# All production API response DTOs below are instantiated by Gson reflection.
# Keeping the concrete classes prevents R8 full-mode horizontal class merging
# and preserves their field names for the HTTPS backend contract.
-keep class com.mychuannong.app.core.network.campustools.ApiEnvelope { *; }
-keep class com.mychuannong.app.core.network.campustools.ApiErrorBody { *; }
-keep class com.mychuannong.app.core.network.campustools.AuthData { *; }
-keep class com.mychuannong.app.core.network.campustools.AuthUser { *; }
-keep class com.mychuannong.app.core.network.campustools.Remote** { *; }
-keep class com.mychuannong.app.core.network.campustools.*Result { *; }
-keep class com.mychuannong.app.core.network.campustools.Reservation** { *; }
-keep class com.mychuannong.app.core.network.campustools.PublicBootstrap { *; }
-keep class com.mychuannong.app.core.network.campustools.VersionCheckResponse { *; }
# Follow settings and notices are instantiated reflectively by Gson as well.
# Without these concrete types R8 full mode removes their constructors/fields,
# so only the minified release fails while debug and backend tests still pass.
-keep class com.mychuannong.app.core.network.campustools.NotificationFollow { *; }
-keep class com.mychuannong.app.core.network.campustools.NotificationFollows { *; }
-keep class com.mychuannong.app.core.network.campustools.NotificationFeed { *; }
-keep class com.mychuannong.app.core.network.campustools.FollowedNotice { *; }
-keep class com.mychuannong.app.**.bean.** { *; }
-keep class com.mychuannong.app.**.model.** { *; }
-keep class com.mychuannong.app.**.dto.** { *; }
-keepclassmembers class com.mychuannong.app.feature.common.data.local.entity.** {
    <fields>;
}

# --- Baseline Profile Installer ---
-keep class androidx.profileinstaller.** { *; }
-keep class com.google.tools.profiler.** { *; }
-dontwarn androidx.profileinstaller.**
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type

# HTTPDNS
-keep class com.tencent.msdk.dns.** { *; }
-dontwarn com.tencent.msdk.dns.**

-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

-keepclassmembers class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

-keepclassmembers class * {
  public <init>();
}
