package com.hpp.daftree.models;


import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.hpp.daftree.MonthlySummary;
import com.hpp.daftree.TransactionWithAccount;
import com.hpp.daftree.UUIDGenerator;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountDao;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.AccountTypeDao;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.CurrencyDao;
import com.hpp.daftree.database.DeletionLog;
import com.hpp.daftree.database.DeletionLogDao;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.database.TransactionDao;
import com.hpp.daftree.database.User;
import com.hpp.daftree.database.UserDao;
import com.hpp.daftree.syncmanagers.SyncPreferences;
import com.hpp.daftree.syncmanagers.SyncWorker;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class DaftreeRepository {
    private static final String TAG = "DaftreeRepository";
    private final AccountDao mAccountDao;
    private final UserDao mUserDao;
    private final CurrencyDao currencyDao;
    private final AccountTypeDao accountTypeDao;
    private final TransactionDao mTransactionDao;
    private String currentUserId;
    private final DeletionLogDao mDeletionLogDao;
    private final WorkManager mWorkManager;
    private final AppDatabase db;
    private Account account;
    private final Context context;
    private final SyncPreferences syncPreferences;
    private boolean isGuest;
    private String guestUID;

    public DaftreeRepository(Application application) {
        db = AppDatabase.getDatabase(application);
        context = application.getApplicationContext();
        mAccountDao = db.accountDao();
        mTransactionDao = db.transactionDao();
        mDeletionLogDao = db.deletionLogDao();
        mUserDao = db.userDao();
        currencyDao = db.currencyDao();
        accountTypeDao = db.accountTypeDao();
        syncPreferences = new SyncPreferences(application.getApplicationContext());
        mWorkManager = WorkManager.getInstance(application);
        isGuest = SecureLicenseManager.getInstance(application.getApplicationContext()).isGuest();
        guestUID = SecureLicenseManager.getInstance(application.getApplicationContext()).guestUID();
        if (isGuest) {
            currentUserId = SecureLicenseManager.getInstance(application.getApplicationContext()).guestUID();
        } else {
            currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ?
                    FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        }
    }


    public UserDao getUserDao() {
        return mUserDao;
    }

    public void deleteTransaction(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (transaction.getFirestoreId() != null && !transaction.getFirestoreId().isEmpty()) {
                DeletionLog log = new DeletionLog();
                log.setFirestoreId(transaction.getFirestoreId());
                log.setCollectionName("transactions");
                mDeletionLogDao.insert(log);
            }
        });
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (transaction.getFirestoreId() != null && !transaction.getFirestoreId().isEmpty()) {
                transaction.setSyncStatus("DELETED");
                transaction.setLastModified(System.currentTimeMillis());
                mTransactionDao.update(transaction);
            }
            mTransactionDao.deleteById(transaction.getId());
            triggerSync();
//            SyncCoordinator.requestSync(context);
        });
    }


    public AccountDao getAccountDao() {
        return mAccountDao;
    }
    public TransactionDao getTransactionsDao() {
        return mTransactionDao;
    }
    public LiveData<List<String>> getDetailSuggestions(int accountId, String query) {
        return mTransactionDao.getDetailSuggestions(accountId, query);
    }

    public LiveData<Double> getDebitForAccount(int accountId, String currency) {
        return mTransactionDao.getDebitForAccount(accountId, currency);
    }

    public LiveData<Double> getCreditForAccount(int accountId, String currency) {
        return mTransactionDao.getCreditForAccount(accountId, currency);
    }

    // للتحقق من وجود بيانات غير متزامنة
    public boolean hasUnsyncedData() {
        // هذه دالة متزامنة يجب استدعاؤها من خيط خلفي
        int unsyncedAccounts = mAccountDao.getUnsyncedCount();
        int unsyncedTransactions = mTransactionDao.getUnsyncedCount();
        int unsyncedCurrencies = currencyDao.getUnsyncedCount();
        int unsyncedAccountType = accountTypeDao.getUnsyncedCount();
        int unsyncedDeletions = mDeletionLogDao.getDeletedUnsyncedCount();
        return (unsyncedAccounts + unsyncedTransactions + unsyncedDeletions) > 0;
    }

    public int getTransactionCountForAccount(int accountId) {
        // يجب أن يتم استدعاؤها من خيط خلفي
        return mTransactionDao.getTransactionCountForAccount(accountId);
    }

    public void updateAccount(Account account) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (!"NEW".equals(account.getSyncStatus())) {
                account.setSyncStatus("EDITED");
            }
            //account.setLastModified(System.currentTimeMillis());
            account.setLastModified(System.currentTimeMillis());
            mAccountDao.update(account);
            triggerSync(); // يمكنك إضافة دالة triggerSync مجددًا إذا أردت مزامنة فورية
//            SyncCoordinator.requestSync(context);
        });
    }

    public void triggerSync() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .build();

        String uniqueWorkName = "UNIQUE_SYNC_WORK";

        mWorkManager.beginUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.REPLACE, // المفتاح السحري هنا!
                syncRequest
        ).enqueue();
    }

    public LiveData<List<Transaction>> getTransactionsForAccount(int accountId, String currency, String searchQuery) {
        return mTransactionDao.getTransactionsForAccount(accountId, currency, searchQuery);
    }

    public void deleteAccount(Account account) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // الخطوة 1: جلب كل العمليات التابعة للحساب لتسجيلها للحذف من Firestore
            List<Transaction> transactionsToDelete = mTransactionDao.getTransactionsForAccountBlocking(account.getId());
            for (Transaction tx : transactionsToDelete) {
                if (tx.getFirestoreId() != null && !tx.getFirestoreId().isEmpty()) {
                    DeletionLog log = new DeletionLog();
                    log.setFirestoreId(tx.getFirestoreId());
                    log.setCollectionName("transactions");
                    Log.e("mDeletionLogDao transactions", tx.getFirestoreId());
                    mDeletionLogDao.insert(log);
                }
            }
            // الخطوة 2: تسجيل الحساب نفسه للحذف من Firestore
            if (account.getFirestoreId() != null && !account.getFirestoreId().isEmpty()) {
                DeletionLog log = new DeletionLog();
                log.setFirestoreId(account.getFirestoreId());
                log.setCollectionName("accounts");
                Log.e("mDeletionLogDao accounts", account.getFirestoreId());
                mDeletionLogDao.insert(log);
            }
        });
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // الخطوة 1: جلب كل العمليات التابعة للحساب لتسجيلها للحذف من Firestore
            List<Transaction> transactionsToDelete = mTransactionDao.getTransactionsForAccountBlocking(account.getId());
            for (Transaction tx : transactionsToDelete) {
                if (tx.getFirestoreId() != null && !tx.getFirestoreId().isEmpty()) {
                    tx.setSyncStatus("DELETED");
                    mTransactionDao.update(tx);
                }
            }

            // الخطوة 2: تسجيل الحساب نفسه للحذف من Firestore
            if (!account.getFirestoreId().isEmpty()) {
                account.setSyncStatus("DELETED");
                //account.setLastModified(System.currentTimeMillis());
                mAccountDao.update(account);
            }
            // الخطوة 3: حذف الحساب من Room (سيقوم Room بحذف كل عملياته تلقائيًا بسبب onDelete = CASCADE)
            // هذا الاستدعاء هو الذي سيُعلم LiveData بالتغيير.
            mAccountDao.delete(account);
            triggerSync();
        });

