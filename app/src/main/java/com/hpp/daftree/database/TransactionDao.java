package com.hpp.daftree.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Upsert;

import com.hpp.daftree.MonthlyBalance;
import com.hpp.daftree.MonthlySummary;
import com.hpp.daftree.TransactionWithAccount;
import com.hpp.daftree.models.AccountTotalsByCurrency;

import java.util.List;

@Dao
public interface TransactionDao {

    @Insert
    void insert(Transaction transaction);

    @Update
    void update(Transaction transaction);

    @Delete
    void delete(Transaction transaction);

//    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND currency = :currency ORDER BY timestamp DESC")
//    LiveData<List<Transaction>> getTransactionsForAccount(int accountId, String currency);

    // استعلام لحذف كل المعاملات لحساب معين
    // تعديل: تغيير الترتيب إلى ASC لحساب الرصيد التراكمي بشكل صحيح
    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND currencyId = :currency ORDER BY timestamp ASC")
    LiveData<List<Transaction>> getTransactionsForAccount(int accountId, String currency);

     /**
     * تعديل: الاستعلام الآن يقبل accountId و currency للفلترة الدقيقة.
     */
//    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND currency = :currency ORDER BY timestamp DESC")
//    LiveData<List<Transaction>> getLiveTransactionsForAccount(int accountId, String currency);

    @Query("SELECT SUM(amount) FROM transactions " +
            "WHERE currencyId = :currency AND type = 1 AND syncStatus != 'DELETED'  AND accountId IN " +
            "(SELECT id FROM accounts WHERE ownerUID = :ownerUID)")
    LiveData<Double> getTotalDebitForUser(String ownerUID, String currency);

    /**
     * جديد: دالة لحساب مجموع كل المبالغ الدائنة (لك / type = -1)
     * لمستخدم معين وبعملة محددة.
     */
    @Query("SELECT SUM(amount) FROM transactions " +
            "WHERE currencyId = :currency AND type = -1 AND syncStatus != 'DELETED'  AND accountId IN " +
            "(SELECT id FROM accounts WHERE ownerUID = :ownerUID)")
    LiveData<Double> getTotalCreditForUser(String ownerUID, String currency);

          @Query("SELECT SUM(amount) FROM transactions " +
            "WHERE accountId = :accountId AND currencyId = :currency AND type = 1 AND syncStatus != 'DELETED' ")
    LiveData<Double> getDebitForAccount(int accountId, String currency);

    @Query("SELECT SUM(amount) FROM transactions WHERE accountId = :accountId AND currencyId = :currency AND type = -1  AND syncStatus != 'DELETED' ")
    LiveData<Double> getCreditForAccount(int accountId, String currency);
     @Query("SELECT * FROM transactions" +
            " WHERE accountId = :accountId AND currencyId = :currency  AND syncStatus != 'DELETED'  AND details LIKE '%' || :searchQuery || '%' ORDER BY timestamp,lastModified ASC")
    LiveData<List<Transaction>> getTransactionsForAccount(int accountId, String currency, String searchQuery);


     /**
     * يجلب العمليات ضمن نطاق زمني محدد لتقرير "الحركة التفصيلية".
     */
    @Query("SELECT * FROM transactions WHERE ownerUID = :ownerUID AND currencyId = :currency  AND syncStatus != 'DELETED' " +
            "AND timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp ASC")
    List<Transaction> getTransactionsByDateRange(String ownerUID, String currency, long startDate, long endDate);

    /**
     * يجمع حركات كل الشهور لسنة معينة لتقرير "الحركة الشهرية الإجمالي".
     */
    @Query("SELECT strftime('%Y-%m', datetime(timestamp/1000, 'unixepoch')) as month, " +
            "SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as totalDebit, " +
            "SUM(CASE WHEN type = -1 THEN amount ELSE 0 END) as totalCredit " +
            "FROM transactions WHERE ownerUID = :ownerUID  AND syncStatus != 'DELETED'  AND currencyId = :currency " +
            "AND strftime('%Y', datetime(timestamp/1000, 'unixepoch')) = :year " +
            "GROUP BY month ORDER BY month ASC")
    List<MonthlyBalance> getMonthlyBalancesForUser(String ownerUID, String currency, String year);

