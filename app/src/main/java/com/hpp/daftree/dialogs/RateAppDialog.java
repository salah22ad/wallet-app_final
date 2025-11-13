package com.hpp.daftree.dialogs;


import static androidx.core.content.ContextCompat.startActivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.CycleInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.hpp.daftree.R;
import com.hpp.daftree.helpers.LanguageHelper;

public class RateAppDialog {

    private static final String PREFS_NAME = "app_rate_prefs";
    private static final String KEY_DIALOG_SHOWN = "rate_dialog_shown";

    // إظهار الديالوج تلقائيًا بعد أسبوع
    public static void showIfNeeded(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean shown = prefs.getBoolean(KEY_DIALOG_SHOWN, false);

        long installTime = prefs.getLong("install_time", 0);
        long currentTime = System.currentTimeMillis();
        long oneWeekMillis = 7L * 24 * 60 * 60 * 1000; // أسبوع

        if (installTime == 0) {
            prefs.edit().putLong("install_time", currentTime).apply();
            return;
        }

        if (!shown && (currentTime - installTime >= oneWeekMillis)) {
            showDialog(activity);
            prefs.edit().putBoolean(KEY_DIALOG_SHOWN, true).apply();
        }
    }

    // إظهار الديالوج يدويًا من القائمة الجانبية
    public static void forceShow(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean shown = prefs.getBoolean(KEY_DIALOG_SHOWN, false);
        if (!shown) {
            showDialog(activity);
            prefs.edit().putBoolean(KEY_DIALOG_SHOWN, true).apply();
        } else {
            showDialog(activity); // السماح بإعادة الفتح يدويًا عند الطلب
        }
    }

    private static void showDialog(@NonNull Activity activity) {
        // مكونات الديالوج
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(70, 50, 70, 40);
        layout.setGravity(Gravity.CENTER);

        // العنوان
        TextView title = new TextView(activity);
        title.setText(R.string.menu_rate_app);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        title.setTextColor(MaterialColors.getColor(activity, com.google.android.material.R.attr.colorOnBackground, 0));

        // الرسالة
        TextView message = new TextView(activity);
        message.setText(R.string.rate_massge);
        message.setTextSize(15);
        message.setGravity(Gravity.CENTER);
        message.setTextColor(MaterialColors.getColor(activity, com.google.android.material.R.attr.colorOnBackground, 0));

        layout.addView(title);
        layout.addView(message);

        // إنشاء ديالوج
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setView(layout)
                .setCancelable(true)
                .setPositiveButton(R.string.rate_buttonn, null)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss());

        final androidx.appcompat.app.AlertDialog dialog = builder.create();


        // إضافة حركة Fade + Slide عند الظهور
        dialog.setOnShowListener(d -> {
            View decorView = dialog.getWindow().getDecorView();
            AnimationSet animSet = new AnimationSet(true);

            Animation slideUp = new TranslateAnimation(0, 0, 200, 0);
            slideUp.setDuration(350);

            Animation fadeIn = new AlphaAnimation(0f, 1f);
            fadeIn.setDuration(400);

            animSet.addAnimation(slideUp);
            animSet.addAnimation(fadeIn);
            decorView.startAnimation(animSet);

            Button rateButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);

            // حركة اهتزاز خفيفة لزر التقييم
            new Handler().postDelayed(() -> {
                Animation shake = new TranslateAnimation(0, 10, 0, 0);
                shake.setDuration(500);
                shake.setInterpolator(new CycleInterpolator(3));
                rateButton.startAnimation(shake);
            }, 800);

            rateButton.setOnClickListener(v -> {
//                openUpToDown(activity);
                launchInAppReview(activity);
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    // تشغيل نظام التقييم داخل التطبيق
    private static void launchInAppReview(Activity activity) {

        openPlayStore(activity);
        ReviewManager manager = ReviewManagerFactory.create(activity);
        manager.requestReviewFlow()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        ReviewInfo reviewInfo = task.getResult();
                        manager.launchReviewFlow(activity, reviewInfo)
                                .addOnCompleteListener(flow -> {
                                    // المستخدم أنهى التقييم
                                });
                    } else {
                        // فشل → افتح Google Play مباشرة
                        openPlayStore(activity);
                    }
                });
    }
    private static void openUpToDown(Context context) {
        final String appPackageName = context.getPackageName();
        String savedLanguage = LanguageHelper.getSavedLanguage(context.getApplicationContext());
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://com-hpp-daftree."+ savedLanguage + ".uptodown.com/android" )));
        } catch (android.content.ActivityNotFoundException e) {
            context.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://com-hpp-daftree.ar.uptodown.com/android")));
        }
    }
    // فتح التطبيق في Google Play
    private static void openPlayStore(Context context) {
        final String appPackageName = context.getPackageName();
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
        } catch (android.content.ActivityNotFoundException e) {
            context.startActivity(new Intent(Intent.ACTION_VIEW,

            Uri.parse("market://details?id=" + appPackageName)));
        }
    }
}

