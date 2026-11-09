package com.smartbus.driver.bluetooth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The shared BLE application protocol: UTF-8 '#routeId alightingStationId'. */
public final class SmartBusBleCodec {

    public static final UUID SERVICE_UUID =
            UUID.fromString("0000f00d-0000-1000-8000-00805f9b34fb");
    public static final UUID CHECK_IN_CHARACTERISTIC_UUID =
            UUID.fromString("0000f00e-0000-1000-8000-00805f9b34fb");
    /** Technical identity only; the business payload remains route + alighting stop. */
    public static final UUID IDENTITY_CHARACTERISTIC_UUID =
            UUID.fromString("0000f00f-0000-1000-8000-00805f9b34fb");
    private static final Pattern PAYLOAD = Pattern.compile("^#([0-9]+) ([0-9]+)$");

    private SmartBusBleCodec() {
    }

    public static DriverBlePayload decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        String value = new String(bytes, StandardCharsets.UTF_8);
        Matcher matcher = PAYLOAD.matcher(value);
        if (!matcher.matches()) return null;
        try {
            long routeId = Long.parseLong(matcher.group(1));
            long stationId = Long.parseLong(matcher.group(2));
            if (routeId <= 0L || stationId <= 0L) return null;
            return new DriverBlePayload(routeId, stationId);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
