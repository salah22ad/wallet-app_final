package com.hpp.daftree.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * ReferralMilestoneHelper
 *
 * دوال مساعدة لحساب ومنع تكرار منح مكافآت الـ "milestone" (كل MILESTONE_COUNT دعوات يمنح MILESTONE_REWARD نقاط).
 *
 * التصميم:
 * - checkIfReachTenth(context, userUid, successfulReferrals)
 *      -> لا تتصل بفايرستور. ترجع عدد النقاط التي يجب منحها الآن (0 إن لم يوجد).
 * - confirmMilestoneAwarded(context, userUid, successfulReferrals)
 *      -> تستدعى بعد نجاح تحديث الخادم (Firestore) لتسجيل آخر milestone محلياً.
 * - applyLocalReward(context, pointsToAdd)
 *      -> تحديث النسخة المحلية عبر SecureLicenseManager (أو التجاهل بهدوء إن لم توجد).
 */
public final class ReferralMilestoneHelper {

    private static final String TAG = "ReferralMilestoneHelper";

    // ثوابت المنطق
    public static final int MILESTONE_COUNT = 3;   // عدد الدعوات لكل مكافأة
    public static final int MILESTONE_REWARD = 10;  // نقاط لكل milestone

    // SharedPreferences لتخزين آخر milestone مُعالج لكل مستخدم
    private static final String PREFS_NAME = "referral_milestones_prefs";
    private static final String KEY_LAST_MILESTONE_PREFIX = "last_milestone_"; // KEY_LAST_MILESTONE_PREFIX + userUid => long

    private ReferralMilestoneHelper() { /* utility class - no instances */ }

    /**
     * يفحص ما إذا وصلت قيمة successfulReferrals إلى مضاعف MILESTONE_COUNT جديد لم يُعالج بعد.
     * لا يقوم بأي استعلام لشبكة أو فايرستور — القرار محلي بالكامل.
     *
     * @param context            Context التطبيق (مطلوب للوصول إلى SharedPreferences)
     * @param userUid            معرف الداعي (مطلوب لكي نُميز التخزين المحلي لكل مستخدم)
     * @param successfulReferrals القيمة الحالية (long) لعدد الدعوات الناجحة (يفضل القيمة المحدثة)
     * @return عدد النقاط (int) التي ينبغي منحها الآن. (0 إن لم يكن هناك مكافأة جديدة)
     */
    public static int checkIfReachTenth(Context context, String userUid, long successfulReferrals) {
        if (context == null) {
            Log.w(TAG, "checkIfReachTenth: context is null");
            return 0;
        }
        if (userUid == null || userUid.isEmpty()) {
            Log.w(TAG, "checkIfReachTenth: userUid فارغ");
            return 0;
        }
        if (successfulReferrals <= 0) {
            return 0;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastMilestone = prefs.getLong(KEY_LAST_MILESTONE_PREFIX + userUid, 0L);

        long milestoneNumber = successfulReferrals / MILESTONE_COUNT; // كم مرة اجتمعت مجموعات العشرة

        // لا جديد إن كان نفس أو أقل من آخر milestone محفوظ
        if (milestoneNumber <= lastMilestone) {
            return 0;
        }

        long delta = milestoneNumber - lastMilestone; // عدد المرات الجديدة التي نحتاج منح مكافآت لها
        long pointsToAwardLong = delta * (long) MILESTONE_REWARD;

        if (pointsToAwardLong <= 0) return 0;

        if (pointsToAwardLong > Integer.MAX_VALUE) {
            Log.w(TAG, "checkIfReachTenth: pointsToAward exceeds Integer.MAX_VALUE - clipping");
        }

        return (int) Math.min(pointsToAwardLong, Integer.MAX_VALUE);
    }

    /**
     * استدعِ هذه الدالة بعد نجاح تحديث النقاط على الخادم (Firestore) لتخزين آخر milestone محليًا
     * حتى لا نمنح نفس المكافأة مرتين.
     *
     * @param context            Context التطبيق
     * @param userUid            معرف المستخدم (الداعي)
     * @param successfulReferrals القيمة الحالية (long) لعدد الدعوات الناجحة (يفضل القيمة المحدثة بعد الزيادة)
     */
    public static void confirmMilestoneAwarded(Context context, String userUid, long successfulReferrals) {
        if (context == null) {
            Log.w(TAG, "confirmMilestoneAwarded: context is null");
            return;
        }
        if (userUid == null || userUid.isEmpty()) {
            Log.w(TAG, "confirmMilestoneAwarded: userUid فارغ");
            return;
        }

        long milestoneNumber = successfulReferrals / MILESTONE_COUNT;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_MILESTONE_PREFIX + userUid, milestoneNumber).apply();

        Log.d(TAG, "confirmMilestoneAwarded: user=" + userUid + " lastMilestone=" + milestoneNumber);
    }

    /**
     * تطبيق مكافأة على التخزين المحلي (SecureLicenseManager) لكي تنعكس فورًا على الواجهة.
     * لا يقوم بأي كتابة إلى فايرستور.
     *
     * @param context     Context التطبيق
     * @param pointsToAdd عدد النقاط التي نضيفها محلياً
     */
    public static void applyLocalReward(Context context, int pointsToAdd) {
        if (context == null) {
            Log.w(TAG, "applyLocalReward: context is null");
            return;
        }
        if (pointsToAdd <= 0) return;

        try {
            SecureLicenseManager secure = SecureLicenseManager.getInstance(context);
            int currentLocal = secure.getReferralRewards();
            // استخدم saveLicenseDataNew المتوفر في SecureLicenseManager
//            secure.saveLicenseDataNew(
//                    secure.getTransactionsCount(),
//                    secure.getAdRewards(),
//                    currentLocal + pointsToAdd
//            );
            Log.d(TAG, "applyLocalReward: added " + pointsToAdd + " local referral points");
        } catch (Exception e) {
            // في حال فشل SecureLicenseManager، نحفظ في SharedPreferences كاحتياط
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                int prev = prefs.getInt("fallback_local_referral_rewards", 0);
                prefs.edit().putInt("fallback_local_referral_rewards", prev + pointsToAdd).apply();
                Log.w(TAG, "applyLocalReward: SecureLicenseManager failed, used fallback prefs", e);
            } catch (Exception ex) {
                Log.e(TAG, "applyLocalReward: failed to persist local reward", ex);
            }
        }
    }

    /**
     * (اختياري) استعلام محلي لمعاينة آخر milestone محفوظ.
     *
     * @param context Context التطبيق
     * @param userUid معرف المستخدم
     * @return قيمة lastMilestone (long) المخزنة محليًا
     */
    public static long getLastMilestone(Context context, String userUid) {
        if (context == null || userUid == null) return 0L;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_LAST_MILESTONE_PREFIX + userUid, 0L);
    }

    /**
     * (اختياري) إعادة تعيين حالة الـ milestone محلياً (لأغراض اختبارية).
     */
    public static void resetLocalMilestone(Context context, String userUid) {
        if (context == null || userUid == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_LAST_MILESTONE_PREFIX + userUid).apply();
        Log.d(TAG, "resetLocalMilestone: reset for user " + userUid);
    }
}
