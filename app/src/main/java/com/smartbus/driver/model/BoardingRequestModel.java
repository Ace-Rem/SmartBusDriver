package com.smartbus.driver.model;

public class BoardingRequestModel {

    private Long id;
    private PassengerModel passenger;
    private TripModel trip;
    private StopModel boardingStop;
    private StopModel destinationStop;
    private Long passengerRecordId;
    private String status;
    private String note;
    private String bluetoothIdentifier;
    private String requestedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PassengerModel getPassenger() {
        return passenger;
    }

    public void setPassenger(PassengerModel passenger) {
        this.passenger = passenger;
    }

    public TripModel getTrip() {
        return trip;
    }

    public void setTrip(TripModel trip) {
        this.trip = trip;
    }

    public StopModel getBoardingStop() {
        return boardingStop;
    }

    public void setBoardingStop(StopModel boardingStop) {
        this.boardingStop = boardingStop;
    }

    public StopModel getDestinationStop() {
        return destinationStop;
    }

    public void setDestinationStop(StopModel destinationStop) {
        this.destinationStop = destinationStop;
    }

    public Long getPassengerRecordId() {
        return passengerRecordId;
    }

    public void setPassengerRecordId(Long passengerRecordId) {
        this.passengerRecordId = passengerRecordId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getBluetoothIdentifier() {
        return bluetoothIdentifier;
    }

    public void setBluetoothIdentifier(String bluetoothIdentifier) {
        this.bluetoothIdentifier = bluetoothIdentifier;
    }

    public String getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(String requestedAt) {
        this.requestedAt = requestedAt;
    }
}
