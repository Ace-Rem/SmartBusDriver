package com.smartbus.driver.repository;

import com.smartbus.driver.database.EntityMapper;
import com.smartbus.driver.database.PassengerRecordDao;
import com.smartbus.driver.database.TripDao;
import com.smartbus.driver.database.TripEntity;
import com.smartbus.driver.model.ApiResponse;
import com.smartbus.driver.model.CreateTripRequest;
import com.smartbus.driver.model.StopModel;
import com.smartbus.driver.model.TripModel;
import com.smartbus.driver.model.UpdateLocationRequest;
import com.smartbus.driver.model.UpdateLocationResponse;
import com.smartbus.driver.network.RetrofitClient;
import com.smartbus.driver.network.SmartBusApi;
import com.smartbus.driver.utils.ApiResponseParser;
import com.smartbus.driver.utils.Constants;
import com.smartbus.driver.utils.Result;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import retrofit2.Response;

public class TripRepository {

    private final SmartBusApi api;
    private final TripDao tripDao;
    private final PassengerRecordDao passengerRecordDao;

    public TripRepository(TripDao tripDao) {
        this(tripDao, null);
    }

    public TripRepository(TripDao tripDao, PassengerRecordDao passengerRecordDao) {
        this.api = RetrofitClient.getApi();
        this.tripDao = tripDao;
        this.passengerRecordDao = passengerRecordDao;
    }

    public Result<TripModel> startTrip(long routeId) {
        TripModel localTrip = startTripLocally(routeId);
        return Result.success(localTrip);
    }

    public TripModel startTripLocally(long routeId) {
        TripEntity existing = tripDao.findByStatus(Constants.TRIP_STATUS_IN_PROGRESS);
        if (existing != null) {
            return EntityMapper.toTripModel(existing);
        }
        TripEntity entity = new TripEntity();
        // Negative IDs are explicitly reserved for local-only trips. Once
        // deferred sync returns, the real backend ID can replace this row.
        entity.id = -System.currentTimeMillis();
        entity.routeId = routeId;
        entity.status = Constants.TRIP_STATUS_IN_PROGRESS;
        entity.startedAt = now();
        // The local trip is immediately usable by Map, BLE and AI. Backend
        // synchronization is deliberately deferred to the background.
        entity.pendingStartSync = true;
        tripDao.upsert(entity);
        return EntityMapper.toTripModel(entity);
    }

    public void syncPendingTrips() {
        List<TripEntity> pending = tripDao.findPendingSync();
        if (pending == null || pending.isEmpty()) {
            return;
        }
        for (TripEntity entity : pending) {
            TripEntity latest = entity.pendingStartSync ? syncStart(entity) : entity;
            if (latest != null && latest.pendingEndSync && latest.id > 0) {
                syncEnd(latest);
            }
        }
    }

    private TripEntity syncStart(TripEntity local) {
        try {
            Response<ApiResponse<TripModel>> response =
                    api.startTrip(new CreateTripRequest(local.routeId)).execute();
            Result<TripModel> result = ApiResponseParser.parse(response);
            if (result.isSuccess() && result.getData() != null) {
                TripEntity synced = EntityMapper.toEntity(result.getData());
                if (synced != null) {
                    if (synced.currentStopId == null) {
                        synced.currentStopId = local.currentStopId;
                    }
                    if (synced.currentLatitude == null) {
                        synced.currentLatitude = local.currentLatitude;
                    }
                    if (synced.currentLongitude == null) {
                        synced.currentLongitude = local.currentLongitude;
                    }
                    synced.pendingStartSync = false;
                    synced.pendingEndSync = local.pendingEndSync;
                    if (Constants.TRIP_STATUS_IN_PROGRESS.equals(local.status)) {
                        synced.status = local.status;
                    }
                    if (local.pendingEndSync) {
                        synced.status = local.status;
                        synced.endedAt = local.endedAt;
                    }
                    synced.localSyncError = null;
                    if (local.id < 0) {
                        tripDao.deleteById(local.id);
                        if (passengerRecordDao != null) {
                            passengerRecordDao.migrateTripId(
                                    local.id,
                                    synced.id,
                                    local.id + ":",
                                    synced.id + ":"
                            );
                        }
                        tripDao.upsert(synced);
                    } else {
                        synced.id = local.id;
                        tripDao.upsert(synced);
                    }
                    return synced;
                }
            } else {
                local.localSyncError = result.getMessage();
                tripDao.upsert(local);
            }
        } catch (IOException exception) {
            local.localSyncError = exception.getMessage();
            tripDao.upsert(local);
        }
        return local;
    }

    private void syncEnd(TripEntity local) {
        if (local.id < 0) {
            local.pendingEndSync = false;
            local.pendingStartSync = false;
            local.localSyncError = "Trip ended locally before backend start sync completed";
            tripDao.upsert(local);
            return;
        }
        try {
            Response<ApiResponse<TripModel>> response = api.endCurrentTrip().execute();
            Result<TripModel> result = ApiResponseParser.parse(response);
            if (result.isSuccess() && result.getData() != null) {
                local.pendingStartSync = false;
                local.pendingEndSync = false;
                local.localSyncError = null;
                tripDao.upsert(local);
            } else {
                local.localSyncError = result.getMessage();
                tripDao.upsert(local);
            }
        } catch (IOException exception) {
            local.localSyncError = exception.getMessage();
            tripDao.upsert(local);
        }
    }

