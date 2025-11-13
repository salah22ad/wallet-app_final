package com.hpp.daftree;

import static com.hpp.daftree.helpers.PreferenceHelper.applyLocale;


import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.location.Geocoder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
//import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;

//import com.facebook.CallbackManager;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.User;
import com.hpp.daftree.databinding.ActivityLoginBinding;
import com.hpp.daftree.databinding.DialogChangePasswordBinding;
import com.hpp.daftree.databinding.DialogForgotPasswordBinding;
import com.hpp.daftree.databinding.DialogSyncBinding;
import com.hpp.daftree.dialogs.DeviceManagementDialog;
import com.hpp.daftree.dialogs.LanguageDialog;
import com.hpp.daftree.dialogs.LanguageViewModel;
import com.hpp.daftree.helpers.LanguageHelper;
import com.hpp.daftree.helpers.PreferenceHelper;
import com.hpp.daftree.models.AppLockManager;

import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.database.DeviceInfo;
import com.hpp.daftree.models.SnackbarHelper;
import com.hpp.daftree.syncmanagers.FirestoreRestoreHelper;
import com.hpp.daftree.syncmanagers.SyncPreferences;
import com.hpp.daftree.ui.BaseActivity;
import com.hpp.daftree.utils.GoogleAuthHelper;
import com.hpp.daftree.utils.GoogleDriveHelper;
import com.hpp.daftree.utils.LicenseManager;
import com.hpp.daftree.utils.ReferralManager;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.utils.SyncPreferencesLicence;
import com.hpp.daftree.utils.VersionManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.google.android.gms.tasks.OnCompleteListener;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.syncmanagers.FirestoreSyncManager;
//import com.hpp.daftree.ui.RewardWelcomeActivity;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoginActivity extends BaseActivity implements LanguageDialog.OnLanguageSelectedListener {
    private ActivityLoginBinding binding;
    //    private LoginViewModel viewModel;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    //    private CallbackManager facebookCallbackManager;
    private static final String TAG = "LoginActivity";
    private GoogleDriveHelper googleDriveHelper;
    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private FirebaseAuth firebaseAuth;
    private FirebaseFunctions firebaseFunctions;
    private GoogleSignInClient mGoogleSignInClient;
    private static final int STORAGE_PERMISSION_CODE = 1001;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 201;
    private SyncPreferences sharedPreferences;
    private GoogleAuthHelper googleAuthHelper;
    private LicenseManager licenseManager;
    private ReferralManager referralManager;
    private AppDatabase db;
    private FirebaseFirestore firestore;
    private DaftreeRepository repository;
    private String referrerUid;
    private boolean isNewUser = false;
    private boolean isSyncDialogShowing = false;
    private int count = 0;
    private int count2 = 0;
    private View rootView;
    private static final int LOGIN_TIMEOUT = 60000 * 3; // 30 ثانية
    private Handler loginTimeoutHandler;
    private AppLockManager lockManager;
    private LanguageViewModel languageViewModel;
    private ProfileViewModel viewModelUserProfile; // لاستدعاء بيانات المستخدم
    private ProgressDialog progressDialog;
    private boolean isRegistrationInProgress = false;
    private ActivityResultLauncher<Intent> restartMainActivityLauncher;
    private boolean isWaitingForEmailVerification = false;
    private Handler verificationHandler = new Handler();
    private static final int VERIFICATION_CHECK_INTERVAL = 3000; // كل 3 ثوانٍ
    private static final int MAX_VERIFICATION_CHECKS = 200; // أقصى 10 دقائق انتظار
    private int verificationCheckCount = 0;
    private FirebaseUser pendingVerificationUser;
    private ProgressDialog verificationDialog;
    private boolean isEmailVerificationInProgress = false;
    private AlertDialog verificationAlertDialog;
    private CountDownTimer verificationCountDownTimer;
    private boolean isFunctionCallFailed = false;

    public interface UserCheckCallback {
        void onCheckComplete(boolean isDeleted);
    }

    // مشغل لطلب صلاحية الموقع

    private FusedLocationProviderClient fusedLocationClient;
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {

                if (isGranted) {
                    setupLocalCurrencyAndProceed();
                } else {
//                    Toast.makeText(this, "لم يتم منح الإذن، سيتم إضافة العملة الافتراضية.", Toast.LENGTH_LONG).show();
                    SnackbarHelper.showSnackbar(binding.getRoot(), getString(R.string.location_permission_denied), SnackbarHelper.SnackbarType.ERROR);

                    addDefaultCurrencyAndProceed();
                }
            });
    private SharedPreferences prefs, referral_prefs;
    private boolean isFirstRun;

    private boolean isApplyingLanguage = false;
    private String lockType = "";
    FirebaseUser currentUser;
    private boolean isGuest = false;
    private String guestUID = "";
    private boolean isRegisterGuestMode = false;
    private DeviceBanManager deviceBanManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        binding = DataBindingUtil.setContentView(this, R.layout.activity_login);
        applyLocale(this, PreferenceHelper.getLanguage(this));

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        //   binding.getRoot().setBackgroundColor(R.color.primary);  //Color.parseColor("#2196F3"));
        MyApplication.applyGlobalTextWatcher(binding.getRoot());
        rootView = binding.getRoot();
        // تسجيل الضيف
        isRegisterGuestMode = getIntent().getBooleanExtra("registerGuest", false);
        if (isRegisterGuestMode) {
            // إخفاء topContainer و tvGuest
//            binding.topContainer.setVisibility(View.GONE);
            binding.tvGuest.setVisibility(View.GONE);
        }
        deviceBanManager = new DeviceBanManager(this);
        firebaseFunctions = FirebaseFunctions.getInstance();
        setupFacebookLogin();
        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        referral_prefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
        isFirstRun = prefs.getBoolean("first_run", true);
        isNewUser = prefs.getBoolean("isNewUser", false);
        referrerUid = referral_prefs.getString("referrer_uid", "");
        String referrer_Uid =getIntent().getStringExtra("REFERRER_UID");
        if (referrer_Uid != null && !referrer_Uid.isEmpty()) {
            referrerUid= referrer_Uid;
        }

        firebaseAuth = FirebaseAuth.getInstance();
        db = AppDatabase.getDatabase(getApplicationContext());
        repository = new DaftreeRepository(getApplication());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        firestore = FirebaseFirestore.getInstance();
        referralManager = new ReferralManager(this);
        // استخدام GoogleAuthHelper
        licenseManager = new LicenseManager(this);
//        applySavedLanguage();
        firebaseAuth = FirebaseAuth.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        sharedPreferences = new SyncPreferences(this);
        lockManager = new AppLockManager(this);
        viewModelUserProfile = new ViewModelProvider(this).get(ProfileViewModel.class);
        languageViewModel = new ViewModelProvider(this).get(LanguageViewModel.class);


        lockType = lockManager.getLockType();
        googleAuthHelper = new GoogleAuthHelper(this, licenseManager, repository);

        restartMainActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // تم إعادة فتح MainActivity
                    Log.d(TAG, "MainActivity أعيد فتحها بنجاح");
                }
        );
//        new Handler(Looper.getMainLooper()).postDelayed(() -> checkUserSession(), 500);

        executor = ContextCompat.getMainExecutor(this);
        setupDisclaimer();

//        binding.tvGuest.setOnClickListener(v -> {
//            guestUID = UUIDGenerator.generateSequentialUUID().toString();
//            SecureLicenseManager.getInstance(this).setGuest(true);
//            SecureLicenseManager.getInstance(this).setGuestUID(guestUID);
//        });
        isGuest = SecureLicenseManager.getInstance(this).isGuest();

        binding.tvGuest.setOnClickListener(v -> {
            SecureLicenseManager.getInstance(this).setGuest(true);
            isGuest = true;
//            startGuestSession();

            startGuestSession();
        });

        binding.googleSignInButton.setOnClickListener(v -> {
            if (!isNetworkAvailable()) {
                Toast.makeText(this, getString(R.string.no_internet), Toast.LENGTH_SHORT).show();
                return;
            }
//            //showLoading(true);
            loginGoogle();
        });
        binding.btnEmail1.setOnClickListener(v -> toggleFormVisibility("emailEnter"));
        binding.registerTextView.setOnClickListener(v -> {
            toggleFormVisibility("register");
        });
        binding.registerButton.setOnClickListener(v -> validateAndRegisterUser());
        binding.loginTextView.setOnClickListener(v -> toggleFormVisibility("emailEnter"));
        handleIncomingDeepLink(getIntent());

        // Trigger initial app open logic
        binding.loginButton.setOnClickListener(v -> validateAndLoginUser());

        // عند الضغط على نص "أنشئ حساباً"

        binding.forgotPasswordTextView.setOnClickListener(v -> {
            String currentEmail = binding.emailEditText.getText().toString().trim();
            showForgotPasswordDialog(currentEmail);
        });

        handleAppOpen();
    }

    /**
     * ✅ تعديل دالة بدء جلسة الضيف لإضافة الفحص
     */
    private void startGuestSession() {
        Log.d(TAG, "بدء جلسة الضيف");

        // ✅ فحص الحظر قبل بدء الجلسة
        deviceBanManager.checkDeviceBan(new DeviceBanManager.BanCheckListener() {
            @Override
            public void onCheckComplete(boolean isBanned, String reason) {
                runOnUiThread(() -> {
                    if (isBanned) {
                        showDeviceBanDialog(reason);
                        return;
                    }

                    // المتابعة مع جلسة الضيف
                    createNewUserGuest();
                    Log.d(TAG, "اول تشغيل للضيف");
                });
            }

            @Override
            public void onCheckError(String error) {
                runOnUiThread(() -> {
                    // في حالة الخطأ، نسمح بالمتابعة مع عرض تحذير
//                    SnackbarHelper.showSnackbar(binding.getRoot(),
//                            "تحذير: " + error,
//                            SnackbarHelper.SnackbarType.ERROR);
                    createNewUserGuest();
                    Log.d(TAG, "اول تشغيل للضيف");
                });
            }
        });
    }

    /**
     * ✅ عرض ديالوج الحظر
     */
    boolean isDialogeShown = false;

    @SuppressLint("StringFormatInvalid")
    private void showDeviceBanDialog(String banReason) {
        isDialogeShown = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.device_block_title))
                .setMessage(getString(R.string.device_block_message, banReason))
                .setPositiveButton(getString(R.string.exit), (dialog, which) -> {
                    isDialogeShown = false;
                    finishAffinity(); // إغلاق التطبيق completamente
                })
                .setCancelable(false)
                .setIcon(R.drawable.ic_warning)
                .show();
    }
    /**
     * تهيئة جلسة الضيف
     */
    private void initializeGuestSession() {
        updateProgressDialog(getString(R.string.preparing_basic_data));

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // التحقق مما إذا كان هذا أول تشغيل للضيف
            if (isFirstRun) {
                createNewUserGuest();
                Log.d(TAG, "اول تشغيل للضيف");
            } else {
                // إذا لم يكن أول تشغيل، انتقل مباشرة
                guestUID = SecureLicenseManager.getInstance(this).guestUID();
                Log.d(TAG, "ضيف قديم " + " guestUID: " + guestUID);
                proceedToGuestMain();
            }
        }, 1000);
    }

    private void createNewUserGuest() {
        try {
            Log.d(TAG, "start انشاء حساب للضيف");

            updateProgressDialog(getString(R.string.preper_guest_account));
            String guestUid = UUIDGenerator.generateSequentialUUID().toString();
            SecureLicenseManager.getInstance(this).setGuestUID(guestUid);
            guestUID = guestUid;
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            // ✅ التأكد من أن guestUID غير فارغ
            if (TextUtils.isEmpty(guestUID)) {
                guestUID = UUIDGenerator.generateSequentialUUID().toString();
                SecureLicenseManager.getInstance(this).setGuestUID(guestUID);
            }
            Log.d(TAG, "انشاء حساب للضيف" + " guestUID: " + guestUID + " isGuest(): " + isGuest);
            User newUser = new User();
            newUser.setOwnerUID(guestUID);
            newUser.setEmail("");
            newUser.setName(getString(R.string.ar_long_text_20));
            newUser.setCompany(getString(R.string.ar_long_text_20));
            newUser.setAddress(getString(R.string.ar_text_10_1));
            newUser.setPhone("+967 734 249 712");
            newUser.setUserType("guest");
            newUser.setSyncStatus("NEW");
            newUser.setIs_active(true);
            newUser.setLast_login(User.getCurrentLocalDateTime());
            newUser.setIs_premium(false);
            newUser.setCreated_at(User.getCurrentLocalDateTime());
            newUser.setLogin_count(1);
            newUser.setMax_devices(1);
            newUser.setTransactions_count(0);
            newUser.setMax_transactions(LicenseManager.FREE_TRANSACTION_LIMIT);

            DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();
            String deviceId = currentDevice.getDeviceId();
            newUser.setDeviceId(deviceId);
            // ✅ حفظ المستخدم محلياً أولاً في SQLite
            saveGuestUserLocally(newUser);

            // ✅ محاولة الحفظ في Firestore إذا كان هناك اتصال
            showProgressDialog(getString(R.string.preper_guest_account));
            checkLocationPermission();
        } catch (Exception e) {
            Log.e(TAG, "createNewUserGuest Error: " + e);
            // ✅ الاستمرار حتى مع وجود خطأ
//            setupGuestData();
            showProgressDialog(getString(R.string.preper_guest_account));
            checkLocationPermission();
        }
    }

    // ✅ دالة جديدة لحفظ الضيف محلياً في SQLite
    private void saveGuestUserLocally(User guestUser) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // حفظ في جدول users المحلي مع تمييزه كضيف
                db.userDao().upsert(guestUser);
                Log.d(TAG, "تم حفظ المستخدم الضيف محلياً في SQLite: " + guestUser.getOwnerUID());
            } catch (Exception e) {
                Log.e(TAG, "خطأ في حفظ الضيف محلياً: " + e.getMessage());
            }
        });
    }

    // ✅ دالة جديدة للحفظ في Firestore مع معالجة الأخطاء
    private void saveGuestToFirestore1(User guestUser) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        firestore.collection("guests").document(guestUID.trim()).set(guestUser)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "تم إنشاء حساب الضيف في Firestore: " + guestUID);
                        new VersionManager(LoginActivity.this).setFirestoreUser_isAdded(true);
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            try {
                                // حفظ في جدول users المحلي مع تمييزه كضيف

                                guestUser.setSyncStatus("SYNCED");
                                db.userDao().upsert(guestUser);
                                Log.d(TAG, "تم تحديث المستخدم الضيف محلياً في SQLite: " + guestUser.getOwnerUID());
                            } catch (Exception e) {
                                Log.e(TAG, "خطأ في تحديث الضيف محلياً: " + e.getMessage());
                            }
                        });
                    } else {
                        Log.e(TAG, "فشل إضافة الضيف في Firestore، سيتم المزامنة لاحقاً: " + task.getException());

                    }
//                    .
//                    setupGuestData();
                    showProgressDialog(getString(R.string.preper_guest_account));
                    checkLocationPermission();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "فشل إضافة الضيف في Firestore: " + e.getMessage());

