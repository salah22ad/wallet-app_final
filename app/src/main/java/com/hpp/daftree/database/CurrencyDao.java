
package com.hpp.daftree.database;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface CurrencyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Currency currency); // تم التعديل من insertAll إلى insert
    @Update
    void update(Currency currency);
    @Upsert
    void upsert(Currency currency);
    @Query("SELECT * FROM currencies WHERE syncStatus != 'DELETED' ORDER BY id ASC") // تم تعديل الاستعلام
    LiveData<List<Currency>> getAllCurrencies();

    @Query("SELECT * FROM currencies WHERE syncStatus != 'DELETED'  ORDER BY id ASC") // تم تعديل الاستعلام
    List<Currency> getAllCurrency();
    @Query("SELECT * FROM currencies WHERE syncStatus != 'DELETED' ORDER BY id ASC") // تم تعديل الاستعلام
    List<Currency> getAllCurrenciesBlocking();

    @Query("SELECT name FROM currencies ORDER BY id ASC") // تم تعديل الاستعلام
    List<String> getAllCurrencyNames();

    // دوال جديدة للمزامنة
    @Query("SELECT * FROM currencies WHERE syncStatus IN ('NEW', 'EDITED')")
    List<Currency> getUnsyncedCurrencies();

    @Query("UPDATE currencies SET syncStatus = :status, firestoreId = :firestoreId, lastModified = :timestamp WHERE id = :id")
    void updateSyncStatus(int id, String firestoreId, String status, long timestamp);

    @Query("SELECT COUNT(*) FROM currencies WHERE syncStatus IN ('NEW', 'EDITED')")
    int getUnsyncedCount();
    @Query("SELECT * FROM currencies WHERE firestoreId = :firestoreId LIMIT 1")
    Currency getCurrencyByFirestoreId(String firestoreId);

    @Query("DELETE FROM currencies WHERE firestoreId = :firestoreId")
    void deleteByFirestoreId(String firestoreId);
    @Query("SELECT id FROM currencies WHERE name = :name ORDER BY id LIMIT 1 ")
    int checkCurrencyByName(String name);
    @Query("SELECT COUNT(*) FROM transactions WHERE currencyId = :currencyId AND syncStatus != 'DELETED'")
    int getTransactionCountForCurrency(int currencyId);


    @Query("SELECT * FROM currencies WHERE name = :name LIMIT 1")
    Currency getCurrencyByName(String name);

    @Query("SELECT COUNT(*) FROM currencies")
    int getCurrencyCount();
    @Query("SELECT * FROM currencies WHERE syncStatus = 'DELETED'")
    List<Currency> getDeletedCurrencies();

    @Delete
    void delete(Currency currency);

    @Query("SELECT * FROM currencies WHERE name = :name LIMIT 1")
    Currency getCurrencyByNameBlocking(String name);
    @Query("SELECT * FROM currencies WHERE id = :currencyId LIMIT 1")
    Currency getCurrencyById(int currencyId);

    @Query("SELECT name FROM currencies WHERE id > 0 ORDER BY id ASC LIMIT 1")
    String getFirstCurrencyById();
    @Query("SELECT id FROM currencies WHERE id > 0 ORDER BY id ASC LIMIT 1")
    int getFirstIdCurrency();
    @Query("SELECT firestoreId FROM currencies WHERE id > 0 ORDER BY id ASC LIMIT 1")
    String getFirstFirestoreIdCurrency();
    @Query("UPDATE currencies SET syncStatus = :status, isDefault = :isDefault")
    void updateAllSyncStatus(String status, boolean isDefault);
    @Query("SELECT name FROM currencies WHERE id = :currencyId")
    String getCurrencyNameById(int currencyId);
    @Query("SELECT MAX(CAST(firestoreId AS LONG)) FROM currencies")
    Long getMaxFirestoreId();

    @Query("SELECT * FROM currencies WHERE firestoreId = :firestoreId")
    Currency getCurrencyByFirestoreIdForUUID(String firestoreId);
    @Query("SELECT * FROM currencies WHERE name = :name  LIMIT 1")
    boolean existsByName(String name);
    @Query("SELECT firestoreId FROM currencies WHERE id = :currencyId LIMIT 1")
    String getCurrencyFirestoreId(int currencyId);

    @Query("UPDATE currencies SET syncStatus = :status, ownerUID = :ownerUID WHERE syncStatus != 'DELETED'")
    void upgradeToOfficialUser(String ownerUID, String status);
    @Query("DELETE FROM currencies ")
    void deleteGuestData();
}