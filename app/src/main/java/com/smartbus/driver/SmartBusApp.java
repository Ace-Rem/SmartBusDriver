package com.smartbus.driver;

import android.app.Application;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.smartbus.driver.database.SmartBusDatabase;
import com.smartbus.driver.network.RetrofitClient;
import com.smartbus.driver.utils.TokenManager;

public class SmartBusApp extends Application {

    private TokenManager tokenManager;
    private SmartBusDatabase database;
    private final MutableLiveData<Boolean> sessionExpired = new MutableLiveData<>(false);

    @Override
    public void onCreate() {
        super.onCreate();
        tokenManager = new TokenManager(this);
        database = SmartBusDatabase.getInstance(this);
        RetrofitClient.init(tokenManager, this);
        com.smartbus.driver.map.MapManager.initOsmConfiguration(this);
    }

    public TokenManager getTokenManager() {
        return tokenManager;
    }

    public SmartBusDatabase getDatabase() {
        return database;
    }

    public LiveData<Boolean> getSessionExpired() {
        return sessionExpired;
    }

    public void notifySessionExpired() {
        sessionExpired.postValue(true);
    }

    public void clearSessionExpired() {
        sessionExpired.postValue(false);
    }
}
