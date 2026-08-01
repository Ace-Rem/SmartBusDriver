package com.smartbus.driver.model;

public class AiAssistantRequest {

    private Long tripId;
    private String question;

    public AiAssistantRequest() {
    }

    public AiAssistantRequest(Long tripId, String question) {
        this.tripId = tripId;
        this.question = question;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
