package com.hpp.daftree.dialogs;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.LocaleList;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.hpp.daftree.AccountTypeViewModel;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.ReportsViewModel;
import com.hpp.daftree.helpers.LanguageHelper;
import com.hpp.daftree.helpers.PreferenceHelper;
import com.hpp.daftree.models.FormatingAmount;
import com.hpp.daftree.syncmanagers.SyncPreferences;
import com.hpp.daftree.UUIDGenerator;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.databinding.DialogAddAccountBinding;
import com.hpp.daftree.databinding.DialogNewInvoiceBinding;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.database.User;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.ui.AddTransactionViewModel;
import com.hpp.daftree.ui.CurrencyViewModel;
import com.hpp.daftree.utils.GoogleAuthHelper;
import com.hpp.daftree.utils.LicenseManager;
import com.hpp.daftree.utils.ReferralManager;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import android.os.Build;

public class InvoiceDialog extends DialogFragment {

    private DialogNewInvoiceBinding binding;
    private DaftreeRepository repository;
    private ReportsViewModel reportsViewModel;
    private CurrencyViewModel currencyViewModel; // ViewModel جديد
    private final List<Currency> allAvailableCurrencies = new ArrayList<>(); // كل العملات المتاحة
    private AddTransactionViewModel viewModel;
    private SyncPreferences syncPreferences;
    private final Calendar selectedDate = Calendar.getInstance();
    private List<Account> accountsList = new ArrayList<>();
    private final Map<String, Account> accountNameToObjectMap = new HashMap<>();
    private final Map<String, Integer> accountNameToIdMap = new HashMap<>();
    // متغيرات لتحديد وضع التشغيل
    private static final String ARG_MODE = "mode";
    private static final String ARG_IMPORT_ID = "import_id";
    private enum Mode { CREATE, VIEW_EDIT,CREATE_ACC }
    private Mode currentMode;
    private AccountTypeViewModel accountTypeViewModel;
    private List<AccountType> availableAccountTypes = new ArrayList<>();
    private  List<Transaction> invoiceTxs = new ArrayList<>();
    private Transaction invoiceTx,paymentTx;
    private String selectedCurrency,accountName;
    private int editImportId = -1;
    private User currentUser;
    private LicenseManager licenseManager ;

    private static final String ARG_CURRENCY = "currency";
    private static final String ARG_AccountName = "currency";
    private static final String ARG_isEdited = "currency";
    private  String isEdited = "false";
    private boolean isGuest;
    private String uid;
    private Handler mainHandler;
    // --- دوال إنشاء جديدة لكل سيناريو ---
    public static InvoiceDialog newInstanceForCreate(String currency) {
        InvoiceDialog dialog = new InvoiceDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_MODE, Mode.CREATE);
        args.putString(ARG_CURRENCY, currency);
        dialog.setArguments(args);
        return dialog;
    }

    public static InvoiceDialog newInstanceForEdit(int importId,String currency,String billEdited) {
        InvoiceDialog dialog = new InvoiceDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_MODE, Mode.VIEW_EDIT);
        args.putInt(ARG_IMPORT_ID, importId);
        args.putString(ARG_CURRENCY, currency);
        args.putString(ARG_isEdited, billEdited);
        dialog.setArguments(args);
        return dialog;
    }
    public static InvoiceDialog newInstanceForAccount(String currency,String accountName) {
        InvoiceDialog dialog = new InvoiceDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_MODE, Mode.CREATE_ACC);

        args.putString(ARG_CURRENCY, currency);
        args.putString(ARG_AccountName, accountName);
        dialog.setArguments(args);
        return dialog;

    }
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogNewInvoiceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

