package com.smartbus.driver.repository;

import com.smartbus.driver.database.EntityMapper;
import com.smartbus.driver.database.PassengerRecordDao;
import com.smartbus.driver.database.PassengerRecordEntity;
import com.smartbus.driver.model.ApiResponse;
import com.smartbus.driver.model.CreatePassengerRecordRequest;
import com.smartbus.driver.model.PassengerRecordModel;
import com.smartbus.driver.network.RetrofitClient;
import com.smartbus.driver.network.SmartBusApi;
import com.smartbus.driver.utils.ApiResponseParser;
import com.smartbus.driver.utils.Result;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Response;

public class PassengerRecordRepository {

    private final SmartBusApi api;
    private final PassengerRecordDao passengerRecordDao;

    public PassengerRecordRepository(PassengerRecordDao passengerRecordDao) {
        this.api = RetrofitClient.getApi();
        this.passengerRecordDao = passengerRecordDao;
    }

    public Result<PassengerRecordModel> create(Long tripId, Long stopId, int passengerCount, String note) {
        try {
            CreatePassengerRecordRequest request =
                    new CreatePassengerRecordRequest(tripId, stopId, passengerCount, note);
            Response<ApiResponse<PassengerRecordModel>> response =
                    api.createPassengerRecord(request).execute();
            Result<PassengerRecordModel> result = ApiResponseParser.parse(response);
            if (result.isSuccess() && result.getData() != null) {
                PassengerRecordEntity entity = EntityMapper.toEntity(result.getData());
                if (entity != null) {
                    passengerRecordDao.upsert(entity);
                }
            }
            return result;
        } catch (IOException exception) {
            return ApiResponseParser.fromThrowable(exception);
        }
    }

    public Result<List<PassengerRecordModel>> getByTrip(long tripId) {
        try {
            Response<ApiResponse<List<PassengerRecordModel>>> response =
                    api.getPassengerRecords(tripId).execute();
            Result<List<PassengerRecordModel>> result = ApiResponseParser.parse(response);
            if (result.isSuccess() && result.getData() != null) {
                passengerRecordDao.deleteByTripId(tripId);
                List<PassengerRecordEntity> entities = new ArrayList<>();
                for (PassengerRecordModel model : result.getData()) {
                    PassengerRecordEntity entity = EntityMapper.toEntity(model);
                    if (entity != null) {
                        entities.add(entity);
                    }
                }
                passengerRecordDao.upsertAll(entities);
            }
            return result;
        } catch (IOException exception) {
            List<PassengerRecordEntity> cached = passengerRecordDao.findByTripId(tripId);
            if (cached != null && !cached.isEmpty()) {
                List<PassengerRecordModel> models = new ArrayList<>();
                for (PassengerRecordEntity entity : cached) {
                    models.add(EntityMapper.toPassengerRecordModel(entity));
                }
                return Result.success(models);
            }
            return ApiResponseParser.fromThrowable(exception);
        }
    }

    public int getCachedPassengerTotal(long tripId) {
        return passengerRecordDao.sumPassengerCount(tripId);
    }
}
