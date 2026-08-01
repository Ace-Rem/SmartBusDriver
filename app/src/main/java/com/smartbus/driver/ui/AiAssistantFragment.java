package com.smartbus.driver.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.snackbar.Snackbar;
import com.smartbus.driver.R;
import com.smartbus.driver.adapter.AiMessageAdapter;
import com.smartbus.driver.databinding.FragmentAiAssistantBinding;
import com.smartbus.driver.model.DashboardState;
import com.smartbus.driver.viewmodel.AiAssistantViewModel;
import com.smartbus.driver.viewmodel.TripViewModel;

public class AiAssistantFragment extends Fragment {

    private FragmentAiAssistantBinding binding;
    private AiAssistantViewModel aiViewModel;
    private TripViewModel tripViewModel;
    private final AiMessageAdapter adapter = new AiMessageAdapter();

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentAiAssistantBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        aiViewModel = new ViewModelProvider(this).get(AiAssistantViewModel.class);
        tripViewModel = new ViewModelProvider(requireActivity()).get(TripViewModel.class);

        View toolbar = binding.getRoot().findViewById(R.id.screenToolbar);
        if (toolbar != null) {
            android.widget.TextView title = toolbar.findViewById(R.id.toolbarTitleTextView);
            if (title != null) {
                title.setText(R.string.ai_title);
            }
            View back = toolbar.findViewById(R.id.toolbarBackButton);
            if (back != null) {
                back.setOnClickListener(v -> {
                    if (requireActivity() instanceof MainActivity) {
                        ((MainActivity) requireActivity()).navigateBackToDashboard();
                    }
                });
            }
        }

        binding.aiMessagesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.aiMessagesRecyclerView.setAdapter(adapter);

        binding.sendAiButton.setOnClickListener(v -> sendCurrentPrompt());
        binding.aiSuggestionTrafficButton.setOnClickListener(v ->
                sendQuestion(getString(R.string.ai_suggestion_traffic))
        );
        binding.aiSuggestionSafetyButton.setOnClickListener(v ->
                sendQuestion(getString(R.string.ai_suggestion_safety))
        );
        binding.aiSuggestionPassengerButton.setOnClickListener(v -> {
            Long tripId = currentTripId();
            aiViewModel.requestSummary(tripId);
        });

        aiViewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
            adapter.submitList(messages);
            if (messages != null && !messages.isEmpty()) {
                binding.aiMessagesRecyclerView.scrollToPosition(messages.size() - 1);
            }
        });
        aiViewModel.getChatState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }
            binding.aiProgress.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);
            binding.sendAiButton.setEnabled(!state.isLoading());
            if (state.isError()) {
                Snackbar.make(
                        binding.snackbarAnchor,
                        state.getMessage() != null ? state.getMessage() : getString(R.string.error_default),
                        Snackbar.LENGTH_LONG
                ).show();
            }
        });
    }

    private void sendCurrentPrompt() {
        String prompt = binding.aiPromptEditText.getText() != null
                ? binding.aiPromptEditText.getText().toString()
                : "";
        sendQuestion(prompt);
        binding.aiPromptEditText.setText("");
    }

    private void sendQuestion(String question) {
        aiViewModel.sendQuestion(currentTripId(), question);
    }

    @Nullable
    private Long currentTripId() {
        DashboardState dashboard = tripViewModel.getCurrentDashboard();
        return dashboard.getTrip() != null ? dashboard.getTrip().getId() : null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
