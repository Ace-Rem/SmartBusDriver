package com.smartbus.driver.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "passenger_records")
public class PassengerRecordEntity {

    @PrimaryKey
    public long id;

    public long tripId;
    public Long stopId;
    public int passengerCount;
    public String recordedAt;
    public String note;
}
