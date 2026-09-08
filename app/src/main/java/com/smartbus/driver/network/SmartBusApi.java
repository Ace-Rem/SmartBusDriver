package com.smartbus.driver.network;

import com.smartbus.driver.model.AiAssistantRequest;
import com.smartbus.driver.model.AiAssistantResponse;
import com.smartbus.driver.model.AiSummaryRequest;
import com.smartbus.driver.model.ApiResponse;
import com.smartbus.driver.model.BoardingRequestModel;
import com.smartbus.driver.model.BootstrapModel;
import com.smartbus.driver.model.CreatePassengerRecordRequest;
import com.smartbus.driver.model.CreateTripRequest;
import com.smartbus.driver.model.DriverModel;
import com.smartbus.driver.model.FastBoardingSignalModel;
import com.smartbus.driver.model.LoginRequest;
import com.smartbus.driver.model.LoginResponse;
import com.smartbus.driver.model.PassengerRecordModel;
import com.smartbus.driver.model.RouteModel;
import com.smartbus.driver.model.StopModel;
import com.smartbus.driver.model.TripModel;
import com.smartbus.driver.model.UpdateLocationRequest;
import com.smartbus.driver.model.UpdateLocationResponse;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface SmartBusApi {

    @POST("auth/login")
    Call<ApiResponse<LoginResponse>> login(@Body LoginRequest request);

    @GET("drivers/me")
    Call<ApiResponse<DriverModel>> getMe();

    @GET("drivers/{id}")
    Call<ApiResponse<DriverModel>> getDriverById(@Path("id") long id);

    @GET("sync/bootstrap")
    Call<ApiResponse<BootstrapModel>> bootstrap();

    @GET("routes")
    Call<ApiResponse<List<RouteModel>>> getRoutes();

    @GET("routes/{routeId}")
    Call<ApiResponse<RouteModel>> getRoute(@Path("routeId") long routeId);

    @GET("routes/{routeId}/stops")
    Call<ApiResponse<List<StopModel>>> getStops(@Path("routeId") long routeId);

    @POST("trips")
    Call<ApiResponse<TripModel>> startTrip(@Body CreateTripRequest request);

    @GET("trips/current")
    Call<ApiResponse<TripModel>> getCurrentTrip();

    @POST("trips/current/end")
    Call<ApiResponse<TripModel>> endCurrentTrip();

    @POST("trips/current/location")
    Call<ApiResponse<UpdateLocationResponse>> updateLocation(@Body UpdateLocationRequest request);

    @GET("trips/current/current-stop")
    Call<ApiResponse<StopModel>> getCurrentStop();

    @GET("trips/current/next-stop")
    Call<ApiResponse<StopModel>> getNextStop();

    @GET("trips/history")
    Call<ApiResponse<List<TripModel>>> getTripHistory();

    @GET("trips/{tripId}")
    Call<ApiResponse<TripModel>> getTrip(@Path("tripId") long tripId);

    @POST("passenger-records")
    Call<ApiResponse<PassengerRecordModel>> createPassengerRecord(@Body CreatePassengerRecordRequest request);

    @GET("passenger-records")
    Call<ApiResponse<List<PassengerRecordModel>>> getPassengerRecords(@Query("tripId") long tripId);

    @GET("boarding-requests")
    Call<ApiResponse<List<BoardingRequestModel>>> getBoardingRequests(@Query("tripId") long tripId);

    @GET("fast-boarding-signals")
    Call<ApiResponse<List<FastBoardingSignalModel>>> getFastBoardingSignals(
            @Query("routeId") long routeId,
            @Query("afterId") long afterId
    );

    @POST("boarding-requests/{id}/confirm-boarding")
    Call<ApiResponse<BoardingRequestModel>> confirmBoarding(@Path("id") long requestId);

    @POST("ai/chat")
    Call<ApiResponse<AiAssistantResponse>> aiChat(@Body AiAssistantRequest request);

    @POST("ai/summary")
    Call<ApiResponse<AiAssistantResponse>> aiSummary(@Body AiSummaryRequest request);
}
