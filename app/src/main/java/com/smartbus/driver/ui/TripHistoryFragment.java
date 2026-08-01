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
import com.smartbus.driver.R;
import com.smartbus.driver.adapter.TripHistoryAdapter;
import com.smartbus.driver.databinding.FragmentTripHistoryBinding;
import com.smartbus.driver.model.TripHistoryItem;
import com.smartbus.driver.utils.StateOverlayHelper;
import com.smartbus.driver.utils.UiState;
import com.smartbus.driver.viewmodel.TripHistoryViewModel;
import java.util.List;

public class TripHistoryFragment extends Fragment {

    private FragmentTripHistoryBinding binding;
    private TripHistoryViewModel viewModel;
    private final TripHistoryAdapter adapter = new TripHistoryAdapter();

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentTripHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TripHistoryViewModel.class);
        binding.tripHistoryRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.tripHistoryRecyclerView.setAdapter(adapter);
        viewModel.getHistoryState().observe(getViewLifecycleOwner(), this::render);
        viewModel.loadHistory();
    }

    private void render(UiState<List<TripHistoryItem>> state) {
        if (binding == null || state == null) {
            return;
        }
        View overlay = binding.getRoot().findViewById(R.id.stateOverlay);
        binding.historyProgress.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);
        if (state.isLoading()) {
            StateOverlayHelper.showLoading(overlay);
            return;
        }
        if (state.isEmpty()) {
            adapter.submitList(null);
            StateOverlayHelper.showEmpty(overlay, getString(R.string.empty_history), state.getMessage());
            return;
        }
        if (state.isError()) {
            StateOverlayHelper.showError(
                    overlay,
                    getString(R.string.error_default),
                    state.getMessage(),
                    () -> viewModel.loadHistory()
            );
            return;
        }
        StateOverlayHelper.hide(overlay);
        adapter.submitList(state.getData());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.loadHistory();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
