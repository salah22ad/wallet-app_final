package com.hpp.daftree.dialogs;

import static com.hpp.daftree.helpers.PreferenceHelper.applyLocale;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.hpp.daftree.MainActivity;
import com.hpp.daftree.R;
import com.hpp.daftree.helpers.LanguageHelper;
import com.hpp.daftree.helpers.PreferenceHelper;
import com.hpp.daftree.ui.BaseActivity;

public class LanguageDialog extends Dialog {

    private Context context;
    private SharedPreferences prefs;
    private OnLanguageSelectedListener listener;
    private Spinner languageSpinner;
    private String selectedLanguage = "ar";
    private  TextView tvAgreement;
    private String[][] languages;
    public interface OnLanguageSelectedListener {
        void onLanguageSelected(String languageCode);
    }

    public LanguageDialog(@NonNull Context context, OnLanguageSelectedListener listener) {
        super(context);
        this.context = context;
        this.listener = listener;
        prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_language);
        setCancelable(false);

        languageSpinner = findViewById(R.id.language_spinner);
        Button okButton = findViewById(R.id.btn_continue);
        TextView warningText = findViewById(R.id.warning_text);
         tvAgreement = findViewById(R.id.tvAgreement);
        // إعداد نص التحذير
        if(prefs.getBoolean("first_run", true)){
            warningText.setVisibility(View.VISIBLE);
            tvAgreement.setVisibility(View.VISIBLE);
        } else {
            warningText.setVisibility(View.GONE);
            tvAgreement.setVisibility(View.GONE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            warningText.setText(Html.fromHtml(context.getString(R.string.language_selection_warning), Html.FROM_HTML_MODE_COMPACT));
        } else {
            warningText.setText(Html.fromHtml(context.getString(R.string.language_selection_warning)));
        }

        // إعداد Spinner
        setupLanguageSpinner();
        LanguageViewModel languageViewModel = new LanguageViewModel();

//        okButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                // حفظ تفضيلات اللغة
//                SharedPreferences.Editor editor = prefs.edit();
//                editor.putString("language", selectedLanguage);
//                editor.putBoolean("first_run", false);
//                editor.apply();
//                applyLocale(context, selectedLanguage);
//                // تحديث لغة التطبيق
//                LanguageHelper.setLocale(context, selectedLanguage);
//                PreferenceHelper.setLanguage(context, selectedLanguage);
//                Log.e("LanguageDialog", "Selected Language: " + selectedLanguage);
//                if (listener != null) {
//                    listener.onLanguageSelected(selectedLanguage);
//                }
//
//                dismiss();
//            }
//        });

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // ✅ احصل على اللغة المختارة مباشرة من الـ Spinner
                int selectedPosition = languageSpinner.getSelectedItemPosition();
                if (selectedPosition >= 0 && selectedPosition < languages.length) {
                    selectedLanguage = languages[selectedPosition][1];
                }

                // حفظ تفضيلات اللغة
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("language", selectedLanguage);
                editor.putBoolean("first_run", false);
                editor.apply();

                BaseActivity.applyLanguage(context, selectedLanguage);
                // ✅ استخدم ViewModel لإرسال التحديث
                languageViewModel.setLanguage(selectedLanguage);

                applyLocale(context, selectedLanguage);
                LanguageHelper.setLocale(context, selectedLanguage);
                PreferenceHelper.setLanguage(context, selectedLanguage);

                Log.e("LanguageDialog", "Final Selected Language: " + selectedLanguage);

                // ✅ أرسل التحديث عبر Listener أيضًا للتوافق مع الكود القديم
                if (listener != null) {
                    listener.onLanguageSelected(selectedLanguage);
                }

                dismiss();
            }
        });
        tvAgreement.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setupDisclaimer();
            }
        });
    }

    private void setupLanguageSpinner1() {
        // مصفوفة اللغات (الاسم المعروض ورمز اللغة)
        String[][] languages = {
                {context.getString(R.string.arabic), "ar"},
                {context.getString(R.string.english), "en"},
                {context.getString(R.string.frances), "fr"},
                {context.getString(R.string.deutsh), "de"},
                {context.getString(R.string.spanish), "es"},
                {context.getString(R.string.turck), "tr"},
                {context.getString(R.string.russian), "ru"}
        };

        // إنشاء مصفوفة للأسماء المعروضة فقط
        String[] languageNames = new String[languages.length];
        for (int i = 0; i < languages.length; i++) {
            languageNames[i] = languages[i][0];
        }

        // إنشاء Adapter مخصص
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                context, R.layout.spinner_item, languageNames) {

            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                // تخصيص العرض المختار
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                // تخصيص العناصر في القائمة المنسدلة
                return view;
            }
        };

        adapter.setDropDownViewResource(R.layout.spinner_item);
        languageSpinner.setAdapter(adapter);

        // تعيين الاختيار الحالي
        String currentLang = prefs.getString("language", "ar");
        for (int i = 0; i < languages.length; i++) {
            if (languages[i][1].equals(currentLang)) {
                languageSpinner.setSelection(i);
                selectedLanguage = currentLang;
                break;
            }
        }

        // إضافة Listener لاختيار اللغة
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedLanguage = languages[position][1];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // لا شيء
            }
        });
    }
    private void setupLanguageSpinner() {
        // مصفوفة اللغات (الاسم المعروض ورمز اللغة)
        languages = new String[][]{
                {context.getString(R.string.arabic), "ar"},
                {context.getString(R.string.english), "en"},
                {context.getString(R.string.frances), "fr"},
                {context.getString(R.string.deutsh), "de"},
                {context.getString(R.string.spanish), "es"},
                {context.getString(R.string.turck), "tr"},
                {context.getString(R.string.russian), "ru"}
        };

        // إنشاء مصفوفة للأسماء المعروضة فقط
        String[] languageNames = new String[languages.length];
        for (int i = 0; i < languages.length; i++) {
            languageNames[i] = languages[i][0];
        }

        // إنشاء Adapter مخصص
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                context, R.layout.spinner_item, languageNames) {

            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                // تخصيص العرض المختار
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                // تخصيص العناصر في القائمة المنسدلة
                return view;
            }
        };

        adapter.setDropDownViewResource(R.layout.spinner_item);
        languageSpinner.setAdapter(adapter);

        // تعيين الاختيار الحالي
        String currentLang = prefs.getString("language", "ar");
        for (int i = 0; i < languages.length; i++) {
            if (languages[i][1].equals(currentLang)) {
                languageSpinner.setSelection(i);
                selectedLanguage = currentLang;
                break;
            }
        }

        // إضافة Listener لاختيار اللغة
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedLanguage = languages[position][1];
                Log.d("LanguageDialog", "Language selected: " + selectedLanguage);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // لا شيء
            }
        });
    }
    private void setupDisclaimer() {
        String text = getContext().getString(R.string.agreement_text);
        SpannableString spannable = new SpannableString(text);

        ClickableSpan termsSpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://hpp-daftree.web.app/terms.html"));
                getContext().startActivity(intent);
            }
        };

        ClickableSpan privacySpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://hpp-daftree.web.app/privacy.html"));
                getContext().startActivity(intent);
            }
        };

        int termsStart = text.indexOf(getContext().getString(R.string.terms_cond));
        int termsEnd = termsStart + (getContext().getString(R.string.terms_cond)).length();
        spannable.setSpan(termsSpan, termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int privacyStart = text.indexOf(getContext().getString(R.string.privacy_cond));
        int privacyEnd = privacyStart + (getContext().getString(R.string.privacy_cond)).length();
        spannable.setSpan(privacySpan, privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        tvAgreement.setText(spannable);
        tvAgreement.setMovementMethod(LinkMovementMethod.getInstance());
    }


}