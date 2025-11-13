package com.hpp.daftree.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Upsert;

import com.hpp.daftree.models.AccountBalanceSummary;
import com.hpp.daftree.models.AccountWithBalance;

import java.util.List;

@Dao
public interface AccountDao {

    @Insert
    long insert(Account account);

    @Update
    void update(Account account);
    @Delete
    void delete(Account account);


    @Query("DELETE FROM accounts WHERE id = :accountId")
    void deleteAccountById(int accountId);



     @Query("SELECT acc.*, " +
            "COALESCE(SUM(CASE WHEN tx.currencyId = :currency THEN tx.amount * tx.type ELSE 0 END), 0) as balance, " +
            "COALESCE(SUM(CASE WHEN tx.currencyId = :currency THEN 1 ELSE 0 END), 0) as transactionCount, " +
            "MAX(tx.timestamp) as lastTransactionDate " +
            "FROM accounts acc " +
            "LEFT JOIN transactions tx ON acc.id = tx.accountId AND tx.syncStatus != 'DELETED' " +
            "WHERE acc.ownerUID = :ownerUID AND acc.syncStatus != 'DELETED' " +
            "AND (:filterType IS NULL OR acc.accountType = :filterType) " +
            "AND acc.accountName LIKE :searchQuery " +
            "GROUP BY acc.id " +
            "HAVING transactionCount > 0 AND tx.syncStatus != 'DELETED' ")
//            "HAVING (SELECT COUNT(*) FROM transactions WHERE accountId = acc.id AND syncStatus != 'DELETED') > 0 ")
    LiveData<List<AccountWithBalance>> getAccountsWithBalances(String ownerUID, String currency, String filterType, String searchQuery);


    @Query("SELECT acc.*, MAX(tx.timestamp) as lastTransactionDate, " +
            "COALESCE(SUM(CASE WHEN tx.currencyId = :currency THEN tx.amount * tx.type ELSE 0 END), 0) as balance, " +
            "COALESCE(SUM(CASE WHEN tx.currencyId = :currency THEN 1 ELSE 0 END), 0) as transactionCount " +
            "FROM accounts acc " +
            "LEFT JOIN transactions tx ON acc.id = tx.accountId " +
            "WHERE acc.ownerUID = :ownerUID AND acc.syncStatus != 'DELETED' " +
            "AND (:filterType IS NULL OR acc.accountType = :filterType) " +
            "GROUP BY acc.id " +
            "HAVING transactionCount > 0 AND tx.syncStatus != 'DELETED'")
    List<AccountWithBalance> getAccountsWithBalancesBlocking(String ownerUID, String currency, String filterType);

    // إضافة هذا الاستعلام لجلب أرصدة الحسابات بنوع معين لشهر معين
    @Query("SELECT acc.*, " +
            "COALESCE(SUM(CASE WHEN tx.currencyId = :currency THEN tx.amount * tx.type ELSE 0 END), 0) as balance, " +
            "COALESCE(MAX(tx.timestamp), 0) as lastTransactionDate, " +
            "COALESCE(SUM(CASE WHEN tx.currencyId = :currency THEN 1 ELSE 0 END), 0) as transactionCount " +
            "FROM accounts acc " +
            "LEFT JOIN transactions tx ON acc.id = tx.accountId " +
            "WHERE acc.ownerUID = :ownerUID AND acc.syncStatus != 'DELETED' " +
            "AND acc.accountType = :accountType " +
            "AND strftime('%Y-%m', datetime(tx.timestamp/1000, 'unixepoch')) = :month " +
            "GROUP BY acc.id " +
            "HAVING balance != 0 AND tx.syncStatus != 'DELETED' ")
    LiveData<List<AccountWithBalance>> getAccountsWithBalancesForMonth(String ownerUID, String currency, String accountType, String month);


