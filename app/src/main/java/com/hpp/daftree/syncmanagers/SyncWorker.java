
package com.hpp.daftree.syncmanagers;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;
import com.hpp.daftree.UUIDGenerator;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.database.AppDatabase;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountDao;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.AccountTypeDao;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.CurrencyDao;
import com.hpp.daftree.database.DeletionLog;
import com.hpp.daftree.database.DeletionLogDao;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.database.TransactionDao;
import com.hpp.daftree.database.User;
import com.hpp.daftree.database.UserDao;
import com.hpp.daftree.notifications.NotificationHelper;
import com.hpp.daftree.utils.SecureLicenseManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SyncWorker extends Worker {
    private static final String TAG = "SyncWorker";
    private final AccountDao accountDao;
    private final TransactionDao transactionDao;
    private final DeletionLogDao deletionLogDao;
    private final UserDao userDao;
    private final CurrencyDao currencyDao;
    private final AccountTypeDao accountTypeDao;

    private final DaftreeRepository repository;
    private final FirebaseFirestore firestore;
  private  boolean isGuest = false;
    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        AppDatabase db = AppDatabase.getDatabase(context.getApplicationContext());
        this.accountDao = db.accountDao();
        this.transactionDao = db.transactionDao();
        this.deletionLogDao = db.deletionLogDao();
        this.userDao = db.userDao();
        this.currencyDao = db.currencyDao();
        this.accountTypeDao = db.accountTypeDao();
        this.repository = new DaftreeRepository((Application) context.getApplicationContext());
        this.firestore = FirebaseFirestore.getInstance();
        isGuest = SecureLicenseManager.getInstance(context).isGuest();
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Sync process started.");
        String uid = FirebaseAuth.getInstance().getUid();
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("MigrationPrefs", Context.MODE_PRIVATE);

        boolean migrationNeeded = prefs.getBoolean("firestore_migration_needed", false);

        try {

            if(isGuest)  return Result.success();
            // 1. مزامنة بيانات الترخيص أولاً
//            LicenseSyncManager.SyncResult licenseResult = Tasks.await(
//                    licenseSyncManager.smartSync().toCompletableFuture()
//            );
//            LicenseSyncManager.SyncResult licenseResult = licenseSyncManager.smartSync().get();
//            if (!licenseResult.isSuccess()) {
//                Log.e(TAG, "فشل في مزامنة الترخيص: " + licenseResult.getMessage());
//                return Result.retry();
//            }

            // 2. مزامنة العمليات المنتظرة
//            TransactionSyncManager.TransactionSyncResult transactionResult = Tasks.await(
//                    transactionSyncManager.syncPendingTransactions().toCompletableFuture()
//            );
//            TransactionSyncManager.TransactionSyncResult transactionResult =
//                    transactionSyncManager.syncPendingTransactions().get();
//            if (transactionResult.getRemainingCount() > 0) {
//                Log.w(TAG, "بقي " + transactionResult.getRemainingCount() + " عملية لم تتم مزامنتها بسبب عدم كفاية الرصيد");
//            }
            if (migrationNeeded) {
                Log.i(TAG, "Firestore migration required. Starting one-time cloud data migration.");
//                migrateFirestoreData();
                prefs.edit().putBoolean("firestore_migration_needed", false).apply();
                Log.i(TAG, "Firestore migration completed and flag cleared.");
            }
//            checkAndFixMissingAccountNumbers(); // ✅ فحص الحسابات القديمة قبل أي مزامنة
//uploadUserProfile


            Completable.fromAction(this::uploadAccounts)
                    .andThen(Completable.fromAction(this::handleDeletions))
                    .andThen(Completable.fromAction(this::uploadCurrencies))
                    .andThen(Completable.fromAction(this::uploadTransactions))
                    .andThen(Completable.fromAction(this::uploadAccountTypes))
                    .andThen(Completable.fromAction(this::uploadUserProfile))
                    .andThen(Completable.fromAction(this::checkDelete))
                    .subscribeOn(Schedulers.io())
                    .blockingAwait(); // الانتظار حتى تنتهي جميع العمليات
            // تحديث وقت آخر مزامنة
            SecureLicenseManager.getInstance(getApplicationContext()).setLastSyncTime(System.currentTimeMillis());
//            SecureLicenseManager secureLicenseManager = new SecureLicenseManager(getApplicationContext());
//            secureLicenseManager.setLastSyncTime(System.currentTimeMillis());
            Log.d(TAG, "Sync process finished successfully.");
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Sync failed, will retry.", e);
            if (e instanceof ExecutionException && e.getCause() instanceof com.google.firebase.firestore.FirebaseFirestoreException) {
                com.google.firebase.firestore.FirebaseFirestoreException ffe = (com.google.firebase.firestore.FirebaseFirestoreException) e.getCause();
                if (ffe.getCode() == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Log.e(TAG, "Sync failed due to permission denied. Will not retry.");
                    return Result.failure(); // لا فائدة من إعادة المحاولة تلقائيًا إذا كانت صلاحيات مفقودة
                }
            }
            return Result.retry(); // إعادة المحاولة في الحالات الأخرى


        }
    }

    private void migrateFirestoreData() throws Exception {
        // الخطوة أ: رفع كل العمليات المحلية (التي تم تحويلها بالفعل)
        // هذا سيضيف حقل currencyId إلى مستندات Firestore
        uploadTransactions();

        // الخطوة ب: تنظيف الحقل القديم
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        List<DocumentSnapshot> oldTransactions = Tasks.await(
                firestore.collection("transactions")
                        .whereEqualTo("ownerUID", uid)
                        .whereNotEqualTo("currency", null) // جلب المستندات التي لا يزال بها الحقل القديم
                        .get()
        ).getDocuments();

        if (oldTransactions.isEmpty()) {
            Log.i(TAG, "No old transaction fields to clean up in Firestore.");
            return;
        }

        Log.i(TAG, "Found " + oldTransactions.size() + " transactions with old 'currency' field. Cleaning up...");
        WriteBatch batch = firestore.batch();
        for (DocumentSnapshot doc : oldTransactions) {
            batch.update(doc.getReference(), "currency", FieldValue.delete());
        }
        Tasks.await(batch.commit());
        Log.i(TAG, "Successfully cleaned up old 'currency' field from Firestore documents.");
    }
    // --- دوال مساعدة جديدة لإنشاء بيانات نظيفة ---

    /**
     * يحول كائن Account (Entity) إلى Map نظيفة لإرسالها لـ Firestore.
     * يستثني الحقول المحلية مثل id, syncStatus, firestoreId.
     */
    private Map<String, Object> getAccountMap(Account account) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", account.getId());
        map.put("ownerUID", account.getOwnerUID());
        map.put("accountName", account.getAccountName());
        map.put("phoneNumber", account.getPhoneNumber());
        map.put("accountType", account.getAccountType());
        map.put("lastModified", account.getLastModified());
        map.put("acTypeFirestoreId", account.getAcTypeFirestoreId());
        // لا نرسل id, firestoreId, syncStatus
        return map;
    }

    /**
     * يحول كائن Transaction (Entity) إلى Map نظيفة لإرسالها لـ Firestore.
     */
    private Map<String, Object> getTransactionMap(Transaction transaction) {
        Map<String, Object> map = new HashMap<>();
        map.put("ownerUID", transaction.getOwnerUID());
        map.put("accountFirestoreId", transaction.getAccountFirestoreId());
        map.put("accountId", transaction.getAccountId()); // ID المحلي للحساب
        map.put("amount", transaction.getAmount());
        map.put("currencyId", transaction.getCurrencyId());
        map.put("details", transaction.getDetails());
        map.put("importID", transaction.getImportID());
        map.put("timestamp", transaction.getTimestamp());
        map.put("type", transaction.getType());
        map.put("lastModified", transaction.getLastModified());
        // لا نرسل id, firestoreId, syncStatus
        return map;
    }
    // --- دوال المزامنة المعدلة ---
   private void handleDeletions1() throws Exception {
        List<DeletionLog> pendingDeletions = deletionLogDao.getAllPendingDeletions();
        Log.d(TAG, "Found " + pendingDeletions.size() + " pending deletions.");
        for (DeletionLog log : pendingDeletions) {
            // 1. إنشاء مرجع للمستند
            com.google.firebase.firestore.DocumentReference docRef = firestore.collection(log.getCollectionName()).document(log.getFirestoreId());

            // 2. التحقق من وجود المستند أولاً
            DocumentSnapshot snapshot = Tasks.await(docRef.get());
            if (snapshot.exists()) {
                // 3. إذا كان موجودًا، قم بحذفه
                Tasks.await(docRef.delete());
                Log.d(TAG, "Successfully deleted document from Firestore: " + log.getFirestoreId());
            } else {
                // 4. إذا لم يكن موجودًا (تم حذفه من جهاز آخر)، فقط سجل ذلك
                Log.d(TAG, "Document not found in Firestore (already deleted?), cleaning up local log for: " + log.getFirestoreId());
            }
            // 5. في كلتا الحالتين، قم بتنظيف السجل المحلي لأن المهمة قد انتهت
            deletionLogDao.deleteByFirestoreId(log.getFirestoreId());
        }
    }
    private void uploadUserProfile() throws ExecutionException, InterruptedException {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        // 1. جلب البيانات المحلية
        User localUser = userDao.getUserProfileBlocking();
        if (localUser == null) return;

        DocumentReference userDocRef = firestore.collection("users").document(userId);
        DocumentSnapshot remoteUserDoc = Tasks.await(userDocRef.get());

        if (remoteUserDoc.exists()) {
            // --- المستخدم موجود على السحابة، قم بالمقارنة والمزامنة ---
            User remoteUser = remoteUserDoc.toObject(User.class);
            if (remoteUser == null) return;

            Map<String, Object> updates = new HashMap<>();
            updates.put("company", localUser.getCompany());
            updates.put("phone", localUser.getPhone());
            updates.put("name", localUser.getName());
            updates.put("address", localUser.getAddress());
            // مقارنة عداد العمليات: دائماً نأخذ القيمة الأكبر (الأحدث)
            if (localUser.getTransactions_count() > remoteUser.getTransactions_count()) {
                updates.put("transactions_count", localUser.getTransactions_count());
            } else {
                localUser.setTransactions_count(remoteUser.getTransactions_count());
            }

            // مقارنة إجمالي العمليات المتاحة: دائماً نأخذ القيمة الأكبر
            if (localUser.getMax_transactions() > remoteUser.getMax_transactions()) {
                updates.put("max_transactions", localUser.getMax_transactions());
            } else {
                localUser.setMax_transactions(remoteUser.getMax_transactions());
            }

            // مقارنة مكافآت الإعلانات
            if (localUser.getAd_rewards() > remoteUser.getAd_rewards()) {
                updates.put("ad_rewards", localUser.getAd_rewards());
            } else {
                localUser.setAd_rewards(remoteUser.getAd_rewards());
            }

            // مقارنة مكافآت الدعوة
            if (localUser.getReferral_rewards() > remoteUser.getReferral_rewards()) {
                updates.put("referral_rewards", localUser.getReferral_rewards());
            } else {
                localUser.setReferral_rewards(remoteUser.getReferral_rewards());
            }


            // إذا كان هناك تحديثات لرفعها، قم برفعها
            if (!updates.isEmpty()) {
                updates.put("lastModified", System.currentTimeMillis()); // تحديث وقت آخر تعديل
                Tasks.await(userDocRef.set(updates, SetOptions.merge()));
                Log.d(TAG, "Synced user data TO Firestore.");
            }

            // تحديث البيانات المحلية بالقيم النهائية بعد المقارنة
            localUser.setSyncStatus("SYNCED");
            userDao.upsert(localUser);
            Log.d(TAG, "Synced user data FROM Firestore to local DB.");

        } else {
            // --- المستخدم غير موجود على السحابة (أول مزامنة له) ---
            localUser.setSyncStatus("SYNCED"); // نعتبره متزامن الآن
            Tasks.await(userDocRef.set(localUser));
            userDao.upsert(localUser);
            Log.d(TAG, "Uploaded new user profile to Firestore.");
        }
    }
    private void handleDeletions() {
        try {
            List<DeletionLog> deletions = deletionLogDao.getAllPendingDeletions();
            FirebaseFirestore firestore = FirebaseFirestore.getInstance();

            for (DeletionLog deletion : deletions) {
                String collection = deletion.getCollectionName();
                String id = deletion.getFirestoreId();

                Log.d(TAG, "Deleting from Firestore: " + collection + "/" + id);

                try {
                    Tasks.await(firestore.collection(collection).document(id).delete());
//                    repository.deleteDeletionLog(deletion); // فقط إذا نجحت
                    deletionLogDao.deleteByFirestoreId(deletion.getFirestoreId());
                } catch (Exception e) {
                    if (e.getCause() instanceof FirebaseFirestoreException) {
                        FirebaseFirestoreException ffe = (FirebaseFirestoreException) e.getCause();
                        if (ffe.getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            deletionLogDao.deleteByFirestoreId(deletion.getFirestoreId());
                            Log.e(TAG, "Skipping deletion due to permission denied: " + collection + "/" + id);
                            continue; // تجاوز هذا العنصر فقط
                        }
                    }
                    Log.e(TAG, "Error deleting document: " + collection + "/" + id, e);
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "Fatal error in handleDeletions()", ex);
            // لا نرمي الاستثناء للخارج → نحمي blockingAwait من الفشل
        }
    }

    private void uploadUserProfile1() throws Exception {
        User user = userDao.getUserProfileBlocking();
        if (user != null && "EDITED".equals(user.getSyncStatus())) {
            String userId = FirebaseAuth.getInstance().getUid();
            if (userId != null) {
                Tasks.await(firestore.collection("users").document(userId).set(user));
                user.setSyncStatus("SYNCED");
                userDao.upsert(user);
            }
        }
    }
    private void uploadUserProfile2() throws Exception {
        User user = userDao.getUserProfileBlocking();
        if (user != null && "EDITED".equals(user.getSyncStatus())) {
            String userId = FirebaseAuth.getInstance().getUid();
            if (userId != null) {
                // تحويل كائن المستخدم إلى Map لإرساله
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("name", user.getName());
                userMap.put("email", user.getEmail());
                userMap.put("company", user.getCompany()); // <-- إضافة حقل الشركة
                userMap.put("address", user.getAddress());
                userMap.put("phone", user.getPhone());
                userMap.put("lastModified", user.getLastModified());

                // TODO: تجهيز لرفع الصورة مستقبلاً
                // في المستقبل، سيتم رفع الصورة إلى Firebase Storage هنا
                // وبعد الحصول على رابط التحميل، يتم إضافته إلى userMap
                // مثال:
                // if (user.getProfileImageUri() != null) {
                //     String imageUrl = await uploadImageToStorage(Uri.parse(user.getProfileImageUri()));
                //     userMap.put("profileImageUrl", imageUrl);
                // }

                Tasks.await(firestore.collection("users").document(userId).set(userMap, SetOptions.merge()));

                // تحديث الحالة المحلية بعد النجاح
                user.setSyncStatus("SYNCED");
                userDao.upsert(user);
                Log.d(TAG, "User profile synced successfully.");
            }
        }
    }
    private void uploadAccounts() throws Exception {
        List<Account> unsynced = accountDao.getUnsyncedAccounts();
        for (Account account : unsynced) {
            Log.d(TAG, "uploadAccounts To Firestore: " + account.getAccountName());
            // تأكد من وجود ownerUID
            if (account.getOwnerUID() == null) {
                Log.e(TAG, "Account missing ownerUID, skipping sync. ID: " + account.getId());
                continue;
            }

            Map<String, Object> accountData = getAccountMap(account);
            String firestoreId = account.getFirestoreId();

            // إنشاء مستند جديد إذا لزم الأمر
            if (firestoreId == null || firestoreId.isEmpty()) {
                firestoreId = firestore.collection("accounts").document().getId();
                account.setFirestoreId(firestoreId);
            }

            // إضافة ownerUID إلى البيانات
            accountData.put("ownerUID", account.getOwnerUID());
            // استخدام set() مع دمج البيانات
            Tasks.await(firestore.collection("accounts").document(firestoreId)
                    .set(accountData, SetOptions.merge()));

            // تحديث الحالة المحلية
            accountDao.updateSyncStatus(account.getId(), "SYNCED", System.currentTimeMillis());
        }
    }

    private void uploadTransactions1() throws Exception {
        List<Transaction> unsynced = transactionDao.getUnsyncedTransactions();
        for (Transaction transaction : unsynced) {
            // تأكد من وجود ownerUID
            if (transaction.getOwnerUID() == null) {
                Log.e(TAG, "Transaction missing ownerUID, skipping sync. ID: " + transaction.getId());
                continue;
            }

            // تأكد من وجود الحساب الأب
            Account parentAccount = accountDao.getAccountByIdBlocking(transaction.getAccountId());
            if (parentAccount == null || parentAccount.getFirestoreId() == null) {
                Log.w(TAG, "Parent account not synced, skipping transaction. Account ID: " + transaction.getAccountId());
                continue;
            }

            Map<String, Object> txData = getTransactionMap(transaction);
            String firestoreId = transaction.getFirestoreId();

            // إنشاء مستند جديد إذا لزم الأمر
            if (firestoreId == null || firestoreId.isEmpty()) {
                firestoreId = firestore.collection("transactions").document().getId();
                transaction.setFirestoreId(firestoreId);
            }

            // إضافة الحقول المطلوبة للقواعد الأمنية
            txData.put("accountFirestoreId", parentAccount.getFirestoreId());
            txData.put("ownerUID", transaction.getOwnerUID());
            txData.put("billType", transaction.getBillType());
            // استخدام set() مع دمج البيانات
            Tasks.await(firestore.collection("transactions").document(firestoreId)
                    .set(txData, SetOptions.merge()));

            // تحديث الحالة المحلية
            transactionDao.updateSyncStatus(transaction.getId(), "SYNCED", System.currentTimeMillis());
        }
    }

    private void uploadCurrencies() throws Exception {
        List<Currency> unsynced = currencyDao.getUnsyncedCurrencies(); // افترض وجود هذه الدالة
        for (Currency currency : unsynced) {
            Map<String, Object> currencyData = new HashMap<>();
            currencyData.put("id", currency.id);
            currencyData.put("name", currency.name);
            currencyData.put("ownerUID", currency.getOwnerUID());
            currencyData.put("lastModified", currency.getLastModified());
            currencyData.put("firestoreId", currency.getFirestoreId());
            currencyData.put("isDefault()", currency.isDefault());
            Tasks.await(firestore.collection("currencies").document(currency.getFirestoreId())
                    .set(currencyData, SetOptions.merge()));
            // تحديث الحالة المحلية
            currencyDao.updateSyncStatus(currency.id, currency.getFirestoreId(), "SYNCED", currency.getLastModified());
        }
    }
    private void uploadUserProfile11() throws Exception {
//       try {
//           User user = userDao.getUserProfileBlocking();
//           if (user != null && "EDITED".equals(user.getSyncStatus())) {
//               String userId = FirebaseAuth.getInstance().getUid();
//               if (userId != null) {
//                   Map<String, Object> userMap = getUserMap(user);
//                   Tasks.await(firestore.collection("users").document(userId).set(userMap, SetOptions.merge()));
//                   user.setSyncStatus("SYNCED");
//                   userDao.upsert(user);
//                   Log.d(TAG, "User profile synced.");
//               }
//           }
//       }catch (Exception e){
//           Log.e(TAG, "Error uploading user profile", e);
//       }
    }
    private Map<String, Object> getUserMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", user.getName());
        map.put("email", user.getEmail());
        map.put("company", user.getCompany());
        map.put("address", user.getAddress());
        map.put("phone", user.getPhone());
        map.put("lastModified", user.getLastModified());
        return map;
    }
    private void uploadAccountTypes() throws Exception {
        List<AccountType> unsynced = accountTypeDao.getUnsyncedAccountTypes();
        for (AccountType accountType : unsynced) {
            Map<String, Object> accountTypeData = getAccountTypeMap(accountType);
            Tasks.await(firestore.collection("accountTypes").document(accountType.getFirestoreId()).set(accountTypeData, SetOptions.merge()));
            accountTypeDao.updateSyncStatus(accountType.id, accountType.getFirestoreId(), "SYNCED", accountType.getLastModified());
        }
        if (!unsynced.isEmpty()) Log.d(TAG, "Synced " + unsynced.size() + " account types.");
    }
    private Map<String, Object> getCurrencyMap(Currency currency) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", currency.id);
        map.put("name", currency.name);
        map.put("ownerUID", currency.getOwnerUID());
        map.put("lastModified", currency.getLastModified());
        return map;
    }

    // **دالة مساعدة جديدة**
    private Map<String, Object> getAccountTypeMap(AccountType accountType) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", accountType.name);
        map.put("ownerUID", accountType.getOwnerUID());
        map.put("lastModified", accountType.getLastModified());
        return map;
    }
    private void uploadAccountTypes1() throws Exception {
//        List<AccountType> unsynced = accountTypeDao.getUnsyncedAccountTypes(); // افترض وجود هذه الدالة
//        for (AccountType accountType : unsynced) {
//            // منطق الرفع مشابه لمنطق رفع الحسابات
//            Map<String, Object> accountTypeData = new HashMap<>();
//            accountTypeData.put("name", accountType.name);
//            accountTypeData.put("ownerUID", accountType.getOwnerUID());
//            accountTypeData.put("lastModified", accountType.getLastModified());
//
//            Tasks.await(firestore.collection("accountTypes").document(accountType.getFirestoreId())
//                    .set(accountTypeData, SetOptions.merge()));
//
//            // تحديث الحالة المحلية
////            accountTypeDao.updateSyncStatus(accountType.id, accountType.getFirestoreId(), "SYNCED", System.currentTimeMillis());
//            accountTypeDao.updateSyncStatus(accountType.id, accountType.getFirestoreId(), "SYNCED", accountType.getLastModified());
//        }
    }

    private int getCurrentAppVersion(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 1;
        }
    }

    private void checkAndFixMissingAccountNumbers() {
        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        int savedVersion = prefs.getInt("last_version_checked", -1);
        int currentVersion = getCurrentAppVersion(context);
        if (currentVersion <= savedVersion) {
            Log.d(TAG, "No new version detected. Skipping accountNumber patch.");
            return;
        }

        Log.d(TAG, "New app version detected: " + currentVersion + ". Starting patching.");

        try {
            List<Account> localAccounts = accountDao.getAll();
            Map<String, Integer> localIdMap = new HashMap<>();
            for (Account acc : localAccounts) {
                if (acc.getFirestoreId() != null && !acc.getFirestoreId().isEmpty()) {
                    localIdMap.put(acc.getFirestoreId(), acc.getId());
                }
            }

            List<DocumentSnapshot> remoteAccounts = Tasks.await(
                    firestore.collection("accounts").get()
            ).getDocuments();

            for (DocumentSnapshot doc : remoteAccounts) {
                if (!doc.contains("accountNumber")) {
                    String docId = doc.getId();
                    if (localIdMap.containsKey(docId)) {
                        int accountNumber = localIdMap.get(docId);
                        Log.d(TAG, "Patching account " + docId + " with accountNumber: " + accountNumber);
                        Tasks.await(doc.getReference().update("accountNumber", accountNumber));
                    } else {
                        Log.w(TAG, "No matching local account for remote doc: " + docId);
                    }
                }
            }

            prefs.edit().putInt("last_version_checked", currentVersion).apply();
            Log.d(TAG, "Finished patching missing accountNumbers.");

        } catch (Exception e) {
            Log.e(TAG, "Error during patching missing accountNumbers", e);
        }
    }
    private void checkDelete() {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        try {
            // 1. حذف المعاملات (transactions)
            List<Transaction> deletedTransactions = repository.getDeletedTransactions();
            for (Transaction tx : deletedTransactions) {
                if (tx.getFirestoreId() != null && !tx.getFirestoreId().isEmpty()) {
                    DocumentSnapshot snapshot = Tasks.await(firestore.collection("transactions").document(tx.getFirestoreId()).get());
                    if (snapshot.exists()) {
                        Tasks.await(firestore.collection("transactions").document(tx.getFirestoreId()).delete());
                    }
                }
                repository.deleteTransactions(tx); // حذف من Room
            }

            // 2. حذف العملات (currencies)
            List<Currency> deletedCurrencies = repository.getDeletedCurrencies();
            for (Currency currency : deletedCurrencies) {
                if (currency.getFirestoreId() != null && !currency.getFirestoreId().isEmpty()) {
                    DocumentSnapshot snapshot = Tasks.await(firestore.collection("currencies").document(currency.getFirestoreId()).get());
                    if (snapshot.exists()) {
                        Tasks.await(firestore.collection("currencies").document(currency.getFirestoreId()).delete());
                    }
                }
                repository.deleteCurrencys(currency);
            }

            // 3. حذف أنواع الحسابات (accountTypes)
            List<AccountType> deletedAccountTypes = repository.getDeletedAccountTypes();
            for (AccountType type : deletedAccountTypes) {
                if (type.getFirestoreId() != null && !type.getFirestoreId().isEmpty()) {
                    DocumentSnapshot snapshot = Tasks.await(firestore.collection("accountTypes").document(type.getFirestoreId()).get());
                    if (snapshot.exists()) {
                        Tasks.await(firestore.collection("accountTypes").document(type.getFirestoreId()).delete());
                    }
                }
                repository.deleteAccountTypes(type);
            }

            // 4. حذف الحسابات (accounts)
            List<Account> deletedAccounts = repository.getDeletedAccounts();
            for (Account acc : deletedAccounts) {
                if (acc.getFirestoreId() != null && !acc.getFirestoreId().isEmpty()) {
                    DocumentSnapshot snapshot = Tasks.await(firestore.collection("accounts").document(acc.getFirestoreId()).get());
                    if (snapshot.exists()) {
                        Tasks.await(firestore.collection("accounts").document(acc.getFirestoreId()).delete());
                    }
                }
                repository.deleteAccounts(acc);
            }

            Log.d(TAG, "checkDelete completed.");

        } catch (Exception e) {
            Log.e(TAG, "Error during checkDelete", e);
        }
    }
    private void uploadTransactions2() throws Exception {
        List<Transaction> unsynced = transactionDao.getUnsyncedTransactions(); // دالة موجودة لديك
        for (Transaction transaction : unsynced) {
            try {
                Log.e(TAG, "Processing transaction: " + transaction.getId() +"\n"+
                        ",FirestoreID: " + transaction.getFirestoreId() +"\n"+
                        ",OwnerUID: " + transaction.getOwnerUID() +"\n"+
                        " ,account: " + transaction.getAccountId() +"\n"+
                        " ,billType: " + transaction.getBillType()+"\n"+
                        ",accountFirestoreId: " + transaction.getAccountFirestoreId() +"\n"+
                        "  ,currencyId: " + transaction.getCurrencyId() +"\n"+
                         " ,amount: " + transaction.getAmount()+"\n"+
                        ", details: " + transaction.getDetails() +"\n"+
                        " , importID: " + transaction.getImportID() +"\n"+
                        " .lastModified: " + transaction.getLastModified() +"\n"+
                        " , timestamp: " + transaction.getTimestamp() +"\n"+
                        " ,type: " + transaction.getType() );

                if (transaction.getOwnerUID().isEmpty()) {
                    Log.e(TAG, "Transaction missing ownerUID, skipping: " + transaction.getId());
                    continue;
                }

                // تأكد من أن الحساب الأب له firestoreId
                Account parentAccount = accountDao.getAccountByIdBlocking(transaction.getAccountId());
                if (parentAccount == null || parentAccount.getFirestoreId() == null || parentAccount.getFirestoreId().isEmpty()) {
                    Log.w(TAG, "Parent account not synced, skipping transaction: " + transaction.getId());
                    continue;
                }

                // تجهيز بيانات للإرسال
                Map<String, Object> txData = getTransactionMap(transaction);
                // تأكد أن الحساب الأب معرف في الحقل accountFirestoreId
                txData.put("accountFirestoreId", parentAccount.getFirestoreId());
                txData.put("ownerUID", transaction.getOwnerUID());
                txData.put("importID", transaction.getImportID());
                // إذا كانت العملية جاءت من الترقية (EDITED) أو قيمتها تتطلب إضافة currencyId في فايرستور:
                if (transaction.getCurrencyId() > 0) {
                    // نرسل رقم العملة المحلي (كما اتفقنا أن نطابقه بين Local و Firestore خلال المراحل)
                    txData.put("currencyId", transaction.getCurrencyId());
                    // نحذف الحقل القديم "currency" من المستند على فايرستور إن وُجد
                    txData.put("currency", com.google.firebase.firestore.FieldValue.delete());
                }

                String firestoreId = transaction.getFirestoreId();

                if (firestoreId != null && !firestoreId.isEmpty()) {
                    // مستند موجود — نحاول تحديثه أولاً
                    com.google.firebase.firestore.DocumentReference docRef = firestore.collection("transactions").document(firestoreId);
                    com.google.firebase.firestore.DocumentSnapshot snapshot = Tasks.await(docRef.get());
                    if (snapshot.exists()) {
                        // حدث المستند — استخدام update أو set(merge)
                        Tasks.await(docRef.set(txData, com.google.firebase.firestore.SetOptions.merge()));
                        transactionDao.updateSyncStatus(transaction.getId(), "SYNCED", System.currentTimeMillis());
                        continue;
                    } else {
                        // إذا لم يوجد المستند (ربما تم حذفه) سننشئ مستندًا جديدًا
                        firestoreId = firestore.collection("transactions").document().getId();
                        transaction.setFirestoreId(firestoreId);
                    }
                } else {
                    // إنشاء firestoreId جديد للمعاملة
                    firestoreId = firestore.collection("transactions").document().getId();
                    transaction.setFirestoreId(firestoreId);
                }

                // أخيرًا، انشئ/اكتب المستند
                Tasks.await(firestore.collection("transactions").document(firestoreId).set(txData, com.google.firebase.firestore.SetOptions.merge()));
                // تحديث الحالة المحلية فقط بعد النجاح
//                transactionDao.updateFirestoreIdAndSyncStatus(transaction.getId(), firestoreId, "SYNCED", System.currentTimeMillis());
                transactionDao.updateSyncStatus(transaction.getId(), "SYNCED", transaction.getLastModified());
            } catch (Exception e) {
                // إذا كان خطأ صلاحيات → لا تعيد محاولة مزامنة هذه العناصر (قد تكون القواعد تمنع الكتابة)
                if (e.getCause() instanceof com.google.firebase.firestore.FirebaseFirestoreException) {
                    com.google.firebase.firestore.FirebaseFirestoreException ffe = (com.google.firebase.firestore.FirebaseFirestoreException) e.getCause();
                    if (ffe.getCode() == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Log.e(TAG, "Permission denied when syncing transaction id=" + transaction.getId() + ", skipping.", e);
                        // نحدّث السجل المحلي لنعلم أنه تم تجاهله (أو نحتفظ به EDITED حسب رغبتك)
                        transactionDao.updateSyncStatus(transaction.getId(), "SYNCED", transaction.getLastModified());
                        continue;
                    }
                }
                // في الحالات الأخرى نرمي الاستثناء ليعاد المحاولة بواسطة WorkManager
                throw e;
            }

        } // end for
    }
    private void uploadTransactions_old() throws Exception {
        List<Transaction> unsynced = transactionDao.getUnsyncedTransactions();
        for (Transaction transaction : unsynced) {
            // تأكد من وجود ownerUID
            Log.d(TAG, "uploadTransactions To Firestore: " + transaction.getId());
            if (transaction.getOwnerUID() == null) {
                Log.e(TAG, "Transaction missing ownerUID, skipping sync. ID: " + transaction.getId());
                continue;
            }

            // تأكد من وجود الحساب الأب
            Account parentAccount = accountDao.getAccountByIdBlocking(transaction.getAccountId());
            if (parentAccount == null || parentAccount.getFirestoreId() == null) {
                Log.w(TAG, "Parent account not synced, skipping transaction. Account ID: " + transaction.getAccountId());
                continue;
            }

            Map<String, Object> txData = getTransactionMap(transaction);
            String firestoreId = transaction.getFirestoreId();

            // إنشاء مستند جديد إذا لزم الأمر
            if (firestoreId == null || firestoreId.isEmpty()) {
                firestoreId = firestore.collection("transactions").document().getId();
                transaction.setFirestoreId(firestoreId);
            }

            // إضافة الحقول المطلوبة للقواعد الأمنية
            txData.put("accountFirestoreId", parentAccount.getFirestoreId());
            txData.put("ownerUID", transaction.getOwnerUID());
            txData.put("billType", transaction.getBillType());
            txData.put("currencyFirestoreId", transaction.getCurrencyFirestoreId());
            // استخدام set() مع دمج البيانات
            Tasks.await(firestore.collection("transactions").document(firestoreId)
                    .set(txData, SetOptions.merge()));

            // تحديث الحالة المحلية
            transactionDao.updateSyncStatus(transaction.getId(), "SYNCED", System.currentTimeMillis());
        }
    }
    private void uploadTransactions() throws Exception {
        TransactionUploadController controller = new TransactionUploadController(getApplicationContext());
        List<Transaction> unsynced = transactionDao.getUnsyncedTransactions();

        for (Transaction tx : unsynced) {
            if (!controller.canSend(tx)) {
                if (!controller.hasBlocked()) {
                    controller.storeFirstBlocked(tx);
                    // أظهر ديالوج تحذير مرة واحدة
//                    NotificationHelper.get().showBlockedDialog(getApplicationContext(),
//                            "عذراً هناك عمليات لم يتم إرسالها...",
//                            "يرجى رفع رصيدك سواء بمشاهدة إعلان أو دعوة صديق...");
                    NotificationHelper.get().showLocalNotification("عمليات معلقة", "عذراً هناك عمليات لم يتم إرسالها بسبب استهلاك رصيدك في الجهاز الاخر , يرجى رفع رصيدك سواء بمشاهدة إعلان أو دعوة صديق او شراء النسخة الكاملة",
                            1001,true);
                }
                continue; // اترك العمليات معلقة
            }

            // 🔹 أكمل الرفع كالمعتاد
           // Map<String,Object> data = getTransactionMap(tx);
            String fsId = tx.getFirestoreId();
            if (fsId == null || fsId.isEmpty()) {
              //  fsId = firestore.collection("transactions").document().getId();

                tx.setFirestoreId(UUIDGenerator.generateSequentialUUID());
            }
            Log.d(TAG, "uploadTransactions To Firestore: " + tx.getId());
            if (tx.getOwnerUID() == null) {
                Log.e(TAG, "Transaction missing ownerUID, skipping sync. ID: " + tx.getId());
                continue;
            }

            // تأكد من وجود الحساب الأب
            Account parentAccount = accountDao.getAccountByIdBlocking(tx.getAccountId());
            if (parentAccount == null || parentAccount.getFirestoreId() == null) {
                Log.w(TAG, "Parent account not synced, skipping transaction. Account ID: " + tx.getAccountId());
                continue;
            }

            Map<String, Object> txData = getTransactionMap(tx);
            // إضافة الحقول المطلوبة للقواعد الأمنية
            txData.put("accountFirestoreId", parentAccount.getFirestoreId());
            txData.put("ownerUID", tx.getOwnerUID());
            txData.put("billType", tx.getBillType());
            txData.put("currencyFirestoreId", tx.getCurrencyFirestoreId());
            txData.put("firestoreId", tx.getFirestoreId());
            Tasks.await(firestore.collection("transactions")
                    .document(fsId).set(txData, SetOptions.merge()));
            transactionDao.updateSyncStatus(tx.getId(),"SYNCED",System.currentTimeMillis());
        }
    }


    private void handleDeletesInFirestore() throws Exception {
        // 1. حذف العمليات
        List<Transaction> deletedTxs = transactionDao.getDeletedTransactions(); // استعلام جديد
        for (Transaction tx : deletedTxs) {
            Tasks.await(firestore.collection("transactions").document(tx.getFirestoreId()).delete());
            transactionDao.delete(tx); // **حذفها نهائيًا من Room بعد مزامنة الحذف**
        }

        // 2. حذف الحسابات
        List<Account> deletedAccs = accountDao.getDeletedAccounts(); // استعلام جديد
        for (Account acc : deletedAccs) {
            Tasks.await(firestore.collection("accounts").document(acc.getFirestoreId()).delete());
            accountDao.delete(acc); // **حذفها نهائيًا من Room**
        }
        List<Currency> deletedCurrency = currencyDao.getDeletedCurrencies(); // استعلام جديد
        for (Currency acc : deletedCurrency) {
            Tasks.await(firestore.collection("currencies").document(acc.getFirestoreId()).delete());
            currencyDao.delete(acc); // **حذفها نهائيًا من Room**
        }
        List<AccountType> deletedaccountType = accountTypeDao.getDeletedAccountTypes(); // استعلام جديد
        for (AccountType acc : deletedaccountType) {
            Tasks.await(firestore.collection("accountTypes").document(acc.getFirestoreId()).delete());
            accountTypeDao.delete(acc); // **حذفها نهائيًا من Room**
        }
        // ... (يمكن إضافة نفس المنطق للعملات وأنواع الحسابات)
    }
}

