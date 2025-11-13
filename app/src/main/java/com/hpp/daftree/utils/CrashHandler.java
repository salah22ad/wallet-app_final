package com.hpp.daftree.utils;

import android.content.Context;
import android.util.Log;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import java.io.PrintWriter;
import java.io.StringWriter;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;
    private static CrashHandler instance;
    private final FirebaseCrashlytics crashlytics;

    public CrashHandler(Context context) {
        this.context = context;
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        this.crashlytics = FirebaseCrashlytics.getInstance();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            // تسجيل معلومات إضافية عن السياق
            logCustomData("APP_STATE", "Crash occurred in thread: " + thread.getName());

            // تسجيل الخطأ الرئيسي
            logException(throwable, "UNCAUGHT_EXCEPTION");

            Log.e("CrashHandler", "Uncaught exception in thread: " + thread.getName(), throwable);
        } catch (Exception e) {
            Log.e("CrashHandler", "Error while reporting crash", e);
        } finally {
            // إعادة معالجة الاستثناء بواسطة النظام الافتراضي
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            } else {
                // إذا لم يكن هناك معالج افتراضي، إنهاء التطبيق
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        }
    }

    /**
     * تسجيل أي خطأ تم التقاطه (Caught Exceptions)
     */
    public void logCaughtException(Throwable throwable, String tag) {
        try {
            logException(throwable, "CAUGHT_EXCEPTION_" + tag);
            Log.w("CrashHandler", "Caught exception [" + tag + "]: ", throwable);
        } catch (Exception e) {
            Log.e("CrashHandler", "Failed to log caught exception", e);
        }
    }

    /**
     * تسجيل أي خطأ تم التقاطه بدون tag
     */
    public void logCaughtException(Throwable throwable) {
        logCaughtException(throwable, "GENERAL");
    }

    /**
     * تسجيل بيانات مخصصة للتصحيح
     */
    public void logCustomData(String key, String value) {
        try {
            crashlytics.setCustomKey(key, value);
            Log.d("CrashHandler", "Custom data - " + key + ": " + value);
        } catch (Exception e) {
            Log.e("CrashHandler", "Failed to log custom data", e);
        }
    }

    /**
     * تسجيل رسالة نصية للتصحيح
     */
    public void logMessage(String message) {
        try {
            crashlytics.log("📝 " + message);
            Log.d("CrashHandler", "Message: " + message);
        } catch (Exception e) {
            Log.e("CrashHandler", "Failed to log message", e);
        }
    }

    /**
     * الدالة الأساسية لتسجيل الاستثناءات
     */
    private void logException(Throwable throwable, String type) {
        try {
            // تسجيل نوع الخطأ
            crashlytics.setCustomKey("EXCEPTION_TYPE", type);
            crashlytics.setCustomKey("TIMESTAMP", String.valueOf(System.currentTimeMillis()));

            // تسجيل معلومات إضافية عن التطبيق
            crashlytics.setCustomKey("APP_VERSION", getAppVersion());
            crashlytics.setCustomKey("ANDROID_VERSION", android.os.Build.VERSION.RELEASE);
            // تسجيل الـ stack trace كامل
            crashlytics.log("🎯 Exception Type: " + type);
            crashlytics.log("📱 Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            crashlytics.log("🤖 Android: " + android.os.Build.VERSION.RELEASE + " SDK: " + android.os.Build.VERSION.SDK_INT);

            // تسجيل الاستثناء في Crashlytics
            crashlytics.recordException(throwable);

        } catch (Exception e) {
            Log.e("CrashHandler", "Failed to record exception in Crashlytics", e);
        }
    }

    /**
     * الحصول على إصدار التطبيق
     */
    private String getAppVersion() {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .versionName;
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * تحويل الـ StackTrace إلى نص
     */
    public static String getStackTraceString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * التهيئة - النمط Singleton
     */
    public static void init(Context context) {
        if (instance == null) {
            instance = new CrashHandler(context.getApplicationContext());
            Thread.setDefaultUncaughtExceptionHandler(instance);
        }
    }

    /**
     * الحصول على النسخة (للاستخدام من أي مكان في التطبيق)
     */
    public static CrashHandler getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CrashHandler must be initialized first");
        }
        return instance;
    }

    /**
     * تفعيل/تعطيل Crashlytics (مفيد لوضع التطوير)
     */
    public void setCrashlyticsEnabled(boolean enabled) {
        crashlytics.setCrashlyticsCollectionEnabled(enabled);
    }
}