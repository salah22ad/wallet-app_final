package com.hpp.daftree;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import android.util.Log;

import androidx.multidex.BuildConfig;

import com.google.firebase.firestore.FirebaseFirestore;
import com.hpp.daftree.utils.LicenseManager;

public class DeviceBanManager {
    private static final String TAG = "DeviceBanManager";
    private static final int MAX_DEVICES_PER_GUEST = 3;
    private static final String BAN_PREFS = "device_ban_prefs";
    private static final String KEY_IS_BANNED = "is_banned";
    private static final String KEY_BAN_REASON = "ban_reason";
    private static final String KEY_LAST_CHECK = "last_check";

    private final Context context;
    private final FirebaseFirestore firestore;
    private final LicenseManager licenseManager;

    public DeviceBanManager(Context context) {
        this.context = context;
        this.firestore = FirebaseFirestore.getInstance();
        this.licenseManager = new LicenseManager(context);
    }

    public interface BanCheckListener {
        void onCheckComplete(boolean isBanned, String reason);
        void onCheckError(String error);
    }

    /**
     * فحص إذا كان الجهاز محظوراً
     */
    public void checkDeviceBan(BanCheckListener listener) {
        // ✅ التحقق من التخزين المحلي أولاً (لتجنب استدعاء الشبكة كل مرة)
        if (isLocallyBanned()) {
            listener.onCheckComplete(true, getLocalBanReason());
            return;
        }

        // ✅ التحقق من اتصال الإنترنت
        if (!isNetworkAvailable()) {
            listener.onCheckComplete(false, null); // غير محظور في حالة عدم وجود اتصال
            return;
        }
        checkDeviceBanInFirestore(listener);
    }

    /**
     * الفحص الرئيسي في Firestore
     */
    private void checkDeviceBanInFirestore(BanCheckListener listener) {
        String currentDeviceId = licenseManager.getDeviceId();

        if (TextUtils.isEmpty(currentDeviceId)) {
            listener.onCheckError(context.getString(R.string.failed_check_device));
            return;
        }

        Log.d(TAG, "بدء فحص حظر الجهاز: " + currentDeviceId);

        // ✅ الاستعلام عن جميع الضيوف الذين لديهم نفس deviceId
        firestore.collection("guests")
                .whereEqualTo("deviceId", currentDeviceId) // ✅ تغيير من arrayContains إلى whereEqualTo
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        int deviceUsageCount = task.getResult().size();
                        Log.d(TAG, "عدد مرات استخدام الجهاز: " + deviceUsageCount);
                        if (BuildConfig.DEBUG) {
                            deviceUsageCount = 0;
                        }else{
                            deviceUsageCount = task.getResult().size();
                        }
                        licenseManager.setGuestUsageCountt(deviceUsageCount);

                        if (deviceUsageCount >= MAX_DEVICES_PER_GUEST) {
                            // ✅ حظر الجهاز
//                             banReason = "تم حظر الجهاز بسبب استخدامه في " + deviceUsageCount + " حسابات ضيف (الحد الأقصى: " + MAX_DEVICES_PER_GUEST + ")";
                            String banReason = context.getString(R.string.blocked_device_message);
                            banDeviceLocally(banReason);
                            listener.onCheckComplete(true, banReason);
                        } else {
                            // ✅ الجهاز غير محظور
                            updateLastCheckTime();
                            listener.onCheckComplete(false, null);
                        }
                    } else {
                        Log.e(TAG, "خطأ في فحص الحظر: " + task.getException());
                        listener.onCheckError(context.getString(R.string.failed_check_device));
                    }
                });
    }
    /**
     * حظر الجهاز محلياً
     */
    private void banDeviceLocally(String reason) {
        SharedPreferences prefs = context.getSharedPreferences(BAN_PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_IS_BANNED, true)
                .putString(KEY_BAN_REASON, reason)
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .apply();

        Log.w(TAG, "تم حظر الجهاز محلياً: " + reason);
    }

    /**
     * التحقق من الحظر المحلي
     */
    private boolean isLocallyBanned() {
        SharedPreferences prefs = context.getSharedPreferences(BAN_PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_IS_BANNED, false);
    }

    private String getLocalBanReason() {
        SharedPreferences prefs = context.getSharedPreferences(BAN_PREFS, Context.MODE_PRIVATE);
        return prefs.getString(KEY_BAN_REASON, context.getString(R.string.device_block_title));
    }

    /**
     * تحديث وقت آخر فحص
     */
    private void updateLastCheckTime() {
        SharedPreferences prefs = context.getSharedPreferences(BAN_PREFS, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
    }

    /**
     * إلغاء حظر الجهاز (لأغراض التطوير)
     */
    public void unbanDevice() {
        SharedPreferences prefs = context.getSharedPreferences(BAN_PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_IS_BANNED)
                .remove(KEY_BAN_REASON)
                .apply();
        Log.i(TAG, "تم إلغاء حظر الجهاز");
    }

    /**
     * التحقق من اتصال الإنترنت
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }
}
