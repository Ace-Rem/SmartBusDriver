package com.smartbus.driver.repository;

import com.smartbus.driver.database.EntityMapper;
import com.smartbus.driver.database.PassengerRecordDao;
import com.smartbus.driver.database.PassengerRecordEntity;
import com.smartbus.driver.model.ApiResponse;
import com.smartbus.driver.model.BoardingRequestModel;
import com.smartbus.driver.model.CreatePassengerRecordRequest;
import com.smartbus.driver.model.PassengerRecordModel;
import com.smartbus.driver.model.StopModel;
import com.smartbus.driver.model.TripModel;
import com.smartbus.driver.network.RetrofitClient;
import com.smartbus.driver.network.SmartBusApi;
import com.smartbus.driver.utils.ApiResponseParser;
import com.smartbus.driver.utils.Result;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import retrofit2.Response;

public class PassengerRecordRepository {

    private final SmartBusApi api;
    private final PassengerRecordDao passengerRecordDao;

    public PassengerRecordRepository(PassengerRecordDao passengerRecordDao) {
        this.api = RetrofitClient.getApi();
        this.passengerRecordDao = passengerRecordDao;
    }

    public Result<PassengerRecordModel> create(Long tripId, Long stopId, int passengerCount, String note) {
        PassengerRecordModel local = createLocal(
                tripId,
                null,
                stopId,
                null,
                null,
                null,
                passengerCount,
                "ON_BOARD",
                note,
                "MANUAL",
                tripId + ":manual:" + System.currentTimeMillis()
        );
        return Result.success(local);
    }

    public Result<PassengerRecordModel> createFromBle(
            long tripId,
            Long destinationStopId,
            Double destinationLatitude,
            Double destinationLongitude,
            String passengerIdentifier,
            String note
    ) {
        if (passengerIdentifier == null || passengerIdentifier.isBlank()) {
            return Result.error("BLE payload thiếu mã hành khách/thiết bị.");
        }
        String idempotencyKey = tripId + ":ble:" + passengerIdentifier;
        PassengerRecordEntity existing = passengerRecordDao.findByTripAndIdempotencyKey(tripId, idempotencyKey);
        if (existing != null) {
            return Result.success(EntityMapper.toPassengerRecordModel(existing), "Hành khách đã được ghi nhận trước đó.");
        }
        PassengerRecordModel local = createLocal(
                tripId,
                null,
                destinationStopId,
                destinationLatitude,
                destinationLongitude,
                passengerIdentifier,
                1,
                "PENDING",
                note,
                "BLE",
                idempotencyKey
        );
        return Result.success(local);
    }

    public Result<PassengerRecordModel> createFromFastSignal(
            long tripId,
            long signalId,
            Long destinationStopId,
            String note
    ) {
        return createFromFastSignal(tripId, signalId, null, destinationStopId, null, note);
    }

    public Result<PassengerRecordModel> createFromFastSignal(
            long tripId,
            long signalId,
            Long boardingStopId,
            Long destinationStopId,
            String passengerIdentifier,
            String note
    ) {
        String identifier = passengerIdentifier == null || passengerIdentifier.isBlank()
                ? "FAST-" + signalId : passengerIdentifier;
        String idempotencyKey = tripId + ":fast:" + signalId;
        PassengerRecordEntity existing = passengerRecordDao.findByTripAndIdempotencyKey(
                tripId,
                idempotencyKey
        );
        if (existing == null && passengerIdentifier != null && !passengerIdentifier.isBlank()) {
            existing = passengerRecordDao.findPendingByPassengerIdentifier(
                    tripId, passengerIdentifier, destinationStopId);
            if (existing != null) {
                existing.idempotencyKey = idempotencyKey;
                existing.source = "BACKEND_FAST";
                existing.boardingStopId = boardingStopId;
                existing.destinationStopId = destinationStopId;
                passengerRecordDao.upsert(existing);
            }
        }
        if (existing != null) {
            return Result.success(
                    EntityMapper.toPassengerRecordModel(existing),
                    "Tín hiệu backend đã được ghi nhận trước đó."
            );
        }
        PassengerRecordModel local = createLocal(
                tripId,
                boardingStopId,
                destinationStopId,
                null,
                null,
                identifier,
                1,
                "PENDING",
                note,
                "BACKEND_FAST",
                idempotencyKey
        );
        return Result.success(local, "Đã nhận yêu cầu lên xe.");
    }

