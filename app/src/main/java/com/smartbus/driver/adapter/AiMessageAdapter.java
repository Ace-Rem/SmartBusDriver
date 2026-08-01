package com.smartbus.driver.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.smartbus.driver.R;
import com.smartbus.driver.databinding.ItemAiMessageBinding;
import com.smartbus.driver.model.AiMessage;
import java.util.ArrayList;
import java.util.List;

public class AiMessageAdapter extends RecyclerView.Adapter<AiMessageAdapter.MessageViewHolder> {

    private final List<AiMessage> items = new ArrayList<>();

    public void submitList(List<AiMessage> messages) {
        items.clear();
        if (messages != null) {
            items.addAll(messages);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAiMessageBinding binding = ItemAiMessageBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new MessageViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {

        private final ItemAiMessageBinding binding;

        MessageViewHolder(ItemAiMessageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AiMessage message) {
            binding.aiMessageText.setText(message.getText());
            if (message.isUser()) {
                binding.aiMessageBubble.setBackgroundResource(R.drawable.bg_soft_blue);
                binding.aiMessageIcon.setImageResource(R.drawable.ic_person);
                binding.aiMessageIcon.setColorFilter(
                        ContextCompat.getColor(itemView.getContext(), R.color.md3_primary)
                );
            } else {
                binding.aiMessageBubble.setBackgroundResource(R.drawable.bg_card);
                binding.aiMessageIcon.setImageResource(R.drawable.ic_ai);
                binding.aiMessageIcon.setColorFilter(
                        ContextCompat.getColor(itemView.getContext(), R.color.md3_primary)
                );
            }
        }
    }
}
