package com.hpp.daftree.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.Log;

import java.util.Locale;

public class PreferenceHelper {
    private static final String PREF_NAME = "AppPrefs";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_FIRST_RUN = "first_run";

    public static void setLanguage(Context context, String lang) {
        Log.e("PreferenceHelper", "Selected Language: " + lang);
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, lang).apply();
    }

    public static String getLanguage(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, "ar"); // العربية افتراضي
    }

    public static boolean isFirstRun(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_FIRST_RUN, true);
    }

    public static void setFirstRun(Context context, boolean firstRun) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_FIRST_RUN, firstRun).apply();
    }
    public static void applyLocale(Context context, String lang) {
        Resources resources = context.getResources();
        Configuration config = resources.getConfiguration();
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);

        // تحديث اللغة
        config.setLocale(locale);

        // تحديد اتجاه النص تلقائيًا
        int layoutDirection = TextUtils.getLayoutDirectionFromLocale(locale);
        config.setLayoutDirection(locale);

        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }


}

