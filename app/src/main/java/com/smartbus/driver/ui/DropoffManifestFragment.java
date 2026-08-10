package com.smartbus.driver.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.smartbus.driver.R;
import com.smartbus.driver.databinding.FragmentDropoffManifestBinding;
import com.smartbus.driver.model.BoardingRequestModel;
import com.smartbus.driver.model.DashboardState;
import com.smartbus.driver.model.PassengerModel;
import com.smartbus.driver.model.StopModel;
import com.smartbus.driver.repository.BoardingRequestRepository;
import com.smartbus.driver.utils.Result;
import com.smartbus.driver.viewmodel.TripViewModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DropoffManifestFragment extends Fragment {

    private FragmentDropoffManifestBinding binding;
    private TripViewModel tripViewModel;
    private final BoardingRequestRepository boardingRequestRepository = new BoardingRequestRepository();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentDropoffManifestBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tripViewModel = new ViewModelProvider(requireActivity()).get(TripViewModel.class);
        binding.dropoffBackButton.setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).navigateBackToDashboard();
            }
        });
        binding.dropoffRefreshButton.setOnClickListener(v -> loadManifest());
        loadManifest();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadManifest();
    }

    private void loadManifest() {
        if (binding == null) {
            return;
        }
        DashboardState dashboard = tripViewModel.getCurrentDashboard();
        if (dashboard == null || dashboard.getTrip() == null || dashboard.getTrip().getId() == null) {
            binding.dropoffManifestContainer.removeAllViews();
            binding.dropoffSummaryTextView.setText(R.string.trip_required_for_passenger);
            return;
        }
        long tripId = dashboard.getTrip().getId();
        binding.dropoffProgress.setVisibility(View.VISIBLE);
        binding.dropoffRefreshButton.setEnabled(false);
        binding.dropoffSummaryTextView.setText(R.string.loading_default);
        executor.execute(() -> {
            Result<List<BoardingRequestModel>> result = boardingRequestRepository.listByTrip(tripId);
            mainHandler.post(() -> {
                if (binding == null) {
                    return;
                }
                binding.dropoffProgress.setVisibility(View.GONE);
                binding.dropoffRefreshButton.setEnabled(true);
                if (!result.isSuccess()) {
                    binding.dropoffSummaryTextView.setText(
                            result.getMessage() != null ? result.getMessage() : getString(R.string.error_default)
                    );
                    Snackbar.make(binding.dropoffSnackbarAnchor, R.string.error_default, Snackbar.LENGTH_LONG).show();
                    return;
                }
                renderManifest(result.getData());
            });
        });
    }

    private void renderManifest(List<BoardingRequestModel> requests) {
        binding.dropoffManifestContainer.removeAllViews();
        List<BoardingRequestModel> activeRequests = new ArrayList<>();
        if (requests != null) {
            for (BoardingRequestModel request : requests) {
                if (request != null
                        && request.getDestinationStop() != null
                        && !"CANCELLED".equals(request.getStatus())) {
                    activeRequests.add(request);
                }
            }
        }
        activeRequests.sort(Comparator.comparingInt(this::destinationOrder));
        if (activeRequests.isEmpty()) {
            binding.dropoffSummaryTextView.setText(R.string.dropoff_manifest_empty);
            return;
        }

        Map<String, List<BoardingRequestModel>> grouped = new LinkedHashMap<>();
        for (BoardingRequestModel request : activeRequests) {
            String stopName = stopName(request.getDestinationStop());
            List<BoardingRequestModel> group = grouped.get(stopName);
            if (group == null) {
                group = new ArrayList<>();
                grouped.put(stopName, group);
            }
            group.add(request);
        }
        binding.dropoffSummaryTextView.setText(
                activeRequests.size() + " khách có bến xuống trong chuyến hiện tại"
        );
        for (Map.Entry<String, List<BoardingRequestModel>> entry : grouped.entrySet()) {
            binding.dropoffManifestContainer.addView(groupCard(entry.getKey(), entry.getValue()));
        }
    }

    private View groupCard(String stopName, List<BoardingRequestModel> requests) {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setRadius(getResources().getDimension(R.dimen.radius_sm));
        card.setCardElevation(getResources().getDimension(R.dimen.elevation_card));
        card.setUseCompatPadding(true);
        card.setCardBackgroundColor(requireContext().getColor(R.color.md3_surface));

        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.spacing_md);
        inner.setPadding(padding, padding, padding, padding);
        card.addView(inner);

        TextView title = text(stopName + " · " + requests.size() + " khách", true);
        inner.addView(title);
        for (BoardingRequestModel request : requests) {
            TextView passenger = text(passengerLine(request), false);
            passenger.setPadding(0, getResources().getDimensionPixelSize(R.dimen.spacing_xs), 0, 0);
            inner.addView(passenger);
        }
        return card;
    }

    private TextView text(String value, boolean title) {
        TextView textView = new TextView(requireContext());
        textView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        textView.setText(value);
        textView.setTextColor(requireContext().getColor(title ? R.color.md3_on_surface : R.color.md3_on_surface_variant));
        textView.setTextSize(title ? 17f : 14f);
        textView.setTypeface(textView.getTypeface(), title ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return textView;
    }

    private String passengerLine(BoardingRequestModel request) {
        PassengerModel passenger = request.getPassenger();
        String name = passenger != null && passenger.getFullName() != null
                ? passenger.getFullName()
                : getString(R.string.placeholder_dash);
        String phone = passenger != null && passenger.getPhoneNumber() != null
                ? " · " + passenger.getPhoneNumber()
                : "";
        return name + phone
                + "\nLên: " + stopName(request.getBoardingStop())
                + " · Trạng thái: " + statusLabel(request.getStatus());
    }

    private int destinationOrder(BoardingRequestModel request) {
        StopModel stop = request.getDestinationStop();
        return stop != null && stop.getStopOrder() != null ? stop.getStopOrder() : Integer.MAX_VALUE;
    }

    private String stopName(StopModel stop) {
        return stop != null && stop.getName() != null ? stop.getName() : getString(R.string.placeholder_dash);
    }

    private String statusLabel(String status) {
        if ("PENDING".equals(status)) {
            return "Chờ xác nhận";
        }
        if ("CONFIRMED".equals(status)) {
            return "Đã gửi check-in";
        }
        if ("BOARDED".equals(status)) {
            return "Đã lên xe";
        }
        if ("COMPLETED".equals(status)) {
            return "Hoàn tất";
        }
        return status != null ? status : getString(R.string.placeholder_dash);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
