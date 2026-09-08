package com.smartbus.driver.repository;

import com.smartbus.driver.database.EntityMapper;
import com.smartbus.driver.database.RouteEntity;
import com.smartbus.driver.database.SmartBusDatabase;
import com.smartbus.driver.database.StopEntity;
import com.smartbus.driver.model.ApiResponse;
import com.smartbus.driver.model.BootstrapModel;
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

public class BootstrapRepository {

    private final SmartBusApi api;
    private final SmartBusDatabase database;

    public BootstrapRepository(SmartBusDatabase database) {
        this.api = RetrofitClient.getApi();
        this.database = database;
    }

    public Result<BootstrapModel> sync() {
        if (hasCoreCache()) {
            return Result.success(null);
        }
        try {
            Response<ApiResponse<BootstrapModel>> response = api.bootstrap().execute();
            Result<BootstrapModel> result = ApiResponseParser.parse(response);
            if (result.isSuccess() && result.getData() != null) {
                cache(result.getData());
            }
            return result;
        } catch (IOException exception) {
            return ApiResponseParser.fromThrowable(exception);
        }
    }

    private boolean hasCoreCache() {
        return !database.routeDao().getAll().isEmpty()
                && !database.stopDao().getAll().isEmpty();
    }

    private void cache(BootstrapModel data) {
        List<RouteEntity> routeEntities = new ArrayList<>();
        List<StopEntity> stopEntities = new ArrayList<>();
        List<RouteModel> routes = data.getRoutes();
        if (routes != null) {
            for (RouteModel route : routes) {
                RouteEntity entity = EntityMapper.toEntity(route);
                if (entity != null) {
                    routeEntities.add(entity);
                }
            }
        }
        List<StopModel> stops = data.getStops();
        if (stops != null) {
            for (StopModel stop : stops) {
                StopEntity entity = EntityMapper.toEntity(stop);
                if (entity != null) {
                    stopEntities.add(entity);
                }
            }
        }
        database.routeDao().upsertAll(routeEntities);
        database.stopDao().upsertAll(stopEntities);
    }
}