    /**
     * يجمع حركات كل الشهور لحساب معين.
     */
    @Query("SELECT strftime('%Y-%m', datetime(timestamp/1000, 'unixepoch')) as month, " +
            "SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as totalDebit, " +
            "SUM(CASE WHEN type = -1 THEN amount ELSE 0 END) as totalCredit " +
            "FROM transactions WHERE accountId = :accountId AND currencyId = :currency  AND syncStatus != 'DELETED' " +
            "AND strftime('%Y', datetime(timestamp/1000, 'unixepoch')) = :year " +
            "GROUP BY month ORDER BY month ASC")
    List<MonthlyBalance> getMonthlyBalancesForAccount(int accountId, String currency, String year);
  // إضافة هذا الاستعلام لجلب المعاملات بنوع حساب معين ضمن نطاق زمني
    @Query("SELECT tx.*, acc.accountName as accountName " +
            "FROM transactions tx " +
            "INNER JOIN accounts acc ON tx.accountId = acc.id " +
            "WHERE acc.ownerUID = :ownerUID  AND acc.syncStatus != 'DELETED' " +
            "AND acc.accountType = :accountType " +
            "AND tx.currencyId = :currency  AND tx.syncStatus != 'DELETED' " +
            "AND tx.timestamp BETWEEN :startDate AND :endDate " +
            "ORDER BY tx.timestamp ASC")
    LiveData<List<TransactionWithAccount>> getTransactionsByAccountType(String ownerUID, String accountType, String currency, long startDate, long endDate);
    @Query("SELECT MAX(timestamp) FROM transactions WHERE accountId = :accountId")
    Long getLastTransactionDateForAccount1(int accountId);
    @Query("SELECT MAX(timestamp) FROM transactions WHERE accountId = :accountId")
    Long getLastTransactionDateForAccount(int accountId);

//    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND currencyId = :currency ORDER BY timestamp ASC")
//    List<Transaction> getTransactionsForAccountBlocking(int accountId, String currency);

    @Query("SELECT SUM(amount) FROM transactions " +
            "WHERE accountId = :accountId AND currencyId = :currency  AND syncStatus != 'DELETED' AND type = 1")
    Double getDebitForAccountBlocking(int accountId, String currency);

    @Query("SELECT SUM(amount) FROM transactions " +
            "WHERE accountId = :accountId AND currencyId = :currency  AND syncStatus != 'DELETED'  AND type = -1")
    Double getCreditForAccountBlocking(int accountId, String currency);
    @Query("SELECT strftime('%Y-%m', datetime(timestamp/1000, 'unixepoch')) as month, " +
            "SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as totalDebit, " +
            "SUM(CASE WHEN type = -1 THEN amount ELSE 0 END) as totalCredit " +
            "FROM transactions WHERE ownerUID = :ownerUID AND currencyId = :currency  AND syncStatus != 'DELETED' " +
            "AND strftime('%Y', datetime(timestamp/1000, 'unixepoch')) = :year " +
            "GROUP BY month ORDER BY month ASC")
    List<MonthlyBalance> getMonthlyBalancesForUserBlocking(String ownerUID, String currency, String year);

    @Query("SELECT strftime('%Y-%m', datetime(timestamp/1000, 'unixepoch')) as month, " +
            "SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as totalDebit, " +
            "SUM(CASE WHEN type = -1 THEN amount ELSE 0 END) as totalCredit " +
            "FROM transactions WHERE accountId = :accountId AND currencyId = :currency  AND syncStatus != 'DELETED' " +
            "AND strftime('%Y', datetime(timestamp/1000, 'unixepoch')) = :year " +
            "GROUP BY month ORDER BY month ASC")
    List<MonthlyBalance> getMonthlyBalancesForAccountBlocking(int accountId, String currency, String year);

