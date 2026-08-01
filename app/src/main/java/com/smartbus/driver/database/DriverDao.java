package com.smartbus.driver.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface DriverDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(DriverEntity entity);

    @Query("SELECT * FROM drivers WHERE id = :id LIMIT 1")
    DriverEntity findById(long id);

    @Query("DELETE FROM drivers")
    void clear();
}
