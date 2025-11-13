package com.hpp.daftree.ui;


import android.view.Gravity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
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
import com.hpp.daftree.AccountTypeViewModel;
import com.hpp.daftree.adapters.AccountsTypeAdapter;
import com.hpp.daftree.database.Currency;

import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.UUIDGenerator;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.databinding.ActivityAccountsTypeBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.firebase.auth.FirebaseAuth;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.utils.EdgeToEdgeUtils;
import com.hpp.daftree.utils.SecureLicenseManager;

public class AccountsTypeActivity extends BaseActivity implements AccountsTypeAdapter.OnItemInteractionListener {

    private ActivityAccountsTypeBinding binding; // افترض وجود هذا الـ binding
    private AccountTypeViewModel accountTypeViewModel;
    private AccountsTypeAdapter adapter;
    private final List<AccountType> defaultTypes = new ArrayList<>();
    private final List<AccountType> allTypes = new ArrayList<>();
    private boolean isGuest;
    private  String guestUID;

    @Override
    public void onAccountTypeLongClicked(AccountType accountType) {
        List<String> defaultTypes = Arrays.asList("عملاء", "موردين", "عام");

        if (defaultTypes.contains(accountType.name)) {
            showDefaultItemWarning();
        } else {
            showEditDeleteAccountTypeDialog(accountType);
        }
    }

    // **دالة جديدة لعرض رسالة التحذير**
    private void showDefaultItemWarning() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("عنصر افتراضي")
                .setMessage("لا يمكن تعديل أو حذف العناصر الافتراضية للتطبيق.")
                .setPositiveButton("موافق", null)
                .setIcon(R.drawable.ic_info)
                .show();
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountsTypeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeUtils.applyEdgeToEdge(this, binding.toolbarAccountsType);
        MyApplication.applyGlobalTextWatcher(binding.getRoot());

        isGuest= SecureLicenseManager.getInstance(this).isGuest();
        guestUID= SecureLicenseManager.getInstance(this).guestUID();
        binding.toolbarAccountsType.setNavigationOnClickListener(v -> finish());
//        setupDefaultAccountTypes();


