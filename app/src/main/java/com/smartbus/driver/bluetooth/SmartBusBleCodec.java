package com.smartbus.driver.bluetooth;

import android.bluetooth.le.ScanRecord;
import android.os.ParcelUuid;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class SmartBusBleCodec {

    public static final int MANUFACTURER_ID = 0x5342;
    public static final UUID SERVICE_UUID =
            UUID.fromString("0000f00d-0000-1000-8000-00805f9b34fb");
    public static final ParcelUuid SERVICE_PARCEL_UUID = new ParcelUuid(SERVICE_UUID);

    private static final byte MAGIC_0 = 0x53;
    private static final byte MAGIC_1 = 0x42;
    private static final int BINARY_PAYLOAD_LENGTH = 18;
    private static final int EXTENDED_PAYLOAD_LENGTH = 24;
    private static final int IDENTIFIER_HASH_LENGTH = 6;

    private SmartBusBleCodec() {
    }

    public static byte[] encode(
            long routeId,
            long destinationStopId
    ) {
        ByteBuffer buffer = ByteBuffer.allocate(BINARY_PAYLOAD_LENGTH).order(ByteOrder.BIG_ENDIAN);
        buffer.put(MAGIC_0);
        buffer.put(MAGIC_1);
        buffer.putLong(routeId);
        buffer.putLong(destinationStopId);
        return buffer.array();
    }

    public static byte[] encodeIdentifier(String bluetoothIdentifier) {
        if (bluetoothIdentifier == null || bluetoothIdentifier.isBlank()) {
            return new byte[0];
        }
        byte[] bytes = bluetoothIdentifier.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 20) {
            return bytes;
        }
        byte[] trimmed = new byte[20];
        System.arraycopy(bytes, 0, trimmed, 0, 20);
        return trimmed;
    }

    public static DriverBlePayload decode(ScanRecord scanRecord) {
        if (scanRecord == null) {
            return null;
        }
        DriverBlePayload payload = decodeManufacturer(scanRecord.getManufacturerSpecificData(MANUFACTURER_ID));
        if (payload != null) {
            String identifier = decodeIdentifier(scanRecord);
            if (identifier != null && !identifier.isBlank()) {
                return payload.withPassengerIdentifier(identifier);
            }
            if (payload.getPassengerIdentifier() != null
                    && !payload.getPassengerIdentifier().isBlank()) {
                return payload;
            }
            return payload.withPassengerIdentifier(
                    "BLE-" + payload.getTripId()
                            + "-" + payload.getDestinationStopId()
            );
        }
        byte[] serviceData = scanRecord.getServiceData(SERVICE_PARCEL_UUID);
        if (serviceData == null || serviceData.length == 0) {
            return null;
        }
        return DriverBlePayload.parse(new String(serviceData, StandardCharsets.UTF_8));
    }

    private static DriverBlePayload decodeManufacturer(byte[] bytes) {
        if (bytes == null || bytes.length < BINARY_PAYLOAD_LENGTH) {
            return null;
        }
        if (bytes[0] != MAGIC_0 || bytes[1] != MAGIC_1) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        buffer.get();
        buffer.get();
        long routeId = buffer.getLong();
        long destinationStopId = buffer.getLong();
        String identifier = null;
        if (bytes.length >= EXTENDED_PAYLOAD_LENGTH) {
            long identifierHash = 0L;
            for (int index = 0; index < IDENTIFIER_HASH_LENGTH; index++) {
                identifierHash = (identifierHash << 8) | (buffer.get() & 0xffL);
            }
            identifier = "BLE-" + String.format(
                    java.util.Locale.ROOT,
                    "%012x",
                    identifierHash
            );
        }
        return new DriverBlePayload(
                routeId,
                destinationStopId <= 0 ? null : destinationStopId,
                null,
                null,
                identifier
        );
    }

    private static String decodeIdentifier(ScanRecord scanRecord) {
        byte[] serviceData = scanRecord.getServiceData(SERVICE_PARCEL_UUID);
        if (serviceData == null || serviceData.length == 0) {
            return null;
        }
        return new String(serviceData, StandardCharsets.UTF_8).trim();
    }

}
