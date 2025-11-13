package com.hpp.daftree.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.hpp.daftree.R;
import com.hpp.daftree.database.DeviceInfo;
import com.hpp.daftree.database.User;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class LicenseManager {
    private static final String TAG = "LicenseManager";
    private static final String PREFS_NAME = "secure_license_prefs";
    private static final String KEY_IS_PREMIUM = "is_premium";
    public static final String KEY_DEVICE_ID = "device_id";
    private static final String USERS_COLLECTION = "users";
    private static final String PREFS_DAILY_LIMIT = "daily_limit_prefs";
    public static final int FREE_TRANSACTION_LIMIT = 100;
    public static final int Successful_Referrals = 0;
    public static final int MAX_DEVICES = 2; // حد الأجهزة للمستخدم المجاني والمميز
    public static final int AD_REWARD_TRANSACTIONS = 3;
    public static final int REFERRAL_REWARD_REFERRER = 5;
    public static final int REFERRAL_REWARD_REFEREE = 3;
    public static final int DAILY_FREE_LIMIT_FOR_EXPIRED_USERS = 3; // <-- جديد: الحصة اليومية
    public static final int FREE_GUEST_TRANSACTION_DAILY = 10;

    private final Context context;
    private final SharedPreferences securePrefs;
    private final FirebaseFirestore firestore;
    private final SharedPreferences dailyLimitPrefs;
    private final FirebaseAuth auth;
    private AuthStateListener authStateListener;
    private ListenerRegistration licenseListener;
    private static LicenseManager instance;
    public interface AuthStateListener {
        void onSignedOut();
    }

    public LicenseManager(Context context) {
        this.context = context;
        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        this.securePrefs = initializeSecurePrefs();
        this.dailyLimitPrefs = context.getSharedPreferences(PREFS_DAILY_LIMIT, Context.MODE_PRIVATE); // <-- جديد

    }

    public static synchronized LicenseManager getInstance(Context context) {
        if (instance == null) {
            instance = new LicenseManager(context);

        }
        return instance;
    }
    private SharedPreferences initializeSecurePrefs() {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(PREFS_NAME, masterKeyAlias, context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "فشل في إنشاء التخزين المشفر، سيتم استخدام التخزين العادي.", e);
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    public boolean isPremiumUser() { return securePrefs.getBoolean(KEY_IS_PREMIUM, false); }
    public void setPremiumStatus(boolean isPremium) { securePrefs.edit().putBoolean(KEY_IS_PREMIUM, isPremium).apply(); }

    public String getDeviceId() {
        String deviceId = securePrefs.getString(KEY_DEVICE_ID, null);
        if (deviceId == null) {
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            String rawId = androidId + Build.MODEL + Build.MANUFACTURER + Build.FINGERPRINT;
            deviceId = hashString(rawId);
            securePrefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
        }
        return deviceId;
    }

    public DeviceInfo getCurrentDeviceInfo() {
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.setDeviceId(getDeviceId());
        deviceInfo.setDeviceName(Build.MANUFACTURER + " " + Build.MODEL);
        deviceInfo.setDeviceModel(Build.MODEL);
        deviceInfo.setAndroidVersion("Android " + Build.VERSION.RELEASE);
        deviceInfo.setRegisteredAt(DeviceInfo.getCurrentLocalDateTime());
        deviceInfo.setLastActiveAt(DeviceInfo.getCurrentLocalDateTime());
        deviceInfo.setActive(true);
        return deviceInfo;
    }

    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA-256 algorithm not available", e);
            return String.valueOf(input.hashCode());
        }
    }

    public String generateUniquePurchaseCode() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) return null;
        String input = user.getUid() + user.getEmail() + getDeviceId() + System.currentTimeMillis();
        String hashedData = hashString(input).substring(0, 16).toUpperCase();
        return "HPP-" + hashedData.substring(0, 4) + "-" + hashedData.substring(4, 8) + "-" +
                hashedData.substring(8, 12) + "-" + hashedData.substring(12, 16);
    }

    // --- دوال إدارة العمليات المحدثة ---

    public boolean canCreateTransaction1(User user) {
        if (user == null) return false;
        if (user.isIs_premium()) return true;

        int totalRewards = user.getAd_rewards() + user.getReferral_rewards();
        int totalTransactionsAvailable = FREE_TRANSACTION_LIMIT + totalRewards;
        return user.getTransactions_count() < totalTransactionsAvailable;
    }
    /**
     * الدالة المحورية الجديدة للتحقق من إمكانية إنشاء عملية.
     * @param user كائن المستخدم الحالي من Firestore.
     * @return true إذا كان يمكنه إنشاء عملية جديدة.
     */
    public boolean canCreateTransaction22(User user) {
        if (user == null) {
            Log.w(TAG, "User object is null, denying transaction.");
            return false;
        }

        // 1. المستخدم المميز يمكنه دائمًا إنشاء عمليات
//        if (user.isIs_premium()) {
//            return true;
//        }
        if (isPremiumUser()) {
            return true;
        }
        // 2. التحقق من الباقة الأساسية والمكافآت
        Log.e(TAG, "user.getTransactions_count: " + user.getTransactions_count()+ "\n"+
                "user.getAd_rewards: " + user.getAd_rewards() + "\n"+
                "user.getReferral_rewards: " + user.getReferral_rewards());
        if (user.getTransactions_count() < FREE_TRANSACTION_LIMIT) {
            return true;
        } else if (user.getAd_rewards() > 0) {
            user.setAd_rewards(user.getAd_rewards() - 1);
            return true;
        } else if (user.getReferral_rewards() > 0) {
            user.setReferral_rewards(user.getReferral_rewards() - 1);
            return true;
        }
        int totalRewards = user.getAd_rewards() + user.getReferral_rewards();
//        int totalTransactionsAvailable = FREE_TRANSACTION_LIMIT + totalRewards;
//        if (user.getTransactions_count() < totalTransactionsAvailable) {
//            return true; // لا يزال لديه رصيد في الباقة
//        }

        // 3. إذا استنفد الباقة، تحقق من الحصة اليومية
//        return checkDailyLimit();
        return  false;
    }