        accountTypeViewModel = new ViewModelProvider(this).get(AccountTypeViewModel.class);
//        accountTypeViewModel.getAllAccountTypes().observe(this, accountsType -> {
//            adapter.setAccountsType(accountsType);
//        });
        accountTypeViewModel.getAllAccountTypes().observe(this, accountTypes -> {
            if (adapter != null) {
                adapter.setAccountsType(accountTypes);
            }
        });
        setupRecyclerView();
        binding.fabAddAccountType.setOnClickListener(v -> showAddAccountTypeDialog());
    }

    private void setupDefaultAccountTypes() {
        AccountType customers = new AccountType();
        customers.name = "عملاء";
        AccountType suppliers = new AccountType();
        suppliers.name = "موردين";
        AccountType general = new AccountType();
        general.name = "عام";
        defaultTypes.add(customers);
        defaultTypes.add(suppliers);
        defaultTypes.add(general);
    }
    // ... (دوال setupRecyclerView, showAddAccountTypeDialog, validate, save)
    private void showEditDeleteAccountTypeDialog(final AccountType accountType) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        final EditText input = new EditText(this);
        input.setText(accountType.name);
        input.setGravity(Gravity.CENTER_HORIZONTAL);
        input.setTypeface(null, Typeface.BOLD);
        input.setEnabled(false);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(30), (source, start, end, dest, dstart, dend) -> {
            String text = dest.toString() + source.toString();
            if (text.split("\\s+").length > 2) return "";
            return null;
        }});
        layout.addView(input);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("تعديل / حذف تصنيف")
                .setView(layout)
                .setPositiveButton("حفظ", null)
                .setNeutralButton("تعديل", null)
                .setNegativeButton("حذف", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button buttonSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button buttonEdit = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            Button buttonDelete = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            buttonSave.setVisibility(View.GONE);

            buttonEdit.setOnClickListener(v -> {
                input.setEnabled(true);
                input.requestFocus();
                buttonEdit.setVisibility(View.GONE);
                buttonSave.setVisibility(View.VISIBLE);
            });

            buttonSave.setOnClickListener(v -> {
                String newName = input.getText().toString().trim();
                if (newName.isEmpty() || newName.equals(accountType.name)) {
                    dialog.dismiss();
                    return;
                }

                // **المنطق الجديد: التحقق من الاسم المكرر قبل الحفظ**
                DaftreeRepository repo = new DaftreeRepository(getApplication());
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    AccountType existing = repo.getAccountTypeByName(newName);
                    runOnUiThread(() -> {
                        if (existing != null) {
                            Toast.makeText(this, "يوجد تصنيف بهذا الاسم بالفعل!", Toast.LENGTH_SHORT).show();
                        } else {
                            repo.updateAccountType(accountType, newName);
                            Toast.makeText(this, "تم تحديث التصنيف بنجاح", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        }
                    });
                });
            });

            buttonDelete.setOnClickListener(v -> {
                // **منطق الحذف الكامل مع التحقق من الارتباط**
                DaftreeRepository repo = new DaftreeRepository(getApplication());
                // تنفيذ الفحص في خيط خلفي لتجنب تجميد الواجهة
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    int count = repo.getAccountCountForType(accountType.name);
                    // العودة للخيط الرئيسي لعرض النتيجة
                    runOnUiThread(() -> {
                        if (count > 0) {
                            // إذا كان هناك حسابات مرتبطة، اعرض رسالة تحذير
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle("لا يمكن الحذف")
                                    .setMessage("لا يمكن حذف هذا التصنيف لأنه مستخدم في " + count + " حساب. يرجى تغيير تصنيف هذه الحسابات أولاً.")
                                    .setPositiveButton("موافق", null)
                                    .setIcon(R.drawable.ic_alert)
                                    .show();
                        } else {
                            // إذا لم يكن هناك ارتباط، اعرض ديالوج تأكيد الحذف
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle("تأكيد الحذف")
                                    .setMessage("هل أنت متأكد من حذف تصنيف '" + accountType.name + "'؟")
                                    .setNegativeButton("إلغاء", null)
                                    .setPositiveButton("حذف", (d, w) -> {
                                        repo.deleteAccountType(accountType);
                                        Toast.makeText(this, "تم حذف التصنيف", Toast.LENGTH_SHORT).show();
                                    })
                                    .show();
                        }
                        dialog.dismiss();
                    });
                });
            });
        });
        dialog.show();
    }
    private void setupRecyclerView() {
        adapter = new AccountsTypeAdapter(new ArrayList<>(), AccountsTypeActivity.this);
        binding.accountsTypesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.accountsTypesRecyclerView.setAdapter(adapter);
    }

    private void showAddAccountTypeDialog() {
        EditText input = new EditText(this);
        // فلتر لمنع إدخال أكثر من كلمتين
        input.setFilters(new InputFilter[] { (source, start, end, dest, dstart, dend) -> {
            String text = dest.toString() + source.toString();
            if (text.split("\\s+").length > 2) {
                return ""; // منع الإدخال
            }
            return null; // السماح بالإدخال
        }});

        new MaterialAlertDialogBuilder(this)
                .setTitle("إضافة نوع جديد")
                .setMessage("أدخل اسم الحساب (كلمتين بحد أقصى)")
                .setView(input)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    String accountTypeName = input.getText().toString().trim();
                    if (validateAccountType(accountTypeName)) {
                        saveAccountType(accountTypeName, () -> {
                            // نجاح -> اغلاق الديالوج على الـ UI thread
                            runOnUiThread(dialog::dismiss);
                        });
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private boolean validateAccountType(String name) {
        List<AccountType> accountTypeList = adapter.getAccountTypes(allTypes); // افترض وجود دالة getter في Adapter

        if (name.isEmpty()) {
            Toast.makeText(this, "اسم التصنيف لا يمكن أن يكون فارغًا", Toast.LENGTH_SHORT).show();
            return false;
        }
        // التحقق من عدم تكرار العملة
        for (AccountType accountType : accountTypeList) {
            if (accountType.name.equalsIgnoreCase(name)) {
                Toast.makeText(this, "هذا التصنيف موجودة بالفعل", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        if ( name.split("\\s+").length > 2) {
            Toast.makeText(this, R.string.error_name_length, Toast.LENGTH_SHORT).show();
            return false; // <-- لن يتم إغلاق الديالوج هنا
        }
        return true;
    }
    /**
     * حفظ عملة جديدة — يقوم بفحص التكرار في DB داخل Background thread
     * @param name اسم العملة
     * @param onSuccess Runnable يُستدعى على UI thread عند نجاح الحفظ (مثلاً لإغلاق الديالوج)
     */
    private void saveAccountType(String name, Runnable onSuccess) {
        if (name == null) return;
        final String trimmed = name.trim();
        if (!validateAccountType(trimmed)) return;

        final String uid;
        if (isGuest) {
            uid = SecureLicenseManager.getInstance(this).guestUID();
        } else {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Toast.makeText(this, "خطأ: المستخدم غير مسجل", Toast.LENGTH_SHORT).show();
                return;
            }
            uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        if (uid == null) {
            Toast.makeText(this, "خطأ: المستخدم غير مسجل", Toast.LENGTH_SHORT).show();
            return;
        }

        // فحص وجود العملة في القاعدة ثم الإدخال
        DaftreeRepository repo = new DaftreeRepository(getApplication());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AccountType existing = repo.getAccountTypeByName(trimmed);
            runOnUiThread(() -> {
                if (existing != null) {
                    Toast.makeText(this, "هذا التصنيف موجودة بالفعل", Toast.LENGTH_SHORT).show();
                } else {
                    AccountType newAccountType = new AccountType();
                    newAccountType.name = name;
                    newAccountType.setOwnerUID(uid);
                    newAccountType.setFirestoreId(UUIDGenerator.generateSequentialUUID());
                    newAccountType.setSyncStatus("NEW");
                    newAccountType.setLastModified(System.currentTimeMillis());
                    newAccountType.setDefault(false);
                    accountTypeViewModel.insert(newAccountType);

                    Toast.makeText(this, "تمت إضافة التصنيف بنجاح", Toast.LENGTH_SHORT).show();

                    if (onSuccess != null) {
                        onSuccess.run(); // عادة إغلاق الديالوج
                    }
                }
            });
        });
    }
    private void saveAccountType11(String name) {
        String uid;
        if (isGuest) {
            uid = SecureLicenseManager.getInstance(this).guestUID();
        } else {
            uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        if (uid == null) {
            Toast.makeText(this, "خطأ: المستخدم غير مسجل", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!validateAccountType(name)){
            return;
        }
        AccountType newAccountType = new AccountType();
        newAccountType.name = name;
        newAccountType.setOwnerUID(uid);
        newAccountType.setFirestoreId(UUIDGenerator.generateSequentialUUID());
        newAccountType.setSyncStatus("NEW");
        newAccountType.setLastModified(System.currentTimeMillis());
        newAccountType.setDefault(false);
        accountTypeViewModel.insert(newAccountType);
        Toast.makeText(this, "تمت إضافة العملة بنجاح", Toast.LENGTH_SHORT).show();
    }
    @Override
    public void onDeleteAccountType(AccountType accountType) {
        if (accountType.isDefault()) {
            Toast.makeText(this,R.string.error_deleted , Toast.LENGTH_SHORT).show();
            return;
        }

        DaftreeRepository repo = new DaftreeRepository(getApplication());
        // تنفيذ الفحص في خيط خلفي لتجنب تجميد الواجهة
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int count = repo.getAccountCountForType(accountType.name);
            // العودة للخيط الرئيسي لعرض النتيجة
            runOnUiThread(() -> {
                if (count > 0) {
                    // إذا كان هناك حسابات مرتبطة، اعرض رسالة تحذير
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("لا يمكن الحذف")
                            .setMessage("لا يمكن حذف هذا التصنيف لأنه مستخدم في " + count + " حساب. يرجى تغيير تصنيف هذه الحسابات أولاً.")
                            .setPositiveButton("موافق", null)
                            .setIcon(R.drawable.ic_alert)
                            .show();
                } else {
                    // إذا لم يكن هناك ارتباط، اعرض ديالوج تأكيد الحذف
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("تأكيد الحذف")
                            .setMessage("هل أنت متأكد من حذف تصنيف '" + accountType.name + "'؟")
                            .setNegativeButton("إلغاء", null)
                            .setPositiveButton("حذف", (d, w) -> {
                                repo.deleteAccountType(accountType);
                                Toast.makeText(this, "تم حذف التصنيف", Toast.LENGTH_SHORT).show();
                            })
                            .show();
                }
            });
        });
    }
    @Override
    public void onEditeAccountType(AccountType accountType) {
        showAccountTypeDialog(accountType, true);
    }

    private void showAccountTypeDialog(AccountType accountType, boolean isEdit) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edite, null);
        EditText editName = dialogView.findViewById(R.id.edit_name);

        if (isEdit && accountType != null) {
            editName.setText(accountType.getName());
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(isEdit ? R.string.edit_account_type : R.string.add_account_type)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String name = editName.getText().toString().trim();
                    if (!validateAccountType(name)) return;
                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.required_field, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (isEdit) {
                        updateAccountType(accountType, name);
                    } else {
                        saveAccountType(name, () -> {
                            // نجاح -> اغلاق الديالوج على الـ UI thread
                            runOnUiThread(dialog::dismiss);
                        });
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
    private void updateAccountType(AccountType accountType, String name) {

        if ( name.equals(accountType.name)) {
            return;
        }
        if (!validateAccountType(name)){
            return;
        }
        // **المنطق الجديد: التحقق من الاسم المكرر قبل الحفظ**
        DaftreeRepository repo = new DaftreeRepository(getApplication());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AccountType existing = repo.getAccountTypeByName(name);
            runOnUiThread(() -> {
                if (existing != null) {
                    Toast.makeText(this, "يوجد تصنيف بهذا الاسم بالفعل!", Toast.LENGTH_SHORT).show();
                } else {
                    repo.updateAccountType(accountType, name);
                    Toast.makeText(this, "تم تحديث التصنيف بنجاح", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}