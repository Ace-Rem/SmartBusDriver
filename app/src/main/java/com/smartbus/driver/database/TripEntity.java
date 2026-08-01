package com.smartbus.driver.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "trips")
public class TripEntity {

    @PrimaryKey
    public long id;

    public long driverId;
    public long routeId;
    public Long currentStopId;
    public String status;
    public String startedAt;
    public String endedAt;
    public Double currentLatitude;
    public Double currentLongitude;
}