//    @Override
    public void onViewCreated1(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = new DaftreeRepository(getActivity().getApplication());
        reportsViewModel = new ViewModelProvider(this).get(ReportsViewModel.class);
        accountTypeViewModel = new ViewModelProvider(this).get(AccountTypeViewModel.class);
        viewModel = new ViewModelProvider(this).get(AddTransactionViewModel.class);
      //  licenseSyncManager = new LicenseSyncManager(getContext());
        licenseManager = new LicenseManager(getContext());
        syncPreferences = new SyncPreferences(getContext());
        currencyViewModel = new ViewModelProvider(this).get(CurrencyViewModel.class); // تهيئة

        String lang = PreferenceHelper.getLanguage(requireContext());
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        isGuest = SecureLicenseManager.getInstance(getContext()).isGuest();
        if (isGuest) {
            uid = SecureLicenseManager.getInstance(getContext()).guestUID();
        } else {
            uid = FirebaseAuth.getInstance().getCurrentUser() != null ?
                    FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        }
        Configuration config = new Configuration(requireContext().getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);

        FormatingAmount.applyTo(binding.invoiceAmountEditText);
        FormatingAmount.applyTo(binding.paidAmountEditText);

        requireContext().getResources()
                .updateConfiguration(config, requireContext().getResources().getDisplayMetrics());

        String savedLanguage = LanguageHelper.getSavedLanguage(getContext());
        LanguageHelper.setLocale(getContext(), savedLanguage);
        MyApplication.applyGlobalTextWatcher(binding.getRoot());
        setupObservers();
        setupEventListeners();
        setupListeners();

        if (getArguments() != null) {
            selectedCurrency = getArguments().getString(ARG_CURRENCY);
            isEdited = getArguments().getString(ARG_isEdited);
            currentMode = (Mode) getArguments().getSerializable(ARG_MODE);
            if (currentMode == Mode.VIEW_EDIT) {
                editImportId = getArguments().getInt(ARG_IMPORT_ID);
                binding.titleTextView.setText(R.string.editr_invoice_tit);
                loadInvoiceData(editImportId);
            } else if (currentMode == Mode.CREATE) {
                binding.titleTextView.setText(R.string.new_invoice_tit);
                binding.accountAutoComplete.requestFocus();
                switchToCreateMode();
            } else {
                binding.titleTextView.setText(R.string.new_invoice_tit);
                accountName = getArguments().getString(ARG_AccountName);
                selectedCurrency = getArguments().getString(ARG_CURRENCY);
                binding.detailsEditText.requestFocus();
                binding.accountAutoComplete.setEnabled(false);
                switchToCreateMode();
            }
        }
        viewModel.getUserProfile().observe(this, user -> {
            if (user != null) {
                this.currentUser = user;
            }
        });

    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // تهيئة Handler مركزي مرتبط بـ Main Looper
        mainHandler = new Handler(Looper.getMainLooper());

        repository = new DaftreeRepository(requireActivity().getApplication());
        reportsViewModel = new ViewModelProvider(this).get(ReportsViewModel.class);
        accountTypeViewModel = new ViewModelProvider(this).get(AccountTypeViewModel.class);
        viewModel = new ViewModelProvider(this).get(AddTransactionViewModel.class);
        // licenseSyncManager = new LicenseSyncManager(getContext());
        licenseManager = new LicenseManager(requireContext());
        syncPreferences = new SyncPreferences(requireContext());
        currencyViewModel = new ViewModelProvider(this).get(CurrencyViewModel.class); // تهيئة

        String lang = PreferenceHelper.getLanguage(requireContext());
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);

        isGuest = SecureLicenseManager.getInstance(requireContext()).isGuest();
        if (isGuest) {
            uid = SecureLicenseManager.getInstance(requireContext()).guestUID();
        } else {
            uid = FirebaseAuth.getInstance().getCurrentUser() != null ?
                    FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        }

        // ====== تعامُل مع Configuration بشكل متوافق مع الإصدارات القديمة والحديثة ======
        Resources res = requireContext().getResources();
        Configuration config = new Configuration(res.getConfiguration());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Android 7.0+ (supports LocaleList)
            LocaleList localeList = new LocaleList(locale);
            LocaleList.setDefault(localeList);
            config.setLocales(localeList);
            config.setLayoutDirection(locale);
            // createConfigurationContext متاح منذ API 17 لكن هنا نتعامل مع N+ أولاً
            Context localized = requireContext().createConfigurationContext(config);
            // استخدم localized.getResources() عند الحاجة للـ resources المحلية
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // API 17..23
            config.setLocale(locale);
            config.setLayoutDirection(locale);
            Context localized = requireContext().createConfigurationContext(config);
        } else {
            // API < 17 (لدعم min API 7) — نستخدم updateConfiguration كحل توافقى
            //noinspection deprecation
            res.updateConfiguration(config, res.getDisplayMetrics());
        }
        // ===========================================================================

        FormatingAmount.applyTo(binding.invoiceAmountEditText);
        FormatingAmount.applyTo(binding.paidAmountEditText);

        // **ملاحظة**: بعض Helpers قد يحتاجون Context مهيأ بالـ locale أعلاه.
        String savedLanguage = LanguageHelper.getSavedLanguage(requireContext());
        LanguageHelper.setLocale(requireContext(), savedLanguage);
        MyApplication.applyGlobalTextWatcher(binding.getRoot());
        setupObservers();
        setupEventListeners();
        setupListeners();

        // ===== استخراج المتغيرات من Arguments بطريقة آمنة (Mode as String fallback to Serializable) =====
        if (getArguments() != null) {
            selectedCurrency = getArguments().getString(ARG_CURRENCY);
            isEdited = getArguments().getString(ARG_isEdited);

            // نقرأ Mode بطريقة آمنة: أولًا كـ String (الموصى بها)، وإلا نجرّب Serializable كحل مؤقت
            if (getArguments().containsKey(ARG_MODE)) {
                // إذا أُرسِل كـ String (مفضَّل)
                String modeName = getArguments().getString(ARG_MODE, null);
                if (modeName != null) {
                    try {
                        currentMode = Mode.valueOf(modeName);
                    } catch (IllegalArgumentException e) {
                        // fallback to Serializable if enum name غير معروف
                        Object ser = getArguments().get(ARG_MODE);
                        if (ser instanceof Mode) {
                            currentMode = (Mode) ser;
                        } else {
                            currentMode = Mode.CREATE; // قيمة افتراضية آمنة
                        }
                    }
                } else {
                    // fallback: قد يكون Serializable (الطريقة القديمة)
                    Object ser = getArguments().get(ARG_MODE);
                    if (ser instanceof Mode) {
                        currentMode = (Mode) ser;
                    } else {
                        currentMode = Mode.CREATE;
                    }
                }
            } else {
                currentMode = Mode.CREATE;
            }

            if (currentMode == Mode.VIEW_EDIT) {
                editImportId = getArguments().getInt(ARG_IMPORT_ID);
                binding.titleTextView.setText(R.string.editr_invoice_tit);
                loadInvoiceData(editImportId);
            } else if (currentMode == Mode.CREATE) {
                binding.titleTextView.setText(R.string.new_invoice_tit);
                binding.accountAutoComplete.requestFocus();
                switchToCreateMode();
            } else {
                binding.titleTextView.setText(R.string.new_invoice_tit);
                accountName = getArguments().getString(ARG_AccountName);
                selectedCurrency = getArguments().getString(ARG_CURRENCY);
                binding.detailsEditText.requestFocus();
                binding.accountAutoComplete.setEnabled(false);
                switchToCreateMode();
            }
        }

        viewModel.getUserProfile().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                this.currentUser = user;
            }
        });
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    private void setupEventListeners() {
        binding.accountAutoComplete.setOnClickListener(v -> showKeyboard(binding.accountAutoComplete));

        binding.invoiceDateEditText.setOnClickListener(v -> showDatePicker());
//        binding.currencyInputLayout.setOnClickListener(this::hideKeyboard);
        binding.currencyAutoComplete.setOnTouchListener((v, event) -> {
            hideKeyboard(binding.currencyAutoComplete);
            return false;
        });

        binding.accountAutoComplete.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT |
                        android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS |
                        android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        );

        binding.accountAutoComplete.setThreshold(1); // عرض الاقتراحات من أول حرف

        // مستمع ذكي للتحكم في الإدخال
        binding.accountAutoComplete.addTextChangedListener(new TextWatcher() {
            private String lastValidText = "";
            private boolean isInternalChange = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isInternalChange) return;

                String currentText = s.toString().trim();

                // إذا كان النص فارغاً أو لم يتغير، لا تفعل شيئاً
                if (currentText.isEmpty() || currentText.equals(lastValidText)) {
                    return;
                }

                // التحقق من التغييرات الكبيرة المفاجئة (الإكمال التلقائي)
                if (lastValidText.length() > 0 &&
                        currentText.length() > lastValidText.length() + 10) {
                    // إرجاع إلى النص السابق
                    isInternalChange = true;
                    binding.accountAutoComplete.setText(lastValidText);
                    binding.accountAutoComplete.setSelection(lastValidText.length());
                    isInternalChange = false;
                    return;
                }

                // تحديث آخر نص صحيح
                lastValidText = currentText;

                // إذا كان النص طويلاً جداً، اقتطعه
                if (currentText.length() > 30) {
                    isInternalChange = true;
                    String shortened = currentText.substring(0, 30);
                    binding.accountAutoComplete.setText(shortened);
                    binding.accountAutoComplete.setSelection(shortened.length());
                    isInternalChange = false;
                    lastValidText = shortened;
                }
            }
        });
        // عند اختيار حساب من القائمة
        binding.accountAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = (String) parent.getItemAtPosition(position);
            Integer accountId = accountNameToIdMap.get(selectedName);
            if (accountId != null) {
                binding.detailsEditText.requestFocus();
                showKeyboard(view);
            }
        });
        // تنسيق حقل المبلغ