//    public boolean canCreateTransaction() {
//        SecureLicenseManager secureLicenseManager = new SecureLicenseManager(context);
//
//        // التحقق من حالة Premium أولاً
//        if (secureLicenseManager.isSecurePremium()) {
//            return true;
//        }
//
//        int transactionsCount = secureLicenseManager.getIntValue(
//                SecureLicenseManager.ENCRYPTED_TRANSACTIONS_COUNT, 4, 0);
//        int adRewards = secureLicenseManager.getIntValue(
//                SecureLicenseManager.ENCRYPTED_AD_REWARDS, 5, 0);
//        int referralRewards = secureLicenseManager.getIntValue(
//                SecureLicenseManager.ENCRYPTED_REFERRAL_REWARDS, 6, 0);
//
//        Log.d(TAG, "بيانات الترخيص: transactions_count=" + transactionsCount +
//                ", ad_rewards=" + adRewards + ", referral_rewards=" + referralRewards);
//
//        // التحقق من الحدود والمكافآت
//        if (transactionsCount < LicenseManager.FREE_TRANSACTION_LIMIT) {
//            return true;
//        } else if (adRewards > 0) {
//            secureLicenseManager.decrementAdRewards();
//            return true;
//        } else if (referralRewards > 0) {
//            // يمكنك إضافة دالة decrementReferralRewards في SecureLicenseManager
//            return true;
//        }
//
//        // التحقق من الحد اليومي
//        LicenseManager licenseManager = new LicenseManager(context);
//        return licenseManager.checkDailyLimit();
//    }
//    public void incrementTransactionCountSecure() {
//        SecureLicenseManager.getInstance(context).incrementTransactionsCount();
//    }

    private static final String GUEST_UsageCount = "usageCount";
    public int getGuestUsageCount() {
        return securePrefs.getInt(GUEST_UsageCount, 1);
    }

    public void setGuestUsageCountt(int usageCount) {
        securePrefs.edit().putInt(GUEST_UsageCount, usageCount).apply();
    }
