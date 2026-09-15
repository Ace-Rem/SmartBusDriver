package com.smartbus.driver.repository;

import com.smartbus.driver.model.ApiResponse;
import com.smartbus.driver.model.FastBoardingSignalModel;
import com.smartbus.driver.model.FastBoardingSignalAcceptRequest;
import com.smartbus.driver.model.FastBoardingAcceptanceModel;
import com.smartbus.driver.network.RetrofitClient;
import com.smartbus.driver.network.SmartBusApi;
import com.smartbus.driver.utils.ApiResponseParser;
import com.smartbus.driver.utils.Result;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import retrofit2.Call;
import java.util.List;
import retrofit2.Response;

public class FastBoardingSignalRepository {

    private final SmartBusApi api;

    public FastBoardingSignalRepository() {
        api = RetrofitClient.getApi();
    }

    public Result<List<FastBoardingSignalModel>> poll(long routeId, long afterId) {
        try {
            Call<ApiResponse<List<FastBoardingSignalModel>>> call =
                    api.getFastBoardingSignals(routeId, afterId);
            // Do not inherit the normal API/AI timeout here. A stuck poll
            // must not block the next check-in batch for minutes.
            call.timeout().timeout(5, TimeUnit.SECONDS);
            Response<ApiResponse<List<FastBoardingSignalModel>>> response = call.execute();
            return ApiResponseParser.parse(response);
        } catch (IOException exception) {
            return ApiResponseParser.fromThrowable(exception);
        }
    }

    public Result<List<FastBoardingSignalModel>> pollCancelled(long routeId, long afterId) {
        try {
            Call<ApiResponse<List<FastBoardingSignalModel>>> call =
                    api.getCancelledFastBoardingSignals(routeId, afterId);
            call.timeout().timeout(5, TimeUnit.SECONDS);
            Response<ApiResponse<List<FastBoardingSignalModel>>> response = call.execute();
            return ApiResponseParser.parse(response);
        } catch (IOException exception) {
            return ApiResponseParser.fromThrowable(exception);
        }
    }

    public Result<FastBoardingAcceptanceModel> accept(
            long signalId, Long tripId, Long routeId, String passengerIdentifier) {
        try {
            Response<ApiResponse<FastBoardingAcceptanceModel>> response =
                    api.acceptFastBoardingSignal(
                            signalId,
                            new FastBoardingSignalAcceptRequest(tripId, routeId, passengerIdentifier)
                    ).execute();
            return ApiResponseParser.parse(response);
        } catch (IOException exception) {
            return ApiResponseParser.fromThrowable(exception);
        }
    }

    public Result<FastBoardingAcceptanceModel> acceptByIdentity(
            Long tripId, Long routeId, String passengerIdentifier) {
        try {
            Response<ApiResponse<FastBoardingAcceptanceModel>> response =
                    api.acceptFastBoardingSignalByIdentity(
                            new FastBoardingSignalAcceptRequest(tripId, routeId, passengerIdentifier)
                    ).execute();
            return ApiResponseParser.parse(response);
        } catch (IOException exception) {
            return ApiResponseParser.fromThrowable(exception);
        }
    }
}
