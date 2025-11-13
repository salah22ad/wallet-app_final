package com.hpp.daftree.ui;

import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.hpp.daftree.AccountTypeViewModel;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.TransactionItem;
import com.hpp.daftree.adapters.TransactionHistoryAdapter;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.database.User;
import com.hpp.daftree.databinding.ActivityAddTransactionBinding;
import com.hpp.daftree.databinding.DialogAddAccountBinding;
import com.hpp.daftree.databinding.DialogEditAccountBinding;
import com.hpp.daftree.databinding.DialogShareOptionsBinding;
import com.hpp.daftree.dialogs.CalculatorDialog;
import com.hpp.daftree.dialogs.PurchaseCodeDialog;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.models.FormatingAmount;
import com.hpp.daftree.syncmanagers.SyncPreferences;
import com.hpp.daftree.utils.EdgeToEdgeUtils;
import com.hpp.daftree.utils.GoogleAuthHelper;
import com.hpp.daftree.utils.LicenseManager;
import com.hpp.daftree.utils.ReferralManager;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class AddTransactionActivity extends BaseActivity implements CalculatorDialog.OnCalculationCompleteListener {

    private static final String TAG = "AddTransactionActivity";

    private ActivityAddTransactionBinding binding;
    private AddTransactionViewModel viewModel;
    private TransactionHistoryAdapter historyAdapter;
    private SyncPreferences syncPreferences;
    private final Calendar selectedDate = Calendar.getInstance();
    private Transaction lastSavedTransaction;
    private DaftreeRepository repository;
    private CurrencyViewModel currencyViewModel;
    private final List<Currency> availableCurrencies = new ArrayList<>();
    private AccountTypeViewModel accountTypeViewModel;
    private List<AccountType> availableAccountTypes = new ArrayList<>();
    private User currentUser;
    private LicenseManager licenseManager;
    private boolean isGuest;
    private String guestUID;

    // --- المتغيرات الجديدة للمنطق المحسّن ---
    private final Map<String, Integer> accountNameToIdMap = new HashMap<>();
    private List<Account> allAccounts = new ArrayList<>();
    private ArrayAdapter<String> accountSuggestionsAdapter;
    private ArrayAdapter<String> detailSuggestionsAdapter;
    private boolean isSelectionFromList = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddTransactionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeUtils.applyEdgeToEdge(this, binding.toolbar);
        MyApplication.applyGlobalTextWatcher(binding.getRoot());

        isGuest = SecureLicenseManager.getInstance(this).isGuest();
        guestUID = SecureLicenseManager.getInstance(this).guestUID();

        viewModel = new ViewModelProvider(this).get(AddTransactionViewModel.class);
        currencyViewModel = new ViewModelProvider(this).get(CurrencyViewModel.class);
        accountTypeViewModel = new ViewModelProvider(this).get(AccountTypeViewModel.class);

        syncPreferences = new SyncPreferences(this);
        repository = new DaftreeRepository(getApplication());
        licenseManager = new LicenseManager(this);

        setupInitialState();
        setupRecyclerView();
        setupCurrencySpinner();
        setupObservers();
        setupEventListeners(); // ✅ دالة واحدة مركزية ومُنقَّحة
        FormatingAmount.applyTo(binding.amountEditText);

        handleIntentExtras();
    }

    private void handleIntentExtras() {
        if (getIntent().hasExtra("ACCOUNT_ID")) {
            int passedAccountId = getIntent().getIntExtra("ACCOUNT_ID", -1);
            String passedAccountName = getIntent().getStringExtra("ACCOUNT_NAME");
            String passedCurrency = getIntent().getStringExtra("CURRENCY");

            if (passedAccountId != -1) {
                binding.accountNameAutoComplete.setText(passedAccountName);
                binding.accountNameAutoComplete.setEnabled(false);
                viewModel.setSelectedAccount(passedAccountId);
                viewModel.setSelectedCurrency(passedCurrency);
                binding.amountEditText.requestFocus();
                new Handler(Looper.getMainLooper()).postDelayed(() -> viewModel.updateTransactionSource(), 300);
            }
        } else {
            String passedCurrency = getIntent().getStringExtra("CURRENCY");
            viewModel.setSelectedCurrency(passedCurrency);
        }
    }

    private void setupInitialState() {
        updateDateInView();
        accountSuggestionsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        binding.accountNameAutoComplete.setAdapter(accountSuggestionsAdapter);
        binding.accountNameAutoComplete.setThreshold(1);

        detailSuggestionsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        binding.detailsEditText.setAdapter(detailSuggestionsAdapter);
        binding.detailsEditText.setThreshold(1);

        binding.accountNameAutoComplete.requestFocus();
        showKeyboard(binding.accountNameAutoComplete);
    }

    private void setupRecyclerView() {
        historyAdapter = new TransactionHistoryAdapter(this, null);
        binding.transactionsHistoryRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.transactionsHistoryRecyclerView.setAdapter(historyAdapter);
    }

    private void setupCurrencySpinner() {
        currencyViewModel.getAllCurrencies().observe(this, currencies -> {
            if (currencies != null && !currencies.isEmpty()) {
                availableCurrencies.clear();
                availableCurrencies.addAll(currencies);
                List<String> currencyNames = availableCurrencies.stream().map(c -> c.name).collect(Collectors.toList());
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, currencyNames);
                binding.currencySpinnerAutocomplete.setAdapter(adapter);

                binding.currencySpinnerAutocomplete.setOnItemClickListener((parent, view, position, id) -> {
                    String selectedCurrency = (String) parent.getItemAtPosition(position);
                    viewModel.setSelectedCurrency(selectedCurrency);
                });

                String passedCurrency = getIntent().getStringExtra("CURRENCY");
                if (passedCurrency != null && currencyNames.contains(passedCurrency)) {
                    binding.currencySpinnerAutocomplete.setText(passedCurrency, false);
                    viewModel.setSelectedCurrency(passedCurrency);
                } else if (!currencyNames.isEmpty()) {
                    binding.currencySpinnerAutocomplete.setText(currencyNames.get(0), false);
                    viewModel.setSelectedCurrency(currencyNames.get(0));
                }
                viewModel.initCurrencyMap(availableCurrencies);
            }
        });
    }

    private void setupObservers() {
        viewModel.getUserProfile().observe(this, user -> this.currentUser = user);

        viewModel.getAllAccounts().observe(this, accounts -> {
            if (accounts != null) {
                this.allAccounts = accounts;
                updateAccountMaps(accounts);
                Log.d(TAG, "Account list updated. Total accounts: " + allAccounts.size());
            }
        });

        viewModel.getSelectedTransactionItems().observe(this, transactionItems -> {
            if (historyAdapter != null) {
                historyAdapter.submitList(transactionItems, () -> {
                    if (transactionItems != null && !transactionItems.isEmpty()) {
                        binding.transactionsHistoryRecyclerView.smoothScrollToPosition(0);
                    }
                });
            }
        });

        viewModel.getDetailSuggestions().observe(this, suggestions -> {
            detailSuggestionsAdapter.clear();
            if (suggestions != null && !suggestions.isEmpty()) {
                detailSuggestionsAdapter.addAll(suggestions);
            }
            detailSuggestionsAdapter.notifyDataSetChanged();
        });

        Observer<Object> transactionSourceObserver = o -> viewModel.updateTransactionSource();
        viewModel.getSelectedAccount().observe(this, transactionSourceObserver);
//        viewModel.getSelectedCurrency().observe(this, transactionSourceObserver);
    }

    private void updateAccountMaps(List<Account> accounts) {
        accountNameToIdMap.clear();
        String currentUID = isGuest ? guestUID : (FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "");
        if (currentUID.isEmpty()) return;

        for (Account account : accounts) {
            if (currentUID.equals(account.getOwnerUID()) && account.getAccountName() != null) {
                accountNameToIdMap.put(account.getAccountName(), account.getId());
            }
        }
    }

    private void setupEventListeners() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.dateEditText.setOnClickListener(v -> showDatePicker());

        // --- المستمع المحسّن لحقل اسم الحساب ---
        binding.accountNameAutoComplete.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isSelectionFromList) return;
                String query = s.toString();
                String normalizedText = normalizeText(s.toString());
                filterAccountSuggestions(query);
            }
        });

        binding.accountNameAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = (String) parent.getItemAtPosition(position);
            Integer accountId = accountNameToIdMap.get(selectedName);

            if (accountId != null) {
                isSelectionFromList = true;
                binding.accountNameAutoComplete.setText(selectedName);
                binding.accountNameAutoComplete.setSelection(selectedName.length());
                isSelectionFromList = false;

                viewModel.setSelectedAccount(accountId);
                binding.amountEditText.requestFocus();
                showKeyboard(binding.amountEditText);
            }
        });

        binding.accountNameAutoComplete.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                handleAccountNameAction();
                return true;
            }
            return false;
        });

        binding.accountNameAutoComplete.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                // تأخير بسيط للسماح لـ onItemClick بالعمل أولاً إذا تم الضغط على عنصر
                new Handler(Looper.getMainLooper()).postDelayed(this::handleAccountNameAction, 150);
            } else {
                binding.accountNameAutoComplete.selectAll();
            }
        });

        // --- المستمع المحسّن لحقل التفاصيل ---
        binding.detailsEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isSelectionFromList) return;
                String normalizedText = normalizeText(s.toString());
