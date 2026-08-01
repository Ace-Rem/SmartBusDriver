package com.smartbus.driver.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.smartbus.driver.R;
import com.smartbus.driver.model.RouteModel;
import java.util.ArrayList;
import java.util.List;

public class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.RouteViewHolder> {

    public interface OnRouteClickListener {
        void onRouteClick(RouteModel route);
    }

    private final List<RouteModel> items = new ArrayList<>();
    private OnRouteClickListener listener;

    public void setOnRouteClickListener(OnRouteClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<RouteModel> routes) {
        items.clear();
        if (routes != null) {
            items.addAll(routes);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RouteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stop, parent, false);
        return new RouteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RouteViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class RouteViewHolder extends RecyclerView.ViewHolder {

        private final TextView orderText;
        private final TextView nameText;
        private final TextView metaText;

        RouteViewHolder(@NonNull View itemView) {
            super(itemView);
            orderText = itemView.findViewById(R.id.stopOrderTextView);
            nameText = itemView.findViewById(R.id.stopNameTextView);
            metaText = itemView.findViewById(R.id.stopTimeTextView);
        }

        void bind(RouteModel route) {
            orderText.setText(route.getCode() != null ? route.getCode() : "—");
            nameText.setText(route.getName() != null ? route.getName() : "—");
            metaText.setText(route.getDescription() != null ? route.getDescription() : "—");
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRouteClick(route);
                }
            });
        }
    }
}
