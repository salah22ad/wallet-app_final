
package com.hpp.daftree.utils;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.hpp.daftree.LoginActivity;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.database.DeviceInfo;
import com.hpp.daftree.R;
import com.hpp.daftree.database.User;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.firebase.auth.FacebookAuthProvider;

public class GoogleAuthHelper {

    private static final String TAG = "GoogleAuthHelper";
    private static final int RC_SIGN_IN = 9001;
    private static final int FACEBOOK_LOGIN_REQUEST_CODE = 64206;
    private static final String USERS_COLLECTION = "users";
    private final Context context;
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final GoogleSignInClient googleSignInClient;
    private AuthCallback authCallback;
    private final LicenseManager licenseManager;
    private final ReferralManager referralManager;
private  DaftreeRepository repository;
    private CallbackManager facebookCallbackManager;
    private LoginManager facebookLoginManager;
    private VersionManager versionManager;
    private ActivityResultLauncher<Intent> facebookLoginLauncher;

    public interface AuthCallback {
        void onSignInFailure(String error);
        void onSignOutSuccess();
        void onSignInSuccess(FirebaseUser user, AuthResult authResult);
        void onSignInProgress(String message);
    }

    public GoogleAuthHelper(Context context, LicenseManager licenseManager,DaftreeRepository repository) {
        this.context = context;
        this.auth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
        this.licenseManager = licenseManager;
        this.referralManager = new ReferralManager(context);
        this.repository = repository;
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail().build();
        this.googleSignInClient = GoogleSignIn.getClient(context, gso);
        this.facebookCallbackManager = CallbackManager.Factory.create();
        this.facebookLoginManager = LoginManager.getInstance();
this.versionManager = new VersionManager(context);
        setupFacebookCallback();
    }
    // تهيئة ActivityResultLauncher
    // ✅ الطريقة الصحيحة لتسجيل الدخول بالفيسبوك
    public void signInWithFacebook(Activity activity, AuthCallback callback) {
        this.authCallback = callback;

        if (!isNetworkAvailable(activity)) {
            if (authCallback != null) {
                authCallback.onSignInFailure(context.getString(R.string.no_internet));
            }
            return;
        }

        if (authCallback != null) {
            authCallback.onSignInProgress(context.getString(R.string.connecting_facebook));
        }

        // ✅ الطريقة الصحيحة لتسجيل الدخول - استخدام الطريقة المباشرة
        facebookLoginManager.logInWithReadPermissions(
                activity,
                Arrays.asList("email", "public_profile")
        );
    }

    // ✅ إضافة طريقة لمعالجة onActivityResult من Activity
    public boolean onFacebookActivityResult(int requestCode, int resultCode, Intent data) {
        return facebookCallbackManager.onActivityResult(requestCode, resultCode, data);
    }

