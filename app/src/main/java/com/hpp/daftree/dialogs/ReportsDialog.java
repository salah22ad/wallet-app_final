package com.hpp.daftree.dialogs;


import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.firebase.auth.FirebaseAuth;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.ReportsViewModel;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.databinding.ActivityReportsBinding;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.User;
import com.hpp.daftree.helpers.PreferenceHelper;
import com.hpp.daftree.models.ReportGenerator;
import com.hpp.daftree.ui.CurrencyViewModel;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ReportsDialog extends DialogFragment {

    private ActivityReportsBinding binding;
    private ReportsViewModel viewModel;
    private User currentUser;
    private List<Account> accountsList;
    private final Calendar startDate = Calendar.getInstance();
    private final Calendar endDate = Calendar.getInstance();
    private static final String ARG_SELECTED_TYPE = "selected_type";
    private static final String ARG_IS_ALL_ACCOUNTS = "is_all_accounts";
    private static final String ARG_IS_Single_ACCOUNT = "is_all_accounts";
    private static final String account_name = "0";
    private static final boolean isSingleAccount = false;
    private static final boolean isAllAccounts = false;
    private static final String ARG_ACCOUNT_ID = "account_id";
    private static final String ARG_ACCOUNT_NAME = "account_name";
    private static final String ARG_LAUNCH_MODE = "launch_mode";
    private static final String ARG_ACCOUNT_TYPE = "account_type";
    private static final String ARG_CURRENCY = "currency";
    private String selectedCurrency;
    private final List<Currency> allAvailableCurrencies = new ArrayList<>();
    private CurrencyViewModel currencyViewModel;

    private enum LaunchMode {GENERAL, FILTERED_TYPE, ALL_ACCOUNTS, SINGLE_ACCOUNT}

    private LaunchMode currentMode;
    private boolean isGuest;
    private  String guestUID;

    public static ReportsDialog newInstanceGeneral() {
        ReportsDialog dialog = new ReportsDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_LAUNCH_MODE, LaunchMode.GENERAL);
        dialog.setArguments(args);
        return dialog;
    }

    public static ReportsDialog newInstanceFromMainToolbar(String accountType, String currency, boolean isAllAccounts) {
        ReportsDialog dialog = new ReportsDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_LAUNCH_MODE, isAllAccounts ? LaunchMode.ALL_ACCOUNTS : LaunchMode.FILTERED_TYPE);
        args.putString(ARG_ACCOUNT_TYPE, accountType);
        args.putString(ARG_CURRENCY, currency);
        dialog.setArguments(args);
        return dialog;
    }

    public static ReportsDialog newInstanceForAccount(int accountId, String accountName, String currency) {
        ReportsDialog dialog = new ReportsDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_LAUNCH_MODE, LaunchMode.SINGLE_ACCOUNT);
        args.putInt(ARG_ACCOUNT_ID, accountId);
        args.putString(ARG_ACCOUNT_NAME, accountName);
        args.putString(ARG_CURRENCY, currency);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = ActivityReportsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ReportsViewModel.class);
        currencyViewModel = new ViewModelProvider(this).get(CurrencyViewModel.class);
        String lang = PreferenceHelper.getLanguage(requireContext());
        Locale locale = new Locale(lang);
        Log.e("ReportsDialog", "تم اكتشاف تغيير اللغة: " + lang);
        Locale.setDefault(locale);
        MyApplication.applyGlobalTextWatcher(binding.getRoot());
        isGuest= SecureLicenseManager.getInstance(getContext()).isGuest();
        guestUID= SecureLicenseManager.getInstance(getContext()).guestUID();
        Configuration config = new Configuration(requireContext().getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);

        requireContext().getResources()
                .updateConfiguration(config, requireContext().getResources().getDisplayMetrics());
        setupToolbar();
        setupListeners();
        setupCurrencyObservers();
        loadDefaultDates();
        setupDatePickers();

        if (getArguments() != null) {
            selectedCurrency = getArguments().getString(ARG_CURRENCY);
            Log.e("ReportsDialog", "ARG_CURRENCY: " + ARG_CURRENCY + ", selectedCurrency: " + selectedCurrency);
            currentMode = (LaunchMode) getArguments().getSerializable(ARG_LAUNCH_MODE);
            switch (currentMode) {
                case GENERAL:
                    setupForGeneralMode();
                    break;
                case FILTERED_TYPE:
                    setupForFilteredTypeMode(getArguments().getString(ARG_ACCOUNT_TYPE), getArguments().getString(ARG_CURRENCY));
                    break;
                case ALL_ACCOUNTS:
                    setupForAllAccountsMode(getArguments().getString(ARG_CURRENCY));
                    break;
                case SINGLE_ACCOUNT:
                    setupForSingleAccountMode(getArguments().getString(ARG_ACCOUNT_NAME), getArguments().getString(ARG_CURRENCY));
                    break;
            }
        }

        setupObservers();
    }

    private void setupCurrencyObservers() {
        currencyViewModel.getAllCurrencies().observe(this, currencies -> {
            if (currencies != null) {
                allAvailableCurrencies.clear();
                allAvailableCurrencies.addAll(currencies);
            }
        });
    }

    private void setupForGeneralMode() {
        binding.checkboxCurrency.setChecked(true);
        binding.radiogroupReportType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isIndividual = (checkedId == R.id.radio_detailed_movement || checkedId == R.id.radio_monthly_summary || checkedId == R.id.radio_total_balance);
            binding.accountsSpinnerLayout.setVisibility(isIndividual ? View.VISIBLE : View.GONE);
            binding.accountTypeSpinnerLayout.setVisibility(isIndividual ? View.GONE : View.VISIBLE);
        });
        binding.radioDetailedMovement.setChecked(true);
    }

    private void setupForFilteredTypeMode(String accountType, String currency) {
        binding.radioDetailedMovement.setVisibility(View.GONE);
        binding.radioMonthlySummary.setVisibility(View.GONE);
        binding.radioTotalBalance.setVisibility(View.GONE);

        binding.accountsSpinnerLayout.setVisibility(View.GONE);
        binding.accountTypeSpinnerLayout.setVisibility(View.GONE);
        binding.currencyLayout.setVisibility(View.GONE);

        binding.spinnerAccountType.setText(accountType, false);
        binding.spinnerCurrencies.setText(currency, false);

        binding.radioDetailedMovementByAccountType.setChecked(true);
    }

    private void setupForAllAccountsMode(String currency) {
        setupForFilteredTypeMode(getString(R.string.account_2), currency);
    }

    private void setupForSingleAccountMode(String accountName, String currency) {
        binding.radioTotalBalanceByAccountType.setVisibility(View.GONE);
        binding.radioDetailedMovementByAccountType.setVisibility(View.GONE);
        binding.radioMonthlySummaryByAccountType.setVisibility(View.GONE);

        binding.accountTypeSpinnerLayout.setVisibility(View.GONE);
        binding.accountsSpinnerLayout.setVisibility(View.VISIBLE);
        binding.currencyLayout.setVisibility(View.GONE);

        binding.spinnerAccounts.setText(accountName, false);
        binding.spinnerAccounts.setEnabled(false);
        binding.spinnerCurrencies.setText(currency, false);

        binding.radioDetailedMovement.setChecked(true);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = ViewGroup.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setLayout(width, height);
        }
    }

    private void setupToolbar() {
       // binding.toolbarReports.setNavigationOnClickListener(v -> dismiss());
    }

    private void setupListeners() {
        binding.checkboxDateRange.setOnCheckedChangeListener((cb, isChecked) ->
                binding.dateRangeLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE));
        binding.checkboxCurrency.setOnCheckedChangeListener((cb, isChecked) ->
                binding.currencyLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE));

        binding.buttonGenerateReport.setOnClickListener(v -> generateReport());

        binding.radiogroupReportType.setOnCheckedChangeListener((group, checkedId) -> {
            updateSpinnersVisibility(checkedId);
        });
    }

    private void updateSpinnersVisibility(int checkedId) {
        if (isSingleAccount || isAllAccounts)
            return;
        if (checkedId == R.id.radio_detailed_movement ||
                checkedId == R.id.radio_monthly_summary ||
                checkedId == R.id.radio_total_balance) {
            binding.accountsSpinnerLayout.setVisibility(View.VISIBLE);
            binding.accountTypeSpinnerLayout.setVisibility(View.GONE);
        } else {
            binding.accountsSpinnerLayout.setVisibility(View.GONE);
            binding.accountTypeSpinnerLayout.setVisibility(View.VISIBLE);
        }
    }

    private List<AccountType> accountTypesList;

    private String findFirestoreIdByAccountTypeName(String name) {
        if (accountTypesList == null || name.isEmpty()) return null;
        for (AccountType type : accountTypesList) {
            if (type.getName().equals(name)) {
                return type.getFirestoreId();
            }
        }
        return null;
    }

    private void setupObservers() {
        viewModel.getUserProfile().observe(getViewLifecycleOwner(), user -> this.currentUser = user);

        viewModel.getAllAccountTypes().observe(getViewLifecycleOwner(), types -> {
            if (types == null || types.isEmpty()) return;
            this.accountTypesList = types;

            List<String> typeNames = types.stream().map(t -> t.name).collect(Collectors.toList());
            ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, typeNames);

            binding.spinnerAccountType.setAdapter(adapter);
            int itemHeight = (int) (48 * getResources().getDisplayMetrics().density);
            binding.spinnerAccountType.setDropDownHeight(itemHeight * 4);

            // عند وجود mode معين، استخدم الاسم بدلاً من firestoreId
            if (currentMode == LaunchMode.FILTERED_TYPE) {
                String accountTypeFirestoreId = getArguments().getString(ARG_ACCOUNT_TYPE);
                String accountTypeName = findAccountTypeNameByFirestoreId(accountTypeFirestoreId);

                if (accountTypeName != null && !accountTypeName.isEmpty()) {
                    binding.spinnerAccountType.setText(accountTypeName, false);
                } else if (!typeNames.isEmpty()) {
                    // Fallback إلى أول عنصر إذا لم يتم العثور على الاسم
                    binding.spinnerAccountType.setText(typeNames.get(0), false);
                }
            } else if (TextUtils.isEmpty(binding.spinnerAccountType.getText().toString()) && !typeNames.isEmpty()) {
                binding.spinnerAccountType.setText(typeNames.get(0), false);
            }
        });
        viewModel.getAllAccounts().observe(getViewLifecycleOwner(), accounts -> {
            if (accounts == null || accounts.isEmpty()) return;
            this.accountsList = accounts;
            List<String> accountNames = accounts.stream().map(Account::getAccountName).collect(Collectors.toList());
            ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, accountNames);
            binding.spinnerAccounts.setAdapter(adapter);
            int itemHeight = (int) (48 * getResources().getDisplayMetrics().density);
            binding.spinnerAccounts.setDropDownHeight(itemHeight * 4);

            if (TextUtils.isEmpty(binding.spinnerAccounts.getText().toString())) {
                binding.spinnerAccounts.setText(accountNames.get(0), false);
            }
        });

        viewModel.getAllCurrencies().observe(this, currencies -> {
            List<String> currencyNames = currencies.stream()
                    .map(c -> c.name)
                    .collect(Collectors.toList());

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    getContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    currencyNames
            );

            binding.spinnerCurrencies.setAdapter(adapter);
            int itemHeight = (int) (48 * getResources().getDisplayMetrics().density);
            binding.spinnerCurrencies.setDropDownHeight(itemHeight * 4);

            if (selectedCurrency != null && currencyNames.contains(selectedCurrency)) {
                binding.spinnerCurrencies.setText(selectedCurrency, false);
            } else if (!currencyNames.isEmpty()) {
                binding.spinnerCurrencies.setText(currencyNames.get(0), false);
            }
        });
    }

    private String findAccountTypeNameByFirestoreId(String firestoreId) {
        if (accountTypesList == null || firestoreId == null || firestoreId.isEmpty()) return null;
        for (AccountType type : accountTypesList) {
            if (firestoreId.equals(type.getFirestoreId())) {
                return type.getName();
            }
        }
        return null;
    }

    private void generateReport() {
        final long finalStartDate;
        final long finalEndDate;
        String currentLanguage = PreferenceHelper.getLanguage(requireContext());

        boolean useDateRange = binding.checkboxDateRange.isChecked();
        if (binding.checkboxDateRange.isChecked()) {
            finalStartDate = startDate.getTimeInMillis();
            finalEndDate = endDate.getTimeInMillis();
        } else {
            finalStartDate = 0L;
            finalEndDate = Long.MAX_VALUE;
        }

        //  String accountType = binding.spinnerAccountType.getText().toString();
        String selectedAccountName = "";
        int isSingleAccount = binding.spinnerAccounts.getVisibility();
        int isSingleAccountType = binding.spinnerAccountType.getVisibility();
        Log.e("generateReport", "currentLanguage: " + currentLanguage + " isSingleAccount: " + isSingleAccount + " isSingleAccountType " + isSingleAccountType);

        if (ARG_IS_Single_ACCOUNT.equals("true")) {
            selectedAccountName = account_name;
        } else {
            selectedAccountName = binding.spinnerAccounts.getText().toString();
        }
        Account selectedAccount = findAccountByName(selectedAccountName);
        boolean forAllCurrencies;
        forAllCurrencies = !binding.checkboxCurrency.isChecked();
        String selectedCurrencyName = binding.spinnerCurrencies.getText().toString();

        // البحث عن معرف العملة من الاسم
        int currencyId = allAvailableCurrencies.stream().filter(currency ->
                        currency.name.equals(selectedCurrencyName))
                .findFirst().map(currency -> currency.id).orElse(-1);

        if (currencyId == -1) {
            Toast.makeText(getContext(), getString(R.string.ar_long_text_49), Toast.LENGTH_SHORT).show();

            return;
        }

        String selectedAccountTypeName = binding.spinnerAccountType.getText().toString();
        // البحث عن firestoreId المقابل للاسم المختار
        String selectedAcTypeFirestoreId = findFirestoreIdByAccountTypeName(selectedAccountTypeName);

//        if ((selectedAcTypeFirestoreId == null) && (currentMode != LaunchMode.ALL_ACCOUNTS)) {
//            Toast.makeText(getContext(), "الرجاء تحديد نوع حساب صالح", Toast.LENGTH_SHORT).show();
//            return;
//        }

        ProgressDialog progress = new ProgressDialog(getContext());
        progress.setMessage(getString(R.string.ar_long_text_5));
        progress.setCancelable(false);
        progress.show();
        boolean finalForAllCurrencies = forAllCurrencies;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                ReportGenerator generator = new ReportGenerator(getContext().getApplicationContext(), currentUser, currentLanguage);
                File pdfFile = null;
                int selectedRadioId = binding.radiogroupReportType.getCheckedRadioButtonId();
                if (currentMode == LaunchMode.ALL_ACCOUNTS) {
                    if (finalForAllCurrencies) {
                        if (selectedRadioId == R.id.radio_total_balance_by_account_type) {
                           
                            pdfFile = generator.generateConsolidatedBalancesReportAllCurrencyAllTypes(finalStartDate, finalEndDate, useDateRange);
                        } else if (selectedRadioId == R.id.radio_detailed_movement_by_account_type) {
                            pdfFile = generator.generateConsolidatedMovementReportAllCurrenciesAllTypes(finalStartDate, finalEndDate, useDateRange);
                        } else if (selectedRadioId == R.id.radio_monthly_summary_by_account_type) {
                           
                            pdfFile = generator.generateMonthlySummaryByAccountAllCurrenciesAllTypes(null, currencyId);
                        }
                    } else {
                        if (selectedRadioId == R.id.radio_total_balance_by_account_type) {
                           
                            pdfFile = generator.generateConsolidatedBalancesReportAllTypes(currencyId, finalStartDate, finalEndDate, useDateRange);
                        } else if (selectedRadioId == R.id.radio_detailed_movement_by_account_type) {

                            pdfFile = generator.generateConsolidatedMovementReportAllTypes(currencyId, finalStartDate, finalEndDate, useDateRange);
                        } else if (selectedRadioId == R.id.radio_monthly_summary_by_account_type) {
                           
                            pdfFile = generator.generateMonthlySummaryByAccountAllTypes(null, currencyId);
                        }
                    }
                } else if ((currentMode != LaunchMode.ALL_ACCOUNTS) && (currentMode != LaunchMode.FILTERED_TYPE)) {
                    if (selectedRadioId == R.id.radio_monthly_summary && selectedAccount != null) {
                        getActivity().runOnUiThread(() -> {
                            if (isGuest) {
                                progress.dismiss();
                                Toast.makeText(getContext(), getContext().getString(R.string.ar_long_text_15), Toast.LENGTH_SHORT).show();
                                dismiss();
                            }
                        });
                        if (finalForAllCurrencies) {
                            pdfFile = generator.generateMonthlyAccountSummaryAllCurrency(selectedAccount, currencyId);
                        } else {
                            pdfFile = generator.generateMonthlyAccountSummary(selectedAccount, currencyId);
                        }
                    } else if (selectedRadioId == R.id.radio_detailed_movement && selectedAccount != null) {
                        if (finalForAllCurrencies) {
                            pdfFile = generator.generateDetailedMovementAccountStatementAllCurrencies(selectedAccount, currencyId, finalStartDate, finalEndDate, useDateRange);
                        } else {
                            pdfFile = generator.generateDetailedMovementAccountStatement(selectedAccount, currencyId, finalStartDate, finalEndDate, useDateRange);
                        }
                    } else if (selectedRadioId == R.id.radio_total_balance && selectedAccount != null) {
                        getActivity().runOnUiThread(() -> {
                            if (isGuest) {
                                progress.dismiss();
                                Toast.makeText(getContext(), getContext().getString(R.string.ar_long_text_15), Toast.LENGTH_SHORT).show();
                                dismiss();
                            }
                        });
                        if (finalForAllCurrencies) {
                            pdfFile = generator.generateDetailedAccountStatementAllCurrencies(selectedAccount, currencyId, finalStartDate, finalEndDate, useDateRange);
                        } else {
                            pdfFile = generator.generateDetailedAccountStatement(selectedAccount, currencyId, finalStartDate, finalEndDate, useDateRange);
                        }
                    }
                } else {
                    if (finalForAllCurrencies) {
                        if (selectedRadioId == R.id.radio_total_balance_by_account_type) {
                           
                            pdfFile = generator.generateConsolidatedBalancesReportAllCurrency(selectedAcTypeFirestoreId, finalStartDate, finalEndDate);
                        } else if (selectedRadioId == R.id.radio_detailed_movement_by_account_type) {
                            pdfFile = generator.generateConsolidatedMovementReportAllCurrencies(selectedAcTypeFirestoreId, finalStartDate, finalEndDate, useDateRange);

                        } else if (selectedRadioId == R.id.radio_monthly_summary_by_account_type) {
                           
                            pdfFile = generator.generateMonthlySummaryByAccountTypeAllCurrency(selectedAcTypeFirestoreId);
                        }

                    } else {
                        if (selectedRadioId == R.id.radio_total_balance_by_account_type) {
                            pdfFile = generator.generateConsolidatedBalancesReport(selectedAcTypeFirestoreId, currencyId, finalStartDate, finalEndDate, useDateRange);
                        } else if (selectedRadioId == R.id.radio_detailed_movement_by_account_type) {
                            pdfFile = generator.generateConsolidatedMovementReport(selectedAcTypeFirestoreId, currencyId, finalStartDate, finalEndDate, useDateRange);

                        } else if (selectedRadioId == R.id.radio_monthly_summary_by_account_type) {
                           
                            pdfFile = generator.generateMonthlySummaryByAccountType(selectedAcTypeFirestoreId, currencyId);
                        }

                    }


                }
                if (pdfFile != null) {
                    File finalPdfFile = pdfFile;
                    getActivity().runOnUiThread(() -> {
                        progress.dismiss();
                        viewPdf(finalPdfFile);
                        dismiss();
                    });
                } else {
                    getActivity().runOnUiThread(() -> {
                        progress.dismiss();
                        Toast.makeText(getContext(), getString(R.string.error_generating_report), Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                getActivity().runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(getContext(), getString(R.string.failed_gen_report) + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private Account findAccountByName(String name) {
        if (accountsList == null || name.isEmpty()) return null;
        for (Account acc : accountsList) {
            if (acc.getAccountName().equals(name)) return acc;
        }
        return null;
    }

    private void showDatePickerDialog(boolean isStartDate) {
        Calendar cal = isStartDate ? startDate : endDate;
        new DatePickerDialog(getContext(), (view, year, month, day) -> {
            cal.set(year, month, day);
            updateDateEditTexts();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateEditTexts() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        binding.edittextStartDate.setText(sdf.format(startDate.getTime()));
        binding.edittextEndDate.setText(sdf.format(endDate.getTime()));
    }

    private void viewPdf(File pdfFile) {
        Uri fileUri = FileProvider.getUriForFile(getContext(), getActivity().getPackageName() + ".provider", pdfFile);
        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(fileUri, "application/pdf");
        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(viewIntent);
        } catch (Exception e) {
            Toast.makeText(getContext(), getString(R.string.open), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadDefaultDates() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();

        binding.edittextEndDate.setText(sdf.format(cal.getTime()));

        cal.set(Calendar.DAY_OF_MONTH, 1);
        binding.edittextStartDate.setText(sdf.format(cal.getTime()));

        startDate.setTime(cal.getTime());
        endDate.setTime(new Date());
    }

    private void setupDatePickers() {
        binding.edittextStartDate.setOnClickListener(v -> showDatePicker(true));
        binding.edittextEndDate.setOnClickListener(v -> showDatePicker(false));
    }

    private void showDatePicker(boolean isStartDate) {
        Calendar calendar = isStartDate ? startDate : endDate;

        DatePickerDialog datePicker = new DatePickerDialog(
                getContext(),
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    updateDateText(isStartDate);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        datePicker.show();
    }

    private void updateDateText(boolean isStartDate) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        if (isStartDate) {
            binding.edittextStartDate.setText(sdf.format(startDate.getTime()));
        } else {
            binding.edittextEndDate.setText(sdf.format(endDate.getTime()));
        }
    }
}