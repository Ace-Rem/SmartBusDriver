package com.smartbus.driver.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.smartbus.driver.SmartBusApp;
import com.smartbus.driver.model.PassengerRecordModel;
import com.smartbus.driver.model.RouteModel;
import com.smartbus.driver.model.TripHistoryItem;
import com.smartbus.driver.model.TripModel;
import com.smartbus.driver.repository.PassengerRecordRepository;
import com.smartbus.driver.repository.RouteRepository;
import com.smartbus.driver.repository.TripRepository;
import com.smartbus.driver.utils.DateTimeUtils;
import com.smartbus.driver.utils.Result;
import com.smartbus.driver.utils.UiState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TripHistoryViewModel extends AndroidViewModel {

    private final TripRepository tripRepository;
    private final RouteRepository routeRepository;
    private final PassengerRecordRepository passengerRecordRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<UiState<List<TripHistoryItem>>> historyState =
            new MutableLiveData<>(UiState.loading());

    public TripHistoryViewModel(@NonNull Application application) {
        super(application);
        SmartBusApp app = (SmartBusApp) application;
        tripRepository = new TripRepository(app.getDatabase().tripDao());
        routeRepository = new RouteRepository(app.getDatabase().routeDao(), app.getDatabase().stopDao());
        passengerRecordRepository = new PassengerRecordRepository(app.getDatabase().passengerRecordDao());
    }

    public LiveData<UiState<List<TripHistoryItem>>> getHistoryState() {
        return historyState;
    }

    public void loadHistory() {
        List<TripHistoryItem> currentItems = historyState.getValue() == null
                ? null
                : historyState.getValue().getData();
        if (currentItems == null || currentItems.isEmpty()) {
            historyState.setValue(UiState.loading());
        }
        executor.execute(() -> {
            List<TripModel> cachedTrips = tripRepository.getCachedHistory();
            boolean hasCache = cachedTrips != null && !cachedTrips.isEmpty();
            if (hasCache) {
                historyState.postValue(UiState.success(buildItems(cachedTrips, true)));
            }

            Result<List<TripModel>> result = tripRepository.syncHistory();
            if (!result.isSuccess()) {
                if (!hasCache) {
                    historyState.postValue(UiState.error(result.getMessage()));
                }
                return;
            }
            List<TripModel> trips = result.getData();
            if (trips == null || trips.isEmpty()) {
                if (!hasCache) {
                    historyState.postValue(UiState.empty("Chưa có chuyến nào được ghi nhận"));
                }
                return;
            }
            historyState.postValue(UiState.success(buildItems(trips, false)));
        });
    }

    private List<TripHistoryItem> buildItems(List<TripModel> trips, boolean cacheOnly) {
        Map<Long, String> routeLabels = new HashMap<>();
        List<TripHistoryItem> items = new ArrayList<>();
        for (TripModel trip : trips) {
            String routeLabel = "Tuyến #" + (trip.getRouteId() != null ? trip.getRouteId() : "?");
            if (trip.getRouteId() != null) {
                if (routeLabels.containsKey(trip.getRouteId())) {
                    routeLabel = routeLabels.get(trip.getRouteId());
                } else {
                    Result<RouteModel> routeResult = routeRepository.getRoute(trip.getRouteId());
                    if (routeResult.isSuccess() && routeResult.getData() != null) {
                        RouteModel route = routeResult.getData();
                        routeLabel = route.getCode() + " - " + route.getName();
                        routeLabels.put(trip.getRouteId(), routeLabel);
                    }
                }
            }

            int passengerTotal = 0;
            if (trip.getId() != null) {
                if (cacheOnly) {
                    passengerTotal = passengerRecordRepository.getCachedPassengerTotal(trip.getId());
                } else {
                    Result<List<PassengerRecordModel>> passengers =
                            passengerRecordRepository.getByTrip(trip.getId());
                    if (passengers.isSuccess() && passengers.getData() != null) {
                        for (PassengerRecordModel record : passengers.getData()) {
                            if (record.getPassengerCount() != null) {
                                passengerTotal += record.getPassengerCount();
                            }
                        }
                    } else {
                        passengerTotal = passengerRecordRepository.getCachedPassengerTotal(trip.getId());
                    }
                }
            }

            items.add(new TripHistoryItem(
                    trip,
                    routeLabel,
                    passengerTotal,
                    DateTimeUtils.formatDuration(trip.getStartedAt(), trip.getEndedAt())
            ));
        }
        return items;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }
}
