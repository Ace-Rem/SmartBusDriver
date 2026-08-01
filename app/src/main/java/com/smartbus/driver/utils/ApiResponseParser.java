package com.smartbus.driver.utils;

import com.google.gson.Gson;
import com.smartbus.driver.model.ApiResponse;
import com.smartbus.driver.model.ErrorResponse;
import java.io.IOException;
import retrofit2.Response;

public final class ApiResponseParser {

    private static final Gson GSON = new Gson();

    private ApiResponseParser() {
    }

    public static <T> Result<T> parse(Response<ApiResponse<T>> response) {
        if (response.isSuccessful() && response.body() != null) {
            ApiResponse<T> body = response.body();
            if (body.isSuccess()) {
                return Result.success(body.getData(), body.getMessage());
            }
            String message = body.getMessage() != null ? body.getMessage() : "Yêu cầu thất bại";
            return Result.error(message);
        }

        String message = "Lỗi máy chủ (" + response.code() + ")";
        if (response.errorBody() != null) {
            try {
                String raw = response.errorBody().string();
                ErrorResponse errorResponse = GSON.fromJson(raw, ErrorResponse.class);
                if (errorResponse != null && errorResponse.getMessage() != null) {
                    message = errorResponse.getMessage();
                }
            } catch (IOException ignored) {
                // Keep default message.
            }
        }
        return Result.error(message);
    }

    public static <T> Result<T> fromThrowable(Throwable throwable) {
        if (throwable instanceof IOException) {
            return Result.error("Không thể kết nối máy chủ");
        }
        String message = throwable.getMessage();
        if (message == null || message.isEmpty()) {
            message = "Đã xảy ra lỗi không xác định";
        }
        return Result.error(message);
    }
}
