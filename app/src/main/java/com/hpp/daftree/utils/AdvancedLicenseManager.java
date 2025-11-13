package com.hpp.daftree.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hpp.daftree.database.DeviceInfo;
import com.hpp.daftree.UserLicense;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * مدير الترخيص المتقدم مع حماية قوية ضد التهكير
 * يدعم ربط الحساب بجهازين كحد أقصى مع توليد كود مميز ثابت لكل جهاز
 */
public class AdvancedLicenseManager {
    private static final String TAG = "AdvancedLicenseManager";
    private static final String PREFS_NAME = "advanced_license_prefs";
    private static final String KEY_IS_PREMIUM = "is_premium";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_UNIQUE_CODE = "unique_code";
    private static final String KEY_LAST_SYNC = "last_sync";
    private static final String KEY_CODE_GENERATED = "code_generated";

    private static final String FIRESTORE_COLLECTION = "users";
    private static final int FREE_PLAN_LIMIT = 6;
    private static final int MAX_DEVICES = 2;

    // Static salt for consistent code generation - NEVER CHANGE THIS
    private static final String STATIC_SALT = "HPP_DATAFEED_APP_2026_SALT_KEY_FIXED";
    private static final String CODE_PREFIX = "HPP";

    private final Context context;
    private SharedPreferences encryptedPrefs;
    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;

    private UserLicense license;

    public AdvancedLicenseManager(Context context) {
        this.context = context;
        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        initializeEncryptedPrefs();
    }

    /**
     * تهيئة SharedPreferences المشفرة
     */
    private void initializeEncryptedPrefs() {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            encryptedPrefs = EncryptedSharedPreferences.create(
                PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to create encrypted preferences, using regular preferences", e);
            // Fallback to regular SharedPreferences if encryption fails
            encryptedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    /**
     * الحصول على Device ID الفريد والثابت للجهاز
     */
    public String getDeviceId() {
        String savedDeviceId = encryptedPrefs.getString(KEY_DEVICE_ID, null);
        if (savedDeviceId != null) {
            Log.d(TAG, "Using saved device ID: " + savedDeviceId.substring(0, 8) + "...");
            return savedDeviceId;
        }

        // Generate unique and consistent device ID based on hardware
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        String deviceModel = Build.MODEL;
        String deviceManufacturer = Build.MANUFACTURER;
        String buildSerial = Build.SERIAL;
        String buildFingerprint = Build.FINGERPRINT;

        // Create a consistent hash based on device hardware characteristics
        String rawId = androidId + deviceModel + deviceManufacturer + buildSerial + buildFingerprint;
        String hashedId = hashString(rawId);

        encryptedPrefs.edit().putString(KEY_DEVICE_ID, hashedId).apply();
        Log.d(TAG, "Generated new device ID: " + hashedId.substring(0, 8) + "...");
        return hashedId;
    }

    /**
     * تشفير النص باستخدام SHA-256
     */
    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA-256 algorithm not available", e);
            // Fallback to simple hash
            return String.valueOf(input.hashCode());
        }
    }

