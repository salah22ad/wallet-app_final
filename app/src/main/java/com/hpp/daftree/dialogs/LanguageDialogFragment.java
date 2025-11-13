package com.hpp.daftree.dialogs;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.hpp.daftree.R;
import com.hpp.daftree.helpers.PreferenceHelper;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public class LanguageDialogFragment extends DialogFragment {
    private LanguageViewModel viewModel;
    public interface LanguageSelectionListener {
        void onLanguageConfirmed();
    }

    private LanguageSelectionListener listener;
    private Button btnConfirm;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof LanguageSelectionListener) {
            listener = (LanguageSelectionListener) context;
        } else {
           try{
            throw new RuntimeException(context.toString()
                + " must implement LanguageSelectionListener");
           } catch (RuntimeException e) {
               Log.e("Language dialogr", String.valueOf(e));
           }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_language, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(LanguageViewModel.class);
        RadioGroup radioGroup = view.findViewById(R.id.language_radio_group);
        btnConfirm = view.findViewById(R.id.btn_continue);
        getDialog().setCancelable(false); // إجباري
        getDialog().setCanceledOnTouchOutside(false);
        AtomicReference<String> lang = new AtomicReference<>(PreferenceHelper.getLanguage(requireContext()));
        Locale locale = new Locale(lang.get());
        Locale.setDefault(locale);

        Configuration config = new Configuration(requireContext().getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);

        requireContext().getResources()
                .updateConfiguration(config, requireContext().getResources().getDisplayMetrics());
        btnConfirm.setOnClickListener(v -> {
            int checkedId = radioGroup.getCheckedRadioButtonId();
             lang.set("ar");

            if (checkedId == R.id.radio_arabic) lang.set("ar");
            else if (checkedId == R.id.radio_english) lang.set("en");
            else if (checkedId == R.id.radio_spanish) lang.set("es");
            else if (checkedId == R.id.radio_france) lang.set("fr");
            viewModel.setLanguage(lang.get());

            PreferenceHelper.setLanguage(requireContext(), lang.get());
            PreferenceHelper.setFirstRun(requireContext(), false);
            if (listener != null) {
                listener.onLanguageConfirmed();
            }

            dismiss();
        });
    }
}

