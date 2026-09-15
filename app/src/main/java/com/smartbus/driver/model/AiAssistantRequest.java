package com.smartbus.driver.model;

import java.util.Map;

public class AiAssistantRequest {

    private Long tripId;
    private Long routeId;
    private String question;
    private Map<String, Object> clientContext;

    public AiAssistantRequest() {
    }

    public AiAssistantRequest(Long tripId, String question) {
        this.tripId = tripId;
        this.question = question;
    }

    public AiAssistantRequest(Long tripId, String question, Map<String, Object> clientContext) {
        this(tripId, null, question, clientContext);
    }

    public AiAssistantRequest(Long tripId, Long routeId, String question, Map<String, Object> clientContext) {
        this.tripId = tripId;
        this.routeId = routeId;
        this.question = question;
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

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public Map<String, Object> getClientContext() {
        return clientContext;
    }

    public void setClientContext(Map<String, Object> clientContext) {
        this.clientContext = clientContext;
    }
}