//        SyncCoordinator.requestSync(context);
    }

    public void batchUpsertTransactions(List<Map<String, Object>> changesBatch) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            for (Map<String, Object> change : changesBatch) {
                String changeType = (String) change.get("type");
                String firestoreId = (String) change.get("firestoreId");
                Map<String, Object> data = safeCastToMap(change.get("data"));

                if ("REMOVED".equals(changeType)) {
                    Log.w(TAG, "REMOVED: " + firestoreId);
                    mTransactionDao.deleteByFirestoreId(firestoreId);
                    continue;
                }

                // تحويل التاريخ بشكل آمن
                Object timestampObj = data.get("timestamp");
                Date timestamp = null;

                if (timestampObj instanceof Timestamp) {
                    timestamp = ((Timestamp) timestampObj).toDate();
                } else if (timestampObj instanceof Date) {
                    timestamp = (Date) timestampObj;
                } else {
                    Log.w(TAG, "Unknown timestamp type: " + (timestampObj != null ? timestampObj.getClass().getSimpleName() : "null"));
                    timestamp = new Date();
                }

                // البحث عن الحساب الأب
                String accountFirestoreId = (String) data.get("accountFirestoreId");
                Account parentAccount = mAccountDao.getAccountByFirestoreId(accountFirestoreId);

                Log.d(TAG, " Parent account  on Transaction 1: " + accountFirestoreId);
                if (parentAccount == null) {
                    Log.e(TAG, "CRITICAL: Parent account not found after sync: " + accountFirestoreId);
                    continue;
                }

                // بقية معالجة المعاملة
                Transaction existing = mTransactionDao.getTransactionByFirestoreId(firestoreId);
                if (existing != null && !"SYNCED".equals(existing.getSyncStatus())) {
                    Log.d("RepoSync", "Skipping incoming change for transaction " + firestoreId + " because local status is " + existing.getSyncStatus());
                    continue;
                }

                long remoteLastModified = (long) data.get("lastModified");

                try {
                    if (existing != null) {
                        if (remoteLastModified > existing.getLastModified() && !Objects.equals(existing.getSyncStatus(), "DELETED")) {
                            Log.d(TAG, "update Transaction : " + accountFirestoreId);
                            String billType = (String) data.get("billType");
                            existing.setAccountId(parentAccount.getId());
                            existing.setAmount(((Number) data.get("amount")).doubleValue());
                            existing.setDetails((String) data.get("details"));
                            existing.setCurrencyId(((Number) data.get("currencyId")).intValue());
                            existing.setType(((Long) data.get("type")).intValue());
                            existing.setAccountFirestoreId((String) data.get("accountFirestoreId"));
                            existing.setTimestamp(timestamp);
                            existing.setAccountFirestoreId(accountFirestoreId);
                            existing.setBillType(billType);
                            existing.setImportID(((Number) data.get("importID")).intValue());
                            existing.setLastModified(remoteLastModified);
                            existing.setSyncStatus("SYNCED");
                            mTransactionDao.update(existing);
//                        }
                        }
                    } else {
                        Log.e(TAG, "insert Transaction :  accountFirestoreId " + accountFirestoreId + "  Transaction firestoreId :" + firestoreId);
                        Transaction newTx = new Transaction();
                        newTx.setFirestoreId(firestoreId);
                        newTx.setAccountId(parentAccount.getId());
                        newTx.setAmount(((Number) data.get("amount")).doubleValue());
                        newTx.setDetails((String) data.get("details"));
                        newTx.setCurrencyId(((Number) data.get("currencyId")).intValue());
                        newTx.setType(((Long) data.get("type")).intValue());
                        newTx.setTimestamp(timestamp);
                        newTx.setAccountFirestoreId((String) data.get("accountFirestoreId"));

                        // التصحيح: استخدام newTx بدلاً من existing
                        newTx.setAccountFirestoreId(accountFirestoreId);

                        Object billTypeObj = data.get("billType");
                        if (billTypeObj != null) {
                            newTx.setBillType(billTypeObj.toString());
                        }

                        Object importIDObj = data.get("importID");
                        if (importIDObj != null) {
                            newTx.setImportID(((Number) importIDObj).intValue());
                        }

                        Object ownerUIDObj = data.get("ownerUID");
                        if (ownerUIDObj != null) {
                            newTx.setOwnerUID(ownerUIDObj.toString());
                        }

                        newTx.setLastModified(remoteLastModified);
                        newTx.setSyncStatus("SYNCED");
                        mTransactionDao.insert(newTx);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "batchUpsertTransactions: " + e);
                    // تسجيل التفاصيل الإضافية للخطأ
                    Log.e(TAG, "Error data: " + data);
                }
            }
        });
    }

    public void batchUpsertTransactions2(List<Map<String, Object>> changesBatch) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            for (Map<String, Object> change : changesBatch) {
                String changeType = (String) change.get("type");
                String firestoreId = (String) change.get("firestoreId");
                Map<String, Object> data = safeCastToMap(change.get("data"));

                if ("REMOVED".equals(changeType)) {
                    mTransactionDao.deleteByFirestoreId(firestoreId);
                    continue;
                }

                // تحويل التاريخ بشكل آمن
                Object timestampObj = data.get("timestamp");
                Date timestamp = null;

                if (timestampObj instanceof Timestamp) {
                    timestamp = ((Timestamp) timestampObj).toDate();
                } else if (timestampObj instanceof Date) {
                    timestamp = (Date) timestampObj;
                } else {
                    Log.w(TAG, "Unknown timestamp type: " + (timestampObj != null ? timestampObj.getClass().getSimpleName() : "null"));
                    timestamp = new Date();
                }

                // البحث عن الحساب الأب
                String accountFirestoreId = data.get("accountFirestoreId").toString().trim();
                Account parentAccount = mAccountDao.getAccountByFirestoreId(accountFirestoreId);

                Log.d(TAG, " Parent account  on Transaction 2: " + accountFirestoreId);
                if (parentAccount == null) {
                    // لا نحاول إنشاء حساب مؤقت، بل نسجل الخطأ فقط
                    Log.e(TAG, "CRITICAL: Parent account not found after sync: " + accountFirestoreId);
                    continue;
                }
                // بقية معالجة المعاملة
                Transaction existing = mTransactionDao.getTransactionByFirestoreId(firestoreId);
                if (existing != null && !"SYNCED".equals(existing.getSyncStatus())) {
                    Log.d("RepoSync", "Skipping incoming change for transaction " + firestoreId + " because local status is " + existing.getSyncStatus());
                    continue; // تجاهل التغيير القادم من السحابة
                }
                long remoteLastModified = (long) data.get("lastModified");


                try {
                    if (existing != null) {
                        if (remoteLastModified > existing.getLastModified()) {
                            Log.e(TAG, "update Transaction : " + accountFirestoreId);
                            String billType = (String) data.get("billType");
                            existing.setAccountId(parentAccount.getId());
                            existing.setAmount(((Number) data.get("amount")).doubleValue());
                            existing.setDetails((String) data.get("details"));
                            existing.setCurrencyId(((Number) data.get("currencyId")).intValue());
                            existing.setType(((Long) data.get("type")).intValue());
                            existing.setTimestamp(timestamp);
                            existing.setAccountFirestoreId(accountFirestoreId);
                            existing.setBillType((String) data.get("billType"));
                            existing.setImportID(((Number) data.get("importID")).intValue());
                            existing.setLastModified(remoteLastModified);
                            existing.setSyncStatus("SYNCED");
                            mTransactionDao.update(existing);
                        }
                    } else {
                        Log.e(TAG, "insert Transaction :  accountFirestoreId " + accountFirestoreId + "  Transaction firestoreId :" + firestoreId);
                        Transaction newTx = new Transaction();
                        newTx.setFirestoreId(firestoreId);
                        newTx.setAccountId(parentAccount.getId());
                        newTx.setAmount(((Number) data.get("amount")).doubleValue());
                        newTx.setDetails((String) data.get("details"));
                        newTx.setCurrencyId(((Number) data.get("currencyId")).intValue());
                        newTx.setType(((Long) data.get("type")).intValue());
                        newTx.setTimestamp(timestamp);
                        existing.setAccountFirestoreId(accountFirestoreId);
                        existing.setBillType((String) data.get("billType"));
                        existing.setImportID(((Number) data.get("importID")).intValue());
                        newTx.setOwnerUID((String) data.get("ownerUID"));
                        newTx.setLastModified(remoteLastModified);
                        newTx.setSyncStatus("SYNCED");
                        mTransactionDao.insert(newTx);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "batchUpsertTransactions: " + e);
                }
            }
        });
    }

    private final Map<String, Map<String, Object>> localAccounts = new HashMap<>();
    private final Map<String, Map<String, Object>> localTransactions = new HashMap<>();
    private final Map<String, Map<String, Object>> localCurrencies = new HashMap<>();
    private final Map<String, Map<String, Object>> localAccountTypes = new HashMap<>();
    private final Map<String, Map<String, Object>> localUsers = new HashMap<>();

    public Map<String, Object> getLocalData(String collection, String firestoreId) {
        Map<String, Object> localData;

        switch (collection) {
            case "accounts":
                localData = localAccounts.get(firestoreId);
                if (localData != null) {
                    // إعادة فقط name و lastModified للمقارنة
                    Map<String, Object> filtered = new HashMap<>();
                    filtered.put("name", localData.get("name"));
                    filtered.put("lastModified", localData.get("lastModified"));
                    return filtered;
                }
                break;

            case "currencies":
                localData = localCurrencies.get(firestoreId);
                if (localData != null) {
                    Map<String, Object> filtered = new HashMap<>();
                    filtered.put("name", localData.get("name"));
                    filtered.put("lastModified", localData.get("lastModified"));
                    return filtered;
                }
                break;

            case "accountTypes":
                localData = localAccountTypes.get(firestoreId);
                if (localData != null) {
                    Map<String, Object> filtered = new HashMap<>();
                    filtered.put("name", localData.get("name"));
                    filtered.put("lastModified", localData.get("lastModified"));
                    return filtered;
                }
                break;

            case "transactions":
                localData = localTransactions.get(firestoreId);
                if (localData != null) {
                    Map<String, Object> filtered = new HashMap<>();
                    filtered.put("lastModified", localData.get("lastModified"));
                    return filtered;
                }
                break;

            case "users":
                // المستخدمين: مقارنة كل الحقول
                return localUsers.get(firestoreId);

            default:
                return null;
        }

        return null;
    }

    public void batchUpsertAccounts(List<Map<String, Object>> changesBatch) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            for (Map<String, Object> change : changesBatch) {
                String changeType = (String) change.get("type");
                String firestoreId = (String) change.get("firestoreId");
                Map<String, Object> data = safeCastToMap(change.get("data"));
                String nameAcc = (String) change.get("accountName");
                Log.e(TAG, "butch account : " + nameAcc);
                if ("REMOVED".equals(changeType)) {
                    mAccountDao.deleteByFirestoreId(firestoreId);
                    Log.e(TAG, "Remove account : " + nameAcc);
                    continue;
                }
                Account existing = mAccountDao.getAccountByFirestoreId(firestoreId);
                long remoteLastModified = (long) data.get("lastModified");
                if (existing != null && !"SYNCED".equals(existing.getSyncStatus())) {
                    Log.d("RepoSync", "Skipping incoming change for account " + firestoreId + " because local status is " + existing.getSyncStatus());
                    continue; // تجاهل التغيير القادم من السحابة
                }
                if (existing != null) {
                    if (remoteLastModified > existing.getLastModified() && !Objects.equals(existing.getSyncStatus(), "DELETED")) {
                        Log.e(TAG, "Update account : " + nameAcc);
                        existing.setAccountName((String) data.get("accountName"));
                        existing.setPhoneNumber((String) data.get("phoneNumber"));
                        existing.setAccountType((String) data.get("accountType"));
                        existing.setLastModified(remoteLastModified);
                        existing.setSyncStatus("SYNCED");
                        mAccountDao.update(existing);
                    }
                } else {
                    Log.e(TAG, "Insert account : " + nameAcc);
                    Account newAccount = new Account();
                    newAccount.setFirestoreId(firestoreId);
                    newAccount.setAccountName((String) data.get("accountName"));
                    newAccount.setPhoneNumber((String) data.get("phoneNumber"));
                    newAccount.setAccountType((String) data.get("accountType"));
                    newAccount.setOwnerUID((String) data.get("ownerUID"));
                    newAccount.setLastModified(remoteLastModified);
                    newAccount.setSyncStatus("SYNCED");
                    mAccountDao.insert(newAccount);
                }
            }
        });
    }

    public void batchUpsertCurrencies(List<Map<String, Object>> changesBatch) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            for (Map<String, Object> change : changesBatch) {
                String changeType = (String) change.get("type");
                String firestoreId = (String) change.get("firestoreId");
                Map<String, Object> data = safeCastToMap(change.get("data"));

                if ("REMOVED".equals(changeType)) {
                    currencyDao.deleteByFirestoreId(firestoreId);
                    continue;
                }

                Currency existing = currencyDao.getCurrencyByFirestoreId(firestoreId);
                if (existing != null && !"SYNCED".equals(existing.getSyncStatus())) {
                    Boolean isDefaultFromFirestore = (Boolean) data.get("isDefault");
                    if (isDefaultFromFirestore != null) {
                        Log.e(TAG, "isDefaultFromFirestore: " + isDefaultFromFirestore);
                        existing.setDefault(isDefaultFromFirestore);
                    } else {
                        existing.setDefault(false); // قيمة افتراضية إذا لم توجد
                    }
                    currencyDao.update(existing);
                    continue; // تجاهل التغيير القادم من السحابة
                }
                long remoteLastModified = (long) data.get("lastModified");

                if (existing != null) {
                    if (remoteLastModified > existing.getLastModified() && !Objects.equals(existing.getSyncStatus(), "DELETED")) {
                        existing.setName((String) data.get("name"));
                        existing.setOwnerUID((String) data.get("ownerUID"));
                        existing.setLastModified(remoteLastModified);
                        existing.setSyncStatus("SYNCED");
                        Boolean isDefaultFromFirestore = (Boolean) data.get("isDefault");
                        if (isDefaultFromFirestore != null) {
                            Log.e(TAG, "isDefaultFromFirestore: " + isDefaultFromFirestore);
                            existing.setDefault(isDefaultFromFirestore);
                        } else {
                            existing.setDefault(false); // قيمة افتراضية إذا لم توجد
                        }
                        currencyDao.update(existing);
                    }
                } else {
                    Currency newCurrency = new Currency();
                    newCurrency.setFirestoreId(firestoreId);
                    newCurrency.setName((String) data.get("name"));
                    newCurrency.setOwnerUID((String) data.get("ownerUID"));
                    newCurrency.setLastModified(remoteLastModified);
                    Boolean isDefaultFromFirestore = (Boolean) data.get("isDefault");
                    if (isDefaultFromFirestore != null) {
                        Log.e(TAG, "isDefaultFromFirestore: " + isDefaultFromFirestore);
                        newCurrency.setDefault(isDefaultFromFirestore);
                    } else {
                        newCurrency.setDefault(false); // قيمة افتراضية إذا لم توجد
                    }
                    newCurrency.setSyncStatus("SYNCED");
                    currencyDao.insert(newCurrency);
                }
            }
        });
    }

    public void batchUpsertAccountTypes(List<Map<String, Object>> changesBatch) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            for (Map<String, Object> change : changesBatch) {
                String changeType = (String) change.get("type");
                String firestoreId = (String) change.get("firestoreId");
                Map<String, Object> data = safeCastToMap(change.get("data"));

                if ("REMOVED".equals(changeType)) {
                    accountTypeDao.deleteByFirestoreId(firestoreId);
                    continue;
                }

                AccountType existing = accountTypeDao.getAccountTypeByFirestoreId(firestoreId);
                if (existing != null && !"SYNCED".equals(existing.getSyncStatus())) {
                    continue; // تجاهل التغيير القادم من السحابة
                }
                long remoteLastModified = (long) data.get("lastModified");

                if (existing != null) {
                    if (remoteLastModified > existing.getLastModified() && !Objects.equals(existing.getSyncStatus(), "DELETED")) {
                        existing.setName((String) data.get("name"));
                        existing.setOwnerUID((String) data.get("ownerUID"));
                        existing.setLastModified(remoteLastModified);
                        existing.setSyncStatus("SYNCED");
                        accountTypeDao.update(existing);
                    }
                } else {
                    AccountType newType = new AccountType();
                    newType.setFirestoreId(firestoreId);
                    newType.setName((String) data.get("name"));
                    newType.setOwnerUID((String) data.get("ownerUID"));
                    newType.setLastModified(remoteLastModified);
                    newType.setSyncStatus("SYNCED");
                    accountTypeDao.insert(newType);
                }
            }
        });
    }

    public void batchUpsertUsers(List<Map<String, Object>> changesBatch) {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                for (Map<String, Object> change : changesBatch) {
                    String changeType = (String) change.get("type");
                    String firestoreId = (String) change.get("firestoreId");
                    Map<String, Object> data = safeCastToMap(change.get("data"));

//                if ("REMOVED".equals(changeType)) {
//                    mUserDao.deleteByFirestoreId(firestoreId);
//                    continue;
//                }

                    User existing = mUserDao.getAccountTypeByFirestoreEmaile(firebaseUser.getEmail());
                    long remoteLastModified = (long) data.get("lastModified");
                    int maxTransactions = (data.get("max_transactions")) != null ?
                            ((Number) data.get("max_transactions")).intValue() : 0;
                    int transactionsCount = (data.get("transactions_count")) != null ?
                            ((Number) data.get("transactions_count")).intValue() : 0;
                    int adRewards = (data.get("ad_rewards")) != null ?
                            ((Number) data.get("ad_rewards")).intValue() : 0;
                    int referralRewards = (data.get("referral_rewards")) != null ?
                            ((Number) data.get("referral_rewards")).intValue() : 0;
                    boolean isPremium = Boolean.TRUE.equals(data.get(("is_premium")));

                    String isAdmin = (String) data.get("userType");
                    new SyncPreferences(context).setKeyUserType(Objects.requireNonNullElse(isAdmin, "user"));
                    // String isAdmin = (data.get("userType")) != null ? (String) data.get("userType") : "";


//                  SecureLicenseManager.getInstance(context)
//                          .saveLicenseData(maxTransactions, transactionsCount,
//                                  adRewards, referralRewards, isPremium);
                    Log.d(TAG, "تم استيراد بيانات الترخيص من Firestore: " +
                            "transactions_count=" + transactionsCount +
                            ", ad_rewards=" + adRewards + ", referral_rewards=" + referralRewards + " isAdmin: " + isAdmin);
                    if (existing != null) {
                        if (remoteLastModified > existing.getLastModified() && !Objects.equals(existing.getSyncStatus(), "DELETED")) {
                            existing.setName((String) data.get("name"));
                            existing.setEmail((String) data.get("email"));
                            existing.setPhone((String) data.get("phone"));
                            existing.setCompany((String) data.get("company"));
                            existing.setAddress((String) data.get("addres"));
//                        existing.setCreatedAt((Date) data.get("createdAt"));
//                        existing.setOwnerUID((String) data.get("ownerUID"));
                            existing.setLastModified(remoteLastModified);
                            existing.setSyncStatus("SYNCED");
                            mUserDao.upsert(existing);
                        }
                    } else {
                        User newUser = new User();
//                    newUser.setFirestoreId(firestoreId);
                        newUser.setName((String) data.get("name"));
                        newUser.setEmail((String) data.get("email"));
                        newUser.setPhone((String) data.get("phone"));
                        newUser.setCompany((String) data.get("company"));
                        newUser.setAddress((String) data.get("addres"));
//                    newUser.setCreatedAt((Date) data.get("createdAt"));
                        newUser.setLastModified(remoteLastModified);
                        newUser.setSyncStatus("SYNCED");
                        mUserDao.upsert(newUser);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "batchUpsertUsers Error: " + e);
            }
        });
    }

    public LiveData<User> getUserProfile() {
        return mUserDao.getUserProfile();
    }

    public void updateUserProfile(User user) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            user.setSyncStatus("EDITED");
            user.setLastModified(System.currentTimeMillis());
            mUserDao.upsert(user);
        });
    }

    public LiveData<List<Currency>> getAllCurrencies11() {
        return currencyDao.getAllCurrencies();
    }

    public List<Currency> getAllCurrenciesBlocking() {
        return currencyDao.getAllCurrenciesBlocking();
    }

    public LiveData<List<AccountType>> getAllAccountTypes() {
        // افترض أن DAO لديه هذه الدالة
        return accountTypeDao.getAllAccountTypes();
    }

    public void insertAccountType(AccountType accountType) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // افترض أن DAO لديه هذه الدالة
            accountTypeDao.insert(accountType);
            triggerSync(); // تشغيل المزامنة بعد إضافة عملة جديدة
        });
    }

    public List<AccountType> getAllAccountTypesBlockingReport() {
        return accountTypeDao.getAllAccountTypesBlockingReport();
    }

    public LiveData<List<String>> getCurrenciesWithTransactions() {
        if (currentUserId == null) {
            return new MutableLiveData<>(new ArrayList<>()); // إرجاع قائمة فارغة إذا لم يكن هناك مستخدم
        }
        return mTransactionDao.getCurrenciesWithTransactions(currentUserId);

    }

    public LiveData<List<String>> getActiveCurrenciesForAccount(int accountId) {
        return mTransactionDao.getActiveCurrenciesForAccount(accountId);
    }

    public Double getAccountBalance(int accountId, String currency) {
        Double debit = mTransactionDao.getDebitForAccountBlocking(accountId, currency);
        Double credit = mTransactionDao.getCreditForAccountBlocking(accountId, currency);
        if (debit == null) debit = 0.0;
        if (credit == null) credit = 0.0;

        return debit - credit;
    }

    public Double getBalanceBeforeDate(int accountId, long startDate) {
        Double balance = mTransactionDao.getBalanceBeforeDate(accountId, startDate);
        return balance;
    }

    public List<TransactionWithAccount> getConsolidatedMovement11(String accountType, long startDate, long endDate) {
        return mTransactionDao.getConsolidatedMovement(accountType, startDate, endDate);
    }

    public List<TransactionWithAccount> getConsolidatedMovementAllCurrencies(String acTypeFirestoreId, int currency, long startDate, long endDate) {
        return mTransactionDao.getConsolidatedMovementAllCurrencies(acTypeFirestoreId, currency, startDate, endDate);
    }

    public List<MonthlySummary> getMonthlySummaryForAccount(int accountId) {
        return mTransactionDao.getMonthlySummaryForAccount(accountId);
    }

    public List<MonthlySummary> getMonthlySummaryForAccountAllCurrencies(int accountId, int currency) {
        return mTransactionDao.getMonthlySummaryForAccountAllCurrencies(accountId, currency);
    }


    public List<MonthlySummary> getMonthlySummaryByAccountTypeByCurrency(String acTypeFirestoreId, int currency) {
        return mTransactionDao.getMonthlySummaryByAccountTypeByCurrency(acTypeFirestoreId, currency);
    }


    public Double getBalanceByAccountTypeBeforeDate(String acTypeFirestoreId, long startDate) {
        return mTransactionDao.getBalanceByAccountTypeBeforeDate(acTypeFirestoreId, startDate);
    }

    public int getTransactionCountForCurrency(int currencyId) {
        return currencyDao.getTransactionCountForCurrency(currencyId);
    }

    public void updateCurrency1(Currency currencyToUpdate, String newName) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String oldName = currencyToUpdate.name;
            long timestamp = System.currentTimeMillis();
            // 1. تحديث اسم العملة في جدول العمليات
