package com.smartbus.driver.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.smartbus.driver.R;
import com.smartbus.driver.databinding.FragmentTripBinding;
import com.smartbus.driver.model.DashboardState;
import com.smartbus.driver.model.DriverModel;
import com.smartbus.driver.model.RouteModel;
import com.smartbus.driver.model.StopModel;
import com.smartbus.driver.utils.DateTimeUtils;
import com.smartbus.driver.utils.UiState;
import com.smartbus.driver.viewmodel.TripViewModel;

public class TripFragment extends Fragment {

    private FragmentTripBinding binding;
    private TripViewModel tripViewModel;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentTripBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tripViewModel = new ViewModelProvider(requireActivity()).get(TripViewModel.class);

        binding.startTripButton.setOnClickListener(v -> {
            if (tripViewModel.getCurrentDashboard().hasActiveTrip()) {
                Snackbar.make(
                        binding.snackbarAnchor,
                        R.string.trip_already_active,
                        Snackbar.LENGTH_LONG
                ).show();
                return;
            }
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).navigateTo(R.id.routeFragment);
            }
        });
        binding.addPassengerButton.setOnClickListener(v -> {
            if (!tripViewModel.getCurrentDashboard().hasActiveTrip()) {
                Snackbar.make(
                        binding.snackbarAnchor,
                        R.string.trip_required_for_passenger,
                        Snackbar.LENGTH_LONG
                ).show();
                return;
            }
            PassengerDialogFragment.show(getParentFragmentManager());
        });
        binding.endTripButton.setOnClickListener(v -> {
            if (!tripViewModel.getCurrentDashboard().hasActiveTrip()) {
                Snackbar.make(
                        binding.snackbarAnchor,
                        R.string.trip_required_for_end,
                        Snackbar.LENGTH_LONG
                ).show();
                return;
            }
            confirmEndTrip();
        });
        binding.aiAssistantButton.setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).navigateTo(R.id.aiAssistantFragment);
            }
        });
        binding.openMapButton.setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).navigateTo(R.id.googleMapFragment);
            }
        });

        tripViewModel.getDashboardState().observe(getViewLifecycleOwner(), this::renderDashboard);
        tripViewModel.getActionState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }
            binding.dashboardProgress.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);
            if (state.isSuccess() && state.getMessage() != null) {
                Snackbar.make(binding.snackbarAnchor, state.getMessage(), Snackbar.LENGTH_SHORT).show();
            } else if (state.isError()) {
                Snackbar.make(
                        binding.snackbarAnchor,
                        state.getMessage() != null ? state.getMessage() : getString(R.string.error_default),
                        Snackbar.LENGTH_LONG
                ).show();
            }
        });
        tripViewModel.getGpsMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Snackbar.make(binding.snackbarAnchor, message, Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        scrollDashboardToTop();
        tripViewModel.loadDashboard(false);
    }

    private void scrollDashboardToTop() {
        if (binding == null) {
            return;
        }
        View root = binding.getRoot();
        if (root instanceof NestedScrollView) {
            ((NestedScrollView) root).smoothScrollTo(0, 0);
            return;
        }
        NestedScrollView scrollView = root.findViewById(R.id.dashboardScrollView);
        if (scrollView != null) {
            scrollView.post(() -> scrollView.smoothScrollTo(0, 0));
        }
    }

    private void renderDashboard(UiState<DashboardState> state) {
        if (binding == null || state == null) {
            return;
        }
        binding.dashboardProgress.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);
        if (!state.isSuccess() || state.getData() == null) {
            if (state.isError()) {
                Snackbar.make(
                        binding.snackbarAnchor,
                        state.getMessage() != null ? state.getMessage() : getString(R.string.error_default),
                        Snackbar.LENGTH_LONG
                ).show();
            }
            return;
        }

        DashboardState data = state.getData();
        DriverModel driver = data.getDriver();
        binding.driverNameTextView.setText(
                driver != null && driver.getFullName() != null
                        ? driver.getFullName()
                        : getString(R.string.placeholder_dash)
        );

        RouteModel route = data.getRoute();
        if (route != null) {
            binding.routeValueTextView.setText(route.getCode() + " - " + route.getName());
        } else {
            binding.routeValueTextView.setText(R.string.placeholder_dash);
        }

        if (data.getTrip() != null && data.getTrip().getStartedAt() != null) {
            binding.tripTimeTextView.setText(DateTimeUtils.formatDisplay(data.getTrip().getStartedAt()));
        } else {
            binding.tripTimeTextView.setText(R.string.placeholder_dash);
        }

        binding.gpsStatusTextView.setText(
                data.getGpsStatus() != null ? data.getGpsStatus() : getString(R.string.gps_value_searching)
        );
        binding.passengerTotalTextView.setText(String.valueOf(data.getPassengerTotal()));
        binding.dropoffCountTextView.setText(String.valueOf(data.getPassengersAlighting()));

        StopModel currentStop = data.getCurrentStop();
        binding.currentStopTextView.setText(
                currentStop != null && currentStop.getName() != null
                        ? currentStop.getName()
                        : getString(R.string.placeholder_dash)
        );
        StopModel nextStop = data.getNextStop();
        binding.nextStopTextView.setText(
                nextStop != null && nextStop.getName() != null
                        ? nextStop.getName()
                        : getString(R.string.placeholder_dash)
        );
        binding.notificationTextView.setText(
                data.getNotification() != null
                        ? data.getNotification()
                        : getString(R.string.notification_placeholder)
        );

        boolean active = data.hasActiveTrip();
        // Keep buttons enabled for readable contrast; guard actions in click listeners.
        binding.startTripButton.setEnabled(true);
        binding.startTripButton.setAlpha(active ? 0.55f : 1f);
        binding.endTripButton.setEnabled(true);
        binding.endTripButton.setAlpha(active ? 1f : 0.55f);
        binding.addPassengerButton.setEnabled(true);
        binding.addPassengerButton.setAlpha(active ? 1f : 0.55f);
    }

    private void confirmEndTrip() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.action_end_trip)
                .setMessage(R.string.dialog_end_trip_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(R.string.dialog_confirm, (dialog, which) -> tripViewModel.endTrip())
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
