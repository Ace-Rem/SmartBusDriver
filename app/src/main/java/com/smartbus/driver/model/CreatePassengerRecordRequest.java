package com.smartbus.driver.model;

public class CreatePassengerRecordRequest {

    private Long tripId;
    private Long stopId;
    private Long boardingStopId;
    private Long destinationStopId;
    private Integer passengerCount;
    private String note;
    private String source;
    private String idempotencyKey;

    public CreatePassengerRecordRequest() {
    }

    public CreatePassengerRecordRequest(Long tripId, Long stopId, Integer passengerCount, String note) {
        this.tripId = tripId;
        this.stopId = stopId;
        this.destinationStopId = stopId;
        this.passengerCount = passengerCount;
        this.note = note;
    }

    public CreatePassengerRecordRequest(
            Long tripId,
            Long boardingStopId,
            Long destinationStopId,
            Integer passengerCount,
            String note,
            String source,
            String idempotencyKey
    ) {
        this.tripId = tripId;
        this.stopId = destinationStopId;
        this.boardingStopId = boardingStopId;
        this.destinationStopId = destinationStopId;
        this.passengerCount = passengerCount;
        this.note = note;
        this.source = source;
        this.idempotencyKey = idempotencyKey;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
