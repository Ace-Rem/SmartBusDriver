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

    public PassengerModel getPassenger() {
        return passenger;
    }

    public TripModel getTrip() {
        return trip;
    }

    public StopModel getBoardingStop() {
        return boardingStop;
    }

    public StopModel getDestinationStop() {
        return destinationStop;
    }

    public Long getPassengerRecordId() {
        return passengerRecordId;
    }

    public String getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public String getBluetoothIdentifier() {
        return bluetoothIdentifier;
    }

    public String getRequestedAt() {
        return requestedAt;
    }
}
