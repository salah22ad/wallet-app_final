
package com.hpp.daftree.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hpp.daftree.database.DeviceInfo;
import com.hpp.daftree.UserLicense;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;

/**
 * مدير الترخيص الآمن والمشفر
 * يحتوي على دوال مشفرة ومحمية ضد التهكير
 */
public class SecureLicenseManager {

    private static final String TAG = "SecureLicenseManager";
    private static final String PREFS_NAME = "secure_license_prefs";
    private static boolean isGuest = false;
    // مفاتيح مشفرة للتخزين
    public static final String ENCRYPTED_KEY_PREMIUM = "cHJlbWl1bV9zdGF0dXM="; // premium_status
    public static final String ENCRYPTED_KEY_TRANSACTION_COUNT = "cXVvdGVfY291bnQ="; // quote_count
    public static final String ENCRYPTED_KEY_MAX_TRANSACTIONS = "bWF4X3F1b3Rlcw=="; // max_quotes
    public static final String ENCRYPTED_KEY_USER_ID = "dXNlcl9pZA=="; // user_id
    private static final String ENCRYPTED_KEY_DEVICE_ID = "ZGV2aWNlX2lk"; // device_id

    // مفاتيح مشفرة جديدة لتخزين بيانات الترخيص
    public static final String ENCRYPTED_MAX_TRANSACTIONS = "bWF4X3RyYW5zYWN0aW9ucw=="; // max_transactions
    public static final String ENCRYPTED_TRANSACTIONS_COUNT = "dHJhbnNhY3Rpb25zX2NvdW50"; // transactions_count
    public static final String ENCRYPTED_AD_REWARDS = "YWRfcmV3YXJkcw=="; // ad_rewards
    public static final String ENCRYPTED_REFERRAL_REWARDS = "cmVmZXJyYWxfcmV3YXJkcw=="; // referral_rewards

    // قيم مشوشة
    private static final int OBFUSCATED_FREE_LIMIT = SecurityUtils.ObfuscationHelper.obfuscateInt(100, 0);
    private static final int OBFUSCATED_MAX_DEVICES = SecurityUtils.ObfuscationHelper.obfuscateInt(2, 1);
    private static final int OBFUSCATED_PREMIUM_LIMIT = SecurityUtils.ObfuscationHelper.obfuscateInt(Integer.MAX_VALUE, 2);

    private final Context context;
    private static SecureLicenseManager instance;
    private SharedPreferences encryptedPrefs;
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;

    private SecureLicenseManager(Context context) {
        this.context = context;
        this.auth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
        initializeSecurePreferences();
    }

    public static synchronized SecureLicenseManager getInstance(Context context) {
        if (instance == null) {
            instance = new SecureLicenseManager(context);
        }
        return instance;
    }

    // في SecureLicenseManager.java
    private static final String ENCRYPTED_LAST_SYNC = "bGFzdF9zeW5j"; // last_sync
    private static final String ENCRYPTED_PENDING_COUNT = "cGVuZGluZ19jb3VudA=="; // pending_count


