package com.hpp.daftree.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "deletion_log")
public class DeletionLog {
    @PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    private String firestoreId;

    @NonNull
    private String collectionName; // e.g., "accounts" or "transactions"

    // Constructor, Getters, Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    @NonNull
    public String getFirestoreId() { return firestoreId; }
    public void setFirestoreId(@NonNull String firestoreId) { this.firestoreId = firestoreId; }

    @NonNull
    public String getCollectionName() { return collectionName; }
    public void setCollectionName(@NonNull String collectionName) { this.collectionName = collectionName; }
}