package com.hpp.daftree.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView; // <-- استيراد TextView

import com.hpp.daftree.R;

public class TutorialDialog {

    // تم إضافة معلمة String dialogMessage
    public static void show(Context context, String activityName, String dialogMessage) {
        SharedPreferences prefs = context.getSharedPreferences("AppTutorials", Context.MODE_PRIVATE);
        boolean isFirstTime = prefs.getBoolean(activityName, true);

        if (isFirstTime) {
            final Dialog dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(R.layout.dialog_instructions);

            // ✨ الجزء الجديد: إيجاد الـ TextView وتعيين الرسالة المخصصة
            TextView messageTextView = dialog.findViewById(R.id.dialog_message);
            if (messageTextView != null) {
                messageTextView.setText(dialogMessage);
            }

            // التحقق من وضع الهاتف وتشغيل الصوت أو الاهتزاز
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                if (audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                    final MediaPlayer mediaPlayer = MediaPlayer.create(context, R.raw.notification_sound);
                    mediaPlayer.start();
                    mediaPlayer.setOnCompletionListener(mp -> mp.release());
                } else {
                    Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                    if (v != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            v.vibrate(500);
                        }
                    }
                }
            }

            Window window = dialog.getWindow();
            if (window == null) {
                return;
            }

            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.getAttributes().windowAnimations = R.style.DialogAnimation;

            WindowManager.LayoutParams windowAttributes = window.getAttributes();
            windowAttributes.gravity = Gravity.BOTTOM;
            window.setAttributes(windowAttributes);
            dialog.setCancelable(true);
            dialog.findViewById(R.id.dialog_message).getRootView().setOnClickListener(v -> dialog.dismiss());

            dialog.show();

            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(activityName, false);
            editor.apply();
        }
    }
}