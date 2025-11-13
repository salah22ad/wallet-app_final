package com.hpp.daftree.dailyreminder;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.hpp.daftree.R;
import android.net.Uri;
import android.media.AudioAttributes;
import android.util.Log;

/**
 * خدمة عرض الإشعارات اليومية المحسنة
 * تضمن ظهور الإشعار بدقة في 8:30 مساءً مع أولوية عالية
 */
public class DailyReminderNotification {
    private static final int NOTIFICATION_ID = 2001;
    private static final int FULL_SCREEN_NOTIFICATION_ID = 2002;

    private final Context context;
    private final NotificationManagerCompat notificationManager;

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public DailyReminderNotification(Context context) {
        this.context = context;
        this.notificationManager = NotificationManagerCompat.from(context);
        createNotificationChannel();
    }

    private String getChannelId() {
        return context.getString(R.string.daftree_daily_reminder_channel);
    }

    /**
     * إنشاء قناة الإشعارات مع إعدادات محسنة
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.daily_reminder_channel_name);
            String description = context.getString(R.string.daily_reminder_channel_desc);
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel channel = new NotificationChannel(getChannelId(), name, importance);
            channel.setDescription(description);

            // تفعيل جميع الميزات المطلوبة للإشعارات اليومية
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setLightColor(0xFF00FF00);

            // نمط اهتزاز واضح لجذب الانتباه
            long[] vibrationPattern = {0, 1000, 500, 1000, 500, 1000}; // اهتزاز أطول
            channel.setVibrationPattern(vibrationPattern);

            // صوت مخصص للإشعار اليومي - حتى في الوضع الصامت
            Uri soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.my_wallet_tone);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED) // إلزامي حتى في الصامت
                    .build();

            channel.setSound(soundUri, audioAttributes);

            // تجاهل وضع عدم الإزعاج (DND)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                channel.setAllowBubbles(true);
                channel.setBypassDnd(true);
            }
            channel.setImportance(NotificationManager.IMPORTANCE_HIGH);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
    private void createNotificationChannel11() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.daily_reminder_channel_name);
            String description = context.getString(R.string.daily_reminder_channel_desc);
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel channel = new NotificationChannel(getChannelId(), name, importance);
            channel.setDescription(description);

            // تفعيل جميع الميزات المطلوبة للإشعارات اليومية
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setLightColor(0xFF00FF00);

            // نمط اهتزاز واضح لجذب الانتباه
            long[] vibrationPattern = {0, 500, 250, 500, 250, 500};
            channel.setVibrationPattern(vibrationPattern);

            // صوت مخصص للإشعار اليومي
            channel.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    channel.getAudioAttributes());

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    /**
     * عرض الإشعار اليومي العادي
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    public void showDailyReminderNotification55(boolean isGuest) {
        String title = context.getString(R.string.daily_reminder_title);
        String message;
        String bigText;

//        if (isGuest) {
//            message = context.getString(R.string.daily_reminder_message);
//            bigText = context.getString(R.string.daily_reminder_big_text);
//        } else {
            message = context.getString(R.string.daily_reminder_message);
            bigText = context.getString(R.string.daily_reminder_big_text);
//        }

        Intent intent = new Intent(context, com.hpp.daftree.MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // نمط الإشعار الممتد
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle()
                .bigText(bigText)
                .setBigContentTitle(title)
                .setSummaryText(context.getString(R.string.daily_reminder_summary));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, getChannelId())
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(bigTextStyle)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

                // الصوت المخصص - حتى في الوضع الصامت
                .setSound(Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.my_wallet_tone))

                // نمط الاهتزاز المخصص
                .setVibrate(new long[]{0, 1000, 500, 1000, 500, 1000})
                .setTicker(message)
//                .setTimeoutAfter(30000) // يختفي بعد 30 ثانية
                .addAction(getDismissAction())
                .addAction(getOpenAppAction());

        notificationManager.notify(NOTIFICATION_ID, builder.build());

//        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, getChannelId())
//                .setSmallIcon(R.drawable.ic_notification)
//                .setContentTitle(title)
//                .setContentText(message)
//                .setStyle(bigTextStyle)
//                .setPriority(NotificationCompat.PRIORITY_MAX) // تغيير إلى MAX
//                .setCategory(NotificationCompat.CATEGORY_ALARM) // تغيير إلى ALARM
//                .setContentIntent(pendingIntent)
//                .setAutoCancel(true)
//
//                // إعدادات الصوت والاهتزاز المحسنة
//                .setDefaults(NotificationCompat.DEFAULT_ALL)
//                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
//
//                // الصوت المخصص - حتى في الوضع الصامت
//                .setSound(Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.my_wallet_tone))
//
//                // نمط الاهتزاز المخصص
//                .setVibrate(new long[]{0, 1000, 500, 1000, 500, 1000})
//
//                // إعدادات إضافية لتجاوز وضع الصامت
//                .setFullScreenIntent(pendingIntent, true) // لإشعارات Android 10+
//                .setTicker(message)
//                .setTimeoutAfter(45000) // زيادة الوقت إلى 45 ثانية
//                .setOnlyAlertOnce(false) // السماح بتكرار التنبيه
//                .addAction(getDismissAction())
//                .addAction(getOpenAppAction());

// للأجهزة القديمة (ما قبل Android 8.0)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setPriority(NotificationCompat.PRIORITY_MAX);
        }
    }
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    public void showDailyReminderNotification(boolean isGuest) {
        String title = context.getString(R.string.daily_reminder_title);
        String message;
        String bigText;

//        if (isGuest) {
//            message = context.getString(R.string.guest_daily_reminder_message);
//            bigText = context.getString(R.string.guest_daily_reminder_big_text);
//        } else {
            message = context.getString(R.string.daily_reminder_message);
            bigText = context.getString(R.string.daily_reminder_big_text);
//        }

        Intent intent = new Intent(context, com.hpp.daftree.MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        // إنشاء AudioAttributes مخصص
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .build();

        // الحصول على URI للصوت المخصص
        Uri soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.my_wallet_tone);


            // للأجهزة القديمة، استخدام NotificationCompat
            NotificationCompat.Builder compatBuilder = new NotificationCompat.Builder(context, getChannelId())
                    .setSmallIcon(R.drawable.ic_stat_name)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setSound(soundUri) // بدون AudioAttributes للإصدارات القديمة
                    .setVibrate(new long[]{0, 1000, 300, 1000, 300, 1000, 300, 1000})
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setTicker(message)
//                    .setTimeoutAfter(60000)
                    .setOnlyAlertOnce(false)
                    .setAutoCancel(true)
                    .addAction(getDismissAction())
                    .addAction(getOpenAppAction());

            notificationManager.notify(NOTIFICATION_ID, compatBuilder.build());


        Log.d("TAG", "تم عرض الإشعار مع إعدادات الصوت المتقدمة");
    }
    /**
     * عرض إشعار ملء الشاشة للمستخدمين النائين (Android 10+)
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    public void showFullScreenNotification(boolean isGuest) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // للأجهزة القديمة، استخدم الإشعار العادي
            showDailyReminderNotification(isGuest);
            return;
        }

        String title = context.getString(R.string.daily_reminder_title);
        String message;

        if (isGuest) {
            message = context.getString(R.string.daily_reminder_message);
        } else {
            message = context.getString(R.string.daily_reminder_message);
        }

        Intent fullScreenIntent = new Intent(context, com.hpp.daftree.MainActivity.class);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(context, 1,
                fullScreenIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, getChannelId())
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

//        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, getChannelId())
//                .setSmallIcon(R.drawable.ic_stat_name)
//                .setContentTitle(title)
//                .setContentText(message)
//                .setPriority(NotificationCompat.PRIORITY_MAX)
//                .setCategory(NotificationCompat.CATEGORY_ALARM)
//                .setFullScreenIntent(fullScreenPendingIntent, true)
//                .setAutoCancel(true)
//                .setDefaults(NotificationCompat.DEFAULT_ALL)
//                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

                // إضافة الصوت والاهتزاز
                .setSound(Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.my_wallet_tone))
                .setVibrate(new long[]{0, 1000, 500, 1000, 500, 1000});
//                .setOnlyAlertOnce(false);
        notificationManager.notify(FULL_SCREEN_NOTIFICATION_ID, builder.build());
    }

    /**
     * إجراء إلغاء الإشعار
     */
    private NotificationCompat.Action getDismissAction() {
        Intent dismissIntent = new Intent(context, DailyReminderReceiver.class);
        dismissIntent.setAction("DISMISS_NOTIFICATION");
        PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(
                context, 0, dismissIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_close_white,
                context.getString(R.string.dismiss),
                dismissPendingIntent
        ).build();
    }

    /**
     * إجراء فتح التطبيق
     */
    private NotificationCompat.Action getOpenAppAction() {
        Intent openIntent = new Intent(context, com.hpp.daftree.MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                context, 1, openIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Action.Builder(
                R.drawable.ic_add,
                context.getString(R.string.open_app),
                openPendingIntent
        ).build();
    }

    /**
     * إلغاء جميع الإشعارات اليومية
     */
    public void cancelDailyReminderNotifications() {
        notificationManager.cancel(NOTIFICATION_ID);
        notificationManager.cancel(FULL_SCREEN_NOTIFICATION_ID);
    }

    /**
     * التحقق من تفعيل الإشعارات
     */
    public boolean areNotificationsEnabled() {
        return notificationManager.areNotificationsEnabled();
    }

    /**
     * فتح إعدادات الإشعارات في النظام
     */
    public void openNotificationSettings() {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        } else {
            intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
            intent.putExtra("app_package", context.getPackageName());
            intent.putExtra("app_uid", context.getApplicationInfo().uid);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
