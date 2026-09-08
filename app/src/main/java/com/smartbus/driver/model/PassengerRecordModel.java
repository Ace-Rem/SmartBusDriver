package com.smartbus.driver.model;

public class PassengerRecordModel {

    private Long id;
    private Long tripId;
    private Long stopId;
    private Long boardingStopId;
    private Long destinationStopId;
    private Double destinationLatitude;
    private Double destinationLongitude;
    private String passengerIdentifier;
    private String status;
    private Integer passengerCount;
    private String recordedAt;
    private String note;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getBoardingStopId() {
        return boardingStopId;
    }

    public void setBoardingStopId(Long boardingStopId) {
        this.boardingStopId = boardingStopId;
    }

    public Long getDestinationStopId() {
        return destinationStopId;
    }

    public void setDestinationStopId(Long destinationStopId) {
        this.destinationStopId = destinationStopId;
    }

    public Double getDestinationLatitude() {
        return destinationLatitude;
    }

    public void setDestinationLatitude(Double destinationLatitude) {
        this.destinationLatitude = destinationLatitude;
    }

    public Double getDestinationLongitude() {
        return destinationLongitude;
    }

    public void setDestinationLongitude(Double destinationLongitude) {
        this.destinationLongitude = destinationLongitude;
    }

    public String getPassengerIdentifier() {
        return passengerIdentifier;
    }

    public void setPassengerIdentifier(String passengerIdentifier) {
        this.passengerIdentifier = passengerIdentifier;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPassengerCount() {
        return passengerCount;
    }

    public void setPassengerCount(Integer passengerCount) {
        this.passengerCount = passengerCount;
    }

    public String getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(String recordedAt) {
        this.recordedAt = recordedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
