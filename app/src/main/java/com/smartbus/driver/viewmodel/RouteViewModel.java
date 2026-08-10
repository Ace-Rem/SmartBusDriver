package com.smartbus.driver.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.smartbus.driver.SmartBusApp;
import com.smartbus.driver.model.RouteModel;
import com.smartbus.driver.model.StopModel;
import com.smartbus.driver.repository.RouteRepository;
import com.smartbus.driver.utils.Result;
import com.smartbus.driver.utils.UiState;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RouteViewModel extends AndroidViewModel {

    private final RouteRepository routeRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<UiState<List<RouteModel>>> routesState =
            new MutableLiveData<>(UiState.loading());
    private final MutableLiveData<UiState<List<StopModel>>> stopsState =
            new MutableLiveData<>(UiState.idle());
    private final MutableLiveData<RouteModel> selectedRoute = new MutableLiveData<>();

    public RouteViewModel(@NonNull Application application) {
        super(application);
        SmartBusApp app = (SmartBusApp) application;
        routeRepository = new RouteRepository(app.getDatabase().routeDao(), app.getDatabase().stopDao());
    }

    public LiveData<UiState<List<RouteModel>>> getRoutesState() {
        return routesState;
    }

    public LiveData<UiState<List<StopModel>>> getStopsState() {
        return stopsState;
    }

    public LiveData<RouteModel> getSelectedRoute() {
        return selectedRoute;
    }

    public void loadRoutes() {
        routesState.setValue(UiState.loading());
        executor.execute(() -> {
            Result<List<RouteModel>> result = routeRepository.getRoutes();
            if (result.isSuccess()) {
                List<RouteModel> data = result.getData();
                if (data == null || data.isEmpty()) {
                    routesState.postValue(UiState.empty("Chưa có tuyến nào"));
                } else {
                    routesState.postValue(UiState.success(data));
                }
                Result<List<RouteModel>> sync = routeRepository.syncRoutes();
                if (sync.isSuccess() && sync.getData() != null && !sync.getData().isEmpty()) {
                    routesState.postValue(UiState.success(sync.getData()));
                }
            } else {
                routesState.postValue(UiState.error(result.getMessage()));
            }
        });
    }

    public void selectRoute(RouteModel route) {
        selectedRoute.setValue(route);
        if (route == null || route.getId() == null) {
            return;
        }
        stopsState.setValue(UiState.loading());
        executor.execute(() -> {
            Result<List<StopModel>> result = routeRepository.getStops(route.getId());
            if (result.isSuccess()) {
                List<StopModel> data = result.getData();
                if (data == null || data.isEmpty()) {
                    stopsState.postValue(UiState.empty("Tuyến chưa có bến"));
                } else {
                    stopsState.postValue(UiState.success(data));
                }
                Result<List<StopModel>> sync = routeRepository.syncStops(route.getId());
                if (sync.isSuccess() && sync.getData() != null && !sync.getData().isEmpty()) {
                    stopsState.postValue(UiState.success(sync.getData()));
                }
            } else {
                stopsState.postValue(UiState.error(result.getMessage()));
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }
}
