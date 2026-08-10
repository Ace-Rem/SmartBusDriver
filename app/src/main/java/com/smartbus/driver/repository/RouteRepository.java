package com.smartbus.driver.repository;

import com.smartbus.driver.database.EntityMapper;
import com.smartbus.driver.database.RouteDao;
import com.smartbus.driver.database.RouteEntity;
import com.smartbus.driver.database.StopDao;
import com.smartbus.driver.database.StopEntity;
import com.smartbus.driver.model.ApiResponse;
import com.smartbus.driver.model.RouteModel;
import com.smartbus.driver.model.StopModel;
import com.smartbus.driver.network.RetrofitClient;
import com.smartbus.driver.network.SmartBusApi;
import com.smartbus.driver.utils.ApiResponseParser;
import com.smartbus.driver.utils.Result;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Response;

public class RouteRepository {

    private final SmartBusApi api;
    private final RouteDao routeDao;
    private final StopDao stopDao;

    public RouteRepository(RouteDao routeDao, StopDao stopDao) {
        this.api = RetrofitClient.getApi();
        this.routeDao = routeDao;
        this.stopDao = stopDao;
    }

    public Result<List<RouteModel>> getRoutes() {
        List<RouteModel> cached = EntityMapper.toRouteModels(routeDao.getAll());
        if (!cached.isEmpty()) {
            return Result.success(cached);
        }
        return syncRoutes();
    }

    public Result<List<RouteModel>> syncRoutes() {
        try {
            Response<ApiResponse<List<RouteModel>>> response = api.getRoutes().execute();
            Result<List<RouteModel>> result = ApiResponseParser.parse(response);
            if (result.isSuccess() && result.getData() != null) {
                List<RouteEntity> entities = new ArrayList<>();
                for (RouteModel model : result.getData()) {
                    RouteEntity entity = EntityMapper.toEntity(model);
                    if (entity != null) {
                        entities.add(entity);
                    }
                }
                routeDao.upsertAll(entities);
            }
            return result;
        } catch (IOException exception) {
            return ApiResponseParser.fromThrowable(exception);
        }
    }

    public Result<RouteModel> getRoute(long routeId) {
        try {
            Response<ApiResponse<RouteModel>> response = api.getRoute(routeId).execute();
            Result<RouteModel> result = ApiResponseParser.parse(response);
            if (result.isSuccess() && result.getData() != null) {
                RouteEntity entity = EntityMapper.toEntity(result.getData());
                if (entity != null) {
                    routeDao.upsert(entity);
                }
            }
            return result;
        } catch (IOException exception) {
            RouteModel cached = EntityMapper.toRouteModel(routeDao.findById(routeId));
            if (cached != null) {
                return Result.success(cached);
            }
            return ApiResponseParser.fromThrowable(exception);
        }
    }

    public Result<List<StopModel>> getStops(long routeId) {
        List<StopModel> cached = EntityMapper.toStopModels(stopDao.findByRouteId(routeId));
        if (!cached.isEmpty()) {
            return Result.success(cached);
        }
        return syncStops(routeId);
    }

    public Result<List<StopModel>> syncStops(long routeId) {
        try {
            Response<ApiResponse<List<StopModel>>> response = api.getStops(routeId).execute();
            Result<List<StopModel>> result = ApiResponseParser.parse(response);
            if (result.isSuccess() && result.getData() != null) {
                stopDao.deleteByRouteId(routeId);
                List<StopEntity> entities = new ArrayList<>();
                for (StopModel model : result.getData()) {
                    StopEntity entity = EntityMapper.toEntity(model);
                    if (entity != null) {
                        entities.add(entity);
                    }
                }
                stopDao.upsertAll(entities);
            }
            return result;
        } catch (IOException exception) {
            return ApiResponseParser.fromThrowable(exception);
        }
    }
}
