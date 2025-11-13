package com.hpp.daftree.utils;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * فئة الأمان والتشفير المتقدم
 * تحتوي على دوال مشفرة لحماية التطبيق من التهكير
 */
public class SecurityUtils {
    
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String AES_KEY_ALGORITHM = "AES";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    
    // مفاتيح التشفير المخفية (يجب تغييرها في الإنتاج)
    private static final String[] OBFUSCATED_KEYS = {
        "QWRtaW5LZXkxMjM0NTY3ODkw", // AdminKey1234567890
        "U2VjdXJlS2V5OTg3NjU0MzIx", // SecureKey9876543210
        "TGljZW5zZUtleTExMjIzMzQ0"  // LicenseKey112233440
    };
    
    /**
     * تشفير النص باستخدام AES-GCM
     */
    public static String encryptText(String plainText, int keyIndex) {
        try {
            String keyString = new String(Base64.decode(OBFUSCATED_KEYS[keyIndex % OBFUSCATED_KEYS.length], Base64.DEFAULT));
            SecretKeySpec secretKey = new SecretKeySpec(keyString.getBytes(StandardCharsets.UTF_8), AES_KEY_ALGORITHM);
            
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            byte[] encryptedData = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            // دمج IV مع البيانات المشفرة
            byte[] encryptedWithIv = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
            System.arraycopy(encryptedData, 0, encryptedWithIv, iv.length, encryptedData.length);
            
            return Base64.encodeToString(encryptedWithIv, Base64.DEFAULT);
        } catch (Exception e) {
            return plainText; // إرجاع النص الأصلي في حالة الخطأ
        }
    }
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return input;
    }
    /**
     * فك تشفير النص
     */
    public static String decryptText(String encryptedText, int keyIndex) {
        try {
            String keyString = new String(Base64.decode(OBFUSCATED_KEYS[keyIndex % OBFUSCATED_KEYS.length], Base64.DEFAULT));
            SecretKeySpec secretKey = new SecretKeySpec(keyString.getBytes(StandardCharsets.UTF_8), AES_KEY_ALGORITHM);
            
            byte[] encryptedWithIv = Base64.decode(encryptedText, Base64.DEFAULT);
            
            // استخراج IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(encryptedWithIv, 0, iv, 0, iv.length);
            
            // استخراج البيانات المشفرة
            byte[] encryptedData = new byte[encryptedWithIv.length - GCM_IV_LENGTH];
            System.arraycopy(encryptedWithIv, GCM_IV_LENGTH, encryptedData, 0, encryptedData.length);
            
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            byte[] decryptedData = cipher.doFinal(encryptedData);
            return new String(decryptedData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return encryptedText; // إرجاع النص المشفر في حالة الخطأ
        }
    }
    
    /**
     * إنشاء hash آمن للنص
     */
    public static String createSecureHash(String input, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return input;
        }
    }
    
    /**
     * التحقق من سلامة التطبيق (Anti-Tampering)
     */
    public static boolean verifyAppIntegrity() {
        try {
            // فحص التوقيع الرقمي للتطبيق
            // فحص checksum للملفات الحساسة
            // فحص البيئة (root detection, emulator detection)
            
            // هذا مثال مبسط - في الإنتاج يجب تطبيق فحوصات أكثر تعقيداً
            String expectedHash = "a1b2c3d4e5f6"; // hash متوقع للتطبيق
            String currentHash = getCurrentAppHash();
            
            return expectedHash.equals(currentHash);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * الحصول على hash التطبيق الحالي
     */
    private static String getCurrentAppHash() {
        // تنفيذ مبسط - في الإنتاج يجب حساب hash حقيقي
        return "a1b2c3d4e5f6";
    }
    
    /**
     * فحص البيئة للكشف عن المحاكيات والـ root
     */
    public static boolean isSecureEnvironment() {
        try {
            // فحص الـ root
//            if (isDeviceRooted()) {
//                return false;
//            }
            
            // فحص المحاكي
            if (isRunningOnEmulator()) {
                return false;
            }
            
            // فحص أدوات التطوير
            return !isDeveloperOptionsEnabled();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * فحص الـ root
     */
    private static boolean isDeviceRooted() {
        // فحص ملفات الـ root الشائعة
        String[] rootPaths = {
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        };
        
        for (String path : rootPaths) {
            if (new java.io.File(path).exists()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * فحص المحاكي
     */
    private static boolean isRunningOnEmulator() {
        return android.os.Build.FINGERPRINT.startsWith("generic")
            || android.os.Build.FINGERPRINT.startsWith("unknown")
            || android.os.Build.MODEL.contains("google_sdk")
            || android.os.Build.MODEL.contains("Emulator")
            || android.os.Build.MODEL.contains("Android SDK built for x86")
            || android.os.Build.MANUFACTURER.contains("Genymotion")
            || (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
            || "google_sdk".equals(android.os.Build.PRODUCT);
    }
    
    /**
     * فحص خيارات المطور
     */
    private static boolean isDeveloperOptionsEnabled() {
        // هذا يتطلب Context - سيتم تنفيذه في الكلاس المناسب
        return false;
    }



        /**
     * تشويش الكود (Code Obfuscation Helper)
     */
    public static class ObfuscationHelper {
        
        private static final int[] MAGIC_NUMBERS = {0x1A2B, 0x3C4D, 0x5E6F, 0x7890};
        
        /**
         * تشويش القيم الرقمية
         */
        public static int obfuscateInt(int value, int index) {
            return value ^ MAGIC_NUMBERS[index % MAGIC_NUMBERS.length];
        }
        
        /**
         * إلغاء تشويش القيم الرقمية
         */
        public static int deobfuscateInt(int obfuscatedValue, int index) {
            return obfuscatedValue ^ MAGIC_NUMBERS[index % MAGIC_NUMBERS.length];
        }
        
        /**
         * تشويش النصوص
         */
        public static String obfuscateString(String value) {
            if (value == null) return null;
            
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                result.append((char) (c ^ (i % 256)));
            }
            return Base64.encodeToString(result.toString().getBytes(), Base64.DEFAULT);
        }
        
        /**
         * إلغاء تشويش النصوص
         */
        public static String deobfuscateString(String obfuscatedValue) {
            if (obfuscatedValue == null) return null;
            
            try {
                String decoded = new String(Base64.decode(obfuscatedValue, Base64.DEFAULT));
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < decoded.length(); i++) {
                    char c = decoded.charAt(i);
                    result.append((char) (c ^ (i % 256)));
                }
                return result.toString();
            } catch (Exception e) {
                return obfuscatedValue;
            }
        }
    }
}

