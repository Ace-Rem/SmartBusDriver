package com.smartbus.driver.gps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.smartbus.driver.utils.Constants;

public class GpsManager {

    public interface LocationListener {
        void onLocationUpdated(double latitude, double longitude);

        void onLocationError(String message);
    }

    private final Context appContext;
    private final FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private LocationListener locationListener;
    private boolean tracking;
    private Location lastKnownLocation;

    public GpsManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(appContext);
    }

    public boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    public void startUpdates(LocationListener listener) {
        this.locationListener = listener;
        if (!hasLocationPermission()) {
            if (listener != null) {
                listener.onLocationError("Chưa cấp quyền vị trí");
            }
            return;
        }
        if (tracking) {
            return;
        }

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                Constants.GPS_UPDATE_INTERVAL_MS
        )
                .setMinUpdateIntervalMillis(Constants.GPS_FASTEST_INTERVAL_MS)
                .setMaxUpdateDelayMillis(Constants.GPS_UPDATE_INTERVAL_MS)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location == null || locationListener == null) {
                    return;
                }
                if (location.hasAccuracy() && location.getAccuracy() > 150f) {
                    return;
                }
                lastKnownLocation = location;
                locationListener.onLocationUpdated(location.getLatitude(), location.getLongitude());
            }
        };

        fusedLocationProviderClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null && locationListener != null) {
                        lastKnownLocation = location;
                        locationListener.onLocationUpdated(location.getLatitude(), location.getLongitude());
                    }
                });

        fusedLocationProviderClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
        );
        tracking = true;
    }

    public void stopUpdates() {
        if (locationCallback != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
        tracking = false;
        locationListener = null;
    }

    public boolean isTracking() {
        return tracking;
    }

    public Location getLastKnownLocation() {
        return lastKnownLocation;
    }
}
