package com.hpp.daftree;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.hpp.daftree.dailyreminder.DailyReminderManager;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.User;
import com.hpp.daftree.helpers.PreferenceHelper;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.syncmanagers.FirestoreSyncManager;
import com.hpp.daftree.syncmanagers.SyncPreferences;
import com.hpp.daftree.syncmanagers.SyncWorker;
import com.hpp.daftree.ui.BaseActivity;
import com.hpp.daftree.utils.CrashHandler;
import com.hpp.daftree.utils.GoogleAuthHelper;
import com.hpp.daftree.notifications.NotificationHelper;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.utils.VersionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MyApplication extends Application {

    public static String defaultCurrencyName;

    private User currentUserProfile;
    private SyncPreferences sharedPreferences;
    private DaftreeRepository repository;
    private GoogleAuthHelper googleAuthHelper;

    @Override
    public void onCreate() {
        super.onCreate();

//        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        String lang = PreferenceHelper.getLanguage(this);
        BaseActivity.applyLanguage(this, lang);
        FirebaseApp.initializeApp(this);

        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true);
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean("first_run", true);
        if(!isFirstRun) {
            String userId = FirebaseAuth.getInstance().getUid();
            boolean isGuest = SecureLicenseManager.getInstance(this).isGuest();
            if (isGuest) {
                FirebaseCrashlytics.getInstance().setUserId("user_" + SecureLicenseManager.getInstance(this).guestUID());
            } else if (userId != null || !userId.isEmpty()) {
                FirebaseCrashlytics.getInstance().setUserId("user_" + userId);
            }

        }
        CrashHandler.init(this);
        CrashHandler.getInstance().setCrashlyticsEnabled(true);
        sharedPreferences = new SyncPreferences(getApplicationContext());
        if (sharedPreferences.getLocalCurrency(SyncPreferences.DEFAULT_CURRENCY) == null) {
            sharedPreferences.setLocalCurrency(getString(R.string.local_currency));
        }

        FacebookSdk.sdkInitialize(getApplicationContext());
        AppEventsLogger.activateApp(this);
        // للتتبع (اختياري)
        FacebookSdk.setAutoLogAppEventsEnabled(true);
        FacebookSdk.setAdvertiserIDCollectionEnabled(true);
        FacebookSdk.setIsDebugEnabled(true);
        String localCurrency = SyncPreferences.DEFAULT_CURRENCY;
        defaultCurrencyName = sharedPreferences.getLocalCurrency(localCurrency);
        // Get instance of Firestore
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        VersionManager versionManager = new VersionManager(this);
        // التهيئة الأساسية
        NotificationHelper.init(this);
        DailyReminderManager.getInstance(this).initializeOnAppStart();
        // 🔥 تهيئة نظام التذكير اليومي الجديد
        initializeDailyReminderSystem();

        // إذا كان أول تشغيل، احفظ الإصدار الحالي
        if (versionManager.isFirstLaunch()) {
            versionManager.saveCurrentVersion();
        }

        // تسجيل معلومات الإصدار للسجلات
        Log.d("AppVersion", versionManager.getVersionInfo());
        // Build settings
        // إعدادات Firestore مع تمكين التخزين المحلي
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build();

        firestore.setFirestoreSettings(settings);
        setupPeriodicSync();

    }

    /**
     * 🔥 تهيئة نظام التذكير اليومي الجديد
     */
    private void initializeDailyReminderSystem() {
        try {
            DailyReminderManager.getInstance(this);
            Log.d("MyApplication", "Daily Reminder System initialized successfully");
        } catch (Exception e) {
            Log.e("MyApplication", "Failed to initialize Daily Reminder System", e);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // 🔥 الحفاظ على اللغة عند تغيير إعدادات الجهاز
        String lang = PreferenceHelper.getLanguage(this);
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);

        Configuration config = new Configuration(newConfig);
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }
    private void setupPeriodicSync() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest syncRequest = new PeriodicWorkRequest.Builder(
                SyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "AppSync",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
        );
    }

    public static void applyGlobalTextWatcher(View rootView) {
        if (rootView instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) rootView;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyGlobalTextWatcher(group.getChildAt(i));
            }
        } else if (rootView instanceof EditText) {
            EditText editText = (EditText) rootView;
            if ("exclude_global_filter".equals(editText.getTag())) {
                return; // تجاهل هذا الحقل ولا تطبق الفلتر عليه
            }
            InputFilter numberFilter = new InputFilter() {
                @Override
                public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                    StringBuilder builder = new StringBuilder();
                    for (int i = start; i < end; i++) {
                        char c = source.charAt(i);
                        switch (c) {
                            case '٠':
                                builder.append('0');
                                break;
                            case '١':
                                builder.append('1');
                                break;
                            case '٢':
                                builder.append('2');
                                break;
                            case '٣':
                                builder.append('3');
                                break;
                            case '٤':
                                builder.append('4');
                                break;
                            case '٥':
                                builder.append('5');
                                break;
                            case '٦':
                                builder.append('6');
                                break;
                            case '٧':
                                builder.append('7');
                                break;
                            case '٨':
                                builder.append('8');
                                break;
                            case '٩':
                                builder.append('9');
                                break;
                            case '،':
                                builder.append(',');
                                break;
                            default:
                                builder.append(c);
                                break;
                        }
                    }
                    return builder.toString();
                }
            };
            // اجمع الفلاتر الحالية مع الجديد
            InputFilter[] existingFilters = editText.getFilters();
            InputFilter[] newFilters = new InputFilter[existingFilters.length + 1];
            System.arraycopy(existingFilters, 0, newFilters, 0, existingFilters.length);
            newFilters[existingFilters.length] = numberFilter;
            editText.setFilters(newFilters);
        }
    }

    public static void initializeDefaultUser(Context context, AppDatabase database) {
        if (!SecureLicenseManager.getInstance(context.getApplicationContext()).isGuest()) {
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            if (database.userDao().getUserProfile() == null) {
                User currentUserProfile = new User(
                        "تطبيق محفظتي الذكية",
                        "ادارات مالية يومية",
                        "تعز، اليمن",
                        "+967 734 249 712",
                        "salah22app@gmail.com",
                        uid
                );
                database.userDao().upsert(currentUserProfile);
            }
        }
    }

    private static String copyDefaultLogoToStorage(Context context) {
        try {
            // إنشاء مجلد Logos إذا لم يكن موجوداً
            File logosDir = new File(context.getFilesDir(), "Logos");
            if (!logosDir.exists()) {
                logosDir.mkdir();
            }

            // إنشاء اسم فريد للملف
            String fileName = "default_app_logo.png";
            File logoFile = new File(logosDir, fileName);

            // نسخ الأيقونة من الـ mipmap إلى التخزين الداخلي
            @SuppressLint("ResourceType") InputStream inputStream = context.getResources().openRawResource(R.mipmap.ic_launcher_round);
            FileOutputStream outputStream = new FileOutputStream(logoFile);

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            inputStream.close();
            outputStream.close();

            return logoFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    void performLogout() {
        // إيقاف المزامنة
        FirestoreSyncManager.getInstance().stopListening();
        // تسجيل الخروج من Firebase
        FirebaseAuth.getInstance().signOut();
        // مسح البيانات المحلية
        SharedPreferences prefs = getSharedPreferences("prefs_uid", MODE_PRIVATE);
        prefs.edit().clear().apply();
        sharedPreferences.setFirstSyncComplete(false);
        // حذف قاعدة البيانات المحلية
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            db.close();

            File databaseFile = getDatabasePath("daftree_database");
            if (databaseFile.exists()) databaseFile.delete();

            File databaseWal = getDatabasePath("daftree_database-wal");
            if (databaseWal.exists()) databaseWal.delete();

            File databaseShm = getDatabasePath("daftree_database-shm");
            if (databaseShm.exists()) databaseShm.delete();
        });

        // التوجيه إلى شاشة تسجيل الدخول
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

}