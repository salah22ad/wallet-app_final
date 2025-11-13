# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Basic optimization and obfuscation
-optimizations !code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# Keep important attributes for debugging and stability
-keepattributes Exceptions,InnerClasses,Signature,EnclosingMethod
-keepattributes *Annotation*

# Preserve line numbers for better crash reporting
-keepattributes SourceFile,LineNumberTable

# Remove logging in release builds (standard practice)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Essential Firebase and Google Play Services
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-dontwarn com.google.firebase.**

# Room Database protection
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* *;
}

# Application-specific classes to keep
-keep class com.hpp.daftree.database.** { *; }
-keep class com.hpp.daftree.models.** { *; }

# Security utilities (keep but don't aggressively obfuscate)
-keep class com.hpp.daftree.utils.SecurityUtils { *; }
-keepclassmembers class com.hpp.daftree.utils.SecurityUtils {
    public static java.lang.String encryptText(java.lang.String, int);
    public static java.lang.String decryptText(java.lang.String, int);
    public static java.lang.String createSecureHash(java.lang.String, java.lang.String);
}

# License management (standard protection)
-keep class com.hpp.daftree.utils.SecureLicenseManager { *; }
-keep class com.hpp.daftree.utils.AdvancedLicenseManager { *; }

# iText PDF library
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# Encrypted SharedPreferences
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# JavaScript interface for WebView (if used)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Serializable and Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Custom view constructors
-keepclassmembers public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(***);
}

# Activity and Fragment lifecycle methods
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}

-keepclassmembers class * extends android.app.Fragment {
    public void *(android.view.View);
}

-keepclassmembers class * extends android.view.View {
    <init>(android.content.Context, android.util.AttributeSet);
    <init>(android.content.Context, android.util.AttributeSet, int);
}

# Support library compatibility
-dontwarn android.**
-dontwarn android.support.**
-dontwarn androidx.**

# Remove unnecessary warnings for common libraries
-dontwarn org.xmlpull.v1.**
-dontwarn org.w3c.dom.**
-dontwarn javax.xml.**
-dontwarn java.lang.**
-dontwarn java.util.**

# Standard ProGuard rules for Android
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# Versioned parcelable
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep relevant resources
-keepclassmembers class **.R$* {
    public static <fields>;
}

# For enumeration classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep generics information
-keepattributes Signature

# Basic obfuscation (moderate level)
-repackageclasses ''

# Remove the following aggressive settings that might cause issues:
# -classobfuscationdictionary dictionary.txt
# -packageobfuscationdictionary dictionary.txt
# -obfuscationdictionary dictionary.txt
# -mergeinterfacesaggressively
# -overloadaggressively
# -adaptclassstrings
# -adaptresourcefilenames
# -adaptresourcefilecontents
# -keepattributes !SourceFile,!LineNumberTable

# Remove all the extensive -dontwarn rules for SpongyCastle and AWT
# as they are unnecessary and might raise suspicions

# Only keep essential warnings suppression
-dontwarn javax.xml.crypto.**
-dontwarn org.spongycastle.**
-dontwarn java.awt.**
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn javax.lang.model.SourceVersion