    @Query("SELECT acc.id as accountId, acc.accountName as accountName," +
            " SUM(tx.amount * tx.type) as balance, MAX(tx.timestamp) as lastTransactionDate" +
            " FROM accounts acc JOIN transactions tx ON acc.id = tx.accountId " +
            "WHERE acc.accountType = :accountType AND tx.currencyId = :currency AND acc.syncStatus != 'DELETED' AND tx.syncStatus != 'DELETED' " +
            "AND tx.timestamp  BETWEEN :startDate AND :endDate GROUP BY acc.id ORDER BY balance DESC")
    List<AccountBalanceSummary> getAccountBalancesByType(String accountType, String currency, long startDate, long endDate);
    @Query("SELECT acc.id as accountId, acc.accountName as accountName," +
            " SUM(tx.amount * tx.type) as balance, MAX(tx.timestamp) as lastTransactionDate" +
            " FROM accounts acc JOIN transactions tx ON acc.id = tx.accountId " +
            "WHERE acc.id = :accountId AND tx.currencyId = :currency AND acc.syncStatus != 'DELETED' AND tx.syncStatus != 'DELETED' " +
            "AND tx.timestamp  BETWEEN :startDate AND :endDate GROUP BY acc.id ORDER BY balance DESC")
    List<AccountBalanceSummary> getAccountBalances2(int accountId, int currency, long startDate, long endDate);

    @Query("SELECT EXISTS (SELECT 1 FROM transactions tx " +
            "JOIN accounts acc ON tx.accountId = acc.id WHERE acc.accountType = :accountType AND tx.currencyId = :currency LIMIT 1)")
    int hasTransactions(String accountType, String currency);


    @Query("SELECT acc.*, " +
            "COALESCE(SUM(CASE WHEN tx.currencyId = :currencyId THEN tx.amount * tx.type ELSE 0 END), 0) as balance, " +
            "COALESCE(SUM(CASE WHEN tx.currencyId = :currencyId THEN 1 ELSE 0 END), 0) as transactionCount, " +
            "MAX(tx.timestamp) as lastTransactionDate " +
            "FROM accounts acc " +
            "LEFT JOIN transactions tx ON acc.id = tx.accountId AND tx.syncStatus != 'DELETED' " +
            "WHERE acc.ownerUID = :ownerUID AND acc.syncStatus != 'DELETED' " +
            "AND (:filterAcTypeFirestoreId IS NULL OR acc.acTypeFirestoreId = :filterAcTypeFirestoreId) " +
            "AND acc.accountName LIKE :searchQuery " +
            "GROUP BY acc.id " +
            "HAVING transactionCount > 0")
    LiveData<List<AccountWithBalance>> getAccountsWithBalances(String ownerUID, int currencyId, String filterAcTypeFirestoreId, String searchQuery);

//    LiveData<List<AccountWithBalance>> getAccountsWithBalances1(String ownerUID, int currencyId, String filterType, String searchQuery);

    // AccountDao.java

    // الاستعلام الآن يقبل acTypeFirestoreId بدلاً من اسم النوع
    @Query("SELECT acc.*, " +
            "COALESCE(SUM(CASE WHEN tx.currencyId = :currencyId THEN tx.amount * tx.type ELSE 0 END), 0) as balance, " +
            "COALESCE(SUM(CASE WHEN tx.currencyId = :currencyId THEN 1 ELSE 0 END), 0) as transactionCount, " +
            "MAX(tx.timestamp) as lastTransactionDate " +
            "FROM accounts acc " +
            "LEFT JOIN transactions tx ON acc.id = tx.accountId AND tx.syncStatus != 'DELETED' " +
            "WHERE acc.ownerUID = :ownerUID AND acc.syncStatus != 'DELETED' " +
            // **هذا هو التعديل الجوهري**
            "AND (:filterAcTypeFirestoreId IS NULL OR acc.acTypeFirestoreId = :filterAcTypeFirestoreId) " +
            "AND acc.accountName LIKE :searchQuery " +
            "GROUP BY acc.id " +
            "HAVING (SELECT COUNT(*) FROM transactions WHERE accountId = acc.id AND syncStatus != 'DELETED') > 0 " +
            "ORDER BY acc.lastModified DESC")
    LiveData<List<AccountWithBalance>> getAccountsWithBalances11(String ownerUID, int currencyId, String filterAcTypeFirestoreId, String searchQuery);