public boolean canCreateTransaction() {
    // القراءة من التخزين المشفر بدلاً من User object
    SecureLicenseManager licenseData = SecureLicenseManager.getInstance(context);

    int transactionsCount = licenseData.getTransactionsCount();
    int adRewards = licenseData.getAdRewards();
    int referralRewards = licenseData.getReferralRewards();
    boolean isPremium = licenseData.isPremium();
    boolean isGuest = licenseData.isGuest();
    int freeLimit ;
    if(getGuestUsageCount() < 3){
        freeLimit  = FREE_TRANSACTION_LIMIT;
    }else{
        freeLimit=0;
    }

//    if(isGuest){
//        {
//            incrementGuestDailyCounter();
//        }
//        return checkGuestDailyLimit();
//    }

    Log.d(TAG, "بيانات الترخيص: transactions_count=" + transactionsCount +
            ", ad_rewards=" + adRewards +
            ", referral_rewards=" + referralRewards);

    if (isPremium) {
        return true;
    }
    if (transactionsCount < freeLimit) {
        return true;
    } else if (adRewards > 0) {
        licenseData.decrementAdRewards();
        return true;
    } else if (referralRewards > 0) {
        licenseData.decrementReferralRewards();
        return true;
    }else {
       incrementDailyCounter();
    }
    return checkDailyLimit();
}

    public void incrementTransactionCount() {
        SecureLicenseManager.getInstance(context).incrementTransactionsCount();
    }
    public void incrementGuestTransactionCount() {
        SecureLicenseManager.getInstance(context).incrementGuestTransactionsCount();
    }
    public boolean hasFreePlanExpired(User user) {
        if (user == null || user.isIs_premium()) return false;
        return SecureLicenseManager.getInstance(context).getTransactionsCount() >= FREE_TRANSACTION_LIMIT;
    }
    /**
     * زيادة عداد العمليات وتحديث الحصة اليومية إذا لزم الأمر.
     * @param user كائن المستخدم الحالي.
     */
    public void incrementTransactionCount(User user) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null || user == null) return;

        DocumentReference userDocRef = firestore.collection(USERS_COLLECTION).document(firebaseUser.getUid());
        userDocRef.update("transactions_count", FieldValue.increment(1));
        user.setTransactions_count(user.getTransactions_count() + 1);
        user.setLastModified(System.currentTimeMillis());
        // إذا كان المستخدم قد استنفد باقته، قم بزيادة عداد الحصة اليومية
        if (user.getTransactions_count() >= FREE_TRANSACTION_LIMIT) {
            incrementDailyCounter();
        }
    }

    // --- دوال خاصة بإدارة الحصة اليومية ---

    /**
     * يتحقق مما إذا كان المستخدم لا يزال يملك عمليات في حصته اليومية.
     * @return true إذا كان بإمكانه إجراء عملية.
     */
    public boolean checkDailyLimit() {
        resetDailyCounter();
        int dailyCount = dailyLimitPrefs.getInt("count", 0);
        Log.d(TAG, "Daily count: " + dailyCount + "/" + DAILY_FREE_LIMIT_FOR_EXPIRED_USERS);
        return dailyCount < DAILY_FREE_LIMIT_FOR_EXPIRED_USERS;
    }

    /**
     * يزيد عداد العمليات اليومية بواحد.
     */
    private void incrementDailyCounter() {
        resetDailyCounter();
        int currentCount = dailyLimitPrefs.getInt("count", 0);
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        dailyLimitPrefs.edit()
                .putInt("count", currentCount + 1)
                .putString("date", todayDate)
                .apply();
    }
    private void incrementGuestDailyCounter() {
        resetDailyCounter();
        int currentCount = dailyLimitPrefs.getInt("count", 0);
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        dailyLimitPrefs.edit()
                .putInt("count", currentCount + 1)
                .putString("date", todayDate)
                .apply();
    }
    public boolean checkGuestDailyLimit() {
        resetDailyCounter();
        int dailyCount = dailyLimitPrefs.getInt("count", 0);
        Log.d(TAG, "Daily count: " + dailyCount + "/" + FREE_GUEST_TRANSACTION_DAILY);
        return dailyCount < FREE_GUEST_TRANSACTION_DAILY;
    }
    /**
     * يقوم بتصفير عداد الحصة اليومية إذا كان التاريخ الحالي مختلفًا عن تاريخ آخر عملية.
     */
    
    public void resetDailyCounter() {
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String lastUsedDate = dailyLimitPrefs.getString("date", "");
        if (!todayDate.equals(lastUsedDate)) {
            dailyLimitPrefs.edit()
                    .putInt("count", 0)
                    .putString("date", todayDate)
                    .apply();
            Log.d(TAG, "تم تصفير عداد الحصة اليومية لليوم الجديد.");
        }
    }

    public void incrementTransactionCount1() {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) return;
        DocumentReference userDocRef = firestore.collection(USERS_COLLECTION).document(firebaseUser.getUid());
        userDocRef.update("transactions_count", FieldValue.increment(1));
    }

    public void addAdRewardTransactions(User user) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) return;
        DocumentReference userDocRef = firestore.collection(USERS_COLLECTION).document(firebaseUser.getUid());
        userDocRef.update("ad_rewards", FieldValue.increment(AD_REWARD_TRANSACTIONS));
    }

    public void startLicenseListener(Consumer<LicenseCheckResult> callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();

        if (firebaseUser == null) {
            callback.accept(new LicenseCheckResult(false, context.getString(R.string.ar_long_text_15), null, false, false));
            return;
        }

        String currentDeviceId = getDeviceId();
        DocumentReference userDocRef = firestore.collection(USERS_COLLECTION).document(firebaseUser.getUid());

        // ✅ إلغاء أي Listeners قديمة قبل إنشاء جديد
        if (licenseListener != null) {
            licenseListener.remove();
        }

        licenseListener = userDocRef.addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                callback.accept(new LicenseCheckResult(false, context.getString(R.string.fail_read_user_data), null, false, false));
                return;
            }

            if (snapshot == null || !snapshot.exists()) {
                callback.accept(new LicenseCheckResult(false, context.getString(R.string.fail_read_user_data), null, false, false));
                return;
            }

            User user = snapshot.toObject(User.class);
            if (user == null) {
                callback.accept(new LicenseCheckResult(false, context.getString(R.string.fail_read_user_data), null, false, false));
                return;
            }

            SecureLicenseManager secure = SecureLicenseManager.getInstance(context);
            setPremiumStatus(user.isIs_premium());
            secure.setDevicesNos(user.getDevices().size());
            // ✅ الإجراءات الإضافية التي طلبتها
            int maxTransactions = snapshot.getLong("max_transactions") != null ?
                    snapshot.getLong("max_transactions").intValue() : 0;
            int transactionsCount = snapshot.getLong("transactions_count") != null ?
                    snapshot.getLong("transactions_count").intValue() : 0;
            int adRewards = snapshot.getLong("ad_rewards") != null ?
                    snapshot.getLong("ad_rewards").intValue() : 0;
            int referralRewards = snapshot.getLong("referral_rewards") != null ?
                    Objects.requireNonNull(snapshot.getLong("referral_rewards")).intValue() : 0;
            boolean isPremium = Boolean.TRUE.equals(snapshot.getBoolean("is_premium"));

