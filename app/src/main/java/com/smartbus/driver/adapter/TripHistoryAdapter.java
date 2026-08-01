package com.smartbus.driver.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.smartbus.driver.R;
import com.smartbus.driver.databinding.ItemTripHistoryBinding;
import com.smartbus.driver.model.TripHistoryItem;
import com.smartbus.driver.model.TripModel;
import com.smartbus.driver.utils.Constants;
import com.smartbus.driver.utils.DateTimeUtils;
import java.util.ArrayList;
import java.util.List;

public class TripHistoryAdapter extends RecyclerView.Adapter<TripHistoryAdapter.HistoryViewHolder> {

    private final List<TripHistoryItem> items = new ArrayList<>();

    public void submitList(List<TripHistoryItem> trips) {
        items.clear();
        if (trips != null) {
            items.addAll(trips);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTripHistoryBinding binding = ItemTripHistoryBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new HistoryViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {

        private final ItemTripHistoryBinding binding;

        HistoryViewHolder(ItemTripHistoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TripHistoryItem item) {
            TripModel trip = item.getTrip();
            binding.tripNameTextView.setText(
                    item.getRouteLabel() != null
                            ? item.getRouteLabel()
                            : itemView.getContext().getString(R.string.placeholder_dash)
            );
            binding.tripTimeTextView.setText(
                    DateTimeUtils.formatRange(
                            trip != null ? trip.getStartedAt() : null,
                            trip != null ? trip.getEndedAt() : null
                    )
            );

            boolean completed = trip != null
                    && Constants.TRIP_STATUS_COMPLETED.equals(trip.getStatus());
            binding.tripStatusTextView.setText(
                    completed
                            ? itemView.getContext().getString(R.string.history_completed)
                            : itemView.getContext().getString(R.string.history_running)
            );
            binding.tripDistanceTextView.setText(
                    itemView.getContext().getString(
                            R.string.history_duration_format,
                            item.getDurationLabel()
                    )
            );
            binding.tripPassengerTextView.setText(
                    itemView.getContext().getString(
                            R.string.history_passengers_format,
                            item.getPassengerTotal()
                    )
            );
        }
    }
}
