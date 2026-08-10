package com.smartbus.driver.repository;

import com.smartbus.driver.database.EntityMapper;
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
import retrofit2.Response;

public class TripRepository {

    private final SmartBusApi api;
    private final TripDao tripDao;

    public TripRepository(TripDao tripDao) {
        this.api = RetrofitClient.getApi();
        this.tripDao = tripDao;
    }

    public Result<TripModel> startTrip(long routeId) {
        try {
            Response<ApiResponse<TripModel>> response =
                    api.startTrip(new CreateTripRequest(routeId)).execute();
            Result<TripModel> result = ApiResponseParser.parse(response);
            cacheTrip(result.getData());
            return result;
        } catch (IOException exception) {
            return ApiResponseParser.fromThrowable(exception);
        }
    }

    public Result<TripModel> getCurrentTrip() {
        try {
            Response<ApiResponse<TripModel>> response = api.getCurrentTrip().execute();
            if (response.code() == 404) {
                return Result.success(null);
            }
            Result<TripModel> result = ApiResponseParser.parse(response);
            if (result.isSuccess()) {
                cacheTrip(result.getData());
            } else if (result.getMessage() != null
                    && result.getMessage().toLowerCase().contains("no in-progress trip")) {
                return Result.success(null);
            }
            return result;
        } catch (IOException exception) {
            TripModel cached = EntityMapper.toTripModel(
                    tripDao.findByStatus(Constants.TRIP_STATUS_IN_PROGRESS)
            );
            if (cached != null) {
                return Result.success(cached);
            }
            return ApiResponseParser.fromThrowable(exception);
        }
    }

    public Result<TripModel> endCurrentTrip() {
        try {
            Response<ApiResponse<TripModel>> response = api.endCurrentTrip().execute();
            Result<TripModel> result = ApiResponseParser.parse(response);
            cacheTrip(result.getData());
            return result;
        } catch (IOException exception) {
            return ApiResponseParser.fromThrowable(exception);
        }
    }

    public Result<UpdateLocationResponse> updateLocation(double latitude, double longitude) {
        try {
            Response<ApiResponse<UpdateLocationResponse>> response =
                    api.updateLocation(new UpdateLocationRequest(latitude, longitude)).execute();
            Result<UpdateLocationResponse> result = ApiResponseParser.parse(response);
            if (result.isSuccess() && result.getData() != null) {
                cacheTrip(result.getData().getTrip());
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
}
