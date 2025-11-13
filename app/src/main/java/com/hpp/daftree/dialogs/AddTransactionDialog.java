package com.hpp.daftree.dialogs;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.databinding.DialogAddNewTransactionBinding;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.models.FormatingAmount;
import com.hpp.daftree.utils.DialogKeyboardUtils;
import com.hpp.daftree.ui.AccountDetailsViewModel;
import com.hpp.daftree.ui.CurrencyViewModel;
import com.hpp.daftree.utils.LicenseManager;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.syncmanagers.SyncPreferences;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AddTransactionDialog extends BottomSheetDialogFragment {

    private static final String ARG_ACCOUNT_ID = "account_id";
    private static final String ARG_ACCOUNT_NAME = "account_name";
    private static final String ARG_ACCOUNT_FIRESTORE_ID = "account_firestore_id";
    private static final String ARG_INITIAL_CURRENCY = "initial_currency";

    // Arguments for edit mode
    private static final String ARG_TRANSACTION_ID = "transaction_id";
    private static final String ARG_TRANSACTION_DETAILS = "transaction_details";
    private static final String ARG_TRANSACTION_AMOUNT = "transaction_amount";
    private static final String ARG_TRANSACTION_TYPE = "transaction_type";
    private static final String ARG_TRANSACTION_CURRENCY_ID = "transaction_currency_id";
    private static final String ARG_TRANSACTION_TIMESTAMP = "transaction_timestamp";

    private DialogAddNewTransactionBinding binding;
    private AccountDetailsViewModel accountViewModel;
    private CurrencyViewModel currencyViewModel;
    private LicenseManager licenseManager;
    private SyncPreferences syncPreferences;
    private SecureLicenseManager secureLicenseManager;

    private int accountId;
    private String accountName;
    private Account currentAccount;
    private Transaction currentTransaction;
    private List<Currency> availableCurrencies = new ArrayList<>();
    private Calendar selectedDate;
    private boolean isEditMode = false;
    private boolean isGuest = false;

    public interface OnTransactionSaveListener {
        void onTransactionSaved(Transaction transaction,String currencyName,double amount);
        void onCurrencyChanged(String newCurrencyName);
    }

    private OnTransactionSaveListener saveListener;

    public static AddTransactionDialog newInstance(int accountId, String accountName) {
        AddTransactionDialog dialog = new AddTransactionDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_ACCOUNT_ID, accountId);
        args.putString(ARG_ACCOUNT_NAME, accountName);
        dialog.setArguments(args);
        return dialog;
    }

    public static AddTransactionDialog newInstanceForEdit(Transaction transaction) {
        AddTransactionDialog dialog = new AddTransactionDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_ACCOUNT_ID, transaction.getAccountId());
        args.putString(ARG_ACCOUNT_NAME, "");
        args.putString(ARG_ACCOUNT_FIRESTORE_ID, transaction.getAccountFirestoreId());
        args.putInt(ARG_TRANSACTION_ID, transaction.getId());
        args.putString(ARG_TRANSACTION_DETAILS, transaction.getDetails());
        args.putDouble(ARG_TRANSACTION_AMOUNT, transaction.getAmount());
        args.putInt(ARG_TRANSACTION_TYPE, transaction.getType());
        args.putInt(ARG_TRANSACTION_CURRENCY_ID, transaction.getCurrencyId());
        args.putLong(ARG_TRANSACTION_TIMESTAMP, transaction.getTimestamp().getTime());
        dialog.setArguments(args);
        return dialog;
    }

    public static AddTransactionDialog newInstanceWithCurrency(int accountId, String accountName, String initialCurrency) {
        AddTransactionDialog dialog = new AddTransactionDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_ACCOUNT_ID, accountId);
        args.putString(ARG_ACCOUNT_NAME, accountName);
        args.putString(ARG_ACCOUNT_FIRESTORE_ID, "");
        args.putString(ARG_INITIAL_CURRENCY, initialCurrency);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // استخراج المعاملات
        if (getArguments() != null) {
            accountId = getArguments().getInt(ARG_ACCOUNT_ID);
            accountName = getArguments().getString(ARG_ACCOUNT_NAME);
            String accountFirestoreId = getArguments().getString(ARG_ACCOUNT_FIRESTORE_ID);

            // التحقق من وضع التعديل
            int transactionId = getArguments().getInt(ARG_TRANSACTION_ID, -1);
            if (transactionId != -1) {
                // إنشاء كائن المعاملة من البيانات المفككة
                currentTransaction = new Transaction();
                currentTransaction.setId(transactionId);
                currentTransaction.setAccountId(accountId);
                currentTransaction.setDetails(getArguments().getString(ARG_TRANSACTION_DETAILS, ""));
                currentTransaction.setAmount(getArguments().getDouble(ARG_TRANSACTION_AMOUNT, 0.0));
                currentTransaction.setType(getArguments().getInt(ARG_TRANSACTION_TYPE, 1));
                currentTransaction.setCurrencyId(getArguments().getInt(ARG_TRANSACTION_CURRENCY_ID, 1));

                long timestamp = getArguments().getLong(ARG_TRANSACTION_TIMESTAMP, System.currentTimeMillis());
                currentTransaction.setTimestamp(new Date(timestamp));

                isEditMode = true;
            }
        }

        // تهيئة التاريخ
        selectedDate = Calendar.getInstance();

        // تهيئة المتغيرات المطلوبة
        initializeManagers();
    }

    private void initializeManagers() {
        if (getContext() != null) {
            licenseManager = new LicenseManager(getContext());
            secureLicenseManager = SecureLicenseManager.getInstance(getContext());
            syncPreferences = new SyncPreferences(getContext());
            isGuest = secureLicenseManager.isGuest();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DialogAddNewTransactionBinding.inflate(inflater, container, false);

        // تطبيق TextWatcher العام
        MyApplication.applyGlobalTextWatcher(binding.getRoot());

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // إعداد النوافذ مع معالجة لوحة المفاتيح
        if (getDialog() != null && getDialog().getWindow() != null) {
            DialogKeyboardUtils.setupKeyboardHandling(getDialog().getWindow(), view);
        }

        setupViewModels();
        setupUI();
        setupListeners();
        loadCurrencyData();
    }

    private void setupViewModels() {
        // إعداد ViewModel للحسابات
        accountViewModel = new ViewModelProvider(requireParentFragment() != null ?
                requireParentFragment() : this,
                new ViewModelProvider.AndroidViewModelFactory(requireActivity().getApplication()))
                .get(AccountDetailsViewModel.class);

        // إعداد ViewModel للعملات
        currencyViewModel = new ViewModelProvider(this).get(CurrencyViewModel.class);
    }

    private void setupUI() {
        // إعداد اسم الحساب
        binding.accountNameEditText.setText(accountName);
        binding.accountNameEditText.setEnabled(false);

        // إعداد التاريخ الحالي في وضع الإضافة
        if (!isEditMode) {
            updateDateDisplay();
        } else if (currentTransaction != null) {
            populateTransactionData();
        }

        // تطبيق تنسيق المبالغ
        FormatingAmount.applyTo(binding.amountEditText);

        // إخفاء أزرار التعديل والحذف في وضع الإضافة
        binding.editDeleteButtonsLayout.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
        binding.saveButtonsLayout.setVisibility(isEditMode ? View.GONE : View.VISIBLE);
    }

    private void setupListeners() {
        // اختيار التاريخ
        binding.dateEditText.setOnClickListener(v -> showDatePicker());

        // أزرار الحفظ
        binding.debitButton.setOnClickListener(v -> saveTransaction(1)); // مدين
        binding.creditButton.setOnClickListener(v -> saveTransaction(-1)); // دائن

        // أزرار التعديل والحذف
        binding.editButton.setOnClickListener(v -> switchToEditMode());
        binding.deleteButton.setOnClickListener(v -> showDeleteConfirmation());

        // حاسبة المبالغ
        binding.amountLayout.setEndIconOnClickListener(v -> showCalculator());

        // إغلاق لوحة المفاتيح عند لمس عناصر أخرى
        binding.currencySpinnerAutocomplete.setOnTouchListener((v, event) -> {
            DialogKeyboardUtils.hideKeyboard(v);
            return false;
        });

        // إعداد التفاصيل
        setupDetailsAutoComplete();

        // إضافة TextWatcher للمبلغ
        binding.amountEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // حفظ القيمة لتجنب مشاكل التنسيق
                if (!s.toString().equals(binding.amountEditText.getText().toString())) {
                    binding.amountEditText.removeTextChangedListener(this);
                    String cleanedText = s.toString().replaceAll(",", "");
                    if (!cleanedText.isEmpty()) {
                        try {
                            double amount = Double.parseDouble(cleanedText);
                            String formattedText = FormatingAmount.formatForDisplay(amount);
                            binding.amountEditText.setText(formattedText);
                            binding.amountEditText.setSelection(formattedText.length());
                        } catch (NumberFormatException e) {
                            binding.amountEditText.setText(cleanedText);
                        }
                    }
                    binding.amountEditText.addTextChangedListener(this);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadCurrencyData() {
        // تحميل قائمة العملات المتاحة
        currencyViewModel.getAllCurrencies().observe(getViewLifecycleOwner(), currencies -> {
            if (currencies != null && !currencies.isEmpty()) {
                availableCurrencies.clear();
                availableCurrencies.addAll(currencies);

                setupCurrencySpinner();
            }
        });
    }

    private void setupCurrencySpinner() {
        if (availableCurrencies.isEmpty()) return;

        List<String> currencyNames = new ArrayList<>();
        for (Currency currency : availableCurrencies) {
            currencyNames.add(currency.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, currencyNames);
        binding.currencySpinnerAutocomplete.setAdapter(adapter);

        // إضافة TextWatcher للتعامل مع تغيير العملة
        binding.currencySpinnerAutocomplete.setOnItemClickListener((parent, view, position, id) -> {
            String selectedCurrency = adapter.getItem(position);
            if (saveListener != null && selectedCurrency != null) {
                saveListener.onCurrencyChanged(selectedCurrency);
            }
        });

        binding.currencySpinnerAutocomplete.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // إشعار عند تغيير العملة يدوياً
                if (saveListener != null && !TextUtils.isEmpty(s.toString())) {
                    saveListener.onCurrencyChanged(s.toString());
                }
            }
        });

        // تحديد العملة الافتراضية أو الحالية
        String initialCurrency = getArguments() != null ?
                getArguments().getString(ARG_INITIAL_CURRENCY) : null;

        if (isEditMode && currentTransaction != null) {
            String currentCurrencyName = getCurrencyNameById(currentTransaction.getCurrencyId());
            if (currentCurrencyName != null) {
                binding.currencySpinnerAutocomplete.setText(currentCurrencyName, false);
            }
        } else if (initialCurrency != null && !initialCurrency.isEmpty()) {
            binding.currencySpinnerAutocomplete.setText(initialCurrency, false);
        } else if (!currencyNames.isEmpty()) {
            binding.currencySpinnerAutocomplete.setText(currencyNames.get(0), false);
        }
    }

    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    selectedDate.set(Calendar.YEAR, year);
                    selectedDate.set(Calendar.MONTH, month);
                    selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateDisplay();
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.show();
    }

    private void updateDateDisplay() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        binding.dateEditText.setText(sdf.format(selectedDate.getTime()));
    }

    private void populateTransactionData() {
        if (currentTransaction == null) return;

        NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);

        // إعداد المبالغ
        binding.amountEditText.setText(FormatingAmount.formatForDisplay(currentTransaction.getAmount()));

        // إعداد التفاصيل
        binding.detailsEditText.setText(currentTransaction.getDetails());

        // إعداد التاريخ
        selectedDate.setTime(currentTransaction.getTimestamp());
        updateDateDisplay();

        // إعداد العملة
        String currencyName = getCurrencyNameById(currentTransaction.getCurrencyId());
        if (currencyName != null) {
            binding.currencySpinnerAutocomplete.setText(currencyName, false);
        }
    }

    private void saveTransaction(int type) {
        if (!validateInput()) {
            return;
        }

        double newAmount = getAmount();
        String newDetails = getDetails();
        int newType = type;
        int currencyId = getCurrencyId();
        String syncStatus = "NEW";

        // في حالة التعديل
        if (isEditMode && currentTransaction != null) {
            Transaction updatedTransaction = new Transaction();
            updatedTransaction.setId(currentTransaction.getId());
            updatedTransaction.setAccountId(currentTransaction.getAccountId());
            updatedTransaction.setAmount(newAmount);
            updatedTransaction.setType(newType);
            updatedTransaction.setDetails(newDetails);
            updatedTransaction.setCurrencyId(currencyId);
            updatedTransaction.setLastModified(System.currentTimeMillis());
            // استخدام التاريخ الجديد من منتقي التاريخ
            updatedTransaction.setTimestamp(selectedDate.getTime());

            // تحديث في ViewModel
            accountViewModel.updateTransaction(updatedTransaction);

            // إشعار بتغيير العملة إذا تم تغييرها
            String oldCurrencyName = findCurrencyNameById(currentTransaction.getCurrencyId());
            String newCurrencyName = findCurrencyNameById(currencyId);
            if (saveListener != null && !TextUtils.equals(oldCurrencyName, newCurrencyName)) {
                saveListener.onCurrencyChanged(newCurrencyName);
            }

            if (saveListener != null) {
                saveListener.onTransactionSaved(updatedTransaction,newCurrencyName,newAmount);
            }

        }
        // في حالة الإضافة
        else {
            // فحص حدود الترخيص
            if (licenseManager != null) {
                if (isGuest) {
                    // Guest mode - فحص الحدود اليومية فقط
                    if (!licenseManager.canCreateTransaction()) {
                        if (getContext() != null) {
                            showNoOperationsDialog(getContext().getString(R.string.daily_limit_titel),
                                    getContext().getString(R.string.daily_limit));
                        }
                        return;
                    }
                } else {
                    // Regular mode - فحص مضاعف
                    if (!licenseManager.canCreateTransaction()) {
                        if (syncPreferences != null) {
                            if (syncPreferences.canCreateTransaction()) {
                                if (getContext() != null) {
                                    showNoOperationsDialog(getContext().getString(R.string.trans_limit_titel),
                                            getContext().getString(R.string.transaction_limit));
                                }
                                syncPreferences.setCanCreateTransaction(false);
                            } else if (!licenseManager.checkDailyLimit()) {
                                if (getContext() != null) {
                                    showNoOperationsDialog(getContext().getString(R.string.daily_limit_titel),
                                            getContext().getString(R.string.daily_limit));
                                }
                                return;
                            }
                            syncPreferences.setCanCreateTransaction(false);
                        }
                    }
                }
            }

            // إنشاء معاملة جديدة
            Transaction newTransaction = new Transaction();
            newTransaction.setAccountId(accountId);
            newTransaction.setAmount(newAmount);
            newTransaction.setType(newType);
            newTransaction.setDetails(newDetails);
            newTransaction.setCurrencyId(currencyId);
            newTransaction.setSyncStatus(syncStatus);
            // إضافة معرف Firebase إذا كان متوفراً
            if (currentAccount != null && currentAccount.getFirestoreId() != null) {
                newTransaction.setAccountFirestoreId(currentAccount.getFirestoreId());
            }
            // استخدام التاريخ الجديد من منتقي التاريخ
            newTransaction.setTimestamp(selectedDate.getTime());
            String newCurrencyName = findCurrencyNameById(currencyId);
            // إضافة في ViewModel
            accountViewModel.addTransaction(newTransaction);

            // زيادة عدد العمليات المنتظرة
            if (licenseManager != null) {
                licenseManager.incrementTransactionCount();
            }

            if (saveListener != null) {
                saveListener.onTransactionSaved(newTransaction,newCurrencyName,newAmount);
            }
        }

        dismiss();
    }

    private String findCurrencyNameById(int currencyId) {
        for (Currency currency : availableCurrencies) {
            if (currency.getId() == currencyId) {
                return currency.getName();
            }
        }
        return "";
    }

    private void showNoOperationsDialog(String title, String message) {
        // تنفيذ نفس منطق showNoOperationsDialog في Activity
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.menu_purchase_app), null)
                .setPositiveButton(getString(R.string.menu_invite_friend), null)
                .setNegativeButton(getString(R.string.cancel), null)
                .setIcon(R.drawable.ic_alert)
                .show();
    }

    private boolean validateInput() {
        boolean isValid = true;

        // التحقق من المبلغ
        double amount = getAmount();
        if (amount <= 0) {
            binding.amountEditText.setError(getString(R.string.required));
            isValid = false;
        }

        // التحقق من التفاصيل
        String details = getDetails();
        if (TextUtils.isEmpty(details)) {
            binding.detailsEditText.setError(getString(R.string.required));
            isValid = false;
        }

        // التحقق من العملة
        int currencyId = getCurrencyId();
        if (currencyId <= 0) {
            Toast.makeText(requireContext(), getString(R.string.required), Toast.LENGTH_SHORT).show();
            isValid = false;
        }

        return isValid;
    }

    private double getAmount() {
        String amountStr = binding.amountEditText.getText().toString().replaceAll(",", "");
        try {
            return Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String getDetails() {
        return binding.detailsEditText.getText().toString().trim();
    }

    private int getCurrencyId() {
        String currencyName = binding.currencySpinnerAutocomplete.getText().toString();
        for (Currency currency : availableCurrencies) {
            if (currency.getName().equals(currencyName)) {
                return currency.getId();
            }
        }
        return -1;
    }

    private String getCurrencyNameById(int currencyId) {
        for (Currency currency : availableCurrencies) {
            if (currency.getId() == currencyId) {
                return currency.getName();
            }
        }
        return null;
    }

    private void setupDetailsAutoComplete() {
        // إعداد الإكمال التلقائي للتفاصيل
        binding.detailsEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s.toString();
                if (!text.contains(" ") && text.length() > 0) {
                    // إرسال نص البحث إلى ViewModel
                    if (accountViewModel != null) {
                        accountViewModel.setDetailsQuery(text);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void showCalculator() {
        String currentValue = binding.amountEditText.getText().toString().replaceAll(",", "");
        if (currentValue.isEmpty()) currentValue = "0";

        CalculatorDialog calculatorDialog = CalculatorDialog.newInstance(currentValue);
        calculatorDialog.setOnCalculationCompleteListener(result -> {
            if (result != null && !result.isEmpty()) {
                try {
                    String cleanString = result.replaceAll("[,]", "");
                    double parsed = Double.parseDouble(cleanString);
                    String formattedResult = FormatingAmount.formatForDisplay(parsed);
                    binding.amountEditText.setText(formattedResult);
                    binding.amountEditText.setSelection(formattedResult.length());
                } catch (NumberFormatException e) {
                    binding.amountEditText.setText(result);
                }
            }
        });

        calculatorDialog.show(getChildFragmentManager(), "CalculatorDialog");
    }

    private void switchToEditMode() {
        binding.saveButtonsLayout.setVisibility(View.VISIBLE);
        binding.editDeleteButtonsLayout.setVisibility(View.GONE);

        binding.amountLayout.setEnabled(true);
        binding.detailsLayout.setEnabled(true);
        binding.currencySpinnerLayout.setEnabled(true);
        binding.dateLayout.setEnabled(true);
    }

    private void showDeleteConfirmation() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.delete_confirmation_title))
                .setMessage(getString(R.string.delete_confirmation_message))
                .setPositiveButton(getString(R.string.yes_delete), (dialog, which) -> {
                    if (saveListener != null && currentTransaction != null) {
                        saveListener.onTransactionSaved(null,null,0.0); // NULL يعني الحذف
                    }
                    dismiss();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    public void setOnTransactionSaveListener(OnTransactionSaveListener listener) {
        this.saveListener = listener;
    }

    // Methods for external data setup
    public void setAvailableCurrencies(List<Currency> currencies) {
        this.availableCurrencies.clear();
        if (currencies != null) {
            this.availableCurrencies.addAll(currencies);
        }
        if (isViewCreated()) {
            setupCurrencySpinner();
        }
    }

    public void setCurrentAccount(Account account) {
        this.currentAccount = account;
    }

    public void setInitialCurrency(String currencyName) {
        if (!TextUtils.isEmpty(currencyName) && binding != null) {
            binding.currencySpinnerAutocomplete.setText(currencyName, false);
        }
    }

    public void setTransactionData(Transaction transaction) {
        if (transaction != null) {
            this.currentTransaction = transaction;
            this.isEditMode = true;
            if (isViewCreated()) {
                populateTransactionData();
            }
        }
    }

    private boolean isViewCreated() {
        return binding != null && binding.getRoot() != null && binding.getRoot().isAttachedToWindow();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}