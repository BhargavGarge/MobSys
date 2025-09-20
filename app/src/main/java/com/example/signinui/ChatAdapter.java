package com.example.signinui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.signinui.model.ChatMessage;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {

    private final List<ChatMessage> messageList;
    private final String currentUserUid;

    // View types to distinguish between sent and received messages
    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;

    public ChatAdapter(List<ChatMessage> messageList, String currentUserUid) {
        this.messageList = messageList;
        this.currentUserUid = currentUserUid;
    }

    @Override
    public int getItemViewType(int position) {
        // If the message sender's UID is the same as the current user's UID, it's a "sent" message
        if (messageList.get(position).getSenderId().equals(currentUserUid)) {
            return VIEW_TYPE_SENT;
        } else {
            return VIEW_TYPE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        // Inflate the correct layout based on the view type
        if (viewType == VIEW_TYPE_SENT) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_sent, parent, false);
        } else {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_received, parent, false);
        }
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        ChatMessage message = messageList.get(position);

        // Set message text
        holder.messageText.setText(message.getMessageText());

        // Set sender name (only for received messages)
        if (holder.senderName != null) {
            String senderName = message.getSenderName();
            if (senderName == null || senderName.isEmpty()) {
                senderName = "Adventure Buddy";
            }
            holder.senderName.setText(senderName);
        }

        // Set timestamp
        if (holder.timestampText != null) {
            String timestamp = formatTimestamp(message.getTimestamp());
            holder.timestampText.setText(timestamp);
        }

        // Set profile image (default avatar for now)
        if (holder.profileImage != null) {
            holder.profileImage.setImageResource(R.drawable.ic_person);
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    // Helper method to add a message and update the RecyclerView
    public void add(ChatMessage message) {
        messageList.add(message);
        notifyItemInserted(messageList.size() - 1);
    }

    // Format timestamp to readable format
    private String formatTimestamp(long timestamp) {
        try {
            Date date = new Date(timestamp);
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return sdf.format(date);
        } catch (Exception e) {
            return "";
        }
    }

    // ViewHolder class to hold the view for each message item
    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        TextView senderName;
        TextView timestampText;
        ImageView profileImage;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.text_view_message);
            senderName = itemView.findViewById(R.id.sender_name); // May be null for sent messages
            timestampText = itemView.findViewById(R.id.timestamp);
            profileImage = itemView.findViewById(R.id.profile_image); // May be null for sent messages
        }
    }
}