package com.smartbus.driver.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.smartbus.driver.model.DriverModel;

public class TokenManager {

    private final SharedPreferences preferences;

    public TokenManager(Context context) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(String accessToken, String tokenType, DriverModel driver) {
        SharedPreferences.Editor editor = preferences.edit()
                .putString(Constants.KEY_ACCESS_TOKEN, accessToken)
                .putString(Constants.KEY_TOKEN_TYPE, tokenType != null ? tokenType : "Bearer");
        if (driver != null) {
            if (driver.getId() != null) {
                editor.putLong(Constants.KEY_DRIVER_ID, driver.getId());
            }
            editor.putString(Constants.KEY_DRIVER_FULL_NAME, driver.getFullName());
            editor.putString(Constants.KEY_DRIVER_PHONE, driver.getPhoneNumber());
            editor.putString(Constants.KEY_DRIVER_LICENSE, driver.getLicenseNumber());
        }
        editor.apply();
    }

    public void clearSession() {
        preferences.edit()
                .remove(Constants.KEY_ACCESS_TOKEN)
                .remove(Constants.KEY_TOKEN_TYPE)
                .remove(Constants.KEY_DRIVER_ID)
                .remove(Constants.KEY_DRIVER_FULL_NAME)
                .remove(Constants.KEY_DRIVER_PHONE)
                .remove(Constants.KEY_DRIVER_LICENSE)
                .apply();
    }

    public boolean isLoggedIn() {
        String token = getAccessToken();
        return token != null && !token.isEmpty();
    }

    public String getAccessToken() {
        return preferences.getString(Constants.KEY_ACCESS_TOKEN, null);
    }

    public String getAuthorizationHeader() {
        String token = getAccessToken();
        if (token == null || token.isEmpty()) {
            return null;
        }
        String type = preferences.getString(Constants.KEY_TOKEN_TYPE, "Bearer");
        return type + " " + token;
    }

    public DriverModel getCachedDriver() {
        if (!preferences.contains(Constants.KEY_DRIVER_ID)) {
            return null;
        }
        DriverModel driver = new DriverModel();
        driver.setId(preferences.getLong(Constants.KEY_DRIVER_ID, 0L));
        driver.setFullName(preferences.getString(Constants.KEY_DRIVER_FULL_NAME, null));
        driver.setPhoneNumber(preferences.getString(Constants.KEY_DRIVER_PHONE, null));
        driver.setLicenseNumber(preferences.getString(Constants.KEY_DRIVER_LICENSE, null));
        return driver;
    }

    public void saveRememberUsername(boolean remember, String username) {
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(Constants.KEY_REMEMBER_USERNAME, remember);
        if (remember) {
            editor.putString(Constants.KEY_SAVED_USERNAME, username);
        } else {
            editor.remove(Constants.KEY_SAVED_USERNAME);
        }
        editor.apply();
    }

    public boolean isRememberUsername() {
        return preferences.getBoolean(Constants.KEY_REMEMBER_USERNAME, false);
    }

    public String getSavedUsername() {
        return preferences.getString(Constants.KEY_SAVED_USERNAME, "");
    }
}