    // تم التعديل: currencyId بدلاً من currency
    @Query("SELECT acc.*, MAX(tx.timestamp) as lastTransactionDate, " +
            "COALESCE(SUM(CASE WHEN tx.currencyId = :currencyId THEN tx.amount * tx.type ELSE 0 END), 0) as balance, " +
            "COALESCE(SUM(CASE WHEN tx.currencyId = :currencyId THEN 1 ELSE 0 END), 0) as transactionCount " +
            "FROM accounts acc " +
            "LEFT JOIN transactions tx ON acc.id = tx.accountId " +
            "WHERE acc.ownerUID = :ownerUID AND acc.syncStatus != 'DELETED' " +
            "AND (:filterType IS NULL OR acc.accountType = :filterType) " +
            "GROUP BY acc.id " +
            "HAVING transactionCount > 0 AND tx.syncStatus != 'DELETED'")
    List<AccountWithBalance> getAccountsWithBalancesBlocking(String ownerUID, int currencyId, String filterType);

    // تم التعديل: currencyId بدلاً من currency
    @Query("SELECT acc.id as accountId, acc.accountName as accountName," +
            " SUM(tx.amount * tx.type) as balance, MAX(tx.timestamp) as lastTransactionDate" +
            " FROM accounts acc JOIN transactions tx ON acc.id = tx.accountId " +
            "WHERE acc.accountType = :accountType AND tx.currencyId = :currencyId AND acc.syncStatus != 'DELETED' AND tx.syncStatus != 'DELETED' " +
            "AND tx.timestamp  BETWEEN :startDate AND :endDate GROUP BY acc.id ORDER BY balance DESC")
    List<AccountBalanceSummary> getAccountBalancesByType11(String accountType, int currencyId, long startDate, long endDate);

    // تم التعديل: currencyId بدلاً من currency
    @Query("SELECT acc.id as accountId, acc.accountName as accountName," +
            " SUM(tx.amount * tx.type) as balance, MAX(tx.timestamp) as lastTransactionDate" +
            " FROM accounts acc JOIN transactions tx ON acc.id = tx.accountId " +
            "WHERE acc.id = :accountId AND tx.currencyId = :currencyId AND acc.syncStatus != 'DELETED' AND tx.syncStatus != 'DELETED' " +
            "AND tx.timestamp  BETWEEN :startDate AND :endDate GROUP BY acc.id ORDER BY balance DESC")
    List<AccountBalanceSummary> getAccountBalances(int accountId, int currencyId, long startDate, long endDate);

    // تم التعديل: currencyId بدلاً من currency
    @Query("SELECT EXISTS (SELECT 1 FROM transactions tx " +
            "JOIN accounts acc ON tx.accountId = acc.id WHERE acc.accountType = :accountType AND tx.currencyId = :currencyId LIMIT 1)")
    int hasTransactions11(String accountType, int currencyId);

