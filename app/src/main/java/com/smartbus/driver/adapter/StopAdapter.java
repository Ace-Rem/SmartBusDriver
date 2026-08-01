package com.smartbus.driver.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.smartbus.driver.R;
import com.smartbus.driver.databinding.ItemStopBinding;
import com.smartbus.driver.model.StopModel;
import java.util.ArrayList;
import java.util.List;

public class StopAdapter extends RecyclerView.Adapter<StopAdapter.StopViewHolder> {

    public interface OnStopClickListener {
        void onStopClick(StopModel stop);
    }

    private final List<StopModel> items = new ArrayList<>();
    private OnStopClickListener listener;
    private Long currentStopId;
    private Long nextStopId;

    public void setOnStopClickListener(OnStopClickListener listener) {
        this.listener = listener;
    }

    public void setHighlightIds(Long currentStopId, Long nextStopId) {
        this.currentStopId = currentStopId;
        this.nextStopId = nextStopId;
        notifyDataSetChanged();
    }

    public void submitList(List<StopModel> stops) {
        items.clear();
        if (stops != null) {
            items.addAll(stops);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StopViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemStopBinding binding = ItemStopBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new StopViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull StopViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class StopViewHolder extends RecyclerView.ViewHolder {

        private final ItemStopBinding binding;

        StopViewHolder(ItemStopBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(StopModel stop) {
            binding.stopOrderTextView.setText(
                    stop.getStopOrder() != null ? String.valueOf(stop.getStopOrder()) : "—"
            );
            binding.stopNameTextView.setText(stop.getName() != null ? stop.getName() : "—");

            boolean isCurrent = stop.getId() != null && stop.getId().equals(currentStopId);
            boolean isNext = stop.getId() != null && stop.getId().equals(nextStopId);
            if (isCurrent) {
                binding.stopTimeTextView.setText(R.string.stop_status_current);
                binding.getRoot().setCardBackgroundColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.smartbus_success_container)
                );
            } else if (isNext) {
                binding.stopTimeTextView.setText(R.string.stop_status_next);
                binding.getRoot().setCardBackgroundColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.md3_primary_container)
                );
            } else {
                binding.stopTimeTextView.setText(
                        stop.getLatitude() != null && stop.getLongitude() != null
                                ? String.format("%.4f, %.4f", stop.getLatitude(), stop.getLongitude())
                                : "—"
                );
                binding.getRoot().setCardBackgroundColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.md3_surface)
                );
            }

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onStopClick(stop);
                }
            });
        }
    }
}
