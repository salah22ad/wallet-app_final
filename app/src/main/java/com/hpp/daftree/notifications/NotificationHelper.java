package com.hpp.daftree.notifications;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.hpp.daftree.MainActivity;
import com.hpp.daftree.MainViewModel;
import com.hpp.daftree.R;

public class NotificationHelper {

    private static final String CHANNEL_MAIN = "daftree_main";
    private static NotificationHelper instance;
    private final Application application;

    private NotificationHelper(Application app) {
        this.application = app;
        createChannels();
    }

    public static synchronized void init(Application app) {
        if (instance == null) {
            instance = new NotificationHelper(app);
        }
    }

    public static NotificationHelper get() {
        if (instance == null) {
            throw new IllegalStateException("NotificationHelper not initialized");
        }
        return instance;
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm =
                    (NotificationManager) application.getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel mainChannel = new NotificationChannel(
                    CHANNEL_MAIN,
                    application.getString(R.string.channel_main_name),
                    NotificationManager.IMPORTANCE_HIGH);
            mainChannel.setDescription(application.getString(R.string.channel_main_desc));
            mainChannel.setShowBadge(true);
            mainChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            mainChannel.enableVibration(true);
            mainChannel.enableLights(true);
            mainChannel.setLightColor(0xFF00FF00);

            long[] vibrationPattern = {0, 500, 250, 500};
            mainChannel.setVibrationPattern(vibrationPattern);

            mainChannel.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    mainChannel.getAudioAttributes());

            nm.createNotificationChannel(mainChannel);
        }
    }

    private PendingIntent getMainPendingIntent(String extraKey, String extraValue) {
        Intent intent = new Intent(application, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (extraKey != null) intent.putExtra(extraKey, extraValue);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT |
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
        return PendingIntent.getActivity(application, (int) System.currentTimeMillis(), intent, flags);
    }

    /**
     * عرض إشعار محلي بسيط مع دعم التمديد.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    public void showLocalNotification(String title, String message, int id, boolean important) {
        // إنشاء نمط الإشعار الممتد
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle()
                .bigText(message)
                .setBigContentTitle(title)
                .setSummaryText( application.getString(R.string.noti_summery));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(application, CHANNEL_MAIN)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(bigTextStyle)
                .setAutoCancel(true)
                .setContentIntent(getMainPendingIntent("notif_click", message))
                .setPriority(important ? NotificationCompat.PRIORITY_HIGH : NotificationCompat.PRIORITY_DEFAULT)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(getMainPendingIntent(null, null), important);

        NotificationManagerCompat.from(application).notify(id, builder.build());

        refreshMainViewModel();
    }


    /**
     * عرض إشعار مع أولوية عالية للخلفية
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    public void showBackgroundNotification(String title, String message, int id) {
        try {
            // إنشاء PendingIntent لفتح التطبيق
            Intent intent = new Intent(application, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("from_notification", true);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    application, id, intent, flags
            );

            // بناء الإشعار
            NotificationCompat.Builder builder = new NotificationCompat.Builder(application, CHANNEL_MAIN)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setTimeoutAfter(60000);

            // إظهار الإشعار
            NotificationManagerCompat.from(application).notify(id, builder.build());

        } catch (Exception e) {
            Log.e("NotificationHelper", "Error showing background notification", e);
        }
    }

    /**
     * التحقق من صلاحيات الإشعارات
     */
    public boolean areNotificationsEnabled() {
        NotificationManagerCompat manager = NotificationManagerCompat.from(application);
        return manager.areNotificationsEnabled();
    }

    /**
     * عرض ديالوج أو إشعار حسب حالة التطبيق.
     */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    public void showDialogOrNotification(Activity currentActivity,
                                         String title,
                                         String message,
                                         boolean important) {
        if (currentActivity != null && !currentActivity.isFinishing()) {
            currentActivity.runOnUiThread(() -> {
                try {
                    new AlertDialog.Builder(currentActivity)
                            .setTitle(title)
                            .setMessage(message)
                            .setPositiveButton(R.string.ok, (d, w) -> d.dismiss())
                            .show();
                } catch (Exception e) {
                    Log.e("NotificationHelper", "Dialog failed, fallback to notification", e);
                    showLocalNotification(title, message, (int) System.currentTimeMillis(), important);
                }
            });
        } else {
            showLocalNotification(title, message, (int) System.currentTimeMillis(), important);
        }
    }

    /**
     * تحديث بيانات الواجهة عبر MainViewModel LiveData.
     */
    private void refreshMainViewModel() {
        try {
            if (application instanceof ViewModelStoreOwner) {
                MainViewModel vm = new ViewModelProvider(
                        (ViewModelStoreOwner) application).get(MainViewModel.class);
                vm.refreshData();
            } else {
                Intent intent = new Intent(application, MainActivity.class);
                intent.setAction("REFRESH_UI");
                PendingIntent.getActivity(application, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
                                        PendingIntent.FLAG_MUTABLE : 0));
            }
        } catch (Exception e) {
            Log.e("NotificationHelper", "Failed to refresh UI", e);
        }
    }


}