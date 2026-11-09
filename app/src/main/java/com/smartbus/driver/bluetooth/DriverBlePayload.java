package com.smartbus.driver.bluetooth;

/** Decoded BLE business data plus connection metadata kept outside the payload. */
public class DriverBlePayload {

    private final long routeId;
    private final long alightingStationId;
    private final String sourceDeviceAddress;
    private final String passengerIdentifier;

    public DriverBlePayload(long routeId, long alightingStationId) {
        this(routeId, alightingStationId, null, null);
    }

    private DriverBlePayload(long routeId, long alightingStationId,
                             String sourceDeviceAddress, String passengerIdentifier) {
        this.routeId = routeId;
        this.alightingStationId = alightingStationId;
        this.sourceDeviceAddress = sourceDeviceAddress;
        this.passengerIdentifier = passengerIdentifier;
    }

    public Long getRouteId() {
        return routeId;
    }

    public Long getDestinationStopId() {
        return alightingStationId;
    }

    public String getSourceDeviceAddress() {
        return sourceDeviceAddress;
    }

    public String getPassengerIdentifier() {
        return passengerIdentifier;
    }

    public DriverBlePayload withSourceDeviceAddress(String address) {
        return new DriverBlePayload(routeId, alightingStationId, address, passengerIdentifier);
    }

    public DriverBlePayload withPassengerIdentifier(String identifier) {
        return new DriverBlePayload(routeId, alightingStationId,
                sourceDeviceAddress, identifier);
    }
}
