package com.hpp.daftree.database;

import android.util.Log;

import com.google.firebase.Timestamp;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class DeviceInfo implements Serializable {
    private String deviceId;
    private String deviceName;
    private String deviceModel;
    private String androidVersion;
    private boolean active;

private Object registeredAt; // تغيير إلى Object
    private Object lastActiveAt;

    public DeviceInfo() {
        // Required empty constructor for Firestore
    }

    // Getters and Setters
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getDeviceModel() { return deviceModel; }
    public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }

    public String getAndroidVersion() { return androidVersion; }
    public void setAndroidVersion(String androidVersion) { this.androidVersion = androidVersion; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

//    public Date getRegisteredAt() { return registeredAt; }
//    public void setRegisteredAt(Date registeredAt) { this.registeredAt = registeredAt; }
//
//    public Date getLastActiveAt() { return lastActiveAt; }
//    public void setLastActiveAt(Date lastActiveAt) { this.lastActiveAt = lastActiveAt; }
public String getRegisteredAt() {
    return convertTimestampToString(registeredAt);
}

    public void setRegisteredAt(Object registeredAt) {
        this.registeredAt = registeredAt;
    }

    public String getLastActiveAt() {
        return convertTimestampToString(lastActiveAt);
    }

    public void setLastActiveAt(Object lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    private String convertTimestampToString(Object timestamp) {
        try {
            if (timestamp instanceof Timestamp) {
                Date date = ((Timestamp) timestamp).toDate();
                return formatDate(date);
            } else if (timestamp instanceof String) {
                return (String) timestamp;
            } else if (timestamp instanceof Date) {
                return formatDate((Date) timestamp);
            }
        } catch (Exception e) {
            Log.e("convertTimestampToString",e.toString());
        }
        return "";
    }

    private String formatDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy 'at' hh:mm:ss a z", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(date);
    }
    public String getDisplayName() {
        if (deviceName != null && !deviceName.trim().isEmpty()) {
            return deviceName;
        }
        return deviceModel != null ? deviceModel : "";
    }
    public static String getCurrentLocalDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a z", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date());
    }


    // دالة مساعدة لتحويل الكائن إلى Map لإرساله إلى Firestore
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("deviceId", deviceId);
        map.put("deviceName", deviceName);
        map.put("deviceModel", deviceModel);
        map.put("androidVersion", androidVersion);
        map.put("active", active);
        // عند الإرسال، استخدم FieldValue.serverTimestamp() بدلاً من إرسال القيمة مباشرة
        map.put("registeredAt", registeredAt);
        map.put("lastActiveAt", lastActiveAt);
        return map;
    }


}