    /**
     * توليد الكود الفريد الثابت للمستخدم والجهاز
     * هذا الكود يجب أن يكون ثابت لنفس المستخدم ونفس الجهاز
     */
    public String generateUniqueCode() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No authenticated user found");
            return null;
        }

        String userUID = currentUser.getUid();
        String userEmail = currentUser.getEmail();
        String deviceId = getDeviceId();

        // Create unique key for this user-device combination
        String userDeviceKey = "code_" + userUID + "_" + deviceId;

        // Check if code already exists for this user-device combination
        String existingCode = encryptedPrefs.getString(userDeviceKey, null);
        if (existingCode != null && !existingCode.isEmpty()) {
            Log.d(TAG, "Using existing unique code for user-device combination");
            return existingCode;
        }

        // Generate new code based on user and device information
        String rawData = userUID + userEmail + deviceId + STATIC_SALT;
        String hashedData = hashString(rawData);

        // Format the code as XXXX-XXXX-XXXX-XXXX
        String formattedCode = formatUniqueCode(hashedData);

        // Save the code for this user-device combination
        encryptedPrefs.edit()
                .putString(userDeviceKey, formattedCode)
                .putString(KEY_UNIQUE_CODE, formattedCode) // For backward compatibility
                .putString(KEY_USER_ID, userUID)
                .putLong(KEY_CODE_GENERATED, System.currentTimeMillis())
                .apply();

        Log.d(TAG, "Generated new unique code: " + formattedCode);
        return formattedCode;
    }

    /**
     * تنسيق الكود الفريد بصيغة XXXX-XXXX-XXXX-XXXX
     */
    private String formatUniqueCode(String hashedData) {
        // Take first 16 characters of hash and format them
        String codeData = hashedData.substring(0, Math.min(16, hashedData.length())).toUpperCase();

        // Ensure we have exactly 16 characters
        while (codeData.length() < 16) {
            codeData += "0";
        }

        // Format as XXXX-XXXX-XXXX-XXXX
        return CODE_PREFIX + codeData.substring(0, 4) + "-" +
               codeData.substring(4, 8) + "-" +
               codeData.substring(8, 12) + "-" +
               codeData.substring(12, 16);
    }

    /**
     * الحصول على الكود الفريد المحفوظ
     */
    public String getSavedUniqueCode() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            return null;
        }

        String userUID = currentUser.getUid();
        String deviceId = getDeviceId();
        String userDeviceKey = "code_" + userUID + "_" + deviceId;

        return encryptedPrefs.getString(userDeviceKey, null);
    }

    /**
     * التحقق من وجود كود فريد محفوظ
     */
    public boolean hasUniqueCode() {
        String savedCode = getSavedUniqueCode();
        return savedCode != null && !savedCode.isEmpty();
    }

    /**
     * مسح الكود الفريد (للاختبار فقط)
     */
    public void clearUniqueCode() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            String userUID = currentUser.getUid();
            String deviceId = getDeviceId();
            String userDeviceKey = "code_" + userUID + "_" + deviceId;

            encryptedPrefs.edit()
                    .remove(userDeviceKey)
                    .remove(KEY_UNIQUE_CODE)
                    .remove(KEY_CODE_GENERATED)
                    .apply();

            Log.d(TAG, "Unique code cleared for testing");
        }
    }
    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss z", Locale.getDefault());
        // لا نغير الـ TimeZone، يظل على الإعداد المحلي للجهاز
        return sdf.format(new Date());
    }
    /**
     * إزالة جهاز من قائمة الأجهزة المصرح بها
     */
    public CompletableFuture<Boolean> removeDevice(String deviceId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        String currentTimestamp = getCurrentTimestamp();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            future.complete(false);
            return future;
        }

        firestore.collection(FIRESTORE_COLLECTION)
                .document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        UserLicense license = documentSnapshot.toObject(UserLicense.class);
                        if (license != null && license.getAuthorizedDevices() != null) {
                            List<DeviceInfo> devices = new ArrayList<>(license.getAuthorizedDevices());
                            devices.removeIf(device -> device.getDeviceId().equals(deviceId));
                            license.setAuthorizedDevices(devices);
                            license.setUpdatedAt(currentTimestamp);

                            firestore.collection(FIRESTORE_COLLECTION)
                                    .document(user.getUid())
                                    .set(license)
                                    .addOnSuccessListener(aVoid -> future.complete(true))
                                    .addOnFailureListener(e -> future.complete(false));
                        } else {
                            future.complete(false);
                        }
                    } else {
                        future.complete(false);
                    }
                })
                .addOnFailureListener(e -> future.complete(false));

        return future;
    }

    /**
     * الحصول على معلومات الجهاز الحالي
     */
    public DeviceInfo getCurrentDeviceInfo() {
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.setDeviceId(getDeviceId());
        deviceInfo.setDeviceName(Build.MODEL);
        deviceInfo.setDeviceModel(Build.MANUFACTURER + " " + Build.MODEL);
        deviceInfo.setAndroidVersion(Build.VERSION.RELEASE);
//        deviceInfo.setRegisteredAt(System.currentTimeMillis());
//        deviceInfo.setLastActiveAt(System.currentTimeMillis());
        deviceInfo.setActive(true);

        return deviceInfo;
    }

    /**
     * فحص الترخيص من Firestore
     */
    public CompletableFuture<LicenseCheckResult> checkLicense() {
        CompletableFuture<LicenseCheckResult> future = new CompletableFuture<>();

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            future.complete(new LicenseCheckResult(false, "المستخدم غير مسجل الدخول", null));
            return future;
        }

        // إعادة تحميل بيانات المستخدم
        currentUser.reload().addOnCompleteListener(reloadTask -> {
            if (!reloadTask.isSuccessful()) {
                future.complete(new LicenseCheckResult(false, "فشل تحديث بيانات المستخدم", null));
                return;
            }

            // استمرار عملية الفحص...
            firestore.collection("users").document(currentUser.getUid())
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {

                            DocumentSnapshot document = task.getResult();
                            if (document.exists()) {
                                boolean isPremium = document.getBoolean("isPremium") != null ?
                                        document.getBoolean("isPremium") : false;

                                // Update local preferences
                                encryptedPrefs.edit()
                                        .putBoolean(KEY_IS_PREMIUM, isPremium)
                                        .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                                        .apply();

                                List<DeviceInfo> devices = new ArrayList<>();
                                // Parse devices from Firestore if needed

                                future.complete(new LicenseCheckResult(true,
                                        isPremium ? "حساب مميز" : "حساب مجاني", devices));
                            } else {
                                future.complete(new LicenseCheckResult(false,
                                        "بيانات المستخدم غير موجودة في قاعدة البيانات", null));
                            }
                        } else {
                            Log.e(TAG, "Error checking license", task.getException());
                            future.complete(new LicenseCheckResult(false,
                                    "خطأ في التحقق من الترخيص: " + task.getException().getMessage(), null));
                        }
                    });
        });

        return future;
    }
    public void setPremiumStatus(boolean isPremium) {
        encryptedPrefs.edit().putBoolean(KEY_IS_PREMIUM, isPremium).apply();
    }
    public CompletableFuture<LicenseCheckResult> checkLicense1() {
        CompletableFuture<LicenseCheckResult> future = new CompletableFuture<>();

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            future.complete(new LicenseCheckResult(false, "المستخدم غير مسجل الدخول", null));
            return future;
        }

        String userUID = currentUser.getUid();

        firestore.collection("users").document(userUID)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            boolean isPremium = document.getBoolean("is_premium") != null ?
                                    document.getBoolean("is_premium") : false;

                            // Update local preferences
                            encryptedPrefs.edit()
                                    .putBoolean(KEY_IS_PREMIUM, isPremium)
                                    .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                                    .apply();

                            List<DeviceInfo> devices = new ArrayList<>();
                            // Parse devices from Firestore if needed

                            future.complete(new LicenseCheckResult(true,
                                    isPremium ? "حساب مميز" : "حساب مجاني", devices));
                        } else {
                            future.complete(new LicenseCheckResult(false,
                                    "بيانات المستخدم غير موجودة في قاعدة البيانات", null));
                        }
                    } else {
                        Log.e(TAG, "Error checking license", task.getException());
                        future.complete(new LicenseCheckResult(false,
                                "خطأ في التحقق من الترخيص: " + task.getException().getMessage(), null));
                    }
                });

        return future;
    }

    /**
     * تسجيل الخروج ومسح البيانات
     */
    public void signOutAndClearData() {
        encryptedPrefs.edit()
                .remove(KEY_IS_PREMIUM)
                .remove(KEY_USER_ID)
                .remove(KEY_LAST_SYNC)
                .apply();

        Log.d(TAG, "User data cleared after sign out");
    }

    /**
     * فئة نتيجة فحص الترخيص
     */
    public static class LicenseCheckResult {
        private final boolean success;
        private final String message;
        private final List<DeviceInfo> authorizedDevices;
        private UserLicense license;
        public LicenseCheckResult(boolean success, String message, List<DeviceInfo> authorizedDevices) {
            this.success = success;
            this.message = message;
            this.authorizedDevices = authorizedDevices;
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

        public UserLicense getLicense() { return license; }
        public boolean hasDeviceLimit() { return authorizedDevices != null && authorizedDevices.size() >= MAX_DEVICES; }
        public boolean isPremium() { return license != null && license.isPremium(); }

    }



    /**
     * التحقق من حالة المستخدم المميز
     */
    public boolean isPremiumUser() {
        return encryptedPrefs.getBoolean(KEY_IS_PREMIUM, false);
    }

}