//            mTransactionDao.updateCurrencyNameInTransactions(oldName, newName, timestamp);

            // 2. تحديث كائن العملة نفسه
            currencyToUpdate.name = newName;
            currencyToUpdate.setSyncStatus("EDITED");
            currencyToUpdate.setLastModified(timestamp);
            currencyDao.update(currencyToUpdate);

            triggerSync();
        });
    }

    public void updateCurrency(Currency currencyToUpdate, String name, String symbol, String code) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String oldName = currencyToUpdate.name;
            currencyToUpdate.name = name;
            currencyToUpdate.setName(name);
            currencyToUpdate.setSymbol(symbol);
            currencyToUpdate.setCode(code);
            currencyToUpdate.setSyncStatus("EDITED");
            currencyToUpdate.setLastModified(System.currentTimeMillis());
            currencyDao.update(currencyToUpdate);

            triggerSync();
        });
    }

    public void updateCurrency(Currency currency, String name) {
        // دالة قديمة للحفاظ على التوافق
        updateCurrency(currency, name, currency.getSymbol(), currency.getCode());
    }

    public void updateCurrencyRestore(Currency currencyToUpdate) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long timestamp = System.currentTimeMillis();
            // 1. تحديث اسم العملة في جدول العمليات
//            mTransactionDao.updateCurrencyNameInTransactions(oldName, newName, timestamp);
            // 2. تحديث كائن العملة نفسه
            currencyToUpdate.setSyncStatus("EDITED");
            currencyToUpdate.setLastModified(timestamp);
            currencyDao.update(currencyToUpdate);
        });
    }

    public void deleteCurrency(Currency currency) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (currency.getFirestoreId() != null && !currency.getFirestoreId().isEmpty()) {
                DeletionLog log = new DeletionLog();
                log.setFirestoreId(currency.getFirestoreId());
                log.setCollectionName("currency");
                mDeletionLogDao.insert(log);
            }
        });
        AppDatabase.databaseWriteExecutor.execute(() -> {
            currency.setSyncStatus("DELETED");
            currency.setLastModified(System.currentTimeMillis());
            currencyDao.update(currency);
            triggerSync();
        });

    }

    // --- منطق أنواع الحسابات الجديد ---
    public int getAccountCountForType(String typeName) {
        return accountTypeDao.getAccountCountForType(typeName);
    }

    public void updateAccountType(AccountType typeToUpdate, String newName) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String oldName = typeToUpdate.name;
            long timestamp = System.currentTimeMillis();

            // 1. تحديث نوع الحساب في كل الحسابات المرتبطة