    @Query("SELECT tx.*, acc.accountName as accountName " +
            "FROM transactions tx JOIN accounts acc ON tx.accountId = acc.id " +
            "WHERE acc.accountType = :accountType  AND acc.syncStatus != 'DELETED'  AND tx.syncStatus != 'DELETED' AND tx.timestamp " +
            "BETWEEN :startDate AND :endDate ORDER BY tx.timestamp ASC")
    List<TransactionWithAccount> getDetailedMovementByAccountType(String accountType, long startDate, long endDate);

    @Query("SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch') as yearMonth, " +
            "SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as totalDebit, " +
            "SUM(CASE WHEN type = -1 THEN amount ELSE 0 END) as totalCredit " +
            "FROM transactions WHERE accountId = :accountId  AND syncStatus != 'DELETED' AND strftime('%Y', timestamp / 1000, 'unixepoch') = :year " +
            "GROUP BY yearMonth")
    List<MonthlySummary> getMonthlySummaryForAccount(int accountId, String year);


    // استعلام مشابه للحركة الشهرية الإجمالية لنوع حساب معين

    @Query("SELECT tx.*, acc.accountName as accountName" +
            " FROM transactions tx JOIN accounts acc ON tx.accountId = acc.id " +
            "WHERE acc.accountType = :accountType AND tx.timestamp  AND acc.syncStatus != 'DELETED'  AND tx.syncStatus != 'DELETED' BETWEEN :startDate AND :endDate ORDER BY tx.timestamp ASC")
    List<TransactionWithAccount> getConsolidatedMovement(String accountType, long startDate, long endDate);

    @Query("SELECT tx.*, acc.accountName as accountName" +
            " FROM transactions tx JOIN accounts acc ON tx.accountId = acc.id " +
            "WHERE acc.acTypeFirestoreId = :acTypeFirestoreId AND tx.currencyId = :currency " +
            "AND acc.syncStatus != 'DELETED'  AND tx.syncStatus != 'DELETED' " +
            "AND tx.timestamp  BETWEEN :startDate AND :endDate ORDER BY tx.timestamp ASC")
    List<TransactionWithAccount> getConsolidatedMovementAllCurrencies(String acTypeFirestoreId,int currency, long startDate, long endDate);

    @Query("SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch') as yearMonth, SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as totalDebit, SUM(CASE WHEN type = -1 THEN amount ELSE 0 END) as totalCredit " +
            "FROM transactions WHERE accountId = :accountId  AND syncStatus != 'DELETED' " +
            " GROUP BY yearMonth ORDER BY yearMonth ASC")
    List<MonthlySummary> getMonthlySummaryForAccount(int accountId);
    @Query("SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch') as yearMonth," +
            " SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as totalDebit, " +
            "SUM(CASE WHEN type = -1 THEN amount ELSE 0 END) as totalCredit " +
            "FROM transactions WHERE accountId = :accountId AND currencyId = :currency  AND syncStatus != 'DELETED' " +
            " GROUP BY yearMonth ORDER BY yearMonth ASC")
    List<MonthlySummary> getMonthlySummaryForAccountAllCurrencies(int accountId,int currency);

    @Query("SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch') as yearMonth," +
            " SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as totalDebit," +
            " SUM(CASE WHEN type = -1 THEN amount ELSE 0 END) as totalCredit FROM transactions " +
            "WHERE accountId IN (SELECT id FROM accounts WHERE accountType = :accountType AND syncStatus != 'DELETED')" +
            "  AND syncStatus != 'DELETED' GROUP BY yearMonth ORDER BY yearMonth ASC")
    List<MonthlySummary> getMonthlySummaryByAccountType(String accountType);
    @Query("SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch') as yearMonth," +
            " SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as totalDebit," +
            " SUM(CASE WHEN type = -1 THEN amount ELSE 0 END) as totalCredit" +
            " FROM transactions " +
            " WHERE currencyId = :currency " +
            "AND accountId IN (SELECT id FROM accounts WHERE acTypeFirestoreId = :acTypeFirestoreId AND syncStatus != 'DELETED') AND syncStatus != 'DELETED'  " +
            "GROUP BY yearMonth ORDER BY yearMonth ASC")
    List<MonthlySummary> getMonthlySummaryByAccountTypeByCurrency(String acTypeFirestoreId,int currency);

