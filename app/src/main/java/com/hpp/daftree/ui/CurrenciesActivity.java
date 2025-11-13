package com.hpp.daftree.ui;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.UUIDGenerator;
import com.hpp.daftree.adapters.CurrenciesAdapter;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.databinding.ActivityCurrenciesBinding;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.utils.EdgeToEdgeUtils;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.util.ArrayList;

public class CurrenciesActivity extends BaseActivity implements CurrenciesAdapter.OnItemInteractionListener {

    private static final String TAG = "CurrenciesActivity";
    private ActivityCurrenciesBinding binding;
    private CurrencyViewModel currencyViewModel;
    private CurrenciesAdapter adapter;
    private boolean isGuest;
    private String guestUID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCurrenciesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeUtils.applyEdgeToEdge(this, binding.toolbarCurrencies);
        MyApplication.applyGlobalTextWatcher(binding.getRoot());
        binding.toolbarCurrencies.setNavigationOnClickListener(v -> finish());

        isGuest = SecureLicenseManager.getInstance(this).isGuest();
        guestUID = SecureLicenseManager.getInstance(this).guestUID();

        currencyViewModel = new ViewModelProvider(this).get(CurrencyViewModel.class);
        currencyViewModel.getAllCurrencies().observe(this, currencies -> {
            if (adapter != null) {
                adapter.setCurrencies(currencies);
            }
        });

