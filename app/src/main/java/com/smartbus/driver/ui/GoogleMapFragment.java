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
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.smartbus.driver.R;
import com.smartbus.driver.adapter.StopAdapter;
import com.smartbus.driver.databinding.FragmentGoogleMapBinding;
import com.smartbus.driver.map.MapManager;
import com.smartbus.driver.model.DashboardState;
import com.smartbus.driver.model.LocalLocation;
import com.smartbus.driver.model.RouteModel;
import com.smartbus.driver.model.StopModel;
import com.smartbus.driver.model.UpdateLocationResponse;
import com.smartbus.driver.utils.DateTimeUtils;
import com.smartbus.driver.utils.StateOverlayHelper;
import com.smartbus.driver.utils.UiState;
import com.smartbus.driver.viewmodel.TripViewModel;
import java.util.List;

public class GoogleMapFragment extends Fragment {

    private FragmentGoogleMapBinding binding;
    private TripViewModel tripViewModel;
    private final MapManager mapManager = new MapManager();
    private final StopAdapter stopAdapter = new StopAdapter();
    private BottomSheetBehavior<View> bottomSheetBehavior;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        MapManager.initOsmConfiguration(requireContext());
        binding = FragmentGoogleMapBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tripViewModel = new ViewModelProvider(requireActivity()).get(TripViewModel.class);

        setupBottomSheet();

        binding.stopsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.stopsRecyclerView.setAdapter(stopAdapter);
        stopAdapter.setOnStopClickListener(stop -> mapManager.focusStop(stop));

        mapManager.attachMap(binding.osmMapView);
        binding.mapStatusTextView.setText(R.string.map_osm_status);

        List<StopModel> stops = tripViewModel.getStopsState().getValue();
        if (stops != null) {
            mapManager.showStops(stops);
            stopAdapter.submitList(stops);
        }
        DashboardState dashboard = tripViewModel.getCurrentDashboard();
        applyStopHighlight(dashboard);
        if (dashboard.getTrip() != null
                && dashboard.getTrip().getCurrentLatitude() != null
                && dashboard.getTrip().getCurrentLongitude() != null) {
            mapManager.updateVehiclePosition(
                    dashboard.getTrip().getCurrentLatitude(),
                    dashboard.getTrip().getCurrentLongitude()
            );
        }

        binding.centerVehicleButton.setOnClickListener(v -> mapManager.centerOnVehicle());
        binding.showRouteButton.setOnClickListener(v -> mapManager.showFullRoute());

