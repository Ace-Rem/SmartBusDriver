package com.smartbus.driver.repository;

import com.google.gson.Gson;
import com.smartbus.driver.model.AiAssistantRequest;
import com.smartbus.driver.model.AiAssistantResponse;
import com.smartbus.driver.model.AiSummaryRequest;
import com.smartbus.driver.model.ApiResponse;
import com.smartbus.driver.model.ErrorResponse;
import com.smartbus.driver.network.RetrofitClient;
import com.smartbus.driver.network.SmartBusApi;
import com.smartbus.driver.utils.ApiResponseParser;
import com.smartbus.driver.utils.Result;
import java.io.IOException;
import java.util.Map;
import retrofit2.Response;

public class AiRepository {

    private static final Gson GSON = new Gson();
    private final SmartBusApi api;

    public AiRepository() {
        this.api = RetrofitClient.getApi();
    }

    public Result<AiAssistantResponse> chat(long tripId, String question) {
        return chat(tripId, question, null);
    }

    public Result<AiAssistantResponse> chat(long tripId, String question, Map<String, Object> clientContext) {
        try {
            Response<ApiResponse<AiAssistantResponse>> response =
                    api.aiChat(new AiAssistantRequest(tripId, question, clientContext)).execute();
            return parseAi(response);
        } catch (IOException exception) {
            return Result.error("Không kết nối được máy chủ AI (" + exception.getClass().getSimpleName()
                    + "). Kiểm tra mạng hoặc Render đang ngủ.");
        }
    }

    public Result<AiAssistantResponse> summary(long tripId) {
        return summary(tripId, null);
    }

    public Result<AiAssistantResponse> summary(long tripId, Map<String, Object> clientContext) {
        try {
            Response<ApiResponse<AiAssistantResponse>> response =
                    api.aiSummary(new AiSummaryRequest(tripId, clientContext)).execute();
            return parseAi(response);
        } catch (IOException exception) {
            return Result.error("Không kết nối được máy chủ AI (" + exception.getClass().getSimpleName()
                    + "). Kiểm tra mạng hoặc Render đang ngủ.");
        }
    }

    private Result<AiAssistantResponse> parseAi(Response<ApiResponse<AiAssistantResponse>> response) {
        if (response.isSuccessful() && response.body() != null) {
            ApiResponse<AiAssistantResponse> body = response.body();
            if (body.isSuccess() && body.getData() != null) {
                AiAssistantResponse data = body.getData();
                if (data.getAnswer() == null || data.getAnswer().isBlank()) {
                    return Result.error("Backend trả về rỗng — kiểm tra AI_API_KEY / AI_BASE_URL trên Render và deploy lại.");
                }
                return Result.success(data, body.getMessage());
            }
            String message = body.getMessage() != null ? body.getMessage() : "Yêu cầu AI thất bại";
            return Result.error(message);
        }

        String message = "Lỗi AI từ máy chủ (HTTP " + response.code() + ")";
        if (response.code() == 401) {
            message = "Phiên đăng nhập hết hạn — hãy đăng nhập lại.";
        } else if (response.code() == 404) {
            message = "Không tìm thấy chuyến/API AI (404). Kiểm tra trip đang chạy và backend đã deploy.";
        } else if (response.code() >= 500) {
            message = "Backend lỗi " + response.code() + ". Xem log Render (AI_API_KEY, OpenRouter).";
        }
        if (response.errorBody() != null) {
            try {
                String raw = response.errorBody().string();
                ErrorResponse errorResponse = GSON.fromJson(raw, ErrorResponse.class);
                if (errorResponse != null && errorResponse.getMessage() != null) {
                    message = errorResponse.getMessage() + " (HTTP " + response.code() + ")";
                }
            } catch (IOException ignored) {
                // keep default
            }
        }
        return Result.error(message);
    }
}
