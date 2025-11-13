package com.hpp.daftree.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;
import android.view.View;

import com.hpp.daftree.R;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.view.View;

import java.util.Locale;

public class LanguageHelper {

    public static Context setLocale(Context context, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            configuration.setLocale(locale);
            configuration.setLayoutDirection(locale);
        } else {
            configuration.locale = locale;
        }

        resources.updateConfiguration(configuration, resources.getDisplayMetrics());

        // إنشاء سياق جديد مع اللغة المحدثة
        return createContextWithLocale(context, locale);
    }

    private static Context createContextWithLocale(Context context, Locale locale) {
        Configuration configuration = context.getResources().getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            configuration.setLocale(locale);
            configuration.setLayoutDirection(locale);
        } else {
            configuration.locale = locale;
        }

        return context.createConfigurationContext(configuration);
    }

    public static void applyDirection(Activity activity) {
        String languageCode = getSavedLanguage(activity);
        if (languageCode.equals("ar")) {
            activity.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        } else {
            activity.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        }
    }
    public static Resources getLocalizedResources(Context context) {
        String languageCode = getSavedLanguage(context);
        Resources resources = context.getResources();
        Configuration configuration = new Configuration(resources.getConfiguration());
        Locale locale = new Locale(languageCode);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            configuration.setLocale(locale);
        } else {
            configuration.locale = locale;
        }

        return context.createConfigurationContext(configuration).getResources();
    }
    public static String getSavedLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        return prefs.getString("language", "ar");
    }

    public static boolean isRTL(Context context) {
        String languageCode = getSavedLanguage(context);
        return languageCode.equals("ar");
    }

    // دالة لإنشاء سياق مخصص للديالوجات
    public static Context getLocalizedContext(Context context) {
        String languageCode = getSavedLanguage(context);
        Locale locale = new Locale(languageCode);
        return createContextWithLocale(context, locale);
    }
}