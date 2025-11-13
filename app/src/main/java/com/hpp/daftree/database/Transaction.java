package com.hpp.daftree.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.google.firebase.auth.FirebaseAuth;

import java.util.Date;

@Entity(tableName = "transactions",
        indices = {
                @Index(value = "accountId"),
                @Index(value = "currencyId") // <-- فهرس جديد لتسريع البحث
        },
        foreignKeys = @ForeignKey(entity = Currency.class, // <-- تعريف العلاقة مع جدول العملات
                parentColumns = "id",
                childColumns = "currencyId",
                onDelete = ForeignKey.RESTRICT)) // <-- منع حذف عملة مستخدمة
public class Transaction {

    @PrimaryKey(autoGenerate = true)
    private int id;
    private int accountId;
    private int type;

    @ColumnInfo(name = "amount")
    private double amount;
    private String details;
    private int currencyId; // <-- تم التغيير من String currency
    private Date timestamp;
    private String firestoreId;
    private String syncStatus;
    private long lastModified;
    private int importID=0;
    private String billType;
    @NonNull
    private String accountFirestoreId = "";
    @NonNull
    private String currencyFirestoreId = "";
    @NonNull
    private String ownerUID = FirebaseAuth.getInstance().getCurrentUser() != null ?
            FirebaseAuth.getInstance().getCurrentUser().getUid() : "";


    // --- Getters ---
    public int getId() { return id; }
    public int getAccountId() { return accountId; }
    public int getType() { return type; }
    public double getAmount() { return amount; }
    public String getDetails() { return details; }
    public int getCurrencyId() { return currencyId; } // <-- تم التحديث
    public Date getTimestamp() { return timestamp; }
    public String getFirestoreId() { return firestoreId; }
    public String getSyncStatus() { return syncStatus; }
    public long getLastModified() { return lastModified; }
    @NonNull
    public String getAccountFirestoreId() { return accountFirestoreId; }
    @NonNull
    public String getOwnerUID() { return ownerUID; }
    public String getBillType() { return billType; }


    // --- Setters ---
    public void setId(int id) { this.id = id; }
    public void setAccountId(int accountId) { this.accountId = accountId; }
    public void setType(int type) { this.type = type; }
    public void setAmount(double amount) { this.amount = amount; }
    public void setDetails(String details) { this.details = details; }
    public void setCurrencyId(int currencyId) { this.currencyId = currencyId; } // <-- تم التحديث
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
    public void setFirestoreId(String firestoreId) { this.firestoreId = firestoreId; }
    public void setSyncStatus(String syncStatus) { this.syncStatus = syncStatus; }
    public void setLastModified(long lastModified) { this.lastModified = lastModified; }
    public void setAccountFirestoreId(@NonNull String accountFirestoreId) { this.accountFirestoreId = accountFirestoreId; }
    public void setOwnerUID(@NonNull String ownerUID) { this.ownerUID = ownerUID; }
    public int getImportID() {
        return (importID <= 0) ? 0 : importID;
    }

    public void setImportID(int importID) {
        this.importID = (importID <= 0) ? 0 : importID;
    }
    public void setBillType(String billType) { this.billType = billType; }
public void setCurrencyFirestoreId( @NonNull String currencyFirestoreId) {this.currencyFirestoreId = currencyFirestoreId;}
    @NonNull
public String getCurrencyFirestoreId(){return currencyFirestoreId; }

}