        tripViewModel.getDashboardState().observe(getViewLifecycleOwner(), this::renderDashboard);
        tripViewModel.getStopsState().observe(getViewLifecycleOwner(), nextStops -> {
            stopAdapter.submitList(nextStops);
            mapManager.showStops(nextStops);
            applyStopHighlight(tripViewModel.getCurrentDashboard());
        });
        tripViewModel.getLocalLocation().observe(getViewLifecycleOwner(), this::onLocalLocation);
        tripViewModel.getLocationUpdate().observe(getViewLifecycleOwner(), this::onLocationUpdate);
    }

    private void setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.mapBottomPanel);
        bottomSheetBehavior.setFitToContents(true);
        bottomSheetBehavior.setHideable(false);
        bottomSheetBehavior.setDraggable(true);
        bottomSheetBehavior.setSkipCollapsed(false);
        bottomSheetBehavior.setPeekHeight(
                getResources().getDimensionPixelSize(R.dimen.map_sheet_peek_height)
        );
        // Keep all content measured so dragging up can expand to full height.
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        updateSheetHint(BottomSheetBehavior.STATE_EXPANDED);

        View.OnClickListener toggleListener = v -> toggleSheet();
        binding.mapSheetHandleArea.setOnClickListener(toggleListener);
        binding.mapSheetHandle.setOnClickListener(toggleListener);
        binding.mapSheetHintTextView.setOnClickListener(toggleListener);

        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED
                        || newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    updateSheetHint(newState);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // No-op: content stays visible; peekHeight clips collapsed area.
            }
        });
    }

    private void toggleSheet() {
        if (bottomSheetBehavior == null) {
            return;
        }
        int current = bottomSheetBehavior.getState();
        if (current == BottomSheetBehavior.STATE_EXPANDED
                || current == BottomSheetBehavior.STATE_HALF_EXPANDED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private void updateSheetHint(int state) {
        if (binding == null) {
            return;
        }
        if (state == BottomSheetBehavior.STATE_COLLAPSED) {
            binding.mapSheetHintTextView.setText(R.string.map_sheet_drag_up_hint);
        } else {
            binding.mapSheetHintTextView.setText(R.string.map_sheet_drag_hint);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mapManager.onResume();
        if (tripViewModel != null) {
            tripViewModel.loadDashboard(false);
        }
    }

    @Override
    public void onPause() {
        mapManager.onPause();
        super.onPause();
    }

    private void renderDashboard(UiState<DashboardState> state) {
        if (binding == null || state == null) {
            return;
        }
        View overlay = binding.getRoot().findViewById(R.id.stateOverlay);
        if (state.isLoading()) {
            binding.mapProgress.setVisibility(View.VISIBLE);
            return;
        }
        StateOverlayHelper.hide(overlay);
        binding.mapProgress.setVisibility(View.GONE);
        if (!state.isSuccess() || state.getData() == null) {
            if (state.isError()) {
                StateOverlayHelper.showError(
                        overlay,
                        getString(R.string.error_default),
                        state.getMessage(),
                        () -> tripViewModel.loadDashboard()
                );
            }
            return;
        }
        DashboardState data = state.getData();
        RouteModel route = data.getRoute();
        if (route != null) {
            binding.mapVehicleTextView.setText(route.getCode() + " · " + getString(R.string.map_vehicle));
            binding.mapStatusTextView.setText(route.getName());
        } else if (data.hasActiveTrip()) {
            binding.mapVehicleTextView.setText(R.string.map_vehicle);
            binding.mapStatusTextView.setText(R.string.map_osm_status);
        } else {
            binding.mapVehicleTextView.setText(R.string.map_vehicle);
            binding.mapStatusTextView.setText(R.string.map_no_active_trip);
        }

        StopModel nextStop = data.getNextStop();
        binding.mapNextStopTextView.setText(
                nextStop != null && nextStop.getName() != null
                        ? nextStop.getName()
                        : getString(R.string.placeholder_dash)
        );
        StopModel currentStop = data.getCurrentStop();
        binding.mapCurrentStopTextView.setText(
                currentStop != null && currentStop.getName() != null
                        ? getString(R.string.map_current_stop_format, currentStop.getName())
                        : getString(R.string.map_current_stop_unknown)
        );
        stopAdapter.setHighlightIds(
                currentStop != null ? currentStop.getId() : null,
                nextStop != null ? nextStop.getId() : null
        );
        applyStopHighlight(data);

        if (data.getTrip() != null
                && data.getTrip().getCurrentLatitude() != null
                && data.getTrip().getCurrentLongitude() != null) {
            mapManager.updateVehiclePosition(
                    data.getTrip().getCurrentLatitude(),
                    data.getTrip().getCurrentLongitude()
            );
        }
    }

    private void onLocalLocation(LocalLocation location) {
        if (binding == null || location == null) {
            return;
        }
        mapManager.updateVehiclePosition(location.getLatitude(), location.getLongitude());
    }

    private void onLocationUpdate(UpdateLocationResponse response) {
        if (binding == null || response == null) {
            return;
        }
        if (response.getTrip() != null
                && response.getTrip().getCurrentLatitude() != null
                && response.getTrip().getCurrentLongitude() != null) {
            mapManager.updateVehiclePosition(
                    response.getTrip().getCurrentLatitude(),
                    response.getTrip().getCurrentLongitude()
            );
        }
        if (response.getNextStop() != null) {
            binding.mapNextStopTextView.setText(response.getNextStop().getName());
        }
        if (response.getCurrentStop() != null) {
            binding.mapCurrentStopTextView.setText(
                    getString(R.string.map_current_stop_format, response.getCurrentStop().getName())
            );
        }
        stopAdapter.setHighlightIds(
                response.getCurrentStop() != null ? response.getCurrentStop().getId() : null,
                response.getNextStop() != null ? response.getNextStop().getId() : null
        );
        mapManager.highlightStops(
                response.getCurrentStop() != null ? response.getCurrentStop().getId() : null,
                response.getNextStop() != null ? response.getNextStop().getId() : null
        );
        if (response.getNearestStopDistanceMeters() != null) {
            binding.mapEtaTextView.setText(
                    DateTimeUtils.formatEtaFromMeters(response.getNearestStopDistanceMeters())
            );
        }
    }

    private void applyStopHighlight(DashboardState dashboard) {
        if (dashboard == null) {
            return;
        }
        Long currentId = dashboard.getCurrentStop() != null ? dashboard.getCurrentStop().getId() : null;
        Long nextId = dashboard.getNextStop() != null ? dashboard.getNextStop().getId() : null;
        mapManager.highlightStops(currentId, nextId);
        stopAdapter.setHighlightIds(currentId, nextId);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mapManager.clear();
        binding = null;
    }
}