//                if (!normalizedText.contains(" ")) {
                    if (!normalizedText.isEmpty()) {
                    viewModel.setDetailsQuery(normalizedText);
                } else {
                    viewModel.setDetailsQuery(null);
                }
            }
        });

        binding.detailsEditText.setOnItemClickListener((parent, view, position, id) -> {
            isSelectionFromList = true;
            String selectedDetail = (String) parent.getItemAtPosition(position);
            binding.detailsEditText.setText(selectedDetail);
            binding.detailsEditText.setSelection(selectedDetail.length());
            isSelectionFromList = false;
            hideKeyboard();
        });

        binding.detailsEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard();
                return true;
            }
            return false;
        });

        // --- مستمعي الأزرار الأخرى ---
        binding.amountEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.detailsEditText.requestFocus();
                return true;
            }
            return false;
        });

        binding.debitButton.setOnClickListener(v -> validateAndSaveTransaction(1));
        binding.creditButton.setOnClickListener(v -> validateAndSaveTransaction(-1));

        binding.amountLayout.setEndIconOnClickListener(v -> {
            String currentValue = binding.amountEditText.getText().toString().replaceAll("[,]", "");
            CalculatorDialog.newInstance(currentValue).show(getSupportFragmentManager(), "CalculatorDialog");
        });
    }

    private void handleAccountNameAction() {
        // التحقق من أن الواجهة لا تزال نشطة
        if (isFinishing() || isDestroyed()) {
            return;
        }
        // التحقق من أن التركيز ليس على القائمة المنسدلة
        if (binding.accountNameAutoComplete.isPopupShowing()) {
            return;
        }

        String accountName = binding.accountNameAutoComplete.getText().toString().trim();
        if (TextUtils.isEmpty(accountName)) return;

        if (isAccountExists(accountName)) {
            Integer accountId = accountNameToIdMap.get(accountName);
            if (accountId != null && (viewModel.getSelectedAccount().getValue() == null || !viewModel.getSelectedAccount().getValue().equals(accountId))) {
                viewModel.setSelectedAccount(accountId);
            }
            // لا تنقل التركيز إذا كان المستخدم قد انتقل بالفعل إلى حقل آخر
            if (binding.accountNameAutoComplete.hasFocus()) {
                binding.amountEditText.requestFocus();
            }
        } else {
            showAddNewAccountDialog(accountName, true);
        }
    }

    private void filterAccountSuggestions(String query) {
        accountSuggestionsAdapter.clear();
        if (query == null || query.isEmpty()) {
            accountSuggestionsAdapter.notifyDataSetChanged();
            return;
        }

        List<String> filteredNames = allAccounts.stream()
                .map(Account::getAccountName)
                .filter(name -> name != null && name.toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());

        if (!filteredNames.isEmpty()) {
            accountSuggestionsAdapter.addAll(filteredNames);
        }
        accountSuggestionsAdapter.notifyDataSetChanged();
    }

    private String normalizeText(String text) {
        if (text == null || text.length() < 2) {
            return text;
        }
        for (int i = (text.length() / 2) + 1; i > 0; i--) {
            String candidate = text.substring(text.length() - i);
            StringBuilder rebuiltSequence = new StringBuilder();
            for (int j = 1; j <= candidate.length(); j++) {
                rebuiltSequence.append(candidate.substring(0, j));
            }
            if (text.endsWith(rebuiltSequence.toString())) {
                return candidate;
            }
        }
        return text;
    }

    private boolean isAccountExists(String accountName) {
        if (TextUtils.isEmpty(accountName)) return false;
        return accountNameToIdMap.containsKey(accountName.trim());
    }

    private void validateAndSaveTransaction(int type) {
        if (!licenseManager.canCreateTransaction()) {
            if (syncPreferences.canCreateTransaction()) {
                showNoOperationsDialog(getString(R.string.trans_limit_titel), getString(R.string.transaction_limit));
                syncPreferences.setCanCreateTransaction(false);
            } else if (!licenseManager.checkDailyLimit()) {
                showNoOperationsDialog(getString(R.string.daily_limit_titel), getString(R.string.daily_limit));
                return;
            }
            syncPreferences.setCanCreateTransaction(false);
        }

        String accountName = binding.accountNameAutoComplete.getText().toString().trim();
        String amountStr = binding.amountEditText.getText().toString().trim().replaceAll(",", "");
        String details = binding.detailsEditText.getText().toString().trim();
        String currencyName = binding.currencySpinnerAutocomplete.getText().toString();

        if (TextUtils.isEmpty(accountName) || !isAccountExists(accountName)) {
            showIfAccountNull(() -> showAddNewAccountDialog(accountName, false));
            return;
        }
        if (TextUtils.isEmpty(amountStr) || Double.parseDouble(amountStr) == 0) {
            binding.amountEditText.setError(getString(R.string.amount_required));
            return;
        }
        if (TextUtils.isEmpty(details)) {
            binding.detailsEditText.setError(getString(R.string.details_required));
            return;
        }
        if (TextUtils.isEmpty(currencyName)) {
            binding.currencySpinnerLayout.setError("يجب اختيار عملة");
            return;
        }

        Integer currencyId = availableCurrencies.stream()
                .filter(c -> c.name.equals(currencyName)).findFirst().map(c -> c.id).orElse(null);
        if (currencyId == null) {
            Toast.makeText(this, getString(R.string.wronge_currency), Toast.LENGTH_SHORT).show();
            return;
        }

        Integer accountId = accountNameToIdMap.get(accountName);
        double amount = Double.parseDouble(amountStr);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            long dateWithoutTime = getStartOfDay(selectedDate.getTime()).getTime();
            int duplicateCount = repository.findDuplicateTransaction(accountId, dateWithoutTime, amount, type, currencyId);
            runOnUiThread(() -> {
                if (duplicateCount > 0) {
                    showDuplicateWarningDialog(() -> saveTransaction(type, accountName, amount, details, currencyId));
                } else {
                    saveTransaction(type, accountName, amount, details, currencyId);
                }
            });
        });
    }

    private void saveTransaction(int type, String accountName, double amount, String details, int currencyId) {
        Integer accountId = accountNameToIdMap.get(accountName);
        if (accountId == null) return;

        Transaction newTransaction = new Transaction();
        newTransaction.setAccountId(accountId);
        newTransaction.setAmount(amount);
        newTransaction.setType(type);
        newTransaction.setCurrencyId(currencyId);
        newTransaction.setDetails(details);
        newTransaction.setTimestamp(selectedDate.getTime());

        viewModel.addTransaction(newTransaction);
        this.lastSavedTransaction = newTransaction;
        onTransactionSaved(amount * type);
        resetFieldsForNewEntry(accountName, accountId);
        licenseManager.incrementTransactionCount();
    }

    private void resetFieldsForNewEntry(String accountName, int accountId) {
        binding.amountEditText.setText("");
        binding.detailsEditText.setText("");
        binding.accountNameAutoComplete.setText(accountName);
        binding.accountNameAutoComplete.dismissDropDown();
        viewModel.setSelectedAccount(accountId);
        binding.amountEditText.requestFocus();
    }

    private void showAddNewAccountDialog(String name, boolean focusAmountAfter) {
        DialogAddAccountBinding dialogBinding = DialogAddAccountBinding.inflate(getLayoutInflater());
        dialogBinding.dialogAccountNameEditText.setText(name);
        MyApplication.applyGlobalTextWatcher(dialogBinding.getRoot());

        AutoCompleteTextView accountTypeSpinner = dialogBinding.spinnerAccountType;
        accountTypeViewModel.getAllAccountTypes().observe(this, types -> {
            if (types != null && !types.isEmpty()) {
                this.availableAccountTypes = types;
                List<String> typeNames = types.stream().map(t -> t.name).collect(Collectors.toList());
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, typeNames);
                accountTypeSpinner.setAdapter(adapter);
                if (!typeNames.isEmpty()) {
                    accountTypeSpinner.setText(typeNames.get(0), false);
                }
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_new_account_title)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.add_account, null)
                .setNegativeButton(R.string.cancel, (d, which) -> binding.accountNameAutoComplete.requestFocus())
                .create();

        dialog.setOnShowListener(d -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                String finalName = dialogBinding.dialogAccountNameEditText.getText().toString().trim();
                String phone = dialogBinding.dialogPhoneNumberEditText.getText().toString().trim();
                String type = accountTypeSpinner.getText().toString();

                if (TextUtils.isEmpty(finalName)) {
                    dialogBinding.dialogAccountNameEditText.setError(getString(R.string.error_account_name_empty));
                    return;
                }
                if (isAccountExists(finalName)) {
                    Toast.makeText(this, R.string.error_account_exists, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (TextUtils.isEmpty(type)) {
                    dialogBinding.accountTypeSpinnerLayout.setError(getString(R.string.required));
                    return;
                }

                String uid = isGuest ? guestUID : (FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "");
                String typeId = findFirestoreIdByAccountTypeName(type);

                Account newAccount = new Account();
                newAccount.setOwnerUID(uid);
                newAccount.setAccountName(finalName);
                newAccount.setPhoneNumber(phone);
                newAccount.setAccountType(type);
                if (typeId != null) newAccount.setAcTypeFirestoreId(typeId);

                viewModel.createAccount(newAccount);
                binding.accountNameAutoComplete.setText(finalName);

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Integer newAccountId = accountNameToIdMap.get(finalName);
                    if (newAccountId != null) {
                        viewModel.setSelectedAccount(newAccountId);
                    }
                    if (focusAmountAfter) {
                        binding.amountEditText.requestFocus();
                        showKeyboard(binding.amountEditText);
                    }
                }, 200);

                dialog.dismiss();
            });
        });

        dialog.show();
    }

    // --- بقية الدوال المساعدة (تبقى كما هي) ---
    private void showKeyboard(View view) {
        if (view.requestFocus()) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    private void showDatePicker() {
        DatePickerDialog.OnDateSetListener dateSetListener = (view, year, month, dayOfMonth) -> {
            selectedDate.set(Calendar.YEAR, year);
            selectedDate.set(Calendar.MONTH, month);
            selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateInView();
            binding.detailsEditText.requestFocus();
        };
        new DatePickerDialog(this, dateSetListener, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateInView() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        binding.dateEditText.setText(sdf.format(selectedDate.getTime()));
    }

    @Override
    public void onCalculationComplete(String result) {
        if (result != null && !result.isEmpty()) {
            try {
                double parsed = Double.parseDouble(result.replaceAll("[,]", ""));
                String formattedResult = FormatingAmount.formatForDisplay(parsed);
                binding.amountEditText.setText(formattedResult);
                binding.amountEditText.setSelection(formattedResult.length());
            } catch (NumberFormatException e) {
                binding.amountEditText.setText(result);
            }
            binding.detailsEditText.requestFocus();
        }
    }

    private String findFirestoreIdByAccountTypeName(String name) {
        if (availableAccountTypes == null || name == null || name.isEmpty()) return null;
        for (AccountType type : availableAccountTypes) {
            if (name.equals(type.getName())) {
                return type.getFirestoreId();
            }
        }
        return null;
    }

    // ... (بقية الدوال مثل onTransactionSaved, showDuplicateWarningDialog, إلخ)
    private void onTransactionSaved(double finalAmount) {
        viewModel.getSelectedTransactionItems().observe(this, new Observer<List<TransactionItem>>() {
            @Override
            public void onChanged(List<TransactionItem> transactionItems) {
                if (transactionItems != null && !transactionItems.isEmpty()) {
                    double finalBalance = transactionItems.get(0).getBalanceAfter();
                    showSaveConfirmationBanner(lastSavedTransaction, finalBalance + finalAmount);
                    viewModel.getSelectedTransactionItems().removeObserver(this);
                }
            }
        });
    }
    private void showIfAccountNull(Runnable onConfirm) {
        String accountNames = binding.accountNameAutoComplete.getText().toString().trim();
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.caution_titel))
                .setMessage(getString(R.string.info_null_Account,accountNames))
                .setPositiveButton(getString(R.string.yes), (dialog, which) -> onConfirm.run())
                .setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
                    binding.accountNameAutoComplete.setText("");
                    binding.amountEditText.setText("");
                    binding.detailsEditText.setText("");
                    binding.accountNameAutoComplete.requestFocus();
                })
                .setIcon(R.drawable.ic_alert)
                .show();
    }
    private void showDuplicateWarningDialog(Runnable onConfirm) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.caution_titel))
                .setMessage(getString(R.string.info_same_trans))
                .setPositiveButton(getString(R.string.yes_save), (dialog, which) -> onConfirm.run())
                .setNegativeButton(getString(R.string.cancel), null)
                .setIcon(R.drawable.ic_alert)
                .show();
    }
    private Date getStartOfDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
    private void showSaveConfirmationBanner(Transaction transaction, double finalBalance) {
        View bannerView = binding.saveConfirmationBanner.getRoot();
        Button shareButton = bannerView.findViewById(R.id.button_share);

        shareButton.setOnClickListener(v -> shareTransaction(transaction, finalBalance));

        bannerView.setVisibility(View.VISIBLE);
        bannerView.setAlpha(0f);
        bannerView.setTranslationY(200f);

        bannerView.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(600)
                .start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            bannerView.animate()
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction(() -> bannerView.setVisibility(View.GONE))
                    .start();
        }, 5000); // إخفاء البانر بعد 5 ثواني
    }
    private void shareTransaction(Transaction transaction, double finalBalance) {
        viewModel.getSelectedAccountDetails().observe(this, new Observer<Account>() {
            @Override
            public void onChanged(Account account) {
                if (account == null) return;
                viewModel.getSelectedAccountDetails().removeObserver(this);

                String phoneNumber = account.getPhoneNumber();
                if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                    showEditAccountDialogForSharing(account, transaction, finalBalance);
                } else {
                    String message = generateShareMessage(transaction, finalBalance);
                    showShareOptionsDialog(phoneNumber, message);
                }
            }
        });
    }
    private void showEditAccountDialogForSharing(Account accountToEdit, Transaction transaction, double finalBalance) {
        DialogEditAccountBinding dialogBinding = DialogEditAccountBinding.inflate(getLayoutInflater());
        dialogBinding.editAccountName.setText(accountToEdit.getAccountName());
        dialogBinding.editAccountPhone.setText(accountToEdit.getPhoneNumber());
        dialogBinding.editAccountName.setVisibility(View.GONE); // إخفاء نوع الحساب
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.edit_1))
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (d, which) -> {
                    String newName = dialogBinding.editAccountName.getText().toString().trim();
                    String newPhone = dialogBinding.editAccountPhone.getText().toString().trim();
                    if (!newPhone.isEmpty()) {
                        accountToEdit.setAccountName(newName);
                        accountToEdit.setPhoneNumber(newPhone);
                        viewModel.updateAccount(accountToEdit); // تحديث الحساب
                        String message = generateShareMessage(transaction, finalBalance);
                        openSmsIntent(newPhone, message);
                    }
                })
                .show();
    }
    private String generateShareMessage(Transaction transaction, double finalBalance) {
        String genMessage = "";
        String transactionType = transaction.getType() == 1 ? getString(R.string.on_you) : getString(R.string.for_you);
        String balanceType;//= finalBalance < 0 ? " لكم " : " عليكم ";
        if (finalBalance < 0) {
            balanceType = getString(R.string.report_balance_type_credit);
        } else if (finalBalance > 0) {
            balanceType = getString(R.string.report_balance_type_debit);
        } else {
            return getString(R.string.zero_balance, binding.accountNameAutoComplete.getText().toString(), binding.currencySpinnerAutocomplete.getText().toString());
//            return "مرحباً " + currentAccount.getAccountName() + "، ليس هناك رصيد حالي بعملة (" + currency + ").";
        }
        // الرصيد المتبقي
        String formattedAmount = NumberFormat.getInstance(Locale.US).format(transaction.getAmount());
        String formattedBalance = NumberFormat.getInstance(Locale.US).format(Math.abs(finalBalance));
        String details = " " + transaction.getDetails() + " ";

        genMessage = transactionType + getString(R.string.amonts) + " " + formattedAmount + " " + details + "\n" +
                getString(R.string.fianal_remain) + " " + balanceType + " " + formattedBalance + " " + binding.currencySpinnerAutocomplete.getText().toString();

        return genMessage;
    }

    private void showShareOptionsDialog(String phoneNumber, String message) {
        DialogShareOptionsBinding dialogBinding = DialogShareOptionsBinding.inflate(getLayoutInflater());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .create();

        // الضغط على خيار الواتساب
        dialogBinding.optionWhatsapp.setOnClickListener(v -> {
            openWhatsApp(phoneNumber, message);
            dialog.dismiss();
        });

        // الضغط على خيار الرسائل
        dialogBinding.optionSms.setOnClickListener(v -> {
            openSmsIntent(phoneNumber, message);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void openSmsIntent(String phoneNumber, String message) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + phoneNumber));
        intent.putExtra("sms_body", message);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.cant_open_app), Toast.LENGTH_SHORT).show();
        }
    }

    private void openWhatsApp(String phoneNumber, String message) {
        try {
            String formattedNumber = phoneNumber.replaceAll("[\\s\\-()]", "");
            // يمكنك تعديل هذا الشرط ليتناسب مع رمز بلدك الدولي
            // if (!formattedNumber.startsWith("+")) {
            //     formattedNumber = "+967" + formattedNumber;
            // }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://api.whatsapp.com/send?phone=" + formattedNumber + "&text=" + Uri.encode(message )));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "تطبيق واتساب غير مثبت.", Toast.LENGTH_SHORT).show();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.whatsapp")));
            } catch (ActivityNotFoundException playStoreException) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.whatsapp" )));
            }
        }
    }

    private void showNoOperationsDialog(String titel, String textInfo) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_no_operations, null);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();
        TextView titelTextView = dialogView.findViewById(R.id.titel_textView);
        TextView noTransactionTextView = dialogView.findViewById(R.id.no_transaction_textView);
        MaterialButton purchaseBtn = dialogView.findViewById(R.id.btn_purchase);
        MaterialButton inviteBtn = dialogView.findViewById(R.id.btn_invite);
        MaterialButton cancelBtn = dialogView.findViewById(R.id.btn_cancel);
        titelTextView.setText(titel);
        noTransactionTextView.setText(textInfo);
        purchaseBtn.setOnClickListener(v -> {
            dialog.dismiss();
            handlePurchaseApp();
        });
        inviteBtn.setOnClickListener(v -> {
            dialog.dismiss();
            if(isGuest){
                Toast.makeText(this, getString(R.string.login_1), Toast.LENGTH_SHORT).show();
                return;
            }

            if (currentUser != null) {
                ReferralManager referralManager = new ReferralManager(this);
                referralManager.generateAndShareReferralLink(FirebaseAuth.getInstance().getCurrentUser());
            }
        });
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void handlePurchaseApp() {
        GoogleAuthHelper googleAuthHelper = new GoogleAuthHelper(this, new LicenseManager(this), repository);
        if (!googleAuthHelper.isSignedIn()) {
            Toast.makeText(this, getString(R.string.login_1), Toast.LENGTH_SHORT).show();
            return;
        }
        PurchaseCodeDialog.newInstance().show(getSupportFragmentManager(), "PurchaseCodeDialog");
    }
}
