package com.smartbus.driver.network;

import com.smartbus.driver.BuildConfig;
import com.smartbus.driver.SmartBusApp;
import com.smartbus.driver.utils.TokenManager;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitClient {

    private static volatile Retrofit retrofit;
    private static volatile SmartBusApi api;
    private static TokenManager tokenManager;
    private static SmartBusApp app;

    private RetrofitClient() {
    }

    public static synchronized void init(TokenManager manager) {
        init(manager, null);
    }

    public static synchronized void init(TokenManager manager, SmartBusApp application) {
        tokenManager = manager;
        app = application;
        retrofit = null;
        api = null;
    }

    public static Retrofit getInstance() {
        if (retrofit == null) {
            synchronized (RetrofitClient.class) {
                if (retrofit == null) {
                    retrofit = new Retrofit.Builder()
                            .baseUrl(BuildConfig.BASE_URL)
                            .client(buildClient())
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();
                }
            }
        }
        return retrofit;
    }

    public static SmartBusApi getApi() {
        if (api == null) {
            synchronized (RetrofitClient.class) {
                if (api == null) {
                    api = getInstance().create(SmartBusApi.class);
                }
            }
        }
        return api;
    }

    private static OkHttpClient buildClient() {
        // Render cold start + OpenRouter AI can exceed 60s; keep generous timeouts.
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(150, TimeUnit.SECONDS);

        if (tokenManager != null) {
            builder.addInterceptor(new AuthInterceptor(tokenManager, app));
        }

        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
            builder.addInterceptor(loggingInterceptor);
        }

        return builder.build();
    }
}
