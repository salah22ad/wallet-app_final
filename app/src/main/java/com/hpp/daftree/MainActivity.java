package com.hpp.daftree;

import static com.hpp.daftree.helpers.PreferenceHelper.applyLocale;


import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Geocoder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.multidex.BuildConfig;
import androidx.navigation.NavController;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.viewpager2.widget.ViewPager2;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.hpp.daftree.adapters.AccountTypesPagerAdapter;
import com.hpp.daftree.adapters.AccountsAdapter;
import com.hpp.daftree.dailyreminder.DailyReminderManager;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.databinding.ActivityMainBinding;
import com.hpp.daftree.databinding.DialogAccountOptionsBinding;
import com.hpp.daftree.databinding.DialogEditAccountBinding;
import com.hpp.daftree.dialogs.DeviceManagementDialog;
import com.hpp.daftree.dialogs.HelpDialog;
import com.hpp.daftree.dialogs.InvoiceDialog;
import com.hpp.daftree.dialogs.LanguageDialog;
import com.hpp.daftree.dialogs.LanguageViewModel;
import com.hpp.daftree.dialogs.PurchaseCodeDialog;
import com.hpp.daftree.dialogs.RateAppDialog;
import com.hpp.daftree.dialogs.ReferralRewardDialog;
import com.hpp.daftree.dialogs.ReportsDialog;
import com.hpp.daftree.dialogs.TutorialDialog;
import com.hpp.daftree.dialogs.UpdateAppDialog;
import com.hpp.daftree.dialogs.UpdateDialog;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.helpers.LanguageHelper;
import com.hpp.daftree.helpers.PreferenceHelper;
import com.hpp.daftree.models.AccountWithBalance;
import com.hpp.daftree.models.AppLockManager;

import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.database.DeviceInfo;
import com.hpp.daftree.database.User;
import com.hpp.daftree.syncmanagers.FirestoreSyncManager;
import com.hpp.daftree.syncmanagers.PendingTxCheckWorker;
import com.hpp.daftree.syncmanagers.RestoreHelper;
import com.hpp.daftree.syncmanagers.SyncPreferences;
import com.hpp.daftree.ui.AccountDetailsActivity;
import com.hpp.daftree.ui.AccountListFragment;
import com.hpp.daftree.ui.AccountsTypeActivity;
import com.hpp.daftree.ui.AddTransactionActivity;
import com.hpp.daftree.ui.BaseActivity;
import com.hpp.daftree.ui.ContactActivity;
import com.hpp.daftree.ui.CurrenciesActivity;
import com.hpp.daftree.ui.CurrencyViewModel;
import com.hpp.daftree.ui.DeleteFromFirestoreActivity;
import com.hpp.daftree.ui.ProfileActivity;
import com.hpp.daftree.ui.WebServerActivity;
import com.hpp.daftree.utils.GoogleAuthHelper;
import com.hpp.daftree.utils.LicenseManager;
import com.hpp.daftree.notifications.NotificationChecker;
import com.hpp.daftree.utils.ReferralManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.google.firebase.firestore.FirebaseFirestore;
//import com.hpp.daftree.utils.ReferralNotificationListener;
import com.hpp.daftree.utils.SecureLicenseManager;
import com.hpp.daftree.models.SnackbarHelper;
import com.hpp.daftree.utils.TamperDetection;
import com.hpp.daftree.utils.VersionManager;
//import nl.dionsegijn.konfetti.KonfettiView;
//import nl.dionsegijn.konfetti.models.Shape;
//import nl.dionsegijn.konfetti.models.Size;
import com.hpp.daftree.utils.EdgeToEdgeUtils;

public class MainActivity extends BaseActivity implements
        AccountsAdapter.OnAccountInteractionListener,
        NavigationView.OnNavigationItemSelectedListener, LicenseManager.AuthStateListener,
        ReferralRewardDialog.OnReferralActionListener {

    private ActivityMainBinding binding;
    private static final String TAG = "MainActivity";
    private static final String TAG1 = "Testing";
    private NavController navController;
    private AppBarConfiguration appBarConfiguration;
    private static final int STORAGE_PERMISSION_CODE = 101;
    private static final int LOGIN_REQUEST_CODE = 1001;
    private SyncPreferences sharedPrefsManager;
    private GoogleAuthHelper googleAuthHelper;
    private NavigationView navigationViewDrawer;
    private DaftreeRepository repository;
    private ActivityResultLauncher<String[]> openDocumentLauncher;
    private final boolean hasUsdTransactions = false;
    private final boolean hasSarTransactions = false;
    private ActivityResultLauncher<Intent> backupLauncher;
    private boolean isProgrammaticScroll = false; // لمنع الحلقات اللانهائية
    private AppLockManager lockManager;
    private ProfileViewModel profileViewModel;
    private MainViewModel mainViewModel;
    private CurrencyViewModel currencyViewModel;
    private AccountTypeViewModel accountTypeViewModel;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    private String currentUserEmail;
    private final List<Currency> availableCurrencies = new ArrayList<>(); // **قائمة ديناميكية**
    private int currentCurrencyIndex = 0;
    private final List<String> activeCurrencies = new ArrayList<>(); // **قائمة بالعملات النشطة فقط**
    private List<AccountType> accountTypesList = new ArrayList<>();
    private NavigationView navigationView;
    private DrawerLayout drawerLayout;
    private AccountTypesPagerAdapter pagerAdapter;

    // متغير لتتبع ما إذا كان السحب يحدث داخل RecyclerView
    private boolean isScrollingRecyclerView = false;

    private FusedLocationProviderClient fusedLocationClient;
    private User currentUserData;
    private LicenseManager licenseManager;
    private ReferralManager referralManager;
    private VersionManager versionManager;
    private LanguageViewModel languageViewModel;
    private boolean isApplyingLanguage = false;
    private ListenerRegistration licenseListener;
    private SharedPreferences prefs;
//    private RewardManager rewardManager;

    private String localLanguage = "";
    private AppDatabase appDatabase;
    boolean isGuest = false;
    private String guestUID;
    private DeviceBanManager deviceBanManager;
    private boolean isSyncCompleted = false;
    private boolean isSyncStop = false;
    private boolean isDataLoaded = false;
    private String preAppliedLanguage = "";
    private DailyReminderManager dailyReminderManager;
    private String referrerUid;
    private final MutableLiveData<Boolean> syncCompletionLiveData = new MutableLiveData<>(false);
    //    private KonfettiView konfettiView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeUtils.applyEdgeToEdge(this, binding.toolbar);
        EdgeToEdgeUtils.applyBottomInset(binding.viewPager);

        MyApplication.applyGlobalTextWatcher(binding.getRoot());
//        rewardManager = new RewardManager();
        completelyReinitializeDataLayer();
        appDatabase = AppDatabase.getDatabase(getApplication());
        mainViewModel = new ViewModelProvider(this).get(MainViewModel.class);
        lockManager = new AppLockManager(this);
        profileViewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        repository = new DaftreeRepository(getApplication());
        googleAuthHelper = new GoogleAuthHelper(this, new LicenseManager(this), repository);
        licenseManager = new LicenseManager(this);
        licenseManager.setAuthStateListener(this);
        referralManager = new ReferralManager(this);
        versionManager = new VersionManager(this);
        dailyReminderManager = DailyReminderManager.getInstance(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        localLanguage = prefs.getString("language", "ar");
        preAppliedLanguage = prefs.getString("language", "ar");
//        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
//        getWindow().setStatusBarColor(Color.TRANSPARENT);
//
//        View toolbar = findViewById(R.id.toolbar);
//        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
//            int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
//            v.setPadding(0, topInset, 0, 0);
//            return insets;
//        });
//        handleIncomingDeepLink(getIntent());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    101
            );
        }
        new Handler().postDelayed(() -> {
        Intent intent = getIntent();
        if (intent != null ) {
            referrerUid = intent.getStringExtra("REFERRER_UID");
            if (referrerUid != null && !referrerUid.isEmpty()) {
//                Snackbar.make(binding.getRoot(), "أنت مسجل مسبقاً ولا يمكنك استخدام رابط الدعوة للحصول على النقاط.", Snackbar.LENGTH_LONG).show();
//                SnackbarHelper.showSnackbar(binding.getRoot(), getString(R.string.ar_long_text_40), SnackbarHelper.SnackbarType.ERROR);
                if (FirebaseAuth.getInstance().getCurrentUser() != null){
                    showReferralNotAvailableDialog();
                }else {
                    ReferralRewardDialog dialog = new ReferralRewardDialog(this, referrerUid, this);
                    dialog.show();

                  }
            }

        }
        }, 5000);

        deviceBanManager = new DeviceBanManager(this);
        guestUID = SecureLicenseManager.getInstance(this).guestUID();
        new Handler().postDelayed(() -> {
            startLicenseListener();
        }, 1000);
        //----------------------------------------------

        String mainMessage = "- قم بالنقر على زر الإضافة اسفل الشاشة لإضافة حساب جديد وعملية جديدة" + "\n" +
                "- عند إضافة حساب سيظهر في الشاشة قم بالنقر عليه لتصفح عملياتة المالية";
        new Handler().postDelayed(() -> {
        TutorialDialog.show(this, "MainActivity", getString(R.string.welcom_message));
        }, 2000);
        // 1. التحقق من المستخدم وتسجيل الدخول
        isGuest = SecureLicenseManager.getInstance(this).isGuest();
        if (isGuest) {
            guestUID = SecureLicenseManager.getInstance(this).guestUID();

        } else {
            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                startActivity(new Intent(this, LoginActivity.class));
                finish();
                return;
            }
            nourmalUser();
        }


        sharedPrefsManager = new SyncPreferences(this);
        currencyViewModel = new ViewModelProvider(this).get(CurrencyViewModel.class);
        accountTypeViewModel = new ViewModelProvider(this).get(AccountTypeViewModel.class); // <-- تهيئة
        profileViewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        // ✅ هذا هو المراقب الذي سيستجيب للتحديثات اللحظية
        profileViewModel.getUserProfile().observe(this, user -> {
            if (user != null) {
                Log.d("MainActivity", "تم تحديث بيانات المستخدم المحلية، جاري تحديث الواجهة...");
                this.currentUserData = user;
                updateNavigationMenuItems(); // تحديث القائمة بالبيانات الجديدة
            }
        });
        checkLocalCurrency();


        languageViewModel = new ViewModelProvider(this).get(LanguageViewModel.class);


        setupToolbarAndDrawer();
        setupViewPager();
        setupEventListeners();
        handleIntentExtras();

        isGuest = SecureLicenseManager.getInstance(this).isGuest();
        // ✅ بدء نظام التحقق من المزامنة (يجب أن يكون قبل setupObservers)

// ✅ إعداد المراقبين بعد نظام المزامنة
        setupObservers();
        setupCurrencyObserver(); // **مراقب جديد للعملات**
        referringNotification();
        NotificationChecker.checkForNotifications(MainActivity.this);
        PeriodicWorkRequest nightlyCheck =
                new PeriodicWorkRequest.Builder(PendingTxCheckWorker.class, 1, TimeUnit.DAYS)
                        .setInitialDelay(getDelayUntilMidnight(), TimeUnit.MILLISECONDS)
                        .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "PendingTxCheck", ExistingPeriodicWorkPolicy.UPDATE, nightlyCheck);
        checkForUpdatesOrHelp();
        new Handler().postDelayed(() -> {
            checkForAppUpdate(false);
        }, 10000);

        //setupLaunchers();
        setupOrientationAwareLayout();
        setupLanguageObserver();
