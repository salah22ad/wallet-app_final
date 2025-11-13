package com.hpp.daftree.utils;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class SecureAssetLoader {

    private static final String KEY = "MyS3cureKey12345"; // نفس المفتاح المستخدم في Gradle

    public static String loadDecryptedHtml(Context context, String fileName) {
        try (InputStream input = context.getAssets().open(fileName);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

            byte[] data = new byte[1024];
            int n;
            while ((n = input.read(data)) != -1) buffer.write(data, 0, n);

            // فك Base64
            byte[] decoded = Base64.getDecoder().decode(buffer.toByteArray());

            // فك تشفير AES
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);

            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, "UTF-8");

        } catch (Exception e) {
            e.printStackTrace();
            return "<html><body><h3>Error decrypting file: " + e.getMessage() + "</h3></body></html>";
        }
    }
}
