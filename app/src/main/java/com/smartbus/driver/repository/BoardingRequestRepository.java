package com.smartbus.driver.repository;

import com.smartbus.driver.model.ApiResponse;
import com.smartbus.driver.model.BoardingRequestModel;
import com.smartbus.driver.network.RetrofitClient;
import com.smartbus.driver.network.SmartBusApi;
import com.smartbus.driver.utils.ApiResponseParser;
import com.smartbus.driver.utils.Result;
import java.io.IOException;
import java.util.List;
import retrofit2.Response;

public class BoardingRequestRepository {

    private final SmartBusApi api;

    public BoardingRequestRepository() {
        api = RetrofitClient.getApi();
    }

    public Result<List<BoardingRequestModel>> listByTrip(long tripId) {
        try {
            Response<ApiResponse<List<BoardingRequestModel>>> response =
                    api.getBoardingRequests(tripId).execute();
            return ApiResponseParser.parse(response);
        } catch (IOException exception) {
            return ApiResponseParser.fromThrowable(exception);
        }
    }

    public Result<BoardingRequestModel> confirmBoarding(long requestId) {
        try {
            Response<ApiResponse<BoardingRequestModel>> response =
                    api.confirmBoarding(requestId).execute();
            return ApiResponseParser.parse(response);
        } catch (IOException exception) {
            return ApiResponseParser.fromThrowable(exception);
        }
    }
}
