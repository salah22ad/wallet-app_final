package com.hpp.daftree.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DeletionLogDao {
    @Insert
    void insert(DeletionLog deletionLog);

    @Query("SELECT * FROM deletion_log")
    List<DeletionLog> getAllPendingDeletions();

    @Query("DELETE FROM deletion_log WHERE firestoreId = :firestoreId")
    void deleteByFirestoreId(String firestoreId);
    @Query("SELECT COUNT(*) FROM deletion_log")
    int getDeletedUnsyncedCount();
}