//                    setupGuestData();
                    showProgressDialog(getString(R.string.preper_guest_account));
                    checkLocationPermission();
                });
    }


    /**
     * إعداد بيانات الضيف
     */
    private void setupGuestData() {
        updateProgressDialog(getString(R.string.setting_up_guest_data));

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // التحقق من إذن الموقع وإعداد العملة
            checkLocationPermissionForGuest();
        }, 500);
    }

    /**
     * التحقق من إذن الموقع للضيف
     */
    private void checkLocationPermissionForGuest() {
        updateProgressDialog(getString(R.string.checking_location_permission));

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "الإذن ممنوح - جاري تحديد العملة المحلية");
            setupLocalCurrencyForGuest();
        } else {
            Log.d(TAG, "طلب إذن الموقع");
            showLocationPermissionDialogForGuest();
        }
    }

    /**
     * عرض ديالوج إذن الموقع للضيف
     */
    private void showLocationPermissionDialogForGuest() {
        hideProgressDialog(); // إخفاء ProgressDialog مؤقتاً لعرض الديالوج


        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.currency_setup_title))
                .setMessage(getString(R.string.currency_setup_message))
                .setPositiveButton(getString(R.string.use_location), (dialog, which) -> {
                    showProgressDialog(getString(R.string.adding_default_currency));
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
                })
                .setNegativeButton(getString(R.string.skip_location), (dialog, which) -> {
                    showProgressDialog(getString(R.string.adding_default_currency));
                    addDefaultCurrencyForGuest();
                })
                .setCancelable(false)
                .show();

    }

    /**
     * إعداد العملة المحلية للضيف
     */
    @SuppressLint("MissingPermission")
    private void setupLocalCurrencyForGuest() {
        updateProgressDialog(getString(R.string.detecting_local_currency));

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                try {
                    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                    String countryCode = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1).get(0).getCountryCode();
                    Locale locale = new Locale("", countryCode);
                    java.util.Currency currencyInfo = java.util.Currency.getInstance(locale);
                    String currencyName = currencyInfo.getDisplayName(new Locale(savedLanguage));
                    Log.d("GuestCurrency", "العملة المكتشفة: " + currencyName);
                    addCurrencyToDatabaseForGuest(currencyName);
                } catch (Exception e) {
                    Log.e("GuestCurrency", "فشل تحديد العملة", e);
                    addDefaultCurrencyForGuest();
                }
            } else {
                Log.w("GuestCurrency", "الموقع غير متاح");
                addDefaultCurrencyForGuest();
            }
        });
    }

    /**
     * إضافة العملة الافتراضية للضيف
     */
    private void addDefaultCurrencyForGuest() {
        addCurrencyToDatabaseForGuest(getString(R.string.local_currency));
    }

    private void addCurrencyToDatabaseForGuest(String name) {
        Log.d(TAG, "إضافة العملة للضيف: " + name);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // التحقق من عدم وجود عملات مسبقاً
                int currencyCount = db.currencyDao().getCurrencyCount();
                if (currencyCount == 0) {
                    Currency existing = repository.getCurrencyByName(name);
                    if (existing == null) {
                        Currency newCurrency = new Currency();
                        newCurrency.setName(name);
                        newCurrency.setOwnerUID(guestUID);
                        newCurrency.setSyncStatus("NEW");
                        newCurrency.setDefault(true);
                        newCurrency.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
                        newCurrency.setLastModified(System.currentTimeMillis());
                        sharedPreferences.setLocalCurrency(name.trim());
                        MyApplication.defaultCurrencyName = name.trim();
                        db.currencyDao().insert(newCurrency);
                        Log.d(TAG, "تم إضافة العملة: " + name);
                    }
                }

                // التحقق من أنواع الحسابات
                int accountTypeCount = db.accountTypeDao().getAccountTypeCount();
                if (accountTypeCount == 0) {
                    Resources localizedResources = LanguageHelper.getLocalizedResources(this);
                    String[] accountTypes = {
                            localizedResources.getString(R.string.account_type_customer),
                            localizedResources.getString(R.string.account_type_supplier),
                            localizedResources.getString(R.string.account_type_general)
                    };

                    for (String type : accountTypes) {
                        AccountType account_Type = repository.getAccountTypeByName(type);
                        if (account_Type == null) {
                            AccountType accountType = new AccountType();
                            accountType.setName(type);
                            accountType.setOwnerUID(guestUID);
                            accountType.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
                            accountType.setSyncStatus("NEW");
                            accountType.setDefault(true);
                            accountType.setLastModified(System.currentTimeMillis());
                            db.accountTypeDao().insert(accountType);
                            Log.d(TAG, "تم إضافة نوع الحساب: " + type);
                        }
                    }
                }

                // تهيئة المستخدم الافتراضي
                MyApplication.initializeDefaultUser(getApplicationContext(), db);

                runOnUiThread(() -> {
                    hideProgressDialog();
                    Log.d(TAG, "تم إعداد بيانات الضيف بنجاح");
                    proceedToGuestMain();
                });

            } catch (Exception e) {
                Log.e(TAG, "خطأ في إعداد بيانات الضيف", e);
                runOnUiThread(() -> {
                    hideProgressDialog();
                    SnackbarHelper.showSnackbar(binding.getRoot(),
                            "خطأ في إعداد البيانات",
                            SnackbarHelper.SnackbarType.ERROR);
                });
            }
        });
    }

    /**
     * الانتقال إلى الشاشة الرئيسية كضيف
     */
    private void proceedToGuestMain() {
        Log.d(TAG, "الانتقال إلى الشاشة الرئيسية كضيف");

        // تحديث حالة first_run
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("first_run", false);
        editor.apply();

        hideProgressDialog();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void guestUserData() {
        Log.d(TAG, " الضيف");
        if (isFirstRun) {
            Log.d(TAG, "1 الضيف");
            // إذا كان أول تشغيل، ابدأ جلسة الضيف
            startGuestSession();

        } else {

            Log.d(TAG, "2 الضيف");
            // إذا لم يكن أول تشغيل، انتقل مباشرة
            String lockType = lockManager.getLockType();
            if (lockType != null && !lockType.isEmpty()) {
                isAppLocked = true;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    startActivity(new Intent(this, LockScreenActivity.class));
                    finish();
                }, 300);
            } else {
                proceedToGuestMain();
            }

        }
    }

    private void googleLogoutForcs() {
        googleAuthHelper.signOut(new GoogleAuthHelper.AuthCallback() {
            @Override
            public void onSignInProgress(String message) {
            }

            @Override
            public void onSignInSuccess(FirebaseUser user, AuthResult authResult) {
            }

            @Override
            public void onSignInFailure(String error) { /* Not used */ }

            @Override
            public void onSignOutSuccess() {
                // سيتم استدعاء onSignedOut() تلقائيًا من المستمع
//            performLogout();
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleDeepLink(intent);

    }

    private void handleDeepLink(Intent intent) {
        Uri data = intent.getData();
        if (data != null) {
            // معالجة الروابط من scheme daftree

            if ("daftree".equals(data.getScheme()) && "invite".equals(data.getHost())) {
                referrerUid = data.getQueryParameter("ref");
            }
            // معالجة الروابط من HTTPS
            else if ("https".equals(data.getScheme()) && "hpp-daftree.web.app".equals(data.getHost())) {
                referrerUid = data.getQueryParameter("ref");
            }
            // سجّل تفاصيل الرابط لأغراض التصحيح
            Log.d("DeepLink", "الرابط المستلم1: " + data.toString());
            Log.d("DeepLink", "كود الدعوة1: " + referrerUid);

            if (referrerUid != null && !referrerUid.isEmpty()) {
                // حفظ كود الدعوة في SharedPreferences للاستخدام لاحقًا
                SharedPreferences prefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
                prefs.edit().putString("referrer_uid", referrerUid).apply();

            }
        }
    }

    String savedLanguage = "";

    public void handleAppOpen() {


        if (isFirstRun) {
            showLanguageDialog();
        } else {
            Log.e(TAG," isGuest = " + isGuest);
//            isGuest() = SecureLicenseManager.getInstance(this).isGuest()();
            savedLanguage = prefs.getString("language", "ar");
            LanguageHelper.setLocale(this, savedLanguage);
            if (isRegisterGuestMode) {
                toggleFormVisibility("loginOptions");
            } else if (isGuest) {
                guestUserData();
            } else {

                handleAfterLanguageSelection();
            }
        }
    }

    private void showLanguageDialog() {
        LanguageDialog dialog = new LanguageDialog(this, this);
        dialog.show();
    }

    @Override
    public void onLanguageSelected(String languageCode) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("language", languageCode);
        editor.putBoolean("first_run", false);
        editor.apply();
        LanguageHelper.setLocale(this, languageCode);
        recreate();
        toggleFormVisibility("loginOptions");
    }


    //    @Override
//    public void onLanguageConfirmed() {
//        toggleFormVisibility("loginOptions");
//    }

    boolean isDelete = false;

    private void dialogeDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.user_remove_tit))
                .setMessage(getString(R.string.user_remove)).setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    // تنفيذ عملية تسجيل الخروج الكاملة
                    performLogout();
                })
                .setCancelable(false)
                .setIcon(R.drawable.ic_alert)
                .show();
    }

    boolean isAppLocked = false;

    public void handleAfterLanguageSelection_last() {
        binding.getRoot().setBackgroundColor(Color.WHITE);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            if ((isUserDeleted(currentUser.getEmail()))) {
                dialogeDelete();
                toggleFormVisibility("loginOptions");

                isDelete = true;
                return;
            }
        }
        // 1. تحقق مما إذا كان المستخدم مسجلاً دخوله
        if (currentUser == null) {

            // إذا لم يكن مسجلاً، أظهر خيارات الدخول
            toggleFormVisibility("loginOptions");
            // تحقق من وجود رابط دعوة
            if (referrerUid != null) {
                referralManager.saveReferrerUid(referrerUid);
                //  handleIncomingDeepLink(getIntent());
            }
            return;
        }
        if (isNewUser) {
            if (referrerUid != null) {
                referralManager.saveReferrerUid(referrerUid);
                referralManager.applyReferralRewardIfAvailable(currentUser.getUid());
                SharedPreferences prefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
                prefs.edit().remove("referrer_uid").apply();

            }
            checkLocationPermission();
            return;
        }
        String lockType = lockManager.getLockType();
        if (lockType != null && !lockType.isEmpty()) {


            isAppLocked = true;

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivity(new Intent(this, LockScreenActivity.class));
                finish();
            }, 300);
            // إذا كان هناك قفل، أظهر شاشة القفل المناسبة
//            checkLockScreen();
        } else {
            // 3. إذا لم يكن هناك قفل، انتقل مباشرة إلى الشاشة الرئيسية
            Intent mainIntent = new Intent(this, MainActivity.class);
            if (referrerUid != null) {
                // إذا كان هناك كود دعوة، نعرض رسالة أن المستخدم مسجل مسبقًا
                mainIntent.putExtra("REFERRER_UID", referrerUid);
                mainIntent.putExtra("SHOW_ALREADY_REGISTERED", true);
            }
            startActivity(mainIntent);
            finish();
        }
    }


    public void onEmailLoginClick() {
        toggleFormVisibility("emailEnter");
    }

    public void onSignupClick() {
        toggleFormVisibility("register");
    }

    public void onBackToLoginOptions() {
        toggleFormVisibility("loginOptions");
    }

    private boolean isLoginOptionsVisible = true;
    private long backPressedTime = 0;

//    @Override
//    public void onBackPressed() {
//        if (!isAppLocked) {
//            onBackToLoginOptions();
//        }
//        // إذا كانت مخفية، التحقق إذا كان الضغط مزدوج للخروج
//        if (backPressedTime + 2000 > System.currentTimeMillis()) {
//            super.onBackPressed();
////            finish();
//        } else {
    ////            Toast.makeText(this, "اضغط مرة أخرى للخروج", Toast.LENGTH_SHORT).show();