//        binding.amountEditText.addTextChangedListener(new ThousandSeparatorTextWatcher(binding.amountEditText));

        // الانتقال من المبلغ للتفاصيل
        binding.detailsEditText.setOnEditorActionListener((v, actionId, event) -> {
            if ((actionId == EditorInfo.IME_ACTION_NEXT) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                binding.invoiceAmountEditText.requestFocus();
                showKeyboard(binding.invoiceAmountEditText);
                return true;
            }
            return false;
        });
        binding.invoiceAmountEditText.setOnEditorActionListener((v, actionId, event) -> {
            if ((actionId == EditorInfo.IME_ACTION_NEXT) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                binding.paidAmountEditText.requestFocus();
                showKeyboard(binding.paidAmountEditText);
                return true;
            }
            return false;
        });
        // إخفاء لوحة المفاتيح بعد التفاصيل
        binding.paidAmountEditText.setOnEditorActionListener((v, actionId, event) -> {
            if ((actionId == EditorInfo.IME_ACTION_NEXT) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                hideKeyboard(binding.paidAmountEditText);
                return true;
            }
            return false;
        });

        // التعامل مع اسم جديد
           binding.accountAutoComplete.setOnEditorActionListener((v, actionId, event) -> {
            if ((actionId == EditorInfo.IME_ACTION_NEXT) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                // نستدعي الدالة ونتحقق من نتيجتها
                if (isValidate()) {
                    // إذا كانت النتيجة true (التحقق ناجح)، ننتقل لحقل المبلغ
                    binding.detailsEditText.requestFocus();
                    showKeyboard(binding.detailsEditText);
                }else{
                    String name = binding.accountAutoComplete.getText().toString().trim();
                    showAddNewAccountDialog(name, true);
                }
                return true; // لمنع أي سلوك افتراضي آخر
            }
            return false;
        });

        // ======== التعديل الرئيسي هنا ========
        // التعامل مع اسم جديد عند فقدان التركيز
        binding.accountAutoComplete.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                binding.accountAutoComplete.selectAll();
            }
        });