//        scheduleDailyReminder();

    }

    int counter = 0;

    private void setupLanguageObserver1() {

        languageViewModel.getSelectedLanguage().observe(this, lang -> {

            if (lang != null && !lang.isEmpty()) {
                counter = +1;
                String currentLang = preAppliedLanguage;
                Log.e(TAG, "اللغة الجديدة: " + lang + " | اللغة الحالية: " + currentLang);

                if (!lang.equals(currentLang)) {
                    Log.e(TAG, "تم اكتشاف تغيير اللغة، جاري التطبيق...");
                    PreferenceHelper.setLanguage(this, lang);
                    applyLocale(this, lang);
                    restartAfterChangeLang();
//                    if (lang == "ar") {
//                        restartAfterChangeLang();
//                    } else {
//                        // ✅ إعادة تشغيل النشاط بعد فترة قصيرة
//                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                            Log.e(TAG, "جاري إعادة إنشاء النشاط...");
//                            recreate();
//                        }, 500);
//                    }
                } else {
                    Log.e(TAG, "اللغة نفسها، لا حاجة للتغيير");
                }

            }
        });
    }
    private void setupLanguageObserver() {
        languageViewModel.getSelectedLanguage().observe(this, lang -> {
            if (lang != null && !lang.isEmpty()) {
                String currentLang = preAppliedLanguage;
                Log.e(TAG, "اللغة الجديدة: " + lang + " | اللغة الحالية: " + currentLang);

                if (!lang.equals(currentLang)) {
                    Log.e(TAG, "تم اكتشاف تغيير اللغة، جاري التطبيق...");

                    // 🔥 استخدام الدالة الجديدة من BaseActivity
                    BaseActivity.applyLanguage(MainActivity.this, lang);

                    // تحديث التفضيلات
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("language", lang);
                    editor.apply();

                    // 🔥 إعادة إنشاء النشاط بشكل صحيح
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        Intent intent = new Intent(MainActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    }, 300);
                }
            }
        });
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == LOGIN_REQUEST_CODE && resultCode == RESULT_OK) {
            Log.e(TAG, "تم تسجيل الدخول بنجاح - إعادة تشغيل التطبيق من SplashActivity");

            // إعادة تشغيل التطبيق من SplashActivity
            restartAppFromSplash();
        }
    }

    private void restartAfterChangeLang() {

        // 2. الانتظار قليلاً لضمان إغلاق قاعدة البيانات
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // 3. إعادة تشغيل التطبيق من SplashActivity
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            // 4. إنهاء جميع الأنشطة
            finishAffinity();
            // 5. إنهاء العملية لضمان إعادة التهيئة الكاملة
            android.os.Process.killProcess(android.os.Process.myPid());
        }, 500); // تأخير 1 ثانية لضمان إغلاق قاعدة البيانات
    }

    /**
     * إعادة تشغيل التطبيق من SplashActivity
     */
    private void restartAppFromSplash() {
        Log.d(TAG, "إعادة تشغيل التطبيق من SplashActivity مع إعادة تهيئة قاعدة البيانات");

        // 1. إغلاق قاعدة البيانات الحالية بشكل صحيح
        closeDatabase();

        // 2. الانتظار قليلاً لضمان إغلاق قاعدة البيانات
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // 3. إعادة تشغيل التطبيق من SplashActivity
            Intent intent = new Intent(this, SplashActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            // 4. إنهاء جميع الأنشطة
            finishAffinity();
            // 5. إنهاء العملية لضمان إعادة التهيئة الكاملة
            android.os.Process.killProcess(android.os.Process.myPid());

        }, 1000); // تأخير 1 ثانية لضمان إغلاق قاعدة البيانات
    }

    private void closeDatabase() {
        try {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                AppDatabase db = AppDatabase.getDatabase(this);
                if (db != null) {
                    db.close();
                    Log.d(TAG, "تم إغلاق قاعدة البيانات بنجاح");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "خطأ في إغلاق قاعدة البيانات: " + e.getMessage());
        }
    }

    private void completelyReinitializeDataLayer() {
        try {
            Log.d(TAG, "إعادة تهيئة كاملة لطبقة البيانات");

            // 1. إعادة إنشاء AppDatabase (سيقوم Room بإعادة فتح الاتصال)
            appDatabase = AppDatabase.getDatabase(getApplication());

            // 2. إعادة إنشاء Repository
            repository = new DaftreeRepository(getApplication());

            // 3. إعادة إنشاء جميع ViewModels
            mainViewModel = new ViewModelProvider(this).get(MainViewModel.class);
            profileViewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
            currencyViewModel = new ViewModelProvider(this).get(CurrencyViewModel.class);
            accountTypeViewModel = new ViewModelProvider(this).get(AccountTypeViewModel.class);

            // 4. إعداد المراقبات
            setupObservers();
            setupCurrencyObserver();

            // 5. التحقق من البيانات بعد التهيئة
            verifyDataAfterReinitialization();

            Log.d(TAG, "تمت إعادة تهيئة طبقة البيانات بنجاح");

        } catch (Exception e) {
            Log.e(TAG, "خطأ في إعادة تهيئة طبقة البيانات: " + e.getMessage());
        }
    }

    private void verifyDataAfterReinitialization() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                int accountCount = repository.getAccountDao().getAccountsCount();
                int transactionCount = repository.getTransactionsDao().getTransactionsCount();

                Log.d(TAG, "📊 بعد إعادة التهيئة:");
                Log.d(TAG, "   - عدد الحسابات: " + accountCount);
                Log.d(TAG, "   - عدد العمليات: " + transactionCount);

                if (accountCount > 0) {
                    runOnUiThread(() -> {
                        if (mainViewModel != null) {
                            mainViewModel.refreshData();
                            Log.d(TAG, "✅ تم طلب تحديث البيانات في الواجهة");
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "خطأ في التحقق من البيانات: " + e.getMessage());
            }
        });
    }

    private void nourmalUser() {


        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        FirestoreSyncManager.getInstance().listenForReferralNotifications(currentUser.getUid(), this);


        if ((!versionManager.getFirestoreUser_isAdded() && googleAuthHelper.isSignedIn())) {
            Log.e(TAG, "updateUserInFirestore : " + " جاري تحديث بيانات المستخدم");
            googleAuthHelper.saveOrUpdateUserInFirestoreUpgrade(currentUser);
        }

        if (currentUser != null && currentUser.getEmail() != null) {
            currentUserEmail = currentUser.getEmail().trim();

        }

    }

    private boolean checkAppSaftey() {
        if (TamperDetection.isAppTampered(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("خطأ في الأمان")
                    .setMessage("تم اكتشاف تلاعب في التطبيق أو بيئة غير آمنة (مثل الروت أو توقيع غير صحيح). سيتم إغلاق التطبيق.")
                    .setPositiveButton("موافق", (dialog, which) -> {
                        finishAffinity();
                    })
                    .setCancelable(false)
                    .show();
            return false;
        }

        // 2. فحص وضع المطور
        if (TamperDetection.isDeveloperOptionsEnabled(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("وضع المطور مفعل")
                    .setMessage("تم اكتشاف تفعيل وضع المطور. قد يؤدي ذلك إلى ثغرات أمنية. يرجى إيقافه للمتابعة.")
                    .setPositiveButton("الانتقال إلى الإعدادات", (dialog, which) -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                            startActivity(intent);
                        } catch (Exception e) {
                            // في حال فشل الانتقال المباشر، يمكن توجيه المستخدم يدويًا
                            new AlertDialog.Builder(this)
                                    .setTitle("خطأ")
                                    .setMessage("تعذر الانتقال مباشرة إلى إعدادات المطور. يرجى إيقافها يدويًا من إعدادات الجهاز.")
                                    .setPositiveButton("موافق", (dialog2, which2) -> {
                                        finishAffinity();
                                    })
                                    .setCancelable(false)
                                    .show();
                        }
                        finishAffinity(); // إغلاق التطبيق بعد توجيه المستخدم
                    })
                    .setNegativeButton("إغلاق التطبيق", (dialog, which) -> {
                        finishAffinity();
                    })
                    .setCancelable(false)
                    .show();
            return false;
        }
        return true;
    }

    private long getDelayUntilMidnight() {
        Calendar now = Calendar.getInstance();
        Calendar midnight = (Calendar) now.clone();
        midnight.add(Calendar.DAY_OF_YEAR, 1);
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        return midnight.getTimeInMillis() - now.getTimeInMillis();
    }



    private void checkForUpdatesOrHelp() {
        if (!versionManager.isFirstLaunch() && versionManager.isNewVersion() && !versionManager.isUpdateShownForCurrentVersion()) {
            if ((versionManager.getLastKnownVersionCode() != -1)) {
                showUpdateDialog();
                versionManager.markUpdateAsShown(); // نعلم أننا عرضنا الديالوج لهذا الإصدار
            }
        }
    }

    private void showUpdateDialog() {
        // استخدام isMinorUpdate لتحديد نوع الرسالة المناسبة
        boolean isMinorUpdate = versionManager.isMinorUpdate();
        UpdateDialog updateDialog = new UpdateDialog(this);
        updateDialog.setCancelable(false);
        updateDialog.setOnDismissListener(dialog -> {
            String message = isMinorUpdate ?
                    "تم تطبيق التحديثات الصغيرة" :
                    "تم استعراض التحديثات الجديدة";
            //  Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        });

        updateDialog.show();
    }

    private void referringNotification() {
        FirestoreSyncManager sync = FirestoreSyncManager.getInstance();
        sync.setReferralNotificationListener((userId, points, notiMessegeTitel, notiMessege) -> {
            runOnUiThread(() -> {
                // ديالوج
                new AlertDialog.Builder(this)
                        .setTitle(notiMessegeTitel)
                        .setMessage(notiMessege)
                        .setPositiveButton(getString(R.string.continue_button), null)
                        .show();
// إشعار
                NotificationService ns = new NotificationService(this);
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED) {
                    ns.showRewardNotification(notiMessegeTitel,
                            notiMessege);
                }
            });
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupEventListeners() {
        setupAddTransactionButtonGlow();
        binding.addTransactionButton.setOnClickListener(v -> {
            if (currentUserData == null) {
                Toast.makeText(this, "جاري تحميل بيانات المستخدم...", Toast.LENGTH_SHORT).show();
            }

//            if (licenseManager.canCreateTransaction()) {
//                Intent intent = new Intent(MainActivity.this, AddTransactionActivity.class);
//                intent.putExtra("CURRENCY", availableCurrencies.get(currentCurrencyIndex).name);
//                startActivity(intent);
//                licenseManager.incrementTransactionCount();
//            } else {
//                showUpgradeDialog();
//            }
        });

        binding.addTransactionButton.setOnClickListener(v -> {
            // **تصحيح: التأكد من أن القائمة ليست فارغة قبل محاولة الوصول إليها**
            if (availableCurrencies.isEmpty() || currentCurrencyIndex >= availableCurrencies.size()) {
                Toast.makeText(this, "جاري تحميل العملات...", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(MainActivity.this, AddTransactionActivity.class);
            // **تصحيح: إرسال اسم العملة من القائمة الديناميكية**
            intent.putExtra("CURRENCY", availableCurrencies.get(currentCurrencyIndex).name);
            startActivity(intent);
        });
        binding.currencyTextView.setOnClickListener(v -> cycleCurrency());

        openDocumentLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                startRestoreProcess(uri);
            }
        });
        backupLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    performBackup(uri);
                }
            }
        });

        // إعداد GestureDetector للسحب العمودي
        final GestureDetector gestureDetector = new GestureDetector(this, new VerticalSwipeListener());

        // إعداد OnTouchListener للـ ViewPager2
        binding.viewPager.setOnTouchListener((v, event) -> {
            // إذا كان السحب يحدث داخل RecyclerView، لا نعالج السحب لتغيير العملة
            if (isScrollingRecyclerView) {
                return false;
            }

            // معالجة السحب لتغيير العملة فقط في المناطق الفارغة
            return gestureDetector.onTouchEvent(event);
        });
    }

    private void simulateWatchAd() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("جاري عرض الإعلان...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            progressDialog.dismiss();
            licenseManager.addAdRewardTransactions(currentUserData); // <-- استدعاء الدالة الجديدة
//            Toast.makeText(this, "تهانينا! لقد حصلت على " + LicenseManager.AD_REWARD_TRANSACTIONS + " عمليات مجانية.", Toast.LENGTH_LONG).show();
//            checkUserLicenseAndSetupUI();
        }, 3000); // محاكاة 3 ثواني
    }

    private void updateNavigationMenuItems() {
        if (currentUserData == null) return;
        SecureLicenseManager licenceData = SecureLicenseManager.getInstance(this);
        NavigationView navigationView = findViewById(R.id.nav_view);
        Menu menu = navigationView.getMenu();
        boolean isSignedIn = (FirebaseAuth.getInstance().getCurrentUser() != null);
//        boolean isPremium = currentUserData.isIs_premium();
        boolean isPremium = licenseManager.isPremiumUser();
        boolean shouldShowUpgradeOptions = !isPremium;
        menu.findItem(R.id.nav_purchase_app).setVisible(shouldShowUpgradeOptions);
        MenuItem adminItem = menu.findItem(R.id.nav_admin_dashboard);
        String userType = sharedPrefsManager.getUserType() != null ? sharedPrefsManager.getUserType() : "user";
        if (userType != null && userType.equals("admin")) {
            adminItem.setVisible(true);
//            binding.lltest.setVisibility(View.VISIBLE);
        } else {
            adminItem.setVisible(false);
//            binding.lltest.setVisibility(View.GONE);
        }
//        MenuItem notificationItem = menu.findItem(R.id.nav_daily_notification);
//
//        // الحصول على التخطيط المخصص
//        View actionView = notificationItem.getActionView();
//        if (actionView != null) {
//            setupNotificationSwitch(actionView, notificationItem);
//        }
//
//        // إعداد مستمع للنقر على العنصر نفسه (بدون التبديل)
//        notificationItem.setOnMenuItemClickListener(item -> {
//            // عند النقر على العنصر، نفتح إعدادات الإشعارات
//            openNotificationSettings();
//            return true;
//        });
        Log.d("MainActivity", "تم استيراد بيانات الترخيص من Firestore: " +
                "userType=" + userType);

//        MenuItem watchAdItem = menu.findItem(R.id.nav_watch_ad);
//        watchAdItem.setVisible(shouldShowUpgradeOptions);
//        int Ad_rewards = licenceData.getAdRewards();
//        if (Ad_rewards > 0) {
//            watchAdItem.setTitle("مشاهدة إعلان (+" + Ad_rewards + " مكافأة)");
//        } else {
//            watchAdItem.setTitle("مشاهدة إعلان");
//        }

        MenuItem inviteItem = menu.findItem(R.id.nav_invite_friend);
        //inviteItem.setVisible(shouldShowUpgradeOptions); // خيار الدعوة متاح دائماً للمسجلين
        int Referral_rewards = licenceData.getReferralRewards();
        inviteItem.setVisible(true);
        if (Referral_rewards > 0) {
            inviteItem.setTitle(getString(R.string.menu_invite_friend) + "(+" + Referral_rewards + ")");
        } else {
            inviteItem.setTitle(getString(R.string.menu_invite_friend));
        }
        MenuItem manageDevice = menu.findItem(R.id.nav_manage_devices);
        if (isGuest) {
            manageDevice.setVisible(false);
        } else {
            manageDevice.setVisible(true);
        }
    }

    private void updateIconColor(ImageView icon, boolean isEnabled) {
        if (isEnabled) {
            // اللون الأزرق عند التفعيل
            icon.setColorFilter(ContextCompat.getColor(this, R.color.blue_500), android.graphics.PorterDuff.Mode.SRC_IN);
        } else {
            // اللون الأساسي (الرمادي) عند الإيقاف
            icon.setColorFilter(ContextCompat.getColor(this, R.color.material_on_surface_emphasis_medium), android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }

    private void showNotificationStatusMessage(boolean isEnabled) {
        String message = isEnabled ?
                "تم تفعيل الإشعارات اليومية" :
                "تم إيقاف الإشعارات اليومية";

//        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void openNotificationSettings() {
        // فتح إعدادات الإشعارات في النظام
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
            intent.putExtra("app_package", getPackageName());
            intent.putExtra("app_uid", getApplicationInfo().uid);
        }
        startActivity(intent);
    }
    private void onReferralRewardReceived(String userId, long points, String notiMessegeTitel, String notiMessege) {
        runOnUiThread(() -> {

            new MaterialAlertDialogBuilder(this)
                    .setTitle(notiMessegeTitel)
                    .setMessage(notiMessege)
                    .setPositiveButton("حسناً", null)
                    .show();
// إشعار
            NotificationService ns = new NotificationService(this);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                ns.showRewardNotification(notiMessegeTitel,
                        notiMessege);
            }
        });

    }


    private class VerticalSwipeListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            float diffY = e2.getY() - e1.getY();
            if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffY > 0) {
                    // سحب للأسفل: التمرير للخلف
                    handleSmartNavigation(binding.viewPager.getCurrentItem(), -1);
                } else {
                    // سحب للأعلى: التمرير للأمام
                    handleSmartNavigation(binding.viewPager.getCurrentItem(), 1);
                }
                return true;
            }
            return false;
        }
    }

    public void setRecyclerViewScrolling(boolean isScrolling) {
        this.isScrollingRecyclerView = isScrolling;
    }

    private void handleSmartNavigation(final int targetPosition, final int direction) {
        int totalPages = pagerAdapter.getItemCount();
        int nextPosition = targetPosition;

        if (direction == 1) { // التمرير للأمام
            nextPosition = (targetPosition + 1) % totalPages;
        } else { // التمرير للخلف
            nextPosition = (targetPosition - 1 + totalPages) % totalPages;
        }

        if (targetPosition == 0) {
            mainViewModel.setFilter(null);
            return;
        }
        String targetType = accountTypesList.get(targetPosition - 1).name;

        AppDatabase.databaseWriteExecutor.execute(() -> {
            int count = repository.getAccountDao().hasAnyTransactionsForType(targetType);
            if (count > 0) {
                // النوع يحتوي على بيانات، قم بتحديث الفلتر
                runOnUiThread(() -> mainViewModel.setFilter(targetType));
            } else {
                // النوع فارغ، انتقل إلى التالي بشكل دائري
                runOnUiThread(() -> {
                    // **الحل هنا:** الكود الآن يقوم بالبحث بشكل دائري ومستمر
//                    int nextPosition = (targetPosition + direction) % pagerAdapter.getItemCount();
                    isProgrammaticScroll = true;
                    binding.viewPager.setCurrentItem((targetPosition + direction) % pagerAdapter.getItemCount());
                });
            }
        });
    }

    private void performBackup(Uri destinationUri) {
        try {
            File dbFile = getDatabasePath(AppDatabase.getDatabase(this).getOpenHelper().getDatabaseName());
            try (InputStream source = new FileInputStream(dbFile);
                 OutputStream destination = getContentResolver().openOutputStream(destinationUri)) {

                byte[] buffer = new byte[1024];
                int length;
                while ((length = source.read(buffer)) > 0) {
                    destination.write(buffer, 0, length);
                }
                Toast.makeText(this, "تم حفظ النسخة الاحتياطية بنجاح!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "فشل النسخ الاحتياطي: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    private void setupToolbarAndDrawer() {
        setSupportActionBar(binding.toolbar);
        drawerLayout = binding.drawerLayout;
        navigationView = binding.navView;
        navigationView.setNavigationItemSelectedListener(this);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        toggle.getDrawerArrowDrawable().setColor(getResources().getColor(R.color.menu_white));
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        View headerView = navigationView.getHeaderView(0);
        MenuItem loginItem = navigationView.getMenu().findItem(R.id.nav_login);
        if (isGuest) {
            loginItem.setVisible(true);
            binding.warningText.setVisibility(View.VISIBLE);
        } else {
            loginItem.setVisible(false);
            binding.warningText.setVisibility(View.GONE);
        }
// تغيير لون الأيقونة
//        Drawable icon = loginItem.getIcon();
//        if (icon != null) {
//            icon = icon.mutate();
//            icon.setColorFilter(ContextCompat.getColor(this, R.color.login_color), PorterDuff.Mode.SRC_IN);
//            loginItem.setIcon(icon);
//        }

        TextView navUserEmail = headerView.findViewById(R.id.textViewUserEmail);


        TextView navVersionNo = headerView.findViewById(R.id.textViewVersionNo);
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = pInfo.versionName;
            String appName = getString(R.string.app_name);
            String versionText = getString(R.string.version_text, appName, versionName);//String.format(Locale.US, "%s - الإصدار %s", appName, versionName);
            navVersionNo.setText(versionText);
        } catch (PackageManager.NameNotFoundException e) {
            navVersionNo.setText(R.string.app_name); // في حالة حدوث خطأ، اعرض اسم التطبيق فقط
        }
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUserEmail != null && !isGuest) {
            navUserEmail.setText(currentUserEmail);
            updateMenuVisibility(); // تحديث رؤية القوائم بعد تعيين البريد الإلكتروني
        } else {
            navUserEmail.setText(R.string.local_email);
        }
//        Menu navMenu = navigationView.getMenu();
//        currentUserEmail = currentUser.getEmail().toString().trim();
//        Log.e("eMail", currentUserEmail);
        // إعداد مفتاح قفل كلمة المرور
        MenuItem passwordLockItem = navigationView.getMenu().findItem(R.id.nav_password_lock);
        SwitchMaterial passwordSwitch = passwordLockItem.getActionView().findViewById(R.id.menu_switch);

        // إعداد مفتاح قفل البصمة
        MenuItem biometricLockItem = navigationView.getMenu().findItem(R.id.nav_biometric_lock);
        SwitchMaterial biometricSwitch = biometricLockItem.getActionView().findViewById(R.id.menu_switch);

        MenuItem generalNotificationsItem = navigationView.getMenu().findItem(R.id.nav_general_notifications);
        SwitchMaterial generalSwitch = generalNotificationsItem.getActionView().findViewById(R.id.menu_switch);

        // مفتاح الإشعارات اليومية
        MenuItem dailyRemindersItem = navigationView.getMenu().findItem(R.id.nav_daily_reminders);
        SwitchMaterial dailySwitch = dailyRemindersItem.getActionView().findViewById(R.id.menu_switch);

        // تعيين الحالة الأولية بناءً على الإعدادات المحفوظة
        updateNotificationSwitches(generalSwitch, dailySwitch);

        // إعداد مستمعين للتغييرات
        generalSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                handleGeneralNotificationsToggle(isChecked, dailySwitch);
            }
        });

        dailySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                handleDailyRemindersToggle(isChecked, generalSwitch);
            }
        });

        // تحديث حالة المفاتيح بناءً على الحالة المحفوظة
        updateLockSwitches(passwordSwitch, biometricSwitch);

        // إضافة مستمعات للأحداث
        passwordSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) { // تأكد من أن التغيير جاء من المستخدم
                handlePasswordLock(isChecked);
            }
        });

        biometricSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                handleBiometricLock(isChecked);
            }
        });
        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
            }

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                // عند فتح القائمة الجانبية، قم بتحديث رؤية العناصر
                updateMenuVisibility();
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
            }

            @Override
            public void onDrawerStateChanged(int newState) {
            }
        });
    }
    /**
     * تحديث حالة مفاتيح الإشعارات
     */
    private void updateNotificationSwitches(SwitchMaterial generalSwitch, SwitchMaterial dailySwitch) {
        boolean isGeneralEnabled = dailyReminderManager.areGeneralNotificationsEnabled();
        boolean isDailyEnabled = dailyReminderManager.isEnabled();

        generalSwitch.setChecked(isGeneralEnabled);
        dailySwitch.setChecked(isDailyEnabled);

        // تعطيل مفتاح الإشعارات اليومية إذا كانت الإشعارات العامة معطلة
//        dailySwitch.setEnabled(isGeneralEnabled);
    }

    /**
     * معالجة تبديل الإشعارات العامة
     */
    private void handleGeneralNotificationsToggle(boolean isEnabled, SwitchMaterial dailySwitch) {
        dailyReminderManager.setGeneralNotificationsEnabled(isEnabled);

        if (isEnabled) {
            Toast.makeText(this, getString(R.string.general_notifications_enabled), Toast.LENGTH_SHORT).show();
            // تمكين مفتاح الإشعارات اليومية
            dailySwitch.setEnabled(true);
        } else {
            Toast.makeText(this, getString(R.string.general_notifications_set_disabled), Toast.LENGTH_SHORT).show();
            // تعطيل مفتاح الإشعارات اليومية وإيقافها
            dailySwitch.setEnabled(false);
//            dailySwitch.setChecked(false);
        }
    }

    /**
     * معالجة تبديل الإشعارات اليومية
     */
    private void handleDailyRemindersToggle(boolean isEnabled, SwitchMaterial generalSwitch) {
        // التأكد من أن الإشعارات العامة مفعلة أولاً
//        if (isEnabled && !dailyReminderManager.areGeneralNotificationsEnabled()) {
//            Toast.makeText(this, "يجب تفعيل الإشعارات العامة أولاً", Toast.LENGTH_SHORT).show();
//            // إعادة المفتاح إلى وضعه السابق
//            updateNotificationSwitches(generalSwitch, (SwitchMaterial) generalSwitch);
//            return;
//        }

        dailyReminderManager.setEnabled(isEnabled);

        if (isEnabled) {
            Toast.makeText(this, getString(R.string.notifications_enabled), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.notifications_disabled), Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * تحديث جميع مفاتيح القائمة الجانبية (القفل + الإشعارات)
     */
    private void updateAllNavigationSwitches() {
        NavigationView navigationView = findViewById(R.id.nav_view);
        Menu menu = navigationView.getMenu();

        // تحديث مفاتيح القفل (الكود الحالي)
        MenuItem passwordLockItem = menu.findItem(R.id.nav_password_lock);
        SwitchMaterial passwordSwitch = passwordLockItem.getActionView().findViewById(R.id.menu_switch);

        MenuItem biometricLockItem = menu.findItem(R.id.nav_biometric_lock);
        SwitchMaterial biometricSwitch = biometricLockItem.getActionView().findViewById(R.id.menu_switch);

        updateLockSwitches(passwordSwitch, biometricSwitch);

        // تحديث مفاتيح الإشعارات (الجديدة)
        MenuItem generalNotificationsItem = menu.findItem(R.id.nav_general_notifications);
        SwitchMaterial generalSwitch = generalNotificationsItem.getActionView().findViewById(R.id.menu_switch);

        MenuItem dailyRemindersItem = menu.findItem(R.id.nav_daily_reminders);
        SwitchMaterial dailySwitch = dailyRemindersItem.getActionView().findViewById(R.id.menu_switch);

        updateNotificationSwitches(generalSwitch, dailySwitch);
    }
    private void showLanguageDialog() {
        LanguageDialog dialog = new LanguageDialog(this, new LanguageDialog.OnLanguageSelectedListener() {
            @Override
            public void onLanguageSelected(String languageCode) {
                languageViewModel.setLanguage(languageCode);
                counter = 0;
                // ✅ أيضًا نفذ المنطق القديم للتوافق
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("language", languageCode);
                localLanguage = languageCode;
                editor.apply();

                PreferenceHelper.setLanguage(MainActivity.this, languageCode);
                applyLocale(MainActivity.this, languageCode);
                Resources localizedResources = LanguageHelper.getLocalizedResources(MainActivity.this);
                Log.e(TAG, "تم اختيار اللغة من الديالوج: " + languageCode);

                // ✅ إعادة التشغيل بعد فترة
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    recreate();
                }, 500);


            }
        });
        dialog.show();
    }

    private void updateMenuVisibility() {

        Menu navMenu = navigationView.getMenu();
        View headerView = navigationView.getHeaderView(0);
        TextView navUserEmail = headerView.findViewById(R.id.textViewUserEmail);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUserEmail != null && !isGuest) {
            navUserEmail.setText(currentUserEmail);
        } else {
            navUserEmail.setText(R.string.local_email);
        }
        LinearLayout icPrim = headerView.findViewById(R.id.llIcPrim);
        final MenuItem adminDashboardItem = navMenu.findItem(R.id.nav_backup_data);
        String userType = sharedPrefsManager.getUserType() != null ? sharedPrefsManager.getUserType() : "user";
        if (userType != null && userType.equals("admin")) {
            adminDashboardItem.setVisible(true);
        }else {
            adminDashboardItem.setVisible(false);
        }
        if (licenseManager.isPremiumUser()) {
            icPrim.setVisibility(View.VISIBLE);

        } else {
            icPrim.setVisibility(View.GONE);
            adminDashboardItem.setVisible(false);
        }

        final MenuItem adminDashboardItem2 = navMenu.findItem(R.id.nav_logout);
        final MenuItem adminDashboardItem3 = navMenu.findItem(R.id.nav_update_data);


