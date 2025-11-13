package com.hpp.daftree.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Debug;
import android.provider.Settings;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class TamperDetection {

    // IMPORTANT: In a real-world scenario, this hash should NOT be hardcoded directly.
    // It should be fetched securely from a remote server, or heavily obfuscated/encrypted
    // and split into multiple parts within the application to make reverse engineering harder.
    // For demonstration, we'll use a placeholder and emphasize secure storage.
    private static final String SECURE_SIGNATURE_HASH = "1C:36:97:38:51:F5:85:01:A8:EA:6C:94:15:51:1C:EC:7A:0F:AB:1A:F8:35:73:2A:65:81:BA:62:67:27:39:0D";


    public static boolean isAppTampered(Context context) {
        return isSignatureChanged(context) || isDeviceRooted();
    }

    // This method specifically checks if Developer Options are enabled.
    public static boolean isDeveloperOptionsEnabled(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
    }

    private static boolean isSignatureChanged(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
            Signature[] signatures = packageInfo.signatures;
            for (Signature signature : signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                String currentSignatureHash = bytesToHex(md.digest());

                // In a real application, compare currentSignatureHash with SECURE_SIGNATURE_HASH
                // which would be retrieved from a secure source.
                // For this example, we'll assume SECURE_SIGNATURE_HASH is set correctly.
                if (!currentSignatureHash.equals(SECURE_SIGNATURE_HASH)) {
                    return true;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return true; // If package not found, something is wrong
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return true; // Should not happen with SHA
        } catch (Exception e) {
            e.printStackTrace();
            return true; // General error during signature check
        }
        return false;
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    private static boolean isDeviceRooted() {
        String[] paths = {
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su",
                "/su/bin/su"
        };
        for (String path : paths) {
            if (new File(path).exists()) return true;
        }

        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"/system/xbin/which", "su"});
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            if (in.readLine() != null) return true;
            return false;
        } catch (Throwable e) {
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
