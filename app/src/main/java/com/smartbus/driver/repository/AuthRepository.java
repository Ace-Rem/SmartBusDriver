package com.smartbus.driver.repository;

import com.smartbus.driver.database.DriverDao;
import com.smartbus.driver.database.DriverEntity;
import com.smartbus.driver.database.EntityMapper;
import com.smartbus.driver.database.SmartBusDatabase;
import com.smartbus.driver.model.DriverModel;
import com.smartbus.driver.model.LoginRequest;
import com.smartbus.driver.model.LoginResponse;
import com.smartbus.driver.network.RetrofitClient;
import com.smartbus.driver.network.SmartBusApi;
import com.smartbus.driver.utils.ApiResponseParser;
import com.smartbus.driver.utils.Result;
import com.smartbus.driver.utils.TokenManager;
import java.io.IOException;
import retrofit2.Response;

public class AuthRepository {

    private final SmartBusApi api;
    private final TokenManager tokenManager;
    private final DriverDao driverDao;
    private final SmartBusDatabase database;

    public AuthRepository(TokenManager tokenManager, SmartBusDatabase database) {
        this.api = RetrofitClient.getApi();
        this.tokenManager = tokenManager;
        this.database = database;
        this.driverDao = database.driverDao();
    }

    public Result<LoginResponse> login(String username, String password) {
        try {
            Response<com.smartbus.driver.model.ApiResponse<LoginResponse>> response =
                    api.login(new LoginRequest(username, password)).execute();
            Result<LoginResponse> result = ApiResponseParser.parse(response);
            if (result.isSuccess() && result.getData() != null) {
                LoginResponse loginResponse = result.getData();
                tokenManager.saveSession(
                        loginResponse.getAccessToken(),
                        loginResponse.getTokenType(),
                        loginResponse.getDriver()
                );
                DriverEntity entity = EntityMapper.toEntity(loginResponse.getDriver());
                if (entity != null) {
                    driverDao.upsert(entity);
                }
            }
            return result;
        } catch (IOException exception) {
            return ApiResponseParser.fromThrowable(exception);
        }
    }

    public Result<DriverModel> getMe() {
        try {
            Response<com.smartbus.driver.model.ApiResponse<DriverModel>> response = api.getMe().execute();
            Result<DriverModel> result = ApiResponseParser.parse(response);
            if (result.isSuccess() && result.getData() != null) {
                DriverEntity entity = EntityMapper.toEntity(result.getData());
                if (entity != null) {
                    driverDao.upsert(entity);
                }
                tokenManager.saveSession(
                        tokenManager.getAccessToken(),
                        "Bearer",
                        result.getData()
                );
            }
            return result;
        } catch (IOException exception) {
            DriverModel cached = tokenManager.getCachedDriver();
            if (cached != null) {
                return Result.success(cached);
            }
            return ApiResponseParser.fromThrowable(exception);
        }
    }

    public void logout() {
        tokenManager.clearSession();
        driverDao.clear();
        database.tripDao().clear();
        database.routeDao().clear();
        database.stopDao().clear();
        database.passengerRecordDao().clear();
    }

    public boolean isLoggedIn() {
        return tokenManager.isLoggedIn();
    }

    public DriverModel getCachedDriver() {
        return tokenManager.getCachedDriver();
    }

    public TokenManager getTokenManager() {
        return tokenManager;
    }
}
