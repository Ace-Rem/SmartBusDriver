package com.smartbus.driver.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.smartbus.driver.SmartBusApp;
import com.smartbus.driver.model.DriverModel;
import com.smartbus.driver.model.RouteModel;
import com.smartbus.driver.model.TripModel;
import com.smartbus.driver.repository.AuthRepository;
import com.smartbus.driver.repository.RouteRepository;
import com.smartbus.driver.repository.TripRepository;
import com.smartbus.driver.utils.Constants;
import com.smartbus.driver.utils.Result;
import com.smartbus.driver.utils.UiState;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DriverProfileViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final TripRepository tripRepository;
    private final RouteRepository routeRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<UiState<DriverModel>> profileState =
            new MutableLiveData<>(UiState.loading());
    private final MutableLiveData<String> routeLabel = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loggedOut = new MutableLiveData<>();
    private volatile boolean profileLoaded;

    public DriverProfileViewModel(@NonNull Application application) {
        super(application);
        SmartBusApp app = (SmartBusApp) application;
        authRepository = new AuthRepository(app.getTokenManager(), app.getDatabase());
        tripRepository = new TripRepository(app.getDatabase().tripDao());
        routeRepository = new RouteRepository(app.getDatabase().routeDao(), app.getDatabase().stopDao());
    }

    public LiveData<UiState<DriverModel>> getProfileState() {
        return profileState;
    }

    public LiveData<String> getRouteLabel() {
        return routeLabel;
    }

    public LiveData<Boolean> getLoggedOut() {
        return loggedOut;
    }

    public void loadProfile() {
        DriverModel cached = authRepository.getCachedDriver();
        if (cached != null) {
            profileLoaded = true;
            profileState.setValue(UiState.success(cached));
            loadRouteLabel();
            return;
        }
        if (profileLoaded) {
            return;
        }
        profileState.setValue(UiState.loading());
        executor.execute(() -> {
            Result<DriverModel> result = authRepository.getMe();
            if (result.isSuccess() && result.getData() != null) {
                profileLoaded = true;
                profileState.postValue(UiState.success(result.getData()));
            } else {
                profileState.postValue(UiState.error(result.getMessage()));
            }
            loadRouteLabel();
        });
    }

    private void loadRouteLabel() {
        executor.execute(() -> {
            Result<TripModel> tripResult = tripRepository.getCurrentTrip();
            if (tripResult.isSuccess()
                    && tripResult.getData() != null
                    && Constants.TRIP_STATUS_IN_PROGRESS.equals(tripResult.getData().getStatus())) {
                Result<RouteModel> routeResult =
                        routeRepository.getRoute(tripResult.getData().getRouteId());
                if (routeResult.isSuccess() && routeResult.getData() != null) {
                    RouteModel route = routeResult.getData();
                    routeLabel.postValue(route.getCode() + " - " + route.getName());
                } else {
                    routeLabel.postValue("—");
                }
            } else {
                routeLabel.postValue("Chưa có chuyến");
            }
        });
    }

    public void logout() {
        loggedOut.setValue(true);
        executor.execute(() -> {
            authRepository.logout();
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }
}
