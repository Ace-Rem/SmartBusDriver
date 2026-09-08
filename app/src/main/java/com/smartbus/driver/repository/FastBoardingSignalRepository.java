package com.smartbus.driver.repository;

import com.smartbus.driver.model.ApiResponse;
import com.smartbus.driver.model.FastBoardingSignalModel;
import com.smartbus.driver.network.RetrofitClient;
import com.smartbus.driver.network.SmartBusApi;
import com.smartbus.driver.utils.ApiResponseParser;
import com.smartbus.driver.utils.Result;
import java.io.IOException;
import java.util.List;
import retrofit2.Response;

public class FastBoardingSignalRepository {

    private final SmartBusApi api;

    public FastBoardingSignalRepository() {
        api = RetrofitClient.getApi();
    }

    public Result<List<FastBoardingSignalModel>> poll(long routeId, long afterId) {
        try {
            Response<ApiResponse<List<FastBoardingSignalModel>>> response =
                    api.getFastBoardingSignals(routeId, afterId).execute();
            return ApiResponseParser.parse(response);
        } catch (IOException exception) {
            return ApiResponseParser.fromThrowable(exception);
        }
    }
}
