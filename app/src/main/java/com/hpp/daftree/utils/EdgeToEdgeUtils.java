package com.hpp.daftree.utils;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class EdgeToEdgeUtils {

    /**
     * يطبق منطق Edge-to-Edge على النشاط (Activity) ويضبط حشوة شريط الأدوات (Toolbar)
     * ليتجنب شريط الحالة (Status Bar).
     *
     * ✅ محدث لدعم Android 15 بشكل كامل
     *
     * @param activity النشاط المراد تطبيق Edge-to-Edge عليه.
     * @param toolbar شريط الأدوات (Toolbar) الذي سيتم تعديل حشوته.
     */
    public static void applyEdgeToEdge(Activity activity, View toolbar) {
        Window window = activity.getWindow();

        // 1. تفعيل وضع Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // 2. ضبط لون شريط الحالة ليكون شفافًا تمامًا
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
            // ✅ إزالة أي translucent flags قديمة
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            // ✅ تفعيل رسم خلفية النظام
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }

        // 3. ضبط ألوان محتوى شريط الحالة (الأيقونات والنصوص)
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(window, window.getDecorView());

        if (insetsController != null) {
            // ✅ جعل أيقونات شريط الحالة فاتحة (بيضاء) لأن خلفية الـ Toolbar داكنة
            insetsController.setAppearanceLightStatusBars(false);

            // ✅ Android 15: ضبط السلوك الافتراضي للـ System Bars
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insetsController.setSystemBarsBehavior(
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        }

        // 4. تطبيق WindowInsets على شريط الأدوات
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            androidx.core.graphics.Insets systemBarsInsets =
                    insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // تحديث الحشوة العلوية لشريط الأدوات لتشمل ارتفاع شريط الحالة
            v.setPadding(
                    v.getPaddingLeft(),
                    systemBarsInsets.top, // ✅ هذا يجعل الـ Toolbar يبدأ تحت شريط الحالة
                    v.getPaddingRight(),
                    v.getPaddingBottom()
            );

            // ✅ عدم استهلاك الـ Insets بالكامل للسماح للعناصر الأخرى باستخدامها
            return insets;
        });
    }

    /**
     * يطبق حشوة سفلية على View لتجنب شريط التنقل (Navigation Bar).
     *
     * @param view الـ View المراد تعديل حشوته السفلية.
     */
    public static void applyBottomInset(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            androidx.core.graphics.Insets systemBarsInsets =
                    insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // تحديث الحشوة السفلية للـ View لتشمل ارتفاع شريط التنقل
            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    systemBarsInsets.bottom
            );

            // ✅ إرجاع الـ Insets للسماح للعناصر الأخرى باستخدامها
            return insets;
        });
    }

    /**
     * دالة مساعدة للحصول على ارتفاع شريط الحالة (Status Bar)
     * لاستخدامه كقيمة لـ actionBarInsetTop.
     */
    public static int getStatusBarHeight(Activity activity) {
        int result = 0;
        int resourceId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = activity.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    /**
     * ✅ دالة جديدة: تطبيق Edge-to-Edge على الـ Activity بالكامل
     * بدون تمرير الـ Toolbar (للاستخدام العام)
     */
    public static void applyEdgeToEdgeToActivity(Activity activity) {
        Window window = activity.getWindow();

        // تفعيل وضع Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // ضبط لون شريط الحالة ليكون شفافًا
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }

        // ضبط ألوان محتوى شريط الحالة
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(window, window.getDecorView());

        if (insetsController != null) {
            insetsController.setAppearanceLightStatusBars(false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insetsController.setSystemBarsBehavior(
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        }
    }
}
