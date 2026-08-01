package com.smartbus.driver.model;

public class TripHistoryItem {

    private final TripModel trip;
    private final String routeLabel;
    private final int passengerTotal;
    private final String durationLabel;

    public TripHistoryItem(TripModel trip, String routeLabel, int passengerTotal, String durationLabel) {
        this.trip = trip;
        this.routeLabel = routeLabel;
        this.passengerTotal = passengerTotal;
        this.durationLabel = durationLabel;
    }

    public TripModel getTrip() {
        return trip;
    }

    public String getRouteLabel() {
        return routeLabel;
    }

    public int getPassengerTotal() {
        return passengerTotal;
    }

    public String getDurationLabel() {
        return durationLabel;
    }
}
