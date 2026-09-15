package com.smartbus.driver.model;

public class FastBoardingSignalAcceptRequest {

    private Long tripId;
    private Long routeId;
    private String passengerIdentifier;

    public FastBoardingSignalAcceptRequest(Long tripId, Long routeId, String passengerIdentifier) {
        this.tripId = tripId;
        this.routeId = routeId;
        this.passengerIdentifier = passengerIdentifier;
    }
}
