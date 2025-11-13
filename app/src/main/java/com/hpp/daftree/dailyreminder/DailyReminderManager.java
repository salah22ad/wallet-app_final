package com.hpp.daftree.dailyreminder;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import  com.hpp.daftree.R;
/**
 * مدير الإشعارات اليومية المحسن
 * يدير إعدادات وحالة الإشعارات اليومية
 */
public class DailyReminderManager {
    private static final String TAG = "DailyReminderManager";
    private static final String PREFS_NAME = "daily_reminder_prefs";
    private static final String PREF_ENABLED = "enabled";
    private static final String PREF_LAST_NOTIFICATION = "last_notification_time";
    private static final String PREF_SCHEDULED = "is_scheduled";

    private static final String PREF_GENERAL_NOTIFICATIONS_ENABLED = "general_notifications_enabled";
    private static DailyReminderManager instance;
    private final Context context;

    private DailyReminderManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized DailyReminderManager getInstance(Context context) {
        if (instance == null) {
            instance = new DailyReminderManager(context);
        }
        return instance;
    }
    /**
     * تفعيل أو إيقاف الإشعارات العامة
     */
    public void setGeneralNotificationsEnabled(boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_GENERAL_NOTIFICATIONS_ENABLED, enabled).apply();

        if (enabled) {
            Log.d(TAG, "تم تفعيل الإشعارات العامة");
        } else {
            Log.d(TAG, "تم إيقاف الإشعارات العامة");
            // إذا تم إيقاف الإشعارات العامة، يتم إيقاف الإشعارات اليومية تلقائياً
//            if (isEnabled()) {
//                setEnabled(false);
//            }
        }
    }

    /**
     * التحقق من تفعيل الإشعارات العامة
     */
    public boolean areGeneralNotificationsEnabled() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_GENERAL_NOTIFICATIONS_ENABLED, true); // مفعلة افتراضياً
    }

    /**
     * تفعيل أو إيقاف الإشعارات اليومية
     */
    public void setEnabled(boolean enabled) {
        // التحقق من أن الإشعارات العامة مفعلة أولاً
        if (enabled && !areGeneralNotificationsEnabled()) {
            Log.w(TAG, "لا يمكن تفعيل الإشعارات اليومية لأن الإشعارات العامة معطلة");
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_ENABLED, enabled).apply();

        if (enabled) {
            // تفعيل الإشعارات - جدولة جديدة
            scheduleReminder();
            Log.d(TAG, "تم تفعيل الإشعارات اليومية");
        } else {
            // إلغاء الإشعارات
            cancelReminder();
            Log.d(TAG, "تم إلغاء الإشعارات اليومية");
        }
    }

    // ... باقي الكود الحالي يبقى كما هو مع إضافة هذا السطر في دالة getStatusDescription()

    /**
     * الحصول على وصف حالة الإشعار
     */
    public String getStatusDescription() {
        if (!areGeneralNotificationsEnabled()) {
            return context.getString(R.string.general_notifications_disabled);
        }

        if (!isEnabled()) {
            return context.getString(R.string.daily_notifications_disabled);
        }

        long minutesUntilNext = getMinutesUntilNextNotification();

        if (minutesUntilNext < 0) {
            return context.getString(R.string.daily_notifications_enabled);
        } else if (minutesUntilNext == 0) {
            return context.getString(R.string.daily_notification_now);
        } else if (minutesUntilNext < 60) {
//            return "الإشعار التالي خلال " + minutesUntilNext + " دقيقة";
            return context.getString(R.string.daily_notification_in_minutes, minutesUntilNext);
        } else {
            long hours = minutesUntilNext / 60;
//            return "الإشعار التالي خلال " + hours + " ساعة";
            return context.getString(R.string.daily_notification_in_hours, hours);
        }
    }
    /**
     * تفعيل أو إيقاف الإشعارات اليومية
     */
    public void setEnabled1(boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_ENABLED, enabled).apply();

        if (enabled) {
            // تفعيل الإشعارات - جدولة جديدة
            scheduleReminder();
            Log.d(TAG, "تم تفعيل الإشعارات اليومية");
        } else {
            // إلغاء الإشعارات
            cancelReminder();
            Log.d(TAG, "تم إلغاء الإشعارات اليومية");
        }
    }

    /**
     * التحقق من تفعيل الإشعارات اليومية
     */
    public boolean isEnabled() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_ENABLED, true); // مفعلة افتراضياً
    }

    /**
     * جدولة الإشعار اليومي
     */
    public void scheduleReminder() {
        try {
            Log.d(TAG, "بدء جدولة الإشعار اليومي");
            DailyReminderScheduler.scheduleDailyReminder(context);
            
            // تسجيل حالة الجدولة
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(PREF_SCHEDULED, true).apply();
            
        } catch (Exception e) {
            Log.e(TAG, "خطأ في جدولة الإشعار اليومي", e);
        }
    }

    /**
     * إلغاء الإشعار اليومي
     */
    public void cancelReminder() {
        try {
            Log.d(TAG, "إلغاء الإشعار اليومي");
            DailyReminderScheduler.cancelScheduledReminder(context);
            
            // إلغاء تسجيل حالة الجدولة
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(PREF_SCHEDULED, false).apply();
            
        } catch (Exception e) {
            Log.e(TAG, "خطأ في إلغاء الإشعار اليومي", e);
        }
    }

    /**
     * إعادة جدولة الإشعار اليومي
     */
    public void rescheduleReminder() {
        try {
            Log.d(TAG, "إعادة جدولة الإشعار اليومي");
            scheduleReminder();
        } catch (Exception e) {
            Log.e(TAG, "خطأ في إعادة جدولة الإشعار", e);
        }
    }

    /**
     * التحقق من أن الإشعار يجب أن يُعرض
     */
    public boolean shouldShowNotification() {
        if (!isEnabled()) {
            return false;
        }

        long lastNotification = getLastNotificationTime();
        long currentTime = System.currentTimeMillis();
        long timeDifference = currentTime - lastNotification;

        // التحقق من أن مر 22 ساعة على الأقل على آخر إشعار
        return timeDifference >= (22 * 60 * 60 * 1000); // 22 ساعة بالميلي ثانية
    }

    /**
     * تسجيل وقت عرض آخر إشعار
     */
    public void markNotificationShown() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(PREF_LAST_NOTIFICATION, System.currentTimeMillis()).apply();
        
        Log.d(TAG, "تم تسجيل وقت عرض آخر إشعار");
    }

    /**
     * الحصول على وقت آخر إشعار
     */
    public long getLastNotificationTime() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(PREF_LAST_NOTIFICATION, 0);
    }

    /**
     * الحصول على الوقت المتبقي للإشعار التالي (بالدقائق)
     */
    public long getMinutesUntilNextNotification() {
        if (!isEnabled()) {
            return -1;
        }

        long nextReminderTime = DailyReminderScheduler.getNextReminderTime(context);
        long currentTime = System.currentTimeMillis();
        long timeDifference = nextReminderTime - currentTime;

        if (timeDifference <= 0) {
            return 0;
        }

        return timeDifference / (60 * 1000); // تحويل إلى دقائق
    }

    /**
     * الحصول على وصف حالة الإشعار
     */
    public String getStatusDescription1() {
        if (!isEnabled()) {
            return "الإشعارات اليومية مُلغاة";
        }

        long minutesUntilNext = getMinutesUntilNextNotification();
        
        if (minutesUntilNext < 0) {
            return "الإشعارات اليومية مُفعلة";
        } else if (minutesUntilNext == 0) {
            return "الإشعار اليومي متاح الآن";
        } else if (minutesUntilNext < 60) {
            return "الإشعار التالي خلال " + minutesUntilNext + " دقيقة";
        } else {
            long hours = minutesUntilNext / 60;
            return "الإشعار التالي خلال " + hours + " ساعة";
        }
    }

    /**
     * إعادة تهيئة الإشعارات عند بدء تشغيل التطبيق
     */
    public void initializeOnAppStart() {
        try {
            Log.d(TAG, "تهيئة الإشعارات اليومية عند بدء التطبيق");

            if (isEnabled()) {
                // إذا كانت مفعلة، تأكد من الجدولة
                boolean isScheduled = isScheduled();
                if (!isScheduled) {
                    Log.d(TAG, "إعادة جدولة الإشعارات - لم تكن مجدولة");
                    scheduleReminder();
                }
            }

            // جدولة معاودة التحقق كل فترة
            schedulePeriodicCheck();

        } catch (Exception e) {
            Log.e(TAG, "خطأ في تهيئة الإشعارات اليومية", e);
        }
    }

    /**
     * التحقق من حالة الجدولة
     */
    private boolean isScheduled() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_SCHEDULED, false);
    }

    /**
     * جدولة فحص دوري للتأكد من حالة الإشعارات
     */
    private void schedulePeriodicCheck() {
        // يمكن إضافة WorkManager للفحص الدوري هنا
        // تبسيط مؤقت - سنفعل هذا في الـ Application class
    }

    /**
     * معالجة تغيير حالة المستخدم (Guest/مسجل)
     */
    public void handleUserStateChange() {
        try {
            if (isEnabled()) {
                // إعادة الجدولة للتأكد من الرسائل المناسبة
                scheduleReminder();
                Log.d(TAG, "تم إعادة جدولة الإشعارات بعد تغيير حالة المستخدم");
            }
        } catch (Exception e) {
            Log.e(TAG, "خطأ في معالجة تغيير حالة المستخدم", e);
        }
    }

    /**
     * الحصول على إحصائيات الإشعارات
     */
    public NotificationStats getNotificationStats() {
        long lastNotification = getLastNotificationTime();
        long currentTime = System.currentTimeMillis();
        long timeSinceLast = currentTime - lastNotification;

        return new NotificationStats(
                isEnabled(),
                timeSinceLast,
                getMinutesUntilNextNotification(),
                shouldShowNotification()
        );
    }

    /**
     * فئة إحصائيات الإشعارات
     */
    public static class NotificationStats {
        public final boolean isEnabled;
        public final long timeSinceLastNotification;
        public final long minutesUntilNext;
        public final boolean shouldShowNow;

        public NotificationStats(boolean isEnabled, long timeSinceLastNotification, 
                                long minutesUntilNext, boolean shouldShowNow) {
            this.isEnabled = isEnabled;
            this.timeSinceLastNotification = timeSinceLastNotification;
            this.minutesUntilNext = minutesUntilNext;
            this.shouldShowNow = shouldShowNow;
        }
    }
}
