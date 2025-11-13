package com.hpp.daftree.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.hpp.daftree.R;
import com.hpp.daftree.utils.VersionManager;

import java.io.File;
import java.util.concurrent.Executors;

public class UpdateAppDialog extends Dialog {

    private Context context;
    private String latestVersion;
    private String changelog;
    private String downloadUrl;
    private VersionManager versionManager;
    private CardView dialogContainer;

    private long downloadId = -1L;
    private BroadcastReceiver downloadReceiver;

    public UpdateAppDialog(@NonNull Context context, String latestVersion, String changelog, String downloadUrl) {
        super(context, R.style.BottomSlideDialogTheme);
        this.context = context;
        this.latestVersion = latestVersion;
        this.changelog = changelog;
        this.downloadUrl = downloadUrl;
        this.versionManager = new VersionManager(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_update_app);

        setupWindow();
        initViews();
        setupAnimations();
        playNotificationEffect();
    }

    private void setupWindow() {
        Window window = getWindow();
        if (window != null) {
            window.setGravity(android.view.Gravity.BOTTOM);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.horizontalMargin = 0;
            window.setAttributes(params);
            window.setWindowAnimations(R.style.BottomSlideDialogAnimation);
        }

        setCancelable(false);
        setCanceledOnTouchOutside(false);
    }

    private void initViews() {
        dialogContainer = findViewById(R.id.dialog_container);
        TextView title = findViewById(R.id.update_title);
        TextView message = findViewById(R.id.update_message);
        Button btnUpdate = findViewById(R.id.btn_update);
        Button btnLater = findViewById(R.id.btn_later);

        title.setText(context.getString(R.string.update_available_title, latestVersion));
        message.setText(changelog != null && !changelog.isEmpty()
                ? changelog
                : context.getString(R.string.update_available_message));

//        btnUpdate.setOnClickListener(v -> {
//            playClickEffect();
//            startDownload();
//            dismissWithAnimation();
//        });

        btnUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String downloadUrl = "https://play.google.com/store/apps/details?id=com.hpp.daftree";
//                String downloadUrl = "https://com-hpp-daftree.ar.uptodown.com/android";
                playClickEffect(); Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                context.startActivity(intent);
                dismissWithAnimation();
            }
        });
        btnLater.setOnClickListener(v -> {
            playClickEffect();
            versionManager.setUpdateIgnored(latestVersion);
            dismissWithAnimation();
        });
    }

    private void startDownload() {
        // تشغيل في الخلفية
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // تحميل الصفحة من Uptodown
                org.jsoup.nodes.Document doc = org.jsoup.Jsoup.connect(downloadUrl).get();

                // استخراج أول رابط مباشر لملف APK
                org.jsoup.nodes.Element link = doc.selectFirst("a[href$=.apk]");
                if (link == null) {
                    throw new Exception("لم يتم العثور على رابط APK مباشر");
                }

                String apkDirectUrl = link.attr("href");
                if (!apkDirectUrl.startsWith("http")) {
                    apkDirectUrl = "https:" + apkDirectUrl;
                }

                // تحميل ملف الـ APK مباشرة
                String fileName = "Daftree_Update_" + latestVersion + ".apk";
                android.app.DownloadManager.Request request =
                        new android.app.DownloadManager.Request(android.net.Uri.parse(apkDirectUrl));
                request.setTitle("Downloading update...");
                request.setDescription("Downloading version " + latestVersion);
                request.setNotificationVisibility(
                        android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(
                        android.os.Environment.DIRECTORY_DOWNLOADS, fileName);

                android.app.DownloadManager dm =
                        (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                downloadId = dm.enqueue(request);

                // مراقبة اكتمال التحميل
                downloadReceiver = new android.content.BroadcastReceiver() {
                    @Override
                    public void onReceive(Context ctx, android.content.Intent intent) {
                        long id = intent.getLongExtra(
                                android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                        if (id == downloadId) {
                            installDownloadedApk();
                            context.unregisterReceiver(this);
                        }
                    }
                };
                ContextCompat.registerReceiver(context, downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_NOT_EXPORTED);

            } catch (Exception e) {
                e.printStackTrace();
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        android.widget.Toast.makeText(context,
                                "فشل في تحديد رابط التحميل المباشر.", android.widget.Toast.LENGTH_LONG).show());
            }
        });
    }

    private void startDownload1() {
        try {
            Uri uri = Uri.parse(downloadUrl);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle("Downloading update...");
            request.setDescription("Downloading version " + latestVersion);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                    "ExpenseTracker_Update_" + latestVersion + ".apk");

            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            downloadId = dm.enqueue(request);

            // Register receiver to detect when download completes
            downloadReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id == downloadId) {
                        installDownloadedApk();
                        context.unregisterReceiver(this);
                    }
                }
            };
            ContextCompat.registerReceiver(context, downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_NOT_EXPORTED);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void installDownloadedApk() {
        try {
            File apkFile = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "ExpenseTracker_Update_" + latestVersion + ".apk");

            Uri apkUri;
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".provider", apkFile);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                apkUri = Uri.fromFile(apkFile);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            }

            context.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupAnimations() {
        dialogContainer.postDelayed(() -> {
            Animation slideUp = AnimationUtils.loadAnimation(context, R.anim.slide_up);
            dialogContainer.startAnimation(slideUp);
        }, 100);
    }

    private void playNotificationEffect() {
        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

            if (audioManager != null) {
                int ringerMode = audioManager.getRingerMode();

                if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                    playNotificationSound();
                } else if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                    playVibration();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playNotificationSound() {
        try {
            MediaPlayer mediaPlayer = MediaPlayer.create(context, R.raw.notification_sound);
            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener(MediaPlayer::release);
                mediaPlayer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playVibration() {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(500);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playClickEffect() {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(50);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void dismissWithAnimation() {
        Animation slideDown = AnimationUtils.loadAnimation(context, R.anim.slide_down);
        dialogContainer.startAnimation(slideDown);
        slideDown.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                dismiss();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
    }

    @Override
    public void show() {
        if (isContextValid()) {
            super.show();
            Window window = getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            }
        } else {
            Log.e("UpdateAppDialog", "Cannot show dialog - context is invalid");
        }
    }
    /**
     * التحقق من أن الـ Context صالح لعرض الـ Dialog
     */
    private boolean isContextValid() {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            return !activity.isFinishing() && !activity.isDestroyed();
        }
        return false;
    }

}
