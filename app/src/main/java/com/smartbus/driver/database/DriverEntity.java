package com.smartbus.driver.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "drivers")
public class DriverEntity {

    @PrimaryKey
    public long id;

    public String fullName;
    public String phoneNumber;
    public String licenseNumber;
}
