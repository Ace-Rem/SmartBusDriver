package com.smartbus.driver.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "passenger_records")
public class PassengerRecordEntity {

    @PrimaryKey
    public long id;

    public long tripId;
    public Long stopId;
    public Long boardingStopId;
    public Long destinationStopId;
    public Double destinationLatitude;
    public Double destinationLongitude;
    public String passengerIdentifier;
    public String status;
    public int passengerCount;
    public String recordedAt;
    public String note;
    public String source;
    public String idempotencyKey;
    public boolean pendingSync;
}
