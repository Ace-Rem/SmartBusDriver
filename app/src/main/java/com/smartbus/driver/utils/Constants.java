package com.smartbus.driver.utils;

public final class Constants {

    public static final String PREFS_NAME = "smartbus_driver_prefs";
    public static final String KEY_ACCESS_TOKEN = "access_token";
    public static final String KEY_TOKEN_TYPE = "token_type";
    public static final String KEY_DRIVER_ID = "driver_id";
    public static final String KEY_DRIVER_FULL_NAME = "driver_full_name";
    public static final String KEY_DRIVER_PHONE = "driver_phone";
    public static final String KEY_DRIVER_LICENSE = "driver_license";
    public static final String KEY_REMEMBER_USERNAME = "remember_username";
    public static final String KEY_SAVED_USERNAME = "saved_username";

    public static final String TRIP_STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String TRIP_STATUS_COMPLETED = "COMPLETED";

    public static final long GPS_UPDATE_INTERVAL_MS = 5_000L;
    public static final long GPS_FASTEST_INTERVAL_MS = 5_000L;
    public static final float MAP_DEFAULT_ZOOM = 16f;

    private Constants() {
    }
}
