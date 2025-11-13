package com.hpp.daftree.database;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.firebase.firestore.Exclude;

@Entity(tableName = "account_types")
public class AccountType {
    @PrimaryKey(autoGenerate = true)

    @Exclude
    public int id;

    public String name;

    // حقول المزامنة
    private String firestoreId;
    private String ownerUID;
    private long lastModified;
    private String syncStatus;
    private boolean isDefault =false;

    public AccountType() {}
    @Ignore
    public AccountType(String name) {
        this.name = name;}
    // Getters and Setters for all fields...
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFirestoreId() { return firestoreId; }
    public void setFirestoreId(String firestoreId) { this.firestoreId = firestoreId; }
    public String getOwnerUID() { return ownerUID; }
    public void setOwnerUID(String ownerUID) { this.ownerUID = ownerUID; }
    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }
    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }

}