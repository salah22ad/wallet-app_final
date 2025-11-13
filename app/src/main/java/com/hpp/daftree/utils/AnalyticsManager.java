package com.hpp.daftree.utils;

import android.content.Context;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;

public class AnalyticsManager {

    private static AnalyticsManager instance;
    private final FirebaseAnalytics firebaseAnalytics;

    private AnalyticsManager(Context context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context);
    }

    public static synchronized AnalyticsManager getInstance(Context context) {
        if (instance == null) {
            instance = new AnalyticsManager(context.getApplicationContext());
        }
        return instance;
    }

    // حدث عند النقر على رابط الدعوة
    public void logInviteLinkClicked(String referrerUid) {
        Bundle bundle = new Bundle();
        bundle.putString("referrer_uid", referrerUid);
        firebaseAnalytics.logEvent("invite_link_clicked", bundle);
    }

    // حدث عند فتح التطبيق عبر رابط الدعوة
    public void logAppOpenedViaInvite(String referrerUid) {
        Bundle bundle = new Bundle();
        bundle.putString("referrer_uid", referrerUid);
        firebaseAnalytics.logEvent("app_opened_via_invite", bundle);
    }

    // حدث عند تسجيل مستخدم جديد عبر الدعوة
    public void logNewUserViaInvite(String newUserId, String referrerUid) {
        Bundle bundle = new Bundle();
        bundle.putString("new_user_id", newUserId);
        bundle.putString("referrer_uid", referrerUid);
        firebaseAnalytics.logEvent("new_user_via_invite", bundle);
    }

    // حدث عند منح مكافأة الدعوة
    public void logRewardGranted(String userId, String rewardType, int amount) {
        Bundle bundle = new Bundle();
        bundle.putString("user_id", userId);
        bundle.putString("reward_type", rewardType);
        bundle.putInt("reward_amount", amount);
        firebaseAnalytics.logEvent("reward_granted", bundle);
    }

    // حدث عند فشل منح المكافأة
    public void logRewardFailed(String userId, String errorReason) {
        Bundle bundle = new Bundle();
        bundle.putString("user_id", userId);
        bundle.putString("error_reason", errorReason);
        firebaseAnalytics.logEvent("reward_failed", bundle);
    }
}