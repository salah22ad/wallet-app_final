package com.hpp.daftree.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AccountTypeDao {
    @Insert
    void insert(AccountType accountType);

    @Query("SELECT * FROM account_types WHERE syncStatus != 'DELETED' ORDER BY id ASC")
    LiveData<List<AccountType>> getAllAccountTypes();
    @Query("SELECT COUNT(*) FROM account_types")
    int getAccountTypeCount();
    // دوال المزامنة
    @Query("SELECT * FROM account_types WHERE syncStatus IN ('NEW', 'EDITED')")
    List<AccountType> getUnsyncedAccountTypes();
    @Query("SELECT COUNT(*) FROM account_types WHERE syncStatus IN ('NEW', 'EDITED')")
    int getUnsyncedCount();
    @Query("UPDATE account_types SET syncStatus = :status, firestoreId = :firestoreId, lastModified = :timestamp WHERE id = :id")
    void updateSyncStatus(int id, String firestoreId, String status, long timestamp);

    @Query("SELECT * FROM account_types WHERE firestoreId = :firestoreId LIMIT 1")
    AccountType getAccountTypeByFirestoreId(String firestoreId);
    @Query("SELECT * FROM account_types  WHERE syncStatus != 'DELETED' ORDER BY id ASC")
    List<AccountType> getAllAccountTypesBlockingRestore();
    @Query("SELECT * FROM account_types  WHERE syncStatus != 'DELETED' ORDER BY id ASC ")
    LiveData<List<AccountType>> getAllAccountTypesBlocking(); // تغيير نوع الإرجاع إلى LiveData

    @Query("SELECT * FROM currencies WHERE syncStatus != 'DELETED' ORDER BY id ASC") // تم تعديل الاستعلام
    List<Currency> getAllCurrenciesBlocking();

    @Query("SELECT * FROM account_types WHERE syncStatus != 'DELETED' ORDER BY id ASC ")
    List<AccountType> getAllAccountTypesBlockingReport(); // تغيير نوع الإرجاع إلى LiveData

    @Query("SELECT name FROM account_types WHERE syncStatus != 'DELETED' ORDER BY id ASC ")
    List<String> getAllAccountTypeNames();

    @Query("SELECT COUNT(*) FROM accounts WHERE accountType = :typeName AND syncStatus != 'DELETED'")
    int getAccountCountForType(String typeName);

    @Update
    void update(AccountType accountType);

    @Query("SELECT * FROM account_types WHERE name = :name LIMIT 1")
    AccountType getAccountTypeByName(String name);
    @Query("SELECT * FROM account_types WHERE name = :name LIMIT 1")
    boolean getAccountTypeByNameRestore(String name);


    @Query("SELECT * FROM account_types WHERE syncStatus = 'DELETED'")
    List<AccountType> getDeletedAccountTypes();

    @Delete
    void delete(AccountType accountType);
    @Query("DELETE FROM account_types WHERE firestoreId = :firestoreId")
    void deleteByFirestoreId(String firestoreId);
    @Query("SELECT * FROM account_types WHERE firestoreId = :firestoreId AND  ownerUID = :ownerUID LIMIT 1")
    boolean existsByFirestoreId(String firestoreId,String ownerUID);
    @Query("SELECT name FROM account_types WHERE firestoreId = :firestoreId LIMIT 1")
    String getAccountTypeNameByFirestoreId(String firestoreId);
    @Query("UPDATE account_types SET syncStatus = :status, ownerUID = :ownerUID WHERE syncStatus != 'DELETED'")
    void upgradeToOfficialUser(String ownerUID, String status);
    @Query("DELETE FROM account_types ")
    void deleteGuestData();
}
