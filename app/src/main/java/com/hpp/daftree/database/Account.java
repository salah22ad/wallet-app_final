
package com.hpp.daftree.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.google.firebase.auth.FirebaseAuth;

@Entity(tableName = "accounts")
public class Account {

    @PrimaryKey(autoGenerate = true)
    private int id;
    @NonNull
    private String firestoreId = "";
    private String syncStatus; // "NEW", "EDITED", "SYNCED"
    private long lastModified;

    @NonNull
    private String ownerUID = FirebaseAuth.getInstance().getCurrentUser() != null ?
            FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

    private String accountName;
    private String phoneNumber;
    private String accountType;
    @NonNull
  private String acTypeFirestoreId = "";

//    private String accountNumber;

    /**
     * مُنشئ فارغ. Room يتطلب هذا لإنشاء نسخ من الكائن.
     */
    public Account() {
    }

    // --- Getters ---
    public int getId() {
        return id;
    }
    public void setId(int id) {this.id = id;}
    @NonNull
    public String getOwnerUID() {
        return ownerUID;
    }
    public void setOwnerUID(@NonNull String ownerUID) {
        this.ownerUID = ownerUID;
    }
//    public String getAccountNumber() {
//        return accountNumber;
//    }
//    public void setAccountNumber(String accountNumber) {
//        this.accountNumber = accountNumber;
//    }

    public String getAccountName() {
        return accountName;
    }
    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }
    public String getPhoneNumber() {
        return phoneNumber;
    }
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    public String getAccountType() {
        return accountType;
    }
    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }
    // --- Setters ---
    @NonNull
   public String getFirestoreId() { return firestoreId; }
    public void setFirestoreId(@NonNull String firestoreId) { this.firestoreId = firestoreId; }

    public String getSyncStatus() { return syncStatus; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
    @NonNull
    public String getAcTypeFirestoreId() { return acTypeFirestoreId; }
    public void setAcTypeFirestoreId(@NonNull String acTypeFirestoreId) { this.acTypeFirestoreId = acTypeFirestoreId; }

    public long getLastModified() { return lastModified; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }

}