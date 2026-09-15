package com.smartbus.driver.model;

public class FastBoardingSignalModel {

    private Long id;
    private Long signalId;
    private Long routeId;
    private Long destinationStopId;
    private Long tripId;
    private Long boardingStopId;
    private Long passengerId;
    private String passengerIdentifier;

    public Long getId() {
        return id;
    }

    public Long getSignalId() { return signalId; }

    public Long getRouteId() {
        return routeId;
    }

    public Long getDestinationStopId() {
        return destinationStopId;
    }

    public Long getTripId() { return tripId; }
    public Long getBoardingStopId() { return boardingStopId; }
    public Long getPassengerId() { return passengerId; }
    public String getPassengerIdentifier() { return passengerIdentifier; }
}
