package com.smartbus.driver.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
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

public class PassengerDialogFragment extends androidx.appcompat.app.AppCompatDialogFragment {

    public static final String TAG = "PassengerDialogFragment";

    private FragmentPassengerRecordBinding binding;
    private PassengerRecordViewModel passengerViewModel;
    private TripViewModel tripViewModel;
    private final List<StopModel> stops = new ArrayList<>();
    private Long selectedStopId;

    public static void show(FragmentManager fragmentManager) {
        PassengerDialogFragment fragment = new PassengerDialogFragment();
        fragment.show(fragmentManager, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        binding = FragmentPassengerRecordBinding.inflate(LayoutInflater.from(requireContext()));
        passengerViewModel = new ViewModelProvider(this).get(PassengerRecordViewModel.class);
        tripViewModel = new ViewModelProvider(requireActivity()).get(TripViewModel.class);

        DashboardState dashboard = tripViewModel.getCurrentDashboard();
        Long routeId = dashboard.getTrip() != null ? dashboard.getTrip().getRouteId() : null;
        passengerViewModel.loadStops(routeId);

        binding.pickupStopInputLayout.setVisibility(View.GONE);
        binding.dropoffStopEditText.setFocusable(false);
        binding.dropoffStopEditText.setClickable(true);
        binding.dropoffStopEditText.setOnClickListener(v -> showStopPicker());

        binding.savePassengerButton.setOnClickListener(v -> {
            DashboardState latest = tripViewModel.getCurrentDashboard();
            Long tripId = latest.getTrip() != null ? latest.getTrip().getId() : null;
            String count = binding.passengerCountEditText.getText() != null
                    ? binding.passengerCountEditText.getText().toString()
                    : "";
            String note = binding.passengerNoteEditText.getText() != null
                    ? binding.passengerNoteEditText.getText().toString()
                    : null;
            passengerViewModel.save(tripId, selectedStopId, count, note);
        });

        passengerViewModel.getStopsState().observe(this, this::renderStops);
        passengerViewModel.getSaveState().observe(this, state -> {
            if (state == null || binding == null) {
                return;
            }
            binding.passengerProgress.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);
            binding.savePassengerButton.setEnabled(!state.isLoading());
            if (state.isSuccess()) {
                tripViewModel.refreshPassengerTotal();
                dismiss();
            } else if (state.isError()) {
                Snackbar.make(
                        binding.snackbarAnchor,
                        state.getMessage() != null ? state.getMessage() : getString(R.string.error_default),
                        Snackbar.LENGTH_LONG
                ).show();
            }
        });

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(binding.getRoot())
                .create();
    }

    private void renderStops(UiState<List<StopModel>> state) {
        if (state == null || !state.isSuccess() || state.getData() == null) {
            return;
        }
        stops.clear();
        stops.addAll(state.getData());
    }

    private void showStopPicker() {
        if (binding == null) {
            return;
        }
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