package com.smartbus.driver.model;

public class CreatePassengerRecordRequest {

    private Long tripId;
    private Long stopId;
    private Integer passengerCount;
    private String note;

    public CreatePassengerRecordRequest() {
    }

    public CreatePassengerRecordRequest(Long tripId, Long stopId, Integer passengerCount, String note) {
        this.tripId = tripId;
        this.stopId = stopId;
        this.passengerCount = passengerCount;
        this.note = note;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public Long getStopId() {
        return stopId;
    }

    public void setStopId(Long stopId) {
        this.stopId = stopId;
    }

    public Integer getPassengerCount() {
        return passengerCount;
    }

    public void setPassengerCount(Integer passengerCount) {
        this.passengerCount = passengerCount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
