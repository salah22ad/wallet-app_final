package com.hpp.daftree.database;

import android.util.Log;

import androidx.room.TypeConverter;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

public class Converters {
    private static final Gson gson = new Gson();

    @TypeConverter
    public static Date fromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }


    @TypeConverter
    public static Boolean fromInt(Integer value) {
        Log.e("AppDatabase", "Boolean fromInt: " + value);
        return value != null && value != 0;
    }

    @TypeConverter
    public static Integer toInt(Boolean value) {
        Log.e("AppDatabase", "Integer toInt: " + value);
        return (value != null && value) ? 1 : 0;
    }

    @TypeConverter
    public static String fromDeviceInfoMap(Map<String, DeviceInfo> deviceInfoMap) {
        if (deviceInfoMap == null) {
            return null;
        }
        return gson.toJson(deviceInfoMap);
    }

    @TypeConverter
    public static Map<String, DeviceInfo> toDeviceInfoMap(String json) {
        if (json == null) {
            return Collections.emptyMap();
        }
        Type mapType = new TypeToken<Map<String, DeviceInfo>>() {
        }.getType();
        return gson.fromJson(json, mapType);
    }

}