    // ... باقي دوال الحسابات تبقى كما هي ...
    @Query("SELECT * FROM accounts WHERE ownerUID = :ownerUID AND syncStatus != 'DELETED'  ORDER BY lastModified ASC")
    LiveData<List<Account>> getAllAccounts(String ownerUID);
    @Query("SELECT * FROM accounts WHERE ownerUID = :ownerUID AND syncStatus != 'DELETED'  ORDER BY lastModified ASC")
    List<Account> getAllAccountsBlocking(String ownerUID);
    @Query("SELECT * FROM accounts WHERE  syncStatus != 'DELETED'  ORDER BY id ASC")
    List<Account> getAllAccountsBlockingRestore();
    @Query("SELECT * FROM accounts   ORDER BY lastModified ASC")
    List<Account> getAllAccountsBlock();
    @Query("SELECT * FROM accounts WHERE id = :accountId LIMIT 1")
    LiveData<Account> getAccountById(int accountId);
    @Query("SELECT firestoreId FROM accounts WHERE id = :accountId LIMIT 1")
    String getAccountFireStoreById(int accountId);
    @Query("SELECT * FROM accounts WHERE syncStatus IN ('NEW', 'EDITED')")
    List<Account> getUnsyncedAccounts();
    @Query("SELECT * FROM accounts WHERE id = :accountId LIMIT 1")
    Account getAccountByIdBlocking(int accountId);
    @Query("SELECT accountName FROM accounts")
    List<String> getAllAccountNames();
    @Query("SELECT * FROM accounts WHERE accountName = :name LIMIT 1")
    Account getAccountByName(String name);
    @Query("SELECT * FROM accounts WHERE syncStatus != 'DELETED' ")
    List<Account> getAll();
    @Query("SELECT COUNT(*) FROM accounts WHERE syncStatus IN ('NEW', 'EDITED')")
    int getUnsyncedCount();
    @Query("UPDATE accounts SET syncStatus = :status, lastModified = :timestamp WHERE id = :id")
    int updateSyncStatus(int id, String status, long timestamp);
    @Query("SELECT * FROM accounts WHERE firestoreId = :firestoreId LIMIT 1")
    Account getAccountByFirestoreId(String firestoreId);

    @Query("SELECT id FROM accounts WHERE firestoreId = :firestoreId LIMIT 1")
    int getAccountIDByFirestoreId(String firestoreId);
    @Query("SELECT * FROM accounts WHERE accountName = :name LIMIT 1")
    Account getAccountByNameBlocking(String name);
    @Query("SELECT * FROM accounts WHERE accountName = :name  LIMIT 1")
    boolean existsByName(String name);

    @Query("DELETE FROM accounts WHERE firestoreId = :firestoreId")
    void deleteByFirestoreId(String firestoreId);
    @Upsert
    void upsert(Account account);
    @Query("SELECT * FROM accounts LIMIT 1")
    Account getFirstAccountBlocking();
    @Query("SELECT * FROM accounts WHERE ownerUID = :ownerUID AND accountType = :accountType")
    List<Account> getAccountsByTypeBlocking(String ownerUID, String accountType);
    @Query("SELECT EXISTS (SELECT 1 FROM transactions tx JOIN accounts acc ON tx.accountId = acc.id WHERE acc.accountType = :accountType LIMIT 1)")
    int hasAnyTransactionsForType(String accountType);
    @Query("SELECT * FROM accounts WHERE syncStatus IN ('DELETED')")
    List<Account> getDeletedAccounts();
    @Query("UPDATE accounts SET accountType = :newName, syncStatus = 'EDITED' WHERE accountType = :oldName")
    void updateAccountTypeInAccounts(String oldName, String newName);
    @Query("SELECT * FROM accounts WHERE firestoreId = :firestoreId AND  ownerUID = :ownerUID LIMIT 1")
    boolean existsByFirestoreId(String firestoreId,String ownerUID);

    @Query("SELECT COUNT(*) FROM transactions t " +
            "JOIN accounts a ON t.accountId = a.id " +
            "WHERE t.currencyId = :currencyId  AND t.syncStatus != 'DELETED' " +
            "AND ((:filter IS NULL OR a.accountType = :filter)  AND a.syncStatus != 'DELETED')")
    int hasTransactionsForCurrencyAndFilter(int currencyId, String filter);