//            mAccountDao.updateAccountTypeInAccounts(oldName, newName, timestamp);
            mAccountDao.updateAccountTypeInAccounts(oldName, newName);

            // 2. تحديث كائن نوع الحساب نفسه
            typeToUpdate.name = newName;
            typeToUpdate.setSyncStatus("EDITED");
            typeToUpdate.setLastModified(timestamp);
            accountTypeDao.update(typeToUpdate);

            triggerSync();
        });
    }

    public void deleteAccountType(AccountType accountType) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (accountType.getFirestoreId() != null && !accountType.getFirestoreId().isEmpty()) {
                DeletionLog log = new DeletionLog();
                log.setFirestoreId(accountType.getFirestoreId());
                log.setCollectionName("accountType");
                mDeletionLogDao.insert(log);
            }
        });
        AppDatabase.databaseWriteExecutor.execute(() -> {
            accountType.setSyncStatus("DELETED");
            accountType.setLastModified(System.currentTimeMillis());
            accountTypeDao.update(accountType);
            triggerSync();
        });
    }

    public AccountType getAccountTypeByName(String name) {
        return accountTypeDao.getAccountTypeByName(name);
    }

    public List<Transaction> getDeletedTransactions() {
        return mTransactionDao.getDeletedTransactions();
    }

    public void deleteTransactions(Transaction transaction) {
        mTransactionDao.delete(transaction);
    }

    public List<Currency> getDeletedCurrencies() {
        return currencyDao.getDeletedCurrencies();
    }

    public void deleteCurrencys(Currency currency) {
        currencyDao.delete(currency);
    }

    public List<AccountType> getDeletedAccountTypes() {
        return accountTypeDao.getDeletedAccountTypes();
    }

    public void deleteAccountTypes(AccountType accountType) {
        accountTypeDao.delete(accountType);
    }

    public List<Account> getDeletedAccounts() {
        return mAccountDao.getDeletedAccounts();
    }

    public void deleteAccounts(Account account) {
        mAccountDao.delete(account);
    }

    public void setUserUID(String uid) {
        this.currentUserId = uid;
    }

    public int findDuplicateTransaction(int accountId, long dateWithoutTime, double amount, int type, int currency) {
        // يجب أن يتم استدعاؤها من خيط خلفي
        return mTransactionDao.findDuplicateTransaction(accountId, dateWithoutTime, amount, type, currency);
    }

    public int getMaxImportId() {
        return mTransactionDao.getMaxImportId();
    }

    public List<Transaction> getTransactionsByImportId(int importId) {
        return mTransactionDao.getTransactionsByImportId(importId);
    }

    public void insertInvoiceTransactions(List<Transaction> transactions) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.runInTransaction(() -> {
                for (Transaction tx : transactions) {
                    if (tx.getOwnerUID() == null) {
                        String accountFirestoreId = mAccountDao.getAccountFireStoreById(tx.getAccountId());
                        String currencyFirestoreId = currencyDao.getCurrencyFirestoreId(tx.getCurrencyId());
                        tx.setCurrencyFirestoreId(currencyFirestoreId);
                        tx.setAccountFirestoreId(accountFirestoreId);
                        tx.setOwnerUID(currentUserId);
                    }
                    mTransactionDao.insert(tx);
                    Log.e("transactions", tx.toString());
                }
            });
            triggerSync();
        });
    }

    public void deleteInvoiceByImportId(int importId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // نستخدم الحذف الناعم لكل عملية في الفاتورة
            List<Transaction> transactions = mTransactionDao.getTransactionsByImportId(importId);
            for (Transaction tx : transactions) {
                tx.setSyncStatus("DELETED");
                tx.setLastModified(System.currentTimeMillis());
                mTransactionDao.update(tx);
                deleteTransaction(tx);
            }
            triggerSync();
        });
    }

    public void insertNeAccount(Account account) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // جديد: إنشاء ID فريد على الجهاز قبل الحفظ
            account.setFirestoreId(UUIDGenerator.generateSequentialUUID());
            account.setSyncStatus("NEW");
            account.setLastModified(System.currentTimeMillis());
            Log.i(TAG, "insertNeAccount: " + account.getAccountName() + "\n" +
                    account.getAccountType() + "\n" +
                    account.getOwnerUID() + "\n" +
                    account.getAcTypeFirestoreId() + "\n" +
                    account.getPhoneNumber() + "\n" +
                    account.getSyncStatus() + "\n" +
                    account.getLastModified());
