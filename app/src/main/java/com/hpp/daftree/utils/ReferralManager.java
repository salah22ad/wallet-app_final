package com.hpp.daftree.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.hpp.daftree.NotificationService;
import com.hpp.daftree.R;

import java.util.HashMap;
import java.util.Map;

public class ReferralManager {

    private static final String TAG = "ReferralManager";
    private static final String PREFS_NAME = "ReferralPrefs";
    private static final String KEY_REFERRER_UID = "referrer_uid";
    private static final String INVITE_URL = "https://hpp-daftree.web.app/invite.html";
    private final Context context;
    private final SharedPreferences prefs;

    public ReferralManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void generateAndShareReferralLink(FirebaseUser currentUser) {
        if (currentUser == null) {
            Toast.makeText(context, context.getString(R.string.login_1), Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = currentUser.getUid();
       String name = currentUser.getDisplayName();
        String referralLink = INVITE_URL + "?ref=" + uid;
        String appInfoLink = "https://hpp-daftree.web.app/invite.html";
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.invite_friend_msg,name, context.getString(R.string.app_name),referralLink,appInfoLink));
        context.startActivity(Intent.createChooser(intent, "مشاركة عبر"));
    }

    public void saveReferrerUid(String uid) {
        prefs.edit().putString(KEY_REFERRER_UID, uid).apply();
    }
    public void validateAndApplyReward(String newUserUid, String referrerUid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. التحقق من وجود المستخدم الداعي
        db.collection("users").document(referrerUid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // 2. التحقق من أن كود الدعوة لم ينته صلاحيته (إذا أردت إضافة expiry)
                        // 3. تطبيق المكافأة
                        applyReferralRewardIfAvailable(newUserUid);

                        // 4. تسجيل الحدث في Analytics
                        AnalyticsManager.getInstance(context).logNewUserViaInvite(newUserUid, referrerUid);
                    } else {
                        // المستخدم الداعي غير موجود
                        AnalyticsManager.getInstance(context).logRewardFailed(newUserUid, "referrer_not_found");
                    }
                })
                .addOnFailureListener(e -> {
                    AnalyticsManager.getInstance(context).logRewardFailed(newUserUid, "validation_error");
                });
    }
  public void applyReferralRewardIfAvailable(String newUserUid) {
        String referrerUid = prefs.getString(KEY_REFERRER_UID, null);

        if (referrerUid == null || referrerUid.isEmpty()) return;
        Log.d(TAG, "تطبيق المكافأة (إنشاء إشارة): المدعو=" + newUserUid + ", الداعي=" + referrerUid);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference newUserRef = db.collection("users").document(newUserUid);

// 1. منح المدعو نقاطه الخاصة
        newUserRef.set(new HashMap<String,Object>() {{
            put("referral_rewards", LicenseManager.REFERRAL_REWARD_REFEREE);
            put("referredBy", referrerUid);
//            put("referrerRewardProcessed", false);
//            put("referrerRewardedAt", null);
        }}, SetOptions.merge());

// 2. إنشاء إشعار للداعي في referral_notifications
        Map<String,Object> notif = new HashMap<>();
        notif.put("senderUid", newUserUid);
        notif.put("targetUid", referrerUid);
        notif.put("type", "referral");
        notif.put("points", LicenseManager.REFERRAL_REWARD_REFERRER);
        notif.put("createdAt", System.currentTimeMillis());
        notif.put("processed", false);

        db.collection("referral_notifications")
                .add(notif)
                .addOnSuccessListener(docRef -> Log.d(TAG, "Referral notification created"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to create referral notification", e));


        // مسح كود الدعوة محلياً (إذا أردت)
        prefs.edit().remove(KEY_REFERRER_UID).apply();
    }

}