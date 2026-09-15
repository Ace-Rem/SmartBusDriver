package com.smartbus.driver.model;

public class FastBoardingAcceptanceModel {

    private Long id;
    private Long signalId;
    private Long passengerId;
    private Long tripId;
    private Long routeId;
    private Long boardingStopId;
    private Long destinationStopId;
    private String status;

    public Long getId() { return id; }
    public Long getSignalId() { return signalId; }
    public Long getPassengerId() { return passengerId; }
    public Long getTripId() { return tripId; }
    public Long getRouteId() { return routeId; }
    public Long getBoardingStopId() { return boardingStopId; }
    public Long getDestinationStopId() { return destinationStopId; }
    public String getStatus() { return status; }
}