        setupRecyclerView();
        binding.fabAddCurrency.setOnClickListener(v -> showCurrencyDialog(null, false));
    }

    private void setupRecyclerView() {
        adapter = new CurrenciesAdapter(new ArrayList<>(), this);
        binding.currenciesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.currenciesRecyclerView.setAdapter(adapter);
    }

    // ========== معالجة النقرات ==========

    @Override
    public void onCurrencyLongClicked(Currency currency) {
        showEditDeleteCurrencyDialog(currency);
    }

    @Override
    public void onEditCurrency(Currency currency) {
        showCurrencyDialog(currency, true);
    }

    @Override
    public void onDeleteCurrency(Currency currency) {
        if (currency.isDefault()) {
            Toast.makeText(this, R.string.error_deleted, Toast.LENGTH_SHORT).show();
            return;
        }
        showDeleteConfirmationDialog(currency);
    }

    // ========== الديالوجات ==========

    private void showCurrencyDialog(Currency currency, boolean isEdit) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_currency_edite, null);
        EditText editName = dialogView.findViewById(R.id.edit_currency_name);
        EditText editSymbol = dialogView.findViewById(R.id.edit_currency_symbol);
        EditText editCode = dialogView.findViewById(R.id.edit_currency_code);

        if (isEdit && currency != null) {
            editName.setText(currency.getName());
            editSymbol.setText(currency.getSymbol());
            editCode.setText(currency.getCode());
        } else {
            // للإضافة: تعيين قيم افتراضية مقترحة
            String currencySymbol = "﷼"; // ﷼

            editSymbol.setHint(currencySymbol);
            editCode.setHint("YER");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isEdit ? R.string.edit_currency : R.string.add_currency)
                .setView(dialogView)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null);

        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                String name = editName.getText().toString().trim();
                String symbol = editSymbol.getText().toString().trim();
                String code = editCode.getText().toString().trim();

                if (!validateCurrencyInputs(name, symbol, code, isEdit ? currency : null)) {
                    return;
                }

                if (isEdit) {
                    updateCurrency(currency, name, symbol, code);
                    dialog.dismiss();
                } else {
                    saveNewCurrency(name, symbol, code, dialog::dismiss);
                }
            });
        });

        dialog.show();
    }

    @SuppressLint("StringFormatMatches")
    private void showEditDeleteCurrencyDialog(final Currency currency) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        final EditText input = new EditText(this);
        input.setText(currency.name);
        input.setEnabled(false);
        input.setTextSize(16);
        input.setPadding(20, 20, 20, 20);
        layout.addView(input);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.currency_details))
                .setView(layout)
                .setPositiveButton(getString(R.string.edit), null)
                .setNegativeButton(getString(R.string.delete), null)
                .setNeutralButton(getString(R.string.cancel), null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button buttonEdit = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button buttonDelete = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            if (currency.isDefault()) {
                buttonDelete.setEnabled(false);
                buttonDelete.setAlpha(0.5f);
            }

            buttonEdit.setOnClickListener(v -> {
                dialog.dismiss();
                showCurrencyDialog(currency, true);
            });

            buttonDelete.setOnClickListener(v -> {
                dialog.dismiss();
                showDeleteConfirmationDialog(currency);
            });
        });

        dialog.show();
    }

    private void showDeleteConfirmationDialog(Currency currency) {
        DaftreeRepository repo = new DaftreeRepository(getApplication());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int count = repo.getTransactionCountForCurrency(currency.id);
            runOnUiThread(() -> {
                if (count > 0) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.delete_2))
                            .setMessage(getString(R.string.cuurency_error_deleted, count))
                            .setPositiveButton(getString(R.string.ar_text_5_3), null)
                            .setIcon(R.drawable.ic_info)
                            .show();
                } else {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.delete_confirmation_title)
                            .setMessage(getString(R.string.currency_delete_message, currency.name))
                            .setNegativeButton(getString(R.string.cancel), null)
                            .setPositiveButton(getString(R.string.delete), (d, w) -> {
                                repo.deleteCurrency(currency);
                                Toast.makeText(this, R.string.currency_deleted_successfully, Toast.LENGTH_SHORT).show();
                            })
                            .show();
                }
            });
        });
    }

    // ========== التحقق من الصحة ==========

    private boolean validateCurrencyInputs(String name, String symbol, String code, Currency editingCurrency) {
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.currency_name_required, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (symbol.isEmpty()) {
            Toast.makeText(this, R.string.currency_symbol_required, Toast.LENGTH_SHORT).show();
            return false;
        }
        if (code.isEmpty()) {
            Toast.makeText(this, R.string.currency_code_required, Toast.LENGTH_SHORT).show();
            return false;
        }

        // التحقق من عدم تكرار الاسم
        DaftreeRepository repo = new DaftreeRepository(getApplication());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Currency existing = repo.getCurrencyByName(name);
            runOnUiThread(() -> {
                if (existing != null && (editingCurrency == null || existing.id != editingCurrency.id)) {
                    Toast.makeText(this, R.string.currency_already_exists, Toast.LENGTH_SHORT).show();
                }
            });
        });

        return true;
    }

    // ========== عمليات قاعدة البيانات ==========

    private void saveNewCurrency(String name, String symbol, String code, Runnable onSuccess) {
        final String uid = getCurrentUserId();
        if (uid == null) return;

        DaftreeRepository repo = new DaftreeRepository(getApplication());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Currency existing = repo.getCurrencyByName(name);
            runOnUiThread(() -> {
                if (existing != null) {
                    Toast.makeText(this, R.string.currency_already_exists, Toast.LENGTH_SHORT).show();
                } else {
                    Currency newCurrency = createNewCurrency(name, symbol, code, uid);
                    currencyViewModel.insert(newCurrency);
                    Toast.makeText(this, R.string.currency_add_successfully, Toast.LENGTH_SHORT).show();
                    if (onSuccess != null) onSuccess.run();
                }
            });
        });
    }

    private void updateCurrency(Currency currency, String name, String symbol, String code) {
        if (name.equals(currency.name) && symbol.equals(currency.symbol) && code.equals(currency.code)) {
            return; // لم يتغير شيء
        }

        DaftreeRepository repo = new DaftreeRepository(getApplication());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Currency existing = repo.getCurrencyByName(name);
            runOnUiThread(() -> {
                if (existing != null && existing.id != currency.id) {
                    Toast.makeText(this, R.string.currency_already_exists, Toast.LENGTH_SHORT).show();
                } else {
                    repo.updateCurrency(currency, name, symbol, code);
                    Toast.makeText(this, R.string.currency_update_successfully, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // ========== دوال مساعدة ==========

    private String getCurrentUserId() {
        if (isGuest) {
            return guestUID;
        } else {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Toast.makeText(this, R.string.ar_long_text_15, Toast.LENGTH_SHORT).show();
                return null;
            }
            return FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
    }

    private Currency createNewCurrency(String name, String symbol, String code, String uid) {
        Currency currency = new Currency();
        currency.name = name;
        currency.symbol = symbol;
        currency.code = code;
        currency.setOwnerUID(uid);
        currency.setFirestoreId(UUIDGenerator.generateSequentialUUID());
        currency.setSyncStatus("NEW");
        currency.setLastModified(System.currentTimeMillis());
        currency.setDefault(false);
        return currency;
    }
}