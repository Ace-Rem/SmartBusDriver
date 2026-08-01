package com.smartbus.driver.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "stops")
public class StopEntity {

    @PrimaryKey
    public long id;

    public long routeId;
    public String name;
    public double latitude;
    public double longitude;
    public int stopOrder;
}