//        }
//        backPressedTime = System.currentTimeMillis();
//
//    }
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Log.d(TAG, "زر الرجوع مُضغط - محاولة العودة لـ MainActivity");
        if(!isLoginOptions){
            onBackToLoginOptions();
            isLoginOptions = true;
            return;
        }
        // 🔥 التحقق من وجود MainActivity في الـ Back Stack
        boolean hasMainActivity = isMainActivityInBackStack();

        if (hasMainActivity) {
            // العودة إلى MainActivity الموجودة
            Log.d(TAG, "العودة إلى MainActivity الموجودة");
            navigateToMainActivity();
        } else {
            // إنشاء MainActivity جديدة والعودة إليها
            Log.d(TAG, "إنشاء MainActivity جديدة للعودة");
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }

        // إنهاء LoginActivity
        finish();
    }

    /**
     * 🔥 التحقق من وجود MainActivity في الـ Back Stack
     */
    private boolean isMainActivityInBackStack() {
        android.app.ActivityManager activityManager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<android.app.ActivityManager.RunningTaskInfo> runningTasks = activityManager.getRunningTasks(Integer.MAX_VALUE);

        for (android.app.ActivityManager.RunningTaskInfo task : runningTasks) {
            if (task.topActivity.getClassName().equals(MainActivity.class.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 🔥 العودة إلى MainActivity بشكل آمن
     */
    private void navigateToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    boolean isLoginOptions = true;
    private void toggleFormVisibility(String form) {
        binding.topContainer.setVisibility(View.VISIBLE); // Always show top unless in specific modes
        binding.loginOptions.setVisibility(View.GONE);
        binding.emailEnterForm.setVisibility(View.GONE);
        binding.registerForm.setVisibility(View.GONE);
        binding.getRoot().setBackgroundColor(Color.WHITE); // Default natural background

        switch (form) {
            case "initial":
                binding.getRoot().setBackgroundColor(Color.parseColor("#2196F3"));
                hideAllExceptTopContainer();
                break;
            case "loginOptions":
                showLoginOptions();
                isLoginOptions = true;
                break;
            case "emailEnter":
                isLoginOptions = false;
                binding.emailEnterForm.setVisibility(View.VISIBLE);
                // binding.topContainer.setVisibility(View.GONE);
                break;
            case "register":
                isLoginOptions = false;
                binding.registerForm.setVisibility(View.VISIBLE);
                // binding.topContainer.setVisibility(View.GONE);
                break;
            case "lock":
                // binding.topContainer.setVisibility(View.GONE);
                break;
        }
    }

    private void hideAllExceptTopContainer() {
        binding.topContainer.setVisibility(View.VISIBLE);
        binding.loginOptions.setVisibility(View.GONE);
        binding.emailEnterForm.setVisibility(View.GONE);
        binding.registerForm.setVisibility(View.GONE);
        fadeIn(binding.icLogo);
        fadeIn(binding.tvWelcome);
        fadeIn(binding.tvAppName);
    }

    private void showLoginOptions() {
        binding.getRoot().setBackgroundColor(Color.WHITE);
        binding.loginOptions.setVisibility(View.VISIBLE);
    }

    public void requestSignIn(Intent signInIntent, ActivityResultLauncher<Intent> launcher) {
        launcher.launch(signInIntent);
    }


    private void fadeIn(View view) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
        animator.setDuration(800);
        animator.start();
    }

    private void setupDisclaimer() {
        String text = getString(R.string.agreement_text);
        SpannableString spannable = new SpannableString(text);

        ClickableSpan termsSpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://hpp-daftree.web.app/terms.html")));
            }
        };
        ClickableSpan privacySpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://hpp-daftree.web.app/privacy.html")));
            }
        };

        int termsStart = text.indexOf(getString(R.string.terms_cond));
        int termsEnd = termsStart + (getString(R.string.terms_cond)).length();
        spannable.setSpan(termsSpan, termsStart, termsEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);// يشير الخطا الى هنا

        int privacyStart = text.indexOf(getString(R.string.privacy_cond));
        int privacyEnd = privacyStart + (getString(R.string.privacy_cond)).length();
        spannable.setSpan(privacySpan, privacyStart, privacyEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        binding.tvAgreement.setText(spannable);
        binding.tvAgreement.setMovementMethod(LinkMovementMethod.getInstance());
    }


    private void checkLocationPermission() {

        Log.d(TAG, getString(R.string.checking_location_permission));
        updateProgressDialog(getString(R.string.checking_location_permission));
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, getString(R.string.detecting_local_currency));

            updateProgressDialog(getString(R.string.detecting_local_currency));
            setupLocalCurrencyAndProceed();
        } else {
            Log.d(TAG, getString(R.string.requesting_location_permission));
            updateProgressDialog(getString(R.string.requesting_location_permission));
            showLocationPermissionExplanation();

        }
    }

    private void showLocationPermissionExplanation() {
        hideProgressDialog();
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.currency_setup_title))
                .setMessage(getString(R.string.currency_setup_message))
                .setPositiveButton(getString(R.string.use_location), (dialog, which) ->{

                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
                })
                .setNegativeButton(getString(R.string.skip_location), (dialog, which) ->
                        addDefaultCurrencyAndProceed())
                .setCancelable(false)
                .show();
    }

    @SuppressLint("MissingPermission")
    private void setupLocalCurrencyAndProceed() {
        Log.d(TAG, getString(R.string.detecting_local_currency));
        updateProgressDialog(getString(R.string.detecting_local_currency));
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                try {
                    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                    String countryCode = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1).get(0).getCountryCode();
                    Locale locale = new Locale("", countryCode);
                    java.util.Currency currencyInfo = java.util.Currency.getInstance(locale);
                    String currencyName = currencyInfo.getDisplayName(new Locale(savedLanguage));
                    String currencyCode = currencyInfo.getCurrencyCode();
                    Log.d("LocalCurrency", "العملة المكتشفة: " + "language: " + savedLanguage + "currencyName: " + currencyName + "countryCode: " + countryCode);
                    addCurrencyToDatabase(currencyName);
                } catch (Exception e) {
                    Log.e("LocalCurrency", "فشل تحديد العملة، سيتم استخدام الافتراضية.", e);
                    addDefaultCurrencyAndProceed();
                }
            } else {
                Log.w("LocalCurrency", "الموقع غير متاح، سيتم استخدام العملة الافتراضية.");
                addDefaultCurrencyAndProceed();
            }
        });
    }

    private void addDefaultCurrencyAndProceed() {
        addCurrencyToDatabase(getString(R.string.local_currency));
    }

    private void addCurrencyToDatabase(String name) {
        String uid;
        if (isGuest) {
            uid = guestUID;
        } else {
            uid = FirebaseAuth.getInstance().getUid();
        }
        Log.d(TAG, "إضافة العمله");
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int currencyCount = db.currencyDao().getCurrencyCount();
            int accTypeCount = db.accountTypeDao().getAccountTypeCount();
//            if (currencyCount > 0) {
//                Log.d(TAG, "تم إضافة العملة مسبقاً، تخطي الإضافة");
//                runOnUiThread(() -> {
//                    //showLoading(false);
//                    navigateToMainActivity(true);
//                });
//                return;
//            }

            if (db.currencyDao().getCurrencyCount() < 1) {
                Currency existing = repository.getCurrencyByName(name);
                Log.e(TAG, "defaultCurrencyName: " + name);
                if (existing == null) {
                    Currency newCurrency = new Currency();
                    newCurrency.setName(name);
                    newCurrency.setOwnerUID(uid);
                    newCurrency.setSyncStatus("NEW");
                    newCurrency.setDefault(true);
                    newCurrency.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
                    newCurrency.setLastModified(System.currentTimeMillis());
                    sharedPreferences.setLocalCurrency(name.trim());
                    MyApplication.defaultCurrencyName = (name.trim());
                    db.currencyDao().insert(newCurrency);
                }
                Resources localizedResources = LanguageHelper.getLocalizedResources(this);
                String[] accountTypes = {
                        localizedResources.getString(R.string.account_type_customer),
                        localizedResources.getString(R.string.account_type_supplier),
                        localizedResources.getString(R.string.account_type_general)
                };
                if (accTypeCount > 0) {
                    runOnUiThread(() -> {
                        //showLoading(false);
                        navigateToMainActivity(true);
                    });
                    return;
                }
                for (String type : accountTypes) {
                    AccountType account_Type = repository.getAccountTypeByName(type);
                    if (account_Type == null) {
                        Log.d(TAG, "إضافة انواع الحسابات: " + type);
                        AccountType accountType = new AccountType();
                        accountType.setName(type);
                        accountType.setOwnerUID(uid);
                        accountType.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
                        accountType.setSyncStatus("NEW");
                        accountType.setDefault(true);
                        accountType.setLastModified(System.currentTimeMillis());
                        db.accountTypeDao().insert(accountType);
                    }
                }
            }
            runOnUiThread(() -> {
                MyApplication.initializeDefaultUser(getApplicationContext(), db);
                //showLoading(false);
                Log.d(TAG, "تمت إضافة العملة بنجاح، جاري الانتقال إلى الشاشة الرئيسية...");
                navigateToMainActivity(true);
            });
        });
    }

    private void addCurrencyToDatabaseforGuest(String name) {

        Log.d(TAG, "إضافة العمله");
        String uid = guestUID;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (db.currencyDao().getCurrencyCount() < 1) {
                Currency existing = repository.getCurrencyByName(name);
                Log.e(TAG, "defaultCurrencyName: " + name);
                if (existing == null) {
                    Currency newCurrency = new Currency();
                    newCurrency.setName(name);
                    newCurrency.setOwnerUID(uid);
                    newCurrency.setSyncStatus("NEW");
                    newCurrency.setDefault(true);
                    newCurrency.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
                    newCurrency.setLastModified(System.currentTimeMillis());
                    sharedPreferences.setLocalCurrency(name.trim());
                    MyApplication.defaultCurrencyName = (name.trim());
                    db.currencyDao().insert(newCurrency);
                }
                Resources localizedResources = LanguageHelper.getLocalizedResources(this);
                String[] accountTypes = {
                        localizedResources.getString(R.string.account_type_customer),
                        localizedResources.getString(R.string.account_type_supplier),
                        localizedResources.getString(R.string.account_type_general)
                };

                for (String type : accountTypes) {
                    AccountType account_Type = repository.getAccountTypeByName(type);
                    if (account_Type == null) {
                        Log.d(TAG, "إضافة انواع الحسابات: " + type);
                        AccountType accountType = new AccountType();
                        accountType.setName(type);
                        accountType.setOwnerUID(uid);
                        accountType.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
                        accountType.setSyncStatus("NEW");
                        accountType.setDefault(true);
                        accountType.setLastModified(System.currentTimeMillis());
                        db.accountTypeDao().insert(accountType);
                    }
                }
            }
            runOnUiThread(() -> {
                MyApplication.initializeDefaultUser(getApplicationContext(), db);
                //showLoading(false);
                Log.d(TAG, "تمت إضافة العملة بنجاح، جاري الانتقال إلى الشاشة الرئيسية...");
                navigateToMainActivity(true);
            });
        });
    }

    private void showForgotPasswordDialog(String prefilledEmail) {
        DialogForgotPasswordBinding dialogBinding = DialogForgotPasswordBinding.inflate(LayoutInflater.from(this));

        dialogBinding.emailEditText.setText(prefilledEmail);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.forgot_password_title))
                .setView(dialogBinding.getRoot())
                .setPositiveButton(getString(R.string.forgot_password_send_button), null)
                .setNegativeButton(getString(R.string.forgot_password_cancel_button), (d, w) -> d.dismiss())
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                EditText emailEditText = dialogBinding.emailEditText;
                String email = emailEditText.getText().toString().trim();

                if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this, getString(R.string.error_invalid_email), Toast.LENGTH_SHORT).show();
                    return;
                }

                sendPasswordResetEmail(email, dialog);
            });
        });

        dialog.show();
    }

    private void sendPasswordResetEmail(String email, AlertDialog dialog) {
        firebaseAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, getString(R.string.forgot_password_sent), Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    } else {
                        Toast.makeText(this, getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void navigateToSyncData() {
        hideProgressDialog();
        String uid = FirebaseAuth.getInstance().getUid() != null ?
                FirebaseAuth.getInstance().getUid() : "";
        SyncPreferencesLicence prefs = new SyncPreferencesLicence(this);
        if (uid == null) {
            Log.e(TAG, "المستخدم غير مسجل دخوله، إلغاء المزامنة");
            return;
        }

        SharedPreferences prefs2 = getSharedPreferences("prefs_uid", MODE_PRIVATE);
        prefs2.edit().putString("uid", FirebaseAuth.getInstance().getUid()).apply();

        repository.setUserUID(FirebaseAuth.getInstance().getUid());

        if (!prefs.isFirstSyncComplete()) {
            //showLoading(true);
            AppDatabase.databaseWriteExecutor.execute(() -> {

                runOnUiThread(() -> {
                    if (isNewUser) {
                        addDefaultData(db, this);
                        insertDefaultUser();
                        navigateToMainActivity(true);
                    } else {
                        runOnUiThread(() -> {
                            //showLoading(false);
                            showSyncDialog();
                        });
                    }
                });
            });
        } else {
            //showLoading(false);
//            FirestoreSyncManager syncManager = FirestoreSyncManager.getInstance();
//            syncManager.startListening(new DaftreeRepository(getApplication()),this);
            navigateToMainActivity(false);
        }
    }

    private void insertDefaultUser() {
        db.databaseWriteExecutor.execute(() -> {
            runOnUiThread(() -> {
                MyApplication.initializeDefaultUser(getApplicationContext(), db);
            });
        });
    }

    private void handleIncomingDeepLink(Intent intent) {
        Uri data = intent.getData();
        if (data != null && "daftree".equals(data.getScheme())) {
            referrerUid = data.getQueryParameter("ref");
            // سجّل تفاصيل الرابط لأغراض التصحيح
            Log.d("DeepLink", "الرابط المستلم: " + data.toString());
            Log.d("DeepLink", "كود الدعوة: " + referrerUid);

            if (referrerUid != null && !referrerUid.isEmpty()) {
                Log.d("DeepLink", "تم استقبال دعوة من: " + referrerUid);
                referralManager.saveReferrerUid(referrerUid);
//                if (!googleAuthHelper.isSignedIn()) {
////                    loginGoogle();
//                }
            }
        }
    }

    void addDefaultData(AppDatabase db, Context context) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) uid = "";

        try {
            String finalUid = uid;
            db.runInTransaction(() -> {

                insertAccountType(db, getString(R.string.clients_account_type), finalUid);
                insertAccountType(db, getString(R.string.account_type_supplier), finalUid);
                insertAccountType(db, getString(R.string.account_type_general), finalUid);
            });
        } catch (Exception e) {
            Log.e("AppDatabase", "Error in addDefaultData: " + e.toString());
        }
    }

    private static void insertAccountType(AppDatabase db, String name, String uid) {
        AccountType accountType = new AccountType();
        accountType.name = name;
        accountType.setOwnerUID(uid);
        accountType.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
        accountType.setSyncStatus("NEW");
        accountType.setDefault(true);
        accountType.setLastModified(System.currentTimeMillis());
        db.accountTypeDao().insert(accountType);
    }
    boolean isSyncDialogShow = false;
    private void showSyncDialog() {
        if(isSyncDialogShow) return;
        isSyncDialogShow = true;
        count2 += 1;
        Log.e(TAG, "showSyncDialog Start Counting: " + count2);
        DialogSyncBinding dialogBinding = DialogSyncBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setCancelable(false)
                .create();
        dialog.show();

        FirestoreRestoreHelper restoreHelper = new FirestoreRestoreHelper(this);

        restoreHelper.startRestore(new FirestoreRestoreHelper.RestoreListener() {
            @Override
            public void onProgressUpdate(String message, int progress, int total) {
                runOnUiThread(() -> {
                    dialogBinding.syncMessageTextview.setText(message);
                    dialogBinding.syncProgressbar.setMax(total);
                    dialogBinding.syncProgressbar.setProgress(progress);
                    dialogBinding.syncProgressTextview.setText(progress + "/" + total);
                });
            }

            @Override
            public void onComplete() {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    new SyncPreferencesLicence(LoginActivity.this).setFirstSyncComplete(true);
                    navigateToMainActivity(false);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e("FirestoreRestoreHelper", "Error during restore: " + error);
                    dialog.dismiss();
                    isSyncDialogShowing = false;
                    new AlertDialog.Builder(LoginActivity.this)
                            .setTitle(getString(R.string.sync_title))
                            .setMessage(getString(R.string.error_sync_message, error))
                            .setPositiveButton(getString(R.string.ok), (d, w) -> navigateToMainActivity(false))
                            .show();
                });
            }
        });
    }

    private void showSyncDialog2() {
        count2 += 1;
        Log.e(TAG, "showSyncDialog Start Counting: " + count2);

        // ⭐ إصلاح: التحقق من اتصال الإنترنت قبل البدء
        if (!isNetworkAvailable()) {
            SnackbarHelper.showSnackbar(binding.getRoot(),
                    getString(R.string.no_internet),
                    SnackbarHelper.SnackbarType.ERROR);
            navigateToMainActivity(false);
            return;
        }

        DialogSyncBinding dialogBinding = DialogSyncBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setCancelable(false)
                .create();
        dialog.show();

        FirestoreRestoreHelper restoreHelper = new FirestoreRestoreHelper(this);

        restoreHelper.startRestore(new FirestoreRestoreHelper.RestoreListener() {
            @Override
            public void onProgressUpdate(String message, int progress, int total) {
                runOnUiThread(() -> {
                    dialogBinding.syncMessageTextview.setText(message);
                    dialogBinding.syncProgressbar.setMax(total);
                    dialogBinding.syncProgressbar.setProgress(progress);
                    dialogBinding.syncProgressTextview.setText(progress + "/" + total);
                });
            }

            @Override
            public void onComplete() {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    recreate();
                    new SyncPreferencesLicence(LoginActivity.this).setFirstSyncComplete(true);
                    navigateToMainActivity(false);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e("FirestoreRestoreHelper", "Error during restore: " + error);
                    dialog.dismiss();
                    isSyncDialogShowing = false;

                    // ⭐ إصلاح: تحسين التعامل مع الأخطاء
                    if (error.contains("فشل في جلب") || error.contains("اتصال")) {
                        new AlertDialog.Builder(LoginActivity.this)
                                .setTitle(getString(R.string.connection_issue))
                                .setMessage(getString(R.string.sync_connection_error))
                                .setPositiveButton(getText(R.string.continue_button), (d, w) -> navigateToMainActivity(false))
                                .show();
                    } else {
                        new AlertDialog.Builder(LoginActivity.this)
                                .setTitle(getString(R.string.sync_title))
                                .setMessage("حدث خطأ أثناء المزامنة: " + error + "\nسيتم استخدام البيانات المحلية.")
                                .setPositiveButton(getString(R.string.ok), (d, w) -> navigateToMainActivity(false))
                                .show();
                    }
                });
            }
        });
    }

    private void navigateToMainActivity(boolean showWelcomeBanner) {
        // إلغاء أي عمليات معلقة أولاً
        cancelLoginTimeout();
        hideProgressDialog();

        // التأكد من إزالة جميع المراقبين
        if (viewModelUserProfile.getUserProfile().hasObservers()) {
            viewModelUserProfile.getUserProfile().removeObservers(this);
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        if (showWelcomeBanner) {
            intent.putExtra("SHOW_WELCOME_BANNER", true);
        }
        if (isRegisterGuestMode ) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("isNewUser", false);
                editor.apply();
                SecureLicenseManager.getInstance(this).setGuest(false);
                isGuest = (false);
                intent.putExtra("FORCE_REFRESH", true);
//                startActivity(intent);
                restartAppFromSplash();
//            restartMainActivity();
                isOldUser = false;

            }, 100);

        }else if (isGuest){
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("isNewUser", false);
                editor.apply();
//                startActivity(intent);
                restartAppFromSplash();

            }, 100);
        }else{
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("isNewUser", false);
                editor.apply();
                SecureLicenseManager.getInstance(this).setGuest(false);
                isGuest = (false);
                intent.putExtra("FORCE_REFRESH", true);
//                startActivity(intent);
                restartAppFromSplash();
//            restartMainActivity();
                isOldUser = false;

            }, 100);
        }
    }
    /**
     * إعادة تشغيل التطبيق من SplashActivity
     */
    private void restartAppFromSplash() {
        Log.d(TAG, "إعادة تشغيل التطبيق مع إعادة تهيئة اتصال Room");

        // 1. إغلاق اتصال Room الحالي فقط (بدون حذف البيانات)
        closeRoomConnection();

        // 2. الانتظار ثم إعادة التشغيل
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(this, SplashActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            isOldUser = false;
            finishAffinity();

            // 3. قتل العملية لضمان إعادة التهيئة الكاملة
            android.os.Process.killProcess(android.os.Process.myPid());
        }, 1000);
    }

    private void closeRoomConnection() {
        try {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                AppDatabase db = AppDatabase.getDatabase(this);
                if (db != null) {
                    db.close();
                    Log.d(TAG, "تم إغلاق اتصال Room بنجاح - البيانات محفوظة");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "خطأ في إغلاق اتصال Room: " + e.getMessage());
        }
    }
    private void restartMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("RESTART_MAIN", true);
        startActivity(intent);
        finish();
    }

    private void proceedToMainActivity(boolean showWelcomeBanner) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        if (showWelcomeBanner) {
            intent.putExtra("SHOW_WELCOME_BANNER", true);
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("isNewUser", false);
        editor.apply();

        startActivity(intent);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // إلغاء البصمة عند مغادرة الشاشة
        if (biometricPrompt != null) {
            try {
                biometricPrompt.cancelAuthentication();
            } catch (Exception e) {
                Log.e(TAG, "Error canceling biometric authentication", e);
            }
        }
    }


    private void showLoading1(boolean isLoading) {
        if (isLoading) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.loginButton.setEnabled(false);
            binding.googleSignInButton.setEnabled(false);
        } else {
            binding.progressBar.setVisibility(View.GONE);
            binding.loginButton.setEnabled(true);
            binding.googleSignInButton.setEnabled(true);
        }
    }

    // باقي الدوال المساعدة...
    private void checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                    startActivityForResult(intent, STORAGE_PERMISSION_REQUEST_CODE);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, STORAGE_PERMISSION_REQUEST_CODE);
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeDatabase();
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    new AlertDialog.Builder(this)
                            .setTitle("الإذن مطلوب")
                            .setMessage("يجب منح إذن التخزين لحفظ البيانات")
                            .setPositiveButton("موافق", (dialog, which) -> requestStoragePermission())
                            .setNegativeButton("إلغاء", null)
                            .show();
                } else {
                    Toast.makeText(this, "تم رفض الإذن بشكل دائم. الرجاء تمكينه من إعدادات التطبيق", Toast.LENGTH_LONG).show();

                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                }
            }
        }
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "تم منح صلاحية الإشعارات.");
            } else {
                Log.w(TAG, "تم رفض صلاحية الإشعارات من المستخدم.");
                // يمكنك إظهار Toast أو Snackbar هنا لإبلاغه.
            }
        }
    }

    private void requestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            initializeDatabase();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
        }
    }

    private void performDeviceLicenseCheck(FirebaseUser user) {
        Log.d(TAG, "بدء فحص ترخيص الجهاز للمستخدم: " + user.getEmail());

        updateProgressDialog(getString(R.string.checking_device_license));

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        DocumentReference userDocRef = firestore.collection("users").document(user.getUid());
        VersionManager versionManager = new VersionManager(this);

        if (isNewUser) {
            startLicenseCheck(user);
            return;
        }

        userDocRef.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.e(TAG, "فشل الوصول لمستند المستخدم: " + task.getException());
                hideProgressDialog();
                SnackbarHelper.showSnackbar(binding.getRoot(), getString(R.string.error_access_user_doc), SnackbarHelper.SnackbarType.ERROR);
                return;
            }

            if (!task.getResult().exists()) {
                if ((!versionManager.getFirestoreUser_isAdded()) && !isNewUser) {
                    createNewUser(userDocRef, user);
                    new VersionManager(this).setFirst_upgrade(false);
                    return;
                } else {
                    startLicenseCheck(user);
                }
                return;
            }
            startLicenseCheck(user);
        });
    }

    private void createNewUser(DocumentReference userRef, FirebaseUser firebaseUser) {
        try {
            LiveData<User> userLiveData = repository.getUserProfile();
            userLiveData.observeForever(new Observer<User>() {
                @Override
                public void onChanged(User localUser) {
                    userLiveData.removeObserver(this);
                    User newUser = new User();
                    newUser.setOwnerUID(firebaseUser.getUid());
                    newUser.setEmail(firebaseUser.getEmail());
                    // Use local data if available, otherwise use Firebase data
                    if (localUser != null) {
                        newUser.setName(localUser.getName());
                        newUser.setAddress(localUser.getAddress());
                        newUser.setCompany(localUser.getCompany());
                        newUser.setPhone(localUser.getPhone());
                    } else {
                        newUser.setName(firebaseUser.getDisplayName());
                        // Set default values for other fields
                        newUser.setName(getString(R.string.ar_long_text_20));
                        newUser.setCompany(getString(R.string.ar_long_text_20));
                        newUser.setAddress(getString(R.string.ar_text_10_1));
                        newUser.setPhone(getString(R.string.string_967_734_249_712));
                    }
                    newUser.setUserType("user");
                    newUser.setSuccessfulReferrals(0);
                    newUser.setIs_active(true);
                    newUser.setIs_premium(false);
                    newUser.setCreated_at(User.getCurrentLocalDateTime());
                    newUser.setLogin_count(1);
                    newUser.setDb_upgrade(1);
                    newUser.setMax_devices(LicenseManager.MAX_DEVICES);
                    newUser.setTransactions_count(0);
                    newUser.setMax_transactions(LicenseManager.FREE_TRANSACTION_LIMIT);
                    newUser.setAd_rewards(0);
                    newUser.setReferral_rewards(0);

                    DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();
                    newUser.getDevices().put(currentDevice.getDeviceId(), currentDevice);

                    userRef.set(newUser).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
//                           repository.updateUser(newUser);
//                           referralManager.applyReferralRewardIfAvailable(firebaseUser.getUid());
                            startLicenseCheck(firebaseUser);
                            new VersionManager(LoginActivity.this).setFirestoreUser_isAdded(true);
                        } else {

                        }
                    });
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "createNewUserData Error: " + e);
        }
    }


    boolean isOldUser = false;

    private void startLicenseCheck(FirebaseUser user) {
        licenseManager.checkLicense().thenAccept(result -> {
            runOnUiThread(() -> {
                Log.d(TAG, "نتيجة فحص الترخيص: " + result.getMessage());
                Log.d(TAG, "تم تجاوز الحد: " + result.isDeviceLimitExceeded());
                Log.d(TAG, "الجهاز مرخص: " + result.isCurrentDeviceAuthorized());

                if (result.isSuccess()) {
                    if (result.isCurrentDeviceAuthorized()) {
                        Log.d(TAG, "الجهاز مرخص، المتابعة إلى الشاشة الرئيسية");
                        if (isNewUser) {
                            if (referrerUid != null && !referrerUid.isEmpty()) {
                                referralManager.applyReferralRewardIfAvailable(user.getUid());
                                SharedPreferences prefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
                                prefs.edit().remove("referrer_uid").apply();
                                checkLocationPermission();
                                SnackbarHelper.showSnackbar(binding.getRoot(), getString(R.string.reward_message), SnackbarHelper.SnackbarType.SUCCESS);

                            }
                        } else {
                            if (isRegisterGuestMode && isGuest) {
                                isOldUser = true;
                                deleteDatabaseCompletely().thenAccept(success -> {
                                    runOnUiThread(() -> {
                                        if (success) {
                                            Log.d(TAG, "تم حذف قاعدة البيانات بنجاح، جاري تحويل الحساب...");

                                            if (!guestUID.isEmpty()) {
                                                deleteGuestAccountsWithSameDevice();
                                            }

                                            // ⭐ إعادة إنشاء اتصال قاعدة البيانات بعد الحذف
                                            AppDatabase newDb = AppDatabase.getDatabase(getApplicationContext());
                                            repository.setUserUID(user.getUid());

                                            // ⭐ إعادة تعيين حالة المزامنة
                                            new SyncPreferencesLicence(LoginActivity.this).setFirstSyncComplete(false);

                                            // ⭐ الانتظار قليلاً لضمان اكتمال التهيئة
                                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                                SecureLicenseManager.getInstance(LoginActivity.this).setGuest(false);
                                                isGuest = false;
                                                hideProgressDialog();
                                                navigateToSyncData();
                                            }, 1000);

                                        } else {
                                            hideProgressDialog();
                                            SnackbarHelper.showSnackbar(binding.getRoot(),
                                                    "فشل في مسح البيانات القديمة، يرجى إعادة المحاولة",
                                                    SnackbarHelper.SnackbarType.ERROR);
                                        }
                                    });
                                }).exceptionally(throwable -> {
                                    runOnUiThread(() -> {
                                        hideProgressDialog();
                                        Log.e(TAG, "خطأ في حذف قاعدة البيانات: " + throwable.getMessage());
                                        SnackbarHelper.showSnackbar(binding.getRoot(),
                                                "خطأ في تحويل الحساب: " + throwable.getMessage(),
                                                SnackbarHelper.SnackbarType.ERROR);
                                    });
                                    return null;
                                });

                            } else {
                                navigateToSyncData();
                            }
                        }
                    } else if (result.isDeviceLimitExceeded()) {
                        Log.e(TAG, "DeviceLimitExceeded: " + result.isDeviceLimitExceeded());
                        //showLoading(false);
                        SnackbarHelper.showSnackbar(binding.getRoot(), getString(R.string.device_limit_exceeded), SnackbarHelper.SnackbarType.ERROR);
                        showDeviceManagementDialog(result.getUser());
                    } else {
                        Log.d(TAG, "هناك مساحة لإضافة الجهاز - جاري إضافة الجهاز");
                        addCurrentDeviceToUser(user.getUid(), result.getUser());
                    }
                } else {
                    //showLoading(false);
                    SnackbarHelper.showSnackbar(binding.getRoot(), getString(R.string.error_add_device, result.getMessage()), SnackbarHelper.SnackbarType.ERROR);
                }
            });
        });
    }

    private void deleteDatabaseCompletely1() {
        try {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                AppDatabase db = AppDatabase.getDatabase(this);
//                db.currencyDao().deleteGuestData();
//                db.accountTypeDao().deleteGuestData();
                db.close();

                File databaseFile = getDatabasePath("daftree_database");
                if (databaseFile.exists()) databaseFile.delete();

                File databaseWal = getDatabasePath("daftree_database-wal");
                if (databaseWal.exists()) databaseWal.delete();

                File databaseShm = getDatabasePath("daftree_database-shm");
                if (databaseShm.exists()) databaseShm.delete();
            });
            String[] databaseNames = {
                    "daftree_database",
                    "daftree_database-wal",
                    "daftree_database-shm",
                    "daftree_database-journal"
            };

            for (String dbName : databaseNames) {
                File dbFile = getDatabasePath(dbName);
                if (dbFile.exists()) {
                    boolean deleted = dbFile.delete();
                    Log.d(TAG, "حذف ملف " + dbName + ": " + (deleted ? "نجح" : "فشل"));
                }
            }

            // أيضاً حذف من مجلد databases
            File databasesDir = new File(getApplicationInfo().dataDir + "/databases");
            if (databasesDir.exists() && databasesDir.isDirectory()) {
                File[] files = databasesDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().contains("daftree_database")) {
                            boolean deleted = file.delete();
                            Log.d(TAG, "حذف " + file.getName() + ": " + (deleted ? "نجح" : "فشل"));
                        }
                    }
                }
            }

            Log.d(TAG, "تم حذف قاعدة البيانات بالكامل بنجاح");

        } catch (Exception e) {
            Log.e(TAG, "خطأ في حذف قاعدة البيانات: " + e.getMessage());
            throw new RuntimeException("فشل في حذف قاعدة البيانات", e);
        }
    }
    private CompletableFuture<Boolean> deleteDatabaseCompletely() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // إغلاق اتصال قاعدة البيانات أولاً
                AppDatabase db = AppDatabase.getDatabase(this);
                if (db != null) {
                    db.close();
                }

                // حذف جميع ملفات قاعدة البيانات
                String[] databaseNames = {
                        "daftree_database",
                        "daftree_database-wal",
                        "daftree_database-shm",
                        "daftree_database-journal"
                };

                boolean allDeleted = true;
                for (String dbName : databaseNames) {
                    File dbFile = getDatabasePath(dbName);
                    if (dbFile.exists()) {
                        boolean deleted = dbFile.delete();
                        Log.d(TAG, "حذف ملف " + dbName + ": " + (deleted ? "نجح" : "فشل"));
                        if (!deleted) allDeleted = false;
                    }
                }

                // حذف من مجلد databases
                File databasesDir = new File(getApplicationInfo().dataDir + "/databases");
                if (databasesDir.exists() && databasesDir.isDirectory()) {
                    File[] files = databasesDir.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (file.getName().contains("daftree_database")) {
                                boolean deleted = file.delete();
                                Log.d(TAG, "حذف " + file.getName() + ": " + (deleted ? "نجح" : "فشل"));
                                if (!deleted) allDeleted = false;
                            }
                        }
                    }
                }

                // ⭐ إعادة تعيين جميع المتغيرات الثابتة والمخبأة
                MyApplication.defaultCurrencyName = null;
                sharedPreferences.setLocalCurrency(null);
                Log.d(TAG, "تم حذف قاعدة البيانات بالكامل بنجاح: " + allDeleted);
                future.complete(allDeleted);

            } catch (Exception e) {
                Log.e(TAG, "خطأ في حذف قاعدة البيانات: " + e.getMessage());
                future.completeExceptionally(e);
            }
        });

        return future;
    }
    private void addCurrentDeviceToUser(String userId, User user) {
        DocumentReference userRef = firestore.collection("users").document(userId);
        DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();

        Map<String, Object> deviceData = new HashMap<>();
        deviceData.put("deviceId", currentDevice.getDeviceId());
        deviceData.put("deviceName", currentDevice.getDeviceName());
        deviceData.put("deviceModel", currentDevice.getDeviceModel());
        deviceData.put("androidVersion", currentDevice.getAndroidVersion());
        deviceData.put("registeredAt", DeviceInfo.getCurrentLocalDateTime());
        deviceData.put("lastActiveAt", DeviceInfo.getCurrentLocalDateTime());
        deviceData.put("active", true);

        // إضافة الجهاز إلى قائمة الأجهزة
        Map<String, Object> updates = new HashMap<>();
        updates.put("devices." + currentDevice.getDeviceId(), deviceData);

        userRef.update(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "تم إضافة الجهاز بنجاح");

                // عرض رسالة نجاح باستخدام الكلاس المساعد
                SnackbarHelper.showSnackbar(
                        binding.getRoot(),
                        "تم إضافة الجهاز بنجاح",
                        SnackbarHelper.SnackbarType.SUCCESS
                );

                if (isNewUser) {
                    checkLocationPermission();
                } else {
                    navigateToSyncData();
                }
            } else {
                //showLoading(false);
                Log.e(TAG, "فشل في إضافة الجهاز: " + task.getException().getMessage());

                // استخدام Snackbar من الكلاس المساعد لرسالة الخطأ
                SnackbarHelper.showSnackbar(binding.getRoot(), "فشل في إضافة الجهاز", SnackbarHelper.SnackbarType.ERROR,
                        "إعادة المحاولة", v -> addCurrentDeviceToUser(userId, user)
                );
            }
        });
    }

    private void showDeviceManagementDialog(User user) {
        List<DeviceInfo> devices = new ArrayList<>(user.getDevices().values());
        DeviceManagementDialog dialog = DeviceManagementDialog.newInstance(devices, licenseManager, true);

        dialog.setDialogListener(new DeviceManagementDialog.DialogListener() {
            @Override
            public void onDeviceRemoved() {
                // بعد إزالة جهاز، أعد محاولة إضافة الجهاز الحالي
                Log.d(TAG, "تم إزالة جهاز، جاري إضافة الجهاز الحالي");
                dialog.dismiss();
                addCurrentDeviceToUser(user.getOwnerUID(), user);
            }

            @Override
            public void onDismissed() {
                // إذا قرر المستخدم عدم إزالة أي جهاز، قم بتسجيل الخروج
                Log.d(TAG, "المستخدم قرر عدم إزالة أي جهاز، جاري تسجيل الخروج");
                //showLoading(false);
                //  Toast.makeText(LoginActivity.this, "تم إلغاء تسجيل الدخول.", Toast.LENGTH_SHORT).show();
                Snackbar.make(rootView, "تم إلغاء تسجيل الدخول.", Snackbar.LENGTH_SHORT).show();
                //  Snackbar.make(btnSave, "تم إلغاء عملية الحفظ", Snackbar.LENGTH_SHORT).show();
                performLogout();

            }
        });

        dialog.show(getSupportFragmentManager(), "DeviceManagementDialog");
    }

    private void performLogout() {
        // إيقاف المزامنة
        FirestoreSyncManager.getInstance().stopListening();

        // تسجيل الخروج من Firebase
        FirebaseAuth.getInstance().signOut();

        // مسح SharedPreferences
        SharedPreferences prefs = getSharedPreferences("prefs_uid", MODE_PRIVATE);
        prefs.edit().clear().apply();

        // مسح بيانات الترخيص المحلية
        SharedPreferences licensePrefs = getSharedPreferences("secure_license_prefs", MODE_PRIVATE);
        licensePrefs.edit().clear().apply();

        // مسح معرف الجهاز من التخزين المشفر
        SharedPreferences securePrefs = getSharedPreferences("secure_license_prefs", MODE_PRIVATE);
        securePrefs.edit().remove(LicenseManager.KEY_DEVICE_ID).apply();

        // مسح تفضيلات المزامنة
        sharedPreferences.setFirstSyncComplete(false);

        // مسح بيانات الترخيص المشفرة
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            SharedPreferences encryptedPrefs = EncryptedSharedPreferences.create(
                    "secure_license_prefs",
                    masterKeyAlias,
                    this,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            encryptedPrefs.edit().clear().apply();
        } catch (Exception e) {
            Log.e(TAG, "خطأ في مسح التخزين المشفر", e);
        }
        licenseManager.clearDeviceData();
        // حذف قاعدة البيانات المحلية تمامًا
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(this);
            db.close();

            File databaseFile = getDatabasePath("daftree_database");
            if (databaseFile.exists()) {
                databaseFile.delete();
            }

            File databaseWal = getDatabasePath("daftree_database-wal");
            if (databaseWal.exists()) {
                databaseWal.delete();
            }

            File databaseShm = getDatabasePath("daftree_database-shm");
            if (databaseShm.exists()) {
                databaseShm.delete();
            }
            googleLogoutForcs();
        });

        // الانتقال لشاشة الدخول
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }


    private void initializeDatabase() {
        AppDatabase.getDatabase(this);
    }

    private void startLoginTimeout() {
        loginTimeoutHandler = new Handler(Looper.getMainLooper());
        loginTimeoutHandler.postDelayed(() -> {
            if (isLoadingShown()) {
                handleLoginTimeout();
            }
        }, LOGIN_TIMEOUT);
    }

    private boolean isLoadingShown() {
        return binding.progressBar.getVisibility() == View.VISIBLE;
    }

    private void handleLoginTimeout() {
        //showLoading(false);
        SnackbarHelper.showSnackbar(binding.getRoot(), "انتهت مهلة تسجيل الدخول. يرجى المحاولة مرة أخرى.", SnackbarHelper.SnackbarType.ERROR);

        // إذا كان المستخدم جديدًا، قم بحذف حسابه
//        if (isNewUser && firebaseAuth.getCurrentUser() != null) {
////            deleteNewUserAccount();
//            performLogout();
//        } else {
//            performLogout();
//        }
    }

    private void cancelLoginTimeout() {
        if (loginTimeoutHandler != null) {
            loginTimeoutHandler.removeCallbacksAndMessages(null);
        }
    }

    private void loginGoogle() {
        if (isRegistrationInProgress) {
            return;
        }

        startLoginTimeout();
        showProgressDialog(getString(R.string.connecting_google));

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            googleAuthHelper.signIn(this, new GoogleAuthHelper.AuthCallback() {
                @Override
                public void onSignInProgress(String message) {
                    updateProgressDialog(message);
                }

                @Override
                public void onSignInSuccess(FirebaseUser user, AuthResult authResult) {
                    cancelLoginTimeout();
                    isRegistrationInProgress = false;
                    Log.d(TAG, "تم تسجيل الدخول بنجاح - Google");

                    // ✅ التحقق من الحساب المحذوف قبل التسجيل
                    checkUserDeletionStatusWithFallback(user.getEmail(), new UserCheckCallback() {
                        @Override
                        public void onCheckComplete(boolean isDeleted) {
                            if (isDeleted) {
                                hideProgressDialog();
                                isRegistrationInProgress = false;
                                performLogout();
                                return;
                            }


                            // إذا لم يكن محذوفًا، تابع العملية الطبيعية
                            isNewUser = authResult.getAdditionalUserInfo().isNewUser();
                            if (isNewUser) {
                                Log.d(TAG, "تم تسجيل الحساب الجديد بنجاح - Google");
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putBoolean("isNewUser", true);
                                editor.apply();

                                handleNewUserRegistration(user);
                            } else {
                                performDeviceLicenseCheck(user);
                            }
                        }
                    });

                }

                @Override
                public void onSignInFailure(String error) {
                    cancelLoginTimeout();
                    isRegistrationInProgress = false;
                    hideProgressDialog();
                    SnackbarHelper.showSnackbar(binding.getRoot(), error, SnackbarHelper.SnackbarType.ERROR);
                }

                @Override
                public void onSignOutSuccess() { /* لا يتم استخدامه هنا */ }
            });
        }, 300);
    }

    /**
     * تحويل المستخدم الضيف إلى مستخدم رسمي
     */
    private void convertGuestToOfficialUser(FirebaseUser user, String name, String email) {
        showProgressDialog(getString(R.string.converting_guest_account));

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        String uid = user.getUid();
        guestUID = SecureLicenseManager.getInstance(this).guestUID();
        // إنشاء بيانات المستخدم الرسمي
        User officialUser = new User();
        officialUser.setOwnerUID(uid);
        officialUser.setEmail(email);
        officialUser.setName(name);
        officialUser.setGuestUID(guestUID);
        officialUser.setUserType("user");
        officialUser.setSuccessfulReferrals(0);
        officialUser.setIs_active(true);
        officialUser.setIs_premium(false);
        officialUser.setCreated_at(User.getCurrentLocalDateTime());
        officialUser.setLogin_count(1);
        officialUser.setDb_upgrade(1);
        officialUser.setMax_devices(LicenseManager.MAX_DEVICES);
        officialUser.setTransactions_count(0);
        officialUser.setMax_transactions(LicenseManager.FREE_TRANSACTION_LIMIT);
        officialUser.setAd_rewards(0);
        officialUser.setReferral_rewards(0);
        DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();
        officialUser.getDevices().put(currentDevice.getDeviceId(), currentDevice);

        // حفظ المستخدم في Firestore
        firestore.collection("users").document(uid)
                .set(officialUser)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // حذف بيانات الضيف من مجموعة guests
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            db.accountDao().upgradeToOfficialUser(uid, "NEW");
                            db.accountTypeDao().upgradeToOfficialUser(uid, "NEW");
                            db.currencyDao().upgradeToOfficialUser(uid, "NEW");
                            db.transactionDao().upgradeToOfficialUser(uid, "NEW");
                        });
                        if (!guestUID.isEmpty()) {
                            //  firestore.collection("guests").document(guestUID).delete();
                            deleteGuestAccountsWithSameDevice();

                        }

                        // تحديث حالة الضيف محلياً

                        hideProgressDialog();
                        Toast.makeText(this, getString(R.string.conversion_success), Toast.LENGTH_SHORT).show();
                        repository.setUserUID(user.getUid());
                        repository.triggerSync();
                        SecureLicenseManager.getInstance(this).setGuest(false);
                        isGuest = (false);
                        // الانتقال إلى الشاشة الرئيسية
                        navigateToMainActivity(true);
                    } else {
                        hideProgressDialog();
                        SnackbarHelper.showSnackbar(binding.getRoot(),
                                getString(R.string.conversion_failed),
                                SnackbarHelper.SnackbarType.ERROR);
                    }
                });
    }

    private void validateAndLoginUser() {
        String email = binding.emailEditText.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.setError(getString(R.string.error_invalid_email));
            return;
        } else {
            binding.emailLayout.setError(null);
        }
        if (TextUtils.isEmpty(password)) {
            binding.etPasswordlayout.setError(getString(R.string.error_password_required));
            return;
        } else {
            binding.etPasswordlayout.setError(null);
        }

        // التحقق أولاً مما إذا كان الحساب محذوفًا
        showProgressDialog(getString(R.string.check_regestiration_account));
        checkIfUserDeleted(email, new UserCheckCallback() {
            @Override
            public void onCheckComplete(boolean isDeleted) {
                if (isDeleted) {
                    hideProgressDialog();
                    dialogeDelete();
                    return;
                }

                // إذا لم يكن محذوفًا، تابع تسجيل الدخول
                Log.e(TAG, "validateAndLoginUser: Start sign in firestore");
                //showLoading(true);
                firebaseAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(LoginActivity.this, new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                Log.d(TAG, "تم تسجيل الدخول بنجاح");
                                if (task.isSuccessful()) {
                                    FirebaseUser user = task.getResult().getUser();
                                    AuthResult authResult = task.getResult();
                                    Log.e(TAG, "validateAndLoginUser: success firestore user: " + user);
                                    if (user != null) {
                                        isNewUser = authResult.getAdditionalUserInfo().isNewUser();
                                        if (isNewUser) {
                                            SharedPreferences.Editor editor = prefs.edit();
                                            editor.putBoolean("isNewUser", true);
                                            editor.apply();
                                        }
                                        performDeviceLicenseCheck(user);
                                    }
                                } else {
                                    //showLoading(false);
                                    hideProgressDialog();
                                    SnackbarHelper.showSnackbar(binding.getRoot(), getString(R.string.error_auth_failed), SnackbarHelper.SnackbarType.ERROR);
                                }
                            }
                        });
            }
        });
    }

    public void handleAfterLanguageSelection() {
        binding.getRoot().setBackgroundColor(Color.WHITE);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser != null && !isGuest) {
            // التحقق بشكل غير متزامن مما إذا كان الحساب محذوفًا
            checkIfUserDeleted(currentUser.getEmail(), new UserCheckCallback() {
                @Override
                public void onCheckComplete(boolean isDeleted) {
                    if (isDeleted) {
                        dialogeDelete();
                        toggleFormVisibility("loginOptions");
                        isDelete = true;
                        return;
                    }

                    // إذا لم يكن محذوفًا، تابع العملية الطبيعية
                    continueAfterLanguageSelection(currentUser);
                }
            });
        } else {

            if (isGuest) {
                guestUserData();
            } else {
                continueAfterLanguageSelection(null);
            }
        }
    }

    private void continueAfterLanguageSelection(FirebaseUser currentUser) {
        if (currentUser == null) {
            toggleFormVisibility("loginOptions");
            if (referrerUid != null) {
                referralManager.saveReferrerUid(referrerUid);
            }
            return;
        }

        if (isNewUser) {
            if (referrerUid != null) {
                referralManager.saveReferrerUid(referrerUid);
                referralManager.applyReferralRewardIfAvailable(currentUser.getUid());
                SharedPreferences prefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
                prefs.edit().remove("referrer_uid").apply();
            }
            checkLocationPermission();
            return;
        }

        String lockType = lockManager.getLockType();
        if (lockType != null && !lockType.isEmpty()) {
            isAppLocked = true;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivity(new Intent(this, LockScreenActivity.class));
                finish();
            }, 300);
        } else {
            Intent mainIntent = new Intent(this, MainActivity.class);
            if (referrerUid != null && referrerUid.isEmpty()) {
                mainIntent.putExtra("REFERRER_UID", referrerUid);
                mainIntent.putExtra("SHOW_ALREADY_REGISTERED", true);
            }
            startActivity(mainIntent);
            finish();
        }
    }

    private void deleteUserDataExceptTransactions(String ownerUID) {
        String[] collections = {"transactions", "accounts", "accountTypes", "currencies", "users"};

        for (String collectionName : collections) {
            firestore.collection(collectionName)
                    .whereEqualTo("ownerUID", ownerUID)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (!queryDocumentSnapshots.isEmpty()) {
                            WriteBatch batch = firestore.batch();
                            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                                batch.delete(doc.getReference());
                            }
                            batch.commit()
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d("Firestore", "تم حذف البيانات من: " + collectionName);
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("Firestore", "فشل الحذف من " + collectionName + ": " + e.getMessage());
                                    });
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("Firestore", "خطأ في الاستعلام من " + collectionName + ": " + e.getMessage());
                    });
        }
    }


    private boolean isUserDeleted(String email) {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        AtomicBoolean isDeleted = new AtomicBoolean(false);

        if (firebaseUser == null) {
            return isDeleted.get();
        } else {
            email = firebaseUser.getEmail();
        }
        if (email == null) {
            return isDeleted.get();
        }
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        String finalEmail = email;
        firestore.collection("deletedAccounts")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.e(TAG, "تم ايجاد الحساب: " + finalEmail);
                        QuerySnapshot userSnapshot = task.getResult();
                        if (!userSnapshot.isEmpty()) {
                            deleteUserDataExceptTransactions(firebaseUser.getUid());
                            performLogout();
                            isDeleted.set(true);
                        }
                    } else {
                        Log.e(TAG, "لم يتم ايجاد الحساب: " + finalEmail);
                    }
                });

        return isDeleted.get();
    }

    //--------------------------------------------register------------------------------
    private void validateAndRegisterUser1() {
        String name = binding.nameRegEditText.getText().toString().trim();
        String email = binding.emailRegisterEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();
        String password2 = binding.passwordRenterEditText.getText().toString().trim();
        // (كود التحقق من المدخلات يبقى كما هو)
        if (TextUtils.isEmpty(name)) {
            binding.nameRegLayout.setError(getString(R.string.error_name_required));
            return;
        } else {
            binding.nameRegLayout.setError(null);
        }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailRegisterLayout.setError(getString(R.string.error_invalid_email));
            return;
        } else {
            binding.emailRegisterLayout.setError(null);
        }
        if (password.length() < 6) {
            binding.passwordLayout.setError(getString(R.string.error_password_short));
            return;
        } else {
            binding.passwordLayout.setError(null);
        }
        if (!password.equals(password2)) {
            binding.passwordLayout.setError(getString(R.string.error_password_mismatch));
            binding.passwordRenterLayout.setError(getString(R.string.error_password_mismatch));
            return;
        } else {
            binding.passwordLayout.setError(null);
            binding.passwordRenterLayout.setError(null);
        }

        // إظهار البروجروس وبدء عملية التسجيل
