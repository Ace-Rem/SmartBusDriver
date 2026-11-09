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

    @Query("SELECT * FROM passenger_records WHERE tripId = :tripId AND status = :status ORDER BY recordedAt DESC")
    List<PassengerRecordEntity> findByTripIdAndStatus(long tripId, String status);

    @Query("SELECT * FROM passenger_records WHERE tripId = :tripId AND destinationLatitude = :latitude AND destinationLongitude = :longitude AND status = :status ORDER BY recordedAt DESC")
    List<PassengerRecordEntity> findByDestinationCoordinatesAndStatus(long tripId, double latitude, double longitude, String status);

    @Query("SELECT * FROM passenger_records WHERE tripId = :tripId AND destinationLatitude = :latitude AND destinationLongitude = :longitude AND status IN ('ON_BOARD', 'ON_BOARD_UPDATE') ORDER BY recordedAt DESC")
    List<PassengerRecordEntity> findOnBoardByDestinationCoordinates(long tripId, double latitude, double longitude);

    @Query("SELECT * FROM passenger_records WHERE pendingSync = 1 ORDER BY recordedAt ASC")
    List<PassengerRecordEntity> findPendingSync();

    @Query("SELECT * FROM passenger_records WHERE tripId = :tripId AND idempotencyKey = :idempotencyKey LIMIT 1")
    PassengerRecordEntity findByTripAndIdempotencyKey(long tripId, String idempotencyKey);

    @Query("SELECT * FROM passenger_records WHERE tripId = :tripId AND passengerIdentifier = :passengerIdentifier AND destinationStopId = :destinationStopId AND status = 'PENDING' LIMIT 1")
    PassengerRecordEntity findPendingByPassengerIdentifier(long tripId, String passengerIdentifier, Long destinationStopId);

    @Query("SELECT * FROM passenger_records WHERE tripId = :tripId AND passengerIdentifier = :passengerIdentifier AND status IN ('PENDING', 'ON_BOARD', 'ON_BOARD_UPDATE') ORDER BY recordedAt DESC LIMIT 1")
    PassengerRecordEntity findActiveByPassengerIdentifier(long tripId, String passengerIdentifier);

    @Query("SELECT * FROM passenger_records WHERE tripId = :tripId AND destinationStopId = :destinationStopId AND source = :source AND status = 'PENDING' ORDER BY recordedAt ASC LIMIT 1")
    PassengerRecordEntity findPendingByTripAndDestinationAndSource(
            long tripId, Long destinationStopId, String source);

    @Query("SELECT * FROM passenger_records WHERE tripId = :tripId AND destinationStopId = :destinationStopId AND status = 'PENDING' ORDER BY recordedAt ASC LIMIT 1")
    PassengerRecordEntity findPendingByTripAndDestination(long tripId, Long destinationStopId);

    @Query("SELECT * FROM passenger_records WHERE tripId = :tripId AND status IN ('PENDING', 'ON_BOARD_UPDATE') ORDER BY recordedAt DESC")
    List<PassengerRecordEntity> findBoardingRequests(long tripId);

    @Query("SELECT * FROM passenger_records WHERE id = :id LIMIT 1")
    PassengerRecordEntity findById(long id);

    @Query("SELECT COALESCE(SUM(passengerCount), 0) FROM passenger_records WHERE tripId = :tripId")
    int sumPassengerCount(long tripId);

    @Query("SELECT COALESCE(SUM(passengerCount), 0) FROM passenger_records WHERE tripId = :tripId AND status = :status")
    int sumPassengerCountByStatus(long tripId, String status);

    @Query("SELECT COALESCE(SUM(passengerCount), 0) FROM passenger_records WHERE tripId = :tripId AND status IN ('ON_BOARD', 'ON_BOARD_UPDATE')")
    int sumPassengerCountOnBoard(long tripId);

    @Query("SELECT COALESCE(SUM(passengerCount), 0) FROM passenger_records WHERE tripId = :tripId AND destinationStopId = :destinationStopId AND status IN ('ON_BOARD', 'ON_BOARD_UPDATE')")
    int sumPassengerCountOnBoardByDestination(long tripId, long destinationStopId);

    @Query("SELECT COALESCE(SUM(passengerCount), 0) FROM passenger_records WHERE tripId = :tripId AND destinationStopId = :destinationStopId AND status = :status")
    int sumPassengerCountByDestinationAndStatus(long tripId, long destinationStopId, String status);

    @Query("SELECT COALESCE(SUM(passengerCount), 0) FROM passenger_records WHERE tripId = :tripId AND destinationLatitude = :latitude AND destinationLongitude = :longitude AND status = :status")
    int sumPassengerCountByDestinationCoordinatesAndStatus(long tripId, double latitude, double longitude, String status);

    @Query("SELECT COALESCE(SUM(passengerCount), 0) FROM passenger_records WHERE tripId = :tripId AND destinationLatitude = :latitude AND destinationLongitude = :longitude AND status IN ('ON_BOARD', 'ON_BOARD_UPDATE')")
    int sumPassengerCountOnBoardByDestinationCoordinates(long tripId, double latitude, double longitude);

    @Query("UPDATE passenger_records SET status = :status, pendingSync = 0 WHERE id = :id")
    void updateStatus(long id, String status);

    @Query("DELETE FROM passenger_records WHERE tripId = :tripId")
    void deleteByTripId(long tripId);

    @Query("DELETE FROM passenger_records")
    void clear();

    @Query("DELETE FROM passenger_records WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM passenger_records WHERE tripId = :tripId AND idempotencyKey = :idempotencyKey AND status = 'PENDING'")
    void deletePendingByTripAndIdempotencyKey(long tripId, String idempotencyKey);

    @Query("UPDATE passenger_records SET tripId = :newTripId, idempotencyKey = CASE WHEN idempotencyKey IS NULL THEN NULL ELSE REPLACE(idempotencyKey, :oldTripPrefix, :newTripPrefix) END WHERE tripId = :oldTripId")
    void migrateTripId(long oldTripId, long newTripId, String oldTripPrefix, String newTripPrefix);
}
