package com.hpp.daftree.syncmanagers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.utils.LicenseManager;
import java.util.List;

public class TransactionUploadController {
    private static final String TAG = "TransactionUploadController";
    private static final String PREFS = "PendingTxPrefs";
    private static final String KEY_FIRST_BLOCKED = "first_blocked_id";

    private final Context context;
    private final LicenseManager licenseManager;

    public TransactionUploadController(Context ctx) {
        this.context = ctx;
        this.licenseManager = LicenseManager.getInstance(ctx);
    }

    /** يفحص إمكانية رفع العمليات، ويعيد True إن كان مسموحاً */
    public boolean canSend(Transaction tx) {
        return licenseManager.canCreateTransaction();
    }

    /** يحفظ أول عملية تم تعليقها حتى لا يُظهر التحذير بشكل متكرر */
    public void storeFirstBlocked(Transaction tx) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!sp.contains(KEY_FIRST_BLOCKED)) {
            sp.edit().putInt(KEY_FIRST_BLOCKED, tx.getId()).apply();
        }
    }

    /** يعيد ضبط العملية المحجوزة عند منتصف الليل بعد التجديد */
    public void resetBlocked() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_FIRST_BLOCKED).apply();
    }

    public boolean hasBlocked() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .contains(KEY_FIRST_BLOCKED);
    }
}
