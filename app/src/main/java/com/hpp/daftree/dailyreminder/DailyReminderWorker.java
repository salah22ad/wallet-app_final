package com.hpp.daftree.dailyreminder;

import android.Manifest;
import android.app.Notification;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.hpp.daftree.R;
import com.hpp.daftree.utils.SecureLicenseManager;

/**
 * Worker محسن للإشعارات اليومية
 * يتعامل مع العمل في الخلفية ويعرض الإشعارات بطريقة موثوقة
 */
public class DailyReminderWorker extends Worker {
    private static final String TAG = "DailyReminderWorker";
    private static final int FOREGROUND_NOTIFICATION_ID = 1001;

    public DailyReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    @NonNull
    @Override
    public Result doWork() {
        try {
            Context context = getApplicationContext();
            Log.d(TAG, "Worker الإشعار اليومي بدأ العمل");

            // عرض إشعار في المقدمة للأجهزة الحديثة
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setForegroundAsync(createForegroundInfo());
            }

            // التأكد من عدم عرض الإشعار أكثر من مرة في اليوم
            if (!shouldShowReminder(context)) {
                Log.d(TAG, "تم تخطي الإشعار - تم عرضه بالفعل اليوم");
                return Result.success();
            }

            // الحصول على حالة المستخدم
            boolean isGuest = SecureLicenseManager.getInstance(context).isGuest();

            // التأكد من تفعيل الإشعارات
            if (!areNotificationsEnabled(context)) {
                Log.w(TAG, "الإشعارات غير مفعلة");
                return Result.success();
            }

            // إنشاء خدمة الإشعارات
            DailyReminderNotification notificationService =
                    new DailyReminderNotification(context);

            // عرض الإشعار بناءً على حالة الجهاز
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isDeviceLocked(context)) {
                // للأجهزة المقفلة، استخدم إشعار ملء الشاشة
                notificationService.showFullScreenNotification(isGuest);
                Log.d(TAG, "تم عرض إشعار ملء الشاشة");
            } else {
                // للأجهزة غير المقفلة، استخدم الإشعار العادي
                notificationService.showDailyReminderNotification(isGuest);
                Log.d(TAG, "تم عرض الإشعار العادي");
            }

            // تسجيل وقت عرض الإشعار
            markReminderShown(context);

            // إعادة الجدولة لليوم التالي
            DailyReminderScheduler.rescheduleDailyReminder(context);

            Log.d(TAG, "تم عرض الإشعار اليومي بنجاح");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "خطأ في Worker الإشعار اليومي", e);

            // إعادة الجدولة رغم الفشل
            try {
                DailyReminderScheduler.rescheduleDailyReminder(getApplicationContext());
            } catch (Exception reScheduleException) {
                Log.e(TAG, "خطأ في إعادة الجدولة", reScheduleException);
            }

            // نجح لتجنب إيقاف العمل
            return Result.success();
        }
    }

    /**
     * إنشاء إشعار في المقدمة
     */
    @NonNull
    private ForegroundInfo createForegroundInfo() {
        Context context = getApplicationContext();
        String title = context.getString(R.string.daily_reminder_processing);
        String text = context.getString(R.string.preparing_daily_reminder);

        Notification notification = new NotificationCompat.Builder(context,
                context.getString(R.string.daftree_daily_reminder_channel))
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_add)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        return new ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification);
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