    @Query("SELECT acc.id as accountId, acc.accountName as accountName," +
            " SUM(tx.amount * tx.type) as balance, MAX(tx.timestamp) as lastTransactionDate" +
            " FROM accounts acc JOIN transactions tx ON acc.id = tx.accountId " +
            "WHERE acc.acTypeFirestoreId = :acTypeFirestoreId AND tx.currencyId = :currencyId AND acc.syncStatus != 'DELETED' AND tx.syncStatus != 'DELETED' " +
            "AND tx.timestamp BETWEEN :startDate AND :endDate GROUP BY acc.id ORDER BY balance DESC")
    List<AccountBalanceSummary> getAccountBalancesByType(String acTypeFirestoreId, int currencyId, long startDate, long endDate);
    @Query("SELECT acc.id as accountId, acc.accountName as accountName," +
            " SUM(tx.amount * tx.type) as balance, MAX(tx.timestamp) as lastTransactionDate" +
            " FROM accounts acc JOIN transactions tx ON acc.id = tx.accountId " +
            "WHERE acc.accountType = :acType AND tx.currencyId = :currencyId AND acc.syncStatus != 'DELETED' AND tx.syncStatus != 'DELETED' " +
            "AND tx.timestamp BETWEEN :startDate AND :endDate GROUP BY acc.id ORDER BY balance DESC")
    List<AccountBalanceSummary> getAccountBalancesBynameType(String acType, int currencyId, long startDate, long endDate);

    // تم تعديل هذا الاستعلام ليعتمد على acTypeFirestoreId
    @Query("SELECT EXISTS (SELECT 1 FROM transactions tx " +
            "JOIN accounts acc ON tx.accountId = acc.id WHERE acc.acTypeFirestoreId = :acTypeFirestoreId AND tx.currencyId = :currencyId LIMIT 1)")
    int hasTransactions(String acTypeFirestoreId, int currencyId);


    // الاستعلام الآن يقبل acTypeFirestoreId بدلاً من اسم النوع
    @Query("SELECT acc.*, " +
            "COALESCE(SUM(CASE WHEN tx.currencyId = :currencyId THEN tx.amount * tx.type ELSE 0 END), 0) as balance, " +
            "COALESCE(SUM(CASE WHEN tx.currencyId = :currencyId THEN 1 ELSE 0 END), 0) as transactionCount, " +
            "MAX(tx.timestamp) as lastTransactionDate " +
            "FROM accounts acc " +
            "LEFT JOIN transactions tx ON acc.id = tx.accountId AND tx.syncStatus != 'DELETED' " +
            "WHERE acc.ownerUID = :ownerUID AND acc.syncStatus != 'DELETED' " +
            "AND (:filterAcTypeFirestoreId IS NULL OR acc.acTypeFirestoreId = :filterAcTypeFirestoreId) " +
            "AND acc.accountName LIKE :searchQuery " +
            "GROUP BY acc.id " +
            "HAVING (SELECT COUNT(*) FROM transactions WHERE accountId = acc.id AND syncStatus != 'DELETED') > 0 " +
            "ORDER BY acc.lastModified DESC")
    LiveData<List<AccountWithBalance>> getAccountsWithBalancesByType(String ownerUID, int currencyId, String filterAcTypeFirestoreId, String searchQuery);
    @Query("SELECT * FROM accounts")
    List<Account> getAllAccountsForDebug();

    @Query("SELECT * FROM accounts WHERE ownerUID = :ownerUID")
    List<Account> getAccountsByOwnerDebug(String ownerUID);
    @Query("UPDATE accounts SET syncStatus = :status, ownerUID = :ownerUID WHERE syncStatus != 'DELETED' ")
    void upgradeToOfficialUser(String ownerUID, String status);

    @Query("DELETE FROM accounts ")
    void deleteGuestData();
    @Query("SELECT COUNT(*) FROM accounts")
    int getAccountsCount();
    /**
     * جلب جميع الحسابات من قاعدة البيانات.
     * يتم إرجاع البيانات كـ LiveData، مما يعني أن واجهة المستخدم سيتم إعلامها تلقائيًا
     * بأي تغييرات تحدث في جدول الحسابات.
     * @return قائمة LiveData تحتوي على جميع الحسابات.
     */
    @Query("SELECT * FROM accounts ORDER BY accountName ASC")
    LiveData<List<Account>> getAllAccount();

    @Query("SELECT * FROM accounts")
    List<Account> getAllAccounts();
}


