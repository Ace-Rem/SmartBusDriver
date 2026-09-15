package com.smartbus.driver.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.smartbus.driver.SmartBusApp;
import com.smartbus.driver.model.AiAssistantResponse;
import com.smartbus.driver.model.AiMessage;
import com.smartbus.driver.model.TripModel;
import com.smartbus.driver.repository.AiRepository;
import com.smartbus.driver.repository.DriverAiContextProvider;
import com.smartbus.driver.repository.DriverAiContextSyncManager;
import com.smartbus.driver.repository.TripRepository;
import com.smartbus.driver.utils.Constants;
import com.smartbus.driver.utils.Result;
import com.smartbus.driver.utils.UiState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiAssistantViewModel extends AndroidViewModel {

    private final AiRepository aiRepository = new AiRepository();
    private final TripRepository tripRepository;
    private final DriverAiContextProvider contextProvider;
    private final DriverAiContextSyncManager contextSyncManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<List<AiMessage>> messages = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<UiState<AiAssistantResponse>> chatState =
            new MutableLiveData<>(UiState.idle());

    public AiAssistantViewModel(@NonNull Application application) {
        super(application);
        SmartBusApp app = (SmartBusApp) application;
        tripRepository = new TripRepository(app.getDatabase().tripDao());
        contextProvider = new DriverAiContextProvider(app.getDatabase());
        contextSyncManager = app.getAiContextSyncManager();
        List<AiMessage> initial = new ArrayList<>();
        initial.add(new AiMessage(
                AiMessage.TYPE_ASSISTANT,
                "Tôi có thể hỗ trợ tóm tắt chuyến, nhắc kiểm tra an toàn hoặc gợi ý xử lý tình huống."
        ));
        messages.setValue(initial);
    }

    public LiveData<List<AiMessage>> getMessages() {
        return messages;
    }

    public LiveData<UiState<AiAssistantResponse>> getChatState() {
        return chatState;
    }

    public void sendQuestion(Long tripId, String question) {
        sendQuestion(tripId, question, null);
    }

    public void sendQuestion(Long tripId, String question, Map<String, Object> clientContext) {
        if (question == null || question.trim().isEmpty()) {
            chatState.setValue(UiState.error("Vui lòng nhập câu hỏi"));
            return;
        }
        String trimmed = question.trim();
        appendMessage(new AiMessage(AiMessage.TYPE_USER, trimmed));
        chatState.setValue(UiState.loading());
        executor.execute(() -> {
            Map<String, Object> localContext = contextProvider.read();
            if (contextSyncManager != null) contextSyncManager.submit(localContext);
            Long resolvedTripId = numberValue(localContext.get("currentTripId"));
            Long routeId = numberValue(localContext.get("currentRouteId"));
            if (resolvedTripId == null && routeId == null) {
                failChat("Cần chọn tuyến hoặc có chuyến đang chạy để hỏi AI");
                return;
            }
            Result<AiAssistantResponse> result = aiRepository.chat(resolvedTripId, routeId, trimmed, localContext);
            if (result.isSuccess() && result.getData() != null
                    && result.getData().getAnswer() != null
                    && !result.getData().getAnswer().isBlank()) {
                appendMessage(new AiMessage(AiMessage.TYPE_ASSISTANT, result.getData().getAnswer()));
                chatState.postValue(UiState.success(result.getData()));
            } else {
                String message = result.getMessage() != null
                        ? result.getMessage()
                        : "AI không trả lời được. Thử lại sau.";
                failChat(message);
            }
        });
    }

    public void requestSummary(Long tripId) {
        requestSummary(tripId, null);
    }

    public void requestSummary(Long tripId, Map<String, Object> clientContext) {
        appendMessage(new AiMessage(AiMessage.TYPE_USER, "Tóm tắt hành khách"));
        chatState.setValue(UiState.loading());
        executor.execute(() -> {
            Map<String, Object> localContext = contextProvider.read();
            if (contextSyncManager != null) contextSyncManager.submit(localContext);
            Long resolvedTripId = numberValue(localContext.get("currentTripId"));
            Long routeId = numberValue(localContext.get("currentRouteId"));
            if (resolvedTripId == null && routeId == null) {
                failChat("Cần chọn tuyến hoặc có chuyến đang chạy để tóm tắt");
                return;
            }
            Result<AiAssistantResponse> result = aiRepository.summary(resolvedTripId, routeId, localContext);
            if (result.isSuccess() && result.getData() != null
                    && result.getData().getAnswer() != null
                    && !result.getData().getAnswer().isBlank()) {
                appendMessage(new AiMessage(AiMessage.TYPE_ASSISTANT, result.getData().getAnswer()));
                chatState.postValue(UiState.success(result.getData()));
            } else {
                String message = result.getMessage() != null
                        ? result.getMessage()
                        : "Không tóm tắt được chuyến. Thử lại sau.";
                failChat(message);
            }
        });
    }

    private Long resolveTripId(Long tripId) {
        if (tripId != null) {
            return tripId;
        }
        Result<TripModel> tripResult = tripRepository.getCurrentTrip();
        if (tripResult.isSuccess()
                && tripResult.getData() != null
                && Constants.TRIP_STATUS_IN_PROGRESS.equals(tripResult.getData().getStatus())) {
            return tripResult.getData().getId();
        }
        return null;
    }

    private Long numberValue(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String answerFromLocalContext(String question, Map<String, Object> context) {
        String q = question.toLowerCase(java.util.Locale.ROOT);
        String route = value(context.get("selectedRouteLabel"));
        String trip = value(context.get("currentTripName"));
        String current = value(context.get("currentStopName"));
        String next = value(context.get("nextStopName"));
        String stops = value(context.get("stopsOnRoute"));
        if (q.contains("bến tiếp theo") || q.contains("tiếp theo")) {
            return next == null ? "Chưa xác định được bến tiếp theo từ dữ liệu local." : "Bến tiếp theo là " + next + ".";
        }
        if (q.contains("bến hiện tại") || q.contains("đang ở bến nào")) {
            return current == null ? "Chưa xác định được bến hiện tại từ dữ liệu local." : "Xe đang ở gần " + current + ".";
        }
        if (q.contains("xe đang ở đâu") || q.contains("xe ở đâu")) {
            String latitude = value(context.get("currentLatitude"));
            String longitude = value(context.get("currentLongitude"));
            return latitude == null || longitude == null
                    ? "Chưa có tọa độ local mới nhất của xe."
                    : "Vị trí local mới nhất của xe: " + latitude + ", " + longitude + ".";
        }
        if (q.contains("những bến") || q.contains("các bến") || q.contains("danh sách bến")) {
            return stops == null || stops.isBlank() ? null : "Các bến của chuyến là: " + stops + ".";
        }
        if (q.contains("hành khách") || q.contains("check-in") || q.contains("check in") || q.contains("checkin")) {
            return "Hiện có " + value(context.get("passengerTotalOnBoard"), "0")
                    + " hành khách đang trên xe, " + value(context.get("passengerGroupCount"), "0")
                    + " nhóm check-in local.";
        }
        if (q.contains("chuyến") || q.contains("tuyến")) {
            if (trip != null && route != null) return "Bạn đang chạy " + trip + " trên " + route + ".";
            if (route != null) return "Bạn đang chạy trên " + route + ".";
        }
        if (q.contains("tóm tắt")) {
            return "Tóm tắt local: " + (trip == null ? "chưa có chuyến" : trip)
                    + (route == null ? "" : " · " + route)
                    + "; bến hiện tại: " + (current == null ? "chưa xác định" : current)
                    + "; bến tiếp theo: " + (next == null ? "hết tuyến" : next)
                    + "; hành khách trên xe: " + value(context.get("passengerTotalOnBoard"), "0") + ".";
        }
        return null;
    }

    private String value(Object raw) {
        return value(raw, null);
    }

    private String value(Object raw, String fallback) {
        if (raw == null || String.valueOf(raw).isBlank() || "null".equals(raw)) return fallback;
        return String.valueOf(raw);
    }

    private AiAssistantResponse response(String answer) {
        AiAssistantResponse response = new AiAssistantResponse();
        response.setAnswer(answer);
        return response;
    }

    private void failChat(String message) {
        appendMessage(new AiMessage(AiMessage.TYPE_ASSISTANT, message));
        chatState.postValue(UiState.error(message));
    }

    private synchronized void appendMessage(AiMessage message) {
        List<AiMessage> current = messages.getValue();
        List<AiMessage> updated = current != null ? new ArrayList<>(current) : new ArrayList<>();
        updated.add(message);
        messages.postValue(updated);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
    }
}
