package com.hpp.daftree.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SyncPreferences {
    private static final String PREFS_NAME = "DaftreeSyncPrefs";
    private static final String KEY_FIRST_SYNC_COMPLETE = "firstSyncComplete";

    private final SharedPreferences sharedPreferences;
    private static  SharedPreferences instance;
    private SharedPreferences.Editor editor;
    public SyncPreferences(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isFirstSyncComplete() {
        return sharedPreferences.getBoolean(KEY_FIRST_SYNC_COMPLETE, false);
    }

    public void setFirstSyncComplete(boolean isComplete) {
        sharedPreferences.edit().putBoolean(KEY_FIRST_SYNC_COMPLETE, isComplete).apply();
    }
    public void clearAll() {
        editor.clear();
        editor.apply();
    }
}