//        if (currentUserEmail != null &&
//                (currentUserEmail.equalsIgnoreCase("salah22app@gmail.com") ||
//                        currentUserEmail.equalsIgnoreCase("salwasalah.8383@gmail.com") ||
//                        currentUserEmail.equalsIgnoreCase("salah22app@gmail.com") ||
//                        currentUserEmail.equalsIgnoreCase("salah22ad1@gmail.com") ||
//                        currentUserEmail.equalsIgnoreCase("salah22ad1122@gmail.com") ||
//                        currentUserEmail.equalsIgnoreCase("salah22ad3@gmail.com") ||
//                        currentUserEmail.equalsIgnoreCase("salah22ad@gmail.com") ||
//                        currentUserEmail.equalsIgnoreCase("salah22ad4544@gmail.com") ||
//                        currentUserEmail.equalsIgnoreCase("salah22dev@gmail.com"))) {
//            adminDashboardItem.setVisible(true);
//            adminDashboardItem2.setVisible(true);
//            adminDashboardItem3.setVisible(true);
//        } else {
//            adminDashboardItem.setVisible(false);
//            adminDashboardItem2.setVisible(false);
//            adminDashboardItem3.setVisible(false);
//        }
//        if (BuildConfig.DEBUG) {
//            adminDashboardItem.setVisible(true);
//            adminDashboardItem2.setVisible(true);
//            adminDashboardItem3.setVisible(true);
//            binding.lltest.setVisibility(View.VISIBLE);
//        } else {
//            adminDashboardItem.setVisible(false);
//            adminDashboardItem2.setVisible(false);
//            adminDashboardItem3.setVisible(false);
//            binding.lltest.setVisibility(View.GONE);
//        }
        updateAllNavigationSwitches();
    }

    private void setupCurrencyObserver() {
        currencyViewModel.getAllCurrencies().observe(this, currencies -> {
            if (currencies != null && !currencies.isEmpty()) {
                availableCurrencies.clear();
                availableCurrencies.addAll(currencies);
                updateUiForSelectedCurrency();
            }
        });
    }

    private void setupObservers() {
        // مراقب واحد وموحد لقائمة الحسابات والإجماليات
        mainViewModel.getAccounts().observe(this, accountWithBalances -> {
//            accountsAdapter.submitList(accountWithBalances);
            updateSummariesFromBalances(accountWithBalances);
        });
        mainViewModel.getCurrenciesWithTransactions().observe(this, activeCurrencyNames -> {
            if (activeCurrencyNames != null) {
                activeCurrencies.clear();
                activeCurrencies.addAll(activeCurrencyNames);
                // تأكد دائمًا من وجود العملة المحلية في القائمة النشطة للعرض
                if (!activeCurrencies.contains(MyApplication.defaultCurrencyName)) {
                    activeCurrencies.add(0, MyApplication.defaultCurrencyName);
                }
            }
        });
    }

    private void updateSummariesFromBalances(List<AccountWithBalance> accounts) {
        double totalDebit = 0.0;  // مجموع الأرصدة المدينة (عليك)
        double totalCredit = 0.0; // مجموع الأرصدة الدائنة (لك)

        if (accounts != null) {
            for (AccountWithBalance acc : accounts) {
                if (acc.balance < 0) { // الرصيد دائن (لك)
                    totalCredit += Math.abs(acc.balance);
                } else { // الرصيد مدين (عليك)
                    totalDebit += acc.balance;
                }
            }
        }

        binding.totalDebitTextView.setText(formatNumber(totalDebit));
        binding.totalCreditTextView.setText(formatNumber(totalCredit));
    }

    private String formatNumber(Double number) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        return nf.format(number != null ? number : 0.0);
    }

    private void cycleCurrency() {
        if (activeCurrencies.isEmpty() || availableCurrencies.isEmpty()) {
            Toast.makeText(this, "لا توجد عملات نشطة", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. احصل على اسم العملة الحالية من الواجهة
        String currentName = binding.currencyTextView.getText().toString();

        // 2. ابحث عن موقعها في قائمة العملات النشطة
        int currentIndexInActiveList = activeCurrencies.indexOf(currentName);

        // 3. احصل على اسم العملة التالية من القائمة النشطة
        int nextIndexInActiveList = (currentIndexInActiveList + 1) % activeCurrencies.size();
        String nextCurrencyName = activeCurrencies.get(nextIndexInActiveList);

        // 4. ابحث عن موقع الاسم الجديد في القائمة الكاملة لتحديث المؤشر الرئيسي
        for (int i = 0; i < availableCurrencies.size(); i++) {
            if (availableCurrencies.get(i).name.equals(nextCurrencyName)) {
                currentCurrencyIndex = i;
                break;
            }
        }

        // 5. قم بتحديث الواجهة والـ ViewModel
        updateUiForSelectedCurrency();
    }

    private void updateUiForSelectedCurrency() {
        if (availableCurrencies.isEmpty() || currentCurrencyIndex >= availableCurrencies.size())
            return;

        String selectedCurrencyName = availableCurrencies.get(currentCurrencyIndex).name;
        binding.currencyTextView.setText(selectedCurrencyName);
        mainViewModel.setCurrency(selectedCurrencyName);
    }

    private void setupBiometricAuthentication() {
        BiometricManager biometricManager = BiometricManager.from(this);

        switch (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                checkEnrolledFingerprints();
                break;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                showCustomAlert(getString(R.string.warning_title),
                        getString(R.string.fingerprint_not_supported),
                        () -> lockManager.setBiometricLock(false));
                break;
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                showCustomAlert(getString(R.string.warning_title),
                        getString(R.string.fingerprint_unavailable),
                        () -> lockManager.setBiometricLock(false));
                break;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                showFingerprintEnrollmentDialog();
                break;
        }
    }

    private void checkEnrolledFingerprints() {
        BiometricPrompt biometricPrompt = new BiometricPrompt(this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
//                        lockManager.enableBiometricLock();
                        lockManager.setBiometricLock(true);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        lockManager.setBiometricLock(false);
                        Toast.makeText(MainActivity.this, errString, Toast.LENGTH_SHORT).show();
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("تأكيد البصمة")
                .setDescription("ضع إصبعك للتحقق من البصمة")
                .setNegativeButtonText(getString(R.string.cancel))
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void showCustomAlert(String title, String message, Runnable onOk) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    if (onOk != null) onOk.run();
                })
                .show();
    }

    private void showFingerprintEnrollmentDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.warning_title))
                .setMessage(getString(R.string.fingerprint_not_enrolled))
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    Intent enrollIntent = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
                    enrollIntent.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                            BiometricManager.Authenticators.BIOMETRIC_WEAK);
                    try {
                        startActivity(enrollIntent);
                    } catch (ActivityNotFoundException e) {
                        e.printStackTrace();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    boolean isBiometricAuthentication = false;
    boolean addBioMetric = false;

    /**
     * عند محاولة تفعيل قفل البصمة
     */
    private void handleBiometricLock(boolean isEnabled) {
        if (isEnabled) {
            String lockType = lockManager.getLockType();
            boolean isPasswordLocked = "password".equals(lockType);
            if (!isPasswordLocked) {
                // عرض تنبيه لتفعيل كلمة المرور أولاً
                addBioMetric = true;
                showEnableBiometricWarningDialog();
            } else {
                // إذا كلمة المرور مفعلة مسبقاً، فعّل البصمة
                setupBiometricAuthentication();
            }
        } else {
            if ("biometric".equals(lockManager.getLockType())) {
                lockManager.disableLock();
                Toast.makeText(this, getString(R.string.biometric_disabled), Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * ديالوج تحذير قبل إعداد البصمة
     */
    private void showEnableBiometricWarningDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.warning_title))
                .setMessage(getString(R.string.enable_password_first))
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    showPasswordSetupDialog();
                })
                .setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
                    dialog.dismiss();
                    // ممكن ترجّع السويتش لوضعه السابق (غير مفعّل)
                    if ("biometric".equals(lockManager.getLockType())) {
                        lockManager.disableLock();
                        Toast.makeText(this, getString(R.string.biometric_disabled), Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void handlePasswordLock(boolean isEnabled) {
        if (isEnabled) {
            showPasswordSetupDialog();
        } else {
            // لا يمكن إلغاء قفل كلمة المرور إذا كان قفل البصمة مفعلًا
            if ("biometric".equals(lockManager.getLockType())) {
                showCustomAlert("تنبيه", "يجب إلغاء قفل البصمة أولاً قبل إلغاء كلمة المرور.", null);
//                binding.settingsRecyclerView.getAdapter().notifyDataSetChanged(); // إعادة المفتاح لوضعه
                return;
            }
            lockManager.disableLock();
            Toast.makeText(this, "تم إلغاء قفل كلمة المرور", Toast.LENGTH_SHORT).show();
//            binding.settingsRecyclerView.getAdapter().notifyDataSetChanged();
        }
    }

    /**
     * ديالوج إعداد كلمة المرور
     */
    private void showPasswordSetupDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        android.view.LayoutInflater inflater = getLayoutInflater();
        android.view.View dialogView = inflater.inflate(R.layout.dialog_password_setup, null);

        TextInputLayout passwordLayout = dialogView.findViewById(R.id.password_layout);
        TextInputLayout confirmPasswordLayout = dialogView.findViewById(R.id.confirm_password_layout);
        TextInputEditText passwordEdit = dialogView.findViewById(R.id.password_edit);
        passwordEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        TextInputEditText confirmPasswordEdit = dialogView.findViewById(R.id.confirm_password_edit);
        confirmPasswordEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btn_confirm);

        builder.setView(dialogView);
        android.app.AlertDialog dialog = builder.create();

        btnConfirm.setOnClickListener(v -> {
            String password = passwordEdit.getText().toString().trim();
            String confirmPassword = confirmPasswordEdit.getText().toString().trim();
            lockManager.setPasswordLock(false);
            passwordLayout.setError(null);
            confirmPasswordLayout.setError(null);

            if (password.isEmpty()) {
                passwordLayout.setError(getString(R.string.password_required));
                lockManager.disableLock();
                return;
            }

            if (!password.equals(confirmPassword)) {
                confirmPasswordLayout.setError(getString(R.string.password_not_match));
                return;
            }

            savePassword(password);
            if (addBioMetric) {
                // بعد الحفظ، شغّل البصمة
                setupBiometricAuthentication();
            }
            dialog.dismiss();


        });

        dialog.setOnCancelListener(dialogInterface -> {
            // ممكن هنا ترجّع سويتش القفل لوضعه السابق
            // passwordSwitch.setChecked(false);
        });

        dialog.show();
    }

    private void updateLockSwitches(SwitchMaterial passwordSwitch, SwitchMaterial biometricSwitch) {
        String lockType = lockManager.getLockType();
        boolean isPasswordLocked = "password".equals(lockType);
        boolean isBiometricLocked = "biometric".equals(lockType);

        passwordSwitch.setChecked(isPasswordLocked || isBiometricLocked);
        passwordSwitch.setEnabled(!isBiometricLocked); // تعطيل مفتاح كلمة المرور عند تفعيل البصمة

        biometricSwitch.setChecked(isBiometricLocked);
    }

    private void checkLocalCurrency() {
        DaftreeRepository repository = new DaftreeRepository(getApplication());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String localCurrency = repository.getFirstCurrency();
            if (localCurrency != null) {
                runOnUiThread(() -> {
                    sharedPrefsManager.setLocalCurrency(localCurrency);
                });
            }
        });
    }

    private void savePassword(String password) {
        DaftreeRepository repository = new DaftreeRepository(getApplication());
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 1. جلب المستخدم بشكل مباشر
            User user = repository.getUserDao().getUserProfileBlocking();
            if (user == null) {
                // في حالة نادرة جدًا أن المستخدم غير موجود، أنشئ واحدًا جديدًا
                user = new User();
                FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
                if (firebaseUser != null) {
                    user.setEmail(firebaseUser.getEmail());
                }
            }
            // 2. تحديث كلمة المرور
            user.setPassword(password);
            // 3. حفظ التغييرات
            repository.updateUserProfile(user);

            // 4. العودة للخيط الرئيسي لتحديث الواجهة وإظهار الرسائل
            runOnUiThread(() -> {
                lockManager.setPasswordLock(true);
                Toast.makeText(this, getString(R.string.password_saved_success), Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void handleIntentExtras() {
        if (getIntent().getBooleanExtra("SHOW_WELCOME_BANNER", false)) {
//            showWelcomeBanner();
        }
//        if (getIntent().getBooleanExtra("REFRESH_DATA", false)) {
//            if (mainViewModel != null) {
//                mainViewModel.refreshData(); // إعادة تحميل البيانات
//            }
//        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration) || super.onSupportNavigateUp();
    }

    // هذه الدالة ضرورية لفتح وإغلاق القائمة الجانبية
    @Override
    public void onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private void confirmLogout() {
        // التحقق من وجود بيانات غير متزامنة في خيط منفصل
        AppDatabase.databaseWriteExecutor.execute(() -> {
            boolean hasUnsyncedData = repository.hasUnsyncedData();

            // العودة للخيط الرئيسي لعرض الديالوج
            new Handler(Looper.getMainLooper()).post(() -> {
                if (hasUnsyncedData) {
                    showUnsyncedDataWarningDialog();
                } else {
                    handleLogout();
                }
            });
        });
    }

    private void showUnsyncedDataWarningDialog() {
        repository.triggerSync();
        new MaterialAlertDialogBuilder(this)
                .setTitle("بيانات غير متزامنة")
                .setMessage("لديك بيانات لم تتم مزامنتها مع السحابة بعد. إذا قمت بتسجيل الخروج الآن، ستفقد هذه البيانات نهائيًا. هل تريد المتابعة؟")
                .setPositiveButton("خروج على أي حال", (dialog, which) -> handleLogout())
                .setNegativeButton("إلغاء", null)
                .setIcon(R.drawable.ic_alert)
                .show();
    }

    private void handleLogout() {

        new AlertDialog.Builder(this)
                .setTitle("تسجيل الخروج")
                .setMessage("هل أنت متأكد أنك تريد تسجيل الخروج؟")
                .setPositiveButton("نعم", (dialog, which) -> {
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
                            performLogout();
                        }
                    });
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        // --- تفعيل منطق البحث ---
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint("ابحث عن حساب...");
        styleSearchView(searchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // عند تغيير النص في مربع البحث، نبلغ الـ ViewModel
                mainViewModel.setSearchQuery(newText);
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

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_filter) {
            showFilterDialog();
            return true;
        }
        if (item.getItemId() == R.id.action_export_pdf) {
            showReportsDialogFromToolbar();
            return true;
        }
        if (item.getItemId() == R.id.action_web_server) {
            startActivity(new Intent(this, WebServerActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupViewPager() {
        accountTypeViewModel.getAllAccountTypes().observe(this, types -> {
            if (types == null) return;
            this.accountTypesList = types;

            pagerAdapter = new AccountTypesPagerAdapter(this, accountTypesList);
            binding.viewPager.setAdapter(pagerAdapter);

            binding.viewPager.setCurrentItem(1, false);

            binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    int realPosition = pagerAdapter.getRealPosition(position);
                    updateToolbarTitle(realPosition);

                    // **هذا هو الكود الذي يفعّل الفلتر بشكل صحيح الآن**
                    if (realPosition == 0) {
                        mainViewModel.setFilter(null); // فلتر "كل الحسابات"
                    } else {
                        if (accountTypesList != null && !accountTypesList.isEmpty() && realPosition - 1 < accountTypesList.size()) {
                            String firestoreId = accountTypesList.get(realPosition - 1).getFirestoreId();
                            mainViewModel.setFilter(firestoreId);
                        }
                    }
                }

                @Override
                public void onPageScrollStateChanged(int state) {
                    super.onPageScrollStateChanged(state);
                    if (state == ViewPager2.SCROLL_STATE_IDLE) {
                        int currentItem = binding.viewPager.getCurrentItem();
                        int itemCount = pagerAdapter.getItemCount();

                        // إذا كنا في الصفحة الإضافية الأولى (الموضع 0)، ننتقل إلى الصفحة قبل الأخيرة الحقيقية
                        if (currentItem == 0) {
                            isProgrammaticScroll = true;
                            binding.viewPager.setCurrentItem(itemCount - 2, false);
                        }
                        // إذا كنا في الصفحة الإضافية الأخيرة (الموضع itemCount - 1)، ننتقل إلى الصفحة الثانية (الموضع 1)
                        else if (currentItem == itemCount - 1) {
                            isProgrammaticScroll = true;
                            binding.viewPager.setCurrentItem(1, false);
                        }
                    }
                }
            });
        });
    }

    String accountType;

    private void updateToolbarTitle(int realPosition) {
        if (realPosition == 0) {
            binding.toolbar.setTitle(getString(R.string.filter_all));

            accountType = getString(R.string.filter_all);
        } else {
            binding.toolbar.setTitle(accountTypesList.get(realPosition - 1).name);
            accountType = accountTypesList.get(realPosition - 1).name.trim();
        }

    }

    private void showFilterDialog() {
        List<String> filterOptionsList = new ArrayList<>();
        filterOptionsList.add(getString(R.string.filter_all)); // "كل الحسابات"

        for (AccountType type : accountTypesList) {
            filterOptionsList.add(type.name);
        }

        String[] filterOptions = filterOptionsList.toArray(new String[0]);
        final String[] filterValues = new String[filterOptions.length];
        filterValues[0] = null; // "كل الحسابات" -> null
        // نفس الاسم
        System.arraycopy(filterOptions, 1, filterValues, 1, filterOptions.length - 1);

        new AlertDialog.Builder(this)
                .setItems(filterOptions, (dialog, which) -> {
                    // عند اختيار فلتر، نبلغ الـ ViewModel
                    mainViewModel.setFilter(filterValues[which]);
                    // تحديث عنوان الشريط العلوي
                    binding.toolbar.setTitle(filterOptions[which]);
                })
                .show();
    }

    private void backupDatabase() {
        try {
            File documentsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            File daftariFolder = new File(documentsFolder, "Daftari");

            if (!daftariFolder.exists()) {
                daftariFolder.mkdirs();
            }

            String dbName = AppDatabase.getDatabase(this).getOpenHelper().getDatabaseName();
            File dbFile = getDatabasePath(dbName);
            String backupFileName = "Daftree_Backup_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(new Date());
            File backupFile = new File(daftariFolder, backupFileName);

            try (FileChannel source = new FileInputStream(dbFile).getChannel();
                 FileChannel destination = new FileOutputStream(backupFile).getChannel()) {
                destination.transferFrom(source, 0, source.size());
                // Toast.makeText(this, "تم حفظ النسخة الاحتياطية بنجاح في مجلد Documents/Daftari", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "فشل النسخ الاحتياطي: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("Backup", "Error backing up database", e);
        }
    }

    private void startRestoreProcess(Uri uri) {
        try {
            String fileName = getFileName(uri);
            if (fileName != null) {
                String extension = getFileExtension(fileName).toLowerCase();

                // التحقق من الامتدادات المدعومة
                if (extension.equals("db") ||
                        extension.equals("p") ||
                        extension.equals("b")) {

                    ProgressDialog progressDialog = new ProgressDialog(this);
                    progressDialog.setMessage(getString(R.string.sync_start));
                    progressDialog.setCancelable(false);
                    progressDialog.show();

                    RestoreHelper restoreHelper = new RestoreHelper(this);
                    restoreHelper.importDatabase(uri, new RestoreHelper.RestoreListener() {
                        @Override
                        public void onRestoreSuccess(int accountsImported, int transactionsImported) {
                            progressDialog.dismiss();
//                String message = "تم بنجاح استيراد " + accountsImported + " حساب و " + transactionsImported + " عملية.";
                            String message = getString(R.string.sync_finish, accountsImported, transactionsImported);
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                            // تشغيل المزامنة لرفع البيانات الجديدة
                            if (!isGuest) {
                                repository.triggerSync();
                            }
                        }

                        @Override
                        public void onRestoreError(String error) {
                            progressDialog.dismiss();
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("خطأ في الاستيراد")
                                    .setMessage(error)
                                    .setPositiveButton("موافق", null)
                                    .show();
                        }
                    });

                } else {
                    // عرض رسالة خطأ للمستخدم
                    Toast.makeText(this, "امتداد الملف غير مدعوم", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    // دالة مساعدة للحصول على امتداد الملف
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot != -1 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1);
        }
        return "";
    }

    // دالة مساعدة للحصول على اسم الملف من URI
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (displayNameIndex != -1) {
                        result = cursor.getString(displayNameIndex);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void showReportsDialogFromToolbar() {
        int currentPosition = binding.viewPager.getCurrentItem();
        String currency = binding.currencyTextView.getText().toString();
        String filter = mainViewModel.getFilter().getValue();

        String accountType;
        boolean isAllAccounts;

        if (filter == null) {
            accountType = getString(R.string.filter_all);
            isAllAccounts = true;
        } else {
            accountType = filter;
            isAllAccounts = false;
        }

        Log.d("ReportsDialog1", "showReportsDialogFromToolbar: " + accountType + " currency " + currency);
        ReportsDialog reportsDialog = ReportsDialog.newInstanceFromMainToolbar(accountType, currency, isAllAccounts);
        reportsDialog.show(getSupportFragmentManager(), "ReportsDialogToolbar");
    }

    @Override
    public void onAccountClicked(Account account, String currency) {
        Intent intent = new Intent(this, AccountDetailsActivity.class);
        intent.putExtra("ACCOUNT_ID", account.getId());
        intent.putExtra("ACCOUNT_NAME", account.getAccountName());
        intent.putExtra("CURRENCY", currency);
        startActivity(intent);
    }

    @Override
    public void onAddTransactionClicked(Account account, String currency) {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == STORAGE_PERMISSION_CODE) {
//            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                backupDatabase();
//            } else {
//                Toast.makeText(this, "تم رفض الإذن، لا يمكن إكمال النسخ الاحتياطي.", Toast.LENGTH_SHORT).show();
//            }
//        }
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "تم منح صلاحية الإشعارات.");
            } else {
                Log.w(TAG, "تم رفض صلاحية الإشعارات من المستخدم.");
                // يمكنك إظهار Toast أو Snackbar هنا لإبلاغه.
            }
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        int id = item.getItemId();
        if (id == R.id.nav_new_bill) {
//          Toast.makeText(this,  item.getTitle() + " قيد التطوير للاصدار القادم", Toast.LENGTH_SHORT).show();
            String currency = binding.currencyTextView.getText().toString();
            InvoiceDialog dialog = InvoiceDialog.newInstanceForCreate(currency);
            dialog.show(getSupportFragmentManager(), "InvoiceDialog");
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }
        if (id == R.id.nav_purchase_app) {
            handlePurchaseApp();
        } else if (id == R.id.nav_test) {
//            startActivity(new Intent(this, AddTransactionActivity2.class));
        } else if (id == R.id.nav_admin_dashboard) {
            startActivity(new Intent(this, AdminDashboardActivity.class));

        } else if (id == R.id.nav_watch_ad) {
            //  simulateWatchAd();
        } else if (id == R.id.nav_invite_friend) {
            referralManager.generateAndShareReferralLink(FirebaseAuth.getInstance().getCurrentUser());
        } else if (id == R.id.nav_manage_devices) {
            showDeviceManagementScreen();
        }
        if (id == R.id.nav_profile) {
            startActivity(new Intent(this, ProfileActivity.class));
        } else if (id == R.id.nav_currencies) {
            startActivity(new Intent(this, CurrenciesActivity.class));
        } else if (id == R.id.nav_account_types) {
            startActivity(new Intent(this, AccountsTypeActivity.class));
        } else if (id == R.id.nav_reports) {
            ReportsDialog reportsDialog = ReportsDialog.newInstanceGeneral();
            reportsDialog.show(getSupportFragmentManager(), "ReportsDialogGeneral");
        } else if (id == R.id.nav_change_language) {
            showLanguageDialog();
//            new LanguageDialogFragment().show(getSupportFragmentManager(), "dialog_language");
//            LanguageSelectionDialog.newInstance(true).show(getSupportFragmentManager(), "LangDialog");

        } else if (id == R.id.nav_update_app) {

            checkForAppUpdate(true);
            // updateApp();
        } else if (id == R.id.nav_rate_app) {
            rateApp();
        } else if (id == R.id.nav_privacy_policy) {
            String updateUrl = "https://hpp-daftree.web.app/privacy.html";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl));
            startActivity(intent);
        } else if (id == R.id.nav_terms_cond) {
            String updateUrl = "https://hpp-daftree.web.app/terms.html";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl));
            startActivity(intent);
        } else if (id == R.id.nav_restore_data) {
//            openDocumentLauncher.launch(new String[]{"*/*"}); // يمكن استخدام "application/x-sqlite3" لتحديد النوع
            openBackupFile();

        } else if (id == R.id.nav_logout) {
            confirmLogout();
        } else if (id == R.id.nav_login) {
            removeAllObservers();

            // إيقاف أي مزامنة جارية
            FirestoreSyncManager.getInstance().stopListening();

            // إيقاف المستمع الخاص بالترخيص
            if (licenseListener != null) {
                licenseListener.remove();
                licenseListener = null;
            }

            // إعادة تعيين حالة البيانات
            isDataLoaded = false;
            isSyncCompleted = false;
            isSyncStop = false;

            Intent intent = new Intent(this, LoginActivity.class);
            intent.putExtra("registerGuest", true);
            startActivityForResult(intent, LOGIN_REQUEST_CODE);
            startActivity(intent);
            finish();
        } else if (id == R.id.nav_contact_support) {
            startActivity(new Intent(this, ContactActivity.class));
        } else if (id == R.id.nav_backup_data) {
//            checkStoragePermissionAndBackup();
            createBackupFile();
        } else if (id == R.id.nav_update_data) {
            startActivity(new Intent(this, DeleteFromFirestoreActivity.class));
        } else if (id == R.id.nav_help) {
//           new HelpDialog().show(getSupportFragmentManager(), "HelpDialog");
            new HelpDialog(this).show();
            // startActivity(new Intent(this, HelpActivity.class));
        } else if (id == R.id.nav_exit) {
//            finish(); // إغلاق التطبيق
            finishAffinity();
        }
//        else {
//            // تعامل مع باقي العناصر
//            Toast.makeText(this, "تم الضغط على: " + item.getTitle(), Toast.LENGTH_SHORT).show();
//        }

        drawerLayout.closeDrawer(GravityCompat.START); // إغلاق القائمة بعد الاختيار
        new Handler().postDelayed(() -> {
            // إلغاء تحديد جميع العناصر في القائمة
            Menu menu = navigationView.getMenu();
            for (int i = 0; i < menu.size(); i++) {
                menu.getItem(i).setChecked(false);
            }
        }, 100); // تأخير بسيط لضمان إلغاء التحديد بعد الانتقال

        return true;
    }

    private void rateApp() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.no_internet), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            RateAppDialog.forceShow(this);

//            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + this.getPackageName()));
//            startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            // إذا لم يكن متجر Google Play مثبتاً، نفتح المتجر عبر المتصفح
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + this.getPackageName()));
            startActivity(intent);
        }
    }

    private void removeAllObservers() {
        try {
            // إزالة مراقب بيانات المستخدم
            if (profileViewModel != null && profileViewModel.getUserProfile().hasObservers()) {
                profileViewModel.getUserProfile().removeObservers(this);
            }

//            // إزالة مراقب اللغة
//            if (languageViewModel != null && languageViewModel.getSelectedLanguage().hasObservers()) {
//                languageViewModel.getSelectedLanguage().removeObservers(this);
//            }

            // إزالة مراقب العملات
            if (currencyViewModel != null && currencyViewModel.getAllCurrencies().hasObservers()) {
                currencyViewModel.getAllCurrencies().removeObservers(this);
            }

            // إزالة مراقب أنواع الحسابات
            if (accountTypeViewModel != null && accountTypeViewModel.getAllAccountTypes().hasObservers()) {
                accountTypeViewModel.getAllAccountTypes().removeObservers(this);
            }

            // إزالة مراقبي MainViewModel
            if (mainViewModel != null) {
                if (mainViewModel.getAccounts().hasObservers()) {
                    mainViewModel.getAccounts().removeObservers(this);
                }
                if (mainViewModel.getCurrenciesWithTransactions().hasObservers()) {
                    mainViewModel.getCurrenciesWithTransactions().removeObservers(this);
                }
                if (mainViewModel.getFilter().hasObservers()) {
                    mainViewModel.getFilter().removeObservers(this);
                }
            }

            // إزالة مراقب المزامنة
            if (syncCompletionLiveData.hasObservers()) {
                syncCompletionLiveData.removeObservers(this);
            }

            Log.d(TAG, "تم إزالة جميع المراقبات بنجاح");

        } catch (Exception e) {
            Log.e(TAG, "خطأ أثناء إزالة المراقبات: " + e.getMessage());
        }
    }


    private void openUpdatePage() {


        try {
            String updateUrl = "https://com-hpp-daftree.ar.uptodown.com/android";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl));
            startActivity(intent);

            // تتبع حدث التحديث (اختياري)
            logUpdateEvent();

        } catch (Exception e) {
            Toast.makeText(this, "تعذر فتح رابط التحديث", Toast.LENGTH_SHORT).show();

            // محاولة بديلة
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.uptodown.com"));
                startActivity(browserIntent);
            } catch (Exception ex) {
                Toast.makeText(this, "يرجى تثبيت متصفح للإنترنت", Toast.LENGTH_LONG).show();
            }
        }
    }

    // دالة لتسجيل حدث التحديث (اختياري)
    private void logUpdateEvent() {
        // هنا يمكنك إضافة كود لتسجيل الحدث في Firebase Analytics أو أي نظام تحليلات
        Log.d("AppUpdate", "User clicked update button");
    }

    @Override
    public void onAccountLongClicked(Account account) {
        showAccountOptionsDialog(account);
    }


    private void showAccountOptionsDialog(final Account account) {
        DialogAccountOptionsBinding dialogBinding = DialogAccountOptionsBinding.inflate(getLayoutInflater());
        dialogBinding.dialogOptionsTitle.setText(account.getAccountName());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot()).create();

        dialogBinding.buttonEditAccount.setOnClickListener(v -> {
            showEditAccountDialog(account);
            dialog.dismiss();
        });

        dialogBinding.buttonDeleteAccount.setOnClickListener(v -> {
            handleAccountDeletion(account);
            dialog.dismiss();
        });

        dialog.show();
    }


    private void showEditAccountDialog(final Account accountToEdit) {
        // 1. تحميل واجهة الديالوج
        DialogEditAccountBinding dialogBinding = DialogEditAccountBinding.inflate(getLayoutInflater());

        // 2. تعبئة الحقول بالبيانات الحالية للحساب
        dialogBinding.editAccountName.setText(accountToEdit.getAccountName());
        dialogBinding.editAccountPhone.setText(accountToEdit.getPhoneNumber());

        // 3. إعداد وتعبئة الـ Spinner الخاص بأنواع الحسابات
        AutoCompleteTextView accountTypeSpinner = dialogBinding.spinnerAccountType;
        if (accountTypesList != null && !accountTypesList.isEmpty()) {
            // استخلاص أسماء التصنيفات من القائمة التي تم تحميلها مسبقًا في MainActivity
            List<String> typeNames = accountTypesList.stream().map(t -> t.name).collect(Collectors.toList());

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line, typeNames);
            accountTypeSpinner.setAdapter(adapter);
            int itemHeight = (int) (48 * getResources().getDisplayMetrics().density);
            accountTypeSpinner.setDropDownHeight(itemHeight * 4);

            // **الأهم: تحديد التصنيف الحالي للحساب كقيمة افتراضية في الـ Spinner**
            if (accountToEdit.getAccountType() != null && typeNames.contains(accountToEdit.getAccountType())) {
                accountTypeSpinner.setText(accountToEdit.getAccountType(), false);
            }
        }

        // 4. بناء وإظهار الديالوج
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.edit))
                .setView(dialogBinding.getRoot())
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.save), (d, which) -> {
                    // 5. منطق الحفظ المحدث
                    String newName = dialogBinding.editAccountName.getText().toString().trim();
                    String newPhone = dialogBinding.editAccountPhone.getText().toString().trim();

                    // **جلب القيمة الجديدة من الـ Spinner**
                    String newType = dialogBinding.spinnerAccountType.getText().toString();

                    if (newName.isEmpty()) {
                        Toast.makeText(this, getString(R.string.error_account_name_empty), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newType.isEmpty()) {
                        Toast.makeText(this, getString(R.string.edit), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // تحديث بيانات كائن الحساب
                    accountToEdit.setAccountName(newName);
                    accountToEdit.setPhoneNumber(newPhone);
                    accountToEdit.setAccountType(newType); // **<-- تعيين التصنيف الجديد**

                    // إرسال الكائن المحدث للحفظ في قاعدة البيانات
                    mainViewModel.updateAccount(accountToEdit);
                    mainViewModel.refreshData();
                    Toast.makeText(this, getString(R.string.success_saving), Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void handleAccountDeletion(final Account accountToDelete) {
        // تنفيذ الفحص في خيط خلفي
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int transactionCount = repository.getTransactionCountForAccount(accountToDelete.getId());

            // العودة للخيط الرئيسي لعرض الديالوج المناسب
            new Handler(Looper.getMainLooper()).post(() -> {
                if (transactionCount > 0) {
                    showDeleteWithTransactionsWarning(accountToDelete, transactionCount);
                } else {
                    showSimpleDeleteConfirmation(accountToDelete);
                }
            });
        });
    }

    private void showDeleteWithTransactionsWarning(final Account account, int count) {
        // String message = "هذا الحساب لديه (" + count + ") عمليات مرتبطة به. هل أنت متأكد من حذفه وحذف كل عملياته نهائيًا؟";
        String message = getString(R.string.confirm_delete_message, String.valueOf(count));
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.caution_titel))
                .setMessage(message)
                .setIcon(R.drawable.ic_alert)
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.ok), (d, w) -> {
                    mainViewModel.deleteAccount(account);
//                    setupObservers();
                })
                .show();
    }

    private void showSimpleDeleteConfirmation(final Account account) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.confirm_1))
                .setMessage(getString(R.string.confirm_delete_account_message, account.getAccountName()))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.delete), (d, w) -> {
                    mainViewModel.deleteAccount(account);
//                    setupObservers();
                })
                .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isGuest) {
            if (mainViewModel != null) {
                mainViewModel.refreshData();
            }
            new Handler().postDelayed(() -> {
                checkDeviceBanOnStart();
                updateGuestToFirestore();
            }, 3000);

            return;
        }
