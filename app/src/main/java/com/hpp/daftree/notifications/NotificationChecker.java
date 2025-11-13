package com.hpp.daftree.notifications;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Date;

public class NotificationChecker {

    private static final String TAG = "NotificationChecker";
    private static final String PREFS_NAME = "NotificationPrefs";
    private static final String LAST_CHECK_TIMESTAMP = "lastCheckTimestamp";

    public static void checkForNotifications(Context context) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(LAST_CHECK_TIMESTAMP, 0);

        db.collection("notifications")
                .whereEqualTo("target", "all_users")
                .whereGreaterThan("timestamp", new Date(lastCheck))
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        Log.d(TAG, "لا توجد إشعارات جديدة.");
                        return;
                    }

                    Log.d(TAG, "تم العثور على " + queryDocumentSnapshots.size() + " إشعار جديد.");

                    // استخدام NotificationHelper بدلاً من NotificationService المباشر
                    NotificationHelper notificationHelper = NotificationHelper.get();
                    long latestTimestamp = lastCheck;

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String title = doc.getString("title");
                        String message = doc.getString("message");
                        Date timestamp = doc.getDate("timestamp");

                        if (title != null && message != null && timestamp != null) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                    == PackageManager.PERMISSION_GRANTED) {
                                // استخدام NotificationHelper لعرض الإشعار
                                notificationHelper.showBackgroundNotification(
                                        title,
                                        message,
                                        (int) System.currentTimeMillis()
                                );

                                if (timestamp.getTime() > latestTimestamp) {
                                    latestTimestamp = timestamp.getTime();
                                }
                            } else {
                                Log.w(TAG, "صلاحية الإشعارات غير متاحة - لن يتم عرض الإشعار.");
                            }
                        }
                    }

                    // تحديث آخر وقت للمزامنة بعد عرض كل الإشعارات
                    prefs.edit().putLong(LAST_CHECK_TIMESTAMP, latestTimestamp).apply();
                })
                .addOnFailureListener(e -> Log.e(TAG, "فشل في جلب الإشعارات: ", e));
    }
}