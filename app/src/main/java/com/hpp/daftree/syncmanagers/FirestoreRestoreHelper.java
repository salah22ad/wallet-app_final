
package com.hpp.daftree.syncmanagers;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.R;
import com.hpp.daftree.UUIDGenerator;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.database.User;
import com.hpp.daftree.helpers.LanguageHelper;
import com.hpp.daftree.utils.RewardManager;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class FirestoreRestoreHelper {
    private static final String TAG = "FirestoreRestoreHelper";

    public interface RestoreListener {
        void onProgressUpdate(String message, int progress, int total);

        void onComplete();

        void onError(String error);
    }

    private final AppDatabase appDb;
    private final FirebaseFirestore firestore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final String currentUserId;
    private final Context context;

    private final RewardManager rewardManager;

    public FirestoreRestoreHelper(Context context) {
        this.context = context.getApplicationContext();
        this.appDb = AppDatabase.getDatabase(context);
        this.firestore = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        this.currentUserId = (currentUser != null) ? currentUser.getUid() : null;
        rewardManager = new RewardManager();
    }


    private int totalCurrencies = 0;
    private int processedCurrencies = 0;

    private ListenerRegistration referralNotifListener;
    private final AtomicLong lastReferralCheck = new AtomicLong(0L);


    private void restoreUserProfile(RestoreListener listener) {
        // handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_userProfile), 0, 100));
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_accounts), 0, 100));
        firestore.collection("users")
                .document(currentUserId)
                .get()
                .addOnCompleteListener(task -> {
                    executor.execute(() -> {
                        if (task.isSuccessful() && task.getResult().exists()) {
                            DocumentSnapshot userDoc = task.getResult();
                            User remoteUser = userDoc.toObject(User.class);
                            // استخراج بيانات الترخيص
                            int maxTransactions = userDoc.getLong("max_transactions") != null ?
                                    userDoc.getLong("max_transactions").intValue() : 0;
                            int transactionsCount = userDoc.getLong("transactions_count") != null ?
                                    userDoc.getLong("transactions_count").intValue() : 0;
                            int adRewards = userDoc.getLong("ad_rewards") != null ?
                                    userDoc.getLong("ad_rewards").intValue() : 0;
                            int referralRewards = userDoc.getLong("referral_rewards") != null ?
                                    Objects.requireNonNull(userDoc.getLong("referral_rewards")).intValue() : 0;
                            boolean isPremium = Boolean.TRUE.equals(userDoc.getBoolean("is_premium"));
                            long lastModified = userDoc.contains("lastModified") ? userDoc.getLong("lastModified") : System.currentTimeMillis();

                            Object last_login =  userDoc.getString("last_login") != null ?
                                    userDoc.getString("last_login") : SecureLicenseManager.getInstance(context).getLast_login();

                            // حفظ البيانات في التخزين المشفر
                            SecureLicenseManager.getInstance(context)
                                    .saveLicenseData(maxTransactions, transactionsCount,
                                            adRewards, referralRewards, isPremium, lastModified,last_login);


                            assert remoteUser != null;
                            String isAdmin = userDoc.getString("userType");
                            if (isAdmin != null) {
                                new SyncPreferences(context).setKeyUserType(isAdmin);
                            } else {
                                new SyncPreferences(context).setKeyUserType("user");
                            }
                            // String isAdmin = (data.get("userType")) != null ? (String) data.get("userType") : "";

                            remoteUser.setAddress(userDoc.getString("address"));
                            remoteUser.setName(userDoc.getString("name"));
                            remoteUser.setCompany(userDoc.getString("company"));
                            remoteUser.setPhone(userDoc.getString("phone"));
                            Log.d(TAG, "تم استيراد بيانات الحساب: " + "\n" +
                                    "name =" + userDoc.getString("name") + "\n" +
                                    ", phone=" + userDoc.getString("phone") + "\n" +
                                    " , transactions_count=" + transactionsCount + "\n" +
                                    " , ad_rewards=" + adRewards);
                            appDb.userDao().upsert(remoteUser);
                            handler.post(() -> {
                                // listener.onProgressUpdate(context.getString(R.string.start_impoart_accounts), 5, 100);
                                Log.w(TAG, "تم الانتهاء من جلب بيانات المستخدم '");
                                restoreAccountTypes(listener);
                            });
                        } else {
                            Log.w(TAG, "لم يتم العثور على بيانات الترخيص في Firestore");
                            handler.post(() -> restoreAccountTypes(listener));
                        }
                    });
                });
    }

    private void restoreAccountTypes1(RestoreListener listener) {
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_accountTypes), 5, 100));
        firestore.collection("accountTypes")
                .whereEqualTo("ownerUID", currentUserId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        executor.execute(() -> {
                            Map<String, String> typeNameToFirestoreIdMap = new HashMap<>();
                            for (QueryDocumentSnapshot doc : task.getResult()) {
                                AccountType accountType = doc.toObject(AccountType.class);
                                accountType.setFirestoreId(doc.getId());
                                accountType.setSyncStatus("SYNCED");

                                if (appDb.accountTypeDao().getAccountTypeByFirestoreId(doc.getId()) == null) {
                                    appDb.accountTypeDao().insert(accountType);
                                }
                                typeNameToFirestoreIdMap.put(accountType.getName(), accountType.getFirestoreId());
                            }
                            // بعد الانتهاء، ابدأ الخطوة التالية: استيراد الحسابات، ومرر الخريطة إليها
                            restoreAccounts(listener, typeNameToFirestoreIdMap);
                        });
                    } else {
                        handler.post(() -> listener.onError("فشل في جلب أنواع الحسابات: " + task.getException().getMessage()));
                    }
                });
    }
    private void restoreAccounts(RestoreListener listener, Map<String, String> typeNameToFirestoreIdMap) {
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_accounts), 15, 100));
        firestore.collection("accounts")
                .whereEqualTo("ownerUID", currentUserId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot accountsSnapshot = task.getResult();
                        if (accountsSnapshot.isEmpty()) {
                            // لا يوجد حسابات، انتقل مباشرة لاستيراد العملات
                            restoreCurrencies(listener, new ArrayList<>(), new HashMap<>());
                            return;
                        }

                        executor.execute(() -> {
                            try {
                                // ⭐ إصلاح: استخدام معاملة مع التحقق من التكرار
                                appDb.runInTransaction(() -> {
                                    List<Account> accountsToProcess = new ArrayList<>();
                                    WriteBatch batch = firestore.batch();
                                    Set<DocumentReference> accountsToUpdate = new HashSet<>();
                                    Set<String> processedFirestoreIds = new HashSet<>();
                                    Set<String> processedAccountNames = new HashSet<>();

                                    // ⭐ أولاً: جمع جميع الحسابات للتحقق من التكرار
                                    for (QueryDocumentSnapshot doc : accountsSnapshot) {
                                        try {
                                            Account account = doc.toObject(Account.class);
                                            account.setFirestoreId(doc.getId());
                                            account.setSyncStatus("SYNCED");

                                            // ⭐ التحقق من التكرار
                                            if (processedFirestoreIds.contains(doc.getId())) {
                                                Log.w(TAG, "تخطي حساب مكرر: " + doc.getId());
                                                continue;
                                            }
                                            processedFirestoreIds.add(doc.getId());

                                            // ⭐ التحقق من تكرار اسم الحساب
                                            if (processedAccountNames.contains(account.getAccountName())) {
                                                Log.w(TAG, "تخطي حساب مكرر بالاسم: " + account.getAccountName());
                                                continue;
                                            }
                                            processedAccountNames.add(account.getAccountName());

                                            // **المنطق الجديد: التحقق من acTypeFirestoreId**
                                            if (!doc.contains("acTypeFirestoreId") || doc.getString("acTypeFirestoreId") == null || doc.getString("acTypeFirestoreId").isEmpty()) {
                                                String typeName = account.getAccountType();
                                                String firestoreId = typeNameToFirestoreIdMap.get(typeName);

                                                if (firestoreId != null) {
                                                    // إذا وجدنا ID مطابق، نجهز لتحديث المستند في Firestore
                                                    DocumentReference docRef = doc.getReference();
                                                    batch.update(docRef, "acTypeFirestoreId", firestoreId);
                                                    accountsToUpdate.add(docRef);
                                                    // ونقوم بتحديث الكائن المحلي الذي سيتم حفظه في Room
                                                    account.setAcTypeFirestoreId(firestoreId);
                                                } else {
                                                    Log.w(TAG, "لم يتم العثور على firestoreId لنوع الحساب: " + typeName);
                                                }
                                            }
                                            accountsToProcess.add(account);
                                        } catch (Exception e) {
                                            Log.e(TAG, "خطأ في معالجة الحساب: " + doc.getId(), e);
                                        }
                                    }

                                    // ⭐ ثانياً: تنفيذ تحديثات Firestore
                                    if (!accountsToUpdate.isEmpty()) {
                                        try {
                                            Tasks.await(batch.commit());
                                            Log.d(TAG, "تم تحديث " + accountsToUpdate.size() + " حساب بدون acTypeFirestoreId.");
                                        } catch (Exception e) {
                                            Log.e(TAG, "فشل تحديث الحسابات في Firestore: " + e.getMessage());
                                        }
                                    }

                                    // ⭐ ثالثاً: إدراج الحسابات في قاعدة البيانات المحلية مع التحقق من التكرار
                                    Map<String, Integer> firestoreToRoomIdMap = new HashMap<>();
                                    List<String> accountFirestoreIds = new ArrayList<>();

                                    for (Account acc : accountsToProcess) {
                                        try {
                                            // ⭐ التحقق من وجود الحساب مسبقاً باستخدام firestoreId
                                            Account existingByFirestoreId = appDb.accountDao().getAccountByFirestoreId(acc.getFirestoreId());
                                            if (existingByFirestoreId != null) {
                                                // ⭐ تحديث الحساب الموجود بدلاً من إدراجه
                                                acc.setId(existingByFirestoreId.getId()); // تعيين نفس ID
                                                appDb.accountDao().update(acc);
                                                Log.d(TAG, "تم تحديث حساب موجود: " + acc.getAccountName());
                                            } else {
                                                // ⭐ التحقق من وجود حساب بنفس الاسم (حماية إضافية)
                                                Account existingByName = appDb.accountDao().getAccountByName(acc.getAccountName());
                                                if (existingByName != null) {
                                                    Log.w(TAG, "تخطي حساب موجود مسبقاً بالاسم: " + acc.getAccountName());
                                                    // استخدام الحساب الموجود
                                                    firestoreToRoomIdMap.put(acc.getFirestoreId(), existingByName.getId());
                                                    accountFirestoreIds.add(acc.getFirestoreId());
                                                    continue;
                                                }

                                                // ⭐ إدراج حساب جديد
                                                long newRoomId = appDb.accountDao().insert(acc);
                                                firestoreToRoomIdMap.put(acc.getFirestoreId(), (int) newRoomId);
                                                accountFirestoreIds.add(acc.getFirestoreId());
                                                Log.d(TAG, "تم إدراج حساب جديد: " + acc.getAccountName() + " برقم: " + newRoomId);
                                            }
                                        } catch (Exception e) {
                                            Log.e(TAG, "فشل في معالجة الحساب: " + acc.getFirestoreId(), e);
                                            // ⭐ محاولة بديلة: استخدام upsert إذا كان متاحاً
                                            try {
                                                if (appDb.accountDao().getAccountByFirestoreId(acc.getFirestoreId()) == null) {
                                                    long newRoomId = appDb.accountDao().insert(acc);
                                                    firestoreToRoomIdMap.put(acc.getFirestoreId(), (int) newRoomId);
                                                    accountFirestoreIds.add(acc.getFirestoreId());
                                                }
                                            } catch (Exception ex) {
                                                Log.e(TAG, "فشل بديل في معالجة الحساب: " + acc.getFirestoreId(), ex);
                                            }
                                        }
                                    }

                                    Log.w(TAG, "تم معالجة " + accountsToProcess.size() + " حساب بنجاح");

                                    // ⭐ الانتقال للخطوة التالية بعد اكتمال المعاملة
                                    handler.post(() -> restoreCurrencies(listener, accountFirestoreIds, firestoreToRoomIdMap));
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "خطأ في معاملة الحسابات", e);
                                handler.post(() -> listener.onError("خطأ في معاملة الحسابات: " + e.getMessage()));
                            }
                        });
                    } else {
                        handler.post(() -> listener.onError("فشل جلب الحسابات: " + task.getException().getMessage()));
                    }
                });
    }
    private void restoreAccounts2(RestoreListener listener, Map<String, String> typeNameToFirestoreIdMap) {
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_accounts), 15, 100));
        firestore.collection("accounts")
                .whereEqualTo("ownerUID", currentUserId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot accountsSnapshot = task.getResult();
                        if (accountsSnapshot.isEmpty()) {
                            // لا يوجد حسابات، انتقل مباشرة لاستيراد العملات
                            restoreCurrencies(listener, new ArrayList<>(), new HashMap<>());
                            return;
                        }

                        List<Account> accountsToInsert = new ArrayList<>();
                        WriteBatch batch = firestore.batch();
                        Set<DocumentReference> accountsToUpdate = new HashSet<>();

                        for (QueryDocumentSnapshot doc : accountsSnapshot) {
                            Account account = doc.toObject(Account.class);
                            account.setFirestoreId(doc.getId());
                            account.setSyncStatus("SYNCED");

                            // **المنطق الجديد: التحقق من acTypeFirestoreId**
                            if (!doc.contains("acTypeFirestoreId") || doc.getString("acTypeFirestoreId") == null || doc.getString("acTypeFirestoreId").isEmpty()) {
                                String typeName = account.getAccountType();
                                String firestoreId = typeNameToFirestoreIdMap.get(typeName);

                                if (firestoreId != null) {
                                    // إذا وجدنا ID مطابق، نجهز لتحديث المستند في Firestore
                                    DocumentReference docRef = doc.getReference();
                                    batch.update(docRef, "acTypeFirestoreId", firestoreId);
                                    accountsToUpdate.add(docRef);
                                    // ونقوم بتحديث الكائن المحلي الذي سيتم حفظه في Room
                                    account.setAcTypeFirestoreId(firestoreId);
                                } else {
                                    Log.w(TAG, "لم يتم العثور على firestoreId لنوع الحساب: " + typeName);
                                }
                            }
                            accountsToInsert.add(account);
                        }

                        // تنفيذ التحديثات في Firestore دفعة واحدة
                        batch.commit().addOnCompleteListener(batchTask -> {
                            if (batchTask.isSuccessful()) {
                                Log.d(TAG, "تم تحديث " + accountsToUpdate.size() + " حساب بدون acTypeFirestoreId.");
                                // الآن، حفظ كل الحسابات في قاعدة البيانات المحلية
                                executor.execute(() -> {
                                    Map<String, Integer> firestoreToRoomIdMap = new HashMap<>();
                                    List<String> accountFirestoreIds = new ArrayList<>();
                                    appDb.runInTransaction(() -> {
                                        for (Account acc : accountsToInsert) {
                                            long newRoomId = appDb.accountDao().insert(acc);
                                            firestoreToRoomIdMap.put(acc.getFirestoreId(), (int) newRoomId);
                                            accountFirestoreIds.add(acc.getFirestoreId());
                                        }
                                    });
                                    Log.w(TAG, "تم الانتهاء من جلب الحسابات '");
                                    // بعد الانتهاء، انتقل للخطوة التالية
                                    restoreCurrencies(listener, accountFirestoreIds, firestoreToRoomIdMap);
                                });
                            } else {
                                handler.post(() -> listener.onError("فشل تحديث الحسابات في Firestore: " + batchTask.getException().getMessage()));
                            }
                        });
                    } else {
                        handler.post(() -> listener.onError("فشل جلب الحسابات: " + task.getException().getMessage()));
                    }
                });
    }

    public void startRestore(RestoreListener listener) {
        executor.execute(() -> {
            try {
                if (currentUserId == null) {
                    throw new Exception("لا يوجد مستخدم مسجل حاليًا. فشلت الاستعادة.");
                }
                // الخطوة الأولى: استيراد بيانات المستخدم والترخيص
                restoreUserProfile(listener);
            } catch (Exception e) {
                Log.e(TAG, "Restore from Firestore failed", e);
                handler.post(() -> listener.onError(e.getMessage()));
            }
        });
    }

    private void restoreCurrencies2(RestoreListener listener, List<String> accountFirestoreIds, Map<String, Integer> firestoreToRoomIdMap) {

        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_currencies), 30, 100));
        Map<String, String> currencyFirestoreIdMap = new HashMap<>();
        firestore.collection("currencies")
                .whereEqualTo("ownerUID", currentUserId)
                .orderBy("firestoreId", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot currenciesSnapshot = task.getResult();
                        totalCurrencies = currenciesSnapshot.size();
                        executor.execute(() -> {
                            if (!currenciesSnapshot.isEmpty()) {
                                DocumentSnapshot firstDoc = currenciesSnapshot.getDocuments().get(0);
                                if (firstDoc.contains("name")) {
                                    String defaultCurrencyName = firstDoc.getString("name");
                                    MyApplication.defaultCurrencyName = defaultCurrencyName;
                                    new SyncPreferences(context).setLocalCurrency(defaultCurrencyName);
                                    Log.e(TAG, "defaultCurrencyName: " + defaultCurrencyName);

                                    Currency localCurrency = appDb.currencyDao().getCurrencyByName(defaultCurrencyName);
                                    if (localCurrency == null) {
                                        Log.e(TAG, "Workaround: العملة الأساسية " + defaultCurrencyName + " غير موجودة محلياً، جاري إضافتها...");
                                        Currency emergencyCurrency = new Currency();
                                        emergencyCurrency.setName(defaultCurrencyName);
                                        emergencyCurrency.setOwnerUID(currentUserId);
                                        emergencyCurrency.setSyncStatus("SYNCED");
                                        emergencyCurrency.setFirestoreId(firstDoc.getString("firestoreId"));
                                        emergencyCurrency.setLastModified(System.currentTimeMillis());
                                        emergencyCurrency.setDefault(true);
                                        appDb.currencyDao().insert(emergencyCurrency);
                                        currencyFirestoreIdMap.put(defaultCurrencyName, emergencyCurrency.getFirestoreId());
                                    }
                                }
                            } else {
                                String defaultCurrencyName = MyApplication.defaultCurrencyName;
                                new SyncPreferences(context).setLocalCurrency(defaultCurrencyName);
                                Log.e(TAG, "defaultCurrencyName: " + defaultCurrencyName);
                                Currency localCurrency = appDb.currencyDao().getCurrencyByName(defaultCurrencyName);

                                if (localCurrency == null) {
                                    // أولاً: توليد firestoreId محلياً
                                    String firestoreId = UUIDGenerator.generateSequentialUUID();
                                    long currencyLastModified = System.currentTimeMillis();
                                    // ثانياً: إنشاء العملة محلياً مع firestoreId
                                    Currency newCurrency = new Currency();
                                    newCurrency.setName(defaultCurrencyName);
                                    newCurrency.setOwnerUID(currentUserId);
                                    newCurrency.setSyncStatus("NEW");
                                    newCurrency.setLastModified(currencyLastModified);
                                    newCurrency.setFirestoreId(firestoreId); // تعيين firestoreId المحلي

                                    newCurrency.setDefault(true);
                                    // ثالثاً: إضافة العملة للجدول المحلي
                                    appDb.currencyDao().insert(newCurrency);
                                    currencyFirestoreIdMap.put(defaultCurrencyName, firestoreId);
                                } else {
                                    Map<String, Object> currencyData = new HashMap<>();
                                    currencyData.put("name", localCurrency.getName());
                                    currencyData.put("ownerUID", currentUserId);
                                    currencyData.put("lastModified", localCurrency.getLastModified());
                                    currencyData.put("isDefault", true);
                                    currencyData.put("firestoreId", localCurrency.getFirestoreId());
                                    currencyData.put("id", localCurrency.getId());
                                    currencyFirestoreIdMap.put(localCurrency.getName(), localCurrency.getFirestoreId());
                                    // استخدام set() بدلاً من add() مع تحديد المعرف مسبقاً
                                    try {
                                        Tasks.await(firestore.collection("currencies").document(localCurrency.getFirestoreId().trim()).set(currencyData));

                                    } catch (Exception e) {
                                        Log.e("CurrencyRestore", "فشل إضافة عملة أساسية: " + defaultCurrencyName, e);
                                    }
                                }
                            }

                            // معالجة العملات الأخرى
                            for (DocumentSnapshot doc : currenciesSnapshot.getDocuments()) {
                                Currency currency = doc.toObject(Currency.class);
                                currency.setSyncStatus("SYNCED");

                                if (!doc.contains("firestoreId")) {
                                    currency.setFirestoreId(doc.getId());
                                    Map<String, Object> updates = new HashMap<>();
                                    updates.put("firestoreId", doc.getId());
                                    firestore.collection("currencies").document(doc.getId()).update(updates);
                                } else {
                                    currency.setFirestoreId(doc.getId());
                                }

                                // التحقق إذا كانت العملة موجودة محلياً
                                if (appDb.currencyDao().getCurrencyByFirestoreId(doc.getId()) == null) {
                                    appDb.currencyDao().insert(currency);
                                    currencyFirestoreIdMap.put(currency.getName(), currency.getFirestoreId());
                                }

                                processedCurrencies++;
                                int progress = 30 + (int) (((double) processedCurrencies / totalCurrencies) * 20);
                                handler.post(() -> listener.onProgressUpdate(context.getString(R.string.impoarting_currencies) + processedCurrencies + "/" + totalCurrencies, progress, 100));
                            }

                            // الانتقال إلى مرحلة استيراد المعاملات
                            // restoreTransactions(listener, currentUserId, accountFirestoreIds, firestoreToRoomIdMap);
                            Log.w(TAG, "تم الانتهاء من جلب العملات '");
                            restoreTransactions(listener, accountFirestoreIds, firestoreToRoomIdMap);
                        });
                    } else {
                        Log.e(TAG, "فشل في جلب العملات", task.getException());
                        handler.post(() -> listener.onError(context.getString(R.string.fail_impoart_currencies) + task.getException().getMessage()));
                    }
                });


        // ...
    }

    private void restoreTransactions(RestoreListener listener,
                                     List<String> accountFirestoreIds,
                                     Map<String, Integer> firestoreToRoomIdMap) {
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_transactions), 50, 100));

        // حساب العدد الإجمالي للعمليات
        AtomicInteger totalTransactions = new AtomicInteger(0);
        AtomicInteger processedTransactions = new AtomicInteger(0);

        // أولاً: حساب العدد الإجمالي للعمليات
        List<Task<QuerySnapshot>> countTasks = new ArrayList<>();
        final int CHUNK_SIZE = 30;
        if (accountFirestoreIds.isEmpty()) {
            handler.post(listener::onComplete);
            return;
        }
        for (int i = 0; i < accountFirestoreIds.size(); i += CHUNK_SIZE) {
            List<String> sublist = accountFirestoreIds.subList(i, Math.min(i + CHUNK_SIZE, accountFirestoreIds.size()));
            Task<QuerySnapshot> task = firestore.collection("transactions")
                    .whereIn("accountFirestoreId", sublist)
                    .get();
            countTasks.add(task);
        }

        Tasks.whenAllComplete(countTasks).addOnCompleteListener(countTask -> {
            for (Task<QuerySnapshot> task : countTasks) {
                if (task.isSuccessful()) {
                    totalTransactions.addAndGet(task.getResult().size());
                }
            }

            // الآن نبدأ باستيراد العمليات
            for (int i = 0; i < accountFirestoreIds.size(); i += CHUNK_SIZE) {
                List<String> sublist = accountFirestoreIds.subList(i, Math.min(i + CHUNK_SIZE, accountFirestoreIds.size()));

                firestore.collection("transactions")
                        .whereIn("accountFirestoreId", sublist)
                        .orderBy("lastModified", Query.Direction.ASCENDING)
                        .get()
                        .addOnCompleteListener(transactionTask -> {
                            if (transactionTask.isSuccessful()) {
                                QuerySnapshot transactionsSnapshot = transactionTask.getResult();

                                // نقل معالجة البيانات إلى الخيط الخلفي
                                executor.execute(() -> {
                                    for (QueryDocumentSnapshot doc : transactionsSnapshot) {
                                        try {
                                            Transaction tx = doc.toObject(Transaction.class);
                                            tx.setFirestoreId(doc.getId());
                                            tx.setSyncStatus("SYNCED");

                                            // معالجة العملة إذا كانت موجودة في الحقل القديم
                                            if (doc.contains("currency")) {
                                                String currencyName = doc.getString("currency");
                                                Integer localCurrencyId = appDb.currencyDao().checkCurrencyByName(currencyName);
                                                if (localCurrencyId == null) {
                                                    tx.setCurrencyId(appDb.currencyDao().getFirstIdCurrency());
                                                    tx.setCurrencyFirestoreId(appDb.currencyDao().getFirstFirestoreIdCurrency());
                                                    Log.w(TAG, "لم يتم العثور على العملة '" + currencyName + "', استخدام العملة الافتراضية (1)");
                                                }

                                                Map<String, Object> updates = new HashMap<>();
                                                updates.put("currencyId", tx.getCurrencyId());
                                                updates.put("currencyFirestoreId", tx.getCurrencyFirestoreId());
                                                updates.put("lastModified", System.currentTimeMillis());
                                                updates.put("currency", FieldValue.delete());
                                                firestore.collection("transactions").document(doc.getId()).update(updates);
                                                tx.setCurrencyId(localCurrencyId);
                                            }

                                            Integer localAccountId = appDb.accountDao().getAccountIDByFirestoreId(tx.getAccountFirestoreId());
                                            if (localAccountId == null) {
                                                Log.e(TAG, "الحساب غير موجود محلياً: " + tx.getAccountFirestoreId() + " للمعاملة: " + doc.getId());
                                               // firestore.collection("transactions").document(doc.getId()).delete();
                                                continue;
                                            }

                                            tx.setAccountId(localAccountId);
                                            Currency currency = appDb.currencyDao().getCurrencyByFirestoreId(tx.getCurrencyFirestoreId());
                                            Account existing = appDb.accountDao().getAccountByIdBlocking(localAccountId);
                                            tx.setAccountFirestoreId(existing.getFirestoreId());

                                            if (currency == null) {
                                                Log.e(TAG, "العملة غير موجودة محلياً: " + tx.getCurrencyId() + " للمعاملة: " + doc.getId());
                                                tx.setCurrencyId(appDb.currencyDao().getFirstIdCurrency());
                                                tx.setCurrencyFirestoreId(appDb.currencyDao().getFirstFirestoreIdCurrency());

                                            }

                                            String currencyFirestoreId = appDb.currencyDao().getCurrencyFirestoreId(tx.getCurrencyId());
                                            if (!doc.contains("currencyFirestoreId")
                                                    || tx.getCurrencyFirestoreId()== ""
                                                    || tx.getCurrencyFirestoreId()== null ) {
                                                tx.setCurrencyFirestoreId(currencyFirestoreId);
                                                Map<String, Object> updates = new HashMap<>();
                                                updates.put("lastModified", System.currentTimeMillis());
                                                updates.put("currencyFirestoreId", currencyFirestoreId);
                                                firestore.collection("transactions").document(doc.getId()).update(updates);
                                            }

                                            tx.setCurrencyFirestoreId(currencyFirestoreId);
                                            appDb.transactionDao().insert(tx);

                                            processedTransactions.incrementAndGet();
                                            int progress = 50 + (int) (((double) processedTransactions.get() / totalTransactions.get()) * 40);
                                            // handler.post(() -> listener.onProgressUpdate(context.getString(R.string.impoart_transactions)+ processedTransactions.get() + "/" + totalTransactions.get(), progress, 100));
                                            handler.post(() -> listener.onProgressUpdate("", 100, 100));

                                        } catch (Exception e) {
                                            Log.e(TAG, "فشل في معالجة المعاملة: " + doc.getId(), e);
                                        }
                                    }
                                    Log.w(TAG, "تم الانتهاء من جلب العمليات '");

                                    handler.post(listener::onComplete);
                                });
                            } else {
                                Log.e(TAG, "فشل في جلب المعاملات", transactionTask.getException());
                                handler.post(() -> listener.onError(context.getString(R.string.fail_impoart_transactions) + transactionTask.getException().getMessage()));
                                handler.post(listener::onComplete);
                            }
                        });
            }
        });
    }
    private void restoreCurrencies1(RestoreListener listener, List<String> accountFirestoreIds, Map<String, Integer> firestoreToRoomIdMap) {
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_currencies), 30, 100));

        Map<String, String> currencyFirestoreIdMap = new HashMap<>();

        firestore.collection("currencies")
                .whereEqualTo("ownerUID", currentUserId)
                .orderBy("firestoreId", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot currenciesSnapshot = task.getResult();
                        totalCurrencies = currenciesSnapshot.size();

                        executor.execute(() -> {
                            try {
                                // ⭐ إصلاح: استخدام معاملة لقاعدة البيانات لمنع التكرار
                                appDb.runInTransaction(() -> {
                                    // ... الكود الحالي للعملة الافتراضية ...
                                    if (!currenciesSnapshot.isEmpty()) {
                                        DocumentSnapshot firstDoc = currenciesSnapshot.getDocuments().get(0);
                                        if (firstDoc.contains("name")) {
                                            String defaultCurrencyName = firstDoc.getString("name");
                                            MyApplication.defaultCurrencyName = defaultCurrencyName;
                                            new SyncPreferences(context).setLocalCurrency(defaultCurrencyName);
                                            Log.e(TAG, "defaultCurrencyName: " + defaultCurrencyName);

                                            Currency localCurrency = appDb.currencyDao().getCurrencyByName(defaultCurrencyName);
                                            if (localCurrency == null) {
                                                Log.e(TAG, "Workaround: العملة الأساسية " + defaultCurrencyName + " غير موجودة محلياً، جاري إضافتها...");
                                                Currency emergencyCurrency = new Currency();
                                                emergencyCurrency.setName(defaultCurrencyName);
                                                emergencyCurrency.setOwnerUID(currentUserId);
                                                emergencyCurrency.setSyncStatus("SYNCED");
                                                emergencyCurrency.setFirestoreId(firstDoc.getString("firestoreId"));
                                                emergencyCurrency.setLastModified(System.currentTimeMillis());
                                                emergencyCurrency.setDefault(true);
                                                appDb.currencyDao().insert(emergencyCurrency);
                                                currencyFirestoreIdMap.put(defaultCurrencyName, emergencyCurrency.getFirestoreId());
                                            }
                                        }
                                    } else {
                                        String defaultCurrencyName = MyApplication.defaultCurrencyName;
                                        new SyncPreferences(context).setLocalCurrency(defaultCurrencyName);
                                        Log.e(TAG, "defaultCurrencyName: " + defaultCurrencyName);
                                        Currency localCurrency = appDb.currencyDao().getCurrencyByName(defaultCurrencyName);

                                        if (localCurrency == null) {
                                            // أولاً: توليد firestoreId محلياً
                                            String firestoreId = UUIDGenerator.generateSequentialUUID();
                                            long currencyLastModified = System.currentTimeMillis();
                                            // ثانياً: إنشاء العملة محلياً مع firestoreId
                                            Currency newCurrency = new Currency();
                                            newCurrency.setName(defaultCurrencyName);
                                            newCurrency.setOwnerUID(currentUserId);
                                            newCurrency.setSyncStatus("NEW");
                                            newCurrency.setLastModified(currencyLastModified);
                                            newCurrency.setFirestoreId(firestoreId); // تعيين firestoreId المحلي

                                            newCurrency.setDefault(true);
                                            // ثالثاً: إضافة العملة للجدول المحلي
                                            appDb.currencyDao().insert(newCurrency);
                                            currencyFirestoreIdMap.put(defaultCurrencyName, firestoreId);
                                        } else {
                                            Map<String, Object> currencyData = new HashMap<>();
                                            currencyData.put("name", localCurrency.getName());
                                            currencyData.put("ownerUID", currentUserId);
                                            currencyData.put("lastModified", localCurrency.getLastModified());
                                            currencyData.put("isDefault", true);
                                            currencyData.put("firestoreId", localCurrency.getFirestoreId());
                                            currencyData.put("id", localCurrency.getId());
                                            currencyFirestoreIdMap.put(localCurrency.getName(), localCurrency.getFirestoreId());
                                            // استخدام set() بدلاً من add() مع تحديد المعرف مسبقاً
                                            try {
                                                Tasks.await(firestore.collection("currencies").document(localCurrency.getFirestoreId().trim()).set(currencyData));

                                            } catch (Exception e) {
                                                Log.e("CurrencyRestore", "فشل إضافة عملة أساسية: " + defaultCurrencyName, e);
                                            }
                                        }
                                    }

                                    // ⭐ إصلاح: منع إضافة العملات المكررة
                                    Set<String> processedFirestoreIds = new HashSet<>();
                                    Set<String> processedCurrencyNames = new HashSet<>();

                                    for (DocumentSnapshot doc : currenciesSnapshot.getDocuments()) {
                                        try {
                                            Currency currency = doc.toObject(Currency.class);
                                            if (currency == null) continue;

                                            currency.setSyncStatus("SYNCED");
                                            String firestoreId = doc.getId();

                                            // ⭐ التحقق من عدم معالجة هذه العملة مسبقاً
                                            if (processedFirestoreIds.contains(firestoreId)) {
                                                Log.w(TAG, context.getString(R.string.skip_duplicate_currency, firestoreId));
                                                continue;
                                            }
                                            processedFirestoreIds.add(firestoreId);

                                            // ⭐ التحقق من عدم تكرار اسم العملة
                                            if (processedCurrencyNames.contains(currency.getName())) {
                                                Log.w(TAG, context.getString(R.string.skip_existing_currency_by_name, currency.getName()));
                                                continue;
                                            }

                                            if (!doc.contains("firestoreId")) {
                                                currency.setFirestoreId(firestoreId);
                                                Map<String, Object> updates = new HashMap<>();
                                                updates.put("firestoreId", firestoreId);
                                                // ⭐ تأجيل التحديث إلى Firestore لاحقاً
                                                firestore.collection("currencies").document(firestoreId).update(updates);
                                            } else {
                                                currency.setFirestoreId(firestoreId);
                                            }

                                            // ⭐ إصلاح: التحقق من وجود العملة باستخدام firestoreId واسم العملة
                                            Currency existingCurrency = appDb.currencyDao().getCurrencyByFirestoreId(firestoreId);
                                            if (existingCurrency == null) {
                                                // التحقق أيضاً بالاسم لمنع التكرار
                                                Currency existingByName = appDb.currencyDao().getCurrencyByName(currency.getName());
                                                if (existingByName == null) {
                                                    appDb.currencyDao().insert(currency);
                                                    currencyFirestoreIdMap.put(currency.getName(), currency.getFirestoreId());
                                                    processedCurrencyNames.add(currency.getName());
                                                    Log.d(TAG, context.getString(R.string.new_currency_added, currency.getName()));
                                                } else {
                                                    Log.w(TAG, context.getString(R.string.skip_existing_currency_by_name, currency.getName()));
                                                }
                                            } else {
                                                Log.w(TAG, context.getString(R.string.skip_existing_currency, firestoreId));
                                            }

                                            processedCurrencies++;
                                            int progress = 30 + (int) (((double) processedCurrencies / totalCurrencies) * 20);
                                            handler.post(() -> listener.onProgressUpdate(context.getString(R.string.impoarting_currencies) + processedCurrencies + "/" + totalCurrencies, progress, 100));

                                        } catch (Exception e) {
                                            Log.e(TAG, "فشل في معالجة العملة: " + doc.getId(), e);
                                        }
                                    }
                                });

                                // ⭐ إصلاح: الانتقال للمرحلة التالية بعد اكتمال المعاملة
                                Log.w(TAG, "تم الانتهاء من جلب العملات");
                                restoreTransactions(listener, accountFirestoreIds, firestoreToRoomIdMap);

                            } catch (Exception e) {
                                Log.e(TAG, context.getString(R.string.currency_transaction_error), e);
                                handler.post(() -> listener.onError(context.getString(R.string.currency_transaction_error) + e.getMessage()));
                            }
                        });
                    } else {
                        Log.e(TAG, "فشل في جلب العملات", task.getException());
                        handler.post(() -> listener.onError(context.getString(R.string.fail_impoart_currencies) + task.getException().getMessage()));
                    }
                });
    }
    private void restoreCurrencies_last(RestoreListener listener, List<String> accountFirestoreIds, Map<String, Integer> firestoreToRoomIdMap) {
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_currencies), 30, 100));

        Map<String, String> currencyFirestoreIdMap = new HashMap<>();

        firestore.collection("currencies")
                .whereEqualTo("ownerUID", currentUserId)
                .orderBy("firestoreId", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot currenciesSnapshot = task.getResult();
                        totalCurrencies = currenciesSnapshot.size();

                        executor.execute(() -> {
                            try {
                                appDb.runInTransaction(() -> {
                                    // ... [الكود الحالي للعملة الافتراضية يبقى كما هو] ...

                                    Set<String> processedFirestoreIds = new HashSet<>();
                                    Set<String> processedCurrencyNames = new HashSet<>();

                                    for (DocumentSnapshot doc : currenciesSnapshot.getDocuments()) {
                                        try {
                                            Currency currency = doc.toObject(Currency.class);
                                            if (currency == null) continue;

                                            currency.setSyncStatus("SYNCED");
                                            String firestoreId = doc.getId();

                                            if (processedFirestoreIds.contains(firestoreId)) {
                                                Log.w(TAG, context.getString(R.string.skip_duplicate_currency, firestoreId));
                                                continue;
                                            }
                                            processedFirestoreIds.add(firestoreId);

                                            if (processedCurrencyNames.contains(currency.getName())) {
                                                Log.w(TAG, context.getString(R.string.skip_existing_currency_by_name, currency.getName()));
                                                continue;
                                            }

                                            // ⭐ الفحص والتحديث الجديد: فحص وجود code و symbol في Firestore
                                            boolean needsFirestoreUpdate = false;
                                            Map<String, Object> firestoreUpdates = new HashMap<>();

                                            // إذا كانت الحقول code أو symbol غير موجودة في Firestore
                                            if (!doc.contains("code") || doc.getString("code") == null || doc.getString("code").isEmpty() ||
                                                    !doc.contains("symbol") || doc.getString("symbol") == null || doc.getString("symbol").isEmpty()) {

                                                // توليد code و symbol بنفس منطق الترقية
                                                CurrencyCodeSymbol currencyData = generateCurrencyCodeAndSymbol(currency.getName());

                                                // تعيين القيم للعملة المحلية
                                                currency.setCode(currencyData.code);
                                                currency.setSymbol(currencyData.symbol);

                                                // تجهيز التحديثات لـ Firestore
                                                firestoreUpdates.put("code", currencyData.code);
                                                firestoreUpdates.put("symbol", currencyData.symbol);
                                                needsFirestoreUpdate = true;

                                                Log.d(TAG, "تم توليد code و symbol للعملة: " + currency.getName() +
                                                        " -> code: " + currencyData.code + ", symbol: " + currencyData.symbol);
                                            } else {
                                                // إذا كانت الحقول موجودة في Firestore، نستخدمها
                                                currency.setCode(doc.getString("code"));
                                                currency.setSymbol(doc.getString("symbol"));
                                            }

                                            // تحديث firestoreId إذا كان مفقوداً
                                            if (!doc.contains("firestoreId")) {
                                                currency.setFirestoreId(firestoreId);
                                                firestoreUpdates.put("firestoreId", firestoreId);
                                                needsFirestoreUpdate = true;
                                            } else {
                                                currency.setFirestoreId(firestoreId);
                                            }

                                            // ⭐ تحديث Firestore إذا لزم الأمر
                                            if (needsFirestoreUpdate) {
                                                try {
                                                    // إضافة lastModified للتحديث
                                                    firestoreUpdates.put("lastModified", System.currentTimeMillis());

                                                    Tasks.await(firestore.collection("currencies")
                                                            .document(firestoreId)
                                                            .update(firestoreUpdates));

                                                    Log.d(TAG, "تم تحديث العملة في Firestore: " + currency.getName() +
                                                            " - code: " + currency.getCode() + ", symbol: " + currency.getSymbol());
                                                } catch (Exception e) {
                                                    Log.e(TAG, "فشل تحديث العملة في Firestore: " + firestoreId, e);
                                                }
                                            }

                                            // ⭐ التحقق من وجود العملة محلياً وإدراجها
                                            Currency existingCurrency = appDb.currencyDao().getCurrencyByFirestoreId(firestoreId);
                                            if (existingCurrency == null) {
                                                // التحقق أيضاً بالاسم لمنع التكرار
                                                Currency existingByName = appDb.currencyDao().getCurrencyByName(currency.getName());
                                                if (existingByName == null) {
                                                    appDb.currencyDao().insert(currency);
                                                    currencyFirestoreIdMap.put(currency.getName(), currency.getFirestoreId());
                                                    processedCurrencyNames.add(currency.getName());
                                                    Log.d(TAG, context.getString(R.string.new_currency_added, currency.getName()) +
                                                            " - code: " + currency.getCode() + ", symbol: " + currency.getSymbol());
                                                } else {
                                                    Log.w(TAG, context.getString(R.string.skip_existing_currency_by_name, currency.getName()));
                                                    // تحديث العملة الموجودة بالـ code و symbol الجديدين إذا لزم الأمر
                                                    if (!existingByName.getCode().equals(currency.getCode()) ||
                                                            !existingByName.getSymbol().equals(currency.getSymbol())) {
                                                        existingByName.setCode(currency.getCode());
                                                        existingByName.setSymbol(currency.getSymbol());
                                                        existingByName.setSyncStatus("EDITED");
                                                        appDb.currencyDao().update(existingByName);
                                                        Log.d(TAG, "تم تحديث العملة الموجودة: " + existingByName.getName() +
                                                                " - code: " + currency.getCode() + ", symbol: " + currency.getSymbol());
                                                    }
                                                }
                                            } else {
                                                Log.w(TAG, context.getString(R.string.skip_existing_currency, firestoreId));
                                                // تحديث العملة الموجودة إذا كانت البيانات مختلفة
                                                if (!existingCurrency.getCode().equals(currency.getCode()) ||
                                                        !existingCurrency.getSymbol().equals(currency.getSymbol())) {
                                                    existingCurrency.setCode(currency.getCode());
                                                    existingCurrency.setSymbol(currency.getSymbol());
                                                    existingCurrency.setSyncStatus("EDITED");
                                                    appDb.currencyDao().update(existingCurrency);
                                                    Log.d(TAG, "تم تحديث العملة الموجودة: " + existingCurrency.getName() +
                                                            " - code: " + currency.getCode() + ", symbol: " + currency.getSymbol());
                                                }
                                            }

                                            processedCurrencies++;
                                            int progress = 30 + (int) (((double) processedCurrencies / totalCurrencies) * 20);
                                            handler.post(() -> listener.onProgressUpdate(context.getString(R.string.impoarting_currencies) + processedCurrencies + "/" + totalCurrencies, progress, 100));

                                        } catch (Exception e) {
                                            Log.e(TAG, "فشل في معالجة العملة: " + doc.getId(), e);
                                        }
                                    }
                                });

                                Log.w(TAG, "تم الانتهاء من جلب العملات");
                                restoreTransactions(listener, accountFirestoreIds, firestoreToRoomIdMap);

                            } catch (Exception e) {
                                Log.e(TAG, context.getString(R.string.currency_transaction_error), e);
                                handler.post(() -> listener.onError(context.getString(R.string.currency_transaction_error) + e.getMessage()));
                            }
                        });
                    } else {
                        Log.e(TAG, "فشل في جلب العملات", task.getException());
                        handler.post(() -> listener.onError(context.getString(R.string.fail_impoart_currencies) + task.getException().getMessage()));
                    }
                });
    }
    private void restoreCurrencies55(RestoreListener listener, List<String> accountFirestoreIds, Map<String, Integer> firestoreToRoomIdMap) {
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_currencies), 30, 100));

        Map<String, String> currencyFirestoreIdMap = new HashMap<>();

        firestore.collection("currencies")
                .whereEqualTo("ownerUID", currentUserId)
                .orderBy("firestoreId", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot currenciesSnapshot = task.getResult();
                        totalCurrencies = currenciesSnapshot.size();

                        executor.execute(() -> {
                            try {
                                appDb.runInTransaction(() -> {
                                    Set<String> processedFirestoreIds = new HashSet<>();
                                    Set<String> processedCurrencyNames = new HashSet<>();

                                    // معالجة العملات من Firestore
                                    for (DocumentSnapshot doc : currenciesSnapshot.getDocuments()) {
                                        try {
                                            Currency remoteCurrency = doc.toObject(Currency.class);
                                            if (remoteCurrency == null) continue;

                                            String firestoreId = doc.getId();
                                            String currencyName = remoteCurrency.getName();

                                            // منع المعالجة المكررة
                                            if (processedFirestoreIds.contains(firestoreId) ||
                                                    processedCurrencyNames.contains(currencyName)) {
                                                Log.w(TAG, "تخطي عملة مكررة: " + currencyName);
                                                continue;
                                            }

                                            // البحث عن عملة محلية بنفس الاسم
                                            Currency localCurrency = appDb.currencyDao().getCurrencyByName(currencyName);

                                            if (localCurrency != null) {
                                                // ⭐ تحديث firestoreId المحلي بقيمة firestoreId من السحابة
                                                if (!firestoreId.equals(localCurrency.getFirestoreId())) {
                                                    localCurrency.setFirestoreId(firestoreId);
                                                    localCurrency.setSyncStatus("SYNCED");
                                                    localCurrency.setLastModified(System.currentTimeMillis());

                                                    // تحديث الحقول الأخرى إذا كانت مختلفة
                                                    if (remoteCurrency.getCode() != null &&
                                                            !remoteCurrency.getCode().equals(localCurrency.getCode())) {
                                                        localCurrency.setCode(remoteCurrency.getCode());
                                                    }
                                                    if (remoteCurrency.getSymbol() != null &&
                                                            !remoteCurrency.getSymbol().equals(localCurrency.getSymbol())) {
                                                        localCurrency.setSymbol(remoteCurrency.getSymbol());
                                                    }

                                                    appDb.currencyDao().update(localCurrency);
                                                    Log.d(TAG, "تم تحديث firestoreId المحلي للعملة: " + currencyName + " -> " + firestoreId);
                                                }
                                            } else {
                                                // إدخال عملة جديدة
                                                remoteCurrency.setFirestoreId(firestoreId);
                                                remoteCurrency.setSyncStatus("SYNCED");

                                                // تأكد من وجود code و symbol
                                                if (remoteCurrency.getCode() == null || remoteCurrency.getCode().isEmpty() ||
                                                        remoteCurrency.getSymbol() == null || remoteCurrency.getSymbol().isEmpty()) {
                                                    CurrencyCodeSymbol generated = generateCurrencyCodeAndSymbol(currencyName);
                                                    remoteCurrency.setCode(generated.code);
                                                    remoteCurrency.setSymbol(generated.symbol);
                                                }

                                                appDb.currencyDao().insert(remoteCurrency);
                                                Log.d(TAG, "تم إضافة عملة جديدة: " + currencyName);
                                            }

                                            currencyFirestoreIdMap.put(currencyName, firestoreId);
                                            processedFirestoreIds.add(firestoreId);
                                            processedCurrencyNames.add(currencyName);

                                            processedCurrencies++;
                                            int progress = 30 + (int) (((double) processedCurrencies / totalCurrencies) * 20);
                                            handler.post(() -> listener.onProgressUpdate(
                                                    context.getString(R.string.impoarting_currencies) + processedCurrencies + "/" + totalCurrencies,
                                                    progress, 100));

                                        } catch (Exception e) {
                                            Log.e(TAG, "فشل في معالجة العملة: " + doc.getId(), e);
                                        }
                                    }

                                    // التأكد من وجود العملة الافتراضية
                                    ensureDefaultCurrency(currencyFirestoreIdMap, processedCurrencyNames);
                                });

                                Log.w(TAG, "تم الانتهاء من جلب العملات");
                                restoreTransactions(listener, accountFirestoreIds, firestoreToRoomIdMap);

                            } catch (Exception e) {
                                Log.e(TAG, context.getString(R.string.currency_transaction_error), e);
                                handler.post(() -> listener.onError(context.getString(R.string.currency_transaction_error) + e.getMessage()));
                            }
                        });
                    } else {
                        Log.e(TAG, "فشل في جلب العملات", task.getException());
                        handler.post(() -> listener.onError(context.getString(R.string.fail_impoart_currencies) + task.getException().getMessage()));
                    }
                });
    }

    // دالة مساعدة للتأكد من وجود العملة الافتراضية
    private void ensureDefaultCurrency_last(Map<String, String> currencyFirestoreIdMap, Set<String> processedCurrencyNames) {
        try {
            String defaultCurrencyName = MyApplication.defaultCurrencyName;
            if (defaultCurrencyName == null || defaultCurrencyName.isEmpty()) {
                defaultCurrencyName = "ريال يمني"; // قيمة افتراضية
            }

            if (!processedCurrencyNames.contains(defaultCurrencyName)) {
                Currency localCurrency = appDb.currencyDao().getCurrencyByName(defaultCurrencyName);

                if (localCurrency == null) {
                    // إنشاء العملة الافتراضية
                    Currency newCurrency = new Currency();
                    newCurrency.setName(defaultCurrencyName);
                    newCurrency.setOwnerUID(currentUserId);
                    newCurrency.setDefault(true);
                    newCurrency.setSyncStatus("NEW");
                    newCurrency.setLastModified(System.currentTimeMillis());

                    CurrencyCodeSymbol generated = generateCurrencyCodeAndSymbol(defaultCurrencyName);
                    newCurrency.setCode(generated.code);
                    newCurrency.setSymbol(generated.symbol);

                    String firestoreId = UUIDGenerator.generateSequentialUUID();
                    newCurrency.setFirestoreId(firestoreId);

                    appDb.currencyDao().insert(newCurrency);
                    currencyFirestoreIdMap.put(defaultCurrencyName, firestoreId);
                    Log.i(TAG, "تم إنشاء العملة الافتراضية: " + defaultCurrencyName);
                } else {
                    currencyFirestoreIdMap.put(defaultCurrencyName, localCurrency.getFirestoreId());
                }
            }

            new SyncPreferences(context).setLocalCurrency(defaultCurrencyName);
            Log.d(TAG, "العملة الافتراضية: " + defaultCurrencyName);

        } catch (Exception e) {
            Log.e(TAG, "فشل في التأكد من العملة الافتراضية", e);
        }
    }

    /**
     * دالة محسنة لتوليد code و symbol للعملة
     */
    private CurrencyCodeSymbol generateCurrencyCodeAndSymbol11(String currencyName) {
        if (currencyName == null || currencyName.trim().isEmpty()) {
            return new CurrencyCodeSymbol("UNK", "?");
        }

        String cleanName = currencyName.trim().toLowerCase();

        // محاولة التعرف على العملة من خلال الخريطة
        CurrencyInfo recognizedCurrency = findMatchingCurrency(cleanName, createCurrencyMapping());
        if (recognizedCurrency != null) {
            return new CurrencyCodeSymbol(recognizedCurrency.code, recognizedCurrency.symbol);
        }

        // إذا لم يتم التعرف، استخدام المنطق المبسط
        SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String savedLanguage = prefs.getString("language", "ar");

        String code, symbol;

        if (savedLanguage.equals("ar") && cleanName.length() >= 2) {
            // للعربية: أول حرفين مع نقطة بينهما
            symbol = cleanName.substring(0, 1) + "." + cleanName.substring(1, 2);
            code = cleanName.length() >= 3 ? cleanName.substring(0, 3).toUpperCase() : cleanName.toUpperCase();
        } else {
            // للإنجليزية: أول 3 أحرف
            code = cleanName.length() >= 3 ? cleanName.substring(0, 3).toUpperCase() : cleanName.toUpperCase();
            symbol = code;
        }

        return new CurrencyCodeSymbol(code, symbol);
    }
    /**
     * دالة مساعدة لتوليد code و symbol للعملة بنفس منطق الترقية
     */
    private CurrencyCodeSymbol generateCurrencyCodeAndSymbol1(String currencyName) {
        if (currencyName == null || currencyName.trim().isEmpty()) {
            return new CurrencyCodeSymbol("UNK", "?");
        }

        String cleanName = currencyName.trim();
        String code;
        String symbol;

        // قراءة اللغة من SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String savedLanguage = prefs.getString("language", "ar");

        if (savedLanguage.equals("ar") && cleanName.length() >= 2) {
            // للعربية: أول حرفين مع نقطة بينهما
            symbol = cleanName.substring(0, 1) + "." + cleanName.substring(1, 2);
            code = cleanName.length() >= 3 ? cleanName.substring(0, 3).toUpperCase() : cleanName.toUpperCase();
        } else {
            // للإنجليزية: أول 3 أحرف كرمز وكود
            code = cleanName.length() >= 3 ? cleanName.substring(0, 3).toUpperCase() : cleanName.toUpperCase();
            symbol = code;
        }

        return new CurrencyCodeSymbol(code, symbol);
    }


    /**
     * دالة مساعدة لتوليد code و symbol للعملة مع التعرف على الأسماء المختلفة
     */
    private CurrencyCodeSymbol generateCurrencyCodeAndSymbol(String currencyName) {
        if (currencyName == null || currencyName.trim().isEmpty()) {
            return new CurrencyCodeSymbol("UNK", "?");
        }

        // أولاً: محاولة التعرف على العملة من خلال الخريطة
        CurrencyInfo recognizedCurrency = findMatchingCurrency(currencyName, createCurrencyMapping());
        if (recognizedCurrency != null) {
            return new CurrencyCodeSymbol(recognizedCurrency.code, recognizedCurrency.symbol);
        }

        // إذا لم يتم التعرف على العملة، نستخدم المنطق المبسط
        String cleanName = currencyName.trim();
        String code;
        String symbol;

        // قراءة اللغة من SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String savedLanguage = prefs.getString("language", "ar");

        if (savedLanguage.equals("ar") && cleanName.length() >= 2) {
            // للعربية: أول حرفين مع نقطة بينهما
            symbol = cleanName.substring(0, 1) + "." + cleanName.substring(1, 2);
            code = cleanName.length() >= 3 ? cleanName.substring(0, 3).toUpperCase() : cleanName.toUpperCase();
        } else {
            // للإنجليزية: أول 3 أحرف كرمز وكود
            code = cleanName.length() >= 3 ? cleanName.substring(0, 3).toUpperCase() : cleanName.toUpperCase();
            symbol = code;
        }

        return new CurrencyCodeSymbol(code, symbol);
    }

    /**
     * خريطة تحتوي على جميع الأسماء المحتملة للعملات مع رموزها وأكوادها
     */
    private Map<String, CurrencyInfo> createCurrencyMapping() {
        Map<String, CurrencyInfo> map = new HashMap<>();
        String currencySymbol = "﷼";
        // العملات العربية
        addCurrencyVariants(map, currencySymbol, "ر.س",
                "ريال سعودي", "ريال السعودي", "سعودي", "ريال سعودي", "الريال السعودي", "sar", "riyal", "saudi");

        addCurrencyVariants(map, currencySymbol, "ر.ي",
                "ريال يمني", "ريال اليمن", "يمني", "ريال يمني", "الريال اليمني", "yer", "yemeni", "yemen", "yemen rial", "yemeni rial");

        addCurrencyVariants(map, "ج.م", "EGP",
                "جنيه مصري", "جنيه مصرى", "مصري", "egp", "egyptian", "egypt");

        addCurrencyVariants(map, "د.إ", "AED",
                "درهم إماراتي", "درهم اماراتي", "درهم", "aed", "uae", "emirati");

        addCurrencyVariants(map, "د.ك", "KWD",
                "دينار كويتي", "دينار", "kwd", "kuwaiti", "kuwait");

        addCurrencyVariants(map, "د.ع", "IQD",
                "دينار عراقي", "عراقي", "iqd", "iraqi", "iraq");

        addCurrencyVariants(map, "ل.س", "SYP",
                "ليرة سورية", "ليرة سوريا", "ليرة", "syp", "syrian", "syria");

        addCurrencyVariants(map, "د.أ", "JOD",
                "دينار أردني", "دينار اردني", "أردني", "jod", "jordanian", "jordan");

        addCurrencyVariants(map,currencySymbol, "ر.ق",
                "ريال قطري", "قطري", "qar", "qatari", "qatar");

        addCurrencyVariants(map, "د.ب", "BHD",
                "دينار بحريني", "بحريني", "bhd", "bahraini", "bahrain");

        addCurrencyVariants(map,currencySymbol, "ر.ع",
                "ريال عماني", "عماني", "omr", "omani", "oman");

        addCurrencyVariants(map, "ل.ل", "LBP",
                "ليرة لبنانية", "لبناني", "lbp", "lebanese", "lebanon");

        // العملات الدولية
        addCurrencyVariants(map, "$", "USD",
                "دولار أمريكي", "دولار امريكي", "دولار", "دولار أمريكان", "دولار امريكان",
                "usd", "dollar", "american", "united states");

        addCurrencyVariants(map, "€", "EUR",
                "يورو", "eur", "euro");

        addCurrencyVariants(map, "£", "GBP",
                "جنيه إسترليني", "جنيه استرليني", "جنيه", "gbp", "pound", "british");

        addCurrencyVariants(map, "¥", "JPY",
                "ين ياباني", "ين", "jpy", "yen", "japanese");

        addCurrencyVariants(map, "₹", "INR",
                "روبية هندية", "روبية", "inr", "rupee", "indian");

        addCurrencyVariants(map, "₺", "TRY",
                "ليرة تركية", "تركي", "try", "turkish", "turkey");

        addCurrencyVariants(map, currencySymbol, "SAR",
                "sar", "saudi riyal", "saudi rial", "riyal"); // إضافة اختصارات إنجليزية

        addCurrencyVariants(map, currencySymbol, "YER",
                "yer", "yemen riyal", "yemeni riyal", "yemeni rial"); // إضافة اختصارات إنجليزية

        return map;
    }

    /**
     * إضافة متغيرات الأسماء للعملة في الخريطة
     */
    private void addCurrencyVariants(Map<String, CurrencyInfo> map, String symbol, String code, String... names) {
        CurrencyInfo info = new CurrencyInfo(symbol, code);
        for (String name : names) {
            map.put(normalizeName(name), info);
        }
    }

    /**
     * تطبيع اسم العملة لإزالة الهمزات والتباينات
     */
    private String normalizeName(String name) {
        if (name == null) return "";

        // إزالة الهمزات والتشكيل
        String normalized = name
                .replaceAll("[إأآا]", "ا")
                .replaceAll("ى", "ي")
                .replaceAll("ة", "ه")
                .replaceAll("ئ", "ي")
                .replaceAll("ؤ", "و")
                .replaceAll("\\p{M}", "") // إزالة التشكيل
                .replaceAll("\\s+", " ") // إزالة المسافات الزائدة
                .trim()
                .toLowerCase();

        return normalized;
    }

    /**
     * البحث عن عملة مطابقة في الخريطة
     */
    private CurrencyInfo findMatchingCurrency(String name, Map<String, CurrencyInfo> currencyMap) {
        if (name == null) return null;

        // محاولة المطابقة المباشرة
        String normalized = normalizeName(name);
        CurrencyInfo exactMatch = currencyMap.get(normalized);
        if (exactMatch != null) {
            Log.d(TAG, "تم التعرف على العملة بالمطابقة المباشرة: " + name + " -> " + exactMatch.code);
            return exactMatch;
        }

        // مطابقة جزئية
        for (Map.Entry<String, CurrencyInfo> entry : currencyMap.entrySet()) {
            if (normalized.contains(entry.getKey()) || entry.getKey().contains(normalized)) {
                Log.d(TAG, "تم التعرف على العملة بالمطابقة الجزئية: " + name + " -> " + entry.getValue().code);
                return entry.getValue();
            }
        }

        Log.d(TAG, "لم يتم التعرف على العملة: " + name + "، سيتم استخدام المنطق المبسط");
        return null;
    }

    /**
     * كلاس مساعد لحفظ معلومات العملة
     */
    private static class CurrencyInfo {
        String symbol;
        String code;

        CurrencyInfo(String symbol, String code) {
            this.symbol = symbol;
            this.code = code;
        }
    }

    /**
     * كلاس مساعد لحفظ code و symbol
     */
    private static class CurrencyCodeSymbol {
        String code;
        String symbol;

        CurrencyCodeSymbol(String code, String symbol) {
            this.code = code;
            this.symbol = symbol;
        }
    }
    private void restoreAccountTypes55(RestoreListener listener) {
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_accountTypes), 5, 100));

        firestore.collection("accountTypes")
                .whereEqualTo("ownerUID", currentUserId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        executor.execute(() -> {
                            try {
                                Map<String, String> typeNameToFirestoreIdMap = new HashMap<>();
                                Set<String> existingTypes = new HashSet<>();

                                // ⭐ إصلاح: استخدام معاملة لمنع التكرار
                                appDb.runInTransaction(() -> {
                                    Set<String> processedFirestoreIds = new HashSet<>();
                                    Set<String> processedTypeNames = new HashSet<>();

                                    // معالجة أنواع الحسابات من Firestore
                                    for (QueryDocumentSnapshot doc : task.getResult()) {
                                        try {
                                            AccountType accountType = doc.toObject(AccountType.class);
                                            if (accountType == null) continue;

                                            accountType.setFirestoreId(doc.getId());
                                            accountType.setSyncStatus("SYNCED");

                                            String firestoreId = doc.getId();
                                            String typeName = accountType.getName();

                                            // ⭐ التحقق من عدم معالجة هذا النوع مسبقاً
                                            if (processedFirestoreIds.contains(firestoreId)) {
                                                Log.w(TAG, "تخطي نوع حساب مكرر: " + firestoreId);
                                                continue;
                                            }
                                            processedFirestoreIds.add(firestoreId);

                                            // ⭐ التحقق من عدم تكرار اسم نوع الحساب
                                            if (processedTypeNames.contains(typeName)) {
                                                Log.w(TAG, "تخطي نوع حساب موجود مسبقاً بالاسم: " + typeName);
                                                continue;
                                            }

                                            // ⭐ التحقق من وجود نوع الحساب باستخدام firestoreId واسم النوع
                                            AccountType existingByFirestoreId = appDb.accountTypeDao().getAccountTypeByFirestoreId(firestoreId);
                                            if (existingByFirestoreId == null) {
                                                // التحقق أيضاً بالاسم لمنع التكرار
                                                AccountType existingByName = appDb.accountTypeDao().getAccountTypeByName(typeName);
                                                if (existingByName == null) {
                                                    appDb.accountTypeDao().insert(accountType);
                                                    typeNameToFirestoreIdMap.put(typeName, firestoreId);
                                                    processedTypeNames.add(typeName);
                                                    existingTypes.add(typeName);
                                                    Log.d(TAG, "تم إضافة نوع حساب جديد: " + typeName);
                                                } else {
                                                    Log.w(TAG, "تخطي نوع حساب موجود مسبقاً بالاسم: " + typeName);
                                                    typeNameToFirestoreIdMap.put(typeName, existingByName.getFirestoreId());
                                                    existingTypes.add(typeName);
                                                }
                                            } else {
                                                Log.w(TAG, "تخطي نوع حساب موجود مسبقاً: " + firestoreId);
                                                typeNameToFirestoreIdMap.put(typeName, firestoreId);
                                                existingTypes.add(typeName);
                                            }
                                        } catch (Exception e) {
                                            Log.e(TAG, "فشل في معالجة نوع الحساب: " + doc.getId(), e);
                                        }
                                    }

                                    // ⭐ إصلاح: إضافة الأنواع الأساسية المفقودة مع التحقق من التكرار
                                    Resources localizedResources = LanguageHelper.getLocalizedResources(context);
                                    String[] requiredTypes = {
                                            localizedResources.getString(R.string.account_type_customer),
                                            localizedResources.getString(R.string.account_type_supplier),
                                            localizedResources.getString(R.string.account_type_general)
                                    };

                                    // إضافة الأنواع المفقودة
                                    for (String requiredType : requiredTypes) {
                                        if (!existingTypes.contains(requiredType)) {
                                            addMissingAccountType55(requiredType, typeNameToFirestoreIdMap, processedTypeNames);
                                        }
                                    }
                                });

                                Log.w(TAG, "تم الانتهاء من جلب انواع الحسابات");
                                // الانتقال للخطوة التالية بعد معالجة جميع الأنواع
                                handler.post(() -> restoreAccounts(listener, typeNameToFirestoreIdMap));

                            } catch (Exception e) {
                                Log.e(TAG, "خطأ في معالجة أنواع الحسابات", e);
                                handler.post(() -> listener.onError("خطأ في معالجة أنواع الحسابات: " + e.getMessage()));
                            }
                        });
                    } else {
                        handler.post(() -> listener.onError("فشل في جلب أنواع الحسابات: " + task.getException().getMessage()));
                    }
                });
    }
    private void restoreAccountTypes(RestoreListener listener) {
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_accountTypes), 5, 100));

        firestore.collection("accountTypes")
                .whereEqualTo("ownerUID", currentUserId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        executor.execute(() -> {
                            try {
                                Map<String, String> typeNameToFirestoreIdMap = new HashMap<>();

                                appDb.runInTransaction(() -> {
                                    Set<String> processedFirestoreIds = new HashSet<>();
                                    Set<String> processedTypeNames = new HashSet<>();

                                    // معالجة أنواع الحسابات من Firestore
                                    for (QueryDocumentSnapshot doc : task.getResult()) {
                                        try {
                                            AccountType remoteAccountType = doc.toObject(AccountType.class);
                                            if (remoteAccountType == null) continue;

                                            String firestoreId = doc.getId();
                                            String typeName = remoteAccountType.getName();

                                            // منع المعالجة المكررة
                                            if (processedFirestoreIds.contains(firestoreId) ||
                                                    processedTypeNames.contains(typeName)) {
                                                Log.w(TAG, "تخطي نوع حساب مكرر: " + typeName);
                                                continue;
                                            }

                                            // البحث عن نوع حساب محلي بنفس الاسم
                                            AccountType localAccountType = appDb.accountTypeDao().getAccountTypeByName(typeName);

                                            if (localAccountType != null) {
                                                // ⭐ تحديث firestoreId المحلي بقيمة firestoreId من السحابة
                                                if (!firestoreId.equals(localAccountType.getFirestoreId())) {
                                                    localAccountType.setFirestoreId(firestoreId);
                                                    localAccountType.setSyncStatus("SYNCED");
                                                    localAccountType.setLastModified(System.currentTimeMillis());
                                                    appDb.accountTypeDao().update(localAccountType);
                                                    Log.d(TAG, "تم تحديث firestoreId المحلي لنوع الحساب: " + typeName + " -> " + firestoreId);
                                                }
                                            } else {
                                                // إدخال نوع حساب جديد
                                                remoteAccountType.setFirestoreId(firestoreId);
                                                remoteAccountType.setSyncStatus("SYNCED");
                                                appDb.accountTypeDao().insert(remoteAccountType);
                                                Log.d(TAG, "تم إضافة نوع حساب جديد: " + typeName);
                                            }

                                            typeNameToFirestoreIdMap.put(typeName, firestoreId);
                                            processedFirestoreIds.add(firestoreId);
                                            processedTypeNames.add(typeName);

                                        } catch (Exception e) {
                                            Log.e(TAG, "فشل في معالجة نوع الحساب: " + doc.getId(), e);
                                        }
                                    }

                                    // إضافة الأنواع الأساسية المفقودة
                                    addMissingAccountTypes(typeNameToFirestoreIdMap, processedTypeNames);
                                });

                                Log.w(TAG, "تم الانتهاء من جلب انواع الحسابات");
                                handler.post(() -> restoreAccounts(listener, typeNameToFirestoreIdMap));

                            } catch (Exception e) {
                                Log.e(TAG, "خطأ في معالجة أنواع الحسابات", e);
                                handler.post(() -> listener.onError("خطأ في معالجة أنواع الحسابات: " + e.getMessage()));
                            }
                        });
                    } else {
                        handler.post(() -> listener.onError("فشل في جلب أنواع الحسابات: " + task.getException().getMessage()));
                    }
                });
    }

    // دالة مساعدة لإضافة الأنواع المفقودة
    private void addMissingAccountTypes(Map<String, String> typeNameToFirestoreIdMap, Set<String> processedTypeNames) {
        try {
            Resources localizedResources = LanguageHelper.getLocalizedResources(context);
            String[] requiredTypes = {
                    localizedResources.getString(R.string.account_type_customer),
                    localizedResources.getString(R.string.account_type_supplier),
                    localizedResources.getString(R.string.account_type_general)
            };

            for (String requiredType : requiredTypes) {
                if (!processedTypeNames.contains(requiredType)) {
                    AccountType localType = appDb.accountTypeDao().getAccountTypeByName(requiredType);

                    if (localType == null) {
                        // إنشاء نوع حساب جديد
                        AccountType newType = new AccountType();
                        newType.setName(requiredType);
                        newType.setOwnerUID(currentUserId);
                        newType.setDefault(true);
                        newType.setSyncStatus("NEW");
                        newType.setLastModified(System.currentTimeMillis());

                        String firestoreId = UUIDGenerator.generateSequentialUUID();
                        newType.setFirestoreId(firestoreId);

                        appDb.accountTypeDao().insert(newType);
                        typeNameToFirestoreIdMap.put(requiredType, firestoreId);
                        processedTypeNames.add(requiredType);
                        Log.i(TAG, "تم إنشاء نوع حساب افتراضي: " + requiredType);
                    } else {
                        typeNameToFirestoreIdMap.put(requiredType, localType.getFirestoreId());
                        processedTypeNames.add(requiredType);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "فشل في إضافة الأنواع المفقودة", e);
        }
    }
    // ⭐ إصلاح: تحديث دالة addMissingAccountType لدعم الحماية من التكرار
    private void addMissingAccountType55(String typeName, Map<String, String> typeNameToFirestoreIdMap, Set<String> processedTypeNames) {
        try {
            // ⭐ التحقق من عدم معالجة هذا النوع مسبقاً
            if (processedTypeNames.contains(typeName)) {
                Log.w(TAG, "تخطي نوع حساب مكرر في الإضافة: " + typeName);
                return;
            }

            // التحقق من وجود النوع محلياً أولاً
            AccountType localType = appDb.accountTypeDao().getAccountTypeByName(typeName);

            if (localType != null) {
                // النوع موجود محلياً، نستخدمه
                typeNameToFirestoreIdMap.put(typeName, localType.getFirestoreId());
                processedTypeNames.add(typeName);
                return;
            }

            // إنشاء نوع حساب جديد
            AccountType newType = new AccountType();
            newType.setName(typeName);
            newType.setOwnerUID(currentUserId);
            newType.setDefault(true);
            newType.setSyncStatus("NEW");
            newType.setLastModified(System.currentTimeMillis());

            String firestoreId = UUIDGenerator.generateSequentialUUID();
            newType.setFirestoreId(firestoreId);

            // إضافة إلى قاعدة البيانات المحلية
            appDb.accountTypeDao().insert(newType);
            typeNameToFirestoreIdMap.put(typeName, firestoreId);
            processedTypeNames.add(typeName);

            Log.i(TAG, "تم إنشاء نوع حساب افتراضي: " + typeName);

        } catch (Exception e) {
            Log.e(TAG, "فشل إنشاء نوع الحساب الافتراضي: " + typeName, e);
        }
    }
    private void restoreAccountTypes2(RestoreListener listener) {
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_accountTypes), 5, 100));

        firestore.collection("accountTypes")
                .whereEqualTo("ownerUID", currentUserId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        executor.execute(() -> {
                            try {
                                Map<String, String> typeNameToFirestoreIdMap = new HashMap<>();
                                Set<String> existingTypes = new HashSet<>();

                                // معالجة أنواع الحسابات من Firestore
                                for (QueryDocumentSnapshot doc : task.getResult()) {
                                    AccountType accountType = doc.toObject(AccountType.class);
                                    accountType.setFirestoreId(doc.getId());
                                    accountType.setSyncStatus("SYNCED");

                                    if (appDb.accountTypeDao().getAccountTypeByFirestoreId(doc.getId()) == null) {
                                        appDb.accountTypeDao().insert(accountType);
                                    }
                                    typeNameToFirestoreIdMap.put(accountType.getName(), accountType.getFirestoreId());
                                    existingTypes.add(accountType.getName());
                                }

                                // أنواع الحسابات الأساسية المطلوبة
                                String[] requiredTypes = {
                                        context.getString(R.string.clients_account_type),
                                        context.getString(R.string.account_type_supplier),
                                        context.getString(R.string.account_type_general)
                                };

                                // إضافة الأنواع المفقودة
                                for (String requiredType : requiredTypes) {
                                    if (!existingTypes.contains(requiredType)) {
                                        addMissingAccountType2(requiredType, typeNameToFirestoreIdMap);
                                    }
                                }
                                Log.w(TAG, "تم الانتهاء من جلب انواع الحسابات '");
                                // الانتقال للخطوة التالية بعد معالجة جميع الأنواع
                                handler.post(() -> restoreAccounts(listener, typeNameToFirestoreIdMap));

                            } catch (Exception e) {
                                Log.e(TAG, "خطأ في معالجة أنواع الحسابات", e);
                                handler.post(() -> listener.onError(" " + e.getMessage()));
                            }
                        });
                    } else {
                        handler.post(() -> listener.onError("فشل في جلب أنواع الحسابات: " + task.getException().getMessage()));
                    }
                });
    }

    private void addMissingAccountType2(String typeName, Map<String, String> typeNameToFirestoreIdMap) {
        try {
            // التحقق من وجود النوع محلياً أولاً
            AccountType localType = appDb.accountTypeDao().getAccountTypeByName(typeName);

            if (localType != null) {
                // النوع موجود محلياً، نستخدمه
                typeNameToFirestoreIdMap.put(typeName, localType.getFirestoreId());
                return;
            }

            // إنشاء نوع حساب جديد
            AccountType newType = new AccountType();
            newType.setName(typeName);
            newType.setOwnerUID(currentUserId);
            newType.setDefault(true);
            newType.setSyncStatus("NEW");
            newType.setLastModified(System.currentTimeMillis());

            String firestoreId = UUIDGenerator.generateSequentialUUID();
            newType.setFirestoreId(firestoreId);

            // إضافة إلى قاعدة البيانات المحلية
            appDb.accountTypeDao().insert(newType);
            typeNameToFirestoreIdMap.put(typeName, firestoreId);

            Log.i(TAG, "تم إنشاء نوع حساب افتراضي: " + typeName);

        } catch (Exception e) {
            Log.e(TAG, "فشل إنشاء نوع الحساب الافتراضي: " + typeName, e);
        }
    }
    private void restoreCurrencies(RestoreListener listener, List<String> accountFirestoreIds, Map<String, Integer> firestoreToRoomIdMap) {
        handler.post(() -> listener.onProgressUpdate(context.getString(R.string.start_impoart_currencies), 30, 100));

        Map<String, String> currencyFirestoreIdMap = new HashMap<>();

        firestore.collection("currencies")
                .whereEqualTo("ownerUID", currentUserId)
                .orderBy("firestoreId", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot currenciesSnapshot = task.getResult();
                        totalCurrencies = currenciesSnapshot.size();

                        executor.execute(() -> {
                            try {
                                appDb.runInTransaction(() -> {
                                    Set<String> processedFirestoreIds = new HashSet<>();
                                    Set<String> processedCurrencyNames = new HashSet<>();

                                    // معالجة العملات من Firestore
                                    for (DocumentSnapshot doc : currenciesSnapshot.getDocuments()) {
                                        try {
                                            Currency remoteCurrency = doc.toObject(Currency.class);
                                            if (remoteCurrency == null) continue;

                                            String firestoreId = doc.getId();
                                            String currencyName = remoteCurrency.getName();

                                            // منع المعالجة المكررة
                                            if (processedFirestoreIds.contains(firestoreId) ||
                                                    processedCurrencyNames.contains(currencyName)) {
                                                Log.w(TAG, "تخطي عملة مكررة: " + currencyName);
                                                continue;
                                            }

                                            // ⭐ التأكد من وجود code و symbol في العملة المستوردة
                                            boolean needsCodeSymbolUpdate = false;
                                            if (remoteCurrency.getCode() == null || remoteCurrency.getCode().isEmpty() ||
                                                    remoteCurrency.getSymbol() == null || remoteCurrency.getSymbol().isEmpty()) {

                                                CurrencyCodeSymbol generated = generateCurrencyCodeAndSymbol(currencyName);
                                                remoteCurrency.setCode(generated.code);
                                                remoteCurrency.setSymbol(generated.symbol);
                                                needsCodeSymbolUpdate = true;
                                                Log.d(TAG, "تم توليد code و symbol للعملة المستوردة: " + currencyName +
                                                        " -> code: " + generated.code + ", symbol: " + generated.symbol);
                                            }

                                            // البحث عن عملة محلية بنفس الاسم
                                            Currency localCurrency = appDb.currencyDao().getCurrencyByName(currencyName);

                                            if (localCurrency != null) {
                                                // ⭐ تحديث firestoreId المحلي بقيمة firestoreId من السحابة
                                                if (!firestoreId.equals(localCurrency.getFirestoreId())) {
                                                    localCurrency.setFirestoreId(firestoreId);
                                                    localCurrency.setSyncStatus("SYNCED");
                                                    localCurrency.setLastModified(System.currentTimeMillis());

                                                    // ⭐ تحديث code و symbol إذا كانت غير موجودة في المحلي
                                                    if (localCurrency.getCode() == null || localCurrency.getCode().isEmpty() ||
                                                            localCurrency.getSymbol() == null || localCurrency.getSymbol().isEmpty()) {

                                                        CurrencyCodeSymbol generated = generateCurrencyCodeAndSymbol(currencyName);
                                                        localCurrency.setCode(generated.code);
                                                        localCurrency.setSymbol(generated.symbol);
                                                        Log.d(TAG, "تم توليد code و symbol للعملة المحلية: " + currencyName);
                                                    }

                                                    appDb.currencyDao().update(localCurrency);
                                                    Log.d(TAG, "تم تحديث firestoreId المحلي للعملة: " + currencyName + " -> " + firestoreId);
                                                } else {
                                                    // ⭐ حتى إذا كان firestoreId متطابق، تأكد من تحديث code و symbol إذا لزم الأمر
                                                    if (needsCodeSymbolUpdate &&
                                                            (localCurrency.getCode() == null || localCurrency.getCode().isEmpty() ||
                                                                    localCurrency.getSymbol() == null || localCurrency.getSymbol().isEmpty())) {

                                                        CurrencyCodeSymbol generated = generateCurrencyCodeAndSymbol(currencyName);
                                                        localCurrency.setCode(generated.code);
                                                        localCurrency.setSymbol(generated.symbol);
                                                        localCurrency.setLastModified(System.currentTimeMillis());
                                                        appDb.currencyDao().update(localCurrency);
                                                        Log.d(TAG, "تم تحديث code و symbol للعملة المحلية: " + currencyName);
                                                    }
                                                }
                                            } else {
                                                // إدخال عملة جديدة
                                                remoteCurrency.setFirestoreId(firestoreId);
                                                remoteCurrency.setSyncStatus("SYNCED");

                                                // ⭐ التأكد النهائي من وجود code و symbol
                                                if (remoteCurrency.getCode() == null || remoteCurrency.getCode().isEmpty() ||
                                                        remoteCurrency.getSymbol() == null || remoteCurrency.getSymbol().isEmpty()) {

                                                    CurrencyCodeSymbol generated = generateCurrencyCodeAndSymbol(currencyName);
                                                    remoteCurrency.setCode(generated.code);
                                                    remoteCurrency.setSymbol(generated.symbol);
                                                    Log.d(TAG, "التأكد النهائي - تم توليد code و symbol للعملة الجديدة: " + currencyName);
                                                }

                                                appDb.currencyDao().insert(remoteCurrency);
                                                Log.d(TAG, "تم إضافة عملة جديدة: " + currencyName);
                                            }

                                            // ⭐ تحديث Firestore إذا كانت تحتاج تحديث code و symbol
                                            if (needsCodeSymbolUpdate) {
                                                try {
                                                    Map<String, Object> updates = new HashMap<>();
                                                    updates.put("code", remoteCurrency.getCode());
                                                    updates.put("symbol", remoteCurrency.getSymbol());
                                                    updates.put("lastModified", System.currentTimeMillis());

                                                    Tasks.await(firestore.collection("currencies")
                                                            .document(firestoreId)
                                                            .update(updates));

                                                    Log.d(TAG, "تم تحديث العملة في Firestore: " + currencyName +
                                                            " - code: " + remoteCurrency.getCode() + ", symbol: " + remoteCurrency.getSymbol());
                                                } catch (Exception e) {
                                                    Log.e(TAG, "فشل تحديث العملة في Firestore: " + firestoreId, e);
                                                }
                                            }

                                            currencyFirestoreIdMap.put(currencyName, firestoreId);
                                            processedFirestoreIds.add(firestoreId);
                                            processedCurrencyNames.add(currencyName);

                                            processedCurrencies++;
                                            int progress = 30 + (int) (((double) processedCurrencies / totalCurrencies) * 20);
                                            handler.post(() -> listener.onProgressUpdate(
                                                    context.getString(R.string.impoarting_currencies) + processedCurrencies + "/" + totalCurrencies,
                                                    progress, 100));

                                        } catch (Exception e) {
                                            Log.e(TAG, "فشل في معالجة العملة: " + doc.getId(), e);
                                        }
                                    }

                                    // التأكد من وجود العملة الافتراضية مع code و symbol
                                    ensureDefaultCurrency(currencyFirestoreIdMap, processedCurrencyNames);
                                });

                                Log.w(TAG, "تم الانتهاء من جلب العملات");
                                restoreTransactions(listener, accountFirestoreIds, firestoreToRoomIdMap);

                            } catch (Exception e) {
                                Log.e(TAG, context.getString(R.string.currency_transaction_error), e);
                                handler.post(() -> listener.onError(context.getString(R.string.currency_transaction_error) + e.getMessage()));
                            }
                        });
                    } else {
                        Log.e(TAG, "فشل في جلب العملات", task.getException());
                        handler.post(() -> listener.onError(context.getString(R.string.fail_impoart_currencies) + task.getException().getMessage()));
                    }
                });
    }

    // تحديث دالة ensureDefaultCurrency لاستخدام generateCurrencyCodeAndSymbol
    private void ensureDefaultCurrency(Map<String, String> currencyFirestoreIdMap, Set<String> processedCurrencyNames) {
        try {
            String defaultCurrencyName = MyApplication.defaultCurrencyName;
            if (defaultCurrencyName == null || defaultCurrencyName.isEmpty()) {
                defaultCurrencyName = "ريال يمني"; // قيمة افتراضية
            }

            if (!processedCurrencyNames.contains(defaultCurrencyName)) {
                Currency localCurrency = appDb.currencyDao().getCurrencyByName(defaultCurrencyName);

                if (localCurrency == null) {
                    // إنشاء العملة الافتراضية
                    Currency newCurrency = new Currency();
                    newCurrency.setName(defaultCurrencyName);
                    newCurrency.setOwnerUID(currentUserId);
                    newCurrency.setDefault(true);
                    newCurrency.setSyncStatus("NEW");
                    newCurrency.setLastModified(System.currentTimeMillis());

                    // ⭐ استخدام generateCurrencyCodeAndSymbol لتعيين code و symbol
                    CurrencyCodeSymbol generated = generateCurrencyCodeAndSymbol(defaultCurrencyName);
                    newCurrency.setCode(generated.code);
                    newCurrency.setSymbol(generated.symbol);

                    String firestoreId = UUIDGenerator.generateSequentialUUID();
                    newCurrency.setFirestoreId(firestoreId);

                    appDb.currencyDao().insert(newCurrency);
                    currencyFirestoreIdMap.put(defaultCurrencyName, firestoreId);
                    Log.i(TAG, "تم إنشاء العملة الافتراضية: " + defaultCurrencyName +
                            " - code: " + generated.code + ", symbol: " + generated.symbol);
                } else {
                    // ⭐ التأكد من أن العملة المحلية الافتراضية تحتوي على code و symbol
                    if (localCurrency.getCode() == null || localCurrency.getCode().isEmpty() ||
                            localCurrency.getSymbol() == null || localCurrency.getSymbol().isEmpty()) {

                        CurrencyCodeSymbol generated = generateCurrencyCodeAndSymbol(defaultCurrencyName);
                        localCurrency.setCode(generated.code);
                        localCurrency.setSymbol(generated.symbol);
                        localCurrency.setLastModified(System.currentTimeMillis());
                        appDb.currencyDao().update(localCurrency);
                        Log.i(TAG, "تم تحديث code و symbol للعملة الافتراضية المحلية: " + defaultCurrencyName);
                    }
                    currencyFirestoreIdMap.put(defaultCurrencyName, localCurrency.getFirestoreId());
                }
            }

            new SyncPreferences(context).setLocalCurrency(defaultCurrencyName);
            Log.d(TAG, "العملة الافتراضية: " + defaultCurrencyName);

        } catch (Exception e) {
            Log.e(TAG, "فشل في التأكد من العملة الافتراضية", e);
        }
    }
}
