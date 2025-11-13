package com.hpp.daftree.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class GuestLicenseManager {


    private static final String TAG = "GuestLicenseManager";
    private static final String PREFS_NAME = "guest_license_prefs";
//---------------------------- الزوار ----------------------

    private static final String GUEST_USER = "guest_user";

    public static final int FREE_GUEST_TRANSACTION_DAILY = 10;

    private final Context context;
    private static GuestLicenseManager instance;
    private SharedPreferences guestPrefs;

    private GuestLicenseManager(Context context) {
        this.context = context;
    }

    public static synchronized GuestLicenseManager getInstance(Context context) {
        if (instance == null) {
            instance = new GuestLicenseManager(context);
        }
        return instance;
    }


    public boolean isGuest() {
        return guestPrefs.getBoolean(GUEST_USER, false);
    }

    public void setGueste(boolean isComplete) {
        guestPrefs.edit().putBoolean(GUEST_USER, isComplete).apply();
    }

}
