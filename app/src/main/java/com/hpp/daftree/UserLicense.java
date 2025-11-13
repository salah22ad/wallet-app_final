package com.hpp.daftree;

import com.google.firebase.firestore.PropertyName;
import com.hpp.daftree.database.DeviceInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * نموذج ترخيص المستخدم لحفظ بيانات الترخيص في Firebase Firestore
 */
public class UserLicense {
//    private String userId;
//    private String email;
//    private String displayName;
    private String uniqueCode;
//    private boolean isPremium;
    private long quotesCount;
    private long maxQuotes;
    private List<DeviceInfo> authorizedDevices;
//    private long createdAt;
//    private long updatedAt;
    private String userId;
    private String email;
    @PropertyName("displayName")
    private String displayName;
    private String photoUrl;
    private String userUID;
    private String purchaseCode;

    @PropertyName("isActive")
    private boolean isActive;

    @PropertyName("isPremium")
    private boolean isPremium;

    @PropertyName("max_devices")
    private int maxDevices;

    @PropertyName("loginCount")
    private int loginCount;

    @PropertyName("createdAt")
    private String createdAt;

    @PropertyName("updatedAt")
    private String updatedAt;

    @PropertyName("last_login")
    private String lastLogin;
    public String getLastLogin() { return lastLogin; }
    public void setLastLogin(String lastLogin) { this.lastLogin = lastLogin; }

    @PropertyName("lastLogout")
    private String lastLogout;
    @PropertyName("devices")
    private Map<String, DeviceInfo> devices;
    public Map< String ,DeviceInfo> getDevices() { return devices; }
    public void setDevices(Map<String ,DeviceInfo> devices) { this.devices = devices; }

    // Getters and Setters for all fields
    // تأكد من استخدام @PropertyName للحقول التي تحتوي على شرطة سفلية

    @PropertyName("isActive")
    public boolean isActive() {
        return isActive;
    }

    @PropertyName("isActive")
    public void setActive(boolean active) {
        isActive = active;
    }
    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss z", Locale.getDefault());
        // لا نغير الـ TimeZone، يظل على الإعداد المحلي للجهاز
        return sdf.format(new Date());
    }
    public UserLicense() {
        // Required empty constructor for Firestore
    }
    
    public UserLicense(String userId, String email, String displayName, String uniqueCode) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.uniqueCode = uniqueCode;
        this.isPremium = false;
        this.quotesCount = 0;
        this.maxQuotes = 6; // Free plan limit
        this.createdAt = getCurrentTimestamp();
        this.updatedAt = getCurrentTimestamp();
    }

    public UserLicense(String email, String displayName, String currentDeviceId) {
        this.email = email;
        this.displayName = displayName;
        this.uniqueCode = currentDeviceId;
    }

    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    
    public String getUniqueCode() { return uniqueCode; }
    public void setUniqueCode(String uniqueCode) { this.uniqueCode = uniqueCode; }
    @PropertyName("isPremium")
    public boolean isPremium() { return isPremium; }
    @PropertyName("isPremium")
    public void setPremium(boolean premium) { isPremium = premium; }
    
    public long getQuotesCount() { return quotesCount; }
    public void setQuotesCount(long quotesCount) { this.quotesCount = quotesCount; }
    
    public long getMaxQuotes() { return maxQuotes; }
    public void setMaxQuotes(long maxQuotes) { this.maxQuotes = maxQuotes; }
    
    public List<DeviceInfo> getAuthorizedDevices() { return authorizedDevices; }
    public void setAuthorizedDevices(List<DeviceInfo> authorizedDevices) { this.authorizedDevices = authorizedDevices; }
    
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
   
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    
    /**
     * فحص إمكانية إنشاء عرض سعر جديد
     */
    public boolean canCreateQuote() {
        if (isPremium) {
            return true; // Premium users have unlimited quotes
        }
        return quotesCount < maxQuotes;
    }
    
    /**
     * زيادة عداد عروض الأسعار
     */
    public void incrementQuoteCount() {
        this.quotesCount++;
        this.updatedAt = getCurrentTimestamp();
    }
    
    /**
     * إضافة نقاط مجانية (من مشاهدة الإعلانات)
     */
    public void addFreeQuotes(int count) {
        if (!isPremium) {
            this.maxQuotes += count;
            this.updatedAt = getCurrentTimestamp();
        }
    }
    
    /**
     * تفعيل الحساب المميز
     */
    public void activatePremium() {
        this.isPremium = true;
        this.maxQuotes = Long.MAX_VALUE; // Unlimited for premium
        this.updatedAt = getCurrentTimestamp();
    }
    
    /**
     * فحص إمكانية إضافة جهاز جديد
     */
    public boolean canAddDevice() {
        return authorizedDevices == null || authorizedDevices.size() < 2;
    }
}

