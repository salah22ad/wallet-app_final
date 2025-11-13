
package com.hpp.daftree.utils;


import android.util.Log;

public class RewardManager {
    private static String TAG="RewardManager";
    private static final int MILESTONE_COUNT = 5;// عدد الدعوات
    public static final int CONST_NUMBER = 5;// قيمة المكافئة
    private static int REWARD_COUNTER ;

    public static int checkForMilestoneRewards(int currentReferrals) {
        if (currentReferrals > 0 && currentReferrals % MILESTONE_COUNT == 0) {
            return CONST_NUMBER;
        }
        return 0;
    }

    // دالة للحصول على قيمة العداد الحالية
    public static int getCurrentCounter() {
        return REWARD_COUNTER;
    }

    // دالة لإعادة تعيين العداد
    public void resetCounter() {
        REWARD_COUNTER = 0;
    }

    // دوال للحصول على الثوابت إذا لزم الأمر
    public static int getMilestoneCount() {
        return MILESTONE_COUNT;
    }

    public static int getConstNumber() {
        return CONST_NUMBER;
    }
}


