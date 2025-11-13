package com.hpp.daftree.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface MigrationFlagDao {
    @Query("SELECT value FROM migration_flags WHERE flag_key = :flag_key LIMIT 1")
    Integer getFlagValue(String flag_key);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertFlag(MigrationFlag flag);

    @Query("UPDATE migration_flags SET value = :value WHERE flag_key = :flag_key")
    void setFlag(String flag_key, int value);
}

