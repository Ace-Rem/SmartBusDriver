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
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<List<AiMessage>> messages = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<UiState<AiAssistantResponse>> chatState =
            new MutableLiveData<>(UiState.idle());

    public AiAssistantViewModel(@NonNull Application application) {
        super(application);
        SmartBusApp app = (SmartBusApp) application;
        tripRepository = new TripRepository(app.getDatabase().tripDao());
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
            Long resolvedTripId = resolveTripId(tripId);
            if (resolvedTripId == null) {
                failChat("Cần có chuyến đang chạy để hỏi AI");
                return;
            }
            Result<AiAssistantResponse> result = aiRepository.chat(resolvedTripId, trimmed, clientContext);
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
            Long resolvedTripId = resolveTripId(tripId);
            if (resolvedTripId == null) {
                failChat("Cần có chuyến đang chạy để tóm tắt");
                return;
            }
            Result<AiAssistantResponse> result = aiRepository.summary(resolvedTripId, clientContext);
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
