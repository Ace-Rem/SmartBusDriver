package com.smartbus.driver.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.smartbus.driver.SmartBusApp;
import com.smartbus.driver.model.LoginResponse;
import com.smartbus.driver.repository.AuthRepository;
import com.smartbus.driver.repository.BootstrapRepository;
import com.smartbus.driver.repository.TripRepository;
import com.smartbus.driver.utils.Result;
import com.smartbus.driver.utils.UiState;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final BootstrapRepository bootstrapRepository;
    private final TripRepository tripRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<UiState<LoginResponse>> loginState = new MutableLiveData<>(UiState.idle());

    public LoginViewModel(@NonNull Application application) {
        super(application);
        SmartBusApp app = (SmartBusApp) application;
        authRepository = new AuthRepository(app.getTokenManager(), app.getDatabase());
        bootstrapRepository = new BootstrapRepository(app.getDatabase());
        tripRepository = new TripRepository(app.getDatabase().tripDao());
    }

    public LiveData<UiState<LoginResponse>> getLoginState() {
        return loginState;
    }

    public String getSavedUsername() {
        return authRepository.getTokenManager().getSavedUsername();
    }

    public boolean isRememberUsername() {
        return authRepository.getTokenManager().isRememberUsername();
    }

    public void login(String username, String password, boolean remember) {
        if (username == null || username.trim().isEmpty()) {
            loginState.setValue(UiState.error("Vui lòng nhập tên đăng nhập"));
            return;
        }
        if (password == null || password.isEmpty()) {
            loginState.setValue(UiState.error("Vui lòng nhập mật khẩu"));
            return;
        }
        loginState.setValue(UiState.loading());
        executor.execute(() -> {
            Result<LoginResponse> result = authRepository.login(username.trim(), password);
            authRepository.getTokenManager().saveRememberUsername(remember, username.trim());
            if (result.isSuccess()) {
                bootstrapRepository.sync();
                loginState.postValue(UiState.success(result.getData(), result.getMessage()));
                tripRepository.syncHistory();
            } else {
                loginState.postValue(UiState.error(result.getMessage()));
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }
}
