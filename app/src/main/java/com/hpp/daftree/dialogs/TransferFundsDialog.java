package com.hpp.daftree.dialogs;

import android.content.Context;
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
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.databinding.DialogTransferFundsBinding;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.models.FormatingAmount;
import com.hpp.daftree.utils.DialogKeyboardUtils;
import com.hpp.daftree.ui.AccountDetailsViewModel;
import com.hpp.daftree.ui.CurrencyViewModel;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransferFundsDialog extends BottomSheetDialogFragment {

    private static final String ARG_ACCOUNT_ID = "account_id";
    private static final String ARG_ACCOUNT_NAME = "account_name";
    private static final String ARG_ACCOUNT_FIRESTORE_ID = "account_firestore_id";
    private static final String ARG_INITIAL_CURRENCY = "initial_currency";

    private DialogTransferFundsBinding binding;
    private AccountDetailsViewModel accountViewModel;
    private CurrencyViewModel currencyViewModel;

    private int currentAccountId;
    private String currentAccountName;
    private String currentAccountFirestoreId;
    private Account currentAccount;
    private String initialCurrency;
    private List<Account> allAccounts = new ArrayList<>();
    private List<Currency> availableCurrencies = new ArrayList<>();

    public interface OnTransferCompleteListener {
        void onTransferComplete();
        void onCurrencyChanged(String newCurrencyName);
    }

    private OnTransferCompleteListener transferListener;

    public static TransferFundsDialog newInstance(int accountId, String accountName, String initialCurrency) {
        TransferFundsDialog dialog = new TransferFundsDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_ACCOUNT_ID, accountId);
        args.putString(ARG_ACCOUNT_NAME, accountName);
        args.putString(ARG_INITIAL_CURRENCY, initialCurrency);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // استخراج المعاملات
        if (getArguments() != null) {
            currentAccountId = getArguments().getInt(ARG_ACCOUNT_ID);
            currentAccountName = getArguments().getString(ARG_ACCOUNT_NAME);
            initialCurrency = getArguments().getString(ARG_INITIAL_CURRENCY);
            currentAccountFirestoreId = getArguments().getString(ARG_ACCOUNT_FIRESTORE_ID, "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DialogTransferFundsBinding.inflate(inflater, container, false);

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
        loadAccountData();
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
        // إعداد اسم الحساب الحالي في حقل "من حساب" (افتراضياً)
        binding.creditorAccountAutocomplete.setText(currentAccountName);
        binding.creditorAccountAutocomplete.setEnabled(false); // الحساب الحالي ثابت

        // تطبيق تنسيق المبالغ
        FormatingAmount.applyTo(binding.amountEditText);
    }

    private void setupListeners() {
        // زر التحويل
        binding.transferButton.setOnClickListener(v -> processTransfer());

        // حاسبة المبالغ
        binding.amountLayout.setEndIconOnClickListener(v -> showCalculator());

        // إغلاق لوحة المفاتيح عند لمس عناصر أخرى
        binding.currencySpinnerAutocomplete.setOnTouchListener((v, event) -> {
            DialogKeyboardUtils.hideKeyboard(v);
            return false;
        });

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

        // إضافة TextWatcher للعملة لتغيير العملة في الشاشة الرئيسية
        binding.currencySpinnerAutocomplete.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (transferListener != null && !TextUtils.isEmpty(s)) {
                    transferListener.onCurrencyChanged(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadAccountData() {
        // تحميل جميع الحسابات المتاحة
        accountViewModel.getAllAccounts().observe(getViewLifecycleOwner(), accounts -> {
            if (accounts != null && !accounts.isEmpty()) {
                allAccounts.clear();
                allAccounts.addAll(accounts);
                setupAccountAutoComplete();
            }
        });
    }

    private void setupAccountAutoComplete() {
        if (allAccounts.isEmpty()) return;

        List<String> accountNames = new ArrayList<>();
        for (Account account : allAccounts) {
            accountNames.add(account.getAccountName());
        }

        // إعداد AutoCompleteTextView لحقل "إلى حساب"
        ArrayAdapter<String> accountAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, accountNames);
        binding.debtorAccountAutocomplete.setAdapter(accountAdapter);
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

        // إعداد العملة الافتراضية
        if (initialCurrency != null && !initialCurrency.isEmpty()) {
            binding.currencySpinnerAutocomplete.setText(initialCurrency);
        }
    }

    private void processTransfer() {
        String creditorAccountName = binding.creditorAccountAutocomplete.getText().toString().trim();
        String debtorAccountName = binding.debtorAccountAutocomplete.getText().toString().trim();
        String amountText = binding.amountEditText.getText().toString().replaceAll(",", "").trim();
        String currencyName = binding.currencySpinnerAutocomplete.getText().toString().trim();
        String details = binding.detailsEditText.getText().toString().trim();

        // التحقق من صحة البيانات
        if (TextUtils.isEmpty(creditorAccountName) || TextUtils.isEmpty(debtorAccountName)) {
            Toast.makeText(requireContext(), R.string.error_select_accounts, Toast.LENGTH_SHORT).show();
            return;
        }

        if (creditorAccountName.equals(debtorAccountName)) {
            Toast.makeText(requireContext(), R.string.error_same_account, Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(amountText)) {
            binding.amountEditText.setError(getString(R.string.error_empty_amount));
            binding.amountEditText.requestFocus();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountText);
            if (amount <= 0) {
                binding.amountEditText.setError(getString(R.string.error_invalid_amount));
                binding.amountEditText.requestFocus();
                return;
            }
        } catch (NumberFormatException e) {
            binding.amountEditText.setError(getString(R.string.error_invalid_amount));
            binding.amountEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(currencyName)) {
            Toast.makeText(requireContext(), R.string.error_select_currency, Toast.LENGTH_SHORT).show();
            return;
        }

        // العثور على الحسابات والعملة
        Account creditorAccount = findAccountByName(creditorAccountName);
        Account debtorAccount = findAccountByName(debtorAccountName);
        Currency currency = findCurrencyByName(currencyName);

        if (creditorAccount == null || debtorAccount == null) {
            Toast.makeText(requireContext(), R.string.error_account_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        if (currency == null) {
            Toast.makeText(requireContext(), R.string.error_currency_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        // إنشاء معاملتين: خصم من الدائن وإضافة للمدين
        createTransferTransactions(creditorAccount, debtorAccount, currency, amount, details);
    }

    private Account findAccountByName(String accountName) {
        for (Account account : allAccounts) {
            if (account.getAccountName().equals(accountName)) {
                return account;
            }
        }
        return null;
    }

    private Currency findCurrencyByName(String currencyName) {
        for (Currency currency : availableCurrencies) {
            if (currency.getName().equals(currencyName)) {
                return currency;
            }
        }
        return null;
    }

    private void createTransferTransactions(Account creditorAccount, Account debtorAccount,
                                            Currency currency, double amount, String details) {
        long timestamp = System.currentTimeMillis();
        String transactionDetails = TextUtils.isEmpty(details) ?
                String.format(Locale.getDefault(), "تحويل من %s إلى %s",
                        creditorAccount.getAccountName(), debtorAccount.getAccountName()) :
                details;

        // إنشاء معاملة خصم من الحساب الدائن (المحول منه)
        Transaction creditorTransaction = new Transaction();
        creditorTransaction.setAccountId(creditorAccount.getId());
        creditorTransaction.setAccountFirestoreId(creditorAccount.getFirestoreId());
        creditorTransaction.setAmount(amount);
        creditorTransaction.setType(-1); // دائن (سالب)
        creditorTransaction.setDetails(transactionDetails);
        creditorTransaction.setCurrencyId(currency.getId());
        creditorTransaction.setTimestamp(new Date(timestamp));
        creditorTransaction.setSyncStatus("NEW");
        creditorTransaction.setLastModified(timestamp);

        // إنشاء معاملة إضافة للحساب المدين (المحول إليه)
        Transaction debtorTransaction = new Transaction();
        debtorTransaction.setAccountId(debtorAccount.getId());
        debtorTransaction.setAccountFirestoreId(debtorAccount.getFirestoreId());
        debtorTransaction.setAmount(amount);
        debtorTransaction.setType(1); // مدين (موجب)
        debtorTransaction.setDetails(transactionDetails);
        debtorTransaction.setCurrencyId(currency.getId());
        debtorTransaction.setTimestamp(new Date(timestamp));
        debtorTransaction.setSyncStatus("NEW");
        debtorTransaction.setLastModified(timestamp);

        // حفظ المعاملتين
        accountViewModel.addTransaction(creditorTransaction);
        accountViewModel.addTransaction(debtorTransaction);

        // إشعار النجاح
        Toast.makeText(requireContext(), R.string.transfer_success, Toast.LENGTH_SHORT).show();

        // إغلاق الديالوج
        if (transferListener != null) {
            transferListener.onTransferComplete();
        }
        dismiss();
    }

    private void showCalculator() {
        // تنفيذ الحاسبة (يمكن إضافتها لاحقاً)
        // الآن نفعل فقط عرض message toast
//        Toast.makeText(requireContext(), "الحاسبة ستتم إضافتها قريباً", Toast.LENGTH_SHORT).show();
    }

    public void setOnTransferCompleteListener(OnTransferCompleteListener listener) {
        this.transferListener = listener;
    }

    public void setAvailableAccounts(List<Account> accounts) {
        this.allAccounts = accounts != null ? accounts : new ArrayList<>();
        setupAccountAutoComplete();
    }

    public void setAvailableCurrencies(List<Currency> currencies) {
        this.availableCurrencies = currencies != null ? currencies : new ArrayList<>();
        setupCurrencySpinner();
    }

    public void setCurrentAccount(Account account) {
        this.currentAccount = account;
        if (account != null) {
            currentAccountId = account.getId();
            currentAccountName = account.getAccountName();
            currentAccountFirestoreId = account.getFirestoreId();

            if (binding != null) {
                binding.creditorAccountAutocomplete.setText(currentAccountName);
            }
        }
    }

    @Override
    public void show(@NonNull FragmentManager manager, @Nullable String tag) {
        // تجاهل المحاولة المتكررة لعرض الديالوج
        if (manager.findFragmentByTag(tag) != null) {
            return;
        }
        super.show(manager, tag);
    }
}