package com.smartbus.driver.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.smartbus.driver.R;
import com.smartbus.driver.SmartBusApp;
import com.smartbus.driver.databinding.ActivityMainBinding;
import com.smartbus.driver.viewmodel.TripViewModel;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final Set<Integer> TOP_LEVEL_DESTINATIONS = new HashSet<>();

    static {
        TOP_LEVEL_DESTINATIONS.add(R.id.tripFragment);
        TOP_LEVEL_DESTINATIONS.add(R.id.googleMapFragment);
        TOP_LEVEL_DESTINATIONS.add(R.id.tripHistoryFragment);
        TOP_LEVEL_DESTINATIONS.add(R.id.driverProfileFragment);
    }

    private ActivityMainBinding binding;
    private TripViewModel tripViewModel;
    private NavController navController;
    private boolean locationRationaleShown;
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (granted) {
                    if (tripViewModel.getCurrentDashboard().hasActiveTrip()) {
                        tripViewModel.startGpsTracking();
                    }
                } else {
                    showLocationPermissionDeniedDialog();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        tripViewModel = new ViewModelProvider(this).get(TripViewModel.class);

        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.navHostFragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();

            // Custom bottom-nav handling: do NOT use NavigationUI restoreState for
            // Dashboard, otherwise AI/route nested under tripFragment come back
            // when switching Map → Điều khiển.
            binding.bottomNavigation.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.tripFragment) {
                    navigateBackToDashboard();
                    return true;
                }
                if (TOP_LEVEL_DESTINATIONS.contains(itemId)) {
                    navigateToTopLevel(itemId);
                    return true;
                }
                return false;
            });

            binding.bottomNavigation.setOnItemReselectedListener(item -> {
                if (item.getItemId() == R.id.tripFragment) {
                    navigateBackToDashboard();
                    return;
                }
                if (navController.getCurrentDestination() != null
                        && navController.getCurrentDestination().getId() != item.getItemId()) {
                    navigateToTopLevel(item.getItemId());
                } else {
                    navController.popBackStack(item.getItemId(), false);
                }
            });

            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                binding.bottomNavigation.setVisibility(View.VISIBLE);
                binding.bottomNavDivider.setVisibility(View.VISIBLE);
                syncBottomNavSelection(destination.getId());
            });
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (navController != null && navController.getCurrentDestination() != null
                        && !TOP_LEVEL_DESTINATIONS.contains(navController.getCurrentDestination().getId())) {
                    navigateBackToDashboard();
                    return;
                }
                if (navController != null && !navController.popBackStack()) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        ensureLocationPermission();
        tripViewModel.loadDashboard();
        tripViewModel.getDashboardState().observe(this, state -> {
            if (state != null
                    && state.isSuccess()
                    && state.getData() != null
                    && state.getData().hasActiveTrip()
                    && !tripViewModel.getGpsManager().isTracking()) {
                ensureLocationPermissionAndStartGps();
            }
        });

        SmartBusApp app = (SmartBusApp) getApplication();
        app.getSessionExpired().observe(this, expired -> {
            if (Boolean.TRUE.equals(expired)) {
                app.clearSessionExpired();
                tripViewModel.stopGpsTracking();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
    }

    public void navigateTo(int destinationId) {
        if (navController == null) {
            return;
        }
        if (TOP_LEVEL_DESTINATIONS.contains(destinationId)) {
            if (destinationId == R.id.tripFragment) {
                navigateBackToDashboard();
                return;
            }
            navigateToTopLevel(destinationId);
            return;
        }
        // Nested screens (AI / Route): keep dashboard under them, do not saveState.
        NavOptions options = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(R.id.tripFragment, false, false)
                .build();
        navController.navigate(destinationId, null, options);
    }

    /**
     * Always land on dashboard root. Never restore nested AI/route that was
     * previously opened under the Điều khiển tab.
     */
    public void navigateBackToDashboard() {
        if (navController == null) {
            return;
        }
        NavOptions options = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(false)
                .setPopUpTo(R.id.tripFragment, true, false)
                .build();
        navController.navigate(R.id.tripFragment, null, options);
        setBottomNavSelectedSilently(R.id.tripFragment);
    }

    private void navigateToTopLevel(int destinationId) {
        if (navController == null) {
            return;
        }
        // Drop AI/route first so they are not saved into the dashboard back stack.
        Integer currentId = navController.getCurrentDestination() != null
                ? navController.getCurrentDestination().getId()
                : null;
        if (currentId != null && !TOP_LEVEL_DESTINATIONS.contains(currentId)) {
            navController.popBackStack(R.id.tripFragment, false);
        }
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == destinationId) {
            setBottomNavSelectedSilently(destinationId);
            return;
        }
        NavOptions options = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(false)
                .setPopUpTo(R.id.tripFragment, false, false)
                .build();
        navController.navigate(destinationId, null, options);
        setBottomNavSelectedSilently(destinationId);
    }

    private void syncBottomNavSelection(int destinationId) {
        if (binding == null) {
            return;
        }
        int selected = destinationId;
        if (!TOP_LEVEL_DESTINATIONS.contains(destinationId)) {
            // Nested AI/route: keep highlighting Điều khiển.
            selected = R.id.tripFragment;
        }
        setBottomNavSelectedSilently(selected);
    }

    private void setBottomNavSelectedSilently(int itemId) {
        if (binding == null) {
            return;
        }
        if (binding.bottomNavigation.getSelectedItemId() == itemId) {
            return;
        }
        binding.bottomNavigation.setOnItemSelectedListener(null);
        binding.bottomNavigation.setSelectedItemId(itemId);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.tripFragment) {
                navigateBackToDashboard();
                return true;
            }
            if (TOP_LEVEL_DESTINATIONS.contains(id)) {
                navigateToTopLevel(id);
                return true;
            }
            return false;
        });
    }

    public void ensureLocationPermission() {
        if (!hasLocationPermission()) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    public void ensureLocationPermissionAndStartGps() {
        if (hasLocationPermission()) {
            tripViewModel.startGpsTracking();
        } else {
            ensureLocationPermission();
        }
    }

    private void showLocationPermissionDeniedDialog() {
        if (locationRationaleShown || isFinishing()) {
            return;
        }
        locationRationaleShown = true;
        boolean permanentlyDenied = !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.location_permission_title)
                .setMessage(R.string.location_permission_message)
                .setNegativeButton(R.string.dialog_cancel, null)
                .setPositiveButton(
                        permanentlyDenied ? R.string.location_open_settings : R.string.retry_action,
                        (dialog, which) -> {
                            locationRationaleShown = false;
                            if (permanentlyDenied) {
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.fromParts("package", getPackageName(), null));
                                startActivity(intent);
                            } else {
                                ensureLocationPermission();
                            }
                        }
                )
                .show();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
