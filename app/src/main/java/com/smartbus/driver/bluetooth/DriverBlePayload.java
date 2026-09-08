package com.smartbus.driver.bluetooth;

public class DriverBlePayload {

    private final long routeId;
    private final Long destinationStopId;
    private final Double destinationLatitude;
    private final Double destinationLongitude;
    private final String passengerIdentifier;

    public DriverBlePayload(
            long routeId,
            Long destinationStopId,
            Double destinationLatitude,
            Double destinationLongitude,
            String passengerIdentifier
    ) {
        this.routeId = routeId;
        this.destinationStopId = destinationStopId;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.passengerIdentifier = passengerIdentifier;
    }

    public long getTripId() {
        // Kept for old callers; the binary BLE identifier is now a route ID.
        return routeId;
    }

    public Long getRouteId() {
        return routeId;
    }

    public Long getDestinationStopId() {
        return destinationStopId;
    }

    public Double getDestinationLatitude() {
        return destinationLatitude;
    }

    public Double getDestinationLongitude() {
        return destinationLongitude;
    }

    public String getPassengerIdentifier() {
        return passengerIdentifier;
    }

    public DriverBlePayload withPassengerIdentifier(String identifier) {
        return new DriverBlePayload(
                routeId,
                destinationStopId,
                destinationLatitude,
                destinationLongitude,
                identifier
        );
    }

    public static DriverBlePayload parse(String payload) {
        if (payload == null || !payload.startsWith("SB1;")) {
            return null;
        }
        Long routeId = null;
        Long destinationStopId = null;
        Double latitude = null;
        Double longitude = null;
        String identifier = null;
        String[] parts = payload.split(";");
        for (String part : parts) {
            int separator = part.indexOf('=');
            if (separator <= 0 || separator >= part.length() - 1) {
                continue;
            }
            String key = part.substring(0, separator);
            String value = part.substring(separator + 1);
            try {
                if ("trip".equals(key) || "t".equals(key)) {
                    routeId = Long.parseLong(value);
                } else if ("route".equals(key) || "r".equals(key)) {
                    routeId = Long.parseLong(value);
                } else if ("dest".equals(key) || "d".equals(key)) {
                    destinationStopId = Long.parseLong(value);
                } else if (("lat".equals(key) || "a".equals(key)) && !"null".equals(value)) {
                    latitude = Double.parseDouble(value);
                } else if (("lng".equals(key) || "o".equals(key)) && !"null".equals(value)) {
                    longitude = Double.parseDouble(value);
                } else if ("id".equals(key) || "i".equals(key)) {
                    identifier = value;
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (routeId == null) {
            return null;
        }
        if (identifier == null || identifier.isBlank()) {
            identifier = "BLE-" + routeId + "-" + latitude + "-" + longitude;
        }
        return new DriverBlePayload(routeId, destinationStopId, latitude, longitude, identifier);
    }
}
