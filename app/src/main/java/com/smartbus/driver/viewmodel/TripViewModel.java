package com.smartbus.driver.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.smartbus.driver.SmartBusApp;
import com.smartbus.driver.gps.GpsManager;
import com.smartbus.driver.model.DashboardState;
import com.smartbus.driver.model.DriverModel;
import com.smartbus.driver.model.LocalLocation;
import com.smartbus.driver.model.PassengerRecordModel;
import com.smartbus.driver.model.RouteModel;
import com.smartbus.driver.model.StopModel;
import com.smartbus.driver.model.TripModel;
import com.smartbus.driver.model.UpdateLocationResponse;
import com.smartbus.driver.repository.AuthRepository;
import com.smartbus.driver.repository.PassengerRecordRepository;
import com.smartbus.driver.repository.RouteRepository;
import com.smartbus.driver.repository.TripRepository;
import com.smartbus.driver.utils.Constants;
import com.smartbus.driver.utils.Result;
import com.smartbus.driver.utils.StopGeoHelper;
import com.smartbus.driver.utils.UiState;
import android.location.Location;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class TripViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final TripRepository tripRepository;
    private final RouteRepository routeRepository;
    private final PassengerRecordRepository passengerRecordRepository;
    private final GpsManager gpsManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean locationUpdateInFlight = new AtomicBoolean(false);
    private volatile Double pendingLatitude;
    private volatile Double pendingLongitude;

    private final MutableLiveData<UiState<DashboardState>> dashboardState =
            new MutableLiveData<>(UiState.loading());
    private final MutableLiveData<UiState<TripModel>> actionState = new MutableLiveData<>(UiState.idle());
    private final MutableLiveData<UpdateLocationResponse> locationUpdate = new MutableLiveData<>();
    private final MutableLiveData<LocalLocation> localLocation = new MutableLiveData<>();
    private final MutableLiveData<List<StopModel>> stopsState = new MutableLiveData<>();
    private final MutableLiveData<String> gpsMessage = new MutableLiveData<>();

    private final Object dashboardLock = new Object();
    private DashboardState currentDashboard = new DashboardState();
    private volatile List<StopModel> cachedStops;

    public TripViewModel(@NonNull Application application) {
        super(application);
        SmartBusApp app = (SmartBusApp) application;
        authRepository = new AuthRepository(app.getTokenManager(), app.getDatabase());
        tripRepository = new TripRepository(app.getDatabase().tripDao());
        routeRepository = new RouteRepository(app.getDatabase().routeDao(), app.getDatabase().stopDao());
        passengerRecordRepository = new PassengerRecordRepository(app.getDatabase().passengerRecordDao());
        gpsManager = new GpsManager(application);
    }

    public LiveData<UiState<DashboardState>> getDashboardState() {
        return dashboardState;
    }

    public LiveData<UiState<TripModel>> getActionState() {
        return actionState;
    }

    public LiveData<UpdateLocationResponse> getLocationUpdate() {
        return locationUpdate;
    }

    public LiveData<LocalLocation> getLocalLocation() {
        return localLocation;
    }

    public LiveData<List<StopModel>> getStopsState() {
        return stopsState;
    }

    public LiveData<String> getGpsMessage() {
        return gpsMessage;
    }

    public GpsManager getGpsManager() {
        return gpsManager;
    }

    public DashboardState getCurrentDashboard() {
        synchronized (dashboardLock) {
            return currentDashboard;
        }
    }

    public void loadDashboard() {
        loadDashboard(true);
    }

    public void loadDashboard(boolean showLoading) {
        if (showLoading) {
            dashboardState.setValue(UiState.loading());
        }
        executor.execute(() -> {
            DashboardState state = new DashboardState();
            DriverModel driver = authRepository.getCachedDriver();
            Result<DriverModel> meResult = authRepository.getMe();
            if (meResult.isSuccess() && meResult.getData() != null) {
                driver = meResult.getData();
            }
            state.setDriver(driver);

            Result<TripModel> tripResult = tripRepository.getCurrentTrip();
            if (tripResult.isSuccess() && tripResult.getData() != null
                    && Constants.TRIP_STATUS_IN_PROGRESS.equals(tripResult.getData().getStatus())) {
                TripModel trip = tripResult.getData();
                state.setTrip(trip);
                state.setTracking(gpsManager.isTracking());

                Result<RouteModel> routeResult = routeRepository.getRoute(trip.getRouteId());
                if (routeResult.isSuccess()) {
                    state.setRoute(routeResult.getData());
                }

                Result<List<StopModel>> stopsResult = routeRepository.getStops(trip.getRouteId());
                List<StopModel> stops = null;
                if (stopsResult.isSuccess()) {
                    stops = StopGeoHelper.sortedByOrder(stopsResult.getData());
                    cachedStops = stops;
                    stopsState.postValue(stops);
                }

                Result<StopModel> currentStopResult = tripRepository.getCurrentStop();
                if (currentStopResult.isSuccess() && currentStopResult.getData() != null) {
                    state.setCurrentStop(currentStopResult.getData());
                }

                Result<StopModel> nextStopResult = tripRepository.getNextStop();
                if (nextStopResult.isSuccess() && nextStopResult.getData() != null) {
                    state.setNextStop(nextStopResult.getData());
                }

                // Prefer nearest/next from device GPS so UI works even if backend
                // has not persisted currentStop yet (or old deploy still gates at 50m).
                Location lastLocation = gpsManager.getLastKnownLocation();
                if (lastLocation != null && stops != null && !stops.isEmpty()) {
                    applyNearestToState(state, stops, lastLocation.getLatitude(), lastLocation.getLongitude());
                } else {
                    DashboardState previous;
                    synchronized (dashboardLock) {
                        previous = currentDashboard;
                    }
                    if (state.getCurrentStop() == null
                            && previous != null
                            && previous.getCurrentStop() != null
                            && previous.getTrip() != null
                            && trip.getId() != null
                            && trip.getId().equals(previous.getTrip().getId())) {
                        state.setCurrentStop(previous.getCurrentStop());
                        state.setNextStop(previous.getNextStop());
                    }
                }

                Result<List<PassengerRecordModel>> passengers =
                        passengerRecordRepository.getByTrip(trip.getId());
                if (passengers.isSuccess() && passengers.getData() != null) {
                    int total = 0;
                    int alighting = 0;
                    Long currentStopId = state.getCurrentStop() != null
                            ? state.getCurrentStop().getId()
                            : null;
                    for (PassengerRecordModel record : passengers.getData()) {
                        if (record.getPassengerCount() != null) {
                            total += record.getPassengerCount();
                            if (currentStopId != null && currentStopId.equals(record.getStopId())) {
                                alighting += record.getPassengerCount();
                            }
                        }
                    }
                    state.setPassengerTotal(total);
                    state.setPassengersAlighting(alighting);
                } else {
                    state.setPassengerTotal(passengerRecordRepository.getCachedPassengerTotal(trip.getId()));
                }

                state.setGpsStatus(gpsManager.hasLocationPermission()
                        ? (gpsManager.isTracking() ? "Đang gửi mỗi 5 giây" : "Đã sẵn sàng")
                        : "Cần quyền vị trí");
                if (state.getCurrentStop() != null) {
                    state.setNotification("Chuyến đang chạy — bến: " + state.getCurrentStop().getName());
                } else {
                    state.setNotification("Chuyến đang chạy");
                }
            } else {
                state.setGpsStatus(gpsManager.hasLocationPermission() ? "Đã sẵn sàng" : "Cần quyền vị trí");
                state.setNotification("Chưa có chuyến đang chạy");
                stopsState.postValue(null);
            }

            synchronized (dashboardLock) {
                currentDashboard = state;
            }
            dashboardState.postValue(UiState.success(state));
        });
    }

    public void startTrip(long routeId) {
        if (getCurrentDashboard().hasActiveTrip()) {
            actionState.setValue(UiState.error("Đã có chuyến đang chạy. Hãy kết thúc trước khi bắt đầu chuyến mới."));
            return;
        }
        actionState.setValue(UiState.loading());
        executor.execute(() -> {
            Result<TripModel> result = tripRepository.startTrip(routeId);
            if (result.isSuccess()) {
                actionState.postValue(UiState.success(result.getData(), "Đã bắt đầu chuyến"));
                loadDashboard(false);
                startGpsTracking();
            } else {
                actionState.postValue(UiState.error(result.getMessage()));
            }
        });
    }

    public void endTrip() {
        actionState.setValue(UiState.loading());
        executor.execute(() -> {
            Result<TripModel> result = tripRepository.endCurrentTrip();
            if (result.isSuccess()) {
                stopGpsTracking();
                actionState.postValue(UiState.success(result.getData(), "Đã kết thúc chuyến"));
                loadDashboard(false);
            } else {
                actionState.postValue(UiState.error(result.getMessage()));
            }
        });
    }

    public void startGpsTracking() {
        if (!gpsManager.hasLocationPermission()) {
            gpsMessage.postValue("Cần cấp quyền vị trí để gửi GPS");
            return;
        }
        if (gpsManager.isTracking()) {
            return;
        }
        gpsManager.startUpdates(new GpsManager.LocationListener() {
            @Override
            public void onLocationUpdated(double latitude, double longitude) {
                DashboardState snapshot = getCurrentDashboard();
                if (snapshot == null || !snapshot.hasActiveTrip()) {
                    return;
                }
                localLocation.postValue(new LocalLocation(latitude, longitude));
                applyLocalNearestStops(latitude, longitude);
                enqueueLocationUpdate(latitude, longitude);
            }

            @Override
            public void onLocationError(String message) {
                gpsMessage.postValue(message);
            }
        });
        synchronized (dashboardLock) {
            currentDashboard.setTracking(true);
            currentDashboard.setGpsStatus("Đang gửi mỗi 5 giây");
            dashboardState.postValue(UiState.success(currentDashboard));
        }
    }

    public void stopGpsTracking() {
        gpsManager.stopUpdates();
        synchronized (dashboardLock) {
            currentDashboard.setTracking(false);
        }
        locationUpdateInFlight.set(false);
        pendingLatitude = null;
        pendingLongitude = null;
    }

    public void refreshPassengerTotal() {
        DashboardState snapshot = getCurrentDashboard();
        if (snapshot.getTrip() == null || snapshot.getTrip().getId() == null) {
            return;
        }
        long tripId = snapshot.getTrip().getId();
        Long currentStopId = snapshot.getCurrentStop() != null ? snapshot.getCurrentStop().getId() : null;
        executor.execute(() -> {
            Result<List<PassengerRecordModel>> passengers =
                    passengerRecordRepository.getByTrip(tripId);
            int total = 0;
            int alighting = 0;
            if (passengers.isSuccess() && passengers.getData() != null) {
                for (PassengerRecordModel record : passengers.getData()) {
                    if (record.getPassengerCount() != null) {
                        total += record.getPassengerCount();
                        if (currentStopId != null && currentStopId.equals(record.getStopId())) {
                            alighting += record.getPassengerCount();
                        }
                    }
                }
            } else {
                total = passengerRecordRepository.getCachedPassengerTotal(tripId);
            }
            synchronized (dashboardLock) {
                currentDashboard.setPassengerTotal(total);
                currentDashboard.setPassengersAlighting(alighting);
                dashboardState.postValue(UiState.success(currentDashboard));
            }
        });
    }

    private void enqueueLocationUpdate(double latitude, double longitude) {
        pendingLatitude = latitude;
        pendingLongitude = longitude;
        if (!locationUpdateInFlight.compareAndSet(false, true)) {
            return;
        }
        executor.execute(this::flushPendingLocationUpdate);
    }

    private void flushPendingLocationUpdate() {
        try {
            Double latitude;
            Double longitude;
            do {
                latitude = pendingLatitude;
                longitude = pendingLongitude;
                pendingLatitude = null;
                pendingLongitude = null;
                if (latitude == null || longitude == null) {
                    break;
                }
                Result<UpdateLocationResponse> result =
                        tripRepository.updateLocation(latitude, longitude);
                if (result.isSuccess() && result.getData() != null) {
                    UpdateLocationResponse data = result.getData();
                    locationUpdate.postValue(data);
                    applyLocationToDashboard(data, latitude, longitude);
                } else {
                    // Backend failed/old: keep showing nearest from local GPS + stops.
                    applyLocalNearestStops(latitude, longitude);
                    if (!result.isSuccess()) {
                        gpsMessage.postValue(result.getMessage());
                    }
                }
            } while (pendingLatitude != null && pendingLongitude != null);
        } finally {
            locationUpdateInFlight.set(false);
            if (pendingLatitude != null && pendingLongitude != null
                    && locationUpdateInFlight.compareAndSet(false, true)) {
                executor.execute(this::flushPendingLocationUpdate);
            }
        }
    }

    private void applyLocationToDashboard(UpdateLocationResponse data, double latitude, double longitude) {
        synchronized (dashboardLock) {
            if (data.getTrip() != null) {
                currentDashboard.setTrip(data.getTrip());
            }
            if (data.getCurrentStop() != null) {
                currentDashboard.setCurrentStop(data.getCurrentStop());
                currentDashboard.setNextStop(data.getNextStop());
            } else {
                // Old backend may omit currentStop outside 50m — fill from local list.
                fillNearestLocked(latitude, longitude);
            }
            currentDashboard.setPassengersAlighting(data.getPassengersAlightingAtCurrentStop());
            currentDashboard.setGpsStatus(String.format("%.5f, %.5f", latitude, longitude));
            if (data.getNotification() != null && !data.getNotification().isEmpty()) {
                currentDashboard.setNotification(data.getNotification());
            } else if (data.isWithinThreshold() && data.getCurrentStop() != null) {
                currentDashboard.setNotification(
                        "Đã đến bến: " + data.getCurrentStop().getName()
                                + " — khách xuống: " + data.getPassengersAlightingAtCurrentStop()
                );
            } else if (currentDashboard.getCurrentStop() != null) {
                String nextName = currentDashboard.getNextStop() != null
                        ? currentDashboard.getNextStop().getName()
                        : "hết tuyến";
                currentDashboard.setNotification(
                        "Gần nhất: " + currentDashboard.getCurrentStop().getName()
                                + " — tiếp theo: " + nextName
                );
            }
            dashboardState.postValue(UiState.success(currentDashboard));
        }
    }

    private void applyLocalNearestStops(double latitude, double longitude) {
        synchronized (dashboardLock) {
            fillNearestLocked(latitude, longitude);
            currentDashboard.setGpsStatus(String.format("%.5f, %.5f", latitude, longitude));
            if (currentDashboard.getCurrentStop() != null) {
                String nextName = currentDashboard.getNextStop() != null
                        ? currentDashboard.getNextStop().getName()
                        : "hết tuyến";
                if (currentDashboard.getNotification() == null
                        || currentDashboard.getNotification().isEmpty()
                        || currentDashboard.getNotification().startsWith("Chuyến đang chạy")
                        || currentDashboard.getNotification().startsWith("Gần nhất:")) {
                    currentDashboard.setNotification(
                            "Gần nhất: " + currentDashboard.getCurrentStop().getName()
                                    + " — tiếp theo: " + nextName
                    );
                }
            }
            dashboardState.postValue(UiState.success(currentDashboard));
        }
    }

    private void fillNearestLocked(double latitude, double longitude) {
        List<StopModel> stops = cachedStops;
        if (stops == null || stops.isEmpty()) {
            return;
        }
        StopModel nearest = StopGeoHelper.findNearest(stops, latitude, longitude);
        if (nearest == null) {
            return;
        }
        currentDashboard.setCurrentStop(nearest);
        currentDashboard.setNextStop(StopGeoHelper.findNext(stops, nearest));
    }

    private void applyNearestToState(
            DashboardState state,
            List<StopModel> stops,
            double latitude,
            double longitude
    ) {
        StopModel nearest = StopGeoHelper.findNearest(stops, latitude, longitude);
        if (nearest == null) {
            return;
        }
        state.setCurrentStop(nearest);
        state.setNextStop(StopGeoHelper.findNext(stops, nearest));
        String nextName = state.getNextStop() != null ? state.getNextStop().getName() : "hết tuyến";
        state.setNotification("Gần nhất: " + nearest.getName() + " — tiếp theo: " + nextName);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopGpsTracking();
        executor.shutdownNow();
    }
}