//            SecureLicenseManager.getInstance(context)
//                    .saveLicenseData(maxTransactions, transactionsCount,
//                            adRewards, referralRewards, isPremium);

            // ✅ التحقق: هل الجهاز الحالي موجود في الأجهزة؟
            if (user.getDevices().containsKey(currentDeviceId)) {
                userDocRef.update(
                        "devices." + currentDeviceId + ".lastActiveAt", DeviceInfo.getCurrentLocalDateTime(),
                        "devices." + currentDeviceId + ".active", true
                );
                callback.accept(new LicenseCheckResult(true, "الجهاز مرخص.", user, false, true));
            } else {
                boolean deviceLimitExceeded = user.getDevices().size() >= MAX_DEVICES;
                callback.accept(new LicenseCheckResult(true, context.getString(R.string.device_unlecinced), user, deviceLimitExceeded, false));
            }
        });
    }

    // لإيقاف الاستماع عند عدم الحاجة (مثلاً onDestroy)
    public void stopLicenseListener() {
        if (licenseListener != null) {
            licenseListener.remove();
            licenseListener = null;
        }
    }


    // --- عمليات Firestore ---
   public CompletableFuture<LicenseCheckResult> checkLicense() {
        CompletableFuture<LicenseCheckResult> future = new CompletableFuture<>();
        FirebaseUser firebaseUser = auth.getCurrentUser();

        if (firebaseUser == null) {
            future.complete(new LicenseCheckResult(false, context.getString(R.string.ar_long_text_15), null, false, false));
            return future;
        }

        DocumentReference userDocRef = firestore.collection(USERS_COLLECTION).document(firebaseUser.getUid());
        String currentDeviceId = getDeviceId();

        userDocRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || !task.getResult().exists()) {
                future.complete(new LicenseCheckResult(false, context.getString(R.string.fail_read_user_data), null, false, false));
                return;
            }
            User user = task.getResult().toObject(User.class);
            if (user == null) {
                future.complete(new LicenseCheckResult(false, context.getString(R.string.fail_read_user_data), null, false, false));
                return;
            }
            SecureLicenseManager secure = SecureLicenseManager.getInstance(context);
            setPremiumStatus(user.isIs_premium());
            secure.setDevicesNos(user.getDevices().size());
            // ✅ التحقق الأساسي: هل الجهاز الحالي موجود في قائمة الأجهزة المرخصة؟
            if (user.getDevices().containsKey(currentDeviceId)) {
                // نعم، الجهاز مرخص. قم بتحديث نشاطه.
                userDocRef.update("devices." + currentDeviceId + ".lastActiveAt", DeviceInfo.getCurrentLocalDateTime(),
                        "devices." + currentDeviceId + ".active", true);
                future.complete(new LicenseCheckResult(true, "الجهاز مرخص.", user, false, true));
            } else {
                // لا، الجهاز غير موجود في القائمة.
                boolean hasSpace = user.getDevices().size() < MAX_DEVICES;

                // إضافة تحقق إضافي: هل تم تجاوز الحد الأقصى للأجهزة؟
                boolean deviceLimitExceeded = user.getDevices().size() >= MAX_DEVICES;

                future.complete(new LicenseCheckResult(true, context.getString(R.string.device_unlecinced), user, deviceLimitExceeded, false));
            }
        });

        return future;
    }
    public static String getDeviceId(Context context) {
        // طريقة بديلة للحصول على deviceId
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }
    public Task<Void> removeDevice(String deviceId) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            return Tasks.forException(new Exception(context.getString(R.string.ar_long_text_15)));
        }
        DocumentReference userDocRef = firestore.collection(USERS_COLLECTION).document(firebaseUser.getUid());
        // الطريقة الآمنة لحذف عنصر من خريطة
        return userDocRef.update("devices." + deviceId, FieldValue.delete());
    }
    public Task<Void> removeDevice1(String deviceId) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) return null;
        DocumentReference userDocRef = firestore.collection(USERS_COLLECTION).document(firebaseUser.getUid());
        // استخدام FieldValue.delete لحذف حقل معين من الخريطة
        return userDocRef.update("devices." + deviceId, FieldValue.delete());
    }

    public void setAuthStateListener(AuthStateListener listener) { this.authStateListener = listener; }

    public void signOutAndClearData() {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser != null) {
            String deviceId = getDeviceId();
            firestore.collection(USERS_COLLECTION).document(firebaseUser.getUid())
                    .update("devices." + deviceId + ".active", false);
        }
        auth.signOut();
        securePrefs.edit().clear().apply();
        Log.d(TAG, "تم تسجيل الخروج ومسح البيانات المحلية.");
        if (authStateListener != null) authStateListener.onSignedOut();
    }
    public void clearDeviceData() {
        try {
            // مسح معرف الجهاز من التخزين المشفر
            securePrefs.edit().remove(KEY_DEVICE_ID).apply();

            // مسح بيانات الترخيص المحلية
            SharedPreferences prefs = context.getSharedPreferences("secure_license_prefs", Context.MODE_PRIVATE);
            prefs.edit().clear().apply();

            // مسح التخزين المشفر
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            SharedPreferences encryptedPrefs = EncryptedSharedPreferences.create(
                    "secure_license_prefs",
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            encryptedPrefs.edit().clear().apply();

            Log.d(TAG, "تم مسح بيانات الجهاز والترخيص بنجاح");
        } catch (Exception e) {
            Log.e(TAG, "خطأ في مسح بيانات الجهاز", e);
        }

    }

    public static class LicenseCheckResult {
        public final boolean success;
        public final String message;
        public final User user;
        public final boolean deviceLimitExceeded;
        public final boolean isCurrentDeviceAuthorized;

        public LicenseCheckResult(boolean success, String message, User user, boolean deviceLimitExceeded, boolean isCurrentDeviceAuthorized) {
            this.success = success;
            this.message = message;
            this.user = user;
            this.deviceLimitExceeded = deviceLimitExceeded;
            this.isCurrentDeviceAuthorized = isCurrentDeviceAuthorized;
        }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public User getUser() { return user; }
        public boolean isDeviceLimitExceeded() { return deviceLimitExceeded; }
        public boolean isPremium() { return user != null && user.isIs_premium(); }
        public boolean isCurrentDeviceAuthorized() { return isCurrentDeviceAuthorized; }
    }
}