//        checkUserLicenseAndSetupUI();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        SharedPreferences prefs = getSharedPreferences("prefs_uid", MODE_PRIVATE);
        if (user == null) {
            // المستخدم غير مسجل دخول → اذهب إلى LoginActivity
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        // تنفيذ ما كان في handleLoginSuccess()
        prefs.edit().putString("uid", user.getUid()).apply();
        if (isNetworkAvailable() && FirebaseAuth.getInstance().getCurrentUser() != null) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            handleUserUpgradeFromV101(user);
            DaftreeRepository repository = new DaftreeRepository(getApplication());
            repository.setUserUID(user.getUid());
            FirestoreSyncManager.getInstance().startListening(repository, this, () -> {
                repository.triggerSync();
            });

        }
//        if (isNetworkAvailable()) {
//            performDeviceLicenseCheck(user);
//
//        }
        if (mainViewModel != null) {
            mainViewModel.refreshData();
        }

//        SecureLicenseManager licenseManager =  SecureLicenseManager.getInstance(this);
//        licenseManager.importLicenseDataFromFirestore().thenAccept(success -> {
//            if (success) {
//                Log.d("TAG", "تم استيراد بيانات الترخيص بنجاح");
//            } else {
//                Log.e("TAG", "فشل في استيراد بيانات الترخيص");
//            }
//        });
//        verifyDeviceAuthorization();
    }

    private void handleUserUpgradeFromV101(FirebaseUser currentUser) {
        VersionManager versionManager = new VersionManager(this);
        int lastVersion = versionManager.getLastKnownVersionCode();

        if (lastVersion == 1) {

            if (currentUser == null) return;

            String userId = currentUser.getUid();
            LicenseManager licenseManager = new LicenseManager(this);
            String currentDeviceId = licenseManager.getDeviceId();

            // التحقق من Firestore لمعرفة إذا كان الجهاز مسجلاً بالفعل
            FirebaseFirestore.getInstance().collection("users").document(userId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            User user = documentSnapshot.toObject(User.class);
                            if (user != null && user.getDevices() != null) {
                                // إذا لم يكن الجهاز الحالي موجودًا في القائمة، قم بإضافته
                                if (!user.getDevices().containsKey(currentDeviceId)) {
                                    addCurrentDeviceToFirestore(userId, currentDeviceId, licenseManager);
                                } else {
                                    // الجهاز موجود بالفعل، لا حاجة للعمل
                                    Log.d(TAG, "Device already registered in Firestore.");
                                }
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error checking user document in Firestore", e);
                    });
        } else {
            performDeviceLicenseCheck(currentUser);
        }
    }

    private void addCurrentDeviceToFirestore(String userId, String deviceId, LicenseManager licenseManager) {
        Map<String, Object> deviceUpdate = new HashMap<>();
        DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();
        deviceUpdate.put("devices." + deviceId, currentDevice.toMap());

        FirebaseFirestore.getInstance().collection("users").document(userId)
                .update(deviceUpdate)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Successfully added device from v1.0.1 upgrade to Firestore.");
                    // بعد الإضافة، يمكنك تحديث الواجهة أو إجراء أي عمل必要
                    checkDeviceAuthorization(); // إعادة فحص الترخيص لتحديث الحالة
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to add device to Firestore", e);
                });
    }

    private void checkUserUpgrare() {
        verifyDeviceAuthorization();

        if (checkExcecedAuthorization()) return;
        DaftreeRepository repo = new DaftreeRepository(getApplication());
        repo.setUserUID(FirebaseAuth.getInstance().getUid());
        FirestoreSyncManager.getInstance().startListening(repo, this, () -> {
            repo.triggerSync();
        });
    }

    private void performDeviceLicenseCheck(FirebaseUser user) {
        if (versionManager.first_upgrade()) {

            Log.d(TAG, "بدء فحص ترخيص الجهاز للمستخدم: " + user.getEmail());

            // ✅ التحقق أولاً من وجود المستخدم في Firestore
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();
            DocumentReference userDocRef = firestore.collection("users").document(user.getUid());
            VersionManager versionManager = new VersionManager(this);

            userDocRef.get().addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Log.e(TAG, "فشل الوصول لمستند المستخدم: " + task.getException());
                    SnackbarHelper.showSnackbar(binding.getRoot(), getString(R.string.error_access_user_doc), SnackbarHelper.SnackbarType.ERROR);
                    return;
                }

                if (!task.getResult().exists()) {
                    if ((!versionManager.getFirestoreUser_isAdded())) {
                        createNewUser(userDocRef, user);
                        new VersionManager(this).setFirst_upgrade(false);
                        return;
                    } else {
                        checkUserUpgrare();
                    }
                    return;
                }
            });
            checkUserUpgrare();
        } else {
            checkUserUpgrare();
        }
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
                            checkUserUpgrare();

                        } else {

                        }
                    });
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "createNewUserData Error: " + e);
        }
    }

    private void verifyDeviceAuthorization() {
        if (!isNetworkAvailable()) return; // لا تقم بالفحص بدون انترنت

        licenseManager.checkLicense().thenAccept(result -> {
            runOnUiThread(() -> {
                if (result.isSuccess() && result.getUser() != null) {
                    // التحقق مما إذا كان ID الجهاز الحالي موجود في القائمة القادمة من Firestore
                    if (!result.getUser().getDevices().containsKey(licenseManager.getDeviceId())) {
                        forceSignOutAndShowAlert();
                    }
                }

            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // اختياري ولكن جيد: التأكد من إيقاف المستمع عند تدمير الـ Activity الرئيسية
        // هذا يمنع أي تسريب في حال لم يقم المستخدم بتسجيل الخروج
        FirestoreSyncManager.getInstance().stopListening();
        if (licenseListener != null) {
            licenseListener.remove();
//            userListener.remove();
        }
        if (versionManager != null) {
            versionManager.shutdown();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // الحل هنا: اطلب من الـ ViewModel تحديث بياناته في كل مرة تعود فيها للشاشة
        if (sharedPrefsManager == null) {
            sharedPrefsManager = new SyncPreferences(this);
        }
        RateAppDialog.showIfNeeded(this);
        updateAllNavigationSwitches();
        updateNavigationMenuItems();
        if (mainViewModel != null) {
            mainViewModel.refreshData();
        }
        Log.e(TAG, " isGuest(): " + isGuest);
        if (!isGuest) {
            deleteGuestAccountsWithSameDevice();
        }
//        refreshNotificationUI();
    }

    boolean isDeviceAuthorized = false;

    private void checkDeviceAuthorization() {
        if (!isNetworkAvailable()) {
            Log.d("LicenseCheck", "لا يوجد اتصال بالإنترنت");
            return;
        }
        checkUserisDeleted("checkDeviceAuthorization");
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Log.d("LicenseCheck", "المستخدم غير مسجل دخول");
            return;
        }

        if (!googleAuthHelper.isSignedIn()) {
            forceSignOutAndShowAlert();
            return;
        }
        licenseManager.checkLicense().thenAccept(result -> {
            runOnUiThread(() -> {

                if (result.isSuccess()) {
                    if (!result.isCurrentDeviceAuthorized()) {
                        // الجهاز غير مرخص - إجبار التسجيل الخروج
                        Log.d("LicenseCheck", "الجهاز غير مرخص، يتم تسجيل الخروج");
                        googleLogoutForcs();
                        forceSignOutAndShowAlert();
                    } else {
                        // الجهاز مرخص - تحديث واجهة المستخدم
                        Log.d("LicenseCheck", "الجهاز مرخص");
                        currentUserData = result.getUser();
                        updateNavigationMenuItems();
                    }
                } else {
                    // فشل في التحقق من الترخيص
                    Log.e("LicenseCheck", "فشل التحقق من الترخيص: " + result.getMessage());
                    forceSignOutAndShowAlert();
                    Toast.makeText(this, getString(R.string.error_add_device), Toast.LENGTH_SHORT).show();
                }
            });
        }).exceptionally(e -> {
            Log.e("LicenseCheck", "خطأ أثناء التحقق من الترخيص: " + e.getMessage());
            runOnUiThread(() -> {
                Toast.makeText(this, getString(R.string.error), Toast.LENGTH_SHORT).show();
            });
            return null;
        });
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

    private boolean checkUserisDeleted(String checking) {
        AtomicBoolean isDeleted = new AtomicBoolean(false);
        if (isNetworkAvailable()) {
            return isDeleted.get();
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            user.reload().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.e(TAG, " User Statues: isActivated  _" + checking);
                } else {
                    Log.e(TAG, " User Statues: isDeleted _" + checking);
                    // الحساب محذوف أو لم يعد صالحًا
                    FirebaseAuth.getInstance().signOut();
                    isDeleted.set(true);
                }
            });
        } else {
            Log.e(TAG, " User Statues: Unkown _" + checking);
            // لا يوجد مستخدم مسجل
            isDeleted.set(true);
        }
        return isDeleted.get();
    }

    private void performLogout() {
        // إيقاف المزامنة
        FirestoreSyncManager.getInstance().stopListening();
        // تسجيل الخروج من Firebase
        FirebaseAuth.getInstance().signOut();

        // مسح البيانات المحلية
        SharedPreferences prefs = getSharedPreferences("prefs_uid", MODE_PRIVATE);
        prefs.edit().clear().apply();
        sharedPrefsManager.setFirstSyncComplete(false);

        // حذف قاعدة البيانات المحلية
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
        googleLogoutForcs();
        // التوجيه إلى شاشة تسجيل الدخول
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    boolean dialogeShow = false;

    private void forceSignOutAndShowAlert() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (dialogeShow) return;
        dialogeShow = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.device_remove_tit))
                .setMessage(getString(R.string.device_remove)).setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    // تنفيذ عملية تسجيل الخروج الكاملة
                    performLogout();
                })
                .setCancelable(false)
                .setIcon(R.drawable.ic_alert)
                .show();
    }

    private boolean checkExcecedAuthorization() {
        checkUserisDeleted("checkExcecedAuthorization");
        Log.e("TAG", ("DeviceLimitExceeded: checkExcecedAuthorization "));
        AtomicBoolean checkExcecedAuthoriz = new AtomicBoolean(false);
        // لا تقم بالفحص إذا لم يكن المستخدم مسجلاً دخوله أو لا يوجد إنترنت
        if (FirebaseAuth.getInstance().getCurrentUser() == null || !isNetworkAvailable()) {
            checkExcecedAuthoriz.set(false);
        }

        licenseManager.checkLicense().thenAccept(result -> {
            runOnUiThread(() -> {
                // الشرط الأساسي: إذا نجح الفحص ولكن النتيجة هي أن "الجهاز غير مرخص"
                if (result.isSuccess() && !result.isCurrentDeviceAuthorized()) {
                    forceSignOutAndShowAlert();
                    Log.e("TAG", ("DeviceLimitExceeded: " + result.isCurrentDeviceAuthorized()));
                    checkExcecedAuthoriz.set(true);
                } else {
                    Log.e("TAG", ("Device Not LimitExceeded: " + result.isCurrentDeviceAuthorized()));
                    checkExcecedAuthoriz.set(false);
                }
                // في كل الحالات الأخرى (الجهاز مرخص، فشل الفحص، ...إلخ)، لا تفعل شيئاً واترك المستخدم يكمل.
            });
        });
        return checkExcecedAuthoriz.get();
    }

    // دالة مساعدة للتحقق من وجود عمليات لعملة وفلتر معين
    private boolean checkTransactionsForCurrencyAndFilter(int currencyName, String filter) {
        // نفذنا هذا الاستعلام في خيط خلفي سابقاً، لكن للتبسيط سنفترض وجود دالة في Repository
        // في التطبيق الحقيقي، يجب تنفيذ هذا في خيط خلفي
        return repository.hasTransactionsForCurrencyAndFilter(currencyName, filter);
    }

    private void showDeviceManagementDialog(User user, boolean isLimitExceeded) {
        if (getSupportFragmentManager().findFragmentByTag("DeviceManagementDialog") != null) {
            return; // لا تفتح الديالوج إذا كان مفتوحًا بالفعل
        }

        List<DeviceInfo> devices = new ArrayList<>(user.getDevices().values());
        DeviceManagementDialog dialog = DeviceManagementDialog.newInstance(devices, licenseManager, isLimitExceeded);
        //disableInteractiveElements();
        // تعيين المستمع لإعادة تمكين العناصر عند الإغلاق
        dialog.setOnDismissListener(new DeviceManagementDialog.OnDismissListener() {
            @Override
            public void onDismiss() {
                /*enableInteractiveElements();*/
            }
        });
        dialog.setDialogListener(new DeviceManagementDialog.DialogListener() {
            @Override
            public void onDeviceRemoved() {
                // بعد إزالة جهاز، أعد فحص الترخيص لتحديث حالة الواجهة
                showDeviceManagementScreen();
//                checkUserLicense();
            }

            @Override
            public void onDismissed() {
                Toast.makeText(MainActivity.this, "ستستمر كمستخدم مجاني على هذا الجهاز.", Toast.LENGTH_LONG).show();
            }
        });
        dialog.show(getSupportFragmentManager(), "DeviceManagementDialog");
    }

    private void updateNavigationHeader() {
        NavigationView navigationView = findViewById(R.id.nav_view);
        View headerView = navigationView.getHeaderView(0);
        TextView userEmailText = headerView.findViewById(R.id.textViewUserEmail);
        TextView licenseStatusText = headerView.findViewById(R.id.license_status);

        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
            }

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                // يتم استدعاؤها مباشرة عند فتح القائمة
                updateNavigationMenuItems();
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
            }

            @Override
            public void onDrawerStateChanged(int newState) {
            }
        });

        if (googleAuthHelper.isSignedIn()) {
            FirebaseUser user = googleAuthHelper.getCurrentUser();
//            userEmailText.setText(user.getEmail());
            licenseStatusText.setText(licenseManager.isPremiumUser() ? getString(R.string.premium_plan) : getString(R.string.free_plan));
            licenseStatusText.setTextColor(licenseManager.isPremiumUser() ? Color.GREEN : Color.YELLOW);
        } else {
//            userEmailText.setText("يرجى تسجيل الدخول");
            licenseStatusText.setText("يرجى تسجيل الدخول");
            licenseStatusText.setTextColor(Color.WHITE);
        }
    }

    private void handlePurchaseApp() {
        if (!googleAuthHelper.isSignedIn()) {
            Toast.makeText(this, getString(R.string.login_1), Toast.LENGTH_SHORT).show();
            return;
        }
        PurchaseCodeDialog.newInstance().show(getSupportFragmentManager(), "PurchaseCodeDialog");
    }

    private void showDeviceManagementScreen() {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.no_internet), Toast.LENGTH_SHORT).show();
            return;
        }
        licenseManager.checkLicense().thenAccept(result -> {
            runOnUiThread(() -> {
                if (result.isSuccess() && result.getUser() != null) {
                    // استدعاء الديالوج وتمرير حالة تجاوز الحد
                    showDeviceManagementDialog(result.getUser(), result.isDeviceLimitExceeded());
                } else {
                    Toast.makeText(this, getString(R.string.fail_impoart_accounts), Toast.LENGTH_SHORT).show();
                }
                updateNavigationHeader();
                updateNavigationMenuItems();
            });
        });
    }

    private void handleIncomingDeepLink(Intent intent) {
        Uri data = intent.getData();
        if (data != null && "daftree".equals(data.getScheme())) {
            String referrerUid = data.getQueryParameter("ref");
            // سجّل تفاصيل الرابط لأغراض التصحيح
            Log.d("DeepLink", "الرابط المستلم: " + data);
            Log.d("DeepLink", "كود الدعوة: " + referrerUid);

            if (referrerUid != null && !referrerUid.isEmpty()) {
                Log.d("DeepLink", "تم استقبال دعوة من: " + referrerUid);
                referralManager.saveReferrerUid(referrerUid);
//                if (!googleAuthHelper.isSignedIn()) {
//                    handleGoogleLogin();
//                }
            }
        }
    }

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

    @Override
    public void onSignedOut() {
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, getString(R.string.logout_succes), Toast.LENGTH_SHORT).show();
            updateUiBasedOnLicense();
        });
    }

    private void updateUiBasedOnLicense() {
//        updateNavigationHeader();
//        updateNavigationMenuItems();
//        checkUserLicense(); // إعادة فحص الترخيص لتحديث حالة Premium
        showDeviceManagementScreen();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        // معالجة الروابط العميقة عندما يكون التطبيق مفتوحًا بالفعل
        Uri data = intent.getData();
        String referrerUid = null;

        if (data != null) {
            if ("daftree".equals(data.getScheme()) && "invite".equals(data.getHost())) {
                referrerUid = data.getQueryParameter("ref");
            } else if ("https".equals(data.getScheme()) && "hpp-daftree.web.app".equals(data.getHost())) {
                referrerUid = data.getQueryParameter("ref");
            }
        }

        if (referrerUid != null) {
            // Snackbar.make(binding.getRoot(), "أنت مسجل مسبقاً ولا يمكنك استخدام رابط الدعوة للحصول على النقاط.", Snackbar.LENGTH_LONG).show();
            SnackbarHelper.showSnackbar(binding.getRoot(), getString(R.string.referral_already_registered2), SnackbarHelper.SnackbarType.ERROR);
//            showAlreadyRegisteredMessage();
        }
    }

    private void startLicenseListener() {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        String users, uid;
        if (isGuest) {
            users = "guests";
            uid = guestUID;
        } else {
            FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            if (firebaseUser == null) return;
            users = "users";
            uid = firebaseUser.getUid();
        }
        if (users == null) return;
        Log.i(TAG, "users:" + users);
        DocumentReference userDocRef = firestore.collection(users).document(uid);
        licenseListener = userDocRef.addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                Log.e(TAG, "License listener failed", e);
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                User user = snapshot.toObject(User.class);
                if (user == null) return;
                DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();
                String currentDeviceId = currentDevice.getDeviceId();
                SecureLicenseManager secure = SecureLicenseManager.getInstance(this);
                licenseManager.setPremiumStatus(user.isIs_premium());
                secure.setDevicesNos(user.getDevices().size());
                Log.e(TAG, "startLicenseListener: " + "عدد لاجهزة: " + user.getDevices().size());
                // ✅ إذا تم إزالة الجهاز من قائمة الأجهزة
                if (!isGuest) {
                    if (!user.getDevices().containsKey(currentDeviceId)) {
                        runOnUiThread(() -> {
                            // Toast.makeText(this, "تمت إزالة هذا الجهاز من الأجهزة المرخصة!", Toast.LENGTH_LONG).show();
                            checkDeviceAuthorization();
                        });
                        return;
                    }
                }
                long lastModified = snapshot.contains("lastModified") ? snapshot.getLong("lastModified") : System.currentTimeMillis();
                // ✅ تحديث باقي بيانات الترخيص
                int maxTransactions = snapshot.getLong("max_transactions") != null ?
                        snapshot.getLong("max_transactions").intValue() : 0;
                int transactionsCount = snapshot.getLong("transactions_count") != null ?
                        snapshot.getLong("transactions_count").intValue() : 0;
                int adRewards = snapshot.getLong("ad_rewards") != null ?
                        snapshot.getLong("ad_rewards").intValue() : 0;
                int referralRewards = snapshot.getLong("referral_rewards") != null ?
                        snapshot.getLong("referral_rewards").intValue() : 0;
                boolean isPremium = Boolean.TRUE.equals(snapshot.getBoolean("is_premium"));
                String isAdmin = (String) snapshot.getString("userType");
                Object last_login = snapshot.getString("last_login") != null ?
                        snapshot.getString("last_login") : SecureLicenseManager.getInstance(this).getLast_login();
                new SyncPreferences(this).setKeyUserType(Objects.requireNonNullElse(isAdmin, "user"));

                Log.d("LicenseListener", "Firestore values -> " + "\n" +
                        "maxTransactions=" + maxTransactions + "\n" +
                        ", transactionsCount=" + transactionsCount + "\n" +
                        ", adRewards=" + adRewards + "\n" +
                        ", referralRewards=" + referralRewards + "\n" +
                        ", last_login=" + last_login + "\n" +
                        ", isPremium=" + isPremium);

                SecureLicenseManager.getInstance(this)
                        .saveLicenseData(maxTransactions, transactionsCount,
                                adRewards, referralRewards, isPremium, lastModified, last_login);
            }
        });
    }

    private void checkDeviceBanOnStart() {
        deviceBanManager.checkDeviceBan(new DeviceBanManager.BanCheckListener() {
            @Override
            public void onCheckComplete(boolean isBanned, String reason) {
                runOnUiThread(() -> {
                    if (isBanned) {
                        Log.e(TAG, "الجهاز محظور: " + reason);
                        if (isDialogeShown) return;
                        showDeviceBanDialog(reason);
                    }
                });
            }

            @Override
            public void onCheckError(String error) {
                Log.e(TAG, "خطأ في فحص الحظر: " + error);
            }
        });
    }

    /**
     * ✅ عرض ديالوج الحظر
     */
    boolean isDialogeShown = false;

    private void showDeviceBanDialog(String banReason) {
        isDialogeShown = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.device_block_title))
                .setMessage(getString(R.string.device_block_message))
                .setPositiveButton(getString(R.string.exit), (dialog, which) -> {
                    isDialogeShown = false;
                    finishAffinity(); // إغلاق التطبيق completamente
                })
                .setNegativeButton(getString(R.string.contact_support), (dialog, which) -> {
                    isDialogeShown = false;
                    sendToWhatsApp();
                })
                .setCancelable(false)
                .setIcon(R.drawable.ic_warning)
                .show();
    }

    private void sendToWhatsApp() {

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String email = user != null ? user.getEmail() : getString(R.string.not_specified);

        String message = "\n\n" + getString(R.string.whatsapp_greeting) + "\n\n" +
                getString(R.string.whatsapp_app_tit) + getString(R.string.app_name) + "\n\n" +
                getString(R.string.whatsapp_request) + "\n\n" +
                getString(R.string.deviceId, licenseManager.getDeviceId()) + "\n\n" +
                getString(R.string.whatsapp_request_final) + "\n\n" +
                getString(R.string.whatsapp_thank_you);

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            // Replace with actual WhatsApp number
            intent.setData(Uri.parse("https://wa.me/967734249712?text=" + Uri.encode(message)));
            startActivity(intent);
            finishAffinity();
        } catch (Exception e) {
            Toast.makeText(this, R.string.whatsapp_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void checkForAppUpdate(boolean manualCheck) {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.no_internet), Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "بدء فحص التحديثات...");
        Log.d(TAG, "الإصدار الحالي: " + versionManager.getCurrentVersionName());
        Log.d(TAG, "رقم البناء الحالي: " + versionManager.getCurrentVersionCode());
        logVersionInfo();
        versionManager.checkForUpdate(new VersionManager.UpdateListener() {
            @Override
            public void onUpdateAvailable(String latestVersion, String changelog, String downloadUrl) {
                Log.d(TAG, "تم العثور على تحديث جديد: " + latestVersion);
                Log.d(TAG, "رابط التحميل: " + downloadUrl);
                Log.d(TAG, "التغييرات: " + changelog);
                try {


                    runOnUiThread(() -> {
//                        UpdateAppDialog updateDialog = new UpdateAppDialog(
//                                MainActivity.this,
//                                latestVersion,
//                                changelog,
//                                downloadUrl
//                        );
                        UpdateAppDialog updateDialog = new UpdateAppDialog(
                                MainActivity.this,
                                latestVersion,
                                getString(R.string.update_available_message),
                                downloadUrl
                        );
                        updateDialog.show();
                    });
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            }

            @Override
            public void onNoUpdateAvailable() {
                Log.d(TAG, "لا توجد تحديثات جديدة - الإصدار الحالي هو الأحدث");
                runOnUiThread(() -> {
                    if (manualCheck) {
                        Toast.makeText(MainActivity.this,
                                getString(R.string.last_update_using),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "خطأ في فحص التحديثات: " + error);
                runOnUiThread(() -> {
//                    Toast.makeText(MainActivity.this,
//                            "فشل في فحص التحديثات: " + error,
//                            Toast.LENGTH_LONG).show();
                });
            }
        }, manualCheck);
    }

    private void logVersionInfo() {
        Log.d(TAG, "=== معلومات الإصدار ===");
        Log.d(TAG, "الإصدار الحالي: " + versionManager.getCurrentVersionName());
        Log.d(TAG, "رقم البناء: " + versionManager.getCurrentVersionCode());
        Log.d(TAG, "الإصدار السابق: " + versionManager.getLastKnownVersionName());
        Log.d(TAG, "رقم البناء السابق: " + versionManager.getLastKnownVersionCode());
        Log.d(TAG, "أول تشغيل: " + versionManager.isFirstLaunch());
        Log.d(TAG, "إصدار جديد: " + versionManager.isNewVersion());
        Log.d(TAG, "تحديث رئيسي: " + versionManager.isMajorUpdate());
        Log.d(TAG, "تحديث ثانوي: " + versionManager.isMinorUpdate());
        Log.d(TAG, "=========================");
    }

    private void createBackupFile() {
        Log.d(TAG, "بدء إنشاء نسخة احتياطية...");

        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/x-sqlite3");

            // التأكد من إضافة .db في اسم الملف
            String baseName = "Daftree_Backup_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String fileName = baseName + ".db";

            Log.d(TAG, "اسم ملف النسخة الاحتياطية: " + fileName);
            intent.putExtra(Intent.EXTRA_TITLE, fileName);

            backupLauncher.launch(intent);

        } catch (Exception e) {
            Log.e(TAG, "خطأ في إنشاء ملف النسخة الاحتياطية: " + e.getMessage());
            Toast.makeText(this, "خطأ في إنشاء ملف النسخة الاحتياطية", Toast.LENGTH_SHORT).show();
        }
    }

    private void openBackupFile() {
        Log.d(TAG, "فتح منتقي الملفات للاستيراد...");

        try {
            // فتح جميع أنواع الملفات المدعومة باستخدام OpenDocument
            String[] mimeTypes = {
                    "application/x-sqlite3",        // .db
                    "application/octet-stream",     // .p, .b
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xlsx, .xlcx
                    "application/vnd.ms-excel"      // .xls
            };

            openDocumentLauncher.launch(mimeTypes);
        } catch (Exception e) {
            Log.e(TAG, "خطأ في فتح منتقي الملفات: " + e.getMessage());
            Toast.makeText(this, "خطأ في فتح منتقي الملفات", Toast.LENGTH_SHORT).show();
        }
    }

    private void simulateRestoreProcess(String fileName) {
        // محاكاة عملية استيراد تستغرق بعض الوقت
        runOnUiThread(() ->
                Toast.makeText(MainActivity.this,
                        "جاري استيراد البيانات من: " + fileName,
                        Toast.LENGTH_SHORT).show()
        );

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "تم استيراد النسخة الاحتياطية بنجاح: " + fileName);
            runOnUiThread(() ->
                    Toast.makeText(MainActivity.this,
                            "تم استيراد النسخة الاحتياطية بنجاح",
                            Toast.LENGTH_SHORT).show()
            );
        }, 3000);
    }

    private boolean isSupportedBackupExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            Log.w(TAG, "امتداد الملف فارغ أو غير محدد");
            return false;
        }

        String lowerExtension = extension.toLowerCase();
        boolean supported = lowerExtension.equals("db") ||
                lowerExtension.equals("p") ||
                lowerExtension.equals("b") ||
                lowerExtension.equals("xlcx") ||
                lowerExtension.equals("xlsx") ||
                lowerExtension.equals("xls") ||
                lowerExtension.equals("sqlite") ||
                lowerExtension.equals("sqlite3") ||
                lowerExtension.equals("backup") ||
                lowerExtension.equals("bak");

        Log.d(TAG, "التحقق من الامتداد: " + lowerExtension + " - مدعوم: " + supported);
        return supported;
    }

    // ✅ دالة للتحقق ومزامنة الضيف عند توفر الإنترنت
    private void saveGuestToFirestore() {

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        Map<String, Object> guestDataMap = new HashMap<>();
        guestDataMap.put("ownerUID", guestUID.trim());
        guestDataMap.put("userType", "guest");
        guestDataMap.put("is_premium", false);
        guestDataMap.put("created_at", User.getCurrentLocalDateTime());
        guestDataMap.put("last_login", User.getCurrentLocalDateTime());
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

                                Log.d(TAG, "تم إنشاء المستخدم الضيف في فايرستور: " + guestUID);
                            } catch (Exception e) {
                                Log.e(TAG, "خطأ في إنشاء الضيف في فايرستور: " + e.getMessage());
                            }
                        }).start();
                    } else {
                        Log.e(TAG, "فشل إضافة الضيف في Firestore، سيتم المزامنة لاحقاً: " + task.getException());

                    }
