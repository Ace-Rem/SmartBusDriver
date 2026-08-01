package com.smartbus.driver.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.smartbus.driver.R;
import com.smartbus.driver.databinding.FragmentPassengerRecordBinding;
import com.smartbus.driver.model.DashboardState;
import com.smartbus.driver.model.StopModel;
import com.smartbus.driver.utils.UiState;
import com.smartbus.driver.viewmodel.PassengerRecordViewModel;
import com.smartbus.driver.viewmodel.TripViewModel;
import java.util.ArrayList;
import java.util.List;

public class PassengerRecordFragment extends Fragment {

    private FragmentPassengerRecordBinding binding;
    private PassengerRecordViewModel passengerViewModel;
    private TripViewModel tripViewModel;
    private final List<StopModel> stops = new ArrayList<>();
    private Long selectedStopId;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentPassengerRecordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        passengerViewModel = new ViewModelProvider(this).get(PassengerRecordViewModel.class);
        tripViewModel = new ViewModelProvider(requireActivity()).get(TripViewModel.class);

        binding.pickupStopInputLayout.setVisibility(View.GONE);
        binding.dropoffStopEditText.setFocusable(false);
        binding.dropoffStopEditText.setClickable(true);
        binding.dropoffStopEditText.setOnClickListener(v -> showStopPicker());

        binding.savePassengerButton.setOnClickListener(v -> {
            DashboardState dashboard = tripViewModel.getCurrentDashboard();
            Long tripId = dashboard.getTrip() != null ? dashboard.getTrip().getId() : null;
            passengerViewModel.save(
                    tripId,
                    selectedStopId,
                    binding.passengerCountEditText.getText() != null
                            ? binding.passengerCountEditText.getText().toString()
                            : "",
                    binding.passengerNoteEditText.getText() != null
                            ? binding.passengerNoteEditText.getText().toString()
                            : null
            );
        });

        passengerViewModel.getStopsState().observe(getViewLifecycleOwner(), this::renderStops);
        passengerViewModel.getSaveState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }
            binding.passengerProgress.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);
            if (state.isSuccess()) {
                tripViewModel.refreshPassengerTotal();
                Snackbar.make(
                        binding.snackbarAnchor,
                        state.getMessage() != null ? state.getMessage() : getString(R.string.save_passenger),
                        Snackbar.LENGTH_SHORT
                ).show();
                binding.passengerCountEditText.setText("");
                binding.passengerNoteEditText.setText("");
                binding.dropoffStopEditText.setText("");
                selectedStopId = null;
            } else if (state.isError()) {
                Snackbar.make(
                        binding.snackbarAnchor,
                        state.getMessage() != null ? state.getMessage() : getString(R.string.error_default),
                        Snackbar.LENGTH_LONG
                ).show();
            }
        });

        DashboardState dashboard = tripViewModel.getCurrentDashboard();
        Long routeId = dashboard.getTrip() != null ? dashboard.getTrip().getRouteId() : null;
        passengerViewModel.loadStops(routeId);
    }

    private void renderStops(UiState<List<StopModel>> state) {
        if (state == null || !state.isSuccess() || state.getData() == null) {
            return;
        }
        stops.clear();
        stops.addAll(state.getData());
    }

    private void showStopPicker() {
        if (stops.isEmpty()) {
            Snackbar.make(binding.snackbarAnchor, R.string.empty_default, Snackbar.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[stops.size()];
        for (int i = 0; i < stops.size(); i++) {
            StopModel stop = stops.get(i);
            labels[i] = (stop.getStopOrder() != null ? stop.getStopOrder() + ". " : "") + stop.getName();
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dropoff_stop_hint)
                .setItems(labels, (dialog, which) -> {
                    StopModel stop = stops.get(which);
                    selectedStopId = stop.getId();
                    binding.dropoffStopEditText.setText(labels[which]);
                })
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}