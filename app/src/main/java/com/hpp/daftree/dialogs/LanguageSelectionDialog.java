// LanguageSelectionDialog.java (كلاس جديد)
package com.hpp.daftree.dialogs;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.hpp.daftree.LoginActivity;
import com.hpp.daftree.databinding.DialogLanguageSelectionBinding;
import com.hpp.daftree.helpers.LocaleHelper;
import android.content.SharedPreferences;
import android.content.Context;

public class LanguageSelectionDialog extends DialogFragment {

    private DialogLanguageSelectionBinding binding;
    private boolean isFirstLaunch = false;

    public static LanguageSelectionDialog newInstance(boolean isFirstLaunch) {
        LanguageSelectionDialog dialog = new LanguageSelectionDialog();
        Bundle args = new Bundle();
        args.putBoolean("isFirstLaunch", isFirstLaunch);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogLanguageSelectionBinding.inflate(inflater, container, false);
        if (getArguments() != null) {
            isFirstLaunch = getArguments().getBoolean("isFirstLaunch", false);
        }
        setCancelable(false); // إجباري
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // تحديد اللغة الحالية
        String currentLang = LocaleHelper.getPersistedLocale(getContext());
        if ("en".equals(currentLang)) {
            binding.radioEnglish.setChecked(true);
        } else if ("es".equals(currentLang)) {
            binding.radioSpanish.setChecked(true);
        } else {
            binding.radioArabic.setChecked(true);
        }

        binding.btnContinue.setOnClickListener(v -> {
            String selectedLang = "ar";
            if (binding.radioEnglish.isChecked()) selectedLang = "en";
            if (binding.radioSpanish.isChecked()) selectedLang = "es";

            // حفظ اللغة الجديدة
            LocaleHelper.setLocale(getContext(), selectedLang);

            // إذا كانت أول مرة، احفظ العلامة وانتقل
            if (isFirstLaunch) {
                SharedPreferences prefs = requireActivity().getSharedPreferences("DaftreeAppPrefs", Context.MODE_PRIVATE);
                prefs.edit().putBoolean("isLanguageSelected", true).apply();
            }

            // إعادة تشغيل التطبيق لتطبيق اللغة في كل مكان
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }
}