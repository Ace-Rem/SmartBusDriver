package com.smartbus.driver.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface PassengerRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PassengerRecordEntity entity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<PassengerRecordEntity> entities);

    @Query("SELECT * FROM passenger_records WHERE tripId = :tripId ORDER BY recordedAt DESC")
    List<PassengerRecordEntity> findByTripId(long tripId);

    @Query("SELECT COALESCE(SUM(passengerCount), 0) FROM passenger_records WHERE tripId = :tripId")
    int sumPassengerCount(long tripId);

    @Query("DELETE FROM passenger_records WHERE tripId = :tripId")
    void deleteByTripId(long tripId);

    @Query("DELETE FROM passenger_records")
    void clear();
}
