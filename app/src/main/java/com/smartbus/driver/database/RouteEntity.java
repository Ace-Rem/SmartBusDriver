package com.smartbus.driver.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "routes")
public class RouteEntity {

    @PrimaryKey
    public long id;

    public String code;
    public String name;
    public String description;
}
