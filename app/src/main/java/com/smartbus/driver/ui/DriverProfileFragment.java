package com.smartbus.driver.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.smartbus.driver.R;
import com.smartbus.driver.databinding.FragmentDriverProfileBinding;
import com.smartbus.driver.model.DashboardState;
import com.smartbus.driver.model.DriverModel;
import com.smartbus.driver.utils.StateOverlayHelper;
import com.smartbus.driver.utils.UiState;
import com.smartbus.driver.viewmodel.DriverProfileViewModel;
import com.smartbus.driver.viewmodel.TripViewModel;

public class DriverProfileFragment extends Fragment {

    private FragmentDriverProfileBinding binding;
    private DriverProfileViewModel viewModel;
    private TripViewModel tripViewModel;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentDriverProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DriverProfileViewModel.class);
        tripViewModel = new ViewModelProvider(requireActivity()).get(TripViewModel.class);

        binding.logoutButton.setOnClickListener(v -> {
            tripViewModel.stopGpsTracking();
            viewModel.logout();
        });

        viewModel.getProfileState().observe(getViewLifecycleOwner(), this::renderProfile);
        viewModel.getRouteLabel().observe(getViewLifecycleOwner(), label -> {
            if (binding != null && label != null) {
                binding.profileRouteTextView.setText(label);
            }
        });
        tripViewModel.getDashboardState().observe(getViewLifecycleOwner(), state -> {
            if (binding == null || state == null || !state.isSuccess() || state.getData() == null) {
                return;
            }
            renderTripMetrics(state.getData());
        });
        viewModel.getLoggedOut().observe(getViewLifecycleOwner(), loggedOut -> {
            if (Boolean.TRUE.equals(loggedOut)) {
                Intent intent = new Intent(requireContext(), LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        });
        viewModel.loadProfile();
    }

    private void renderProfile(UiState<DriverModel> state) {
        if (binding == null || state == null) {
            return;
        }
        View overlay = binding.getRoot().findViewById(R.id.stateOverlay);
        binding.profileProgress.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);
        if (state.isLoading()) {
            StateOverlayHelper.showLoading(overlay);
            return;
        }
        if (state.isError()) {
            StateOverlayHelper.showError(
                    overlay,
                    getString(R.string.error_default),
                    state.getMessage(),
                    () -> viewModel.loadProfile()
            );
            return;
        }
        StateOverlayHelper.hide(overlay);
        DriverModel driver = state.getData();
        if (driver == null) {
            return;
        }
        binding.profileNameTextView.setText(
                driver.getFullName() != null ? driver.getFullName() : getString(R.string.placeholder_dash)
        );
        binding.profileLicenseTextView.setText(
                driver.getLicenseNumber() != null ? driver.getLicenseNumber() : getString(R.string.placeholder_dash)
        );
        binding.profilePhoneTextView.setText(
                driver.getPhoneNumber() != null ? driver.getPhoneNumber() : getString(R.string.placeholder_dash)
        );
        renderTripMetrics(tripViewModel.getCurrentDashboard());
    }

    private void renderTripMetrics(DashboardState dashboard) {
        if (binding == null || dashboard == null) {
            return;
        }
        if (dashboard.hasActiveTrip()) {
            binding.profileRatingTextView.setText(R.string.profile_trip_active);
            binding.profileShiftTextView.setText(String.valueOf(dashboard.getPassengerTotal()));
            if (dashboard.getCurrentStop() != null && dashboard.getCurrentStop().getName() != null) {
                binding.profileVehicleTextView.setText(dashboard.getCurrentStop().getName());
            } else {
                binding.profileVehicleTextView.setText(R.string.profile_no_current_stop);
            }
        } else {
            binding.profileRatingTextView.setText(R.string.profile_trip_idle);
            binding.profileShiftTextView.setText("0");
            binding.profileVehicleTextView.setText(R.string.profile_no_current_stop);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.loadProfile();
        }
        if (tripViewModel != null) {
            tripViewModel.loadDashboard(false);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