    private PassengerRecordModel createLocal(
            Long tripId,
            Long boardingStopId,
            Long destinationStopId,
            Double destinationLatitude,
            Double destinationLongitude,
            String passengerIdentifier,
            int passengerCount,
            String status,
            String note,
            String source,
            String idempotencyKey
    ) {
        PassengerRecordEntity entity = new PassengerRecordEntity();
        entity.id = -System.currentTimeMillis();
        entity.tripId = tripId == null ? 0L : tripId;
        entity.stopId = destinationStopId;
        entity.boardingStopId = boardingStopId;
        entity.destinationStopId = destinationStopId;
        entity.destinationLatitude = destinationLatitude;
        entity.destinationLongitude = destinationLongitude;
        entity.passengerIdentifier = passengerIdentifier;
        entity.status = status;
        entity.passengerCount = passengerCount;
        entity.recordedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        entity.note = note;
        entity.source = source;
        entity.idempotencyKey = idempotencyKey;
        // Manual records are real backend data. Fast/BLE records remain local
        // until the driver explicitly accepts the passenger.
        entity.pendingSync = "MANUAL".equals(source);
        passengerRecordDao.upsert(entity);
        return EntityMapper.toPassengerRecordModel(entity);
    }

    public void syncPending() {
        List<PassengerRecordEntity> pending = passengerRecordDao.findPendingSync();
        if (pending == null) return;
        for (PassengerRecordEntity record : pending) {
            syncPending(record);
        }
    }

    private void syncPending(PassengerRecordEntity local) {
        if (local.tripId <= 0) {
            return;
        }
        try {
            CreatePassengerRecordRequest request =
                    new CreatePassengerRecordRequest(
                            local.tripId,
                            local.boardingStopId,
                            local.destinationStopId != null ? local.destinationStopId : local.stopId,
                            local.passengerCount,
                            local.note,
                            local.source,
                            local.idempotencyKey
                    );
            Response<ApiResponse<PassengerRecordModel>> response =
                    api.createPassengerRecord(request).execute();
            Result<PassengerRecordModel> result = ApiResponseParser.parse(response);
            if (result.isSuccess() && result.getData() != null) {
                PassengerRecordEntity entity = EntityMapper.toEntity(result.getData());
                if (entity != null) {
                    entity.status = local.status;
                    entity.passengerIdentifier = local.passengerIdentifier;
                    entity.source = local.source;
                    entity.idempotencyKey = local.idempotencyKey;
                    entity.pendingSync = false;
                    if (local.id < 0) {
                        passengerRecordDao.deleteById(local.id);
                        passengerRecordDao.upsert(entity);
                    } else {
                        entity.id = local.id;
                        passengerRecordDao.upsert(entity);
                    }
                } else {
                    local.pendingSync = false;
                    passengerRecordDao.upsert(local);
                }
            }
        } catch (IOException ignored) {
        }
    }

    public Result<PassengerRecordModel> createRemote(Long tripId, Long stopId, int passengerCount, String note) {
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
        List<PassengerRecordEntity> cached = passengerRecordDao.findByTripId(tripId);
        List<PassengerRecordModel> models = new ArrayList<>();
        if (cached != null) {
            for (PassengerRecordEntity entity : cached) {
                models.add(EntityMapper.toPassengerRecordModel(entity));
            }
        }
        return Result.success(models);
    }

    public List<PassengerRecordModel> getCachedByTrip(long tripId) {
        List<PassengerRecordEntity> cached = passengerRecordDao.findByTripId(tripId);
        List<PassengerRecordModel> models = new ArrayList<>();
        if (cached == null) {
            return models;
        }
        for (PassengerRecordEntity entity : cached) {
            models.add(EntityMapper.toPassengerRecordModel(entity));
        }
        return models;
    }

    public PassengerRecordModel getById(long recordId) {
        return EntityMapper.toPassengerRecordModel(passengerRecordDao.findById(recordId));
    }

    public int getCachedPassengerTotal(long tripId) {
        return passengerRecordDao.sumPassengerCountByStatus(tripId, "ON_BOARD");
    }

    public int getCachedAlightingTotal(long tripId, long destinationStopId) {
        return passengerRecordDao.sumPassengerCountByDestinationAndStatus(tripId, destinationStopId, "ON_BOARD");
    }

    public int getCachedAlightingTotal(long tripId, double latitude, double longitude) {
        return passengerRecordDao.sumPassengerCountByDestinationCoordinatesAndStatus(
                tripId,
                latitude,
                longitude,
                "ON_BOARD"
        );
    }

    public List<BoardingRequestModel> getLocalBoardingRequests(long tripId) {
        return toBoardingRequests(passengerRecordDao.findByTripIdAndStatus(tripId, "PENDING"));
    }

