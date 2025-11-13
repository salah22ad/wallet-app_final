package com.hpp.daftree.dialogs;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class LanguageViewModel extends androidx.lifecycle.ViewModel {
    private final MutableLiveData<String> selectedLanguage = new MutableLiveData<>();
    private String currentLanguage = "";

    public LiveData<String> getSelectedLanguage() {
        return selectedLanguage;
    }

    public void setLanguage(String langCode) {
        // ✅ تأكد من إطلاق الحدث حتى لو كانت القيمة نفسها
        if (!langCode.equals(currentLanguage)) {
            currentLanguage = langCode;
            selectedLanguage.setValue(langCode);
        } else {
            // ✅ إذا كانت اللغة نفسها، أعد إطلاق الحدث بالقيمة الحالية
            selectedLanguage.setValue(langCode);
        }
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }
}