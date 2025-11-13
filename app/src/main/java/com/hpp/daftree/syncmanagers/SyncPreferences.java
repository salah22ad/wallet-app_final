package com.hpp.daftree.syncmanagers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class SyncPreferences {
    private static final String PREFS_NAME = "DaftreeSyncPrefs";
    private static final String KEY_FIRST_SYNC_COMPLETE = "firstSyncComplete";
    public static final String KEY_CAN_Create_Transaction = "canCreateTransaction";
    public static final String KEY_LAST_SYNC_ACCOUNTS = "lastSyncAccounts";
    public static final String KEY_LAST_SYNC_TRANSACTIONS = "lastSyncTransactions";
    public static final String KEY_LAST_SYNC_CURRENCIES = "lastSyncCurrencies";
    public static final String KEY_LAST_SYNC_ACCOUNT_TYPES = "lastSyncAccountTypes";
    public static final String KEY_LAST_SYNC_USERS = "lastSyncAccountTypes";
    public static final String DEFAULT_CURRENCY = "defaultCurrency";

    private static final String KEY_USER_TYPE = "user_type";
    private final SharedPreferences sharedPreferences;


    public SyncPreferences(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isFirstSyncComplete() {
        return sharedPreferences.getBoolean(KEY_FIRST_SYNC_COMPLETE, false);
    }

    public void setFirstSyncComplete(boolean isComplete) {
        sharedPreferences.edit().putBoolean(KEY_FIRST_SYNC_COMPLETE, isComplete).apply();
    }
    public String getUserType() {
        if (sharedPreferences == null) {
            Log.e("SyncPreferences", "SharedPreferences is null");
            return "user"; // قيمة افتراضية
        }
        return sharedPreferences.getString(KEY_USER_TYPE, "user");
    }
    public void setKeyUserType(String userType) {
        if (sharedPreferences == null) {
            Log.e("SyncPreferences", "SharedPreferences is null");
            return;
        }
        sharedPreferences.edit().putString(KEY_USER_TYPE, userType).apply();
    }
    public boolean canCreateTransaction() {
        return sharedPreferences.getBoolean(KEY_CAN_Create_Transaction, true);
    }

    public void setCanCreateTransaction(boolean canCreate) {
        sharedPreferences.edit().putBoolean(KEY_CAN_Create_Transaction, canCreate).apply();
    }
    public void setLocalCurrency(String localCurrency) {
        sharedPreferences.edit().putString(DEFAULT_CURRENCY, localCurrency).apply();
    }
    public String getLocalCurrency(String localCurrency) {
        return sharedPreferences.getString(localCurrency, "محلي");
    }
    // **دوال جديدة لجلب وحفظ الطابع الزمني**
    public long getLastSyncTimestamp(String collectionKey) {
        return sharedPreferences.getLong(collectionKey, 0L);
    }

    public void setLastSyncTimestamp(String collectionKey, long timestamp) {
        sharedPreferences.edit().putLong(collectionKey, timestamp).apply();
    }


}