//        //showLoading(true);
        isRegistrationInProgress = true;


        showProgressDialog("جاري التحقق من حالة الحساب...");
        checkUserDeletionStatus(email, new UserCheckCallback() {
            @Override
            public void onCheckComplete(boolean isDeleted) {
                if (isDeleted) {
                    hideProgressDialog();
                    return;
                }
                showProgressDialog(getString(R.string.creating_system_account));
                // إذا لم يكن محذوفًا، تابع إنشاء الحساب
                createUserInAuth(name, email, password);
            }
        });
    }

    private void createUserInAuth1(String name, String email, String password) {
        updateProgressDialog("جاري إنشاء الحساب في النظام...");
        String uid = FirebaseAuth.getInstance().getUid();
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            // ✅ إرسال رسالة التحقق
                            updateProgressDialog(getString(R.string.sending_verification));
                            firebaseUser.sendEmailVerification()
                                    .addOnCompleteListener(verifyTask -> {
                                        if (verifyTask.isSuccessful()) {
                                            Log.d(TAG, "رسالة التحقق أُرسلت.");
                                        }
                                    });

                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putBoolean("isNewUser", true);
                            editor.apply();
                            handleNewUserRegistration(firebaseUser);
                        }

                    } else {
//                        //showLoading(false);
                        isRegistrationInProgress = false;
                        hideProgressDialog();
                        String errorMessage = task.getException() != null ?
                                task.getException().getMessage() : getString(R.string.register_failed);
                        SnackbarHelper.showSnackbar(binding.getRoot(), errorMessage,
                                SnackbarHelper.SnackbarType.ERROR);
                    }
                });
    }
    // دالة محسنة لتسجيل المستخدم الجديد
    private void validateAndRegisterUser2() {
        if (isWaitingForEmailVerification) {
            SnackbarHelper.showSnackbar(binding.getRoot(),
                    "يجب الانتظار حتى اكتمال التحقق من الحساب الحالي",
                    SnackbarHelper.SnackbarType.WARNING);
            return;
        }

        String name = binding.nameRegEditText.getText().toString().trim();
        String email = binding.emailRegisterEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();
        String password2 = binding.passwordRenterEditText.getText().toString().trim();

        // التحقق من المدخلات
        if (TextUtils.isEmpty(name)) {
            binding.nameRegLayout.setError(getString(R.string.error_name_required));
            return;
        } else {
            binding.nameRegLayout.setError(null);
        }

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailRegisterLayout.setError(getString(R.string.error_invalid_email));
            return;
        } else {
            binding.emailRegisterLayout.setError(null);
        }

        if (password.length() < 6) {
            binding.passwordLayout.setError(getString(R.string.error_password_short));
            return;
        } else {
            binding.passwordLayout.setError(null);
        }

        if (!password.equals(password2)) {
            binding.passwordLayout.setError(getString(R.string.error_password_mismatch));
            binding.passwordRenterLayout.setError(getString(R.string.error_password_mismatch));
            return;
        } else {
            binding.passwordLayout.setError(null);
            binding.passwordRenterLayout.setError(null);
        }

        isRegistrationInProgress = true;
        showProgressDialog("جاري التحقق من حالة الحساب...");

        checkUserDeletionStatus(email, new UserCheckCallback() {
            @Override
            public void onCheckComplete(boolean isDeleted) {
                if (isDeleted) {
                    hideProgressDialog();
                    isRegistrationInProgress = false;
                    return;
                }

                showProgressDialog(getString(R.string.creating_system_account));
                createUserInAuth(name, email, password);
            }
        });
    }

    // تعديل دالة createUserInAuth
    private void createUserInAuth(String name, String email, String password) {
        // فحص مزدوج لمنع التنفيذ المتكرر
        if (isRegistrationInProgress || isEmailVerificationInProgress) {
            Log.w(TAG, "محاولة إنشاء حساب أثناء وجود عملية سابقة - تم الرفض");
            return;
        }

        isRegistrationInProgress = true;
        isEmailVerificationInProgress = true;

        updateProgressDialog("جاري إنشاء الحساب في النظام...");

        // تسجيل خروج أي مستخدم موجود مسبقاً
        firebaseAuth.signOut();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                            if (firebaseUser != null) {
                                Log.d(TAG, "تم إنشاء الحساب بنجاح: " + firebaseUser.getUid());
                                updateAuthProfileName(firebaseUser, name, email);
                            } else {
                                handleAuthError("فشل في الحصول على بيانات المستخدم بعد الإنشاء");
                            }
                        } else {
                            isRegistrationInProgress = false;
                            isEmailVerificationInProgress = false;
                            hideProgressDialog();

                            String errorMessage = "فشل في إنشاء الحساب";
                            if (task.getException() != null) {
                                errorMessage = task.getException().getMessage();
                                // معالجة الأخطاء الشائعة
                                if (errorMessage.contains("email already in use")) {
                                    errorMessage = "هذا البريد الإلكتروني مستخدم بالفعل";
                                }
                            }

                            SnackbarHelper.showSnackbar(binding.getRoot(), errorMessage,
                                    SnackbarHelper.SnackbarType.ERROR);
                        }
                    });
        }, 500); // تأخير بسيط لضمان اكتمال تسجيل الخروج
    }

    // دالة مساعدة لمعالجة الأخطاء
    // دالة محسنة لتحديث الملف الشخصي وإرسال التحقق
    private void updateAuthProfileName(FirebaseUser firebaseUser, String name, String email) {
        updateProgressDialog("جاري إعداد الحساب...");

        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build();

        firebaseUser.updateProfile(profileUpdates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // إرسال رسالة التحقق بعد تحديث الاسم
                        sendEmailVerification(firebaseUser, name, email);
                    } else {
                        isRegistrationInProgress = false;
                        hideProgressDialog();
                        SnackbarHelper.showSnackbar(binding.getRoot(),
                                "فشل في إعداد الحساب",
                                SnackbarHelper.SnackbarType.ERROR);
                    }
                });
    }

    // دالة جديدة لإرسال التحقق بالبريد الإلكتروني وبدء المراقبة
    private void sendEmailVerification(FirebaseUser firebaseUser, String name, String email) {
        updateProgressDialog("جاري إرسال رابط التحقق إلى بريدك الإلكتروني...");

        firebaseUser.sendEmailVerification()
                .addOnCompleteListener(verifyTask -> {
                    if (verifyTask.isSuccessful()) {
                        Log.d(TAG, "تم إرسال رابط التحقق بنجاح إلى: " + email);

                        // حفظ حالة المستخدم المنتظر التحقق
                        pendingVerificationUser = firebaseUser;
                        isWaitingForEmailVerification = true;

                        // إخفاء progress dialog العادي وإظهار dialog التحقق
                        hideProgressDialog();
                        showEmailVerificationDialog(name, email);

                        // بدء مراقبة حالة التحقق
                        startEmailVerificationMonitoring();

                    } else {
                        isRegistrationInProgress = false;
                        hideProgressDialog();
                        Log.e(TAG, "فشل إرسال رابط التحقق: " + verifyTask.getException());
                        SnackbarHelper.showSnackbar(binding.getRoot(),
                                "فشل إرسال رابط التحقق: " + (verifyTask.getException() != null ?
                                        verifyTask.getException().getMessage() : "خطأ غير معروف"),
                                SnackbarHelper.SnackbarType.ERROR);
                    }
                });
    }
    // دالة جديدة لعرض ديالوج انتظار التحقق


    // دالة جديدة لبدء مراقبة حالة التحقق
    private void startEmailVerificationMonitoring() {
        verificationCheckCount = 0;
        verificationHandler.postDelayed(verificationRunnable, VERIFICATION_CHECK_INTERVAL);
    }

    // دالة جديدة للتوقف عن مراقبة التحقق
    private void stopEmailVerificationMonitoring() {
        verificationHandler.removeCallbacks(verificationRunnable);
        verificationCheckCount = 0;
    }


    // دالة جديدة للتعامل مع النجاح في التحقق
    private void handleEmailVerifiedSuccessfully() {
        Log.d(TAG, "بدء معالجة التحقق الناجح");

        runOnUiThread(() -> {
            // إيقاف المراقبة أولاً
            stopEmailVerificationMonitoring();

            // إغلاق جميع الـ dialogs
            if (verificationAlertDialog != null && verificationAlertDialog.isShowing()) {
                verificationAlertDialog.dismiss();
                verificationAlertDialog = null;
            }

            if (verificationDialog != null && verificationDialog.isShowing()) {
                verificationDialog.dismiss();
                verificationDialog = null;
            }

            hideProgressDialog();

            // التأكد من أن المستخدم لا يزال موجوداً ومفعل
            FirebaseUser currentUser = firebaseAuth.getCurrentUser();
            if (currentUser == null || !currentUser.isEmailVerified()) {
                Log.e(TAG, "المستخدم غير موجود أو غير مفعل بعد النجاح المفترض");
                SnackbarHelper.showSnackbar(binding.getRoot(),
                        "خطأ في التحقق. يرجى المحاولة مرة أخرى.",
                        SnackbarHelper.SnackbarType.ERROR);
                return;
            }

            // تحديث الحالات
            isRegistrationInProgress = false;
            isEmailVerificationInProgress = false;
            isWaitingForEmailVerification = false;

            // تحديث preferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("isNewUser", true);
            editor.apply();

            Log.d(TAG, "تم التحقق بنجاح، جاري متابعة التسجيل...");

            // متابعة عملية التسجيل
            handleNewUserRegistration(currentUser);
            pendingVerificationUser = null;
        });
    }

    // دالة جديدة لإعادة إرسال رابط التحقق
    private void resendVerificationEmail() {
        if (pendingVerificationUser != null) {
            showProgressDialog("جاري إعادة إرسال رابط التحقق...");

            pendingVerificationUser.sendEmailVerification()
                    .addOnCompleteListener(task -> {
                        hideProgressDialog();

                        if (task.isSuccessful()) {
                            SnackbarHelper.showSnackbar(binding.getRoot(),
                                    "تم إعادة إرسال رابط التحقق بنجاح",
                                    SnackbarHelper.SnackbarType.SUCCESS);

                            // إعادة بدء المراقبة
                            isWaitingForEmailVerification = true;
                            verificationCheckCount = 0;
                            showEmailVerificationDialog(
                                    pendingVerificationUser.getDisplayName(),
                                    pendingVerificationUser.getEmail()
                            );
                            startEmailVerificationMonitoring();

                        } else {
                            SnackbarHelper.showSnackbar(binding.getRoot(),
                                    "فشل إعادة إرسال الرابط: " +
                                            (task.getException() != null ? task.getException().getMessage() : "خطأ غير معروف"),
                                    SnackbarHelper.SnackbarType.ERROR);
                        }
                    });
        }
    }

    // دالة جديدة لإلغاء عملية التحقق
    private void cancelEmailVerification() {
        stopEmailVerificationMonitoring();
        isWaitingForEmailVerification = false;
        isRegistrationInProgress = false;
        pendingVerificationUser = null;

        // تسجيل الخروج من الحساب غير المفعل
        if (firebaseAuth.getCurrentUser() != null) {
            firebaseAuth.signOut();
        }

        // تنظيف الحقول
        binding.emailRegisterEditText.setText("");
        binding.passwordEditText.setText("");
        binding.passwordRenterEditText.setText("");

        SnackbarHelper.showSnackbar(binding.getRoot(),
                "تم إلغاء عملية التسجيل",
                SnackbarHelper.SnackbarType.INFO);
    }


    // تعديل دالة handleNewUserRegistration لإضافة تحقق إضافي
    private void handleNewUserRegistration(FirebaseUser user) {
        // تحقق إضافي من أن البريد مفعل
        if (!user.isEmailVerified()) {
            Log.w(TAG, "تم استدعاء handleNewUserRegistration مع مستخدم غير مفعل!");
            return;
        }

        Log.d(TAG, "بدء إعداد بيانات الحساب الجديد بعد التحقق");

        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("isNewUser", true);
        editor.apply();

        // تطبيق مكافآت الإحالة إذا كانت متاحة
        if (referrerUid != null && !referrerUid.isEmpty()) {
            referralManager.applyReferralRewardIfAvailable(user.getUid());
            SharedPreferences referralPrefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
            referralPrefs.edit().remove("referrer_uid").apply();

            SnackbarHelper.showSnackbar(binding.getRoot(),
                    getString(R.string.reward_message),
                    SnackbarHelper.SnackbarType.SUCCESS);
        }

        // إذا كان في وضع تحويل الضيف
        if (isRegisterGuestMode && isGuest) {
            convertGuestToOfficialUser(user, user.getDisplayName(), user.getEmail());
        } else {
            showProgressDialog(getString(R.string.preparing_account));
            checkLocationPermission();
        }
    }
    private void showEmailVerificationDialog(String name, String email) {
        runOnUiThread(() -> {
            // إخفاء أي dialogs موجودة مسبقاً
            if (verificationAlertDialog != null && verificationAlertDialog.isShowing()) {
                verificationAlertDialog.dismiss();
            }
            if (verificationDialog != null && verificationDialog.isShowing()) {
                verificationDialog.dismiss();
            }

            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_email_verification, null);
            TextView tvEmail = dialogView.findViewById(R.id.tvEmail);
            TextView tvTimer = dialogView.findViewById(R.id.tvTimer);
            Button btnResend = dialogView.findViewById(R.id.btnResend);
            Button btnCancel = dialogView.findViewById(R.id.btnCancel);
            ProgressBar progressBar = dialogView.findViewById(R.id.progressBar);

            tvEmail.setText(email);

            verificationAlertDialog = new MaterialAlertDialogBuilder(this)
                    .setTitle("تفعيل الحساب مطلوب")
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();

            // بدء العد التنازلي
            startVerificationCountdown(tvTimer, btnResend, 120); // 120 ثانية = دقيقتين

            btnResend.setOnClickListener(v -> {
                progressBar.setVisibility(View.VISIBLE);
                btnResend.setEnabled(false);
                resendVerificationEmail();
                new Handler().postDelayed(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnResend.setEnabled(true);
                }, 2000);
            });

            btnCancel.setOnClickListener(v -> {
                cancelEmailVerification();
                verificationAlertDialog.dismiss();
            });

            verificationAlertDialog.show();

            // بدء المراقبة الفورية
            startEmailVerificationMonitoring();
        });
    }

    // دالة العد التنازلي
    private void startVerificationCountdown(TextView tvTimer, Button btnResend, int seconds) {
        new CountDownTimer(seconds * 1000, 1000) {
            public void onTick(long millisUntilFinished) {
                long secondsRemaining = millisUntilFinished / 1000;
                String timeText = String.format(Locale.getDefault(),
                        "سيتم إعادة الإرسال بعد: %d ثانية", secondsRemaining);
                tvTimer.setText(timeText);

                if (secondsRemaining <= 0) {
                    btnResend.setEnabled(true);
                    tvTimer.setText("يمكنك إعادة إرسال الرابط الآن");
                } else {
                    btnResend.setEnabled(false);
                }
            }

            public void onFinish() {
                btnResend.setEnabled(true);
                tvTimer.setText("يمكنك إعادة إرسال الرابط الآن");
            }
        }.start();
    }

    // تحسين دالة checkEmailVerificationStatus بشكل كامل
    private void checkEmailVerificationStatus() {
        runOnUiThread(() -> {
            // فحص شامل لجميع الحالات
            if (pendingVerificationUser == null) {
                Log.e(TAG, "pendingVerificationUser is null - checking current user");

                // محاولة استخدام المستخدم الحالي
                FirebaseUser currentUser = firebaseAuth.getCurrentUser();
                if (currentUser != null && !currentUser.isEmailVerified()) {
                    pendingVerificationUser = currentUser;
                    Log.d(TAG, "تم استعادة المستخدم من firebaseAuth");
                } else {
                    Log.e(TAG, "لا يوجد مستخدم للتحقق - إيقاف المراقبة");
                    stopEmailVerificationMonitoring();
                    return;
                }
            }

            // فحص إضافي للتأكد
            if (pendingVerificationUser == null) {
                Log.e(TAG, "المستخدم لا يزال null - إيقاف المراقبة");
                stopEmailVerificationMonitoring();
                handleVerificationError("فقدان بيانات المستخدم");
                return;
            }

            Log.d(TAG, "جاري التحقق من حالة البريد للمستخدم: " + pendingVerificationUser.getEmail());

            // إعادة تحميل بيانات المستخدم
            pendingVerificationUser.reload().addOnCompleteListener(reloadTask -> {
                if (reloadTask.isSuccessful()) {
                    // الحصول على أحدث بيانات المستخدم
                    FirebaseUser refreshedUser = firebaseAuth.getCurrentUser();
                    if (refreshedUser == null) {
                        Log.e(TAG, "المستخدم أصبح null بعد إعادة التحميل");
                        handleVerificationError("فقدان الاتصال بالمستخدم");
                        return;
                    }

                    // تحديث المرجع
                    pendingVerificationUser = refreshedUser;

                    if (refreshedUser.isEmailVerified()) {
                        Log.d(TAG, "تم التحقق من البريد بنجاح!");
                        handleEmailVerifiedSuccessfully();
                    } else {
                        verificationCheckCount++;
                        Log.d(TAG, "البريد لم يتم التحقق بعد. المحاولة: " + verificationCheckCount);

                        if (verificationCheckCount >= MAX_VERIFICATION_CHECKS) {
                            handleVerificationTimeout();
                        } else {
                            // تحديث واجهة المستخدم
                            updateVerificationProgress();
                            // الاستمرار في المراقبة
                            verificationHandler.postDelayed(verificationRunnable, VERIFICATION_CHECK_INTERVAL);
                        }
                    }
                } else {
                    Log.e(TAG, "فشل إعادة تحميل بيانات المستخدم: " + reloadTask.getException());
                    // إعادة المحاولة بعد فترة
                    verificationHandler.postDelayed(verificationRunnable, VERIFICATION_CHECK_INTERVAL);
                }
            });
        });
    }

    private void showProgressDialog(String message) {
        runOnUiThread(() -> {
            try {
                if (progressDialog == null) {
                    progressDialog = new ProgressDialog(this);
                    progressDialog.setCancelable(false);
                    progressDialog.setCanceledOnTouchOutside(false);
                }
                progressDialog.setMessage(message);
                if (!progressDialog.isShowing()) {
                    progressDialog.show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error showing progress dialog", e);
            }
        });
    }

    private void updateProgressDialog(String message) {
        runOnUiThread(() -> {
            try {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.setMessage(message);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating progress dialog", e);
            }
        });
    }

    private void hideProgressDialog() {
        runOnUiThread(() -> {
            try {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error hiding progress dialog", e);
            }
        });
    }

    private void saveUserToFirestore(FirebaseUser firebaseUser, String name) {
        String uid = FirebaseAuth.getInstance().getUid();
        FirebaseUser user = firebaseAuth.getCurrentUser();
        // ... (الكود الحالي لحفظ المستخدم)
        firestore.collection("users").document(uid).set(user)
                .addOnSuccessListener(aVoid -> {
                    // ✅ بعد النجاح، أظهر ديالوج يطلب من المستخدم التحقق
                    showVerificationDialog(String.valueOf(user.getDisplayName()));
                })
                .addOnFailureListener(e -> {
                    // ...
                });
    }

    private void showVerificationDialog(String name) {
        //showLoading(false);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.verify_email))
                .setMessage(getString(R.string.verify_email_message))
                .setPositiveButton(getString(R.string.understand_action), (dialog, which) -> {
                    // بعد الضغط على موافق، انتقل للشاشة الرئيسية
                    //showLoading(true);
                    updateAuthProfileName(name);
                })
                .setCancelable(false)
                .show();
    }

    private void updateAuthProfileName(String name) {
        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        if (firebaseUser == null) {
            //showLoading(false);
            return;
        }

        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build();

        firebaseUser.updateProfile(profileUpdates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // الخطوة 2: نجح تحديث الاسم
                        // الخطوة 3: الآن أظهر ديالوج إعداد العملة

                    } else {
                        //showLoading(false);
                        Toast.makeText(this, "فشل تحديث الاسم.", Toast.LENGTH_SHORT).show();
                    }
                    checkLocationPermission();
                });
    }

    /// --------------------------------------lockscreen--------------

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        } catch (Exception e) {
            Log.e("NetworkCheck", "خطأ في التحقق من الاتصال: " + e.getMessage());
            return false;
        }
    }

    // ✅ تحسين إعداد تسجيل الدخول بالفيسبوك
    private void setupFacebookLogin() {
        binding.facebookSignInButton.setOnClickListener(v -> {
            if (!isNetworkAvailable()) {
                SnackbarHelper.showSnackbar(binding.getRoot(),
                        getString(R.string.no_internet), SnackbarHelper.SnackbarType.ERROR);
                return;
            }
            loginWithFacebook();
        });
    }

    // ✅ تحسين دالة تسجيل الدخول بالفيسبوك
    private void loginWithFacebook() {
        if (isRegistrationInProgress) {
            Log.d(TAG, "تسجيل الدخول بالفيسبوك مرفوض - العملية جارية بالفعل");
            return;
        }

        isRegistrationInProgress = true;
        showProgressDialog(getString(R.string.connecting_facebook));
        Log.d(TAG, "بدء تسجيل الدخول بالفيسبوك");

        // ✅ استخدام الطريقة الصحيحة مع تمرير Activity
        googleAuthHelper.signInWithFacebook(this, new GoogleAuthHelper.AuthCallback() {
            @Override
            public void onSignInProgress(String message) {
                Log.d(TAG, "تقدم تسجيل الدخول بالفيسبوك: " + message);
                updateProgressDialog(message);
            }

            @Override
            public void onSignInSuccess(FirebaseUser user, AuthResult authResult) {
                isRegistrationInProgress = false;
                Log.d(TAG, "تم تسجيل الدخول بنجاح - Facebook - User: " + user.getEmail());

                // ✅ التحقق من الحساب المحذوف أولاً
                checkUserDeletionStatusWithFallback(user.getEmail(), new UserCheckCallback() {
                    @Override
                    public void onCheckComplete(boolean isDeleted) {
                        if (isDeleted) {
                            hideProgressDialog();
                            isRegistrationInProgress = false;
                            performLogout();
                            return;
                        }


                        isNewUser = authResult.getAdditionalUserInfo().isNewUser();
                        prefs.edit().putBoolean("isNewUser", isNewUser).apply();
                        Log.d(TAG, "isNewUser - Facebook: " + isNewUser);

                        if (isNewUser) {
                            Log.d(TAG, "مستخدم جديد - Facebook");
                            handleNewUserRegistration(user);
                        } else {
                            Log.d(TAG, "مستخدم موجود - Facebook");
                            performDeviceLicenseCheck(user);
                        }
                    }
                });
            }

            @Override
            public void onSignInFailure(String error) {
                isRegistrationInProgress = false;
                hideProgressDialog();
                Log.e(TAG, "فشل تسجيل الدخول بالفيسبوك: " + error);
                SnackbarHelper.showSnackbar(binding.getRoot(),
                        "فشل تسجيل الدخول بالفيسبوك: " + error,
                        SnackbarHelper.SnackbarType.ERROR);
            }

            @Override
            public void onSignOutSuccess() {
                // غير مستخدم هنا
            }
        });
    }

    // ✅ معالجة onActivityResult للفيسبوك
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult - requestCode: " + requestCode + ", resultCode: " + resultCode);

        // معالجة نتيجة جوجل
        if (requestCode == GoogleAuthHelper.getSignInRequestCode()) {
            Log.d(TAG, "معالجة نتيجة جوجل");
            googleAuthHelper.handleSignInResult(data);
        }

        // ✅ معالجة نتيجة الفيسبوك
        if (googleAuthHelper.onFacebookActivityResult(requestCode, resultCode, data)) {
            Log.d(TAG, "تمت معالجة نتيجة الفيسبوك");
        }
    }

    // ✅ تحسين دالة التحقق من الحساب المحذوف
    private void checkIfUserDeleted(String email, UserCheckCallback callback) {
        if (email == null) {
            Log.d(TAG, "البريد الإلكتروني فارغ - تخطي التحقق");
            callback.onCheckComplete(false);
            return;
        }

        Log.d(TAG, "التحقق من الحساب المحذوف: " + email);
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        firestore.collection("deletedAccounts")
                .whereEqualTo("email", email.toLowerCase())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot userSnapshot = task.getResult();
                        if (!userSnapshot.isEmpty()) {
                            Log.e(TAG, "تم العثور على الحساب المحذوف: " + email);
                            callback.onCheckComplete(true);
                        } else {
                            Log.d(TAG, "الحساب غير محذوف: " + email);
                            callback.onCheckComplete(false);
                        }
                    } else {
                        Log.e(TAG, "خطأ في التحقق من الحساب المحذوف: " + email);
                        // في حالة الخطأ، نفترض أن الحساب غير محذوف للمتابعة
                        callback.onCheckComplete(false);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "فشل التحقق من الحساب المحذوف: " + e.getMessage());
                    callback.onCheckComplete(false);
                });
    }
    /**
     * ✅ التحقق من حالة الحساب باستخدام Firebase Functions قبل التسجيل - الإصدار المصحح
     */
   // التطبيق اندرويد وهذه الداله التي يتم بها استدعاء الفانكشن
    private void checkUserDeletionStatus(String email, UserCheckCallback callback) {
        if (email == null || email.isEmpty()) {
            Log.d(TAG, "البريد الإلكتروني فارغ - تخطي التحقق");
            callback.onCheckComplete(false);
            return;
        }

        Log.d(TAG, "التحقق من حالة الحساب قبل التسجيل: " + email);

        // إعداد بيانات الاستدعاء
        Map<String, Object> data = new HashMap<>();
        data.put("email", email.toLowerCase().trim());

        // استدعاء دالة Firebase Functions
        firebaseFunctions
                .getHttpsCallable("checkUserDeletionStatus")
                .call(data)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        try {
                            HttpsCallableResult result = task.getResult();
                            Map<String, Object> resultData = (Map<String, Object>) result.getData();

                            Log.d(TAG, "نتيجة التحقق: " + resultData);

                            // التحقق من نجاح العملية
                            Boolean success = (Boolean) resultData.get("success");
                            Boolean isDeleted = (Boolean) resultData.get("isDeleted");

                            if (success != null && success && isDeleted != null && isDeleted) {
                                Log.e(TAG, "تم العثور على الحساب المحذوف: " + email);

                                // الحصول على بيانات الحذف
                                String message = (String) resultData.get("message");
                                String reason = (String) resultData.get("reason");

                                runOnUiThread(() -> {
                                    showAccountDeletedDialog(message, reason);
                                });
                                callback.onCheckComplete(true);
                            } else {
                                Log.d(TAG, "الحساب غير محذوف أو حدث خطأ: " + email);
                                callback.onCheckComplete(false);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "خطأ في معالجة نتيجة التحقق: " + e.getMessage());
                            callback.onCheckComplete(false);
                        }
                    } else {
                        // في حالة فشل الاستدعاء، نعتبر الحساب غير محذوف
                        Exception exception = task.getException();
                        Log.e(TAG, "فشل استدعاء دالة التحقق: " +
                                (exception != null ? "PERMISSION_DENIED " + exception.hashCode(): "خطأ غير معروف"));
                        callback.onCheckComplete(false);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "فشل التحقق من حالة الحساب: " + e.getMessage());
                    callback.onCheckComplete(false);
                });
    }

    /**
     * ✅ عرض ديالوج الحساب المحذوف - الإصدار المصحح
     */
    private void showAccountDeletedDialog(String message, String reason) {
        runOnUiThread(() -> {
            StringBuilder dialogMessage = new StringBuilder();

            if (message != null) {
                dialogMessage.append(message);
            } else {
                dialogMessage.append(getString(R.string.account_deleted_message));
            }

            if (reason != null && !reason.isEmpty() && !reason.equals(getString(R.string.no_deletion_reason))) {
                dialogMessage.append(getString(R.string.deletion_reason)).append(reason);
            }

            dialogMessage.append(getString(R.string.deletion_final_message));

            new MaterialAlertDialogBuilder(LoginActivity.this)
                    .setTitle("الحساب محذوف")
                    .setMessage(dialogMessage.toString())
                    .setPositiveButton("موافق", (dialog, which) -> {
                        // تنظيف الحقول
                        binding.emailEditText.setText("");
                        binding.etPassword.setText("");
                        binding.emailRegisterEditText.setText("");
                        binding.passwordEditText.setText("");
                        binding.passwordRenterEditText.setText("");
                    })
                    .setCancelable(false)
                    .setIcon(R.drawable.ic_warning)
                    .show();
        });
    }
    // ✅ تحسين إدارة حالة التقدم
    private void showRegistrationProgress() {
        Log.d(TAG, "بدء إعداد بيانات الحساب الجديد - Facebook");
        updateProgressDialog(getString(R.string.preparing_account));

//        new Handler(Looper.getMainLooper()).postDelayed(() -> {
//            Log.d(TAG, "جاري إعداد البيانات الأساسية - Facebook");
//            updateProgressDialog(getString(R.string.preparing_basic_data));
//            checkLocationPermission();
//        }, 1000);
    }

    /**
     * دالة مركزية للتعامل مع تسجيل المستخدم الجديد
     */
    private void handleNewUserRegistration1(FirebaseUser user) {
        Log.d(TAG, "بدء إعداد بيانات الحساب الجديد");

        // تحديث حالة isNewUser في SharedPreferences
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("isNewUser", true);
        editor.apply();

        // تطبيق مكافآت الإحالة إذا كانت متاحة
        if (referrerUid != null && !referrerUid.isEmpty()) {
            referralManager.applyReferralRewardIfAvailable(user.getUid());
            SharedPreferences referralPrefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
            referralPrefs.edit().remove("referrer_uid").apply();

            SnackbarHelper.showSnackbar(binding.getRoot(),
                    getString(R.string.reward_message),
                    SnackbarHelper.SnackbarType.SUCCESS);
        }

        // إذا كان في وضع تحويل الضيف
        if (isRegisterGuestMode && isGuest) {

            convertGuestToOfficialUser(user, user.getDisplayName(), user.getEmail());
        } else {
            // الانتقال المباشر لإعداد العملة بدون تأخير
            showProgressDialog(getString(R.string.preparing_account));
            checkLocationPermission();
        }
    }

    public void deleteGuestAccountsWithSameDevice() {
        String currentDeviceId = licenseManager.getDeviceId();

        if (TextUtils.isEmpty(currentDeviceId)) {
            return;
        }

        Log.d(TAG, "بدء حذف حسابات الضيف للجهاز: " + currentDeviceId);

        // البحث عن جميع حسابات الضيف التي تستخدم نفس deviceId
        firestore.collection("guests")
                .whereEqualTo("deviceId", currentDeviceId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        List<DocumentSnapshot> documents = task.getResult().getDocuments();
                        int totalCount = documents.size();

                        if (totalCount == 0) {
                            Log.d(TAG, "لا توجد حسابات ضيف للحذف");

                            return;
                        }

                        Log.d(TAG, "تم العثور على " + totalCount + " حساب ضيف للحذف");
                        deleteGuestDocuments(documents);
                    } else {
                        Log.e(TAG, "خطأ في البحث عن حسابات الضيف: " + task.getException());

                    }
                });
    }

    private void deleteGuestDocuments(List<DocumentSnapshot> documents) {
        WriteBatch batch = firestore.batch();

        for (DocumentSnapshot doc : documents) {
            batch.delete(doc.getReference());
        }

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "تم حذف " + documents.size() + " حساب ضيف بنجاح");
                    SecureLicenseManager.getInstance(this).setGuestUID("");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "فشل في حذف حسابات الضيف: " + e.getMessage());
                });
    }

    // تحسين دالة validateAndRegisterUser
    private void validateAndRegisterUser() {
        if (isWaitingForEmailVerification || isEmailVerificationInProgress) {
            SnackbarHelper.showSnackbar(binding.getRoot(),
                    getString(R.string.registration_in_progress),
                    SnackbarHelper.SnackbarType.WARNING);
            return;
        }

        String name = binding.nameRegEditText.getText().toString().trim();
        String email = binding.emailRegisterEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();
        String password2 = binding.passwordRenterEditText.getText().toString().trim();

        // التحقق من المدخلات
        if (TextUtils.isEmpty(name)) {
            binding.nameRegLayout.setError(getString(R.string.error_name_required));
            return;
        } else {
            binding.nameRegLayout.setError(null);
        }

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailRegisterLayout.setError(getString(R.string.error_invalid_email));
            return;
        } else {
            binding.emailRegisterLayout.setError(null);
        }

        if (password.length() < 6) {
            binding.passwordLayout.setError(getString(R.string.error_password_short));
            return;
        } else {
            binding.passwordLayout.setError(null);
        }

        if (!password.equals(password2)) {
            binding.passwordLayout.setError(getString(R.string.error_password_mismatch));
            binding.passwordRenterLayout.setError(getString(R.string.error_password_mismatch));
            return;
        } else {
            binding.passwordLayout.setError(null);
            binding.passwordRenterLayout.setError(null);
        }

        isRegistrationInProgress = true;
        isFunctionCallFailed = false; // إعادة تعيين حالة الفشل

        showProgressDialog(getString(R.string.checking_account_status));

        // استخدام النسخة المحسنة من التحقق مع التعامل مع PERMISSION_DENIED
        checkUserDeletionStatusWithFallback(email, new UserCheckCallback() {
            @Override
            public void onCheckComplete(boolean isDeleted) {
                if (isDeleted) {
                    hideProgressDialog();
                    isRegistrationInProgress = false;
                    return;
                }

                // إذا لم يكن محذوفًا، تابع إنشاء الحساب
                showProgressDialog(getString(R.string.creating_system_account));
                createUserInAuthWithProtection(name, email, password);
            }
        });
    }

    // النسخة النهائية من التحقق مع التعامل مع PERMISSION_DENIED
    private void checkUserDeletionStatusWithFallback(String email, UserCheckCallback callback) {
        if (email == null || email.isEmpty()) {
            callback.onCheckComplete(false);
            return;
        }

        Log.d(TAG, "التحقق من حالة الحساب مع Fallback: " + email);

        // المحاولة الأولى: استخدام Firebase Functions
        attemptFunctionCall(email, new UserCheckCallback() {
            @Override
            public void onCheckComplete(boolean isDeleted) {
                if (!isDeleted && isFunctionCallFailed) {
                    // إذا فشلت الدالة، استخدم Fallback مباشرة
                    Log.d(TAG, "استخدام Fallback بعد فشل استدعاء الدالة");
                    checkDeletedAccountsDirectFallback(email, callback);
                } else {
                    callback.onCheckComplete(isDeleted);
                }
            }
        });
    }

    // محاولة استدعاء الدالة مع التعامل مع الأخطاء
    private void attemptFunctionCall(String email, UserCheckCallback callback) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("email", email.toLowerCase().trim());

            firebaseFunctions
                    .getHttpsCallable("checkUserDeletionStatus")
                    .call(data)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            isFunctionCallFailed = false;
                            try {
                                HttpsCallableResult result = task.getResult();
                                Map<String, Object> resultData = (Map<String, Object>) result.getData();

                                Boolean success = (Boolean) resultData.get("success");
                                Boolean isDeleted = (Boolean) resultData.get("isDeleted");

                                if (success != null && success && isDeleted != null && isDeleted) {
                                    Log.e(TAG, "تم العثور على الحساب المحذوف عبر الدالة: " + email);
                                    String message = (String) resultData.get("message");
                                    String reason = (String) resultData.get("reason");
                                    runOnUiThread(() -> {
                                        showAccountDeletedDialog(message, reason);
                                    });
                                    callback.onCheckComplete(true);
                                } else {
                                    Log.d(TAG, "الحساب غير محذوف عبر الدالة: " + email);
                                    callback.onCheckComplete(false);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "خطأ في معالجة نتيجة الدالة: " + e.getMessage());
                                isFunctionCallFailed = true;
                                callback.onCheckComplete(false);
                            }
                        } else {
                            // هنا يتم التعامل مع PERMISSION_DENIED وغيرها من الأخطاء
                            Exception exception = task.getException();
                            String errorMessage = exception != null ? exception.getMessage() : getString(R.string.unknown_error);
                            Log.e(TAG, "فشل استدعاء دالة التحقق: " + errorMessage);

                            isFunctionCallFailed = true;

                            // لا نعرض رسالة للمستخدم هنا، ننتقل مباشرة للـ Fallback
                            callback.onCheckComplete(false);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "فشل كامل في استدعاء الدالة: " + e.getMessage());
                        isFunctionCallFailed = true;
                        callback.onCheckComplete(false);
                    });

        } catch (Exception e) {
            Log.e(TAG, "خطأ في استدعاء الدالة: " + e.getMessage());
            isFunctionCallFailed = true;
            callback.onCheckComplete(false);
        }
    }

    // Fallback مباشر للبحث في Firestore
    private void checkDeletedAccountsDirectFallback(String email, UserCheckCallback callback) {
        Log.d(TAG, "التحقق المباشر من الحساب المحذوف في deletionLogs: " + email);

        firestore.collection("deletionLogs")
                .whereEqualTo("email", email.toLowerCase().trim())
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            Log.e(TAG, "تم العثور على الحساب المحذوف في deletionLogs: " + email);
                            runOnUiThread(() -> {
                                showAccountDeletedDialog(getString(R.string.account_deleted_message), getString(R.string.no_deletion_reason));
                            });
                            callback.onCheckComplete(true);
                        } else {
                            Log.d(TAG, "الحساب غير محذوف في deletionLogs: " + email);
                            callback.onCheckComplete(false);
                        }
                    } else {
                        Log.e(TAG, "فشل التحقق المباشر من الحساب المحذوف: " +
                                (task.getException() != null ? task.getException().getMessage() : getString(R.string.auth_unknown_error)));
                        // في حالة فشل جميع المحاولات، نعتبر الحساب غير محذوف للمتابعة
                        callback.onCheckComplete(false);
                    }
                });
    }

    // نسخة محمية من إنشاء المستخدم مع منع التكرار
    private void createUserInAuthWithProtection(String name, String email, String password) {
        // فحص مزدوج لمنع التنفيذ المتكرر
        if (isEmailVerificationInProgress) {
            Log.w(TAG, "محاولة إنشاء حساب أثناء وجود عملية تحقق - تم الرفض");
            hideProgressDialog();
            isRegistrationInProgress = false;
            SnackbarHelper.showSnackbar(binding.getRoot(),
                    getString(R.string.registration_in_progress),
                    SnackbarHelper.SnackbarType.WARNING);
            return;
        }

        isEmailVerificationInProgress = true;
        updateProgressDialog(getString(R.string.creating_system_account));

        // تسجيل خروج أي مستخدم موجود مسبقاً لتجنب التكرار
        if (firebaseAuth.getCurrentUser() != null) {
            Log.d(TAG, "تسجيل خروج المستخدم الحالي قبل إنشاء حساب جديد");
            firebaseAuth.signOut();
        }

        // تأخير بسيط لضمان اكتمال تسجيل الخروج
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                            if (firebaseUser != null) {
                                Log.d(TAG, getString(R.string.user_created) + firebaseUser.getUid());
                                updateAuthProfileAndSendVerification(firebaseUser, name, email);
                            } else {
                                handleAuthError(getString(R.string.auth_user_data_error));
                            }
                        } else {
                            handleAuthError(task.getException() != null ?
                                    getAuthErrorMessage(task.getException()) : getString(R.string.auth_unknown_error));
                        }
                    });
        }, 1000);
    }

    // تحسين رسائل أخطاء المصادقة
    private String getAuthErrorMessage(Exception exception) {
        String errorMessage = exception.getMessage();
        if (errorMessage.contains("email already in use")) {
            return getString(R.string.email_already_used);
        } else if (errorMessage.contains("invalid email")) {
            return getString(R.string.invalid_email);
        } else if (errorMessage.contains("weak password")) {
            return getString(R.string.weak_password);
        } else {
            return getString(R.string.auth_unknown_error) +" " + errorMessage;
        }
    }

    // دالة موحدة لتحديث الملف الشخصي وإرسال التحقق
    private void updateAuthProfileAndSendVerification(FirebaseUser firebaseUser, String name, String email) {
        updateProgressDialog(getString(R.string.preparing_account));

        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build();

        firebaseUser.updateProfile(profileUpdates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        sendEmailVerificationWithRetry(firebaseUser, name, email);
                    } else {
                        handleAuthError(getString(R.string.auth_profile_error));
                    }
                });
    }

    // إرسال التحقق مع إمكانية إعادة المحاولة
    private void sendEmailVerificationWithRetry(FirebaseUser firebaseUser, String name, String email) {
        updateProgressDialog(getString(R.string.sending_verification_email));

        firebaseUser.sendEmailVerification()
                .addOnCompleteListener(verifyTask -> {
                    if (verifyTask.isSuccessful()) {
                        Log.d(TAG, "تم إرسال رابط التحقق بنجاح إلى: " + email);

                        // حفظ حالة المستخدم المنتظر التحقق
                        pendingVerificationUser = firebaseUser;
                        isWaitingForEmailVerification = true;

                        // إخفاء progress dialog العادي وإظهار dialog التحقق
                        hideProgressDialog();
                        showStableVerificationDialog(name, email);

                    } else {
                        handleAuthError(getString(R.string.auth_verification_error) +
                                (verifyTask.getException() != null ? verifyTask.getException().getMessage() : "خطأ غير معروف"));
                    }
                });
    }

    // ديالوج تحقق مستقر لا يختفي
    private void showStableVerificationDialog(String name, String email) {
        runOnUiThread(() -> {
            try {
                // إغلاق أي dialogs موجودة مسبقاً
                if (verificationAlertDialog != null && verificationAlertDialog.isShowing()) {
                    verificationAlertDialog.dismiss();
                }

                View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_email_verification, null);
                TextView tvEmail = dialogView.findViewById(R.id.tvEmail);
                TextView tvTimer = dialogView.findViewById(R.id.tvTimer);
                TextView tvNote = dialogView.findViewById(R.id.tvNote);
                Button btnResend = dialogView.findViewById(R.id.btnResend);
                Button btnCancel = dialogView.findViewById(R.id.btnCancel);
                ProgressBar progressBar = dialogView.findViewById(R.id.progressBar);

                tvEmail.setText(email);
                tvNote.setText(getString(R.string.verification_note));
                verificationAlertDialog = new MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.account_activation_required))
                        .setView(dialogView)
                        .setCancelable(false)
                        .create();

                // بدء العد التنازلي (120 ثانية = دقيقتين)
                startStableCountdown(tvTimer, btnResend, 120);

                btnResend.setOnClickListener(v -> {
                    progressBar.setVisibility(View.VISIBLE);
                    btnResend.setEnabled(false);
                    resendVerificationWithFeedback();
                    // إعادة تفعيل الزر بعد 5 ثوانٍ
                    new Handler().postDelayed(() -> {
                        progressBar.setVisibility(View.GONE);
                    }, 5000);
                });

                btnCancel.setOnClickListener(v -> {
                    safelyCancelVerification();
                });

                verificationAlertDialog.show();

                // بدء مراقبة حالة التحقق بعد تأخير بسيط
                new Handler().postDelayed(() -> {
                    startRobustVerificationMonitoring();
                }, 3000); // تأخير 3 ثوانٍ لضمان استقرار النظام

            } catch (Exception e) {
                Log.e(TAG, "خطأ في عرض ديالوج التحقق: " + e.getMessage());
                handleAuthError(getString(R.string.error_dialoge));
            }
        });
    }

    // بدء مراقبة قوية للتحقق
    private void startRobustVerificationMonitoring() {
        verificationCheckCount = 0;
        if (verificationHandler == null) {
            verificationHandler = new Handler(Looper.getMainLooper());
        }

        // بدء المراقبة الفورية
        verificationHandler.post(verificationRunnable);
    }

    // Runnable محسن للمراقبة
    private Runnable verificationRunnable = new Runnable() {
        @Override
        public void run() {
            checkVerificationStatusRobust();
        }
    };

    // التحقق القوي من حالة البريد
    private void checkVerificationStatusRobust() {
        Log.d(TAG, "جاري التحقق من حالة البريد... المحاولة: " + (verificationCheckCount + 1));

        // الحصول على المستخدم الحالي مباشرة من FirebaseAuth
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "لا يوجد مستخدم حالي - إيقاف المراقبة");
            stopVerificationMonitoring();
            handleVerificationError(getString(R.string.verification_error_user_null));
            return;
        }

        // تحديث المرجع
        pendingVerificationUser = currentUser;

        // إعادة تحميل بيانات المستخدم
        currentUser.reload().addOnCompleteListener(reloadTask -> {
            if (reloadTask.isSuccessful()) {
                // الحصول على أحدث بيانات المستخدم
                FirebaseUser refreshedUser = firebaseAuth.getCurrentUser();
                if (refreshedUser == null) {
                    Log.e(TAG, "المستخدم أصبح null بعد إعادة التحميل");
                    handleVerificationError(getString(R.string.verification_error_user_null));
                    return;
                }

                if (refreshedUser.isEmailVerified()) {
                    Log.d(TAG, "تم التحقق من البريد بنجاح!");
                    handleSuccessfulVerification();
                } else {
                    verificationCheckCount++;
                    Log.d(TAG, "البريد لم يتم التحقق بعد. المحاولة: " + verificationCheckCount);

                    if (verificationCheckCount >= MAX_VERIFICATION_CHECKS) {
                        handleVerificationTimeout();
                    } else {
                        // تحديث واجهة المستخدم والاستمرار في المراقبة
                        updateVerificationProgress();
                        verificationHandler.postDelayed(verificationRunnable, VERIFICATION_CHECK_INTERVAL);
                    }
                }
            } else {
                Log.e(TAG, "فشل إعادة تحميل بيانات المستخدم: " + reloadTask.getException());
                // إعادة المحاولة بعد فترة
                verificationHandler.postDelayed(verificationRunnable, VERIFICATION_CHECK_INTERVAL);
            }
        });
    }

    // التعامل مع النجاح في التحقق
    private void handleSuccessfulVerification() {
        Log.d(TAG, "بدء معالجة التحقق الناجح");

        runOnUiThread(() -> {
            // إيقاف جميع العمليات أولاً
            stopVerificationMonitoring();

            // إغلاق ديالوج التحقق
            if (verificationAlertDialog != null && verificationAlertDialog.isShowing()) {
                verificationAlertDialog.dismiss();
                verificationAlertDialog = null;
            }

            // إيقاف العد التنازلي
            if (verificationCountDownTimer != null) {
                verificationCountDownTimer.cancel();
            }

            // التأكد النهائي من أن المستخدم مفعل
            FirebaseUser currentUser = firebaseAuth.getCurrentUser();
            if (currentUser == null || !currentUser.isEmailVerified()) {
                Log.e(TAG, getString(R.string.verification_failed_not_available));
                SnackbarHelper.showSnackbar(binding.getRoot(),
                        getString(R.string.verification_failed),
                        SnackbarHelper.SnackbarType.ERROR);
                return;
            }

            // تحديث جميع الحالات
            isRegistrationInProgress = false;
            isEmailVerificationInProgress = false;
            isWaitingForEmailVerification = false;

            // تحديث preferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("isNewUser", true);
            editor.apply();

            Log.d(TAG, "تم التحقق بنجاح، جاري متابعة التسجيل...");

            // متابعة عملية التسجيل
            proceedAfterSuccessfulVerification(currentUser);
        });
    }

    // المتابعة بعد التحقق الناجح
    private void proceedAfterSuccessfulVerification(FirebaseUser user) {
        // إظهار رسالة نجاح
        SnackbarHelper.showSnackbar(binding.getRoot(),
                getString(R.string.verification_success),
                SnackbarHelper.SnackbarType.SUCCESS);

        // تطبيق مكافآت الإحالة إذا كانت متاحة
        if (referrerUid != null && !referrerUid.isEmpty()) {
            referralManager.applyReferralRewardIfAvailable(user.getUid());
            SharedPreferences referralPrefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
            referralPrefs.edit().remove("referrer_uid").apply();
        }

        // إذا كان في وضع تحويل الضيف
        if (isRegisterGuestMode && isGuest) {
            convertGuestToOfficialUser(user, user.getDisplayName(), user.getEmail());
        } else {
            showProgressDialog(getString(R.string.preparing_account));
            checkLocationPermission();
        }

        pendingVerificationUser = null;
    }

    // إعادة الإرسال مع تغذية راجعة
    private void resendVerificationWithFeedback() {
        if (pendingVerificationUser != null) {
            pendingVerificationUser.sendEmailVerification()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            SnackbarHelper.showSnackbar(binding.getRoot(),
                                    getString(R.string.verification_resent_success),
                                    SnackbarHelper.SnackbarType.SUCCESS);
                        } else {
                            SnackbarHelper.showSnackbar(binding.getRoot(),
                                    getString(R.string.verification_resent_failed) +
                                            (task.getException() != null ? task.getException().getMessage() : "خطأ غير معروف"),
                                    SnackbarHelper.SnackbarType.ERROR);
                        }
                    });
        }
    }

    // إلغاء آمن للتحقق
    private void safelyCancelVerification() {
        stopVerificationMonitoring();
        isRegistrationInProgress = false;
        isEmailVerificationInProgress = false;
        isWaitingForEmailVerification = false;

        // تسجيل الخروج من الحساب غير المفعل
        if (firebaseAuth.getCurrentUser() != null) {
            firebaseAuth.signOut();
        }

        // إغلاق الديالوج
        if (verificationAlertDialog != null && verificationAlertDialog.isShowing()) {
            verificationAlertDialog.dismiss();
            verificationAlertDialog = null;
        }

        pendingVerificationUser = null;

        SnackbarHelper.showSnackbar(binding.getRoot(),
                getString(R.string.registration_cancelled),
                SnackbarHelper.SnackbarType.INFO);
    }

    // إيقاف المراقبة
    private void stopVerificationMonitoring() {
        if (verificationHandler != null) {
            verificationHandler.removeCallbacks(verificationRunnable);
        }
        verificationCheckCount = 0;
    }

    // العد التنازلي المستقر
    private void startStableCountdown(TextView tvTimer, Button btnResend, int seconds) {
        verificationCountDownTimer = new CountDownTimer(seconds * 1000, 1000) {
            public void onTick(long millisUntilFinished) {
                long secondsRemaining = millisUntilFinished / 1000;
                String timeText = String.format(Locale.getDefault(),
                        getString(R.string.resend_countdown), secondsRemaining);
                tvTimer.setText(timeText);

                btnResend.setEnabled(secondsRemaining <= 0);
            }

            public void onFinish() {
                btnResend.setEnabled(true);
                tvTimer.setText(getString(R.string.resend_verification));
            }
        }.start();
    }

    // تحديث تقدم المراقبة
    private void updateVerificationProgress() {
        runOnUiThread(() -> {
            if (verificationAlertDialog != null && verificationAlertDialog.isShowing()) {
                View dialogView = verificationAlertDialog.findViewById(R.id.tvTimer);
                if (dialogView instanceof TextView) {
                    TextView tvProgress = (TextView) dialogView;
                    String progressText = String.format(Locale.getDefault(),
                            getString(R.string.verification_progress), verificationCheckCount, MAX_VERIFICATION_CHECKS);
                    tvProgress.setText(progressText);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // تنظيف شامل لجميع الموارد
        stopVerificationMonitoring();

        if (verificationHandler != null) {
            verificationHandler.removeCallbacksAndMessages(null);
        }

        if (verificationAlertDialog != null && verificationAlertDialog.isShowing()) {
            verificationAlertDialog.dismiss();
            verificationAlertDialog = null;
        }

        if (verificationCountDownTimer != null) {
            verificationCountDownTimer.cancel();
        }

        // إعادة تعيين جميع الحالات
        isRegistrationInProgress = false;
        isEmailVerificationInProgress = false;
        isWaitingForEmailVerification = false;
        pendingVerificationUser = null;
        isFunctionCallFailed = false;

        cancelLoginTimeout();
        hideProgressDialog();
    }

    // دوال مساعدة لمعالجة الأخطاء
    private void handleAuthError(String error) {
        runOnUiThread(() -> {
            isRegistrationInProgress = false;
            isEmailVerificationInProgress = false;
            hideProgressDialog();

            SnackbarHelper.showSnackbar(binding.getRoot(), error,
                    SnackbarHelper.SnackbarType.ERROR);

            // تنظيف أي بيانات متبقية
            if (firebaseAuth.getCurrentUser() != null) {
                firebaseAuth.signOut();
            }
        });
    }

    private void handleVerificationError(String error) {
        runOnUiThread(() -> {
            stopVerificationMonitoring();
            if (verificationAlertDialog != null && verificationAlertDialog.isShowing()) {
                verificationAlertDialog.dismiss();
            }

            SnackbarHelper.showSnackbar(binding.getRoot(),
                    getString(R.string.verification_error) + error,
                    SnackbarHelper.SnackbarType.ERROR);

            safelyCancelVerification();
        });
    }

    private void handleVerificationTimeout() {
        runOnUiThread(() -> {
            stopVerificationMonitoring();

            if (verificationAlertDialog != null && verificationAlertDialog.isShowing()) {
                verificationAlertDialog.dismiss();
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.verification_timeout_title))
                    .setMessage(getString(R.string.verification_timeout_message))
                    .setPositiveButton(getString(R.string.retry_verification), (dialog, which) -> {
                        if (pendingVerificationUser != null) {
                            resendVerificationWithFeedback();
                            showStableVerificationDialog(
                                    pendingVerificationUser.getDisplayName(),
                                    pendingVerificationUser.getEmail()
                            );
                        }
                    })
                    .setNegativeButton(getString(R.string.cancel_verification), (dialog, which) -> safelyCancelVerification())
                    .setCancelable(false)
                    .show();
        });
    }
}