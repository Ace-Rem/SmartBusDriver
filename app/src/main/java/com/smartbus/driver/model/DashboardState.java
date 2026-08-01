package com.smartbus.driver.model;

import com.smartbus.driver.utils.Constants;

public class DashboardState {

    private DriverModel driver;
    private TripModel trip;
    private RouteModel route;
    private StopModel currentStop;
    private StopModel nextStop;
    private int passengerTotal;
    private int passengersAlighting;
    private String gpsStatus;
    private String notification;
    private boolean tracking;

    public DriverModel getDriver() {
        return driver;
    }

    public void setDriver(DriverModel driver) {
        this.driver = driver;
    }

    public TripModel getTrip() {
        return trip;
    }

    public void setTrip(TripModel trip) {
        this.trip = trip;
    }

    public RouteModel getRoute() {
        return route;
    }

    public void setRoute(RouteModel route) {
        this.route = route;
    }

    public StopModel getCurrentStop() {
        return currentStop;
    }

    public void setCurrentStop(StopModel currentStop) {
        this.currentStop = currentStop;
    }

    public StopModel getNextStop() {
        return nextStop;
    }

    public void setNextStop(StopModel nextStop) {
        this.nextStop = nextStop;
    }

    public int getPassengerTotal() {
        return passengerTotal;
    }

    public void setPassengerTotal(int passengerTotal) {
        this.passengerTotal = passengerTotal;
    }

    public int getPassengersAlighting() {
        return passengersAlighting;
    }

    public void setPassengersAlighting(int passengersAlighting) {
        this.passengersAlighting = passengersAlighting;
    }

    public String getGpsStatus() {
        return gpsStatus;
    }

    public void setGpsStatus(String gpsStatus) {
        this.gpsStatus = gpsStatus;
    }

    public String getNotification() {
        return notification;
    }

    public void setNotification(String notification) {
        this.notification = notification;
    }

    public boolean isTracking() {
        return tracking;
    }

    public void setTracking(boolean tracking) {
        this.tracking = tracking;
    }

    public boolean hasActiveTrip() {
        return trip != null && Constants.TRIP_STATUS_IN_PROGRESS.equals(trip.getStatus());
    }
}
