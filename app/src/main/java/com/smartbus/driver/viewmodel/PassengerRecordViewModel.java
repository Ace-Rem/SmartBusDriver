package com.smartbus.driver.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.smartbus.driver.SmartBusApp;
import com.smartbus.driver.model.PassengerRecordModel;
import com.smartbus.driver.model.StopModel;
import com.smartbus.driver.repository.PassengerRecordRepository;
import com.smartbus.driver.repository.RouteRepository;
import com.smartbus.driver.utils.Result;
import com.smartbus.driver.utils.UiState;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PassengerRecordViewModel extends AndroidViewModel {

    private final PassengerRecordRepository passengerRecordRepository;
    private final RouteRepository routeRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<UiState<PassengerRecordModel>> saveState =
            new MutableLiveData<>(UiState.idle());
    private final MutableLiveData<UiState<List<StopModel>>> stopsState =
            new MutableLiveData<>(UiState.idle());

    public PassengerRecordViewModel(@NonNull Application application) {
        super(application);
        SmartBusApp app = (SmartBusApp) application;
        passengerRecordRepository = new PassengerRecordRepository(app.getDatabase().passengerRecordDao());
        routeRepository = new RouteRepository(app.getDatabase().routeDao(), app.getDatabase().stopDao());
    }

    public LiveData<UiState<PassengerRecordModel>> getSaveState() {
        return saveState;
    }

    public LiveData<UiState<List<StopModel>>> getStopsState() {
        return stopsState;
    }

    public void loadStops(Long routeId) {
        if (routeId == null) {
            stopsState.setValue(UiState.empty("Chưa có tuyến đang chạy"));
            return;
        }
        stopsState.setValue(UiState.loading());
        executor.execute(() -> {
            Result<List<StopModel>> result = routeRepository.getStops(routeId);
            if (result.isSuccess()) {
                List<StopModel> data = result.getData();
                if (data == null || data.isEmpty()) {
                    stopsState.postValue(UiState.empty("Không có bến trên tuyến"));
                } else {
                    stopsState.postValue(UiState.success(data));
                }
            } else {
                stopsState.postValue(UiState.error(result.getMessage()));
            }
        });
    }

    public void save(Long tripId, Long stopId, String countText, String note) {
        if (tripId == null) {
            saveState.setValue(UiState.error("Chưa có chuyến đang chạy"));
            return;
        }
        int count;
        try {
            count = Integer.parseInt(countText != null ? countText.trim() : "");
        } catch (NumberFormatException exception) {
            saveState.setValue(UiState.error("Số hành khách không hợp lệ"));
            return;
        }
        if (count < 0) {
            saveState.setValue(UiState.error("Số hành khách phải >= 0"));
            return;
        }
        if (stopId == null) {
            saveState.setValue(UiState.error("Vui lòng chọn bến xuống"));
            return;
        }
        saveState.setValue(UiState.loading());
        executor.execute(() -> {
            Result<PassengerRecordModel> result =
                    passengerRecordRepository.create(tripId, stopId, count, note);
            if (result.isSuccess()) {
                // The local Room row is the source of truth for the UI. The
                // activity-scoped TripViewModel retries the server write in
                // the background, even after this fragment/dialog is closed.
                saveState.postValue(UiState.success(result.getData(), "Đã lưu hành khách"));
            } else {
                saveState.postValue(UiState.error(result.getMessage()));
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }
}
