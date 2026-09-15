package com.smartbus.driver.model;

import java.util.Map;

public class AiSummaryRequest {

    private Long tripId;
    private Long routeId;
    private Map<String, Object> clientContext;

    public AiSummaryRequest() {
    }

    public AiSummaryRequest(Long tripId) {
        this.tripId = tripId;
    }

    public AiSummaryRequest(Long tripId, Map<String, Object> clientContext) {
        this(tripId, null, clientContext);
    }

    public AiSummaryRequest(Long tripId, Long routeId, Map<String, Object> clientContext) {
        this.tripId = tripId;
        this.routeId = routeId;
        this.clientContext = clientContext;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }

    public Map<String, Object> getClientContext() {
        return clientContext;
    }

    public void setClientContext(Map<String, Object> clientContext) {
        this.clientContext = clientContext;
    }
}
