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
import com.smartbus.driver.adapter.RouteAdapter;
import com.smartbus.driver.adapter.StopAdapter;
import com.smartbus.driver.databinding.FragmentRouteBinding;
import com.smartbus.driver.model.RouteModel;
import com.smartbus.driver.model.StopModel;
import com.smartbus.driver.utils.StateOverlayHelper;
import com.smartbus.driver.utils.UiState;
import com.smartbus.driver.viewmodel.RouteViewModel;
import com.smartbus.driver.viewmodel.TripViewModel;
import java.util.List;

public class RouteFragment extends Fragment {

    private FragmentRouteBinding binding;
    private RouteViewModel routeViewModel;
    private TripViewModel tripViewModel;
    private final RouteAdapter routeAdapter = new RouteAdapter();
    private final StopAdapter stopAdapter = new StopAdapter();
    private RouteModel pendingRoute;
    private boolean showingStops;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentRouteBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        routeViewModel = new ViewModelProvider(this).get(RouteViewModel.class);
        tripViewModel = new ViewModelProvider(requireActivity()).get(TripViewModel.class);

        View toolbar = binding.getRoot().findViewById(R.id.screenToolbar);
        if (toolbar != null) {
            android.widget.TextView title = toolbar.findViewById(R.id.toolbarTitleTextView);
            if (title != null) {
                title.setText(R.string.route_title);
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

        binding.routeStopsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.routeStopsRecyclerView.setAdapter(routeAdapter);

        routeAdapter.setOnRouteClickListener(route -> {
            pendingRoute = route;
            showingStops = true;
            binding.routeNameTextView.setText(route.getCode() + " - " + route.getName());
            binding.routeStopsRecyclerView.setAdapter(stopAdapter);
            routeViewModel.selectRoute(route);
            Snackbar.make(binding.snackbarAnchor, R.string.route_confirm_start, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_start_trip, v -> startSelectedRoute())
                    .show();
        });

        binding.routeNameTextView.setOnClickListener(v -> {
            if (showingStops) {
                showRouteList();
            }
        });

        routeViewModel.getRoutesState().observe(getViewLifecycleOwner(), this::renderRoutes);
        routeViewModel.getStopsState().observe(getViewLifecycleOwner(), this::renderStops);
        tripViewModel.getActionState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) {
                return;
            }
            binding.routeProgress.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);
            if (state.isSuccess() && state.getMessage() != null) {
                Snackbar.make(binding.snackbarAnchor, state.getMessage(), Snackbar.LENGTH_SHORT).show();
                if (requireActivity() instanceof MainActivity) {
                    ((MainActivity) requireActivity()).ensureLocationPermissionAndStartGps();
                    ((MainActivity) requireActivity()).navigateTo(R.id.tripFragment);
                }
            } else if (state.isError()) {
                Snackbar.make(
                        binding.snackbarAnchor,
                        state.getMessage() != null ? state.getMessage() : getString(R.string.error_default),
                        Snackbar.LENGTH_LONG
                ).show();
            }
        });
        routeViewModel.loadRoutes();
    }

    private void startSelectedRoute() {
        if (tripViewModel.getCurrentDashboard().hasActiveTrip()) {
            Snackbar.make(binding.snackbarAnchor, R.string.trip_already_active, Snackbar.LENGTH_LONG).show();
            return;
        }
        if (pendingRoute != null && pendingRoute.getId() != null) {
            tripViewModel.startTrip(pendingRoute.getId());
        }
    }

    private void showRouteList() {
        showingStops = false;
        pendingRoute = null;
        binding.routeStopsRecyclerView.setAdapter(routeAdapter);
        binding.routeNameTextView.setText(R.string.route_select_prompt);
        routeViewModel.loadRoutes();
    }

    private void renderRoutes(UiState<List<RouteModel>> state) {
        if (binding == null || state == null) {
            return;
        }
        View overlay = binding.getRoot().findViewById(R.id.stateOverlay);
        binding.routeProgress.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);
        if (state.isLoading()) {
            StateOverlayHelper.showLoading(overlay);
            return;
        }
        if (state.isEmpty()) {
            StateOverlayHelper.showEmpty(overlay, getString(R.string.empty_default), state.getMessage());
            return;
        }
        if (state.isError()) {
            StateOverlayHelper.showError(
                    overlay,
                    getString(R.string.error_default),
                    state.getMessage(),
                    () -> routeViewModel.loadRoutes()
            );
            return;
        }
        StateOverlayHelper.hide(overlay);
        showingStops = false;
        binding.routeStopsRecyclerView.setAdapter(routeAdapter);
        routeAdapter.submitList(state.getData());
        binding.routeNameTextView.setText(R.string.route_select_prompt);
    }

    private void renderStops(UiState<List<StopModel>> state) {
        if (binding == null || state == null) {
            return;
        }
        binding.routeProgress.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);
        if (state.isSuccess()) {
            stopAdapter.submitList(state.getData());
        } else if (state.isError()) {
            Snackbar.make(
                    binding.snackbarAnchor,
                    state.getMessage() != null ? state.getMessage() : getString(R.string.error_default),
                    Snackbar.LENGTH_LONG
            ).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
