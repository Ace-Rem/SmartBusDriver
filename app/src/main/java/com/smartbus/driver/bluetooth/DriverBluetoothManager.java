package com.smartbus.driver.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;

public class DriverBluetoothManager {

    public interface Listener {
        void onPayload(DriverBlePayload payload);

        void onMessage(String message);
    }

    private final Context context;
    private BluetoothLeScanner scanner;
    private ScanCallback callback;
    private boolean scanning;
    private boolean scanUsesFallback;

    public DriverBluetoothManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean canScan() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        try {
            return adapter.isEnabled();
        } catch (SecurityException ignored) {
            return false;
        }
    }

    public void startScanning(Listener listener) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            listener.onMessage("Thiết bị tài xế không hỗ trợ Bluetooth LE.");
            return;
        }
        if (!canScan()) {
            listener.onMessage("Cần bật/cấp quyền Bluetooth scan để nhận check-in BLE.");
            return;
        }
        try {
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) {
                listener.onMessage("Bluetooth LE scanner chưa sẵn sàng.");
                return;
            }
            if (scanning) {
                return;
            }
            ScanSettings.Builder settingsBuilder = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
            ScanSettings settings = settingsBuilder.build();
            callback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    handleScanResult(result, listener);
                }

                @Override
                public void onBatchScanResults(java.util.List<ScanResult> results) {
                    if (results == null) {
                        return;
                    }
                    for (ScanResult result : results) {
                        handleScanResult(result, listener);
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    if (scanUsesFallback) {
                        scanning = false;
                        listener.onMessage("BLE scan lỗi: " + errorCode);
                        return;
                    }
                    scanUsesFallback = true;
                    try {
                        scanner.startScan(null, settings, callback);
                        scanning = true;
                        listener.onMessage("Đang quét check-in BLE (fallback)...");
                    } catch (SecurityException exception) {
                        scanning = false;
                        listener.onMessage("BLE scan lỗi: " + errorCode);
                    }
                }
            };
            // Scan all advertisements. The only business filter is routeId in
            // TripViewModel; hardware filters can silently discard valid packets.
            scanner.startScan(null, settings, callback);
            scanning = true;
            listener.onMessage("Đang quét check-in BLE của hành khách...");
        } catch (SecurityException exception) {
            listener.onMessage("Cần quyền Bluetooth scan để nhận check-in.");
        }
    }

    public void stopScanning() {
        if (scanner == null || callback == null) {
            scanning = false;
            return;
        }
        try {
            scanner.stopScan(callback);
        } catch (SecurityException ignored) {
        }
        callback = null;
        scanning = false;
        scanUsesFallback = false;
    }

    public boolean isScanning() {
        return scanning;
    }

    private void handleScanResult(ScanResult result, Listener listener) {
        if (result == null || result.getScanRecord() == null) {
            return;
        }
        DriverBlePayload payload = SmartBusBleCodec.decode(result.getScanRecord());
        if (payload != null) {
            listener.onPayload(payload);
        }
    }
}
