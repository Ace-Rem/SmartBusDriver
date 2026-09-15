package com.smartbus.driver.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.smartbus.driver.SmartBusApp;
import com.smartbus.driver.bluetooth.DriverBlePayload;
import com.smartbus.driver.bluetooth.DriverBluetoothManager;
import com.smartbus.driver.gps.GpsManager;
import com.smartbus.driver.model.DashboardState;
import com.smartbus.driver.model.BoardingRequestModel;
import com.smartbus.driver.model.DriverModel;
import com.smartbus.driver.model.FastBoardingSignalModel;
import com.smartbus.driver.model.LocalLocation;
import com.smartbus.driver.model.PassengerRecordModel;
import com.smartbus.driver.model.RouteModel;
import com.smartbus.driver.model.StopModel;
import com.smartbus.driver.model.TripModel;
import com.smartbus.driver.model.UpdateLocationResponse;
import com.smartbus.driver.repository.AuthRepository;
import com.smartbus.driver.repository.BoardingRequestRepository;
import com.smartbus.driver.repository.BootstrapRepository;
import com.smartbus.driver.repository.DriverAiContextProvider;
import com.smartbus.driver.repository.DriverAiContextSyncManager;
import com.smartbus.driver.repository.FastBoardingSignalRepository;
import com.smartbus.driver.repository.PassengerRecordRepository;
import com.smartbus.driver.repository.RouteRepository;
import com.smartbus.driver.repository.TripRepository;
import com.smartbus.driver.utils.Constants;
import com.smartbus.driver.utils.Result;
import com.smartbus.driver.utils.StopGeoHelper;
import com.smartbus.driver.utils.UiState;
import android.location.Location;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class TripViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final TripRepository tripRepository;
    private final BootstrapRepository bootstrapRepository;
    private final RouteRepository routeRepository;
    private final PassengerRecordRepository passengerRecordRepository;
    private final BoardingRequestRepository boardingRequestRepository;
    private final FastBoardingSignalRepository fastBoardingSignalRepository;
    private final DriverAiContextProvider aiContextProvider;
    private final DriverAiContextSyncManager aiContextSyncManager;
    private final GpsManager gpsManager;
    private final DriverBluetoothManager bluetoothManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService fastSignalExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean locationUpdateInFlight = new AtomicBoolean(false);
    private final AtomicBoolean fastSignalPollInFlight = new AtomicBoolean(false);
    private volatile Double pendingLatitude;
    private volatile Double pendingLongitude;
    private volatile Double pendingDropoffLatitude;
    private volatile Double pendingDropoffLongitude;
    private volatile long lastFastSignalId;
    private volatile long lastFastSignalCancellationId;
    private Long fastSignalRouteId;
    private boolean fastSignalPolling;
    private final Runnable fastSignalPollRunnable = this::pollFastBoardingSignals;

    private final MutableLiveData<UiState<DashboardState>> dashboardState =
            new MutableLiveData<>(UiState.loading());
    private final MutableLiveData<UiState<TripModel>> actionState = new MutableLiveData<>(UiState.idle());
    private final MutableLiveData<UpdateLocationResponse> locationUpdate = new MutableLiveData<>();
    private final MutableLiveData<LocalLocation> localLocation = new MutableLiveData<>();
    private final MutableLiveData<List<StopModel>> stopsState = new MutableLiveData<>();
    private final MutableLiveData<String> gpsMessage = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<BoardingRequestModel>>> boardingRequestsState =
            new MutableLiveData<>(UiState.idle());
    private final MutableLiveData<String> dropoffPrompt = new MutableLiveData<>();

    private final Object dashboardLock = new Object();
    private DashboardState currentDashboard = new DashboardState();
    private volatile List<StopModel> cachedStops;

    public TripViewModel(@NonNull Application application) {
        super(application);
        SmartBusApp app = (SmartBusApp) application;
        authRepository = new AuthRepository(app.getTokenManager(), app.getDatabase());
        tripRepository = new TripRepository(
                app.getDatabase().tripDao(),
                app.getDatabase().passengerRecordDao()
        );
        aiContextProvider = new DriverAiContextProvider(app.getDatabase());
        aiContextSyncManager = app.getAiContextSyncManager();
        bootstrapRepository = new BootstrapRepository(app.getDatabase());
        routeRepository = new RouteRepository(app.getDatabase().routeDao(), app.getDatabase().stopDao());
        passengerRecordRepository = new PassengerRecordRepository(app.getDatabase().passengerRecordDao());
        boardingRequestRepository = new BoardingRequestRepository();
        fastBoardingSignalRepository = new FastBoardingSignalRepository();
        gpsManager = new GpsManager(application);
        bluetoothManager = new DriverBluetoothManager(application);
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

    public LiveData<UiState<List<BoardingRequestModel>>> getBoardingRequestsState() {
        return boardingRequestsState;
    }

    public LiveData<String> getDropoffPrompt() {
        return dropoffPrompt;
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

                // Prefer nearest/next from device GPS so UI works even if backend
                // has not persisted currentStop yet (or old deploy still gates at 50m).
                Location lastLocation = gpsManager.getLastKnownLocation();
                if (lastLocation != null && stops != null && !stops.isEmpty()) {
                    applyNearestToState(state, stops, lastLocation.getLatitude(), lastLocation.getLongitude());
                } else {
                    StopModel persistedCurrent = StopGeoHelper.findById(stops, trip.getCurrentStopId());
                    if (persistedCurrent != null) {
                        state.setCurrentStop(persistedCurrent);
                        state.setNextStop(StopGeoHelper.findNext(stops, persistedCurrent));
                    }
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
                        if ("ON_BOARD".equals(record.getStatus()) && record.getPassengerCount() != null) {
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

                loadBoardingRequests(trip.getId(), false);

                state.setGpsStatus(gpsManager.hasLocationPermission()
                        ? (gpsManager.isTracking() ? "Đang gửi mỗi 5 giây" : "Đã sẵn sàng")
                        : "Cần quyền vị trí");
                if (state.getCurrentStop() != null) {
                    state.setNotification("Chuyến đang chạy — bến: " + state.getCurrentStop().getName());
                } else {
                    state.setNotification("Chuyến đang chạy");
                }
                mainHandler.post(this::startBleScanning);
                mainHandler.post(this::startFastSignalPolling);
            } else {
                state.setGpsStatus(gpsManager.hasLocationPermission() ? "Đã sẵn sàng" : "Cần quyền vị trí");
                state.setNotification("Chưa có chuyến đang chạy");
                boardingRequestsState.postValue(UiState.success(null));
                stopsState.postValue(null);
            }

            synchronized (dashboardLock) {
                currentDashboard = state;
            }
            dashboardState.postValue(UiState.success(state));
            tripRepository.syncPendingTrips();
            passengerRecordRepository.syncPending();
            bootstrapRepository.sync();
        });
    }

    public void preloadTripHistory() {
        executor.execute(tripRepository::syncHistory);
    }

    public void startTrip(long routeId) {
        if (getCurrentDashboard().hasActiveTrip()) {
            actionState.setValue(UiState.error("Đã có chuyến đang chạy. Hãy kết thúc trước khi bắt đầu chuyến mới."));
            return;
        }
        executor.execute(() -> {
            Result<TripModel> result = tripRepository.startTrip(routeId);
            if (result.isSuccess()) {
                publishStartedTrip(result.getData());
                pushAiContextInBackground();
                actionState.postValue(UiState.success(result.getData(), "Đã bắt đầu chuyến"));
                mainHandler.post(() -> {
                    startGpsTracking();
                    startBleScanning();
                    // The server check-in channel must start with the trip;
                    // waiting for a dashboard reload can miss the signal TTL.
                    startFastSignalPolling();
                });
            } else {
                actionState.postValue(UiState.error(result.getMessage()));
            }
        });
    }

    public void endTrip() {
        clearActiveTripLocally("Đã kết thúc chuyến");
        executor.execute(() -> {
            Result<TripModel> result = tripRepository.endCurrentTrip();
            if (result.isSuccess()) {
                actionState.postValue(UiState.success(result.getData(), "Đã kết thúc chuyến"));
                loadDashboard(false);
                stopBleScanning();
                executor.execute(tripRepository::syncPendingTrips);
            } else {
                actionState.postValue(UiState.error(result.getMessage()));
                loadDashboard(false);
            }
        });
    }

    private void clearActiveTripLocally(String notification) {
        stopGpsTracking();
        stopBleScanning();
        stopFastSignalPolling();
        DashboardState next = new DashboardState();
        DashboardState previous;
        synchronized (dashboardLock) {
            previous = currentDashboard;
            next.setDriver(previous.getDriver());
            next.setGpsStatus(gpsManager.hasLocationPermission() ? "Đã sẵn sàng" : "Cần quyền vị trí");
            next.setNotification(notification);
            next.setTracking(false);
            currentDashboard = next;
        }
        cachedStops = null;
        stopsState.postValue(null);
        boardingRequestsState.postValue(UiState.success(null));
        dashboardState.postValue(UiState.success(next));
    }

    public void startGpsTracking() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::startGpsTracking);
            return;
        }
        try {
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
                    // Room queries must not run on the main thread; GPS callbacks
                    // are delivered on the main looper, so defer DB work to executor.
                    localLocation.postValue(new LocalLocation(latitude, longitude));
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
        } catch (RuntimeException exception) {
            gpsMessage.postValue("Không khởi động được định vị: " + exception.getMessage());
        }
    }

    public void clearActionState() {
        actionState.setValue(UiState.idle());
    }

    private void publishStartedTrip(TripModel trip) {
        if (trip == null) {
            return;
        }
        DashboardState state = new DashboardState();
        state.setDriver(authRepository.getCachedDriver());
        state.setTrip(trip);
        state.setTracking(gpsManager.isTracking());
        state.setGpsStatus(gpsManager.hasLocationPermission() ? "Đã sẵn sàng" : "Cần quyền vị trí");
        state.setNotification("Chuyến đang chạy");
        Result<RouteModel> routeResult = routeRepository.getRoute(trip.getRouteId());
        if (routeResult.isSuccess()) {
            state.setRoute(routeResult.getData());
            if (routeResult.getData() != null) {
                state.setNotification("Đang chạy chuyến "
                        + routeResult.getData().getCode() + " - " + routeResult.getData().getName());
            }
        }
        Result<List<StopModel>> stopsResult = routeRepository.getStops(trip.getRouteId());
        if (stopsResult.isSuccess()) {
            List<StopModel> stops = StopGeoHelper.sortedByOrder(stopsResult.getData());
            cachedStops = stops;
            stopsState.postValue(stops);
        }
        synchronized (dashboardLock) {
            currentDashboard = state;
        }
        boardingRequestsState.postValue(UiState.success(null));
        dashboardState.postValue(UiState.success(state));
        mainHandler.post(this::startBleScanning);
        mainHandler.post(this::startFastSignalPolling);
    }

    public void stopGpsTracking() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::stopGpsTracking);
            return;
        }
        try {
            gpsManager.stopUpdates();
        } catch (RuntimeException exception) {
            gpsMessage.postValue("Không dừng được định vị: " + exception.getMessage());
        }
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
                    if ("ON_BOARD".equals(record.getStatus()) && record.getPassengerCount() != null) {
                        total += record.getPassengerCount();
                        if (currentStopId != null && currentStopId.equals(record.getStopId())) {
                            alighting += record.getPassengerCount();
                        }
                    }
                }
            } else {
                total = passengerRecordRepository.getCachedPassengerTotal(tripId);
            }
            if (currentStopId != null) {
                alighting = passengerRecordRepository.getCachedAlightingTotal(tripId, currentStopId);
            }
            synchronized (dashboardLock) {
                currentDashboard.setPassengerTotal(total);
                currentDashboard.setPassengersAlighting(alighting);
                dashboardState.postValue(UiState.success(currentDashboard));
            }
            pushAiContextInBackground();
        });
    }

    /**
     * Flushes locally recorded passengers without making the current UI wait
     * for a slow/offline backend. The records stay in Room until the request
     * succeeds, so leaving the manual-add screen cannot lose the count.
     */
    public void syncPassengerRecordsInBackground() {
        executor.execute(() -> {
            passengerRecordRepository.syncPending();
            pushAiContextInBackground();
        });
    }

    private void pushAiContextInBackground() {
        if (aiContextProvider != null && aiContextSyncManager != null) {
            executor.execute(() -> aiContextSyncManager.submit(aiContextProvider.read()));
        }
    }

    public void refreshBoardingRequests() {
        DashboardState snapshot = getCurrentDashboard();
        if (snapshot.getTrip() == null || snapshot.getTrip().getId() == null) {
            boardingRequestsState.setValue(UiState.success(null));
            return;
        }
        loadBoardingRequests(snapshot.getTrip().getId(), true);
    }

    public void scanPassengerCheckIns() {
        ensureBleScanning();
        DashboardState snapshot = getCurrentDashboard();
        if (snapshot == null || !snapshot.hasActiveTrip()) {
            gpsMessage.setValue("Hãy bắt đầu chuyến trước khi quét hành khách.");
            return;
        }
        gpsMessage.setValue("Đang quét check-in BLE của hành khách cho tuyến #"
                + snapshot.getTrip().getRouteId());
    }

    public void ensureBleScanning() {
        if (!getCurrentDashboard().hasActiveTrip()) {
            return;
        }
        startBleScanning();
    }

    public void confirmBoardingRequest(long requestId) {
        actionState.setValue(UiState.loading());
        executor.execute(() -> {
            DashboardState snapshot = getCurrentDashboard();
            Long tripId = snapshot == null || snapshot.getTrip() == null
                    ? null : snapshot.getTrip().getId();
            Long routeId = snapshot == null || snapshot.getTrip() == null
                    ? null : snapshot.getTrip().getRouteId();
            Long signalId = passengerRecordRepository.fastSignalId(requestId);
            PassengerRecordModel pendingRecord = passengerRecordRepository.getById(requestId);
            if (tripId != null && routeId != null && pendingRecord != null
                    && pendingRecord.getPassengerIdentifier() != null) {
                Result<?> accepted = signalId != null
                        ? fastBoardingSignalRepository.accept(
                                signalId, tripId, routeId, pendingRecord.getPassengerIdentifier())
                        : fastBoardingSignalRepository.acceptByIdentity(
                                tripId, routeId, pendingRecord.getPassengerIdentifier());
                if (!accepted.isSuccess()) {
                    actionState.postValue(UiState.error(
                            accepted.getMessage() == null
                                    ? "Chưa gửi được xác nhận lên máy chủ hành khách."
                                    : accepted.getMessage()
                    ));
                    return;
                }
            }
            Result<PassengerRecordModel> result = passengerRecordRepository.confirmLocalBoarding(requestId);
            if (result.isSuccess()) {
                actionState.postValue(UiState.success(null, "Đã xác nhận hành khách lên xe"));
                refreshPassengerTotal();
                refreshBoardingRequests();
            } else {
                actionState.postValue(UiState.error(result.getMessage()));
            }
        });
    }

    public void markCurrentDropoffDone() {
        DashboardState snapshot = getCurrentDashboard();
        Double latitude = pendingDropoffLatitude;
        Double longitude = pendingDropoffLongitude;
        if (snapshot == null || snapshot.getTrip() == null || snapshot.getTrip().getId() == null
                || latitude == null || longitude == null) {
            actionState.setValue(UiState.error("Chưa có nhóm khách xuống trong phạm vi 200m."));
            return;
        }
        executor.execute(() -> {
            passengerRecordRepository.markDroppedOffByCoordinates(snapshot.getTrip().getId(), latitude, longitude);
            pendingDropoffLatitude = null;
            pendingDropoffLongitude = null;
            actionState.postValue(UiState.success(null, "Đã trừ khách xuống khỏi bộ đếm"));
            refreshPassengerTotal();
        });
    }

    private void loadBoardingRequests(long tripId, boolean showLoading) {
        if (showLoading) {
            boardingRequestsState.postValue(UiState.loading());
        }
        executor.execute(() -> {
            List<BoardingRequestModel> requests = passengerRecordRepository.getLocalBoardingRequests(tripId);
            enrichLocalBoardingRequests(requests, tripId);
            boardingRequestsState.postValue(UiState.success(requests));
        });
    }

    private void enrichLocalBoardingRequests(List<BoardingRequestModel> requests, long tripId) {
        if (requests == null) return;
        DashboardState snapshot = getCurrentDashboard();
        Long routeId = snapshot == null || snapshot.getTrip() == null
                ? null : snapshot.getTrip().getRouteId();
        for (BoardingRequestModel request : requests) {
            if (request == null) continue;
            if (request.getTrip() == null) request.setTrip(new TripModel());
            request.getTrip().setId(tripId);
            request.getTrip().setRouteId(routeId);
            request.setBoardingStop(namedStop(request.getBoardingStop()));
            request.setDestinationStop(namedStop(request.getDestinationStop()));
        }
    }

    private StopModel namedStop(StopModel source) {
        if (source == null || source.getId() == null || cachedStops == null) return source;
        for (StopModel stop : cachedStops) {
            if (stop != null && source.getId().equals(stop.getId())) return stop;
        }
        return source;
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
                DashboardState snapshot = getCurrentDashboard();
                if (snapshot != null && snapshot.getTrip() != null && snapshot.getTrip().getId() != null) {
                    tripRepository.updateLocalLocation(snapshot.getTrip().getId(), latitude, longitude);
                }
                applyLocalNearestStops(latitude, longitude);
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
            if (data.getTrip() != null && currentDashboard.getTrip() == null) {
                currentDashboard.setTrip(data.getTrip());
            }
            if (data.getCurrentStop() != null) {
                currentDashboard.setCurrentStop(data.getCurrentStop());
                currentDashboard.setNextStop(data.getNextStop());
                if (currentDashboard.getTrip() != null && currentDashboard.getTrip().getId() != null) {
                    tripRepository.updateLocalCurrentStop(
                            currentDashboard.getTrip().getId(), data.getCurrentStop().getId());
                }
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
        pushAiContextInBackground();
    }

    private void applyLocalNearestStops(double latitude, double longitude) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            executor.execute(() -> applyLocalNearestStops(latitude, longitude));
            return;
        }
        synchronized (dashboardLock) {
            fillNearestLocked(latitude, longitude);
            currentDashboard.setGpsStatus(String.format("%.5f, %.5f", latitude, longitude));
            if (currentDashboard.getTrip() != null
                    && currentDashboard.getTrip().getId() != null
                    && currentDashboard.getCurrentStop() != null
                    && currentDashboard.getCurrentStop().getId() != null) {
                int alighting = passengerRecordRepository.getCachedAlightingTotal(
                        currentDashboard.getTrip().getId(),
                        currentDashboard.getCurrentStop().getId()
                );
                currentDashboard.setPassengerTotal(
                        passengerRecordRepository.getCachedPassengerTotal(currentDashboard.getTrip().getId())
                );
                currentDashboard.setPassengersAlighting(alighting);
            }
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
            applyDropoffPromptLocked(latitude, longitude);
            dashboardState.postValue(UiState.success(currentDashboard));
        }
        pushAiContextInBackground();
    }

    private void applyDropoffPromptLocked(double latitude, double longitude) {
        if (currentDashboard.getTrip() == null || currentDashboard.getTrip().getId() == null) {
            return;
        }
        List<PassengerRecordModel> records = passengerRecordRepository.getCachedByTrip(currentDashboard.getTrip().getId());
        if (records == null || records.isEmpty()) {
            return;
        }
        int nearestCount = 0;
        Double nearestLatitude = null;
        Double nearestLongitude = null;
        float nearestMeters = Float.MAX_VALUE;
        for (PassengerRecordModel record : records) {
            if (!"ON_BOARD".equals(record.getStatus())
                    || record.getDestinationLatitude() == null
                    || record.getDestinationLongitude() == null
                    || record.getPassengerCount() == null) {
                continue;
            }
            float[] result = new float[1];
            Location.distanceBetween(
                    latitude,
                    longitude,
                    record.getDestinationLatitude(),
                    record.getDestinationLongitude(),
                    result
            );
            if (result[0] <= 200f && result[0] < nearestMeters) {
                nearestMeters = result[0];
                nearestLatitude = record.getDestinationLatitude();
                nearestLongitude = record.getDestinationLongitude();
            }
        }
        if (nearestLatitude == null || nearestLongitude == null) {
            pendingDropoffLatitude = null;
            pendingDropoffLongitude = null;
            return;
        }
        pendingDropoffLatitude = nearestLatitude;
        pendingDropoffLongitude = nearestLongitude;
        nearestCount = passengerRecordRepository.getCachedAlightingTotal(
                currentDashboard.getTrip().getId(),
                nearestLatitude,
                nearestLongitude
        );
        currentDashboard.setPassengersAlighting(nearestCount);
        String message = "Còn " + Math.round(nearestMeters)
                + " m tới bến xuống, có " + nearestCount + " khách xuống.";
        currentDashboard.setNotification(message);
        dropoffPrompt.postValue(message);
    }

    private void fillNearestLocked(double latitude, double longitude) {
        List<StopModel> stops = cachedStops;
        if (stops == null || stops.isEmpty()) {
            return;
        }
        StopModel nearest = StopGeoHelper.findCurrentAlongRoute(
                stops,
                currentDashboard.getCurrentStop(),
                latitude,
                longitude
        );
        if (nearest == null) {
            return;
        }
        currentDashboard.setCurrentStop(nearest);
        currentDashboard.setNextStop(StopGeoHelper.findNext(stops, nearest));
        if (currentDashboard.getTrip() != null && currentDashboard.getTrip().getId() != null) {
            tripRepository.updateLocalCurrentStop(currentDashboard.getTrip().getId(), nearest.getId());
        }
    }

    private void applyNearestToState(
            DashboardState state,
            List<StopModel> stops,
            double latitude,
            double longitude
    ) {
        StopModel nearest = StopGeoHelper.findCurrentAlongRoute(stops, state.getCurrentStop(), latitude, longitude);
        if (nearest == null) {
            return;
        }
        state.setCurrentStop(nearest);
        state.setNextStop(StopGeoHelper.findNext(stops, nearest));
        if (state.getTrip() != null && state.getTrip().getId() != null) {
            tripRepository.updateLocalCurrentStop(state.getTrip().getId(), nearest.getId());
        }
        String nextName = state.getNextStop() != null ? state.getNextStop().getName() : "hết tuyến";
        state.setNotification("Gần nhất: " + nearest.getName() + " — tiếp theo: " + nextName);
    }

    private void startBleScanning() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::startBleScanning);
            return;
        }
        try {
            bluetoothManager.startScanning(new DriverBluetoothManager.Listener() {
                @Override
                public void onPayload(DriverBlePayload payload) {
                    handleBlePayload(payload);
                }

                @Override
                public void onMessage(String message) {
                    gpsMessage.postValue(message);
                }
            });
        } catch (RuntimeException exception) {
            gpsMessage.postValue("Không khởi động được BLE scan: " + exception.getMessage());
        }
    }

    private void startFastSignalPolling() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::startFastSignalPolling);
            return;
        }
        DashboardState snapshot = getCurrentDashboard();
        if (snapshot == null || !snapshot.hasActiveTrip()
                || snapshot.getTrip().getRouteId() == null) {
            return;
        }
        Long routeId = snapshot.getTrip().getRouteId();
        if (fastSignalRouteId == null || !fastSignalRouteId.equals(routeId)) {
            fastSignalRouteId = routeId;
            lastFastSignalId = 0L;
            lastFastSignalCancellationId = 0L;
        }
        if (fastSignalPolling) {
            return;
        }
        fastSignalPolling = true;
        mainHandler.post(fastSignalPollRunnable);
    }

    private void stopFastSignalPolling() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::stopFastSignalPolling);
            return;
        }
        fastSignalPolling = false;
        mainHandler.removeCallbacks(fastSignalPollRunnable);
        fastSignalRouteId = null;
        lastFastSignalId = 0L;
        lastFastSignalCancellationId = 0L;
    }

    private void pollFastBoardingSignals() {
        if (!fastSignalPolling) {
            return;
        }
        DashboardState snapshot = getCurrentDashboard();
        if (snapshot == null || !snapshot.hasActiveTrip()
                || snapshot.getTrip().getId() == null
                || snapshot.getTrip().getRouteId() == null) {
            stopFastSignalPolling();
            return;
        }
        long targetTripId = snapshot.getTrip().getId();
        long routeId = snapshot.getTrip().getRouteId();
        if (!fastSignalPollInFlight.compareAndSet(false, true)) {
            mainHandler.postDelayed(fastSignalPollRunnable, 1_000L);
            return;
        }
        fastSignalExecutor.execute(() -> {
            boolean changed = false;
            try {
                // Fetch the complete short-lived pending set so Room can also
                // reconcile requests that were cancelled before this app's
                // current cursor, not only newly appended events.
                Result<List<FastBoardingSignalModel>> result =
                        fastBoardingSignalRepository.poll(routeId, 0L);
                if (result.isSuccess() && result.getData() != null) {
                    List<Long> activeSignalIds = new ArrayList<>();
                    for (FastBoardingSignalModel signal : result.getData()) {
                        if (signal == null || signal.getId() == null
                                || signal.getRouteId() == null
                                || !Long.valueOf(routeId).equals(signal.getRouteId())
                                || (signal.getTripId() != null
                                && !Long.valueOf(targetTripId).equals(signal.getTripId())
                                && !isLocalTripId(targetTripId))
                                || signal.getDestinationStopId() == null) {
                            continue;
                        }
                        activeSignalIds.add(signal.getId());
                        if (signal.getId() <= lastFastSignalId) continue;
                        DashboardState current = getCurrentDashboard();
                        if (current == null || current.getTrip() == null
                                || !current.hasActiveTrip()
                                || !Long.valueOf(targetTripId).equals(current.getTrip().getId())
                                || !Long.valueOf(routeId).equals(current.getTrip().getRouteId())) {
                            break;
                        }
                        Result<PassengerRecordModel> local = passengerRecordRepository.createFromFastSignal(
                                targetTripId,
                                signal.getId(),
                                signal.getBoardingStopId(),
                                signal.getDestinationStopId(),
                                signal.getPassengerIdentifier(),
                                "Backend fast signal route=" + signal.getRouteId()
                                        + " destinationStop=" + signal.getDestinationStopId()
                        );
                        // Advance the cursor for every valid event, including an
                        // idempotent event already present in Room, so one old
                        // signal cannot block later signal batches.
                        lastFastSignalId = Math.max(lastFastSignalId, signal.getId());
                        if (local.isSuccess()) {
                            changed = true;
                        }
                    }
                    passengerRecordRepository.removePendingFastSignalsNotIn(
                            targetTripId, activeSignalIds);
                }
                // Render new requests as soon as the pending-signal poll
                // finishes. Cancellation cleanup is secondary and must never
                // delay the driver's visible request list.
                if (changed) {
                    loadBoardingRequests(targetTripId, false);
                    changed = false;
                }

                Result<List<FastBoardingSignalModel>> cancelled =
                        fastBoardingSignalRepository.pollCancelled(routeId, lastFastSignalCancellationId);
                if (cancelled.isSuccess() && cancelled.getData() != null) {
                    for (FastBoardingSignalModel event : cancelled.getData()) {
                        if (event == null || event.getId() == null
                                || event.getId() <= lastFastSignalCancellationId) {
                            continue;
                        }
                        lastFastSignalCancellationId = Math.max(
                                lastFastSignalCancellationId, event.getId());
                        long signalId = event.getSignalId() == null
                                ? event.getId() : event.getSignalId();
                        passengerRecordRepository.removePendingFastSignal(targetTripId, signalId);
                        changed = true;
                    }
                }
                if (changed) {
                    loadBoardingRequests(targetTripId, false);
                }
            } finally {
                fastSignalPollInFlight.set(false);
                if (fastSignalPolling) {
                    mainHandler.postDelayed(fastSignalPollRunnable, 1_000L);
                }
            }
        });
    }

    private boolean isLocalTripId(long tripId) {
        return tripId < 0L || tripId >= 1_000_000_000_000L;
    }

    private void stopBleScanning() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::stopBleScanning);
            return;
        }
        try {
            bluetoothManager.stopScanning();
        } catch (RuntimeException exception) {
            gpsMessage.postValue("Không dừng được BLE scan: " + exception.getMessage());
        }
    }

    private void handleBlePayload(DriverBlePayload payload) {
        if (payload == null) {
            return;
        }
        DashboardState snapshot = getCurrentDashboard();
        if (snapshot == null || snapshot.getTrip() == null || snapshot.getTrip().getId() == null) {
            return;
        }
        Long currentRouteId = snapshot.getTrip().getRouteId();
        if (currentRouteId == null || payload.getRouteId() == null
                || !payload.getRouteId().equals(currentRouteId)) {
            return;
        }
        long targetTripId = snapshot.getTrip().getId();
        executor.execute(() -> {
            String note = "BLE check-in route=" + payload.getRouteId()
                    + " destinationStop=" + payload.getDestinationStopId();
            Result<PassengerRecordModel> result = passengerRecordRepository.createFromBle(
                    targetTripId,
                    payload.getDestinationStopId(),
                    payload.getDestinationLatitude(),
                    payload.getDestinationLongitude(),
                    payload.getPassengerIdentifier(),
                    note
            );
            if (result.isSuccess()) {
                refreshBoardingRequests();
                pushAiContextInBackground();
                gpsMessage.postValue(result.getMessage() != null
                        ? result.getMessage()
                        : "Đã nhận yêu cầu check-in BLE.");
            } else {
                gpsMessage.postValue(result.getMessage());
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopGpsTracking();
        stopBleScanning();
        stopFastSignalPolling();
        executor.shutdownNow();
        fastSignalExecutor.shutdownNow();
    }
}
