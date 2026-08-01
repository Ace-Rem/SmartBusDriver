package com.smartbus.driver.model;

public class AiSummaryRequest {

    private Long tripId;

    public AiSummaryRequest() {
    }

    public AiSummaryRequest(Long tripId) {
        this.tripId = tripId;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }
}
