package com.hpp.daftree.ui;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
//import android.widget.SearchView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.SearchView;

import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.hpp.daftree.dialogs.CalculatorDialog;
import com.hpp.daftree.dialogs.InvoiceDialog;
import com.hpp.daftree.dialogs.AddTransactionDialog;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.helpers.PreferenceHelper;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.models.FormatingAmount;
import com.hpp.daftree.models.ReportGenerator;
import com.hpp.daftree.dialogs.ReportsDialog;
import com.hpp.daftree.dialogs.TransferFundsDialog;
import com.hpp.daftree.syncmanagers.SyncPreferences;
import com.hpp.daftree.adapters.TransactionHistoryAdapter;
import com.hpp.daftree.TransactionItem;
import com.hpp.daftree.dialogs.TutorialDialog;
import com.hpp.daftree.ViewModelFactory;
import com.hpp.daftree.databinding.ActivityAccountDetailsBinding;
import com.hpp.daftree.databinding.DialogAddNewTransactionBinding;
import com.hpp.daftree.databinding.DialogEditAccountBinding;
import com.hpp.daftree.databinding.DialogShareOptionsBinding;
import com.hpp.daftree.dialogs.PurchaseCodeDialog;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.database.User;
//import com.hpp.daftree.transactionsync.LicenseSyncManager;
import com.hpp.daftree.utils.EdgeToEdgeUtils;
import com.hpp.daftree.utils.GoogleAuthHelper;
import com.hpp.daftree.utils.LicenseManager;
import com.hpp.daftree.utils.ReferralManager;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.io.File;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import android.os.Handler;

import java.util.stream.Collectors;

