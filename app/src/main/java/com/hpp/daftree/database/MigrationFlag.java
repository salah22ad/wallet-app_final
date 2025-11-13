package com.hpp.daftree.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "migration_flags")
public class MigrationFlag {
    @PrimaryKey
    @NonNull
    public String flag_key;
    public int value; // 0 or 1

    public MigrationFlag(@NonNull String flag_key, int value) {
        this.flag_key = flag_key;
        this.value = value;
    }
}

