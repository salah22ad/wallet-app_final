package com.hpp.daftree.helpers;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public class LanguageObserver {
    private static LanguageObserver instance;
    private final List<LanguageChangeListener> listeners = new ArrayList<>();
    private String currentLanguage;

    private LanguageObserver() {}

    public static LanguageObserver getInstance() {
        if (instance == null) {
            instance = new LanguageObserver();
        }
        return instance;
    }

    public void setCurrentLanguage(Context context) {
        this.currentLanguage = LanguageHelper.getSavedLanguage(context);
    }

    public void addListener(LanguageChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(LanguageChangeListener listener) {
        listeners.remove(listener);
    }

    public void notifyLanguageChanged(Context context, String newLanguage) {
        this.currentLanguage = newLanguage;
        for (LanguageChangeListener listener : listeners) {
            listener.onLanguageChanged(context, newLanguage);
        }
    }

    public interface LanguageChangeListener {
        void onLanguageChanged(Context context, String newLanguage);
    }
}
