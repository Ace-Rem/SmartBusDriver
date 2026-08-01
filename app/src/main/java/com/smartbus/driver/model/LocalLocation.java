package com.smartbus.driver.model;

public class LocalLocation {

    private final double latitude;
    private final double longitude;

    public LocalLocation(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }
}
