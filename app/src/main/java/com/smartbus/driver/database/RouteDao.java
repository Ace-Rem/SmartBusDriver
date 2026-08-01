package com.smartbus.driver.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface RouteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<RouteEntity> entities);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(RouteEntity entity);

    @Query("SELECT * FROM routes ORDER BY code ASC")
    List<RouteEntity> getAll();

    @Query("SELECT * FROM routes WHERE id = :id LIMIT 1")
    RouteEntity findById(long id);

    @Query("DELETE FROM routes")
    void clear();
}