//            mAccountDao.insert(account);
            long newId = mAccountDao.insert(account);
            Log.i(TAG, "Account inserted with ID: " + newId);

            Account savedAccount = mAccountDao.getAccountByNameBlocking(account.getAccountName());
            if (savedAccount != null) {
                Log.i(TAG, "Account verified in database - ID: " + savedAccount.getId() + "\n" +
                        "Account verified in database - OwnerUID: " + account.getOwnerUID() + "\n" +
                        "Account verified in database - AccountType: " + account.getAccountType() + "\n" +
                        "Account verified in database - AcTypeFirestoreId: " + account.getAcTypeFirestoreId() + "\n" +
                        "Account verified in database - PhoneNumber: " + account.getPhoneNumber() + "\n" +
                        "Account verified in database - SyncStatus: " + account.getSyncStatus() + "\n" +
                        "Account verified in database - LastModified: " + account.getLastModified() + "\n" +
                        "Account verified in database - FirestoreId: " + account.getFirestoreId());
            } else {
                Log.e(TAG, "Account NOT found in database after insertion!");
            }
        });
    }

    public void debugAccounts() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<Account> allAccounts = mAccountDao.getAllAccountsForDebug();
            Log.d("DebugAccounts", "=== ALL ACCOUNTS IN DATABASE ===");
            for (Account account : allAccounts) {
                Log.d("DebugAccounts",
                        "ID: " + account.getId() +
                                ", Name: " + account.getAccountName() +
                                ", OwnerUID: " + account.getOwnerUID() +
                                ", SyncStatus: " + account.getSyncStatus() +
                                ", AccountType: " + account.getAccountType());
            }
            String guestUID = SecureLicenseManager.getInstance(context).guestUID();
            String currentUID = isGuest ? guestUID : currentUserId;
            List<Account> userAccounts = mAccountDao.getAccountsByOwnerDebug(currentUID);
            Log.d("DebugAccounts", "=== ACCOUNTS FOR CURRENT USER ===");
            Log.d("DebugAccounts", "Current UID: " + currentUID);
            for (Account account : userAccounts) {
                Log.d("DebugAccounts",
                        "ID: " + account.getId() +
                                ", Name: " + account.getAccountName() +
                                ", SyncStatus: " + account.getSyncStatus());
            }
            Log.d("DebugAccounts", "Total user accounts: " + userAccounts.size());
        });
    }

    public long insertNewAccount(Account account) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // جديد: إنشاء ID فريد على الجهاز قبل الحفظ
            account.setFirestoreId(UUIDGenerator.generateSequentialUUID());
            account.setSyncStatus("NEW");
            account.setLastModified(System.currentTimeMillis());
            Log.i(TAG, "insertNeAccount: " + account.getAccountName() + "\n" +
                    account.getAccountType() + "\n" +
                    account.getOwnerUID() + "\n" +
                    account.getAcTypeFirestoreId() + "\n" +
                    account.getPhoneNumber() + "\n" +
                    account.getSyncStatus() + "\n" +
                    account.getLastModified());
            long newId = mAccountDao.insert(account);
            Log.i(TAG, "Account inserted with ID2: " + newId);
        });
        Account savedAccount = mAccountDao.getAccountByNameBlocking(account.getAccountName());
        if (savedAccount != null) {
            Log.i(TAG, "Account verified in database - ID: " + savedAccount.getId() + "\n" +
                    "Account verified in database - OwnerUID: " + account.getOwnerUID());
        } else {
            Log.e(TAG, "Account NOT found in database after insertion!");
        }
        return 0;
    }

    public List<MonthlySummary> getGlobalMonthlySummary(int currency) {
        return mTransactionDao.getGlobalMonthlySummary(currency);
    }

    public List<AccountTotalsByCurrency> getAccountTotalsByCurrency(int accountId) {
        return mTransactionDao.getAccountTotalsByCurrency(accountId);
    }

    public LiveData<List<AccountWithBalance>> getAccountsWithBalances(int currencyId, String filterType, String searchQuery) {
        return mAccountDao.getAccountsWithBalances(currentUserId, currencyId, filterType, "%" + searchQuery + "%");
    }

    public LiveData<List<Account>> getAllAccounts() {
        return mAccountDao.getAllAccounts(currentUserId);
    }

    public String getFirstCurrency() {
        return currencyDao.getFirstCurrencyById();
    }

    public LiveData<Account> getAccountById(int accountId) {
        return mAccountDao.getAccountById(accountId);
    }

    // --- دوال العمليات ---

    public LiveData<List<Transaction>> getTransactionsForAccount(int accountId, int currencyId, String searchQuery) {
        return mTransactionDao.getTransactionsForAccount(accountId, currencyId, searchQuery);
    }

    public Integer checkIfFirst(int accountId, int currencyId) {
        return mTransactionDao.checkIfFirst(accountId, currencyId);
    }

    public LiveData<List<Transaction>> getLiveTransactionsForAccount(int accountId, int currencyId) {
        return mTransactionDao.getLiveTransactionsForAccount(accountId, currencyId);
    }

    public void insertTransaction(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String accountFirestoreId = mAccountDao.getAccountFireStoreById(transaction.getAccountId());
            transaction.setAccountFirestoreId(accountFirestoreId);
            transaction.setFirestoreId(UUIDGenerator.generateSequentialUUID());
            transaction.setSyncStatus("NEW");
            transaction.setLastModified(System.currentTimeMillis());

            String currencyFirestoreId = currencyDao.getCurrencyFirestoreId(transaction.getCurrencyId());

            if (transaction.getOwnerUID() == null) {
                transaction.setOwnerUID(currentUserId);
            }
            transaction.setCurrencyFirestoreId(currencyFirestoreId);
//            Log.d(TAG, "Inserted transaction : " + transaction.getOwnerUID()+"\n"+
//                    transaction.getDetails()+"\n" + transaction.getBillType()+"\n"+
//                    transaction.getType()+"\n" + transaction.getTimestamp()+"\n"+
//                    transaction.getCurrencyId()+"\n" + transaction.getAmount()+"\n"+
//                    transaction.getAccountId()+"\n" + transaction.getFirestoreId()+"\n"+
//                    transaction.getAccountFirestoreId()+"\n");
            mTransactionDao.insert(transaction);
            triggerSync();
        });
    }

    public void insertTransactionInvoice(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {

            String accountFirestoreId = mAccountDao.getAccountFireStoreById(transaction.getAccountId());
            transaction.setAccountFirestoreId(accountFirestoreId);
            transaction.setFirestoreId(UUIDGenerator.generateSequentialUUID());
            transaction.setSyncStatus("NEW");
            transaction.setLastModified(System.currentTimeMillis());
            if (transaction.getOwnerUID() == null) {
                transaction.setOwnerUID(currentUserId);
            }
            mTransactionDao.insert(transaction);

        });
    }

    public void updateTransaction(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Transaction currentTransaction = mTransactionDao.getTransactionByIdBlocking(transaction.getId());
            if (currentTransaction == null) return;
            String currencyFirestoreId = currencyDao.getCurrencyFirestoreId(transaction.getCurrencyId());
            mTransactionDao.updateUserChanges(
                    transaction.getId(),
                    transaction.getAmount(),
                    transaction.getDetails(),
                    transaction.getCurrencyId(), // <-- تم التحديث هنا
                    transaction.getType(),
                    transaction.getTimestamp().getTime(),
                    System.currentTimeMillis(), currencyFirestoreId
            );
            triggerSync();
        });
    }

    public void updateInvoice(Transaction transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Transaction currentTransaction = mTransactionDao.getTransactionByFirestoreId(transaction.getFirestoreId());
            if (currentTransaction == null) return;

            String currencyFirestoreId = currencyDao.getCurrencyFirestoreId(transaction.getCurrencyId());
            mTransactionDao.updateUserChanges(
                    transaction.getId(),
                    transaction.getAmount(),
                    transaction.getDetails(),
                    transaction.getCurrencyId(), // <-- تم التحديث هنا
                    transaction.getType(),
                    transaction.getTimestamp().getTime(),
                    System.currentTimeMillis(),
                    currencyFirestoreId
            );

        });
    }

    public void updateInvoiceTransactions(List<Transaction> transactions) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.runInTransaction(() -> {
                for (Transaction tx : transactions) {
                    if (tx.getOwnerUID() == null) {
                        tx.setOwnerUID(currentUserId);
                    }

                    String currencyFirestoreId = currencyDao.getCurrencyFirestoreId(tx.getCurrencyId());
                    tx.setCurrencyFirestoreId(currencyFirestoreId);
                    if (Objects.equals(tx.getSyncStatus(), "NEW")) {
//                        tx.setLastModified(System.currentTimeMillis());
                        mTransactionDao.insert(tx);
                    } else if (Objects.equals(tx.getSyncStatus(), "EDITED")) {
                        updateInvoice(tx);
                    } else {
                        deleteTransaction(tx);
                    }
                    Log.e("transactions", tx.toString());
                }
            });
            triggerSync();
        });
    }

    public List<AccountBalanceSummary> getAccountBalancesByType11(String accountType, int currencyId, long startDate, long endDate) {
        return mAccountDao.getAccountBalancesByType(accountType, currencyId, startDate, endDate);
    }


    public List<AccountBalanceSummary> getAccountBalances(int accountId, int currencyId, long startDate, long endDate) {
        return mAccountDao.getAccountBalances(accountId, currencyId, startDate, endDate);
    }


    public List<Transaction> getTransactionsForAccountBlocking(int accountId, int currencyId) {
        return mTransactionDao.getTransactionsForAccountBlocking(accountId, currencyId);
    }

    public Currency getCurrencyByName(String name) {
        return currencyDao.getCurrencyByName(name);
    }

    public LiveData<List<Currency>> getAllCurrencies() {
        return currencyDao.getAllCurrencies();
    }

    public void insertCurrency(Currency currency) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            currencyDao.insert(currency);
            triggerSync();
        });
    }

    public String getCurrencyNameById(int currencyId) {
        return currencyDao.getCurrencyNameById(currencyId);
    }

    public boolean hasTransactionsForCurrencyAndFilter(int currencyId, String filter) {
        try {
            return AppDatabase.databaseWriteExecutor.submit(() -> {
                return mAccountDao.hasTransactionsForCurrencyAndFilter(currencyId, filter) > 0;
            }).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public String getCurrencyFirestoreId(int currencyId) {
        return currencyDao.getCurrencyFirestoreId(currencyId);
    }

    public List<AccountBalanceSummary> getAccountBalancesByType(String acTypeFirestoreId, int currencyId, long startDate, long endDate) {
        return mAccountDao.getAccountBalancesByType(acTypeFirestoreId, currencyId, startDate, endDate);
    }

    public List<AccountBalanceSummary> getAccountBalancesBynameType(String acType, int currencyId, long startDate, long endDate) {
        return mAccountDao.getAccountBalancesBynameType(acType, currencyId, startDate, endDate);
    }

    public String getAccountTypeNameByFirestoreId(String firestoreId) {
        return accountTypeDao.getAccountTypeNameByFirestoreId(firestoreId);
    }

    public List<TransactionWithAccount> getConsolidatedMovement(String acTypeFirestoreId, long startDate, long endDate) {
        // ملاحظة: يجب التأكد من أن استعلام getConsolidatedMovement في TransactionDao
        // تم تحديثه ليعتمد على acTypeFirestoreId أيضاً إذا كان ذلك ضرورياً.
        // سأفترض حالياً أن التجميع يتم على مستوى الحسابات التي تم تصفيتها بالفعل.
        return mTransactionDao.getConsolidatedMovement(acTypeFirestoreId, startDate, endDate);
    }

    /**
     * ✅ الحصول على حسابات مع الأرصدة مع تحديث قسري
     */
    public LiveData<List<AccountWithBalance>> getAccountsWithBalancesForceRefresh(int currencyId, String filterType, String searchQuery) {
        return mAccountDao.getAccountsWithBalances(currentUserId, currencyId, filterType, "%" + searchQuery + "%");
    }

    /**
     * ✅ الحصول على عدد المعاملات الحديثة
     */
    public int getRecentTransactionsCount() {
        return mTransactionDao.getTransactionsCount();
    }

    /**
     * ✅ الحصول على عدد الحسابات
     */
    public int getAccountsCount() {
        return mAccountDao.getAccountsCount();
    }
    public List<Account> getAccounts() {
        Log.d("MainViewModel", "استدعاء getAccounts()");
        return mAccountDao.getAllAccounts();
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> safeCastToMap(Object obj) {
        if (obj instanceof Map) {
            try {
                return (Map<String, Object>) obj;
            } catch (ClassCastException e) {
                return Collections.emptyMap();
            }
        }
        return Collections.emptyMap();
    }

}