    @Query("SELECT SUM(amount * type) FROM transactions WHERE accountId = :accountId AND syncStatus != 'DELETED'  AND timestamp < :startDate")
    Double getBalanceBeforeDate(int accountId, long startDate);
    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND syncStatus != 'DELETED'  AND currencyId = :currency ORDER BY timestamp ASC")
    List<Transaction> getTransactionsForAccountBlocking(int accountId, String currency);
    @Query("SELECT SUM(tx.amount * tx.type) FROM transactions tx JOIN accounts acc ON tx.accountId = acc.id WHERE acc.acTypeFirestoreId = :acTypeFirestoreId AND tx.timestamp < :startDate")
    Double getBalanceByAccountTypeBeforeDate(String acTypeFirestoreId, long startDate);

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE importID = :importId AND importId != 0  LIMIT 1)")
    boolean transactionExistsByImportId(int importId);

    @Query("UPDATE transactions SET currencyId = :newName, syncStatus = 'EDITED', lastModified = :timestamp WHERE currencyId = :oldName")
    void updateCurrencyNameInTransactions(String oldName, String newName, long timestamp);

//    @Query("SELECT DISTINCT currencyId FROM transactions WHERE accountId IN (SELECT id FROM accounts WHERE ownerUID = :ownerUID) AND syncStatus != 'DELETED'")
//    List<String> getCurrenciesWithTransactionsBlocking(String ownerUID);

