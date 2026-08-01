package com.smartbus.driver.model;

public class UpdateLocationResponse {

    private TripModel trip;
    private StopModel nearestStop;
    private Double nearestStopDistanceMeters;
    private boolean withinThreshold;
    private StopModel currentStop;
    private StopModel nextStop;
    private int passengersAlightingAtCurrentStop;
    private String notification;

    public TripModel getTrip() {
        return trip;
    }

    public void setTrip(TripModel trip) {
        this.trip = trip;
    }

    public StopModel getNearestStop() {
        return nearestStop;
    }

    public void setNearestStop(StopModel nearestStop) {
        this.nearestStop = nearestStop;
    }

    public Double getNearestStopDistanceMeters() {
        return nearestStopDistanceMeters;
    }

    public void setNearestStopDistanceMeters(Double nearestStopDistanceMeters) {
        this.nearestStopDistanceMeters = nearestStopDistanceMeters;
    }

    public boolean isWithinThreshold() {
        return withinThreshold;
    }

    public void setWithinThreshold(boolean withinThreshold) {
        this.withinThreshold = withinThreshold;
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

    public int getPassengersAlightingAtCurrentStop() {
        return passengersAlightingAtCurrentStop;
    }

    public void setPassengersAlightingAtCurrentStop(int passengersAlightingAtCurrentStop) {
        this.passengersAlightingAtCurrentStop = passengersAlightingAtCurrentStop;
    }

    public String getNotification() {
        return notification;
    }

    public void setNotification(String notification) {
        this.notification = notification;
    }
}
