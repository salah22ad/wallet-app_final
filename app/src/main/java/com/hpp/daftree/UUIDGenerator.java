package com.hpp.daftree;
import android.util.Log;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
/**
 * UUIDGenerator - كلاس لتوليد معرفات فريدة مرتبة تسلسلياً
 */
public class UUIDGenerator {
    private static final AtomicLastTimestamp lastTimestamp = new AtomicLastTimestamp();
    private static long lastTimestamp2 = -1L;
    private static final SecureRandom random = new SecureRandom();
    /**
     * يولد FirestoreId تصاعدي يعتمد على الوقت + جزء عشوائي للتفرد
     * الصيغة: timePart(16) + "-" + randomPart(12)
     */
    public static synchronized String generateSequentialUUID() {
        long now = System.currentTimeMillis();
        // ضمان عدم تكرار نفس التوقيت (لو استدعيت الدالة في نفس الميلي ثانية)
        if (now <= lastTimestamp2) {
            now = lastTimestamp2 + 1;
        }
        lastTimestamp2 = now;
        // الجزء الزمني (طول ثابت 16 خانة Hex)
        String timePart = String.format("%016x", now);
        // الجزء العشوائي (12 خانة Hex)
        String randomPart = String.format("%012x", random.nextLong() & 0xFFFFFFFFFFFFL);

        return timePart + "-" + randomPart;
    }
    /**
     * يحصل على معرف فريد مرتب تسلسلياً
     */
    public static String generateSequentialUUID1() {
        long currentTime = System.currentTimeMillis();
        long lastTime = lastTimestamp.get();

        // إذا كان الوقت الحالي أقل أو يساوي الأخير، نزيد بمقدار 1 مللي ثانية
        if (currentTime <= lastTime) {
            currentTime = lastTime + 1;
        }

        // تحديث آخر وقت
        lastTimestamp.set(currentTime);
Log.e("generateSequentialUUID ", "UUID: " + timeBasedUUID(currentTime) + " Time: " + currentTime);
        // إنشاء UUID بناءً على الوقت مع جزء عشوائي
        return timeBasedUUID(currentTime);
    }
    public static void initialize(long timestamp) {
        lastTimestamp.set(timestamp);
    }
    /**
     * إنشاء UUID يعتمد على الوقت مع جزء عشوائي
     */
    private static String timeBasedUUID(long timestamp) {
        // تحويل الوقت إلى جزء من UUID
        String timePart = Long.toHexString(timestamp);

        // إضافة جزء عشوائي لضمان التفرد
        String randomPart = UUID.randomUUID().toString().substring(0, 12);

        // دمج الجزئين مع وضع الوقت أولاً للترتيب
        return timePart + "-" + randomPart;
    }

    /**
     * يحصل على المعرف الأخير المستخدم (للاستخدام عند إعادة التشغيل)
     */
    public static String getLastGeneratedUUID() {
        return timeBasedUUID(lastTimestamp.get());
    }

    /**
     * فئة مساعدة لإدارة آخر وقت مستخدم
     */
    private static class AtomicLastTimestamp {
        private final AtomicLong value = new AtomicLong(0);

        public long get() {
            return value.get();
        }

        public void set(long newValue) {
            value.set(newValue);
        }
    }
}