    public Result<PassengerRecordModel> confirmLocalBoarding(long recordId) {
        PassengerRecordEntity record = passengerRecordDao.findById(recordId);
        if (record == null) {
            return Result.error("Không tìm thấy bản ghi hành khách.");
        }
        record.status = "ON_BOARD";
        record.pendingSync = true;
        passengerRecordDao.upsert(record);
        syncPending(record);
        PassengerRecordEntity synced = passengerRecordDao.findByTripAndIdempotencyKey(
                record.tripId,
                record.idempotencyKey
        );
        if (synced == null) {
            // The API may be temporarily unavailable. Keep the local record
            // visible and let the next background sync retry it.
            synced = passengerRecordDao.findById(recordId);
        }
        return Result.success(
                EntityMapper.toPassengerRecordModel(synced),
                "Đã thêm hành khách vào chuyến"
        );
    }

    public Long fastSignalId(long recordId) {
        PassengerRecordEntity entity = passengerRecordDao.findById(recordId);
        if (entity == null || entity.idempotencyKey == null) {
            return null;
        }
        String prefix = entity.tripId + ":fast:";
        if (!entity.idempotencyKey.startsWith(prefix)) {
            return null;
        }
        try {
            return Long.parseLong(entity.idempotencyKey.substring(prefix.length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public void removePendingFastSignal(long tripId, long signalId) {
        passengerRecordDao.deletePendingByTripAndIdempotencyKey(
                tripId, tripId + ":fast:" + signalId);
    }

    public void migrateTripId(long oldTripId, long newTripId) {
        if (oldTripId == newTripId) return;
        passengerRecordDao.migrateTripId(
                oldTripId,
                newTripId,
                oldTripId + ":",
                newTripId + ":"
        );
    }

    public void removePendingFastSignalsNotIn(long tripId, List<Long> activeSignalIds) {
        Set<Long> active = new HashSet<>();
        if (activeSignalIds != null) active.addAll(activeSignalIds);
        List<PassengerRecordEntity> pending = passengerRecordDao.findByTripIdAndStatus(tripId, "PENDING");
        if (pending == null) return;
        String prefix = tripId + ":fast:";
        for (PassengerRecordEntity record : pending) {
            if (record.idempotencyKey == null || !record.idempotencyKey.startsWith(prefix)) continue;
            try {
                long signalId = Long.parseLong(record.idempotencyKey.substring(prefix.length()));
                if (!active.contains(signalId)) passengerRecordDao.deleteById(record.id);
            } catch (NumberFormatException ignored) {
                // Keep malformed local data visible for manual inspection.
            }
        }
    }

    public void markDroppedOffByCoordinates(long tripId, double latitude, double longitude) {
        List<PassengerRecordEntity> records = passengerRecordDao.findByDestinationCoordinatesAndStatus(
                tripId,
                latitude,
                longitude,
                "ON_BOARD"
        );
        if (records == null) {
            return;
        }
        for (PassengerRecordEntity record : records) {
            passengerRecordDao.updateStatus(record.id, "DROPPED_OFF");
        }
    }

    private List<BoardingRequestModel> toBoardingRequests(List<PassengerRecordEntity> records) {
        List<BoardingRequestModel> requests = new ArrayList<>();
        if (records == null) {
            return requests;
        }
        for (PassengerRecordEntity record : records) {
            BoardingRequestModel request = new BoardingRequestModel();
            request.setId(record.id);
            request.setPassengerRecordId(record.id);
            request.setStatus(record.status);
            request.setBluetoothIdentifier(record.passengerIdentifier);
            request.setRequestedAt(record.recordedAt);
            request.setNote(record.note);
            TripModel trip = new TripModel();
            trip.setId(record.tripId);
            request.setTrip(trip);
            StopModel boarding = new StopModel();
            boarding.setId(record.boardingStopId);
            boarding.setName(record.boardingStopId == null
                    ? null : "Bến lên #" + record.boardingStopId);
            request.setBoardingStop(boarding);
            StopModel destination = new StopModel();
            destination.setId(record.destinationStopId);
            destination.setLatitude(record.destinationLatitude);
            destination.setLongitude(record.destinationLongitude);
            destination.setName(destinationName(record));
            request.setDestinationStop(destination);
            requests.add(request);
        }
        return requests;
    }

    private String destinationName(PassengerRecordEntity record) {
        if (record.destinationLatitude == null || record.destinationLongitude == null) {
            if (record.destinationStopId != null) {
                return "Bến xuống #" + record.destinationStopId;
            }
            return "Bến xuống đã chọn";
        }
        return String.format("%.5f, %.5f", record.destinationLatitude, record.destinationLongitude);
    }
}