    /**
     * حفظ وقت آخر مزامنة
     */
    public void setLastSyncTime(long timestamp) {
        try {
            String encryptedTimestamp = SecurityUtils.encryptText(String.valueOf(timestamp), 8);
            encryptedPrefs.edit()
                    .putString(new String(Base64.decode(ENCRYPTED_LAST_SYNC, Base64.DEFAULT)), encryptedTimestamp)
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "خطأ في حفظ وقت المزامنة", e);
        }
    }

    /**
     * الحصول على وقت آخر مزامنة
     */
    public long getLastSyncTime() {
        try {
            String encryptedValue = encryptedPrefs.getString(
                    new String(Base64.decode(ENCRYPTED_LAST_SYNC, Base64.DEFAULT)),
                    SecurityUtils.encryptText("0", 8)
            );
            String decryptedValue = SecurityUtils.decryptText(encryptedValue, 8);
            return Long.parseLong(decryptedValue);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * حفظ عدد العمليات المنتظرة
     */
    public void setPendingTransactionsCount(int count) {
        try {
            String encryptedCount = SecurityUtils.encryptText(String.valueOf(count), 9);
            encryptedPrefs.edit()
                    .putString(new String(Base64.decode(ENCRYPTED_PENDING_COUNT, Base64.DEFAULT)), encryptedCount)
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "خطأ في حفظ عدد العمليات المنتظرة", e);
        }
    }

    /**
     * الحصول على عدد العمليات المنتظرة
     */
    public int getPendingTransactionsCount() {
        try {
            String encryptedValue = encryptedPrefs.getString(
                    new String(Base64.decode(ENCRYPTED_PENDING_COUNT, Base64.DEFAULT)),
                    SecurityUtils.encryptText("0", 9)
            );
            String decryptedValue = SecurityUtils.decryptText(encryptedValue, 9);
            return Integer.parseInt(decryptedValue);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * التحقق من الحاجة إلى المزامنة
     */
    public boolean needsSync() {
        long lastSync = getLastSyncTime();
        long currentTime = System.currentTimeMillis();
        // مزامنة كل ساعة على الأقل أو إذا كان هناك عمليات منتظرة
        return (currentTime - lastSync) > 3600000 || getPendingTransactionsCount() > 0;
    }

    /**
     * تهيئة التخزين المشفر
     */
    private void initializeSecurePreferences() {
        try {
            // التحقق من سلامة البيئة
            if (!SecurityUtils.isSecureEnvironment()) {
                Log.w(TAG, "غير آمن: تم اكتشاف بيئة غير آمنة");
                // يمكن إضافة إجراءات إضافية هنا
            }

            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            encryptedPrefs = EncryptedSharedPreferences.create(
                    PREFS_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "خطأ في تهيئة التخزين المشفر", e);
            // استخدام SharedPreferences عادي كبديل (مع تحذير)
            encryptedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    /**
     * حفظ بيانات الترخيص في التخزين المشفر
     */

    public void saveLicenseData1(int maxTransactions, int transactionsCount,
                                 int adRewards, int referralRewards, boolean isPremium) {
        try {
            String encryptedMaxTransactions = SecurityUtils.encryptText(String.valueOf(maxTransactions), 3);
            String encryptedTransactionsCount = SecurityUtils.encryptText(String.valueOf(transactionsCount), 4);
            String encryptedAdRewards = SecurityUtils.encryptText(String.valueOf(adRewards), 5);
            String encryptedReferralRewards = SecurityUtils.encryptText(String.valueOf(referralRewards), 6);
            String encryptedIsPremium = SecurityUtils.encryptText(String.valueOf(isPremium), 7);

            encryptedPrefs.edit()
                    .putString(new String(Base64.decode(ENCRYPTED_MAX_TRANSACTIONS, Base64.DEFAULT)), encryptedMaxTransactions)
                    .putString(new String(Base64.decode(ENCRYPTED_TRANSACTIONS_COUNT, Base64.DEFAULT)), encryptedTransactionsCount)
                    .putString(new String(Base64.decode(ENCRYPTED_AD_REWARDS, Base64.DEFAULT)), encryptedAdRewards)
                    .putString(new String(Base64.decode(ENCRYPTED_REFERRAL_REWARDS, Base64.DEFAULT)), encryptedReferralRewards)
                    .putString(new String(Base64.decode(ENCRYPTED_KEY_PREMIUM, Base64.DEFAULT)), encryptedIsPremium)
                    .apply();

            Log.d(TAG, "تم حفظ بيانات الترخيص في التخزين المشفر");
        } catch (Exception e) {
            Log.e(TAG, "خطأ في حفظ بيانات الترخيص", e);
        }
    }

    /**
     * استيراد بيانات الترخيص من Firestore وحفظها مشفرة محلياً
     */

    /**
     * الحصول على عدد المعاملات المتبقية
     */
    public int getRemainingTransactions() {
        try {
            boolean isPremium = isSecurePremium();
            if (isPremium) {
                return Integer.MAX_VALUE;
            }

            int maxTransactions = getIntValue(ENCRYPTED_MAX_TRANSACTIONS, 3, 0);
            int transactionsCount = getIntValue(ENCRYPTED_TRANSACTIONS_COUNT, 4, 0);
            int adRewards = getIntValue(ENCRYPTED_AD_REWARDS, 5, 0);
            int referralRewards = getIntValue(ENCRYPTED_REFERRAL_REWARDS, 6, 0);

            int totalAvailable = maxTransactions + adRewards + referralRewards;
            return Math.max(0, totalAvailable - transactionsCount);
        } catch (Exception e) {
            Log.e(TAG, "خطأ في حساب المعاملات المتبقية", e);
            return 0;
        }
    }

    /**
     * زيادة عداد المعاملات
     */
    public void incrementTransactionsCount1() {
        try {
            int currentCount = getIntValue(ENCRYPTED_TRANSACTIONS_COUNT, 4, 0);
            String encryptedCount = SecurityUtils.encryptText(String.valueOf(currentCount + 1), 4);

            encryptedPrefs.edit()
                    .putString(new String(Base64.decode(ENCRYPTED_TRANSACTIONS_COUNT, Base64.DEFAULT)), encryptedCount)
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "خطأ في زيادة عداد المعاملات", e);
        }
    }

    /**
     * تقليل مكافآت الإعلانات
     */
    public void decrementAdRewards1() {
        try {
            int currentRewards = getIntValue(ENCRYPTED_AD_REWARDS, 5, 0);
            if (currentRewards > 0) {
                String encryptedRewards = SecurityUtils.encryptText(String.valueOf(currentRewards - 1), 5);

                encryptedPrefs.edit()
                        .putString(new String(Base64.decode(ENCRYPTED_AD_REWARDS, Base64.DEFAULT)), encryptedRewards)
                        .apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "خطأ في تقليل مكافآت الإعلانات", e);
        }
    }

    public void decrementReferralRewards1() {
        try {
            int currentReferralRewards = getIntValue(ENCRYPTED_REFERRAL_REWARDS, 6, 0);
            if (currentReferralRewards > 0) {
                String encryptedReferralRewards = SecurityUtils.encryptText(String.valueOf(currentReferralRewards - 1), 5);

                encryptedPrefs.edit()
                        .putString(new String(Base64.decode(ENCRYPTED_REFERRAL_REWARDS, Base64.DEFAULT)), encryptedReferralRewards)
                        .apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "خطأ في تقليل مكافآت الدعوات", e);
        }
    }

    // دالة مساعدة للحصول على قيمة int من التخزين المشفر
    public int getIntValue(String encryptedKey, int keyIndex, int defaultValue) {
        try {
            String encryptedValue = encryptedPrefs.getString(
                    new String(Base64.decode(encryptedKey, Base64.DEFAULT)),
                    SecurityUtils.encryptText(String.valueOf(defaultValue), keyIndex)
            );
            String decryptedValue = SecurityUtils.decryptText(encryptedValue, keyIndex);
            return Integer.parseInt(decryptedValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * الحصول على بيانات الترخيص كسلسلة نصية للتسجيل (لأغراض التصحيح فقط)
     */
    public String getLicenseDataForLogging() {
        try {
            int maxTransactions = getIntValue(ENCRYPTED_MAX_TRANSACTIONS, 3, 0);
            int transactionsCount = getIntValue(ENCRYPTED_TRANSACTIONS_COUNT, 4, 0);
            int adRewards = getIntValue(ENCRYPTED_AD_REWARDS, 5, 0);
            int referralRewards = getIntValue(ENCRYPTED_REFERRAL_REWARDS, 6, 0);
            boolean isPremium = isSecurePremium();

            return String.format("max_transactions=%d, transactions_count=%d, ad_rewards=%d, referral_rewards=%d, is_premium=%b",
                    maxTransactions, transactionsCount, adRewards, referralRewards, isPremium);
        } catch (Exception e) {
            return "خطأ في قراءة بيانات الترخيص";
        }
    }


    /**
     * توليد كود فريد آمن
     */
    private String generateSecureUniqueCode(FirebaseUser user) {
        String rawData = user.getUid() + user.getEmail() + System.currentTimeMillis() + getCurrentDeviceInfo().getDeviceId();
        String salt = "SecureSalt2024";
        return SecurityUtils.createSecureHash(rawData, salt);
    }

    /**
     * تحديث البيانات المحلية المشفرة
     */
    private void updateSecureLocalData(boolean isPremium, int quotesCount, int maxTransactions) {
        try {
            String encryptedPremium = SecurityUtils.encryptText(String.valueOf(isPremium), 0);
            String encryptedTransactionCount = SecurityUtils.encryptText(String.valueOf(quotesCount), 1);
            String encryptedMaxTransactions = SecurityUtils.encryptText(String.valueOf(maxTransactions), 2);

            encryptedPrefs.edit()
                    .putString(new String(Base64.decode(ENCRYPTED_KEY_PREMIUM, Base64.DEFAULT)), encryptedPremium)
                    .putString(new String(Base64.decode(ENCRYPTED_KEY_TRANSACTION_COUNT, Base64.DEFAULT)), encryptedTransactionCount)
                    .putString(new String(Base64.decode(ENCRYPTED_KEY_MAX_TRANSACTIONS, Base64.DEFAULT)), encryptedMaxTransactions)
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "خطأ في تحديث البيانات المحلية", e);
        }
    }

    /**
     * فحص حالة Premium
     */
    public boolean isSecurePremium() {
        try {
            String encryptedValue = encryptedPrefs.getString(
                    new String(Base64.decode(ENCRYPTED_KEY_PREMIUM, Base64.DEFAULT)),
                    SecurityUtils.encryptText("false", 0)
            );
            String decryptedValue = SecurityUtils.decryptText(encryptedValue, 0);
            return Boolean.parseBoolean(decryptedValue);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * الحصول على عدد عروض الأسعار المتبقية
     */
    public int getSecureRemainingTransactions() {
        if (isSecurePremium()) {
            return SecurityUtils.ObfuscationHelper.deobfuscateInt(OBFUSCATED_PREMIUM_LIMIT, 2);
        }

        try {
            String encryptedCount = encryptedPrefs.getString(
                    new String(Base64.decode(ENCRYPTED_KEY_TRANSACTION_COUNT, Base64.DEFAULT)),
                    SecurityUtils.encryptText("0", 1)
            );
            String encryptedMax = encryptedPrefs.getString(
                    new String(Base64.decode(ENCRYPTED_KEY_MAX_TRANSACTIONS, Base64.DEFAULT)),
                    SecurityUtils.encryptText(String.valueOf(getDeobfuscatedFreeLimit()), 2)
            );

            int currentCount = Integer.parseInt(SecurityUtils.decryptText(encryptedCount, 1));
            int maxTransactions = Integer.parseInt(SecurityUtils.decryptText(encryptedMax, 2));

            return Math.max(0, maxTransactions - currentCount);
        } catch (Exception e) {
            return 0;
        }
    }

    private static final String KEY_MAX_TRANSACTIONS = "encrypted_max_trans";
    private static final String KEY_DEVICES_NOS = "devices_count";
    private static final String KEY_TRANSACTIONS_COUNT = "encrypted_trans_count";
    private static final String KEY_Guest_TRANSACTIONS_COUNT = "encrypted_trans_count";
    private static final String KEY_AD_REWARDS = "encrypted_ad_rewards";
    private static final String KEY_REFERRAL_REWARDS = "encrypted_ref_rewards";
    private static final String KEY_IS_PREMIUM = "encrypted_is_premium";
    private static final String GUEST_USER = "guest_user";
    private static final String GUEST_UID = "guest_uid";


    private static final String KEY_USER_TYPE = "user_type";
    // --------------- NEW
    private static final String NEW_KEY_TRANSACTIONS_COUNT = "encrypted_trans_count";
    private static final String NEW_KEY_AD_REWARDS = "encrypted_ad_rewards";
    private static final String NEW_KEY_REFERRAL_REWARDS = "encrypted_ref_rewards";
    //---------DIFF_
    private static final String DIFF_KEY_TRANSACTIONS_COUNT = "encrypted_trans_count";
    private static final String DIFF_KEY_AD_REWARDS = "encrypted_ad_rewards";
    private static final String DIFF_KEY_REFERRAL_REWARDS = "encrypted_ref_rewards";
    private static final String KEY_LAST_MODIFIED = "last_modified";

    public String getUserType() {
        return encryptedPrefs.getString(KEY_USER_TYPE, "");
    }

    public void setKeyUserType(String userType) {
        encryptedPrefs.edit().putString(KEY_USER_TYPE, userType).apply();
    }

    private String convertTimestampToString(Object timestamp) {
        try {
            if (timestamp instanceof Timestamp) { return formatDate(((Timestamp) timestamp).toDate()); }
            else if (timestamp instanceof String) { return (String) timestamp; }
            else if (timestamp instanceof Date) { return formatDate((Date) timestamp); }
        } catch (Exception e) { Log.e("convertTimestampToString",e.toString()); }
        return "";
    }
    private Object last_login;
    public String getLast_login() { return convertTimestampToString(last_login); }
    public void setLast_login(Object created_at) { this.last_login = created_at; }
    private String formatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a z", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(date);
    }
    public static String getCurrentLocalDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a z", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date());
    }

    public void saveLicenseData(int maxTransactions, int transactionsCount,
                                int adRewards, int referralRewards, boolean isPremium,
                                long lastModified,Object last_login) {
        long localLastModified = getLastModified();

        if (lastModified > localLastModified) {
            encryptedPrefs.edit()
                    .putInt(KEY_MAX_TRANSACTIONS, maxTransactions)
                    .putInt(KEY_TRANSACTIONS_COUNT, transactionsCount)
                    .putInt(KEY_AD_REWARDS, adRewards)
                    .putInt(KEY_REFERRAL_REWARDS, referralRewards)
                    .putBoolean(KEY_IS_PREMIUM, isPremium)
                    .apply();
            setLast_login(last_login);
            Log.d(TAG, "تم حفظ بيانات الترخيص: " +
                    "transactions_count=" + transactionsCount +
                    ", ad_rewards=" + adRewards);
        }
    }


    public long getLastModified() {
        return encryptedPrefs.getLong(KEY_LAST_MODIFIED, 0);
    }


    public void saveLicenseDataNew(int transactionsCount,
                                   int adRewards, int referralRewards) {
        encryptedPrefs.edit()
                .putInt(NEW_KEY_TRANSACTIONS_COUNT, transactionsCount)
                .putInt(NEW_KEY_AD_REWARDS, adRewards)
                .putInt(NEW_KEY_REFERRAL_REWARDS, referralRewards)
                .apply();

        Log.d(TAG, "تم حفظ بيانات الترخيص: " +
                "transactions_count=" + transactionsCount +
                ", ad_rewards=" + adRewards);
    }


    // دوال القراءة
    public int getMaxTransactions() {
        return encryptedPrefs.getInt(KEY_MAX_TRANSACTIONS, 0);
    }



    public int getGuestTransactionsCount() {
        return encryptedPrefs.getInt(KEY_Guest_TRANSACTIONS_COUNT, 0);
    }
    public int getTransactionsCount() {
        return encryptedPrefs.getInt(KEY_TRANSACTIONS_COUNT, 0);
    }
    public int getAdRewards() {
        return encryptedPrefs.getInt(KEY_AD_REWARDS, 0);
    }

    public int getReferralRewards() {
        return encryptedPrefs.getInt(KEY_REFERRAL_REWARDS, 0);
    }

    public void setReferralRewards(int referralRewards) {
        encryptedPrefs.edit().putInt(KEY_REFERRAL_REWARDS, referralRewards).apply();
    }

    public int getDevicesNos() {
        return encryptedPrefs.getInt(KEY_DEVICES_NOS, 0);
    }

    public void setDevicesNos(int devicesNos) {
        encryptedPrefs.edit().putInt(KEY_DEVICES_NOS, devicesNos).apply();
    }

    public boolean isPremium() {
        return encryptedPrefs.getBoolean(KEY_IS_PREMIUM, false);
    }

    public boolean isGuest() {
        Log.d(TAG, "isGuest: " + isGuest);
        return encryptedPrefs.getBoolean(GUEST_USER, false);
    }
    public static boolean isGuest2() {
        Log.d(TAG, "isGuest: " + isGuest);
        return isGuest;
    }
    public void setGuest(boolean isComplete) {
        encryptedPrefs.edit().putBoolean(GUEST_USER, isComplete).apply();
        isGuest = isComplete;
    }

    public String guestUID() {
        return encryptedPrefs.getString(GUEST_UID, "");
    }

    public void setGuestUID(String uid) {
        encryptedPrefs.edit().putString(GUEST_UID, uid).apply();
    }

    // دوال التحديث
    public void incrementTransactionsCount() {
        int currentCount = getTransactionsCount();
        encryptedPrefs.edit()
                .putInt(KEY_TRANSACTIONS_COUNT, currentCount + 1)
                .apply();
        incrementTransactionsCountFirestore();
    }
    private void incrementTransactionsCountFirestore() {
        String users, uid;
        if (isGuest()) {
            users = "guests";
            uid = guestUID();
        } else {
            users = "users";
            uid = auth.getCurrentUser().getUid();
        }
        DocumentReference userDocRef = firestore.collection(users).document(uid);
        userDocRef.update("transactions_count", FieldValue.increment(1));
        userDocRef.update("lastModified", System.currentTimeMillis());
        Log.e(TAG, "incrementTransactionsCountFirestore");
    }
    public void decrementAdRewards() {
        int currentRewards = getAdRewards();
        if (currentRewards > 0) {
            encryptedPrefs.edit()
                    .putInt(KEY_AD_REWARDS, currentRewards - 1)
                    .apply();
            String users, uid;
            if (isGuest()) {
                users = "guests";
                uid = guestUID();
            } else {
                users = "users";
                uid = auth.getCurrentUser().getUid();
            }
            DocumentReference userDocRef = firestore.collection(users).document(uid);
            userDocRef.update("ad_rewards", FieldValue.increment(-1));
        }
    }
    public void decrementReferralRewards() {
        int currentReferralRewards = getReferralRewards();
        if (currentReferralRewards > 0) {
            encryptedPrefs.edit()
                    .putInt(KEY_REFERRAL_REWARDS, currentReferralRewards - 1)
                    .apply();
            String users, uid;
            if (isGuest()) {
                users = "guests";
                uid = guestUID();
            } else {
                users = "users";
                uid = auth.getCurrentUser().getUid();
            }
            DocumentReference userDocRef = firestore.collection(users).document(uid);
            userDocRef.update("referral_rewards", FieldValue.increment(-1));
        }
    }
    public void incrementGuestTransactionsCount() {
        int currentCount = getTransactionsCount();
        encryptedPrefs.edit()
                .putInt(KEY_Guest_TRANSACTIONS_COUNT, currentCount + 1)
                .apply();
        incrementTransactionsCountFirestore();
    }

    public void clearLicenseData() {
        encryptedPrefs.edit().clear().apply();
    }

    /**
     * استهلاك عرض سعر واحد
     */
    public boolean consumeSecureTransaction() {
        if (isSecurePremium()) {
            return true; // المستخدمون المميزون لديهم عروض غير محدودة
        }

        if (getSecureRemainingTransactions() <= 0) {
            return false; // لا توجد عروض متبقية
        }

        try {
            String encryptedCount = encryptedPrefs.getString(
                    new String(Base64.decode(ENCRYPTED_KEY_TRANSACTION_COUNT, Base64.DEFAULT)),
                    SecurityUtils.encryptText("0", 1)
            );
            int currentCount = Integer.parseInt(SecurityUtils.decryptText(encryptedCount, 1));

            String newEncryptedCount = SecurityUtils.encryptText(String.valueOf(currentCount + 1), 1);
            encryptedPrefs.edit()
                    .putString(new String(Base64.decode(ENCRYPTED_KEY_TRANSACTION_COUNT, Base64.DEFAULT)), newEncryptedCount)
                    .apply();

            return true;
        } catch (Exception e) {
            Log.e(TAG, "خطأ في استهلاك عرض السعر", e);
            return false;
        }
    }

    /**
     * الحصول على معلومات الجهاز الحالي
     */
    private DeviceInfo getCurrentDeviceInfo() {
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.setDeviceId(SecurityUtils.createSecureHash(
                android.provider.Settings.Secure.getString(context.getContentResolver(),
                        android.provider.Settings.Secure.ANDROID_ID), "DeviceSalt"));
        deviceInfo.setDeviceName(android.os.Build.MODEL);
        deviceInfo.setDeviceModel(android.os.Build.MODEL);
        deviceInfo.setAndroidVersion(android.os.Build.VERSION.RELEASE);
        return deviceInfo;
    }

    /**
     * الحصول على الحد المجاني بعد إلغاء التشويش
     */
    private int getDeobfuscatedFreeLimit() {
        return SecurityUtils.ObfuscationHelper.deobfuscateInt(OBFUSCATED_FREE_LIMIT, 0);
    }

    /**
     * نتيجة فحص الترخيص الآمن
     */
    public static class SecureLicenseResult {
        private final boolean success;
        private final String message;
        private List<DeviceInfo> authorizedDevices;
        private UserLicense license;

        public SecureLicenseResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public SecureLicenseResult(boolean success, String message, List<DeviceInfo> authorizedDevices) {
            this.success = success;
            this.message = message;
            this.authorizedDevices = authorizedDevices;
        }

        public SecureLicenseResult(boolean success, String message, UserLicense license) {
            this.success = success;
            this.message = message;
            this.license = license;
            this.authorizedDevices = license != null ? license.getAuthorizedDevices() : null;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public List<DeviceInfo> getAuthorizedDevices() {
            return authorizedDevices;
        }

        public UserLicense getLicense() {
            return license;
        }

        public boolean isPremium() {
            return license != null && license.isPremium();
        }

        public boolean hasDeviceLimit() {
            int maxDevices = SecurityUtils.ObfuscationHelper.deobfuscateInt(
                    SecurityUtils.ObfuscationHelper.obfuscateInt(2, 1), 1);
            return authorizedDevices != null && authorizedDevices.size() >= maxDevices;
        }
    }
}