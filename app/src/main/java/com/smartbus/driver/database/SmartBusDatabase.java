package com.smartbus.driver.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                DriverEntity.class,
                RouteEntity.class,
                StopEntity.class,
                TripEntity.class,
                PassengerRecordEntity.class
        },
        version = 1,
        exportSchema = false
)
public abstract class SmartBusDatabase extends RoomDatabase {

    private static final String DB_NAME = "smartbus_driver.db";
    private static volatile SmartBusDatabase instance;

    public abstract DriverDao driverDao();

    public abstract RouteDao routeDao();

    public abstract StopDao stopDao();

    public abstract TripDao tripDao();

    public abstract PassengerRecordDao passengerRecordDao();

    public static SmartBusDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (SmartBusDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    SmartBusDatabase.class,
                                    DB_NAME
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
