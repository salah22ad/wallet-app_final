package com.hpp.daftree.ui;

import static androidx.fragment.app.FragmentManager.TAG;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.hpp.daftree.helpers.PreferenceHelper;

import java.util.Locale;

public abstract class BaseActivity extends AppCompatActivity {

    // متغير ثابت يمكن استدعاؤه في التقارير
    public static boolean isRtl = true;
private static final String TAG = "BaseActivity";
    @Override
    protected void attachBaseContext(Context newBase) {
        String lang = PreferenceHelper.getLanguage(newBase); // تأكد من وجود PreferenceHelper
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Log.d(TAG, "اللغة الجديدة: " + lang);
        // بناء نسخة من التهيئة مع اللغة والاتجاه
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);

        // 🔥 الإضافة الجديدة: جعل حجم الخط ثابتًا
        config.fontScale = 0.85f; // تعيين حجم الخط إلى الطبيعي

        Context localizedContext = newBase;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // الطريقة الصحيحة: createConfigurationContext موجود على Context (API 17+)
            localizedContext = newBase.createConfigurationContext(config);
        } else {
            // fallback للأجهزة القديمة (ملاحظة: updateConfiguration مُلغاة ولكنها لازمة للأجهزة القديمة)
            Resources res = newBase.getResources();
            res.updateConfiguration(config, res.getDisplayMetrics());
            localizedContext = newBase;
        }

        super.attachBaseContext(localizedContext);
    }
    private void applyLayoutDirection() {
        String lang = PreferenceHelper.getLanguage(this);
        Locale locale = new Locale(lang);
        int layoutDir = TextUtils.getLayoutDirectionFromLocale(locale);
        isRtl = (layoutDir == View.LAYOUT_DIRECTION_RTL);

        // تطبيق اتجاه الواجهة
        getWindow().getDecorView().setLayoutDirection(
                isRtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR
        );
    }

    // 🔥 الإضافة الجديدة: تطبيق حجم الخط الثابت
    private void applyFixedFontScale() {
        Configuration configuration = getResources().getConfiguration();
        if (configuration.fontScale != 0.85f) {
            configuration.fontScale = 0.85f;
            getResources().updateConfiguration(configuration, getResources().getDisplayMetrics());
        }
    }

    // 🔥 الإضافة الجديدة: تجاهل تغييرات حجم الخط من النظام
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // تجاهل تغييرات حجم الخط
        if (newConfig.fontScale != 0.85f) {
            newConfig.fontScale = 0.85f;
            getResources().updateConfiguration(newConfig, getResources().getDisplayMetrics());
        }

        // إعادة تطبيق اتجاه الواجهة بعد التغيير
        applyLayoutDirection();
    }
    public static void applyLanguage(Context context, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale);
        } else {
            configuration.locale = locale;
        }

        resources.updateConfiguration(configuration, resources.getDisplayMetrics());

        // حفظ اللغة في التفضيلات
        PreferenceHelper.setLanguage(context, languageCode);
    }

    // 🔥 إضافة هذه الدالة لضمان تطبيق اللغة عند إنشاء النشاط
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // تطبيق اللغة قبل استدعاء super.onCreate()
        String lang = PreferenceHelper.getLanguage(this);
        applyLanguage(this, lang);

        super.onCreate(savedInstanceState);
        applyLayoutDirection();
        applyFixedFontScale();
    }

    // 🔥 الإضافة الجديدة: ضمان حجم الخط الثابت عند استئناف النشاط
    @Override
    protected void onResume() {
        super.onResume();
        applyFixedFontScale(); // التأكد من أن حجم الخط ثابت عند العودة للنشاط
    }
}