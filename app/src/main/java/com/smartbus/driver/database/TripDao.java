package com.smartbus.driver.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface TripDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(TripEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<TripEntity> entities);

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    TripEntity findById(long id);

    @Query("SELECT * FROM trips WHERE status = :status LIMIT 1")
    TripEntity findByStatus(String status);

    @Query("SELECT * FROM trips WHERE routeId = :routeId AND status = :status ORDER BY startedAt DESC LIMIT 1")
    TripEntity findByRouteAndStatus(long routeId, String status);

    @Query("SELECT * FROM trips WHERE pendingStartSync = 1 OR pendingEndSync = 1 ORDER BY startedAt ASC")
    List<TripEntity> findPendingSync();

    @Query("UPDATE trips SET currentLatitude = :latitude, currentLongitude = :longitude WHERE id = :id")
    void updateLocation(long id, double latitude, double longitude);

    @Query("UPDATE trips SET currentStopId = :stopId WHERE id = :id")
    void updateCurrentStop(long id, Long stopId);

    @Query("SELECT * FROM trips ORDER BY startedAt DESC")
    List<TripEntity> getAll();

    @Query("DELETE FROM trips")
    void clear();

    @Query("DELETE FROM trips WHERE id = :id")
    void deleteById(long id);
}
