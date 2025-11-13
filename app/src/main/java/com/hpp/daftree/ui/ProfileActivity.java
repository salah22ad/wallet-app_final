package com.hpp.daftree.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;

import com.hpp.daftree.DeleteAccountActivity;
import com.hpp.daftree.LoginActivity;
import com.hpp.daftree.ProfileViewModel;
import com.hpp.daftree.R;
import com.hpp.daftree.SplashActivity;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.databinding.ActivityProfileBinding;
import com.hpp.daftree.database.User;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.syncmanagers.FirestoreSyncManager;
import com.hpp.daftree.syncmanagers.SyncPreferences;
import com.hpp.daftree.utils.EdgeToEdgeUtils;
import com.hpp.daftree.utils.GoogleAuthHelper;
import com.hpp.daftree.utils.LicenseManager;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.utils.VersionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends BaseActivity {

    private ActivityProfileBinding binding;
    private ProfileViewModel viewModel;
    private User currentUserProfile;
    private FirebaseUser firebaseUser;
    private Uri selectedImageUri;

    private SyncPreferences sharedPrefsManager;
    private DaftreeRepository repository;
    private GoogleAuthHelper googleAuthHelper;
    private FirebaseFunctions firebaseFunctions;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    Glide.with(this)
                            .load(selectedImageUri)
                            .into(binding.profileImageView);
                }
            }
    );
    private boolean isGuest;
    private String guestUID;
    private SharedPreferences prefs;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeUtils.applyEdgeToEdge(this, binding.toolbarProfile);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        isGuest = SecureLicenseManager.getInstance(this).isGuest();
        guestUID = SecureLicenseManager.getInstance(this).guestUID();
        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        // تهيئة Firebase Functions
        firebaseFunctions = FirebaseFunctions.getInstance();

        sharedPrefsManager = new SyncPreferences(this);
        repository = new DaftreeRepository(getApplication());
        if (!isGuest) {
            googleAuthHelper = new GoogleAuthHelper(this, new LicenseManager(this), repository);
            firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            if (firebaseUser == null) {
                Toast.makeText(this, "المستخدم غير مسجل", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            binding.deleteProfileButton.setVisibility(View.VISIBLE);
        } else {
            binding.deleteProfileButton.setVisibility(View.GONE);
        }
        setupToolbar();
        observeUserProfile();
        binding.profileImageLayout.setOnClickListener(v -> openGallery());
        binding.saveProfileButton.setOnClickListener(v -> saveUserProfile());
        binding.deleteProfileButton.setOnClickListener(v -> deleteAccountDirectly()); // تم التعديل هنا
    }

    /**
     * الدالة الجديدة لحذف الحساب مباشرة دون فتح صفحة الويب
     */
    private void deleteAccountDirectly() {
        // عرض تأكيد الحذف
        new android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_account))
                .setMessage(getString(R.string.delete_account_message))
                .setPositiveButton(getString(R.string.yes_delete_acc), (dialog, which) -> {
                    showLoading(true);
                    executeAccountDeletion();
                })
                .setNegativeButton(getString(R.string.btn_undo), (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * تنفيذ عملية حذف الحساب عبر Firebase Functions
     */
    private void executeAccountDeletion() {
        if (isGuest) {
           // Toast.makeText(this, "لا يمكن حذف الحساب ", Toast.LENGTH_SHORT).show();
            showLoading(false);
            return;
        }

        // إعداد بيانات الحذف
        Map<String, Object> data = new HashMap<>();
        data.put("reason", "user_initiated_from_app");
        data.put("customReason", "تم الحذف مباشرة من التطبيق");
        data.put("language", "ar");

        // استدعاء دالة Firebase Functions
        firebaseFunctions
                .getHttpsCallable("deleteUserAccount")
                .call(data)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // تم حذف الحساب بنجاح من السيرفر
                        HttpsCallableResult result = task.getResult();
                        if (result != null) {
                            Object dataResult = result.getData();
                            if (dataResult instanceof Map) {
                                Map<String, Object> resultData = (Map<String, Object>) dataResult;
                                Boolean success = (Boolean) resultData.get("success");
                                if (success != null && success) {
                                    // نجح الحذف من السيرفر، الآن نقوم بحذف البيانات المحلية
                                    completeLocalDeletion();
                                    return;
                                }
                            }
                        }
                        // إذا لم يكن هناك نتيجة صحيحة
                        showDeletionError(getString(R.string.error_generic));
                    } else {
                        // فشل استدعاء الدالة
                        Exception exception = task.getException();
                        String errorMessage = getString(R.string.error_generic) +  ": " +
                                (exception != null ? exception.getMessage() : "خطأ غير معروف");
                        showDeletionError(errorMessage);
                    }
                });
    }

    /**
     * إكمال عملية الحذف المحلي بعد نجاح الحذف من السيرفر
     */
    private void completeLocalDeletion1() {
        try {
            // إيقاف المزامنة أولاً
            FirestoreSyncManager.getInstance().stopListening();
            // تسجيل الخروج من Firebase
            FirebaseAuth.getInstance().signOut();
            SecureLicenseManager.getInstance(this).setGuest(false);
            SecureLicenseManager.getInstance(this).setGuestUID("");
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("language", "ar");
            editor.putBoolean("first_run", true);
            editor.apply();
            // مسح الإعدادات المحلية
            SharedPreferences prefs = getSharedPreferences("prefs_uid", MODE_PRIVATE);
            prefs.edit().clear().apply();
            sharedPrefsManager.setFirstSyncComplete(false);

            // إلغاء تفعيل إصدار Firestore
            new VersionManager(this).setFirestoreUser_isAdded(false);

            // تسجيل الخروج من جوجل إذا كان متصلاً
            if (googleAuthHelper != null) {
                googleAuthHelper.signOut(new GoogleAuthHelper.AuthCallback() {
                    @Override
                    public void onSignInProgress(String message) {}

                    @Override
                    public void onSignInSuccess(FirebaseUser user, AuthResult authResult) {}

                    @Override
                    public void onSignInFailure(String error) {}

                    @Override
                    public void onSignOutSuccess() {
                        performLogout();
                    }
                });
            }

            // حذف قاعدة البيانات المحلية
            AppDatabase.databaseWriteExecutor.execute(() -> {
                try {
                    AppDatabase db = AppDatabase.getDatabase(ProfileActivity.this);
                    db.close();

                    // حذف ملفات قاعدة البيانات
                    File databaseFile = getDatabasePath("daftree_database");
                    if (databaseFile.exists()) databaseFile.delete();

                    File databaseWal = getDatabasePath("daftree_database-wal");
                    if (databaseWal.exists()) databaseWal.delete();

                    File databaseShm = getDatabasePath("daftree_database-shm");
                    if (databaseShm.exists()) databaseShm.delete();

                    // حذف الملفات الأخرى المرتبطة بالتطبيق
                    File sharedPrefsDir = new File(getFilesDir().getParent() + "/shared_prefs");
                    if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory()) {
                        File[] prefFiles = sharedPrefsDir.listFiles();
                        if (prefFiles != null) {
                            for (File file : prefFiles) {
                                file.delete();
                            }
                        }
                    }

                    // على الخيط الرئيسي، ننهي التطبيق
                    runOnUiThread(() -> {
                        showLoading(false);
                       // Toast.makeText(ProfileActivity.this, "تم حذف حسابك بنجاح", Toast.LENGTH_LONG).show();
                        // إغلاق جميع الأنشطة وإنهاء التطبيق
                        Intent intent = new Intent(ProfileActivity.this, SplashActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finishAffinity(); // إنهاء جميع الأنشطة

                        // إنهاء عملية التطبيق بالكامل
                        android.os.Process.killProcess(android.os.Process.myPid());
                        System.exit(0);
                    });

                } catch (Exception e) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        showDeletionError("خطأ في حذف البيانات المحلية: " + e.getMessage());
                    });
                }
            });

        } catch (Exception e) {
            showLoading(false);
            showDeletionError("خطأ في عملية الحذف: " + e.getMessage());
        }
    }
    /**
     * إكمال عملية الحذف المحلي بعد نجاح الحذف من السيرفر
     */
    private void completeLocalDeletion() {
        try {
            // إيقاف المزامنة أولاً
            FirestoreSyncManager.getInstance().stopListening();

            // تسجيل الخروج من Firebase
            FirebaseAuth.getInstance().signOut();

            // إعادة تعيين إعدادات الضيف
            SecureLicenseManager secureLicenseManager = SecureLicenseManager.getInstance(this);
            secureLicenseManager.setGuest(false);
            secureLicenseManager.setGuestUID("");

            // مسح جميع الإعدادات المحلية
            clearAllSharedPreferences();

            // إعادة تعيين إعدادات المزامنة
            sharedPrefsManager.setFirstSyncComplete(false);
//            sharedPrefsManager.clearAllSyncData();

            // إلغاء تفعيل إصدار Firestore
            new VersionManager(this).setFirestoreUser_isAdded(false);

            // تسجيل الخروج من جوجل إذا كان متصلاً
            if (googleAuthHelper != null) {
                googleAuthHelper.signOut(new GoogleAuthHelper.AuthCallback() {
                    @Override
                    public void onSignInProgress(String message) {}

                    @Override
                    public void onSignInSuccess(FirebaseUser user, AuthResult authResult) {}

                    @Override
                    public void onSignInFailure(String error) {}

                    @Override
                    public void onSignOutSuccess() {}
                });
            }

            // حذف قاعدة البيانات المحلية بما في ذلك العملات وأنواع الحسابات
            AppDatabase.databaseWriteExecutor.execute(() -> {
                try {
                    AppDatabase db = AppDatabase.getDatabase(ProfileActivity.this);

                    // بدء معاملة لحذف جميع البيانات من جميع الجداول
                    db.runInTransaction(() -> {
                        try {
                            // حذف جميع البيانات من جميع الجداول بالترتيب الصحيح
                            db.transactionDao().deleteGuestData();
                            db.accountDao().deleteGuestData();
                            db.accountTypeDao().deleteGuestData();
                            db.currencyDao().deleteGuestData();

                            Log.d("ProfileActivity", "تم حذف جميع البيانات من قاعدة البيانات المحلية");
                        } catch (Exception e) {
                            Log.e("ProfileActivity", "خطأ في حذف البيانات من الجداول: " + e.getMessage());
                        }
                    });

                    db.close();

                    // حذف ملفات قاعدة البيانات
                    deleteDatabaseFiles();

                    // حذف الملفات الأخرى المرتبطة بالتطبيق
                    deleteAppFiles();

                    // على الخيط الرئيسي، ننهي التطبيق
                    runOnUiThread(() -> {
                        showLoading(false);
                        restartApplication();
                    });

                } catch (Exception e) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        showDeletionError("خطأ في حذف البيانات المحلية: " + e.getMessage());
                    });
                }
            });

        } catch (Exception e) {
            showLoading(false);
            showDeletionError("خطأ في عملية الحذف: " + e.getMessage());
        }
    }
    /**
     * مسح جميع إعدادات SharedPreferences
     */
    private void clearAllSharedPreferences() {
        try {
            // مسح prefs_uid
            SharedPreferences prefsUid = getSharedPreferences("prefs_uid", MODE_PRIVATE);
            prefsUid.edit().clear().apply();

            // مسح AppPrefs
            SharedPreferences appPrefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            appPrefs.edit().clear().apply();

            // مسح sync_preferences
            SharedPreferences syncPrefs = getSharedPreferences("sync_preferences", MODE_PRIVATE);
            syncPrefs.edit().clear().apply();

            // مسح license_prefs إذا كان موجوداً
            SharedPreferences licensePrefs = getSharedPreferences("license_prefs", MODE_PRIVATE);
            licensePrefs.edit().clear().apply();

            // مسح default_preferences إذا كان موجوداً
            SharedPreferences defaultPrefs = getSharedPreferences("default_preferences", MODE_PRIVATE);
            defaultPrefs.edit().clear().apply();

            // إعادة تعيين الإعدادات الأساسية
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("language", "ar");
            editor.putBoolean("first_run", true);
            editor.putBoolean("is_first_launch", true);
            editor.apply();

            Log.d("ProfileActivity", "تم مسح جميع إعدادات SharedPreferences");
        } catch (Exception e) {
            Log.e("ProfileActivity", "خطأ في مسح SharedPreferences: " + e.getMessage());
        }
    }

    /**
     * حذف ملفات قاعدة البيانات
     */
    private void deleteDatabaseFiles() {
        try {
            File databasePath = getDatabasePath("daftree_database");

            // حذف ملفات SQLite الرئيسية
            File[] databaseFiles = {
                    databasePath,
                    new File(databasePath.getParent(), "daftree_database-wal"),
                    new File(databasePath.getParent(), "daftree_database-shm"),
                    new File(databasePath.getParent(), "daftree_database-journal")
            };

            for (File file : databaseFiles) {
                if (file.exists()) {
                    boolean deleted = file.delete();
                    Log.d("ProfileActivity", "حذف ملف " + file.getName() + ": " + (deleted ? "نجح" : "فشل"));
                }
            }
        } catch (Exception e) {
            Log.e("ProfileActivity", "خطأ في حذف ملفات قاعدة البيانات: " + e.getMessage());
        }
    }

    /**
     * حذف ملفات التطبيق الأخرى
     */
    private void deleteAppFiles() {
        try {
            // حذف مجلد SharedPreferences
            File sharedPrefsDir = new File(getFilesDir().getParent() + "/shared_prefs");
            if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory()) {
                File[] prefFiles = sharedPrefsDir.listFiles();
                if (prefFiles != null) {
                    for (File file : prefFiles) {
                        boolean deleted = file.delete();
                        Log.d("ProfileActivity", "حذف ملف " + file.getName() + ": " + (deleted ? "نجح" : "فشل"));
                    }
                }
            }

            // حذف مجلد cache
            File cacheDir = getCacheDir();
            if (cacheDir.exists() && cacheDir.isDirectory()) {
                File[] cacheFiles = cacheDir.listFiles();
                if (cacheFiles != null) {
                    for (File file : cacheFiles) {
                        boolean deleted = file.delete();
                        Log.d("ProfileActivity", "حذف ملف cache " + file.getName() + ": " + (deleted ? "نجح" : "فشل"));
                    }
                }
            }

            // حذف مجلد files (باستثناء الملفات الأساسية)
            File filesDir = getFilesDir();
            if (filesDir.exists() && filesDir.isDirectory()) {
                File[] appFiles = filesDir.listFiles();
                if (appFiles != null) {
                    for (File file : appFiles) {
                        // تجنب حذف المجلدات الأساسية إذا لزم الأمر
                        if (!file.getName().equals("important_folder")) { // تعدل حسب الحاجة
                            boolean deleted = file.delete();
                            Log.d("ProfileActivity", "حذف ملف " + file.getName() + ": " + (deleted ? "نجح" : "فشل"));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ProfileActivity", "خطأ في حذف ملفات التطبيق: " + e.getMessage());
        }
    }

    /**
     * إعادة تشغيل التطبيق
     */
    private void restartApplication() {
        try {
            // إعادة تشغيل التطبيق من البداية
            Intent intent = new Intent(ProfileActivity.this, SplashActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            // إضافة إشارة لإعادة التعيين الكامل
            intent.putExtra("RESET_APP", true);
            intent.putExtra("CLEAR_ALL_DATA", true);

            startActivity(intent);
            finishAffinity(); // إنهاء جميع الأنشطة

            // تأكيد إنهاء العملية
            new Handler().postDelayed(() -> {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);
            }, 1000);

        } catch (Exception e) {
            Log.e("ProfileActivity", "خطأ في إعادة تشغيل التطبيق: " + e.getMessage());

            // طريقة بديلة إذا فشلت الطريقة الأولى
            Intent intent = new Intent(ProfileActivity.this, SplashActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
    }
    private void performLogout() {
        try {
            // إيقاف المزامنة أولاً
            FirestoreSyncManager.getInstance().stopListening();
            FirebaseAuth.getInstance().signOut();
            new VersionManager(this).setFirestoreUser_isAdded(false);

            // مسح الإعدادات المحلية
            SharedPreferences prefs = getSharedPreferences("prefs_uid", MODE_PRIVATE);
            prefs.edit().clear().apply();
            sharedPrefsManager.setFirstSyncComplete(false);

            googleLogoutForcs();

            AppDatabase.databaseWriteExecutor.execute(() -> {
                try {
                    AppDatabase db = AppDatabase.getDatabase(this);

                    // حذف جميع البيانات من الجداول
                    db.runInTransaction(() -> {
                        db.transactionDao().deleteGuestData();
                        db.accountDao().deleteGuestData();
                        db.accountTypeDao().deleteGuestData();
                        db.currencyDao().deleteGuestData();
                    });

                    db.close();

                    // حذف ملفات قاعدة البيانات
                    deleteDatabaseFiles();

                } catch (Exception e) {
                    Log.e("ProfileActivity", "خطأ في حذف البيانات أثناء تسجيل الخروج: " + e.getMessage());
                }
            });

            // إعادة التشغيل
            Intent intent = new Intent(this, SplashActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra("RESET_APP", true);
            startActivity(intent);
            finish();

        } catch (Exception e) {
            Log.e("ProfileActivity", "خطأ في تسجيل الخروج: " + e.getMessage());

            // طريقة بديلة
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }
    /**
     * عرض رسالة خطأ
     */
    private void showDeletionError(String errorMessage) {
        showLoading(false);
        new android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_title))
//                .setMessage(errorMessage)
                .setMessage(getString(R.string.delete_message))
                .setPositiveButton(getString(R.string.retry_again), (dialog, which) -> executeAccountDeletion())
                .setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.dismiss())
                .show();
        Log.e("ProvileActivity", "Error deleting account: " + errorMessage);
    }

    // باقي الدوال تبقى كما هي بدون تغيير
    private void deleteAccount() {
        // تم التعليق لأننا نستخدم الحذف المباشر الآن
        // startActivity(new Intent(this, DeleteAccountActivity.class));
        // finish();
    }

    private void performLogout1() {
        // هذه الدالة تبقى كما هي للاستخدام في其他地方
        FirestoreSyncManager.getInstance().stopListening();
        FirebaseAuth.getInstance().signOut();
        new VersionManager(this).setFirestoreUser_isAdded(false);

        SharedPreferences prefs = getSharedPreferences("prefs_uid", MODE_PRIVATE);
        prefs.edit().clear().apply();
        sharedPrefsManager.setFirstSyncComplete(false);
        googleLogoutForcs();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            db.close();

            File databaseFile = getDatabasePath("daftree_database");
            if (databaseFile.exists()) databaseFile.delete();

            File databaseWal = getDatabasePath("daftree_database-wal");
            if (databaseWal.exists()) databaseWal.delete();

            File databaseShm = getDatabasePath("daftree_database-shm");
            if (databaseShm.exists()) databaseShm.delete();
        });

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void googleLogoutForcs() {
        if (googleAuthHelper != null) {
            googleAuthHelper.signOut(new GoogleAuthHelper.AuthCallback() {
                @Override
                public void onSignInProgress(String message) {}
                @Override
                public void onSignInSuccess(FirebaseUser user, AuthResult authResult) {}
                @Override
                public void onSignInFailure(String error) {}
                @Override
                public void onSignOutSuccess() {}
            });
        }
    }

    private void setupToolbar() {
        binding.toolbarProfile.setNavigationOnClickListener(v -> finish());
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void observeUserProfile() {
        showLoading(true);
        viewModel.getUserProfile().observe(this, user -> {
            showLoading(false);
            if (user != null) {
                currentUserProfile = user;
                binding.profileNameEdittext.setText(user.getName());
                binding.profileCompanyEdittext.setText(user.getCompany());
                binding.profileAddressEdittext.setText(user.getAddress());
                binding.profilePhoneEdittext.setText(user.getPhone());
                binding.profileEmailEdittext.setText(user.getEmail());

                String imagePath = user.getProfileImageUri();
                if (imagePath != null && !imagePath.isEmpty()) {
                    Glide.with(this)
                            .load(new File(imagePath))
                            .placeholder(R.drawable.ic_person)
                            .into(binding.profileImageView);
                }
            } else {
                currentUserProfile = new User();
                if (firebaseUser != null) {
                    binding.profileNameEdittext.setText(firebaseUser.getDisplayName());
                    binding.profileEmailEdittext.setText(firebaseUser.getEmail());
                }
            }
        });
    }

    private void saveUserProfile() {
        String name = binding.profileNameEdittext.getText().toString().trim();
        String company = binding.profileCompanyEdittext.getText().toString().trim();
        String address = binding.profileAddressEdittext.getText().toString().trim();
        String phone = binding.profilePhoneEdittext.getText().toString().trim();
        String email = binding.profileEmailEdittext.getText().toString().trim();
        if (name.isEmpty()) {
            binding.profileNameEdittext.setError(getString(R.string.error_name_required));
            return;
        }

        if (currentUserProfile == null) {
            currentUserProfile = new User();
        }
        String users, uid;
        if (isGuest) {
            users = "guests";
            uid = SecureLicenseManager.getInstance(this).guestUID();
        } else {
            users = "users";
            uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        currentUserProfile.setName(name);
        currentUserProfile.setCompany(company);
        currentUserProfile.setAddress(address);
        currentUserProfile.setPhone(phone);
        currentUserProfile.setEmail(email);
        currentUserProfile.setOwnerUID(uid);

        if (selectedImageUri != null) {
            String imagePath = saveImageToInternalStorage(selectedImageUri);
            if (imagePath != null) {
                currentUserProfile.setProfileImageUri(imagePath);
            }
        }

        viewModel.updateUserProfile(currentUserProfile);
        Toast.makeText(this, "تم حفظ البيانات بنجاح", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void showLoading(boolean isLoading) {
        binding.profileProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.saveProfileButton.setVisibility(isLoading ? View.GONE : View.VISIBLE);
       // binding.deleteProfileButton.setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }

    private String saveImageToInternalStorage(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            File file = new File(getFilesDir(), "profile_image.jpg");
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
            }
            return file.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}