//        binding.detailsEditText.setOnFocusChangeListener((v, hasFocus) -> {
//            if (hasFocus) {
//                String name = binding.accountAutoComplete.getText().toString().trim();
//                Log.e("accountName", name);
//                mainHandler.postDelayed(() -> {
//                    if (!TextUtils.isEmpty(name) && !isAccountExists(name) ) {
//                        showAddNewAccountDialog(name, true);
//
//                    } else {
//                        binding.detailsEditText.selectAll();
//                    }
//                }, 100);
//            }
//        });
//        binding.invoiceAmountEditText.setOnFocusChangeListener((v, hasFocus) -> {
//            if (hasFocus) {
//                String name = binding.accountAutoComplete.getText().toString().trim();
//                Log.e("accountName", name);
//                mainHandler.postDelayed(() -> {
//                    if (!TextUtils.isEmpty(name) && !isAccountExists(name) ) {
//                        showAddNewAccountDialog(name, true);
//                    } else {
//                        binding.invoiceAmountEditText.selectAll();
//                    }
//                }, 100);
//            }
//        });
        binding.detailsEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                String name = binding.accountAutoComplete.getText().toString().trim();
                Log.e("accountName", name);

                mainHandler.postDelayed(() -> {
                    // التحقق المزدوج من وجود الحساب
                    if (!TextUtils.isEmpty(name) && !isAccountExists(name) && !isNewAccAdded) {
                        showAddNewAccountDialog(name, true);
                    } else {
                        binding.detailsEditText.selectAll();
                        isNewAccAdded = false; // إعادة تعيين العلامة
                    }
                }, 150); // زيادة التأخير قليلاً
            }
        });

        binding.invoiceAmountEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                String name = binding.accountAutoComplete.getText().toString().trim();
                Log.e("accountName", name);
                mainHandler.postDelayed(() -> {
                    // التحقق المزدوج من وجود الحساب
                    if (!TextUtils.isEmpty(name) && !isAccountExists(name) && !isNewAccAdded) {
                        showAddNewAccountDialog(name, true);
                    } else {
                        binding.invoiceAmountEditText.selectAll();
                        isNewAccAdded = false; // إعادة تعيين العلامة
                    }
                }, 150); // زيادة التأخير قليلاً
            }
        });
    }
    private boolean isAccountExists1(String accountName) {
        if (TextUtils.isEmpty(accountName)) {
            return false;
        }
        return accountNames.contains(accountName) && accountNameToIdMap.get(accountName) != null;
    }
    private boolean isAccountExists(String accountName) {
        if (TextUtils.isEmpty(accountName)) {
            return false;
        }
        // تنظيف النص من المسافات الزائدة
        String cleanName = accountName.trim();
        // البحث في القائمة مع تجاهل حالة الأحرف والمسافات
        for (String existingName : accountNames) {
            if (existingName != null && existingName.trim().equalsIgnoreCase(cleanName)) {
                return true;
            }
        }
        return false;
    }
  boolean isNewAccAdded = true;

    private boolean isValidate() {

        String name = binding.accountAutoComplete.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(getContext(), R.string.error_account_name_empty, Toast.LENGTH_SHORT).show();
            binding.accountAutoComplete.requestFocus();
            return false;
        }
