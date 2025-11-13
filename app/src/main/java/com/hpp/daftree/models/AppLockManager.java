package com.hpp.daftree.models;

import android.content.Context;
import android.content.SharedPreferences;

public class AppLockManager {
    private static final String PREFS_NAME = "AppLockPrefs";
    private static final String KEY_LOCK_ENABLED = "lock_enabled";
    private static final String KEY_LOCK_TYPE = "lock_type"; // "password" or "biometric"

    private final SharedPreferences prefs;

    public AppLockManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isLockEnabled() {
        return prefs.getBoolean(KEY_LOCK_ENABLED, false);
    }

    public String getLockType() {
        return prefs.getString(KEY_LOCK_TYPE, null);
    }

    public void setPasswordLock(boolean enabled) {
        prefs.edit()
                .putBoolean(KEY_LOCK_ENABLED, enabled)
                .putString(KEY_LOCK_TYPE, "password")
                .apply();
    }

    public void setBiometricLock(boolean enabled) {
        prefs.edit()
                .putBoolean(KEY_LOCK_ENABLED, enabled)
                .putString(KEY_LOCK_TYPE, "biometric")
                .apply();
    }

    public void disableLock() {
        prefs.edit().clear().apply();
    }
}