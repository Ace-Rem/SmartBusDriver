package com.smartbus.driver.network;

import androidx.annotation.NonNull;
import com.smartbus.driver.SmartBusApp;
import com.smartbus.driver.utils.TokenManager;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {

    private final TokenManager tokenManager;
    private final SmartBusApp app;

    public AuthInterceptor(TokenManager tokenManager) {
        this(tokenManager, null);
    }

    public AuthInterceptor(TokenManager tokenManager, SmartBusApp app) {
        this.tokenManager = tokenManager;
        this.app = app;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        String path = original.url().encodedPath();
        boolean isLogin = path.endsWith("/auth/login");

        String authorization = tokenManager.getAuthorizationHeader();
        Request request = original;
        if (!isLogin && authorization != null && !authorization.isEmpty()) {
            request = original.newBuilder()
                    .header("Authorization", authorization)
                    .build();
        }

        Response response = chain.proceed(request);
        if (!isLogin
                && response.code() == 401
                && authorization != null
                && !authorization.isEmpty()) {
            tokenManager.clearSession();
            if (app != null) {
                app.notifySessionExpired();
            }
        }
        return response;
    }
}
