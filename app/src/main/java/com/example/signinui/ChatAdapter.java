package com.example.signinui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.signinui.model.ChatMessage;
import com.google.firebase.auth.FirebaseAuth;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {

    private final List<ChatMessage> messageList;
    private final String currentUserUid;

    // View types to distinguish between sent and received messages
    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;

    public ChatAdapter(List<ChatMessage> messageList) {
        this.messageList = messageList;
        this.currentUserUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
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
        holder.messageText.setText(message.getMessageText());
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

    // ViewHolder class to hold the view for each message item
    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            // This assumes both your sent and received layouts have a TextView with this ID
            messageText = itemView.findViewById(R.id.text_view_message);
        }
    }
}