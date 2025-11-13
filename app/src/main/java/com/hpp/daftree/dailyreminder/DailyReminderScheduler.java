package com.hpp.daftree.dailyreminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * مجدول الإشعارات اليومية المحسن
 * يجمع بين WorkManager للضمان والألارم ماناجر للدقة
 */
public class DailyReminderScheduler {
    private static final String TAG = "DailyReminderScheduler";
    private static final String WORK_NAME = "DailyReminderWork";
    private static final String ALARM_ACTION = "DAFTREE_DAILY_REMINDER";
    private static final int ALARM_REQUEST_CODE = 1001;

    /**
     * جدولة الإشعار اليومي باستخدام WorkManager + AlarmManager
     */
    public static void scheduleDailyReminder(Context context) {
        try {
            Log.d(TAG, "بدء جدولة الإشعار اليومي...");

            // إلغاء الجدولة السابقة
            cancelScheduledReminder(context);

            // 1. جدولة WorkManager (للضمان العام)
            scheduleWorkManagerReminder(context);

            // 2. جدولة AlarmManager (للدقة في التوقيت)
            scheduleAlarmManagerReminder(context);

            Log.d(TAG, "تم جدولة الإشعار اليومي بنجاح للـ 8:30 مساءً");

        } catch (Exception e) {
            Log.e(TAG, "خطأ في جدولة الإشعار اليومي", e);
            // إعادة المحاولة
            retrySchedule(context);
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
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
        );

        Log.d(TAG, "تم جدولة WorkManager بنجاح");
    }