    // ✅ تحسين معالجة token الفيسبوك
    private void handleFacebookAccessToken(AccessToken token) {
        Log.d(TAG, "handleFacebookAccessToken:" + token);

        if (token == null) {
            Log.e(TAG, "Facebook AccessToken is null");
            if (authCallback != null) {
                authCallback.onSignInFailure("فشل في الحصول على رمز فيسبوك");
            }
            return;
        }

        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());
        auth.signInWithCredential(credential)
                .addOnCompleteListener((Activity) context, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            Log.d(TAG, "Facebook signInWithCredential:success - User: " + user.getEmail());

                            if (authCallback != null) {
                                authCallback.onSignInProgress(context.getString(R.string.loading_user_data));

                                boolean isNewUser = task.getResult().getAdditionalUserInfo().isNewUser();
                                Log.d(TAG, "Is new user: " + isNewUser);
                                saveOrUpdateUserInFirestore(user, task.getResult());
                            }
                        } else {
                            Log.e(TAG, "Facebook signInWithCredential:success but user is null");
                            if (authCallback != null) {
                                authCallback.onSignInFailure(context.getString(R.string.error_get_user_detail));
                            }
                        }
                    } else {
                        Log.w(TAG, "Facebook signInWithCredential:failure", task.getException());
                        String errorMessage = context.getString(R.string.facebook_auth_failed);
                        if (task.getException() != null) {
                            errorMessage += ": " + task.getException().getMessage();
                        }
                        if (authCallback != null) {
                            authCallback.onSignInFailure(errorMessage);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Facebook signInWithCredential:exception", e);
                    if (authCallback != null) {
                        authCallback.onSignInFailure("خطأ غير متوقع: " + e.getMessage());
                    }
                });
    }
    private void setupFacebookCallback() {
        facebookLoginManager.registerCallback(facebookCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                Log.d("FacebookLogin", "Login successful");
                if (authCallback != null) {
                    authCallback.onSignInProgress("جاري المصادقة مع فيسبوك...");
                }
                handleFacebookAccessToken(loginResult.getAccessToken());
            }

            @Override
            public void onCancel() {
                Log.d("FacebookLogin", "Login canceled");
                if (authCallback != null) {
                    authCallback.onSignInFailure(context.getString(R.string.facebook_login_canceled));
                }
            }

            @Override
            public void onError(FacebookException error) {
                Log.e("FacebookLogin", "Login error", error);
                if (authCallback != null) {
                    authCallback.onSignInFailure(("facebook_login_error") + ": " + error.getMessage());
                }
            }
        });
    }

    /**
     * بدء تسجيل الدخول عبر Facebook
     */
    public void signInWithFacebook1(AuthCallback callback) {
        this.authCallback = callback;

        if (!isNetworkAvailable((Activity) context)) {
            if (authCallback != null) {
                authCallback.onSignInFailure(context.getString(R.string.no_internet));
            }
            return;
        }

        if (authCallback != null) {
            authCallback.onSignInProgress(context.getString(R.string.connecting_facebook));
        }

        // إطلاق تسجيل الدخول باستخدام ActivityResultLauncher
        facebookLoginManager.logInWithReadPermissions(
                (Activity) context,
                Arrays.asList("email", "public_profile")
        );
    }



    /**
     * معالجة AccessToken الخاص بـ Facebook وتحويله إلى Firebase Credential
     */
    private void handleFacebookAccessToken1(AccessToken token) {
        Log.d("FacebookLogin", "handleFacebookAccessToken:" + token);

        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());
        auth.signInWithCredential(credential)
                .addOnCompleteListener((Activity) context, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null && authCallback != null) {
                            authCallback.onSignInProgress(context.getString(R.string.loading_user_data));

                            boolean isNewUser = task.getResult().getAdditionalUserInfo().isNewUser();
                            saveOrUpdateUserInFirestore(user, task.getResult());
                        }
                    } else {
                        String errorMessage = "";//context.getString(R.string.firebase_auth_failed);
                        if (task.getException() != null) {
                            errorMessage += ": " + task.getException().getMessage();
                        }
                        if (authCallback != null) {
                            authCallback.onSignInFailure(errorMessage);
                        }
                    }
                });
    }

    public void signIn_(ActivityResultLauncher<Intent> launcher, AuthCallback callback) {
        if (!isNetworkAvailable(context)) { // قد تحتاج لتمرير context هنا
            callback.onSignInFailure("لا يوجد اتصال بالإنترنت");
            return;
        }
        this.authCallback = callback;
        try {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            launcher.launch(signInIntent); // <-- التغيير الأهم: استخدام الـ launcher
        } catch (Exception e) {
            Log.e(TAG, "فشل في بدء تسجيل الدخول بـ Google", e);
            if (callback != null) callback.onSignInFailure("فشل في بدء تسجيل الدخول: " + e.getMessage());
        }
    }
    public void signIn(Activity activity, AuthCallback callback) {
        if (!isNetworkAvailable(activity)) {
            callback.onSignInFailure("لا يوجد اتصال بالإنترنت");
            return;
        }
        Log.e(TAG, "بدء تسجيل الدخول بـ Google");
        this.authCallback = callback;
//        Intent signInIntent = googleSignInClient.getSignInIntent();
//        activity.startActivityForResult(signInIntent, RC_SIGN_IN);
        try {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            activity.startActivityForResult(signInIntent, RC_SIGN_IN);

        } catch (Exception e) {
            Log.e(TAG, "فشل في بدء تسجيل الدخول بـ Google", e);
            if (callback != null) callback.onSignInFailure("فشل في بدء تسجيل الدخول: " + e.getMessage());
        }
    }
    public void handleSignInResult(Intent data) {
        if (data == null) {
            if (authCallback != null) authCallback.onSignInFailure("فشل في الحصول على بيانات التسجيل");
            return;
        }

        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account != null) {
                if (account.getIdToken() == null) {
                    if (authCallback != null) authCallback.onSignInFailure("رمز المصادقة غير صالح");
                    return;
                }

                firebaseAuthWithGoogle(account.getIdToken());
            }
        } catch (ApiException e) {
            Log.w(TAG, "فشل تسجيل الدخول بـ Google", e);
            if (authCallback != null) authCallback.onSignInFailure("فشل تسجيل الدخول بـ Google: " + e.getStatusCode());
        }
    }

    public void handleSignInResult1(Intent data) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account != null) firebaseAuthWithGoogle(account.getIdToken());
        } catch (ApiException e) {
            Log.w(TAG, "فشل تسجيل الدخول بـ Google", e);
            if (authCallback != null) authCallback.onSignInFailure("فشل تسجيل الدخول بـ Google: " + e.getStatusCode());
        }
    }

    private void firebaseAuthWithGoogle1(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential).addOnCompleteListener((Activity) context, task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = auth.getCurrentUser();
                if (user != null) saveOrUpdateUserInFirestore(user, task.getResult());
            } else {
                if (authCallback != null) authCallback.onSignInFailure("فشل المصادقة مع Firebase.");
            }
        });
    }

    private void firebaseAuthWithGoogle2(String idToken) {
        if (idToken == null || idToken.isEmpty()) {
            if (authCallback != null) {
                authCallback.onSignInFailure("رمز المصادقة غير صالح");
            }
            return;
        }

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential).addOnCompleteListener((Activity) context, task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = auth.getCurrentUser();
                if (user != null) {
                    saveOrUpdateUserInFirestore(user, task.getResult());
                } else {
                    if (authCallback != null) {
                        authCallback.onSignInFailure(context.getString(R.string.error_get_user_detail));
                    }
                }
            } else {
                String errorMessage = "فشل المصادقة مع Firebase";
                if (task.getException() != null) {
                    errorMessage += ": " + task.getException().getMessage();
                }
                if (authCallback != null) {
                    authCallback.onSignInFailure(errorMessage);
                }
            }
        });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        if (idToken == null || idToken.isEmpty()) {
            if (authCallback != null) {
                authCallback.onSignInFailure(context.getString(R.string.invalid_token));
            }
            return;
        }

        try {
            AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
            auth.signInWithCredential(credential).addOnCompleteListener((Activity) context, task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        saveOrUpdateUserInFirestore(user, task.getResult());
                    } else {
                        if (authCallback != null) {
                            authCallback.onSignInFailure(context.getString(R.string.ar_long_text_33));
                        }
                    }
                } else {
                    String errorMessage = context.getString(R.string.auth_failure);
                    if (task.getException() != null) {
                        errorMessage += ": " + task.getException().getMessage();
                    }
                    if (authCallback != null) {
                        authCallback.onSignInFailure(errorMessage);
                    }
                }
            }).addOnFailureListener(e -> {
                Log.e(TAG, "فشل في مصادقة Firebase", e);
                if (authCallback != null) {
                    authCallback.onSignInFailure(context.getString(R.string.auth_failure) + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "استثناء في مصادقة Firebase", e);
            if (authCallback != null) {
                authCallback.onSignInFailure("خطأ غير متوقع: " + e.getMessage());
            }
        }
    }
    private boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
    private void saveOrUpdateUserInFirestore(FirebaseUser firebaseUser, AuthResult authResult) {
        Log.d(TAG, "بدء حفظ/تحديث بيانات المستخدم: " + firebaseUser.getUid());

        DocumentReference userRef = firestore.collection("users").document(firebaseUser.getUid());

        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document != null && document.exists()) {
                    Log.d(TAG, "المستخدم موجود في Firestore - جاري التحديث");
                    updateUserLoginData(userRef, firebaseUser, authResult);

                } else {
                    Log.d(TAG, "مستخدم جديد - جاري الإنشاء");
                    createNewUserData(userRef, firebaseUser, authResult);
                }
            } else {
//                createNewUserData(userRef, firebaseUser, authResult);
                Log.e(TAG, "فشل في التحقق من وجود المستخدم", task.getException());
                if (authCallback != null) {
                    authCallback.onSignInFailure("فشل في التحقق من بيانات المستخدم: " +
                            (task.getException() != null ? task.getException().getMessage() : ""));
                }
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "استثناء في عملية التحقق", e);
            if (authCallback != null) {
                authCallback.onSignInFailure("خطأ في الاتصال: " + e.getMessage());
            }
        });
    }

    public void saveOrUpdateUserInFirestoreUpgrade(FirebaseUser firebaseUser) {
        Log.d(TAG, "بدء حفظ/تحديث بيانات المستخدم FirestoreUpgrade : " + firebaseUser.getUid());

        DocumentReference userRef = firestore.collection("users").document(firebaseUser.getUid());

        userRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document != null && document.exists()) {
                    Log.d(TAG, "المستخدم موجود في Firestore - جاري التحديث FirestoreUpgrade ");
                    if(!document.contains("db_upgrade")){
                        new  VersionManager(context).setFirst_upgrade(true);
                    }
                    updateUserLoginDataUpgrade(userRef, firebaseUser);

                } else {
                    Log.d(TAG, "مستخدم جديد - جاري الإنشاء FirestoreUpgrade ");

                    createNewUserDataUpgrade(userRef, firebaseUser);
                }

              new  VersionManager(context).setFirestoreUser_isAdded(true);
            } else {
                createNewUserDataUpgrade(userRef, firebaseUser);
                Log.e(TAG, "فشل في التحقق من وجود المستخدم FirestoreUpgrade ", task.getException());
                if (authCallback != null) {
                    authCallback.onSignInFailure("فشل في التحقق من بيانات المستخدم: " +
                            (task.getException() != null ? task.getException().getMessage() : ""));
                }
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "استثناء في عملية التحقق FirestoreUpgrade ", e);
            if (authCallback != null) {
                authCallback.onSignInFailure("خطأ في الاتصال: " + e.getMessage());
            }
        });
    }

    private void saveOrUpdateUserInFirestore1(FirebaseUser firebaseUser, AuthResult authResult) {
        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(firebaseUser.getUid());
        userRef.get().addOnCompleteListener(task -> {
          try{
            if (task.isSuccessful() && task.getResult() != null) {
                if (task.getResult().exists()) {
                    updateUserLoginData(userRef, firebaseUser, authResult);
                } else {
                    createNewUserData(userRef, firebaseUser, authResult);
                }
            } else {
                if (authCallback != null) authCallback.onSignInFailure("خطأ في التحقق من بيانات المستخدم.");

            }
          } catch (Exception e) {
              Log.w(TAG, "فشل تسجيل الدخول بـ Google", e);
              if (authCallback != null) authCallback.onSignInFailure("فشل تسجيل الدخول بـ Google: " + e);
          }
        });
    }

    private void createNewUserData1(DocumentReference userRef, FirebaseUser firebaseUser, AuthResult authResult) {
        User newUser = new User();
        newUser.setOwnerUID(firebaseUser.getUid());
        newUser.setEmail(firebaseUser.getEmail());
        newUser.setName(firebaseUser.getDisplayName());
        newUser.setProfileImageUri(firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : "");
        newUser.setIs_active(true);
        newUser.setIs_premium(false);
        newUser.setCreated_at(User.getCurrentLocalDateTime());
        newUser.setLogin_count(1);
        newUser.setMax_devices(LicenseManager.MAX_DEVICES);

        // --- تهيئة الحقول الجديدة للمستخدم الجديد ---
        newUser.setTransactions_count(0);
        newUser.setAd_rewards(0);
        newUser.setReferral_rewards(0);

        DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();
        newUser.getDevices().put(currentDevice.getDeviceId(), currentDevice);

        userRef.set(newUser).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "تم إنشاء بيانات مستخدم جديد بنجاح.");
                referralManager.applyReferralRewardIfAvailable(firebaseUser.getUid());
                if (authCallback != null) authCallback.onSignInSuccess(firebaseUser, authResult);
            } else {
                if (authCallback != null) authCallback.onSignInFailure("فشل في إنشاء بيانات المستخدم.");
            }
        });
    }
    private void createNewUserData(DocumentReference userRef, FirebaseUser firebaseUser, AuthResult authResult) {
        try {
            Log.d(TAG, "جار اضافة المستخدم الجديد");

            // الحصول على LiveData للمستخدم
            LiveData<User> userLiveData = repository.getUserProfile();

            // إضافة مراقب مع timeout لضمان عدم التعلق
            final Observer<User> userObserver = new Observer<User>() {
                @Override
                public void onChanged(User localUser) {
                    // إزالة المراقب فوراً لمنع التكرار
                    userLiveData.removeObserver(this);

                    Log.d(TAG, "تم الحصول على بيانات المستخدم المحلي: " + (localUser != null ? "موجود" : "غير موجود"));

                    User newUser = createUserObject(firebaseUser, localUser);

                    // حفظ المستخدم في Firestore
                    saveUserToFirestore(userRef, newUser, firebaseUser, authResult);
                }
            };

            // إضافة المراقب
            userLiveData.observeForever(userObserver);

            // إضافة timeout للسلامة - إذا لم تأتِ البيانات خلال 5 ثوانٍ
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                userLiveData.removeObserver(userObserver);
                if (!isUserSaved) { // تأكد من أن المتغير isUserSaved يُعرِّف في الكلاس
                    Log.w(TAG, "انتهت مهلة انتظار بيانات المستخدم المحلي، استخدام البيانات الافتراضية");
                    createDefualtNewUserData(userRef, firebaseUser, authResult);
                }
            }, 5000);

        } catch (RuntimeException e) {
            Log.e(TAG, "createNewUserData Error: " + e.getMessage(), e);
            createDefualtNewUserData(userRef, firebaseUser, authResult);
        }
    }
    // متغير لتتبع حالة حفظ المستخدم
    private boolean isUserSaved = false;

    private User createUserObject(FirebaseUser firebaseUser, User localUser) {
        User newUser = new User();
        newUser.setOwnerUID(firebaseUser.getUid());
        newUser.setEmail(firebaseUser.getEmail());

        // استخدام البيانات المحلية إذا كانت متاحة، وإلا استخدام بيانات Firebase
        if (localUser != null) {
            newUser.setName(localUser.getName());
            newUser.setAddress(localUser.getAddress());
            newUser.setCompany(localUser.getCompany());
            newUser.setPhone(localUser.getPhone());
        } else {
            newUser.setName(firebaseUser.getDisplayName());
            // تعيين القيم الافتراضية للحقول الأخرى
            newUser.setName(context.getString(R.string.ar_long_text_20));
            newUser.setCompany(context.getString(R.string.ar_long_text_20));
            newUser.setAddress(context.getString(R.string.ar_text_10_1));
            newUser.setPhone(context.getString(R.string.string_967_734_249_712));
        }

        newUser.setUserType("user");
        newUser.setSuccessfulReferrals(0);
        newUser.setIs_active(true);
        newUser.setIs_premium(false);
        newUser.setCreated_at(User.getCurrentLocalDateTime());
        newUser.setLogin_count(1);
        newUser.setMax_devices(LicenseManager.MAX_DEVICES);
        newUser.setTransactions_count(0);
        newUser.setMax_transactions(LicenseManager.FREE_TRANSACTION_LIMIT);
        newUser.setAd_rewards(0);
        newUser.setDb_upgrade(1);
        newUser.setReferral_rewards(0);
        newUser.setApp_Version(versionManager.getCurrentVersionName());
        DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();
        newUser.getDevices().put(currentDevice.getDeviceId(), currentDevice);

        return newUser;
    }

    private void saveUserToFirestore(DocumentReference userRef, User newUser, FirebaseUser firebaseUser, AuthResult authResult) {
        newUser.setApp_Version(versionManager.getCurrentVersionName());
        userRef.set(newUser).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                isUserSaved = true;
                Log.d(TAG, "تم إضافة المستخدم بنجاح في Firestore");

                // تطبيق مكافأة الإحالة إذا كانت متاحة
                referralManager.applyReferralRewardIfAvailable(firebaseUser.getUid());

                if (authCallback != null) {
                    authCallback.onSignInSuccess(firebaseUser, authResult);
                }
            } else {
                Log.e(TAG, "فشل في إنشاء بيانات المستخدم في Firestore", task.getException());
                if (authCallback != null) {
                    authCallback.onSignInFailure(context.getString(R.string.ar_long_text_33) +
                            (task.getException() != null ? task.getException().getMessage() : ""));
                }
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "فشل في عملية حفظ المستخدم في Firestore", e);
            if (authCallback != null) {
                authCallback.onSignInFailure("فشل في حفظ بيانات المستخدم: " + e.getMessage());
            }
        });
    }
    private void createDefualtNewUserData(DocumentReference userRef, FirebaseUser firebaseUser, AuthResult authResult) {
        try {
            Log.d(TAG, "إنشاء مستخدم جديد بالبيانات الافتراضية");

            User newUser = new User();
            newUser.setOwnerUID(firebaseUser.getUid());
            newUser.setEmail(firebaseUser.getEmail());
            newUser.setName(firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "تطبيق محفظتي الذكية");
            newUser.setCompany("ادارات مالية يومية");
            newUser.setAddress(" اليمن");
            newUser.setPhone("+967 734 249 712");
            newUser.setUserType("user");
            newUser.setSuccessfulReferrals(0);
            newUser.setIs_active(true);
            newUser.setIs_premium(false);
            newUser.setCreated_at(User.getCurrentLocalDateTime());
            newUser.setLogin_count(1);
            newUser.setMax_devices(LicenseManager.MAX_DEVICES);
            newUser.setTransactions_count(0);
            newUser.setMax_transactions(LicenseManager.FREE_TRANSACTION_LIMIT);
            newUser.setAd_rewards(0);
            newUser.setDb_upgrade(1);
            newUser.setReferral_rewards(0);
            newUser.setApp_Version(versionManager.getCurrentVersionName());
            DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();
            newUser.getDevices().put(currentDevice.getDeviceId(), currentDevice);

            saveUserToFirestore(userRef, newUser, firebaseUser, authResult);

        } catch (Exception e) {
            Log.e(TAG, "خطأ في createDefualtNewUserData", e);
            if (authCallback != null) {
                authCallback.onSignInFailure("خطأ غير متوقع: " + e.getMessage());
            }
        }
    }
    private void createNewUserData_(DocumentReference userRef, FirebaseUser firebaseUser, AuthResult authResult) {
        // Get user data from local database using LiveData
        try {
            LiveData<User> userLiveData = repository.getUserProfile();


            Log.d(TAG, "جار اضافة المستخدم الجديد '" );
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
                        newUser.setName(context.getString(R.string.ar_long_text_20));
                        newUser.setCompany(context.getString(R.string.ar_long_text_20));
                        newUser.setAddress(context.getString(R.string.ar_text_10_1));
                        newUser.setPhone(context.getString(R.string.string_967_734_249_712));
                    }
                    newUser.setUserType("user");
                    newUser.setSuccessfulReferrals(0);
                    newUser.setIs_active(true);
                    newUser.setIs_premium(false);
                    newUser.setCreated_at(User.getCurrentLocalDateTime());
                    newUser.setLogin_count(1);
                    newUser.setMax_devices(LicenseManager.MAX_DEVICES);
                    newUser.setTransactions_count(0);
                    newUser.setMax_transactions(LicenseManager.FREE_TRANSACTION_LIMIT);
                    newUser.setAd_rewards(0);
                    newUser.setDb_upgrade(1);
                    newUser.setReferral_rewards(0);

                    DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();
                    newUser.getDevices().put(currentDevice.getDeviceId(), currentDevice);

                    userRef.set(newUser).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
//                           repository.updateUser(newUser);
//                           referralManager.applyReferralRewardIfAvailable(firebaseUser.getUid());
                            if (authCallback != null) {

                                Log.d(TAG, "تم إضافة المستخدم بنجاح '" );
                                authCallback.onSignInSuccess(firebaseUser, authResult);
                            }
                        } else {
                            if (authCallback != null)
                                authCallback.onSignInFailure("فشل في إنشاء بيانات المستخدم.");
                        }
                    });
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG,"createNewUserData Error: "+ e);
            createDefualtNewUserData(userRef, firebaseUser, authResult);
        }
    }
    private void createNewUserDataUpgrade(DocumentReference userRef, FirebaseUser firebaseUser) {
        // Get user data from local database using LiveData
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
//                        newUser.setName(context.getString(R.string.ar_long_text_20));
                        newUser.setCompany(context.getString(R.string.ar_long_text_20));
                        newUser.setAddress(context.getString(R.string.ar_text_10_1));
                        newUser.setPhone(context.getString(R.string.string_967_734_249_712));
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
                    newUser.setApp_Version(versionManager.getCurrentVersionName());
                    DeviceInfo currentDevice = licenseManager.getCurrentDeviceInfo();
                    newUser.getDevices().put(currentDevice.getDeviceId(), currentDevice);

                    userRef.set(newUser).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
//                           repository.updateUser(newUser);
//                           referralManager.applyReferralRewardIfAvailable(firebaseUser.getUid());
                            new VersionManager(context).setFirestoreUser_isAdded(true);
                            if (authCallback != null) {
                              //  authCallback.onSignInSuccessUpgrade(firebaseUser);
                            }
                        } else {
                            if (authCallback != null)
                                authCallback.onSignInFailure("فشل في إنشاء بيانات المستخدم.");
                        }
                    });
                }
            });
        } catch (RuntimeException e) {
            Log.e(TAG,"createNewUserData Error: "+ e);
        }
    }

   private void updateUserLoginData(DocumentReference userRef, FirebaseUser firebaseUser, AuthResult authResult) {
        // تحديث بيانات المستخدم بدون إضافة الجهاز تلقائياً
        Map<String, Object> updates = new HashMap<>();
        updates.put("updated_at", User.getCurrentLocalDateTime());
        updates.put("last_login", User.getCurrentLocalDateTime());
        updates.put("login_count", FieldValue.increment(1));
        updates.put("is_active", true);
        updates.put("name", firebaseUser.getDisplayName());
        updates.put("app_Version", versionManager.getCurrentVersionName());
        updates.put("profileImageUri", firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : "");
        userRef.set(updates, SetOptions.merge()).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "تم تحديث بيانات تسجيل الدخول للمستخدم 1 .");
                if (authCallback != null) authCallback.onSignInSuccess(firebaseUser, authResult);
            } else {
                if (authCallback != null) authCallback.onSignInFailure("فشل في تحديث بيانات تسجيل الدخول.");
            }
        });
    }

    private void updateUserLoginDataUpgrade(DocumentReference userRef, FirebaseUser firebaseUser) {
        // تحديث بيانات المستخدم بدون إضافة الجهاز تلقائياً
       boolean isFirst=new VersionManager(context).first_upgrade();
        Map<String, Object> updates = new HashMap<>();
        if(isFirst){
            updates.put("db_upgrade", 1);
        }
        updates.put("updated_at", User.getCurrentLocalDateTime());
        updates.put("last_login", User.getCurrentLocalDateTime());
        updates.put("login_count", FieldValue.increment(1));
        updates.put("is_active", true);
        updates.put("name", firebaseUser.getDisplayName());
        updates.put("profileImageUri", firebaseUser.getPhotoUrl() != null ? firebaseUser.getPhotoUrl().toString() : "");
        updates.put("app_Version", versionManager.getCurrentVersionName());
        userRef.set(updates, SetOptions.merge()).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "تم تحديث بيانات تسجيل الدخول للمستخدم.");
               // if (authCallback != null) authCallback.onSignInSuccessUpgrade(firebaseUser);
            } else {
                if (authCallback != null) authCallback.onSignInFailure("فشل في تحديث بيانات تسجيل الدخول.");
            }
        });
    }

    public void signOut(AuthCallback callback) { this.authCallback = callback; googleSignInClient.signOut().addOnCompleteListener(task -> { licenseManager.signOutAndClearData(); if (callback != null) callback.onSignOutSuccess(); }); }
    public boolean isSignedIn() { return auth.getCurrentUser() != null; }
    public FirebaseUser getCurrentUser() { return auth.getCurrentUser(); }
    public static int getSignInRequestCode() { return RC_SIGN_IN; }

}