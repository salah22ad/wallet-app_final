
package com.hpp.daftree.database;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "currencies")
public class Currency {
    @PrimaryKey(autoGenerate = true)
//    @Exclude // نستبعد ID المحلي من الحفظ في Firestore
    public int id;
    public String name;
    public String code;
    public String symbol;
    private String firestoreId; // ID الخاص بالمستند في Firestore
    private String ownerUID;    // لربط العملة بالمستخدم الحالي
    private long lastModified;
    private String syncStatus;  // "NEW", "EDITED", "SYNCED"
    private boolean isDefault = false;


    public Currency() {} // مُنشئ فارغ مطلوب
    @Ignore
    public Currency(String name) {
        this.name = name;
    }
    @Ignore
    public Currency(String name, boolean isDefault) {
        this.name = name;
        this.isDefault = isDefault;
    }
    // Getters and Setters for new fields...
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
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