//        if (name.split("\\s+").length < 3) {
//            binding.accountAutoComplete.setError(getString(R.string.error_account_name_length));
//            binding.accountAutoComplete.requestFocus();
//            return false;
//        }
        if (accountNames.contains(name)) {
            Integer accountId = accountNameToIdMap.get(name);
            return true;
        } else {
            return false;
        }

    }

    private void showAddNewAccountDialog(String name, Boolean isSave) {
        // 1. تحميل واجهة الديالوج وتعبئة Spinner (لا تغيير هنا)
        DialogAddAccountBinding dialogBinding = DialogAddAccountBinding.inflate(getLayoutInflater());
        dialogBinding.dialogAccountNameEditText.setText(name);
        MyApplication.applyGlobalTextWatcher(dialogBinding.getRoot());

        AutoCompleteTextView accountTypeSpinner = dialogBinding.spinnerAccountType;
        accountTypeViewModel.getAllAccountTypes().observe(this, types -> {
            if (types != null && !types.isEmpty()) {
                this.availableAccountTypes = types;
                List<String> typeNames = types.stream().map(t -> t.name).collect(Collectors.toList());
                ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                        android.R.layout.simple_dropdown_item_1line, typeNames);
                accountTypeSpinner.setAdapter(adapter);
                if (!typeNames.isEmpty()) {
                    accountTypeSpinner.setText(typeNames.get(0), false);
                }
            }
        });

        // --- بداية الحل الجديد والمبسط ---

        // 2. بناء الديالوج بدون مستمع لزر الحفظ مبدئيًا
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                .setTitle(R.string.add_new_account_title)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.add_account, null) // <-- مهم جدًا: نضع null هنا
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    binding.accountAutoComplete.selectAll();
                    binding.accountAutoComplete.requestFocus();
                });

        // 3. إنشاء وإظهار الديالوج
        AlertDialog dialog = builder.create();
        dialog.show();

        // 4. التحكم الكامل بزر الحفظ بعد إظهار الديالوج
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(v -> {
            // كل منطق التحقق والحفظ موجود هنا الآن
            String finalName = dialogBinding.dialogAccountNameEditText.getText().toString().trim();
            String phone = dialogBinding.dialogPhoneNumberEditText.getText().toString().trim();
            String type = accountTypeSpinner.getText().toString();

            // -- التحقق من صحة المدخلات --
//            if (TextUtils.isEmpty(finalName) || finalName.split("\\s+").length < 2) {
//                Toast.makeText(getContext(), R.string.error_account_name_length, Toast.LENGTH_SHORT).show();
//                return; // <-- لن يتم إغلاق الديالوج هنا
//            }
            if (TextUtils.isEmpty(type)) {
                dialogBinding.accountTypeSpinnerLayout.setError(getString(R.string.ar_text_5));
                return; // <-- لن يتم إغلاق الديالوج هنا
            }
            if (accountNames.contains(finalName)) {
                Toast.makeText(getContext(), R.string.error_account_exists, Toast.LENGTH_SHORT).show();
                return; // <-- لن يتم إغلاق الديالوج هنا
            }
            isNewAccAdded = true;
            // -- كل شيء سليم، قم بإنشاء وحفظ الحساب الجديد --
            Account newAccount = new Account();

            String typeId = findFirestoreIdByAccountTypeName(type);
            newAccount.setOwnerUID(uid);
            newAccount.setAccountName(finalName);
            newAccount.setPhoneNumber(phone);
            newAccount.setAccountType(type);
            assert typeId != null;
            newAccount.setAcTypeFirestoreId(typeId);
            viewModel.createAccount(newAccount); // استدعاء دالة الحفظ البسيطة
            binding.accountAutoComplete.setText(finalName);
//            if (!isSave) {
//            binding.detailsEditText.requestFocus();
//            }

            // 5. **الأهم:** إغلاق الديالوج يدويًا وفورًا بعد النجاح
            dialog.dismiss();
            showKeyboard(v);
        });
    }
    private String findFirestoreIdByAccountTypeName(String name) {
        if (availableAccountTypes == null || name.isEmpty()) return null;
        for (AccountType type : availableAccountTypes) {
            if (type.getName().equals(name)) {
                return type.getFirestoreId();
            }
        }
        return null;
    }

    private void showKeyboard(View view) {
        if (view.requestFocus()) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }
    private void hideKeyboard1(View view) {
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
    List<String> accountNames = new ArrayList<>();
    private void setupObservers() {
        updateDateInView();
//        setupAmountFormatting(binding.invoiceAmountEditText);
//        setupAmountFormatting(binding.paidAmountEditText);
        reportsViewModel.getAllAccounts().observe(getViewLifecycleOwner(), accounts -> {
            if (accounts == null) return;
            this.accountsList = accounts;
            accountNameToObjectMap.clear();
            accountNames.clear();
            for (Account acc : accounts) {
                accountNames.add(acc.getAccountName());
                accountNameToObjectMap.put(acc.getAccountName(), acc);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, accountNames);
            binding.accountAutoComplete.setAdapter(adapter);
            int itemHeight = (int) (48 * getResources().getDisplayMetrics().density);
            binding.accountAutoComplete.setDropDownHeight(itemHeight * 4);
        });
        currencyViewModel.getAllCurrencies().observe(this, currencies -> {
            if (currencies != null) {
                allAvailableCurrencies.clear();
                allAvailableCurrencies.addAll(currencies);
            }
        });
        reportsViewModel.getAllCurrencies().observe(getViewLifecycleOwner(), currencies -> {
            if (currencies == null) return;
            List<String> currencyNames = currencies.stream().map(c -> c.name).collect(Collectors.toList());
            ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, currencyNames);
            binding.currencyAutoComplete.setAdapter(adapter);
            int itemHeight = (int) (48 * getResources().getDisplayMetrics().density);
            binding.currencyAutoComplete.setDropDownHeight(itemHeight * 4);

            if (selectedCurrency != null && currencyNames.contains(selectedCurrency)) {
                binding.currencyAutoComplete.setText(selectedCurrency, false);
            } else if (!currencyNames.isEmpty()) {
                binding.currencyAutoComplete.setText(currencyNames.get(0), false);
            }
        });


    }

    private void setupListeners() {
        TextWatcher textWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateRemainingAmount(); }
        };
        binding.invoiceAmountEditText.addTextChangedListener(textWatcher);
        binding.paidAmountEditText.addTextChangedListener(textWatcher);

        binding.saveButton.setOnClickListener(v -> saveInvoice());
        binding.btnCancel.setOnClickListener(v -> dismiss());
        binding.editButton.setOnClickListener(v -> switchToEditMode());
        binding.deleteButton.setOnClickListener(v -> deleteInvoice());
    }
    private void loadInvoiceData(int importId) {

        AppDatabase.databaseWriteExecutor.execute(() -> {
             invoiceTxs = repository.getTransactionsByImportId(importId);
            if (invoiceTxs.isEmpty()) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), R.string.bill_not_found, Toast.LENGTH_SHORT).show();
                    dismiss();
                });
                return;
            }
            invoiceTx = null;
            paymentTx = null;
            if (invoiceTxs != null && invoiceTxs.size() == 1) {
                invoiceTx = invoiceTxs.get(0);
            }else {
                for (Transaction tx : invoiceTxs) {
                    String billType = tx.getBillType();
                    int type = tx.getType();
                    // إذا كان هناك معاملة واحدة فقط، فهي الفاتورة
                    if ((billType.split("\\s+").length == 1)) {
                        invoiceTx = tx;
                    } else {
                        paymentTx = tx;
                    }
                }
            }


            Account account = repository.getAccountDao().getAccountByIdBlocking(invoiceTx.getAccountId());

            getActivity().runOnUiThread(() -> {
                binding.accountAutoComplete.setText(account.getAccountName(), false);
                String details = invoiceTx.getDetails();
                Log.e("details",details);
                if (details != null) {
                    // إزالة "دفعة نقدية" وأي مسافات تليها من بداية النص
//                    details = details.replaceFirst("^دفعة نقدية من مبيعات\\s*", "");
//                    details = details.replaceFirst("^دفعة نقدية من مشتريات\\s*", "");

                    details = details.replaceFirst("^"+ R.string.ar_long_text_47 +"\\s*", "");
                    details = details.replaceFirst("^"+ R.string.ar_long_text_48 +"\\s*", "");
/*

"+ R.string.ar_text_6_2 +"
 */
                }

//                binding.detailsEditText.setText(invoiceTx.getDetails());

                binding.currencyAutoComplete.setText(selectedCurrency, false);
                selectedDate.setTime(invoiceTx.getTimestamp());
                // التمييز بين المبيعات والمشتريات بناءً على type وbillType
                boolean isSales = false;

                // الأولوية لفحص type
                if (invoiceTx.getType() == 1) {
                    isSales = true;
                } else if (invoiceTx.getType() == -1) {
                    isSales = false;
                }
                // إذا كان type غير محدد، نفحص billType
                else if (invoiceTx.getBillType() != null &&
                        invoiceTx.getBillType().contains(getString(R.string.ar_text_6_2))) {
                    isSales = true;
                } else if (invoiceTx.getBillType() != null &&
                        invoiceTx.getBillType().contains(getString(R.string.ar_text_7_2))) {
                    isSales = false;
                }

                if ((isSales)) {
                    binding.radioSales.setChecked(true);
                    binding.radioPurchases.setChecked(false);
//                    binding.invoiceAmountEditText.setText(String.valueOf(invoiceTx.getAmount()));
//                    binding.paidAmountEditText.setText(paymentTx != null ? String.valueOf(paymentTx.getAmount()) : "0");

                    binding.invoiceAmountEditText.setText(FormatingAmount.formatForDisplay(invoiceTx.getAmount()));
                    binding.paidAmountEditText.setText(paymentTx != null ? FormatingAmount.formatForDisplay(paymentTx.getAmount()) : "0");
                    binding.detailsEditText.setText(details.trim());
                } else {
                    binding.radioPurchases.setChecked(true);
                    binding.radioSales.setChecked(false);
                    binding.invoiceAmountEditText.setText(FormatingAmount.formatForDisplay(invoiceTx.getAmount()));
                    binding.paidAmountEditText.setText(paymentTx != null ? FormatingAmount.formatForDisplay(paymentTx.getAmount()) : "0");

                    // إزالة "دفعة نقدية" وأي مسافات تليها من بداية النص
                       binding.detailsEditText.setText(details.trim());
                }
                switchToViewMode();
            });
        });
    }
    private void switchToViewMode() {
        setFieldsEnabled(false);
        binding.layoutSaveActions.setVisibility(View.GONE);
        binding.layoutEditActions.setVisibility(View.VISIBLE);
    }

    private void switchToEditMode() {
        setFieldsEnabled(true);
        binding.layoutSaveActions.setVisibility(View.VISIBLE);
        binding.layoutEditActions.setVisibility(View.GONE);
    }

    private void switchToCreateMode() {
        setFieldsEnabled(true);
        binding.layoutSaveActions.setVisibility(View.VISIBLE);
        binding.layoutEditActions.setVisibility(View.GONE);
        binding.accountAutoComplete.setEnabled(true);
//        Account account = repository.getAccountDao().getAccountByName(accountName);
        binding.accountAutoComplete.setText(accountName, false);
        binding.currencyAutoComplete.setText(selectedCurrency, false);
        binding.radioSales.setChecked(true);

        if (currentMode == Mode.CREATE) {
            binding.accountAutoComplete.requestFocus();
            mainHandler.postDelayed(() -> {
            showKeyboard(binding.accountAutoComplete);
            }, 100);
        }else {
            binding.accountAutoComplete.setEnabled(false);
             binding.detailsEditText.requestFocus();
                mainHandler.postDelayed(() -> {
            showKeyboard(binding.detailsEditText);
                }, 100);
        }


    }

    private void setFieldsEnabled(boolean isEnabled) {
        binding.radioSales.setEnabled(isEnabled);
        binding.radioPurchases.setEnabled(isEnabled);
//        binding.accountAutoComplete.setEnabled(isEnabled);
        binding.invoiceDateEditText.setEnabled(isEnabled);
        binding.detailsEditText.setEnabled(isEnabled);
        binding.invoiceAmountEditText.setEnabled(isEnabled);
        binding.paidAmountEditText.setEnabled(isEnabled);
        binding.currencyAutoComplete.setEnabled(isEnabled);
    }

    private void showDatePicker() {
        DatePickerDialog.OnDateSetListener dateSetListener = (view, year, month, dayOfMonth) -> {
            selectedDate.set(Calendar.YEAR, year);
            selectedDate.set(Calendar.MONTH, month);
            selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateInView();
            String name = binding.accountAutoComplete.getText().toString().trim();
            if (!name.isEmpty()) {
                binding.detailsEditText.requestFocus();
            }
        };
        new DatePickerDialog(getContext(), dateSetListener,
                selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateInView() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        binding.invoiceDateEditText.setText(sdf.format(selectedDate.getTime()));
    }
    private void updateRemainingAmount() {
        try {

//            double invoiceValue = getAmountValue(binding.invoiceAmountEditText);
//            double paidValue = getAmountValue(binding.paidAmountEditText);

            double invoiceValue = FormatingAmount.parseAmount(binding.invoiceAmountEditText.getText().toString().trim());
            double paidValue = FormatingAmount.parseAmount(binding.paidAmountEditText.getText().toString().trim());

            double remaining = invoiceValue - paidValue;
            binding.remainingAmountTextView.setText(FormatingAmount.formatForDisplay(remaining));
        } catch (NumberFormatException e) {
            binding.remainingAmountTextView.setText(formatCurrency(0.0));
        }
    }
    private double getAmountValue(EditText editText) {
        String text = editText.getText().toString().replace(",", "");
        return TextUtils.isEmpty(text) ? 0 : Double.parseDouble(text);
    }
    private void saveInvoice() {
        String accountName = binding.accountAutoComplete.getText().toString();
        Account selectedAccount = accountNameToObjectMap.get(accountName);
        selectedCurrency = binding.currencyAutoComplete.getText().toString();
        Integer currencyId = allAvailableCurrencies.stream()
                .filter(currency -> currency.name.equals(selectedCurrency))
                .findFirst().map(currency -> currency.id)
                .orElse(null);
        if (currencyId == null) {
            Toast.makeText(getContext(),  R.string.wronge_currency, Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedAccount == null) {
            showIfAccountNull(() -> showAddNewAccountDialog(accountName, false));
            return;
        }


        boolean isSales = binding.radioSales.isChecked();
        String billType = isSales ?   getString(R.string.ar_text_6_2) : getString(R.string.ar_text_7_2);
        String subBillType = isSales ?   getString(R.string.ar_long_text_45) : getString(R.string.ar_long_text_46);
        String subDetails = isSales ?   getString(R.string.ar_long_text_47) : getString(R.string.ar_long_text_48) ;
        String details = binding.detailsEditText.getText().toString().trim();
        double invoiceValue = getAmountValue(binding.invoiceAmountEditText);
        double paidValue = getAmountValue(binding.paidAmountEditText);

        if (TextUtils.isEmpty(details)) {
            binding.detailsEditText.setError(getString(R.string.ar_text_5));
            return;
        }
        if (TextUtils.isEmpty(binding.invoiceAmountEditText.getText().toString().trim())) {
            binding.invoiceAmountEditText.setError(getString(R.string.ar_text_5));
            return;
        }
        if (currentMode != Mode.VIEW_EDIT) {
            if (!licenseManager.canCreateTransaction()) {
                if (syncPreferences.canCreateTransaction()) {
                    showNoOperationsDialog(getString(R.string.trans_limit_titel),getString(R.string.transaction_limit));
                    syncPreferences.setCanCreateTransaction(false);
                }else if(!licenseManager.checkDailyLimit()) {
                    showNoOperationsDialog(getString(R.string.daily_limit_titel),getString(R.string.daily_limit));
                    return;
                }
                syncPreferences.setCanCreateTransaction(false);
            }

        }
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Account account = repository.getAccountDao().getAccountByIdBlocking(selectedAccount.getId());
            // إذا كنا في وضع التعديل، نحدث الفاتورة الحالية

            if (currentMode == Mode.VIEW_EDIT) {
                // تحديث المعاملات الحالية بدلاً من حذفها
                List<Transaction> updateInvoiceTransactions = new ArrayList<>();
//                .
//                invoiceTx = invoiceTxs.get(0);
//                paymentTx = invoiceTxs.size() > 1 ? invoiceTxs.get(1) : null;
                // تحديث معاملة الفاتورة
                invoiceTx.setAccountId(account.getId());
                invoiceTx.setBillType(billType);
                invoiceTx.setAccountFirestoreId(account.getFirestoreId());
                invoiceTx.setDetails(details);
                invoiceTx.setAmount(invoiceValue);
                invoiceTx.setCurrencyId(currencyId);
                invoiceTx.setType(isSales ? 1 : -1);
                invoiceTx.setTimestamp(selectedDate.getTime());
                invoiceTx.setLastModified(System.currentTimeMillis());
                invoiceTx.setSyncStatus("EDITED");
                updateInvoiceTransactions.add(invoiceTx);
                // تحديث أو إزالة معاملة الدفع
                if (paidValue > 0) {
                    if (paymentTx != null) {
                        paymentTx.setAccountId(account.getId());
                        paymentTx.setBillType(subBillType);
                        paymentTx.setDetails(subDetails + " " + details);
                        paymentTx.setAmount(paidValue);
                        paymentTx.setAccountFirestoreId(account.getFirestoreId());
                        paymentTx.setCurrencyId(currencyId);
                        paymentTx.setType(isSales ? -1 : 1);
                        paymentTx.setTimestamp(selectedDate.getTime());
                        paymentTx.setLastModified(System.currentTimeMillis());
                        paymentTx.setSyncStatus("EDITED");
//                        repository.updateTransaction(paymentTx);
                        updateInvoiceTransactions.add(paymentTx);

                    } else {
                        // إنشاء معاملة دفع جديدة إذا لم تكن موجودة
                        paymentTx = new Transaction();
                        paymentTx.setAccountId(account.getId());
                        paymentTx.setImportID(editImportId);
                        paymentTx.setBillType(subBillType);
                        paymentTx.setDetails(subDetails + " " + details);
                        paymentTx.setAmount(paidValue);
                        paymentTx.setAccountFirestoreId(account.getFirestoreId());
                        paymentTx.setCurrencyId(currencyId);
                        paymentTx.setType(isSales ? -1 : 1);
                        paymentTx.setTimestamp(selectedDate.getTime());
                        paymentTx.setOwnerUID(uid);
                        paymentTx.setFirestoreId(UUIDGenerator.generateSequentialUUID());
                        paymentTx.setSyncStatus("NEW");
                        paymentTx.setLastModified(System.currentTimeMillis());
//                        repository.insertTransaction(paymentTx);
                        updateInvoiceTransactions.add(paymentTx);

                    }
                } else if (paymentTx != null) {
                    // إذا كان المبلغ المدفوع صفرًا ونوجد معاملة دفع، نقوم بحذفها
//                    repository.deleteTransaction(paymentTx);
                    paymentTx.setLastModified(System.currentTimeMillis());
                    paymentTx.setSyncStatus("DELETED");

                    updateInvoiceTransactions.add(paymentTx);
                }
                // تحديث معاملة الفاتورة
                repository.updateInvoiceTransactions(updateInvoiceTransactions);
            } else {
                // وضع الإنشاء: إضافة فاتورة جديدة
                int nextImportId = repository.getMaxImportId() + 1;
                List<Transaction> invoiceTransactions = new ArrayList<>();
                Transaction invoiceTx = new Transaction();
                invoiceTx.setAccountId(account.getId());
                invoiceTx.setImportID(nextImportId);
                invoiceTx.setBillType(billType);
                invoiceTx.setAccountFirestoreId(account.getFirestoreId());
                invoiceTx.setDetails(details);
                invoiceTx.setAmount(invoiceValue);
                invoiceTx.setCurrencyId(currencyId);
                invoiceTx.setType(isSales ? 1 : -1);
                invoiceTx.setTimestamp(selectedDate.getTime());
                invoiceTx.setOwnerUID(uid);
                invoiceTx.setFirestoreId(UUIDGenerator.generateSequentialUUID());
                invoiceTx.setSyncStatus("NEW");
                invoiceTx.setLastModified(System.currentTimeMillis());
                invoiceTransactions.add(invoiceTx);

                if (paidValue > 0) {
                    Transaction paymentTx = new Transaction();
                    paymentTx.setAccountId(account.getId());
                    paymentTx.setImportID(nextImportId);
                    paymentTx.setBillType(subBillType);
                    paymentTx.setDetails(subDetails + " " + details);
                    paymentTx.setAmount(paidValue);
                    paymentTx.setAccountFirestoreId(account.getFirestoreId());
                    paymentTx.setCurrencyId(currencyId);
                    paymentTx.setType(isSales ? -1 : 1);
                    paymentTx.setTimestamp(selectedDate.getTime());
                    paymentTx.setOwnerUID(uid);
                    paymentTx.setFirestoreId(UUIDGenerator.generateSequentialUUID());
                    paymentTx.setSyncStatus("NEW");
                    paymentTx.setLastModified(System.currentTimeMillis());
                    invoiceTransactions.add(paymentTx);
                }

                repository.insertInvoiceTransactions(invoiceTransactions);
//                licenseManager.incrementTransactionCount(currentUser);
//                currentUser.setTransactions_count(currentUser.getTransactions_count() + 1);

                licenseManager.incrementTransactionCount();

            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), getString(R.string.save_2), Toast.LENGTH_SHORT).show();
                    dismiss();
                });
            }
        });
    }
    private void showIfAccountNull(Runnable onConfirm) {
        String accountNames = binding.accountAutoComplete.getText().toString().trim();
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(getString(R.string.caution_titel))
                .setMessage(getString(R.string.info_null_Account,accountNames))
                .setPositiveButton(getString(R.string.yes), (dialog, which) -> onConfirm.run())
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    binding.accountAutoComplete.setText("");
                    binding.invoiceAmountEditText.setText("");
                    binding.remainingAmountTextView.setText("0.0");
                    binding.paidAmountEditText.setText("");
                    binding.detailsEditText.setText("");
                    binding.accountAutoComplete.requestFocus();
                })
                .setIcon(R.drawable.ic_alert)
                .show();
    }
    private void showNoOperationsDialog(String titel, String textInfo) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_no_operations, null);

        AlertDialog dialog = new MaterialAlertDialogBuilder(getContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();
        TextView titelTextView = dialogView.findViewById(R.id.titel_textView);
        TextView noTransactionTextView = dialogView.findViewById(R.id.no_transaction_textView);
        MaterialButton purchaseBtn = dialogView.findViewById(R.id.btn_purchase);
//        MaterialButton watchAdBtn = dialogView.findViewById(R.id.btn_watch_ad);
        MaterialButton inviteBtn = dialogView.findViewById(R.id.btn_invite);
        MaterialButton cancelBtn = dialogView.findViewById(R.id.btn_cancel);
        titelTextView.setText(titel);
        noTransactionTextView.setText(textInfo);
        purchaseBtn.setOnClickListener(v -> {
            dialog.dismiss();
            handlePurchaseApp();
        });
//        watchAdBtn.setOnClickListener(v -> {
//            dialog.dismiss();
//            simulateWatchAd();
//        });
        inviteBtn.setOnClickListener(v -> {
            dialog.dismiss();
            ReferralManager referralManager = new ReferralManager(getContext());
            referralManager.generateAndShareReferralLink(FirebaseAuth.getInstance().getCurrentUser());
        });
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void handlePurchaseApp() {

        GoogleAuthHelper googleAuthHelper=new GoogleAuthHelper(getContext(), new LicenseManager(getContext()),repository);
        if (!googleAuthHelper.isSignedIn()) {
            Toast.makeText(getContext(), getString(R.string.login_1), Toast.LENGTH_SHORT).show();
            return;
        }
        PurchaseCodeDialog.newInstance().show(getChildFragmentManager(), "PurchaseCodeDialog");
    }

    private void deleteInvoice() {
        new MaterialAlertDialogBuilder(getContext())
                .setTitle(getString(R.string.confirm_1))
                .setMessage(getString(R.string.confirm_delete_invoice))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.yes_delete), (d, w) -> {
                    repository.deleteInvoiceByImportId(editImportId);
                    Toast.makeText(getContext(), getString(R.string.success_delete_invoice), Toast.LENGTH_SHORT).show();
                    dismiss();
                })
                .show();
    }

    private String formatCurrency(double amount) {
        return NumberFormat.getNumberInstance(Locale.US).format(amount);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
    }
}