    public Result<TripModel> getCurrentTrip() {
        TripModel cached = EntityMapper.toTripModel(
                tripDao.findByStatus(Constants.TRIP_STATUS_IN_PROGRESS)
        );
        return Result.success(cached);
    }

    public Result<TripModel> endCurrentTrip() {
        TripEntity active = tripDao.findByStatus(Constants.TRIP_STATUS_IN_PROGRESS);
        if (active == null) {
            return Result.success(null);
        }
        active.status = Constants.TRIP_STATUS_COMPLETED;
        active.endedAt = now();
        active.pendingStartSync = true;
        active.pendingEndSync = true;
        tripDao.upsert(active);
        TripModel ended = EntityMapper.toTripModel(active);
        return Result.success(ended);
    }

    public void updateLocalLocation(long tripId, double latitude, double longitude) {
        tripDao.updateLocation(tripId, latitude, longitude);
    }

    public void updateLocalCurrentStop(long tripId, Long stopId) {
        tripDao.updateCurrentStop(tripId, stopId);
    }

    public Result<UpdateLocationResponse> updateLocation(double latitude, double longitude) {
        try {
            Response<ApiResponse<UpdateLocationResponse>> response =
                    api.updateLocation(new UpdateLocationRequest(latitude, longitude)).execute();
            Result<UpdateLocationResponse> result = ApiResponseParser.parse(response);
            if (result.isSuccess() && result.getData() != null) {
                TripModel remoteTrip = result.getData().getTrip();
                TripEntity localActive = tripDao.findByStatus(Constants.TRIP_STATUS_IN_PROGRESS);
                if (localActive != null && remoteTrip != null) {
                    // Keep the local identity/status authoritative while the
                    // server catches up. Only merge facts produced by GPS.
                    if (remoteTrip.getCurrentStopId() != null) {
                        localActive.currentStopId = remoteTrip.getCurrentStopId();
                    }
                    if (remoteTrip.getCurrentLatitude() != null && remoteTrip.getCurrentLongitude() != null) {
                        localActive.currentLatitude = remoteTrip.getCurrentLatitude();
                        localActive.currentLongitude = remoteTrip.getCurrentLongitude();
                    }
                    tripDao.upsert(localActive);
                } else {
                    cacheTrip(remoteTrip);
                }
            }
            return result;
        } catch (IOException exception) {
            return ApiResponseParser.fromThrowable(exception);
        }
    }

    public Result<StopModel> getCurrentStop() {
        try {
            Response<ApiResponse<StopModel>> response = api.getCurrentStop().execute();
            if (response.code() == 404) {
                return Result.success(null);
            }
            Result<StopModel> result = ApiResponseParser.parse(response);
            if (!result.isSuccess()
                    && result.getMessage() != null
                    && result.getMessage().toLowerCase().contains("not set")) {
                return Result.success(null);
            }
            return result;
        } catch (IOException exception) {
            return ApiResponseParser.fromThrowable(exception);
        }
    }

    public Result<StopModel> getNextStop() {
        try {
            Response<ApiResponse<StopModel>> response = api.getNextStop().execute();
            if (response.code() == 404) {
                return Result.success(null);
            }
            Result<StopModel> result = ApiResponseParser.parse(response);
            if (!result.isSuccess()
                    && result.getMessage() != null
                    && (result.getMessage().toLowerCase().contains("not set")
                    || result.getMessage().toLowerCase().contains("last stop"))) {
                return Result.success(null);
            }
            return result;
        } catch (IOException exception) {
            return ApiResponseParser.fromThrowable(exception);
        }
    }

    public Result<List<TripModel>> getHistory() {
        Result<List<TripModel>> result = syncHistory();
        if (result.isSuccess()) {
            return result;
        }
        List<TripModel> cached = getCachedHistory();
        if (!cached.isEmpty()) {
            return Result.success(cached);
        }
        return result;
    }

    public List<TripModel> getCachedHistory() {
        return EntityMapper.toTripModels(tripDao.getAll());
    }

    public Result<List<TripModel>> syncHistory() {
        try {
            Response<ApiResponse<List<TripModel>>> response = api.getTripHistory().execute();
            Result<List<TripModel>> result = ApiResponseParser.parse(response);
            if (result.isSuccess() && result.getData() != null) {
                List<TripEntity> entities = new ArrayList<>();
                for (TripModel model : result.getData()) {
                    TripEntity entity = EntityMapper.toEntity(model);
                    if (entity != null) {
                        entities.add(entity);
                    }
                }
                tripDao.upsertAll(entities);
            }
            return result;
        } catch (IOException exception) {
            return ApiResponseParser.fromThrowable(exception);
        }
    }

    private void cacheTrip(TripModel trip) {
        TripEntity entity = EntityMapper.toEntity(trip);
        if (entity != null) {
            tripDao.upsert(entity);
        }
    }

    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