//                    .
//                    setupGuestData();

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "فشل إضافة الضيف في Firestore: " + e.getMessage());

                });
    }

    private void updateGuestToFirestore() {
        if (!isNetworkAvailable()) return;
        String guestSatate = prefs.getString("guest_state", "NEW");
        if (guestSatate.equals("NEW")) {
            saveGuestToFirestore();
            return;
        }
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        Map<String, Object> updates = new HashMap<>();
        updates.put("last_login", User.getCurrentLocalDateTime());
        updates.put("login_count", FieldValue.increment(1));

        firestore.collection("guests").document(guestUID).update(updates)
                // firestore.collection("guests").document(guestUID.trim()).set(guestUser)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "تم تحديث حساب الضيف في Firestore: " + guestUID);

                    } else {
                        Log.e(TAG, "فشل تحديث حساب الضيف في Firestore، سيتم المزامنة لاحقاً: " + task.getException());

                    }
//                    .
//                    setupGuestData();

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "فشل تحديث حساب الضيف في Firestore: " + e.getMessage());
                    saveGuestToFirestore();
                });
    }

    public void deleteGuestAccountsWithSameDevice() {
        if (SecureLicenseManager.getInstance(this).guestUID() == null
                || SecureLicenseManager.getInstance(this).guestUID().isEmpty()
                || SecureLicenseManager.getInstance(this).guestUID() == "") {
            return;
        }
        String currentDeviceId = licenseManager.getDeviceId();
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
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
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
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



    /**
     * 🔥 إعداد توهج زر إضافة العملية للمستخدمين الجدد
     */
    private void setupAddTransactionButtonGlow() {
        // التحقق من أن هذا هو أول استخدام للمستخدم
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isFirstTimeUser = prefs.getBoolean("first_transaction_button_glow", true);

        if (isFirstTimeUser && binding.addTransactionButton != null) {
            Log.d(TAG, "إعداد توهج زر إضافة العملية للمستخدم الجديد");

            // إنشاء ObjectAnimator للتوهج
            ObjectAnimator glowAnimator = ObjectAnimator.ofFloat(
                    binding.addTransactionButton,
                    "alpha", 1.0f, 0.6f, 1.0f
            );
            glowAnimator.setDuration(1000); // ثانية واحدة
            glowAnimator.setRepeatCount(ObjectAnimator.INFINITE); // تكرار إلى ما لا نهاية
            glowAnimator.setRepeatMode(ObjectAnimator.REVERSE); // ذهاب وإياب

            // بدء التوهج بعد تأخير قصير
            new Handler().postDelayed(() -> {
                glowAnimator.start();

                // إيقاف التوهج عند النقر على الزر
                binding.addTransactionButton.setOnClickListener(v -> {
                    glowAnimator.cancel();

                    // حفظ حالة أن المستخدم استخدم الزر
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean("first_transaction_button_glow", false);
                    editor.apply();

                    // تنفيذ الوظيفة الأساسية للزر
                    if (availableCurrencies.isEmpty() || currentCurrencyIndex >= availableCurrencies.size()) {
                        Toast.makeText(this, "جاري تحميل العملات...", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Intent intent = new Intent(MainActivity.this, AddTransactionActivity.class);
                    intent.putExtra("CURRENCY", availableCurrencies.get(currentCurrencyIndex).name);
                    startActivity(intent);
                });

                // إيقاف التوهج بعد 10 ثوان إذا لم يتم النقر
                new Handler().postDelayed(() -> {
                    if (glowAnimator.isRunning()) {
                        glowAnimator.cancel();
                        Log.d(TAG, "انتهى توقيت توهج زر إضافة العملية");
                    }
                }, 10000); // 10 ثوان

            }, 2000); // بدء التوهج بعد ثانيتين
        }
    }


    /**
     * عرض تنبيه أن مكافآت الإحالة غير متاحة للمستخدمين الحاليين
     */
    private void showReferralNotAvailableDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.referral_not_available_title))
                .setMessage(getString(R.string.referral_not_available_message))
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> {
                    // إزالة referrerUid وتابع كضيف
                    SharedPreferences prefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
                    prefs.edit().remove("referrer_uid").apply();
                })
                .setCancelable(false)
                .show();
    }
    /**
     * تنفيذ إجراءات الإحالة للحوار الجديد
     */
    @Override
    public void onRegisterClicked(String referrerUid) {
        Log.d("ReferralAction", "تسجيل مستخدم جديد مع إحالة: " + referrerUid);
        Intent loginIntent = new Intent(this, LoginActivity.class);
        loginIntent.putExtra("REFERRER_UID", referrerUid);
        loginIntent.putExtra("registerGuest", true);
        startActivityForResult(loginIntent, LOGIN_REQUEST_CODE);
        startActivity(loginIntent);
    }

    @Override
    public void onCancel(String referrerUid) {
        Log.d("ReferralAction", "إلغاء الإحالة: " + referrerUid);
        SharedPreferences prefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
        prefs.edit().remove("referrer_uid").apply();
        this.referrerUid = "";
    }

    @Override
    public void onDismiss() {
        Log.d("ReferralAction", "إغلاق حوار مكافأة الإحالة");
        // في حالة إغلاق الحوار، تابع كضيف
        // 🔥 إزالة referrerUid للتأكد من عدم التداخل
        SharedPreferences prefs = getSharedPreferences("referral_prefs", MODE_PRIVATE);
        prefs.edit().remove("referrer_uid").apply();
        this.referrerUid = "";
    }
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // تحديث تخطيط الـ RecyclerView في جميع الـ Fragments
        updateRecyclerViewLayouts();

        // تحديث تخطيط الكارد السفلي
        updateBottomCardLayout();
    }

    private void updateRecyclerViewLayouts() {
        // تحديث الـ RecyclerView في الـ ViewPager الحالي
        if (binding.viewPager != null && binding.viewPager.getAdapter() != null) {
            int currentItem = binding.viewPager.getCurrentItem();

            // إعادة إنشاء الـ Adapter لتطبيق التغييرات
            Fragment currentFragment = getSupportFragmentManager()
                    .findFragmentByTag("f" + binding.viewPager.getCurrentItem());

            if (currentFragment instanceof AccountListFragment) {
                ((AccountListFragment) currentFragment).recreateLayoutManagerForMainActivity();
            }
        }
    }

    private void updateBottomCardLayout1() {
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (binding.bottomSummaryCard != null) {
            ViewGroup.LayoutParams params = binding.bottomSummaryCard.getLayoutParams();
            if (params instanceof ConstraintLayout.LayoutParams) {
                ConstraintLayout.LayoutParams constraintParams = (ConstraintLayout.LayoutParams) params;

                if (isLandscape) {
                    // تصميم مضغوط للوضع الأفقي
                    constraintParams.height = getResources().getDimensionPixelSize(R.dimen.bottom_card_height);
                    binding.bottomSummaryCard.setCardElevation(getResources().getDimension(R.dimen.card_elevation_land));
                } else {
                    // تصميم عادي للوضع العمودي
                    constraintParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    binding.bottomSummaryCard.setCardElevation(getResources().getDimension(R.dimen.card_elevation_land));
                }
                binding.bottomSummaryCard.setLayoutParams(constraintParams);
            }
        }
    }
    private void updateBottomCardLayout() {
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (binding.bottomSummaryCard != null) {
            ViewGroup.LayoutParams params = binding.bottomSummaryCard.getLayoutParams();
            if (params instanceof ConstraintLayout.LayoutParams) {
                ConstraintLayout.LayoutParams constraintParams = (ConstraintLayout.LayoutParams) params;

                if (isLandscape) {
                    // تصميم مضغوط للوضع الأفقي
                    try {
                        constraintParams.height = getResources().getDimensionPixelSize(R.dimen.bottom_card_height);
                        binding.bottomSummaryCard.setCardElevation(getResources().getDimension(R.dimen.card_elevation_land));
                    } catch (Resources.NotFoundException e) {
                        // استخدام قيم افتراضية في حالة عدم وجود الأبعاد
                        constraintParams.height = (int) (80 * getResources().getDisplayMetrics().density);
                        binding.bottomSummaryCard.setCardElevation(6f);
                    }
                } else {
                    // تصميم عادي للوضع العمودي
                    constraintParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    try {
                        binding.bottomSummaryCard.setCardElevation(getResources().getDimension(R.dimen.card_elevation));
                    } catch (Resources.NotFoundException e) {
                        binding.bottomSummaryCard.setCardElevation(4f);
                    }
                }
                binding.bottomSummaryCard.setLayoutParams(constraintParams);
            }
        }
    }
    // استدعاء هذه الدالة في onCreate بعد setupViewPager
    private void setupOrientationAwareLayout() {
        updateBottomCardLayout();
        updateRecyclerViewLayouts();
    }
}