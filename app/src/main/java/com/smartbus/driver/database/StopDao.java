package com.smartbus.driver.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface StopDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<StopEntity> entities);

    @Query("SELECT * FROM stops WHERE routeId = :routeId ORDER BY stopOrder ASC")
    List<StopEntity> findByRouteId(long routeId);

    @Query("DELETE FROM stops WHERE routeId = :routeId")
    void deleteByRouteId(long routeId);

    @Query("DELETE FROM stops")
    void clear();
}
