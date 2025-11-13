package com.hpp.daftree.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(User user);
    @Query("SELECT * FROM user_profile WHERE id = 1")
    LiveData<User> getUserProfile();

    @Query("SELECT * FROM user_profile WHERE id = 1")
    User getUserProfileBlocking();
    @Query("SELECT * FROM user_profile WHERE email = :email LIMIT 1")
    User getAccountTypeByFirestoreEmaile(String email);
}