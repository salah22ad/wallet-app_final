package com.hpp.daftree.database;

import android.util.Log;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

@Entity(tableName = "user_profile")
public class User {
    @PrimaryKey
    private int id = 1;
    private String name;
    private String email;
    private String address;
    private String phone;
    private String ownerUID;
    private String password;
    private String company;
    private String profileImageUri;
    private String syncStatus;
    @Ignore
    private String userType;
    private long lastModified;

    @ServerTimestamp
    private Date createdAt;

    @Ignore
    private boolean is_active;
    @Ignore
    private boolean is_premium;
    @Ignore
    private int max_devices;
    @Ignore
    private String app_Version;
    @Ignore
    private int successfulReferrals;
    @Ignore
    private int transactions_count = 0; // <-- تم التعديل

    @Ignore
    private int ad_rewards = 0; // <-- حقل جديد لمكافآت الإعلانات
    @Ignore
    private int db_upgrade;
    @Ignore
    private int referral_rewards = 0; // <-- حقل جديد لمكافآت الدعوة

    @Ignore
    private int max_transactions;
    @Ignore
    private long login_count;
    @Ignore
    private Object last_login;
    @Ignore
    private Object last_logout;
    @Ignore
    private Object created_at;
    @Ignore
    private Object updated_at;
    @Ignore
    @PropertyName("devices")
    private Map<String, DeviceInfo> devices = new HashMap<>();
    @Ignore
    private String fcmToken;
    @Ignore
    private String guestUID;
    @Ignore
   public User(String name, String company, String address, String phone, String email, String ownerUID) {
        this.name = name;
        this.company = company;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.ownerUID = ownerUID;
    }

    public User() {

    }


    // --- Getters & Setters ---
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    @Exclude
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getGuestUID() {
        return guestUID;
    }

    public void setGuestUID(String guestUID) {
        this.guestUID = guestUID;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    @Exclude
    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    @Exclude
    public Date getCreatedAt() {
        return createdAt;
    }

    @Exclude
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    @Exclude
    public String getProfileImageUri() {
        return profileImageUri;
    }

    public void setProfileImageUri(String profileImageUri) {
        this.profileImageUri = profileImageUri;
    }

    public String getOwnerUID() {
        return ownerUID;
    }

    public void setOwnerUID(String ownerUID) {
        this.ownerUID = ownerUID;
    }

    public boolean isIs_active() {
        return is_active;
    }

    public void setIs_active(boolean is_active) {
        this.is_active = is_active;
    }

    public boolean isIs_premium() {
        return is_premium;
    }

    public void setIs_premium(boolean is_premium) {
        this.is_premium = is_premium;
    }

    public int getMax_devices() {
        return max_devices;
    }

    public void setMax_devices(int max_devices) {
        this.max_devices = max_devices;
    }
    public String getApp_Version() {
        return app_Version;
    }

    public void setApp_Version(String app_Version) {
        this.app_Version = app_Version;
    }

    public int getSuccessfulReferrals() {
        return successfulReferrals;
    }

    public void setSuccessfulReferrals(int successfulReferrals) {
        this.successfulReferrals = successfulReferrals;
    }

    public int getTransactions_count() {
        return transactions_count;
    }

    public void setTransactions_count(int transactions_count) {
        this.transactions_count = transactions_count;
    }

    public int getAd_rewards() {
        return ad_rewards;
    }

    public void setAd_rewards(int ad_rewards) {
        this.ad_rewards = ad_rewards;
    }


    public int getReferral_rewards() {
        return referral_rewards;
    }

    public void setReferral_rewards(int referral_rewards) {
        this.referral_rewards = referral_rewards;
    }

    public int getDb_upgrade() {
        return db_upgrade;
    }

    public void setDb_upgrade(int db_upgrade) {
        this.db_upgrade = db_upgrade;
    }

    public int getMax_transactions() {
        return max_transactions;
    }

    public void setMax_transactions(int max_transactions) {
        this.max_transactions = max_transactions;
    }

    public long getLogin_count() {
        return login_count;
    }

    public void setLogin_count(long login_count) {
        this.login_count = login_count;
    }

    public Map<String, DeviceInfo> getDevices() {
        return devices;
    }

    public void setDevices(Map<String, DeviceInfo> devices) {
        this.devices = devices;
    }

    // ... (بقية الدوال المساعدة بدون تغيير)
    public String getLast_login() {
        return convertTimestampToString(last_login);
    }

    public void setLast_login(Object last_login) {
        this.last_login = last_login;
    }

    public String getUpdated_at() {
        return convertTimestampToString(updated_at);
    }

    public void setUpdated_at(Object updated_at) {
        this.updated_at = updated_at;
    }

    public String getLast_logout() {
        return convertTimestampToString(last_logout);
    }

    public void setLast_logout(Object last_logout) {
        this.last_logout = last_logout;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    private String convertTimestampToString(Object timestamp) {
        try {
            if (timestamp instanceof Timestamp) {
                return formatDate(((Timestamp) timestamp).toDate());
            } else if (timestamp instanceof String) {
                return (String) timestamp;
            } else if (timestamp instanceof Date) {
                return formatDate((Date) timestamp);
            }
        } catch (Exception e) {
            Log.e("convertTimestampToString", e.toString());
        }
        return "";
    }

    public String getCreated_at() {
        return convertTimestampToString(created_at);
    }

    public void setCreated_at(Object created_at) {
        this.created_at = created_at;
    }

    private String formatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a z", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(date);
    }

    public static String getCurrentLocalDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a z", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date());
    }

    @Ignore
    private String deviceId;

    // Getter and Setter
    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
}