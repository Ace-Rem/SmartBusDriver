package com.smartbus.driver.model;

import java.util.Map;

public class AiSummaryRequest {

    private Long tripId;
    private Map<String, Object> clientContext;

    public AiSummaryRequest() {
    }

    public AiSummaryRequest(Long tripId) {
        this.tripId = tripId;
    }

    public AiSummaryRequest(Long tripId, Map<String, Object> clientContext) {
        this.tripId = tripId;
        this.clientContext = clientContext;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public Map<String, Object> getClientContext() {
        return clientContext;
    }

    public void setClientContext(Map<String, Object> clientContext) {
        this.clientContext = clientContext;
    }
}