public class AccountDetailsActivity extends BaseActivity implements
        TransactionHistoryAdapter.OnItemInteractionListener, CalculatorDialog.OnCalculationCompleteListener {

    private static final String TAG = "AccountDetailsActivity";
    private ActivityAccountDetailsBinding binding;
    private AccountDetailsViewModel viewModel;
    private AddTransactionViewModel addTransactionViewModel;
    private TransactionHistoryAdapter historyAdapter;
    private User currentUser;
    private int accountId;
    private List<Account> accountsList;
    private String accountName;
    //    private User currentUserData;
    private LicenseManager licenseManager;
    //  private LicenseSyncManager licenseSyncManager;
//    private final String[] currencies = {"محلي", "سعودي", "دولار"};
    private CurrencyViewModel currencyViewModel; // ViewModel جديد
    private final List<Currency> allAvailableCurrencies = new ArrayList<>(); // كل العملات المتاحة
    private final List<String> activeCurrenciesForAccount = new ArrayList<>(); // العملات النشطة فقط لهذا الحساب

    private int currentCurrencyIndex = 0;
    private Transaction lastSavedTransaction;
    private boolean hasUsdTransactions = false;
    private boolean hasSarTransactions = false;
    private ViewModelFactory factory;
    private Account currentAccount;
    private static final int PDF_PERMISSION_CODE = 102;
    private DaftreeRepository repository;
    private ActivityResultLauncher<Intent> pdfSaveLauncher;
    private EditText dialogAmountEditText; // متغير لحفظ مرجع حقل المبلغ في الديالوج
    private SyncPreferences syncPreferences;

    boolean isGuest = false;
    private String guestUID = "";


    @Override
    public void onCalculationComplete(String result) {
        if (dialogAmountEditText != null) {
            dialogAmountEditText.setText(result);
        }
        if (result != null && !result.isEmpty()) {
            try {
                if (dialogAmountEditText == null) return;
                String cleanString = result.replaceAll("[,]", "");
                // تنسيق الناتج مع فواصل الآلاف
                double parsed = Double.parseDouble(cleanString);
                //DecimalFormat formatter = new DecimalFormat("#,###");
                NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);
                String formattedResult = FormatingAmount.formatForDisplay(parsed);
                dialogAmountEditText.setText(formattedResult);
                dialogAmountEditText.setSelection(formattedResult.length()); // نقل المؤشر للنهاية
            } catch (NumberFormatException e) {
                dialogAmountEditText.setText(result); // عرض الناتج كما هو إذا فشل التنسيق
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeUtils.applyEdgeToEdge(this, binding.toolbar);
        MyApplication.applyGlobalTextWatcher(binding.getRoot());
        shadowMoving();
        licenseManager = new LicenseManager(this);
        syncPreferences = new SyncPreferences(this);
        repository = new DaftreeRepository(getApplication());
        String mainMessage = getString(R.string.account_detail_activity_help);
        // TutorialDialog.show(this, "AccountDetailsActivity", mainMessage);
        isGuest = SecureLicenseManager.getInstance(this).isGuest();
        if (isGuest) {
            guestUID = SecureLicenseManager.getInstance(this).guestUID();
        }
        accountId = getIntent().getIntExtra("ACCOUNT_ID", -1);
        if (accountId == -1) {
            finish();
            return;
        }
        accountName = getIntent().getStringExtra("ACCOUNT_NAME");
        setupToolbar();
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        // تعديل 1: تمرير context و listener للـ Adapter
        historyAdapter = new TransactionHistoryAdapter(this, new TransactionHistoryAdapter.OnItemInteractionListener() {
            @Override
            public void onItemLongClick(TransactionItem transactionItem) {
                // الكود الحالي
                Transaction tx = transactionItem.getTransaction();
                String currencyName = findCurrencyNameById(tx.getCurrencyId());
                if (currencyName == null) return;

                if (tx.getBillType() != null && !tx.getBillType().isEmpty()) {
                    InvoiceDialog dialog = InvoiceDialog.newInstanceForEdit(
                            tx.getImportID(),
                            currencyName,
                            "true"
                    );
                    dialog.show(getSupportFragmentManager(), "InvoiceDialog");
                } else {
                    showAddTransactionDialog(tx);
                }
            }

            @Override
            public void onEditIconClick(TransactionItem transactionItem) {
                // استدعاء نفس الدالة المستخدمة في onItemLongClick
                onItemLongClick(transactionItem);
            }
        }, true); // ⭐⭐ تمرير true لعرض أيقونة التعديل ⭐⭐

        binding.transactionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.transactionsRecyclerView.setAdapter(historyAdapter);


        factory = new ViewModelFactory(getApplication(), accountId);
        viewModel = new ViewModelProvider(this, factory).get(AccountDetailsViewModel.class);
        currencyViewModel = new ViewModelProvider(this).get(CurrencyViewModel.class); // تهيئة
        addTransactionViewModel = new ViewModelProvider(this).get(AddTransactionViewModel.class);
        viewModel.getTransactionItems().observe(this, transactionItems -> {
            updateSummariesFromItems(transactionItems);
            historyAdapter.submitList(transactionItems, () -> {
                if (transactionItems != null && !transactionItems.isEmpty()) {
                    binding.transactionsRecyclerView.smoothScrollToPosition(0);
                    historyAdapter.notifyDataSetChanged();
                }
            });
        });
        String initialCurrency = getIntent().getStringExtra("CURRENCY");
        if (initialCurrency == null) {
            initialCurrency = MyApplication.defaultCurrencyName;//= String.valueOf(R.string.currency_local); // قيمة افتراضية في حالة عدم وجودها
            // R.string.currency_local
        }
        viewModel.getAccountDetails().observe(this, account -> {
            this.currentAccount = account; // تحديث المتغير المحلي دائمًا
            if (account != null) {
                // تحديث اسم الحساب في الشريط العلوي في حال تم تعديله
                binding.toolbar.setTitle(account.getAccountName());
            }
        });
        binding.currencyTextView.setText(initialCurrency);

        // 3. إخبار الـ ViewModel بالبدء في تحميل بيانات هذه العملة فورًا
        viewModel.setCurrency(initialCurrency);

        // ... الكود الخاص بمراقبة accountDetails وإعدادات الواجهة الأخرى ...
        setupCurrencyObservers();
        setupObservers();
        updateUiForSelectedCurrency();
        setupBottomBar();
        pdfSaveLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    writePdfToUri(uri); // عند اختيار مكان الحفظ، نبدأ عملية الكتابة
                }
            }
        });


    }

    private void shadowMoving() {
        ValueAnimator elevationAnimator = ValueAnimator.ofFloat(0f, 8f);
        elevationAnimator.setInterpolator(new LinearOutSlowInInterpolator());
        elevationAnimator.setDuration(250);

        // استماع لحركة التمرير في RecyclerView
        binding.transactionsRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            float previousScrollY = 0f;
            boolean elevated = false;

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                // مجموع التمرير الرأسي
                float scrollY = recyclerView.computeVerticalScrollOffset();

                // إذا تم التمرير للأسفل — أضف الظل
                if (scrollY > 8 && !elevated) {
                    elevated = true;
                    animateElevation(binding.appBarLayout, 8f);
                    animateElevation(binding.recyclerHeader, 6f);
                }
                // إذا عدنا للأعلى — أزل الظل
                else if (scrollY <= 8 && elevated) {
                    elevated = false;
                    animateElevation(binding.appBarLayout, 0f);
                    animateElevation(binding.recyclerHeader, 0f);
                }

                previousScrollY = scrollY;
            }
        });
    }

    // دالة مساعدة لإضافة تأثير الظل التدريجي بسلاسة
    private void animateElevation(View view, float targetElevation) {
        float start = view.getElevation();
        ValueAnimator animator = ValueAnimator.ofFloat(start, targetElevation);
        animator.setDuration(220);
        animator.setInterpolator(new LinearOutSlowInInterpolator());
        animator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            ViewCompat.setElevation(view, value);
        });
        animator.start();
    }

    private void setupObservers() {
        // هذا المراقب سيستجيب للتغيير الذي قمنا به في onCreate ويعرض البيانات
        viewModel.getTransactionItems().observe(this, transactionItems -> {
            updateSummariesFromItems(transactionItems);
            historyAdapter.submitList(transactionItems, () -> {
                if (transactionItems != null && !transactionItems.isEmpty()) {
                    binding.transactionsRecyclerView.smoothScrollToPosition(0);
                }
            });
        });

        viewModel.getAccountDetails().observe(this, account -> {
            this.currentAccount = account;
            if (account != null) {
                binding.toolbar.setTitle(account.getAccountName());
            }
        });

        viewModel.getUserProfile().observe(this, user -> this.currentUser = user);

        // مراقبون لتحديث حالة وجود عمليات للعملات الأخرى
        viewModel.getDebitUsd().observe(this, total -> checkUsdTransactions());
        viewModel.getCreditUsd().observe(this, total -> checkUsdTransactions());
        viewModel.getDebitSar().observe(this, total -> checkSarTransactions());
        viewModel.getCreditSar().observe(this, total -> checkSarTransactions());
    }

    private void setupTotalObservers1() {
        viewModel.getDebitUsd().observe(this, total -> checkUsdTransactions());
        viewModel.getCreditUsd().observe(this, total -> checkUsdTransactions());
        viewModel.getDebitSar().observe(this, total -> checkSarTransactions());
        viewModel.getCreditSar().observe(this, total -> checkSarTransactions());
    }

    private void setupCurrencyObservers() {
        // 1. جلب كل العملات المتاحة في التطبيق لاستخدامها في الـ Spinner
        currencyViewModel.getAllCurrencies().observe(this, currencies -> {
            if (currencies != null) {
                allAvailableCurrencies.clear();
                allAvailableCurrencies.addAll(currencies);
            }
        });

        // 2. جلب العملات النشطة (التي لها حركة) لهذا الحساب فقط
        viewModel.getActiveCurrencies().observe(this, activeNames -> {
            if (activeNames != null) {
                activeCurrenciesForAccount.clear();
                // تأكد دائمًا من وجود العملة الحالية في القائمة النشطة
                String currentDisplayedCurrency = binding.currencyTextView.getText().toString();
                if (!activeNames.contains(currentDisplayedCurrency)) {
                    activeCurrenciesForAccount.add(currentDisplayedCurrency);
                }
                activeCurrenciesForAccount.addAll(activeNames);
            }
        });
    }

    // **تعديل كامل لمنطق التنقل بين العملات**
    private void cycleCurrency() {
        if (activeCurrenciesForAccount.isEmpty()) {
            Toast.makeText(this, "لا توجد حركات لعملات أخرى في هذا الحساب", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentCurrency = binding.currencyTextView.getText().toString();
        int currentIndex = activeCurrenciesForAccount.indexOf(currentCurrency);

        // الانتقال إلى العملة التالية في القائمة النشطة
        int nextIndex = (currentIndex + 1) % activeCurrenciesForAccount.size();
        String nextCurrency = activeCurrenciesForAccount.get(nextIndex);

        // تحديث الواجهة
        binding.currencyTextView.setText(nextCurrency);
        viewModel.setCurrency(nextCurrency);
    }

    private void checkUsdTransactions() {
        Double debit = viewModel.getDebitUsd().getValue();
        Double credit = viewModel.getCreditUsd().getValue();
        hasUsdTransactions = (debit != null && debit > 0) || (credit != null && credit > 0);
    }

    private void checkSarTransactions() {
        Double debit = viewModel.getDebitSar().getValue();
        Double credit = viewModel.getCreditSar().getValue();
        hasSarTransactions = (debit != null && debit > 0) || (credit != null && credit > 0);
    }

    private void cycleCurrency_last() {
        if (!hasUsdTransactions && !hasSarTransactions) {
            currentCurrencyIndex = 0; // "محلي"
            updateUiForSelectedCurrency();
            return;
        }

//        for (int i = 0; i < currencies.length; i++) {
//            currentCurrencyIndex = (currentCurrencyIndex + 1) % currencies.length;
//            String nextCurrency = currencies[currentCurrencyIndex];
//
//            if (nextCurrency.equals("محلي")) break;
//            if (nextCurrency.equals("دولار") && hasUsdTransactions) break;
//            if (nextCurrency.equals("سعودي") && hasSarTransactions) break;
//        }
        updateUiForSelectedCurrency();
    }

    private void updateSummariesFromItems(List<TransactionItem> items) {
        if (items == null || items.isEmpty()) {
            binding.totalDebitTextView.setText("0");
            binding.totalCreditTextView.setText("0");
            binding.totalBalanceTextView.setText("0");
            return;
        }

        double totalDebit = 0.0, totalCredit = 0.0;
        for (TransactionItem item : items) {
            Transaction tx = item.getTransaction();
            if (tx.getType() == 1) totalDebit += tx.getAmount();
            else totalCredit += tx.getAmount();
        }

        double finalBalance = items.get(0).getBalanceAfter(); // أول عنصر هو الأحدث

        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        binding.totalDebitTextView.setText(nf.format(totalDebit));
        binding.totalCreditTextView.setText(nf.format(totalCredit));
        binding.totalBalanceTextView.setText(nf.format(finalBalance));
    }

    // تعديل 3: الدالة تتعامل الآن مع TransactionItem
    @Override
    public void onItemLongClick(TransactionItem transactionItem) {
        // عند الضغط المطول، نمرر المعاملة لوضع التعديل
//        showAddTransactionDialog(transactionItem.getTransaction());
        Transaction tx = transactionItem.getTransaction();
        String currencyName = findCurrencyNameById(tx.getCurrencyId());
        if (currencyName == null) return;
        // **المنطق الجديد: التحقق من نوع العملية**
        if (tx.getBillType() != null && !tx.getBillType().isEmpty()) {
            InvoiceDialog dialog = InvoiceDialog.newInstanceForEdit(
                    tx.getImportID(),
                    currencyName,
                    "true"
            );
            dialog.show(getSupportFragmentManager(), "InvoiceDialog");
        } else {
            // إذا كانت عملية عادية، اعرض الديالوج القديم
            showAddTransactionDialog(tx);
        }
    }

    @Override
    public void onEditIconClick(TransactionItem transactionItem) {
        onItemLongClick(transactionItem);
    }

    // جديد: دالة مخصصة لإعداد الإكمال التلقائي في الديالوج
    // TODO: هذه الدالة لم تعد مستخدمة مع AddTransactionDialog الجديد
    // private void setupDetailsAutoCompleteInDialog(DialogAddNewTransactionBinding dialogBinding) {
    //     // تم حذف الكود - الديالوج الجديد يتعامل مع هذا داخلياً
    // }

    private void setupToolbar() {
        binding.toolbar.setTitle(accountName);
//        binding.toolbar.setNavigationOnClickListener(v -> finish());
        // يمكن إضافة منطق البحث هنا للفلترة على القائمة المحلية
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    }

    private void setupBottomBar() {
        binding.currencyTextView.setOnClickListener(v -> cycleCurrency());
        binding.addTransactionButton.setOnClickListener(v -> {
            showAddTransactionDialog(null);
        });
        binding.smsButton.setOnClickListener(v -> shareTransaction());
//        binding.transferFundsButton.setOnClickListener(v -> showTransferFundsDialog());
    }

    private void showEditAccountDialogForSharing(Account accountToEdit) {
        DialogEditAccountBinding dialogBinding = DialogEditAccountBinding.inflate(getLayoutInflater());
        dialogBinding.editAccountName.setText(accountToEdit.getAccountName());
        dialogBinding.editAccountPhone.setText(accountToEdit.getPhoneNumber());
        dialogBinding.editAccountName.setVisibility(View.GONE); // إخفاء نوع الحساب

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.edit_1))
                .setView(dialogBinding.getRoot())
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.ok), (d, which) -> {
                    String newName = dialogBinding.editAccountName.getText().toString().trim();
                    String newPhone = dialogBinding.editAccountPhone.getText().toString().trim();
                    if (!newPhone.isEmpty()) {
                        accountToEdit.setAccountName(newName);
                        accountToEdit.setPhoneNumber(newPhone);
                        viewModel.updateAccount(accountToEdit); // تحديث الحساب
                        String stateBalance = "";

//                        String currency = currencies[currentCurrencyIndex];
                        String amountStr = binding.totalBalanceTextView.getText().toString().trim().replaceAll(",", "");
                        double finalBalance1 = Double.parseDouble(amountStr);
                        if (finalBalance1 < 0) {
                            finalBalance1 = Math.abs(finalBalance1);
                            stateBalance = " لكم ";
                        } else if (finalBalance1 > 0) {
                            stateBalance = " عليكم ";
                        } else {
                            stateBalance = "";
                        }
                        String formattedBalance = NumberFormat.getInstance(Locale.US).format(Math.abs(finalBalance1));
//                        String message = "مرحباً " + accountName + "، رصيدكم الحالي لدينا بعملة (" + currency + ")  " + " هو " + stateBalance + " : " +  formattedBalance;
                        String message = generateShareMessage();
                        openSmsIntent(newPhone, message);
//                        Toast.makeText(this, "تم حفظ التعديلات", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    // جديد: دالة لفتح تطبيق الرسائل مباشرة
    private void openSmsIntent(String phoneNumber, String message) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + phoneNumber));
        intent.putExtra("sms_body", message);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "لا يمكن فتح تطبيق الرسائل", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareTransaction() {
        // الآن، نستخدم المتغير المحلي مباشرة دون الحاجة لمراقب جديد
        if (currentAccount == null) {
            Toast.makeText(this, "جاري تحميل بيانات الحساب، يرجى المحاولة بعد لحظات", Toast.LENGTH_SHORT).show();
            // return;
        }

        String phoneNumber = currentAccount.getPhoneNumber();
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            showEditAccountDialogForSharing(currentAccount);
        } else {
            String message = generateShareMessage();
            openSmsIntent(phoneNumber, message);
        }
    }

    // تعديل: الدالة الآن لا تحتاج لبارامترات
    private String generateShareMessage() {
        String stateBalance = "";
//        String currency = currencies[currentCurrencyIndex];
        String currency = binding.currencyTextView.getText().toString();
        String amountStr = binding.totalBalanceTextView.getText().toString().replaceAll("[,]", "");
        double finalBalance = Double.parseDouble(amountStr);

        if (finalBalance < 0) {
            stateBalance = getString(R.string.report_balance_type_credit);
        } else if (finalBalance > 0) {
            stateBalance = getString(R.string.report_balance_type_debit);
        } else {
            return getString(R.string.zero_balance, currentAccount.getAccountName(), currency);
//            return "مرحباً " + currentAccount.getAccountName() + "، ليس هناك رصيد حالي بعملة (" + currency + ").";
        }

        String formattedBalance = NumberFormat.getInstance(Locale.US).format(Math.abs(finalBalance));
        return getString(R.string.final_sms_balance, currentAccount.getAccountName(), currency, stateBalance, formattedBalance);
        //  return "مرحباً " + currentAccount.getAccountName() + "، رصيدكم الحالي لدينا بعملة (" + currency + ") هو" + stateBalance + ": " + formattedBalance;
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void showAddTransactionDialog(@Nullable Transaction transactionToEdit) {
        // إنشاء الديالوج الجديد
        AddTransactionDialog dialog;

        if (transactionToEdit != null) {
            // وضع التعديل
            dialog = AddTransactionDialog.newInstanceForEdit(transactionToEdit);
        } else {
            // وضع الإضافة
            String initialCurrency = binding.currencyTextView.getText().toString();
            dialog = AddTransactionDialog.newInstanceWithCurrency(accountId, accountName, initialCurrency);
        }

        // تمرير البيانات المطلوبة للديالوج
        dialog.setAvailableCurrencies(allAvailableCurrencies);
        dialog.setCurrentAccount(currentAccount); // تمرير Account object

        // ضبط المستمع لحفظ المعاملة
        dialog.setOnTransactionSaveListener(new AddTransactionDialog.OnTransactionSaveListener() {
            @Override
            public void onTransactionSaved(Transaction transaction,String newCurrencyName, double finalAmount) {
                binding.currencyTextView.setText(newCurrencyName);
                // تحديث المجموع حسب العملة الجديدة
                viewModel.setCurrency(newCurrencyName);

            }

            @Override
            public void onCurrencyChanged(String newCurrencyName) {
                // تحديث اسم العملة في الواجهة الرئيسية
                if (newCurrencyName != null) {
                    binding.currencyTextView.setText(newCurrencyName);
                    // تحديث المجموع حسب العملة الجديدة
                    viewModel.setCurrency(newCurrencyName);
                }
            }
        });

        // عرض الديالوج
        dialog.show(getSupportFragmentManager(), "AddTransactionDialog");
    }
    private void showTransferFundsDialog() {
        // إنشاء الديالوج الجديد للتحويل
        String initialCurrency = binding.currencyTextView.getText().toString();
        TransferFundsDialog dialog = TransferFundsDialog.newInstance(accountId, accountName, initialCurrency);

        // تمرير البيانات المطلوبة للديالوج
        dialog.setAvailableCurrencies(allAvailableCurrencies);
//        dialog.setAvailableAccounts(allAccounts); // تمرير جميع الحسابات
        dialog.setCurrentAccount(currentAccount); // تمرير Account object

        // ضبط المستمع للتحويل
        dialog.setOnTransferCompleteListener(new TransferFundsDialog.OnTransferCompleteListener() {
            @Override
            public void onTransferComplete() {
                // إعادة تحميل البيانات لتحديث الواجهة
//                viewModel.refreshTransactions();
//                updateTotalForCurrency();
            }

            @Override
            public void onCurrencyChanged(String newCurrencyName) {
                // تحديث اسم العملة في الواجهة الرئيسية
                if (newCurrencyName != null) {
//                    binding.currencyTextView.setText(newCurrencyName);
//                    // تحديث المجموع حسب العملة الجديدة
//                    updateTotalForCurrency();
                }
            }
        });

        // عرض الديالوج
        dialog.show(getSupportFragmentManager(), "TransferFundsDialog");
    }

    private String findCurrencyNameById(int currencyId) {
        for (Currency currency : allAvailableCurrencies) {
            if (currency.getId() == currencyId) {
                return currency.getName();
            }
        }
        return null; // في حالة عدم العثور عليها
    }

    // TODO: هذه الدالة لم تعد مستخدمة مع AddTransactionDialog الجديد
    // private void setupDialogUI(DialogAddNewTransactionBinding dialogBinding, @Nullable Transaction transaction, boolean isEditMode) {
    //     // تم حذف الكود - الديالوج الجديد يتعامل مع هذا داخلياً
    // }

    // TODO: هذه الدالة لم تعد مستخدمة مع AddTransactionDialog الجديد
    // private void saveChanges(AlertDialog dialog, DialogAddNewTransactionBinding dialogBinding,
    //                          @Nullable Transaction oldTransaction, int newType, Calendar newDate) {
    //     // تم حذف الكود - الديالوج الجديد يتعامل مع حفظ البيانات داخلياً
    // }

    //    private void showSyncWarningDialog(int pendingCount) {
//        new AlertDialog.Builder(this)
//                .setTitle("تنبيه")
//                .setMessage("لديك " + pendingCount + " عملية تنتظر المزامنة. " +
//                        "يجب مزامنة هذه العمليات أولاً قبل إنشاء عمليات جديدة.")
//                .setPositiveButton("مزامنة الآن", (dialog, which) -> {
//                    // بدء عملية المزامنة
//                    LicenseSyncManager syncManager = new LicenseSyncManager(this);
//                    syncManager.smartSync();
//                })
//                .setNegativeButton("لاحقاً", null)
//                .show();
//    }
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    /**
     * ديالوج جديد ومميز لإعلام المستخدم بانتهاء الحصة اليومية.
     */
    private void showDailyLimitExceededDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("تم استيفاء الحصة اليومية")
                .setMessage("لقد استهلكت حصتك المجانية من العمليات لهذا اليوم. للحصول على عدد غير محدود من العمليات، يرجى ترقية حسابك.")
                .setPositiveButton("شراء التطبيق الآن", (dialog, which) -> {
                    // إظهار ديالوج شراء التطبيق
                    PurchaseCodeDialog.newInstance().show(getSupportFragmentManager(), "PurchaseCodeDialog");
                })
                .setNegativeButton("لاحقاً", null)
                .setIcon(R.drawable.ic_alert) // تأكد من وجود أيقونة مناسبة
                .show();
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
            ReferralManager referralManager = new ReferralManager(this);
            referralManager.generateAndShareReferralLink(FirebaseAuth.getInstance().getCurrentUser());
        });
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void simulateWatchAd() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("جاري عرض الإعلان...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            progressDialog.dismiss();
            licenseManager.addAdRewardTransactions(currentUser); // <-- استدعاء الدالة الجديدة
        }, 3000); // محاكاة 3 ثواني
    }

    private void handlePurchaseApp() {

        GoogleAuthHelper googleAuthHelper = new GoogleAuthHelper(this, new LicenseManager(this), repository);
        if (!googleAuthHelper.isSignedIn()) {
            Toast.makeText(this, getString(R.string.login_1), Toast.LENGTH_SHORT).show();
            return;
        }
        PurchaseCodeDialog.newInstance().show(getSupportFragmentManager(), "PurchaseCodeDialog");
    }

    private void onTransactionSaved(double finalAmount) {
//        resetFieldsForNewEntry(); // إفراغ الحقول

        // نراقب القائمة للحصول على الرصيد النهائي المحدث
        viewModel.getSelectedTransactionItems().observe(this, new Observer<List<TransactionItem>>() {
            @Override
            public void onChanged(List<TransactionItem> transactionItems) {
                if (transactionItems != null && !transactionItems.isEmpty()) {
                    // أول عنصر في القائمة (المرتبة تنازليًا) هو الأحدث ويحمل الرصيد النهائي
                    double finalBalance = transactionItems.get(0).getBalanceAfter();

                    showSaveConfirmationBanner(lastSavedTransaction, finalBalance + finalAmount);
                    // نزيل المراقب فورًا لمنع استدعائه مرة أخرى بشكل غير مقصود
                    viewModel.getSelectedTransactionItems().removeObserver(this);
                }
            }
        });
    }

    // جديد: دالة لعرض بانر التأكيد مع حركة
    private void showSaveConfirmationBanner(Transaction transaction, double finalBalance) {
        View bannerView = binding.saveConfirmationBanner.getRoot();
        Button shareButton = bannerView.findViewById(R.id.button_share);

        shareButton.setOnClickListener(v -> shareTransactionNew(transaction, finalBalance));

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

    private void openSmsIntentNew(String phoneNumber, String message) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + phoneNumber));
        intent.putExtra("sms_body", message);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "لا يمكن فتح تطبيق الرسائل", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareTransactionNew(Transaction transaction, double finalBalance) {
        viewModel.getAccountDetails().observe(this, new Observer<Account>() {
            @Override
            public void onChanged(Account account) {
                if (account == null) return;
                viewModel.getAccountDetails().removeObserver(this);

                String phoneNumber = account.getPhoneNumber();
                if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                    showEditAccountDialogForSharingNew(account, transaction, finalBalance);
                } else {
                    String message = generateShareMessageNew(transaction, finalBalance);
                    showShareOptionsDialog(phoneNumber, message);
                }
            }
        });
    }

    private void showEditAccountDialogForSharingNew(Account accountToEdit, Transaction transaction, double finalBalance) {
        DialogEditAccountBinding dialogBinding = DialogEditAccountBinding.inflate(getLayoutInflater());
        dialogBinding.editAccountName.setText(accountToEdit.getAccountName());
        dialogBinding.editAccountPhone.setText(accountToEdit.getPhoneNumber());
        dialogBinding.editAccountName.setVisibility(View.GONE); // إخفاء نوع الحساب

        new MaterialAlertDialogBuilder(this)
                .setTitle("تعديل حساب")
                .setView(dialogBinding.getRoot())
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", (d, which) -> {
                    String newName = dialogBinding.editAccountName.getText().toString().trim();
                    String newPhone = dialogBinding.editAccountPhone.getText().toString().trim();
                    if (!newPhone.isEmpty()) {
                        accountToEdit.setAccountName(newName);
                        accountToEdit.setPhoneNumber(newPhone);
                        viewModel.updateAccount(accountToEdit); // تحديث الحساب
                        String message = generateShareMessageNew(transaction, finalBalance);
                        openSmsIntent(newPhone, message);
//                        Toast.makeText(this, "تم حفظ التعديلات", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private String generateShareMessageNew(Transaction transaction, double finalBalance) {
        String genMessage = "";
        String currencyName = findCurrencyNameById(transaction.getCurrencyId());

        String transactionType = transaction.getType() == 1 ? " عليك " : " لك ";
        String balanceType = finalBalance < 0 ? " لكم " : " عليكم ";
        String formattedAmount = NumberFormat.getInstance(Locale.US).format(transaction.getAmount());
        String formattedBalance = NumberFormat.getInstance(Locale.US).format(Math.abs(finalBalance));
        String details = " " + transaction.getDetails() + " ";
        genMessage = transactionType + "مبلغ وقدره " + formattedAmount + currencyName + details + "\n" +
                "الرصيد المتبقي" + balanceType + formattedBalance + currencyName;
        return genMessage;
//        return String.format(Locale.US,
//                "%s مبلغ وقدره %s %s.\n الرصيد المتبقي %s %s %s.",
//                transactionType, formattedAmount, transaction.getCurrency(), " "+ transaction.getDetails() + " ",
//                balanceType, formattedBalance, transaction.getCurrency()
//        );
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
            openSmsIntentNew(phoneNumber, message);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void openWhatsApp(String phoneNumber, String message) {
        try {
            // تنظيف الرقم من أي رموز وإضافة رمز الدولة الدولي (افترضي لليمن)
            String formattedNumber = phoneNumber.replaceAll("[\\s\\-()]", "");
            if (!formattedNumber.startsWith("967")) {
                // يمكنك تعديل هذا الشرط ليتناسب مع أرقام بلدك
                // formattedNumber = "967" + formattedNumber;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://api.whatsapp.com/send?phone=" + formattedNumber + "&text=" + Uri.encode(message)));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // إذا لم يكن واتساب مثبتًا، يتم توجيه المستخدم للمتجر
            Toast.makeText(this, "تطبيق واتساب غير مثبت. جاري توجيهك للمتجر...", Toast.LENGTH_SHORT).show();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.whatsapp")));
            } catch (ActivityNotFoundException playStoreException) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.whatsapp")));
            }
        }
    }

    // TODO: هذه الدالة لم تعد مستخدمة مع AddTransactionDialog الجديد
    // private void showDatePickerInDialog(DialogAddNewTransactionBinding dialogBinding, Calendar date) {
    //     // تم حذف الكود - الديالوج الجديد يتعامل مع التاريخ داخلياً
    // }

    // TODO: هذه الدالة لم تعد مستخدمة مع AddTransactionDialog الجديد
    // private void updateDateInDialogView(DialogAddNewTransactionBinding dialogBinding, Calendar date) {
    //     // تم حذف الكود - الديالوج الجديد يتعامل مع التاريخ داخلياً
    // }

    private void updateUiForSelectedCurrency() {
//        String selectedCurrency = currencies[currentCurrencyIndex];
        String selectedCurrency = binding.currencyTextView.getText().toString();
        binding.currencyTextView.setText(selectedCurrency);
        // إخبار الـ ViewModel بتغيير العملة
        viewModel.setCurrency(selectedCurrency);

    }

    private void cycleCurrency11() {
        // activeCurrenciesForAccount هي القائمة التي يتم تحديثها من الـ ViewModel
        // وتحتوي فقط على أسماء العملات التي لها حركة
        if (activeCurrenciesForAccount.isEmpty()) {
            Toast.makeText(this, "لا توجد حركات لعملات أخرى في هذا الحساب", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentCurrency = binding.currencyTextView.getText().toString();
        int currentIndex = activeCurrenciesForAccount.indexOf(currentCurrency);

        // الانتقال إلى العملة التالية في القائمة النشطة بشكل دائري
        int nextIndex = (currentIndex + 1) % activeCurrenciesForAccount.size();
        String nextCurrency = activeCurrenciesForAccount.get(nextIndex);

        // **تحديث الواجهة والـ ViewModel مباشرة**
        binding.currencyTextView.setText(nextCurrency);
        viewModel.setCurrency(nextCurrency); // هذا السطر هو البديل الأفضل لـ updateUiForSelectedCurrency
    }

    // TODO: هذه الدالة لم تعد مستخدمة مع AddTransactionDialog الجديد
    // private void switchToViewMode(DialogAddNewTransactionBinding dialogBinding) {
    //     // تم حذف الكود - الديالوج الجديد يتعامل مع هذه الحالات داخلياً
    // }

    // TODO: هذه الدالة لم تعد مستخدمة مع AddTransactionDialog الجديد
    // private void switchToEditMode(DialogAddNewTransactionBinding dialogBinding) {
    //     // تم حذف الكود - الديالوج الجديد يتعامل مع هذه الحالات داخلياً
    // }


    /**
     * جديد: دالة لعرض ديالوج تأكيد الحذف بشكل احترافي.
     */
    private void showDeleteConfirmationDialog(Transaction transactionToDelete) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_confirmation_title)
                .setMessage(R.string.delete_confirmation_message)
                .setIcon(R.drawable.ic_alert)
                // تغيير لون العنوان إلى الأحمر (يتطلب تعريف اللون في colors.xml)
                // .setTitleTextColor(ContextCompat.getColor(this, R.color.red_error))
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    // لا تفعل شيئًا عند الإلغاء
                })
                .setPositiveButton(R.string.yes_delete, (dialog, which) -> {
                    // عند التأكيد، اطلب من الـ ViewModel حذف العملية
                    viewModel.deleteTransaction(transactionToDelete);
                    //    Toast.makeText(this, "تم حذف العملية", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_export_pdf) {
            Log.w(TAG, "action_export_pdf");
            showReportsDialog();
            return true;
        }
        if (id == R.id.action_transfer_funds) {
            showTransferFundsDialog();
            return true;
        }
        if (id == R.id.nav_new_bill) {
            String currency = binding.currencyTextView.getText().toString();
            InvoiceDialog dialog = InvoiceDialog.newInstanceForAccount(currency, accountName);
            dialog.show(getSupportFragmentManager(), "InvoiceDialog");
            return true;
        }
        if (id == R.id.action_share_pdf) {
            Log.w(TAG, "action_share_pdf");
            generateAndSharePdf();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void generateAndSharePdf() {

        // جلب البيانات الحالية من الواجهة
        List<TransactionItem> currentList = historyAdapter.getCurrentList();
//        if (currentList.isEmpty()) {
//            Toast.makeText(this, "لا توجد عمليات لتصديرها", Toast.LENGTH_SHORT).show();
//            return;
//        }
//        String currency = currencies[currentCurrencyIndex];
        String totalDebit = binding.totalDebitTextView.getText().toString();
        String totalCredit = binding.totalCreditTextView.getText().toString();
        String totalBalance = binding.totalBalanceTextView.getText().toString();
        String newCurrencyName = binding.currencyTextView.getText().toString();
        Integer currencyId = allAvailableCurrencies.stream().filter(currency -> currency.name.equals(newCurrencyName)).findFirst().map(currency -> currency.id).orElse(null);
        Log.e(TAG, "generateAndSharePdf: accountName " + accountName + " currentList " + currentList + " currency " + newCurrencyName + " totalDebit " + totalDebit + " totalCredit " + totalCredit + " totalBalance " + totalBalance);
        // إظهار ديالوج التقدم
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("جاري إنشاء التقرير...");
        progressDialog.show();

        // تحديد فترة التقرير (لكامل العمليات)
        long startDateInMillis = 0L;
        long endDateInMillis = Long.MAX_VALUE;

        new Thread(() -> {
            // إنشاء التقرير في خيط خلفي
            Account accountForReport = this.currentAccount;
            String currentLanguage = PreferenceHelper.getLanguage(this);
            ReportGenerator generator = new ReportGenerator(getApplicationContext(), currentUser, currentLanguage);

            File pdfFile = null;
            try {
                // **الخطوة 3: تمرير الحساب الحالي مباشرة (هذا هو الإصلاح الرئيسي)**
                //   pdfFile = generator.generateDetailedAccountStatement(accountForReport, currencyId, startDateInMillis, endDateInMillis, false);
            } catch (Exception e) {
                Log.e(TAG, "Error generating PDF in background", e);
            }


            File finalPdfFile = pdfFile;
            runOnUiThread(() -> {
                progressDialog.dismiss();
                if (finalPdfFile != null) {
                    sharePdf(finalPdfFile);
                } else {
                    Toast.makeText(this, "فشل إنشاء التقرير", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void sharePdf(File pdfFile) {
        Uri fileUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", pdfFile);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // يمكنك استخدام Intent.createChooser لعرض قائمة تطبيقات
        startActivity(Intent.createChooser(shareIntent, "مشاركة التقرير عبر..."));
    }

    private void writePdfToUri(Uri uri) {
        List<TransactionItem> currentList = historyAdapter.getCurrentList();
        if (currentList.isEmpty()) {
            Toast.makeText(this, "لا توجد عمليات لإنشاء تقرير", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("جاري إنشاء التقرير...");
        progressDialog.show();

//        String currency = currencies[currentCurrencyIndex];
        String currency = binding.currencyTextView.getText().toString();
        String totalDebit = binding.totalDebitTextView.getText().toString();
        String totalCredit = binding.totalCreditTextView.getText().toString();
        String totalBalance = binding.totalBalanceTextView.getText().toString();

        new Thread(() -> {
            boolean success = false;
            try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                if (outputStream != null) {
//                    success = PdfGenerator.createPdf(outputStream, accountName, currency, currentList, totalDebit, totalCredit, totalBalance);
                }
            } catch (Exception e) {
                Log.e("PDF_SAVE", "Failed to write to URI", e);
            }

            boolean finalSuccess = success;
            runOnUiThread(() -> {
                progressDialog.dismiss();
                if (finalSuccess) {
                    Toast.makeText(this, "تم حفظ التقرير بنجاح", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "فشل إنشاء التقرير", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void generateAndOpenFile() {
        // **الخطوة 1: التحقق من أن بيانات الحساب والمستخدم جاهزة**
//        if (currentAccount == null || currentUser == null) {
//            Toast.makeText(this, "جاري تحميل البيانات، يرجى المحاولة بعد لحظات", Toast.LENGTH_SHORT).show();
//            return;
//        }

        List<TransactionItem> currentList = historyAdapter.getCurrentList();
        if (currentList.isEmpty()) {
            Toast.makeText(this, "لا توجد عمليات لإنشاء تقرير", Toast.LENGTH_SHORT).show();
            return;
        }
        String currentLanguage = PreferenceHelper.getLanguage(this);
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("جاري إنشاء التقرير...");
        progressDialog.show();

        // جلب العملة الحالية من الواجهة
        String newCurrencyName = binding.currencyTextView.getText().toString();
        Integer currencyId = allAvailableCurrencies.stream().filter(currency -> currency.name.equals(newCurrencyName)).findFirst().map(currency -> currency.id).orElse(null);


        // تحديد فترة التقرير (لكامل العمليات)
        long startDateInMillis = 0L;
        long endDateInMillis = Long.MAX_VALUE;

        new Thread(() -> {
            // **الخطوة 2: إنشاء نسخة من الحساب الحالي لضمان عدم حدوث مشاكل في الخيوط**
            // هذا إجراء احترازي لضمان أن بيانات الحساب لن تتغير أثناء إنشاء التقرير
            Account accountForReport = this.currentAccount;

            ReportGenerator generator = new ReportGenerator(getApplicationContext(), currentUser, currentLanguage);
            File pdfFile = null;
            try {
                // **الخطوة 3: تمرير الحساب الحالي مباشرة (هذا هو الإصلاح الرئيسي)**
                //  pdfFile = generator.generateDetailedAccountStatement(accountForReport, currencyId, startDateInMillis, endDateInMillis, false);
            } catch (Exception e) {
                Log.e(TAG, "Error generating PDF in background", e);
            }
            File finalPdfFile = pdfFile;
            runOnUiThread(() -> {
                progressDialog.dismiss();
                if (finalPdfFile != null) {
                    // إذا نجح الإنشاء، نقوم بعرضه مباشرة
                    viewPdf(finalPdfFile);
                } else {
                    Toast.makeText(this, "فشل إنشاء التقرير", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private Account findAccountByName(String name) {
        if (accountsList == null || name.isEmpty()) return null;
        for (Account acc : accountsList) {
            if (acc.getAccountName().equals(name)) return acc;
        }
        return null;
    }

    private void viewPdf(File pdfFile) {
        // استخدام FileProvider للوصول الآمن للملف
        Uri fileUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", pdfFile);

        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(fileUri, "application/pdf");
        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // صلاحية مؤقتة للقراءة

        try {
            startActivity(viewIntent);
        } catch (Exception e) {
            Toast.makeText(this, "لا يوجد تطبيق لفتح ملفات PDF", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.account_details_menu, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        // استخدام الكلاس الصحيح
        SearchView searchView = (SearchView) searchItem.getActionView();
        // تطبيق التنسيق
        styleSearchView(searchView);
        searchView.setQueryHint("ابحث في التفاصيل...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                viewModel.setTransactionSearchQuery(newText);
                return true;
            }
        });
        return true;
    }

    private void styleSearchView(SearchView searchView) {
        // الوصول إلى أيقونة البحث
        ImageView searchIcon = searchView.findViewById(androidx.appcompat.R.id.search_mag_icon);
        searchIcon.setColorFilter(Color.WHITE);
        // الوصول إلى زر الإغلاق
        ImageView closeButton = searchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        closeButton.setColorFilter(Color.WHITE);
        // الوصول إلى حقل النص
        EditText searchText = searchView.findViewById(androidx.appcompat.R.id.search_src_text);
        searchText.setTextColor(Color.WHITE);
        searchText.setHintTextColor(Color.LTGRAY);
    }

    private void showReportsDialog() {
        if (currentAccount == null) return;
        String currency = binding.currencyTextView.getText().toString();

        ReportsDialog reportsDialog = ReportsDialog.newInstanceForAccount(currentAccount.getId(), currentAccount.getAccountName(), currency);
        reportsDialog.show(getSupportFragmentManager(), "ReportsDialogSingleAccount");
    }
}