    /**
     * جدولة الإشعار باستخدام AlarmManager (للمزيد من الدقة)
     */
    private static void scheduleAlarmManagerReminder1(Context context) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            // حساب الوقت حتى 8:30 مساءً
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.HOUR_OF_DAY, 20); // 8 PM
            calendar.set(Calendar.MINUTE, 30);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            long triggerTime = calendar.getTimeInMillis();

            // إذا كان الوقت الحالي بعد 8:30 مساءً، نضيف يوم
            if (triggerTime <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
                triggerTime = calendar.getTimeInMillis();
            }

            // إنشاء Intent للاستقبال
            Intent intent = new Intent(ALARM_ACTION);
            intent.setClass(context, DailyReminderReceiver.class);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    ALARM_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // جدولة AlarmManager بناءً على إصدار Android
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0+ - استخدام setExactAndAllowWhileIdle للدقة العالية
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // Android 4.4+ - استخدام setExact
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            } else {
                // الأجهزة القديمة - استخدام set
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            }

            Log.d(TAG, "تم جدولة AlarmManager بنجاح للوقت: " + calendar.getTime());

        } catch (Exception e) {
            Log.e(TAG, "خطأ في جدولة AlarmManager", e);
        }
    }
    /**
     * جدولة الإشعار باستخدام AlarmManager (للمزيد من الدقة)
     */
    private static void scheduleAlarmManagerReminder(Context context) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

            // حساب الوقت حتى 8:30 مساءً
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.HOUR_OF_DAY, 20); // 8 PM
            calendar.set(Calendar.MINUTE, 30);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            long triggerTime = calendar.getTimeInMillis();

            // إذا كان الوقت الحالي بعد 8:30 مساءً، نضيف يوم
            if (triggerTime <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
                triggerTime = calendar.getTimeInMillis();
            }

            // إنشاء Intent للاستقبال
            Intent intent = new Intent(ALARM_ACTION);
            intent.setClass(context, DailyReminderReceiver.class);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    ALARM_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // التحقق من إمكانية جدولة التنبيهات الدقيقة (لـ Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    scheduleExactAlarmCompat(alarmManager, triggerTime, pendingIntent);
                } else {
                    // إذا لم تكن الصلاحية متاحة، استخدام البديل
                    handleNoExactAlarmPermission(context, alarmManager, triggerTime, pendingIntent);
                    return;
                }
            } else {
                // للأجهزة الأقدم، استخدام الطريقة العادية
                scheduleExactAlarmCompat(alarmManager, triggerTime, pendingIntent);
            }

            Log.d(TAG, "تم جدولة AlarmManager بنجاح للوقت: " + calendar.getTime());

        } catch (SecurityException e) {
            Log.e(TAG, "لا يوجد صلاحية لجدولة التنبيهات الدقيقة", e);
            handleNoExactAlarmPermission(context, null, 0, null);
        } catch (Exception e) {
            Log.e(TAG, "خطأ في جدولة AlarmManager", e);
        }
    }

    /**
     * جدولة تنبيه دقيق متوافقة مع جميع الإصدارات
     */
    private static void scheduleExactAlarmCompat(AlarmManager alarmManager, long triggerTime, PendingIntent pendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6.0+ - استخدام setExactAndAllowWhileIdle للدقة العالية
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // Android 4.4+ - استخدام setExact
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            } else {
                // الأجهزة القديمة - استخدام set
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException في جدولة التنبيه الدقيق", e);
            throw e; // إعادة الرمي للمعالجة في الدالة الأم
        }
    }

    /**
     * التعامل مع عدم وجود صلاحية للتنبيهات الدقيقة
     */
    private static void handleNoExactAlarmPermission(Context context, AlarmManager alarmManager,
                                                     long triggerTime, PendingIntent pendingIntent) {
        Log.w(TAG, "لا توجد صلاحية لجدولة التنبيهات الدقيقة");

        // البديل 1: استخدام WorkManager كحل بديل
        Log.i(TAG, "الاعتماد على WorkManager للجدولة");

        // البديل 2: إذا كان AlarmManager متاحًا، استخدام جدولة غير دقيقة
        if (alarmManager != null && pendingIntent != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                }
                Log.i(TAG, "تم استخدام جدولة غير دقيقة كبديل");
            } catch (Exception e) {
                Log.e(TAG, "فشل في الجدولة غير الدقيقة", e);
            }
        }

        // البديل 3: إشعار المستخدم بضرورة منح الصلاحية (اختياري)
        notifyUserAboutExactAlarmPermission(context);
    }

    /**
     * إشعار المستخدم بخصوص صلاحية التنبيهات الدقيقة (اختياري)
     */
    private static void notifyUserAboutExactAlarmPermission(Context context) {
        // يمكن تنفيذ هذا حسب احتياج التطبيق
        Log.i(TAG, "يوصى بطلب صلاحية التنبيهات الدقيقة من المستخدم");
    }
    /**
     * إعادة الجدولة يومياً تلقائياً
     */
    public static void rescheduleDailyReminder(Context context) {
        Log.d(TAG, "إعادة جدولة الإشعار اليومي");
        scheduleDailyReminder(context);
    }

    /**
     * إلغاء جميع الجدولات
     */
    public static void cancelScheduledReminder(Context context) {
        try {
            // إلغاء WorkManager
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);

            // إلغاء AlarmManager
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(ALARM_ACTION);
            intent.setClass(context, DailyReminderReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    ALARM_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            alarmManager.cancel(pendingIntent);

            Log.d(TAG, "تم إلغاء جميع جدولات الإشعارات اليومية");
        } catch (Exception e) {
            Log.e(TAG, "خطأ في إلغاء الجدولة", e);
        }
    }

    /**
     * إعادة المحاولة في حالة الفشل
     */
    /**
     * إعادة المحاولة في حالة الفشل مع تحديد حد للمحاولات
     */
    private static void retrySchedule(Context context) {
        // استخدام SharedPreferences لتتبع عدد المحاولات
        SharedPreferences prefs = context.getSharedPreferences("DailyReminderPrefs", Context.MODE_PRIVATE);
        int retryCount = prefs.getInt("retry_count", 0);
        final int MAX_RETRY_COUNT = 3; // أقصى عدد للمحاولات

        if (retryCount >= MAX_RETRY_COUNT) {
            Log.w(TAG, "تم الوصول إلى الحد الأقصى للمحاولات (" + MAX_RETRY_COUNT + ")، إيقاف إعادة المحاولة");
            resetRetryCount(context); // إعادة تعيين العداد
            return;
        }

        // زيادة عدد المحاولات
        retryCount++;
        prefs.edit().putInt("retry_count", retryCount).apply();

        Log.d(TAG, "محاولة إعادة الجدولة رقم: " + retryCount + " من " + MAX_RETRY_COUNT);

        // استخدام WorkManager للجدولة المؤقتة بدلاً من Thread.sleep
        scheduleRetryWorkManager(context, retryCount);
    }

    /**
     * جدولة إعادة المحاولة باستخدام WorkManager لتجنب استهلاك الموارد
     */
    private static void scheduleRetryWorkManager(Context context, int retryCount) {
        // حساب وقت التأخير بناءً على عدد المحاولات (Exponential Backoff)
        long delayMinutes = calculateRetryDelay(retryCount);

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(false)
                .build();

        OneTimeWorkRequest retryWorkRequest = new OneTimeWorkRequest.Builder(RetryWorker.class)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueue(retryWorkRequest);

        Log.d(TAG, "تم جدولة إعادة المحاولة بعد " + delayMinutes + " دقائق");
    }

    /**
     * حساب وقت التأخير باستخدام Exponential Backoff
     */
    private static long calculateRetryDelay(int retryCount) {
        switch (retryCount) {
            case 1: return 2;  // 2 دقائق للمحاولة الأولى
            case 2: return 5;  // 5 دقائق للمحاولة الثانية
            case 3: return 15; // 15 دقيقة للمحاولة الثالثة
            default: return 30; // 30 دقيقة لأي محاولات إضافية
        }
    }

    /**
     * إعادة تعيين عداد المحاولات
     */
    public static void resetRetryCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("DailyReminderPrefs", Context.MODE_PRIVATE);
        prefs.edit().putInt("retry_count", 0).apply();
        Log.d(TAG, "تم إعادة تعيين عداد المحاولات");
    }

    /**
     * Worker مخصص لإعادة المحاولة
     */
    public static class RetryWorker extends Worker {
        public RetryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            Log.d(TAG, "بدء إعادة المحاولة عبر Worker");

            try {
                // إعادة جدولة الإشعارات
                scheduleWorkManagerReminder(getApplicationContext());
                scheduleAlarmManagerReminder(getApplicationContext());

                // إعادة تعيين العداد عند النجاح
                resetRetryCount(getApplicationContext());

                Log.d(TAG, "تمت إعادة الجدولة بنجاح عبر Worker");
                return Result.success();

            } catch (Exception e) {
                Log.e(TAG, "فشل في إعادة الجدولة عبر Worker", e);

                // عدم إعادة المحاولة تلقائياً - سيتم المحاولة في المرة القادمة
                return Result.failure();
            }
        }
    }
    private static void retrySchedule2(Context context) {
        try {
            Log.d(TAG, "إعادة محاولة جدولة الإشعار في دقيقة...");

            // تأخير قصير ثم إعادة المحاولة مرة واحدة فقط
            Thread.sleep(TimeUnit.MINUTES.toMillis(1));

            // محاولة واحدة فقط لتجنب الحلقة اللا نهائية
            Log.d(TAG, "إعادة محاولة الجدولة...");
            scheduleWorkManagerReminder(context);
            scheduleAlarmManagerReminder(context);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "تم إيقاف إعادة المحاولة", e);
        } catch (Exception e) {
            Log.e(TAG, "فشل في إعادة المحاولة - سيتم التخلي عن الجدولة", e);
        }
    }
    /**
     * التحقق من حالة الجدولة
     */
    public static boolean isReminderScheduled(Context context) {
        // يمكن تحسين هذا للتحقق من حالة WorkManager وAlarmManager
        return true; // تبسيط مؤقت
    }

    /**
     * الحصول على الوقت المتبقي للإشعار التالي
     */
    public static long getNextReminderTime(Context context) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, 20);
        calendar.set(Calendar.MINUTE, 30);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        long nextTime = calendar.getTimeInMillis();
        
        // إذا كان الوقت الحالي بعد 8:30، نضيف يوم
        if (nextTime <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            nextTime = calendar.getTimeInMillis();
        }

        return nextTime - System.currentTimeMillis();
    }
}