//    @Query("SELECT MAX(importID) FROM transactions")
//    int getMaxImportId();
  @Query("SELECT CASE WHEN MAX(importID) < 100000 THEN 100000 ELSE MAX(importID) + 1 END FROM transactions")
  int getMaxImportId();
    @Query("SELECT * FROM transactions WHERE importID = :importId AND importId != 0 ORDER BY firestoreId ASC")
    List<Transaction> getTransactionsByImportId(int importId);

    @Query("DELETE FROM transactions WHERE importID = :importId AND importId != 0")
    void deleteTransactionsByImportId(int importId);
    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId AND date(timestamp / 1000, 'unixepoch') = date(:date / 1000, 'unixepoch') AND amount = :amount AND type = :type AND currencyId = :currency AND syncStatus != 'DELETED'")
    int findDuplicateTransaction(int accountId, long date, double amount, int type, int currency);
  @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId  AND currencyId = :currency AND syncStatus != 'DELETED'")
  Integer checkIfFirst(int accountId, int currency);

    // **دالة جديدة: لجلب الإجماليات الشهرية لكل الحسابات مجمعة حسب العملة**
    @Query("SELECT strftime('%Y-%m', timestamp / 1000, 'unixepoch') as yearMonth, " +
            "SUM(CASE WHEN type = 1 THEN amount ELSE 0 END) as totalDebit, " +
            "SUM(CASE WHEN type = -1 THEN amount ELSE 0 END) as totalCredit " +
            "FROM transactions WHERE currencyId = :currency AND syncStatus != 'DELETED' " +
            "GROUP BY yearMonth ORDER BY yearMonth ASC")
    List<MonthlySummary> getGlobalMonthlySummary(int currency);


    @Query("DELETE FROM transactions WHERE accountId = :accountId")
    void deleteTransactionsForAccount(int accountId);

    // تم التعديل: currencyId بدلاً من currency
    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND currencyId = :currencyId ORDER BY timestamp ASC")
    LiveData<List<Transaction>> getTransactionsForAccount(int accountId, int currencyId);

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND syncStatus != 'DELETED'  ORDER BY timestamp DESC")
    LiveData<List<Transaction>> getLiveTransactionsForAccount(int accountId);

    // تم التعديل: currencyId بدلاً من currency
    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND currencyId = :currencyId AND syncStatus != 'DELETED'  ORDER BY timestamp,lastModified DESC")
    LiveData<List<Transaction>> getLiveTransactionsForAccount(int accountId, int currencyId);

    // تم التعديل: currencyId بدلاً من currency
    @Query("SELECT SUM(amount) FROM transactions " +
            "WHERE currencyId = :currencyId AND type = 1 AND syncStatus != 'DELETED'  AND accountId IN " +
            "(SELECT id FROM accounts WHERE ownerUID = :ownerUID)")
    LiveData<Double> getTotalDebitForUser(String ownerUID, int currencyId);

    // تم التعديل: currencyId بدلاً من currency
    @Query("SELECT SUM(amount) FROM transactions " +
            "WHERE currencyId = :currencyId AND type = -1 AND syncStatus != 'DELETED'  AND accountId IN " +
            "(SELECT id FROM accounts WHERE ownerUID = :ownerUID)")
    LiveData<Double> getTotalCreditForUser(String ownerUID, int currencyId);

    @Query("SELECT * FROM transactions WHERE syncStatus IN ('NEW', 'EDITED','DELETED')")
    List<Transaction> getUnsyncedTransactions();

    @Query("SELECT * FROM transactions WHERE syncStatus IN ('DELETED')")
    List<Transaction> getDeletedTransactions();

    @Query("SELECT * FROM transactions WHERE id = :transactionId AND syncStatus != 'DELETED'  LIMIT 1")
    Transaction getTransactionByIdBlocking(int transactionId);

    // تم التعديل: currencyId بدلاً من currency
    @Query("UPDATE transactions SET " +
            "amount = :amount, " +
            "details = :details, " +
            "currencyId = :currencyId, " +
            "currencyFirestoreId = :currencyFirestoreId, " +
            "type = :type, " +
            "timestamp = :timestamp, " +
            "syncStatus = CASE WHEN syncStatus = 'NEW' THEN 'NEW' ELSE 'EDITED' END, " +
            "lastModified = :lastModified " +
            "WHERE id = :id")
    void updateUserChanges(int id, double amount, String details, int currencyId, int type, long timestamp, long lastModified,String  currencyFirestoreId);

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    int deleteById(int transactionId);

    @Query("SELECT DISTINCT details FROM transactions WHERE accountId = :accountId AND details LIKE :query || '%'")
    LiveData<List<String>> getDetailSuggestions(int accountId, String query);

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND syncStatus != 'DELETED' ")
    List<Transaction> getTransactionsForAccountBlocking(int accountId);

    @Query("SELECT * FROM transactions WHERE accountId = :accountId")
    List<Transaction> getImportTransactionsForAccountBlocking(int accountId);

    // تم التعديل: currencyId بدلاً من currency
    @Query("SELECT SUM(amount) FROM transactions " +
            "WHERE accountId = :accountId AND currencyId = :currencyId AND type = 1 AND syncStatus != 'DELETED' ")
    LiveData<Double> getDebitForAccount(int accountId, int currencyId);

    // تم التعديل: currencyId بدلاً من currency
    @Query("SELECT SUM(amount) FROM transactions WHERE accountId = :accountId AND currencyId = :currencyId AND type = -1  AND syncStatus != 'DELETED' ")
    LiveData<Double> getCreditForAccount(int accountId, int currencyId);

    @Query("SELECT COUNT(*) FROM transactions WHERE syncStatus IN ('NEW', 'EDITED')")
    int getUnsyncedCount();

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId  AND syncStatus != 'DELETED' ")
    int getTransactionCountForAccount(int accountId);

    @Query("UPDATE transactions SET syncStatus = :status, lastModified = :timestamp WHERE id = :id")
    int updateSyncStatus(int id, String status, long timestamp);

    // تم التعديل: currencyId بدلاً من currency
    @Query("SELECT * FROM transactions" +
            " WHERE accountId = :accountId AND currencyId = :currencyId  AND syncStatus != 'DELETED'  AND details LIKE '%' || :searchQuery || '%' ORDER BY timestamp,lastModified ASC")
    LiveData<List<Transaction>> getTransactionsForAccount(int accountId, int currencyId, String searchQuery);

    @Query("SELECT * FROM transactions WHERE firestoreId = :firestoreId  AND syncStatus != 'DELETED'  LIMIT 1")
    Transaction getTransactionByFirestoreId(String firestoreId);

    @Query("DELETE FROM transactions WHERE firestoreId = :firestoreId")
    void deleteByFirestoreId(String firestoreId);

    @Upsert
    void upsert(Transaction transaction);

    @Query("SELECT DISTINCT c.name FROM transactions tx JOIN currencies c ON tx.currencyId = c.id " +
            "WHERE tx.accountId IN (SELECT id FROM accounts WHERE ownerUID = :ownerUID) AND tx.syncStatus != 'DELETED'")
    LiveData<List<String>> getCurrenciesWithTransactions(String ownerUID);
    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND currencyId = :currency AND syncStatus != 'DELETED'  ORDER BY timestamp,lastModified ASC")
    LiveData<List<Transaction>> getLiveTransactionsForAccount(int accountId, String currency);

    @Query("SELECT DISTINCT c.name FROM transactions tx JOIN currencies c ON tx.currencyId = c.id WHERE tx.accountId = :accountId AND tx.syncStatus != 'DELETED'")
    LiveData<List<String>> getActiveCurrenciesForAccount(int accountId);

    @Query("SELECT SUM(amount) FROM transactions " +
            "WHERE accountId = :accountId AND currencyId = :currencyId  AND syncStatus != 'DELETED' AND type = 1")
    Double getDebitForAccountBlocking(int accountId, int currencyId);

    @Query("SELECT SUM(amount) FROM transactions " +
            "WHERE accountId = :accountId AND currencyId = :currencyId  AND syncStatus != 'DELETED'  AND type = -1")
    Double getCreditForAccountBlocking(int accountId, int currencyId);

    // تم التعديل: currencyId بدلاً من currency
    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND syncStatus != 'DELETED'  AND currencyId = :currencyId ORDER BY timestamp ASC")
    List<Transaction> getTransactionsForAccountBlocking(int accountId, int currencyId);

    // ... باقي الدوال التي لا تتعامل مع العملة تبقى كما هي ...

    // تم التعديل: JOIN مع جدول العملات لجلب الاسم والتجميع بالرقم
    @Query("SELECT c.name as currency, " +
            "SUM(CASE WHEN tx.type = 1 THEN tx.amount ELSE 0 END) as totalDebit, " +
            "SUM(CASE WHEN tx.type = -1 THEN tx.amount ELSE 0 END) as totalCredit " +
            "FROM transactions tx JOIN currencies c ON tx.currencyId = c.id " +
            "WHERE tx.accountId = :accountId AND tx.syncStatus != 'DELETED' " +
            "GROUP BY tx.currencyId")
    List<AccountTotalsByCurrency> getAccountTotalsByCurrency(int accountId);

    @Query("SELECT * FROM transactions WHERE firestoreId = :firestoreId AND importId != 0 LIMIT 1")
    Transaction getTransactionByImportIdBlocking(String firestoreId);
  @Query("SELECT * FROM transactions WHERE firestoreId = :firestoreId AND  ownerUID = :ownerUID LIMIT 1")
  boolean existsByFirestoreId(String firestoreId,String ownerUID);
  @Query("UPDATE transactions SET syncStatus = :status, ownerUID = :ownerUID WHERE syncStatus != 'DELETED'")
  void upgradeToOfficialUser(String ownerUID, String status);
  @Query("DELETE FROM transactions ")
  void deleteGuestData();
  @Query("SELECT COUNT(*) FROM transactions WHERE lastModified < :timeThreshold")
  int getTransactionsCountAfter(long timeThreshold);

  // الحصول على عدد المعاملات الإجمالي
  @Query("SELECT COUNT(*) FROM transactions")
  int getTransactionsCount();
}

