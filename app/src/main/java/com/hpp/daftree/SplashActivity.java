package com.hpp.daftree;

import static com.hpp.daftree.helpers.PreferenceHelper.applyLocale;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Geocoder;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.DeviceInfo;
import com.hpp.daftree.database.User;
import com.hpp.daftree.dialogs.LanguageDialog;
import com.hpp.daftree.dialogs.LanguageViewModel;
import com.hpp.daftree.helpers.LanguageHelper;
import com.hpp.daftree.helpers.PreferenceHelper;
import com.hpp.daftree.models.AppLockManager;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.models.SnackbarHelper;
import com.hpp.daftree.syncmanagers.SyncPreferences;
import com.hpp.daftree.ui.BaseActivity;
import com.hpp.daftree.utils.LicenseManager;
import com.hpp.daftree.utils.ReferralManager;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.utils.VersionManager;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends BaseActivity
        implements LanguageDialog.OnLanguageSelectedListener {
    private static final String TAG = "SplashActivity";
    private ProgressDialog progressDialog;
    private FusedLocationProviderClient fusedLocationClient;
    private View rootView;
    private SyncPreferences syncPreferences;
    private VersionManager versionManager;
    private DeviceBanManager deviceBanManager;
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    showProgressDialog(getString(R.string.preper_guest_account));
                    setupLocalCurrencyAndProceed();
                } else {
//                    Toast.makeText(this, getString(R.string.location_permission_denied), Toast.LENGTH_LONG).show();
                    SnackbarHelper.showSnackbar(rootView, getString(R.string.location_permission_denied), SnackbarHelper.SnackbarType.ERROR);
                    showProgressDialog(getString(R.string.preper_guest_account));
                    addDefaultCurrencyAndProceed();
                }
            });
    private SharedPreferences prefs, referral_prefs;
    private boolean isFirstRun, isFirstRunGuest;

    private boolean isApplyingLanguage = false;
    private LicenseManager licenseManager;
    private ReferralManager referralManager;
    private AppDatabase db;
    private FirebaseFirestore firestore;
    private DaftreeRepository repository;
    private String referrerUid;
    private FirebaseAuth firebaseAuth;
    private String lockType = "";
    FirebaseUser currentUser;
    private boolean isGuest = false;
    private String guestUID = "";
    String savedLanguage = "";
    private Executor executor;
    private ProfileViewModel viewModelUserProfile;

    private LanguageViewModel languageViewModel;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash); // ملف تخطيط بسيط يحتوي على شعار التطبيق
        rootView = findViewById(android.R.id.content);
        applyLocale(this, PreferenceHelper.getLanguage(this));
        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        isFirstRun = prefs.getBoolean("first_run", true);
        savedLanguage = prefs.getString("language", "ar");
        isFirstRunGuest = prefs.getBoolean("first_run_guest", true);
        referral_prefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
        referrerUid = referral_prefs.getString("referrer_uid", "");
        firebaseAuth = FirebaseAuth.getInstance();
        db = AppDatabase.getDatabase(getApplicationContext());
        repository = new DaftreeRepository(getApplication());
        syncPreferences = new SyncPreferences(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        firestore = FirebaseFirestore.getInstance();
        referralManager = new ReferralManager(this);
        licenseManager = new LicenseManager(this);
        viewModelUserProfile = new ViewModelProvider(this).get(ProfileViewModel.class);
        executor = ContextCompat.getMainExecutor(this);
        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        isGuest = SecureLicenseManager.getInstance(this).isGuest();
        guestUID = SecureLicenseManager.getInstance(this).guestUID();
        versionManager = new VersionManager(this);
        languageViewModel  = new LanguageViewModel();
        deviceBanManager = new DeviceBanManager(this);
        // معالجة intent لاستخراج بيانات الدعوة
//        handleDeepLink(getIntent());
        handleIncomingDeepLink(getIntent());
        setupLanguageObserver();
        checkUserAndNavigate();
//        new Handler(Looper.getMainLooper()).postDelayed(() -> {
//            handleAppOpen();
//        }, 500);

    }
    private void setupLanguageObserver() {
        languageViewModel.getSelectedLanguage().observe(this, lang -> {
            if (lang != null && !lang.isEmpty()) {
                String currentLang = savedLanguage;
                Log.e(TAG, "اللغة الجديدة: " + lang + " | اللغة الحالية: " + currentLang);

                if (!lang.equals(currentLang)) {
                    Log.e(TAG, "تم اكتشاف تغيير اللغة، جاري التطبيق...");

                    // 🔥 استخدام الدالة الجديدة من BaseActivity
                    BaseActivity.applyLanguage(SplashActivity.this, lang);

                    // تحديث التفضيلات
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("language", lang);
                    editor.apply();

                }
            }
        });
    }
    private void checkUserAndNavigate() {
        // التحقق من حالة المستخدم
        boolean isLoggedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
        boolean isGuest = SecureLicenseManager.getInstance(this).isGuest();

        // 🔥 التحقق من أن البيانات موجودة في قاعدة البيانات قبل الانتقال
        verifyDatabaseData(() -> {
            handleAppOpen();
        });
    }

    private void verifyDatabaseData(Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(this);

                // التحقق من وجود البيانات المستوردة
                int accountCount = db.accountDao().getAccountsCount();
                int transactionCount = db.transactionDao().getTransactionsCount();

                Log.d("SplashActivity", "✅ التحقق من قاعدة البيانات:");
                Log.d("SplashActivity", "   - عدد الحسابات: " + accountCount);
                Log.d("SplashActivity", "   - عدد العمليات: " + transactionCount);

                runOnUiThread(onComplete);

            } catch (Exception e) {
                Log.e("SplashActivity", "❌ خطأ في التحقق من قاعدة البيانات: " + e.getMessage());
                runOnUiThread(onComplete);
            }
        });
    }
    public void handleAppOpen() {


        if (isFirstRun) {
            showLanguageDialog();
        } else {
            isGuest = SecureLicenseManager.getInstance(this).isGuest();
            savedLanguage = prefs.getString("language", "ar");
            LanguageHelper.setLocale(this, savedLanguage);
            PreferenceHelper.setLanguage(this, savedLanguage);
            if (isGuest) {
                guestUserData();

            } else {

                proceedToActivity();
            }
        }
    }

    private void guestUserData() {
        Log.d(TAG, " الضيف");
        isFirstRunGuest = prefs.getBoolean("first_run_guest", true);

        if (isFirstRun || isFirstRunGuest) {
            deviceBanManager.checkDeviceBan(new DeviceBanManager.BanCheckListener() {
                @Override
                public void onCheckComplete(boolean isBanned, String reason) {
                    runOnUiThread(() -> {
                        if (isBanned) {
                            showDeviceBanDialog(reason);
                            return;
                        }

                        // المتابعة مع جلسة الضيف
                        Log.d(TAG, "أول تشغيل للضيف");
                        showProgressDialog(getString(R.string.preper_guest_account));
                        showProgressDialog(getString(R.string.preper_guest_account));
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
                        showProgressDialog(getString(R.string.preper_guest_account));
                        createNewUserGuest();
                        Log.d(TAG, "اول تشغيل للضيف");
                    });
                }
            });


        } else {

            proceedToActivity();

        }
    }
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
        PreferenceHelper.setLanguage(this, languageCode);
        SecureLicenseManager.getInstance(this).setGuest(true);
        isGuest = true;
        recreate();

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

    private void handleIncomingDeepLink(Intent intent) {
        Uri data = intent.getData();
        if (data != null && "daftree".equals(data.getScheme())) {
            referrerUid = data.getQueryParameter("ref");
            // سجّل تفاصيل الرابط لأغراض التصحيح
            Log.d("DeepLink", "الرابط المستلم: " + data.toString());
            Log.d("DeepLink", "كود الدعوة: " + referrerUid);

//            if (referrerUid != null && !referrerUid.isEmpty()) {
//                Log.d("DeepLink", "تم استقبال دعوة من: " + referrerUid);
//                referralManager.saveReferrerUid(referrerUid);
//                if (currentUser == null) {
//                    Intent loginIntent = new Intent(this, LoginActivity.class);
//                    if (referrerUid != null) {
//                        loginIntent.putExtra("REFERRER_UID", referrerUid);
//                    }
//                    intent.putExtra("registerGuest", true);
//                    startActivity(loginIntent);
//                } else {
//                    SharedPreferences prefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
//                    prefs.edit().remove("referrer_uid").apply();
////                    SnackbarHelper.showSnackbar(rootView, getString(R.string.ar_long_text_40), SnackbarHelper.SnackbarType.ERROR);
//                    showAlreadyRegisteredMessage();
//                }
//            }
        }
    }

    void proceedToActivity() {

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            AppLockManager lockManager = new AppLockManager(this);
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            SharedPreferences prefs = getSharedPreferences("prefs_uid", MODE_PRIVATE);

            if (lockManager.isLockEnabled()) {
                // يوجد مستخدم والقفل مفعل، اذهب لشاشة القفل
                startActivity(new Intent(this, LockScreenActivity.class));
            } else {
                Intent mainIntent = new Intent(this, MainActivity.class);
                if (referrerUid != null) {
                    mainIntent.putExtra("REFERRER_UID", referrerUid);
                }
                startActivity(mainIntent);
            }
            finish();
        }, 100); // تأخير 1.5 ثانية لعرض الشعار

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
                .setPositiveButton(getString(R.string.use_location), (dialog, which) -> {
                    requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
                })
                .setNegativeButton(getString(R.string.skip_location), (dialog, which) -> {
                    showProgressDialog(getString(R.string.preper_guest_account));
                    addDefaultCurrencyAndProceed();
                })
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
                    String currencySymbol = currencyInfo.getSymbol(new Locale(savedLanguage));
                    String code;
                    if (savedLanguage.equals("ar") && currencyName != null && currencyName.length() >= 2) {
                        // للعربية: أول حرفين مع نقطة بينهما
                        code = currencyName.substring(0, 1) + "." + currencyName.substring(1, 2);
                    } else {
                        code = currencyInfo.getCurrencyCode();

                    }
                    if (code == "ر.ي" || code == "ر.س" || code == "ر.ق" || code == "ر.ع") {
                        currencySymbol = "﷼";
                    }
                    Log.d("LocalCurrency", "العملة المكتشفة: " + "language: " + savedLanguage + "currencyName: " + currencyName + "\n" +
                            "currencySymbol: " + currencySymbol + " Code: " + code);

                    addCurrencyToDatabase(currencyName, currencySymbol, code);
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

    @SuppressLint("MissingPermission")
    private void setupLocalCurrencyAndProceed2() {
        Log.d(TAG, getString(R.string.detecting_local_currency));
        updateProgressDialog(getString(R.string.detecting_local_currency));
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                try {
                    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                    String countryCode = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1).get(0).getCountryCode();
                    Locale locale = new Locale("", countryCode);
                    java.util.Currency currencyInfo = java.util.Currency.getInstance(locale);
                    String currencyCode = currencyInfo.getCurrencyCode();
                    String currencyName = currencyInfo.getDisplayName(new Locale(savedLanguage));

                    // التبسيط: إذا كانت اللغة عربية، ننشئ رمزاً من أول حرفين
                    String symbol;
                    if (savedLanguage.equals("ar") && currencyName != null && currencyName.length() >= 2) {
                        // للعربية: أول حرفين مع نقطة بينهما
                        symbol = currencyName.substring(0, 1) + "." + currencyName.substring(1, 2);
                    } else {
                        // للغات الأخرى: نستخدم كود العملة كما هو
                        symbol = currencyCode;
                    }

                    Log.d("LocalCurrency", "العملة المكتشفة: " + "countryCode: " + countryCode +
                            ", currencyCode: " + currencyCode + ", currencyName: " + currencyName +
                            ", symbol: " + symbol);
                    addCurrencyToDatabase(currencyName, symbol, currencyCode);
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


    /**
     * تعديل الدالة الافتراضية أيضاً
     */
    private void addDefaultCurrencyAndProceed() {
        String defaultName = getString(R.string.local_currency);
        String defaultCode = "LOC";
        String symbol;

        if (savedLanguage.equals("ar") && defaultName.length() >= 2) {
            symbol = defaultName.substring(0, 1) + "." + defaultName.substring(1, 2);
        } else {
            symbol = defaultCode;
        }

        addCurrencyToDatabase(defaultName, symbol, defaultCode);
    }

    private void addDefaultCurrencyAndProceed1() {
        addCurrencyToDatabase(getString(R.string.local_currency), "l", "LOC");
    }

    private void addCurrencyToDatabase(String name, String symbol, String code) {
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
            if (db.currencyDao().getCurrencyCount() < 1) {
                Currency existing = repository.getCurrencyByName(name);
                Log.e(TAG, "defaultCurrencyName: " + name);
                if (existing == null) {
                    Currency newCurrency = new Currency();
                    newCurrency.setName(name);
                    newCurrency.setOwnerUID(uid);
                    newCurrency.setSyncStatus("NEW");
                    newCurrency.setSymbol(symbol);
                    newCurrency.setCode(code);
                    newCurrency.setDefault(true);
                    newCurrency.setFirestoreId(UUIDGenerator.generateSequentialUUID().toString());
                    newCurrency.setLastModified(System.currentTimeMillis());
                    syncPreferences.setLocalCurrency(name.trim());
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
                    //showLoading(false);
                    runOnUiThread(this::navigateToMainActivity);
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
                navigateToMainActivity();
            });
        });
    }

    private void navigateToMainActivity() {
        // إلغاء أي عمليات معلقة أولاً
        hideProgressDialog();
        // التأكد من إزالة جميع المراقبين
        if (viewModelUserProfile.getUserProfile().hasObservers()) {
            viewModelUserProfile.getUserProfile().removeObservers(this);
        }
        Intent intent = new Intent(this, MainActivity.class);
        if (referrerUid != null) {
            intent.putExtra("REFERRER_UID", referrerUid);
        }
       // Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        isFirstRunGuest = prefs.getBoolean("first_run_guest", true);

        if (isFirstRun || isFirstRunGuest) {

            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.user_remove_tit))
                    .setMessage(getString(R.string.first_start_message))
                    .setPositiveButton(getString(R.string.continue_button), (dialog, which) -> {
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean("isNewUser", false);
                        editor.putBoolean("first_run_guest", false);
                        editor.putBoolean("first_run", false);
                        editor.apply();
                        startActivity(intent);
                        finish();
                    })
                    .setCancelable(false)
                    .show();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("first_run", false);
            editor.putBoolean("first_run_guest", false);
            editor.apply();
        } else {
            // تأخير بسيط لضمان استقرار الانتقال
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("isNewUser", false);
                editor.apply();
                startActivity(intent);
                finish();
            }, 100);
        }
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
            Log.d(TAG, "انشاء حساب للضيف" + " guestUID: " + guestUID + " isGuest: " + isGuest);
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
            newUser.setApp_Version(versionManager.getCurrentVersionName());
            DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();
            String deviceId = currentDevice.getDeviceId();
            newUser.setDeviceId(deviceId);
            // ✅ حفظ المستخدم محلياً أولاً في SQLite
            saveGuestUserLocally(newUser);
            if (isNetworkAvailable()) {
                saveGuestToFirestore(newUser);
            }
            // ✅ محاولة الحفظ في Firestore إذا كان هناك اتصال

            checkLocationPermission();
        } catch (Exception e) {
            Log.e(TAG, "createNewUserGuest Error: " + e);
            // ✅ الاستمرار حتى مع وجود خطأ
//            setupGuestData();
            showProgressDialog(getString(R.string.preper_guest_account));
            checkLocationPermission();
        }
    }

    private void saveGuestToFirestore(User guestUser) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        Map<String, Object> guestDataMap = new HashMap<>();
        guestDataMap.put("ownerUID", guestUID.trim());
        guestDataMap.put("userType", "guest");
        guestDataMap.put("is_premium", false);
        guestDataMap.put("created_at", User.getCurrentLocalDateTime());
        guestDataMap.put("last_login", User.getCurrentLocalDateTime());
        guestDataMap.put("app_Version", versionManager.getCurrentVersionName());
        guestDataMap.put("login_count", 1);
        guestDataMap.put("max_devices", 1);
        guestDataMap.put("transactions_count", 0);
        guestDataMap.put("transactionsCount", 0);
        guestDataMap.put("ad_rewards", 0);
        guestDataMap.put("referral_rewards", 0);
        guestDataMap.put("lastModified", System.currentTimeMillis());
        DeviceInfo deviceInfo = licenseManager.getCurrentDeviceInfo();
        guestDataMap.put("deviceId", deviceInfo.getDeviceId()); // ✅ استخدام deviceId كحقل عادي
        guestDataMap.put("deviceName", deviceInfo.getDeviceName());
        guestDataMap.put("deviceModel", deviceInfo.getDeviceModel());
        firestore.collection("guests").document(guestUID.trim()).set(guestDataMap)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "تم إنشاء حساب الضيف في Firestore: " + guestUID);
                        new Thread(() -> {
                            try {
                                // حفظ في جدول users المحلي مع تمييزه كضيف
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putString("guest_state", "SAVE");
                                editor.apply();
                                Log.d(TAG, "تم تحديث المستخدم الضيف محلياً في SQLite: " + guestUser.getOwnerUID());
                            } catch (Exception e) {
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putString("guest_state", "NEW");
                                editor.apply();
                                Log.e(TAG, "خطأ في تحديث الضيف محلياً: " + e.getMessage());
                            }
                        }).start();
                    } else {
                        Log.e(TAG, "فشل إضافة الضيف في Firestore، سيتم المزامنة لاحقاً: " + task.getException());
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("guest_state", "NEW");
                        editor.apply();
                    }
//                    .
//                    setupGuestData();

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "فشل إضافة الضيف في Firestore: " + e.getMessage());
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("guest_state", "NEW");
                    editor.apply();
                });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
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

    private void showAlreadyRegisteredMessage() {
        Snackbar snackbar = Snackbar.make(rootView,
                getString(R.string.referral_already_registered),
                Snackbar.LENGTH_LONG);

        // إضافة زر للشرح أكثر
        snackbar.setAction(getString(R.string.menu_more), v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.about_referral))
                    .setMessage(getString(R.string.about_referral_message))
                    .setPositiveButton(getString(R.string.ar_text_5_3), null)
                    .show();
        });

        snackbar.show();
    }

}