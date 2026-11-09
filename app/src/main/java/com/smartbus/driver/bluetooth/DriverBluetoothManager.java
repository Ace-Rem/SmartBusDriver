package com.smartbus.driver.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import androidx.core.content.ContextCompat;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/** Peripheral/GATT server kept alive for the duration of the driver's active trip. */
public class DriverBluetoothManager {

    public interface Listener {
        void onPayload(DriverBlePayload payload);
        void onMessage(String message);
    }

    private final Context context;
    private BluetoothGattServer gattServer;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;
    private boolean advertising;
    private final ConcurrentHashMap<String, String> passengerIdentifiers = new ConcurrentHashMap<>();

    public DriverBluetoothManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean canAdvertise() {
        return advertiseUnavailableReason() == null;
    }

    public synchronized void startAdvertising(Listener listener) {
        String unavailableReason = advertiseUnavailableReason();
        if (unavailableReason != null) {
            listener.onMessage(unavailableReason);
            return;
        }
        stopAdvertising();
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            listener.onMessage("Bluetooth GATT Server chưa sẵn sàng.");
            return;
        }
        try {
            gattServer = bluetoothManager.openGattServer(context, new BluetoothGattServerCallback() {
                @Override
                public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                    if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                        listener.onMessage("Có hành khách kết nối BLE.");
                    } else if (newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) {
                        passengerIdentifiers.remove(safeAddress(device));
                        listener.onMessage("Hành khách đã ngắt kết nối BLE.");
                    }
                }

                @Override
                public void onServiceAdded(int status, BluetoothGattService service) {
                    if (status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
                        listener.onMessage("BLE GATT service đã sẵn sàng, đang bắt đầu quảng bá...");
                        startAdvertisingPayload(listener);
                    } else {
                        listener.onMessage("Không tạo được BLE service check-in.");
                        stopAdvertising();
                    }
                }

                @Override
                public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                        BluetoothGattCharacteristic characteristic, boolean preparedWrite,
                        boolean responseNeeded, int offset, byte[] value) {
                    if (preparedWrite || offset != 0) {
                        if (responseNeeded) {
                            gattServer.sendResponse(device, requestId,
                                    android.bluetooth.BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null);
                        }
                        return;
                    }
                    String address = safeAddress(device);
                    if (SmartBusBleCodec.IDENTITY_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                        String identifier = decodeIdentifier(value);
                        if (identifier == null) {
                            if (responseNeeded) {
                                gattServer.sendResponse(device, requestId,
                                        android.bluetooth.BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH,
                                        0, null);
                            }
                            return;
                        }
                        passengerIdentifiers.put(address, identifier);
                        if (responseNeeded) {
                            gattServer.sendResponse(device, requestId,
                                    android.bluetooth.BluetoothGatt.GATT_SUCCESS, 0, null);
                        }
                        return;
                    }
                    if (!SmartBusBleCodec.CHECK_IN_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
                        if (responseNeeded) {
                            gattServer.sendResponse(device, requestId,
                                    android.bluetooth.BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null);
                        }
                        return;
                    }
                    DriverBlePayload payload = SmartBusBleCodec.decode(value);
                    if (responseNeeded) {
                        gattServer.sendResponse(device, requestId,
                                payload == null ? android.bluetooth.BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH
                                        : android.bluetooth.BluetoothGatt.GATT_SUCCESS,
                                0, null);
                    }
                    if (payload == null) {
                        listener.onMessage("Đã bắt được kết nối BLE nhưng payload không hợp lệ.");
                        return;
                    }
                    listener.onMessage("Đã bắt được BLE từ hành khách: routeId=" + payload.getRouteId()
                            + ", alightingStationId=" + payload.getDestinationStopId());
                    String passengerIdentifier = passengerIdentifiers.get(address);
                    listener.onPayload(payload.withSourceDeviceAddress(address)
                            .withPassengerIdentifier(passengerIdentifier));
                }
            });
            if (gattServer == null) {
                listener.onMessage("Không mở được Bluetooth GATT Server.");
                return;
            }
            BluetoothGattService service = new BluetoothGattService(
                    SmartBusBleCodec.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
            BluetoothGattCharacteristic checkIn = new BluetoothGattCharacteristic(
                    SmartBusBleCodec.CHECK_IN_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE
                            | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE);
            service.addCharacteristic(checkIn);
            BluetoothGattCharacteristic identity = new BluetoothGattCharacteristic(
                    SmartBusBleCodec.IDENTITY_CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE
                            | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE);
            service.addCharacteristic(identity);
            if (!gattServer.addService(service)) {
                listener.onMessage("Không tạo được BLE service check-in.");
                stopAdvertising();
                return;
            }
        } catch (SecurityException | IllegalArgumentException exception) {
            listener.onMessage("Không khởi động được BLE peripheral.");
            stopAdvertising();
        }
    }

    private void startAdvertisingPayload(Listener listener) {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            advertiser = adapter == null ? null : adapter.getBluetoothLeAdvertiser();
            if (advertiser == null) {
                listener.onMessage("Thiết bị không hỗ trợ BLE advertising.");
                stopAdvertising();
                return;
            }
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(true)
                    .setTimeout(0)
                    .build();
            AdvertiseData data = new AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    .addServiceUuid(new ParcelUuid(SmartBusBleCodec.SERVICE_UUID))
                    .build();
            advertiseCallback = new AdvertiseCallback() {
                @Override
                public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                    advertising = true;
                    listener.onMessage("Driver đang quảng bá BLE và chờ check-in.");
                }

                @Override
                public void onStartFailure(int errorCode) {
                    advertising = false;
                    listener.onMessage("BLE advertise lỗi: " + errorCode);
                }
            };
            advertiser.startAdvertising(settings, data, advertiseCallback);
        } catch (SecurityException | IllegalArgumentException exception) {
            listener.onMessage("Không khởi động được BLE advertise.");
            stopAdvertising();
        }
    }

    public synchronized void stopAdvertising() {
        if (advertiser != null && advertiseCallback != null) {
            try {
                advertiser.stopAdvertising(advertiseCallback);
            } catch (SecurityException ignored) {
            }
        }
        if (gattServer != null) {
            try {
                gattServer.close();
            } catch (SecurityException ignored) {
            }
        }
        advertiser = null;
        advertiseCallback = null;
        gattServer = null;
        passengerIdentifiers.clear();
        advertising = false;
    }

    public boolean isAdvertising() {
        return advertising;
    }

    private String advertiseUnavailableReason() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return "Thiết bị không có Bluetooth để quảng bá BLE.";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
                != PackageManager.PERMISSION_GRANTED) {
            return "Chưa được cấp quyền Bluetooth quảng bá (BLUETOOTH_ADVERTISE).";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            return "Chưa được cấp quyền kết nối Bluetooth (BLUETOOTH_CONNECT).";
        }
        try {
            if (!adapter.isEnabled()) {
                return "Bluetooth tài xế đang tắt, chưa thể quảng bá BLE.";
            }
            if (!adapter.isMultipleAdvertisementSupported()) {
                return "Thiết bị tài xế không hỗ trợ BLE Peripheral/Multiple Advertisement.";
            }
        } catch (SecurityException ignored) {
            return "Không có quyền kiểm tra trạng thái Bluetooth quảng bá.";
        }
        return null;
    }

    private String safeAddress(BluetoothDevice device) {
        try {
            return device == null ? "unknown" : device.getAddress();
        } catch (SecurityException ignored) {
            return "unknown";
        }
    }

    private String decodeIdentifier(byte[] value) {
        if (value == null || value.length == 0 || value.length > 128) {
            return null;
        }
        String identifier = new String(value, StandardCharsets.UTF_8).trim();
        if (identifier.isEmpty() || identifier.indexOf('\n') >= 0
                || identifier.indexOf('\r') >= 0) {
            return null;
        }
        return identifier;
    }
}
