package com.hpp.daftree.dailyreminder;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.RequiresPermission;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.hpp.daftree.utils.SecureLicenseManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * مستقبل الإشعارات اليومية المحسن
 * يتعامل مع AlarmManager وWorkManager ويتأكد من عرض الإشعار بدقة
 */
public class DailyReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "DailyReminderReceiver";
    private static final String WORKER_ACTION = "DAFTREE_WORKER_REMINDER";
    private static final String ALARM_ACTION = "DAFTREE_DAILY_REMINDER";
    private static final String DISMISS_ACTION = "DISMISS_NOTIFICATION";

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "تم استقبال Broadcast للإجراء: " + action);

        try {
            if (WORKER_ACTION.equals(action)) {
                // تم استدعاؤه من WorkManager
                handleWorkManagerTrigger(context, intent);
            } else if (ALARM_ACTION.equals(action)) {
                // تم استدعاؤه من AlarmManager
                handleAlarmManagerTrigger(context, intent);
            } else if (DISMISS_ACTION.equals(action)) {
                // طلب إلغاء الإشعار
                handleDismissAction(context, intent);
            } else {
                Log.w(TAG, "إجراء غير معروف: " + action);
                // إعادة الجدولة في حالة الخطأ
                try {
                    scheduleWorkManagerReminder(context);
                } catch (Exception reScheduleException) {
                    Log.e(TAG, "خطأ في إعادة الجدولة", reScheduleException);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "خطأ في معالجة Broadcast", e);
            
            // إعادة الجدولة في حالة الخطأ
            try {
                DailyReminderScheduler.rescheduleDailyReminder(context);
            } catch (Exception reScheduleException) {
                Log.e(TAG, "خطأ في إعادة الجدولة", reScheduleException);
            }
        }
    }
    /**
     * جدولة الإشعار باستخدام WorkManager
     */
    private static void scheduleWorkManagerReminder(Context context) {
        // حساب الوقت حتى 8:30 مساءً
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, 20); // 8 PM
        calendar.set(Calendar.MINUTE, 30);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long currentTime = System.currentTimeMillis();
        long targetTime = calendar.getTimeInMillis();

        long initialDelay = targetTime - currentTime;

        // إذا كان الوقت الحالي بعد 8:30 مساءً، نضيف يوم
        if (initialDelay < 0) {
            initialDelay += TimeUnit.DAYS.toMillis(1);
        }

        // قيود مرنة لضمان التشغيل
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .build();

        // إنشاء WorkRequest محسن
        PeriodicWorkRequest.Builder workRequestBuilder = new PeriodicWorkRequest.Builder(
                DailyReminderWorker.class,
                1, // كل يوم
                TimeUnit.DAYS,
                15, // فترة مرونة 15 دقيقة
                TimeUnit.MINUTES
        )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints);



        PeriodicWorkRequest workRequest = workRequestBuilder.build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "DailyReminderWork",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
        );

        Log.d(TAG, "تم جدولة WorkManager بنجاح");
    }

    /**
     * معالجة الاستدعاء من WorkManager
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private void handleWorkManagerTrigger(Context context, Intent intent) {
        Log.d(TAG, "تم استدعاء الإشعار من WorkManager");

        try {
            // التحقق من أن الإشعار يجب أن يُعرض
            if (!shouldShowReminder(context)) {
                Log.d(TAG, "تم تخطي الإشعار - تم عرضه بالفعل اليوم");
                DailyReminderScheduler.rescheduleDailyReminder(context);
                return;
            }

            // التحقق من تفعيل الإشعارات
            if (!areNotificationsEnabled(context)) {
                Log.w(TAG, "الإشعارات غير مفعلة");
                DailyReminderScheduler.rescheduleDailyReminder(context);
                return;
            }

            // الحصول على حالة المستخدم
            boolean isGuest = SecureLicenseManager.getInstance(context).isGuest();

            // إنشاء خدمة الإشعارات وعرض الإشعار
            DailyReminderNotification notificationService = 
                    new DailyReminderNotification(context);
            
            notificationService.showDailyReminderNotification(isGuest);

            // تسجيل وقت عرض الإشعار
            markReminderShown(context);

            Log.d(TAG, "تم عرض الإشعار بنجاح من WorkManager");

        } catch (Exception e) {
            Log.e(TAG, "خطأ في عرض الإشعار من WorkManager", e);
        } finally {
            // إعادة الجدولة دائماً
            DailyReminderScheduler.rescheduleDailyReminder(context);
        }
    }

    /**
     * معالجة الاستدعاء من AlarmManager
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private void handleAlarmManagerTrigger(Context context, Intent intent) {
        Log.d(TAG, "تم استدعاء الإشعار من AlarmManager");

        try {
            // التحقق من أن الإشعار يجب أن يُعرض
            if (!shouldShowReminder(context)) {
                Log.d(TAG, "تم تخطي الإشعار - تم عرضه بالفعل اليوم");
                return;
            }

            // التحقق من تفعيل الإشعارات
            if (!areNotificationsEnabled(context)) {
                Log.w(TAG, "الإشعارات غير مفعلة");
                return;
            }

            // الحصول على حالة المستخدم
            boolean isGuest = SecureLicenseManager.getInstance(context).isGuest();

            // إنشاء خدمة الإشعارات
            DailyReminderNotification notificationService = 
                    new DailyReminderNotification(context);
            
            // التحقق من حالة الجهاز وعرض الإشعار المناسب
            if (isDeviceLocked(context)) {
                // للجهاز المقفل، استخدم إشعار ملء الشاشة
                notificationService.showFullScreenNotification(isGuest);
                Log.d(TAG, "تم عرض إشعار ملء الشاشة");
            } else {
                // للجهاز غير المقفل، استخدم الإشعار العادي
                notificationService.showDailyReminderNotification(isGuest);
                Log.d(TAG, "تم عرض الإشعار العادي");
            }

            // تسجيل وقت عرض الإشعار
            markReminderShown(context);

            Log.d(TAG, "تم عرض الإشعار بنجاح من AlarmManager");

        } catch (Exception e) {
            Log.e(TAG, "خطأ في عرض الإشعار من AlarmManager", e);
        }
    }

    /**
     * معالجة طلب إلغاء الإشعار
     */
    private void handleDismissAction(Context context, Intent intent) {
        Log.d(TAG, "تم طلب إلغاء الإشعار");
        
        try {
            DailyReminderNotification notificationService = 
                    new DailyReminderNotification(context);
            notificationService.cancelDailyReminderNotifications();
            
            Log.d(TAG, "تم إلغاء الإشعارات بنجاح");
        } catch (Exception e) {
            Log.e(TAG, "خطأ في إلغاء الإشعار", e);
        }
    }

    /**
     * التحقق من أن الإشعار يجب أن يُعرض
     */
    private boolean shouldShowReminder(Context context) {
        long lastReminder = getLastReminderTime(context);
        long currentTime = System.currentTimeMillis();
        long timeDifference = currentTime - lastReminder;

        // التحقق من أن مر 22 ساعة على الأقل على آخر إشعار
        return timeDifference >= (22 * 60 * 60 * 1000); // 22 ساعة بالميلي ثانية
    }

    /**
     * التحقق من تفعيل الإشعارات
     */
    private boolean areNotificationsEnabled(Context context) {
        DailyReminderNotification notificationService = 
                new DailyReminderNotification(context);
        return notificationService.areNotificationsEnabled();
    }

    /**
     * التحقق من حالة قفل الجهاز
     */
    private boolean isDeviceLocked(Context context) {
        android.app.KeyguardManager keyguardManager = 
                (android.app.KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return keyguardManager.isKeyguardLocked();
    }

    /**
     * الحصول على وقت آخر إشعار
     */
    private long getLastReminderTime(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(
                "daily_reminder_prefs", Context.MODE_PRIVATE);
        return prefs.getLong("last_reminder_time", 0);
    }

    /**
     * تسجيل وقت عرض الإشعار
     */
    private void markReminderShown(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(
                "daily_reminder_prefs", Context.MODE_PRIVATE);
        prefs.edit().putLong("last_reminder_time", System.currentTimeMillis()).apply();
    }
}
