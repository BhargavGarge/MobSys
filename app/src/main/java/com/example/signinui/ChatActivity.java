package com.example.signinui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.signinui.model.ChatMessage;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    // UI Components
    private RecyclerView chatRecyclerView;
    private EditText messageInput;
    private ImageButton sendButton;
    private TextView partnerNameText;
    private ImageView partnerProfileImage;
    private TextView onlineStatusText;

    // Firebase
    private DatabaseReference messagesDbRef;
    private DatabaseReference usersDbRef;
    private ChildEventListener mChildEventListener;
    private FirebaseAuth mAuth;

    // Bluetooth Service
    private BluetoothService bluetoothService;
    private boolean isServiceBound = false;

    // Chat Data
    private ChatAdapter messageListAdapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private String currentUserUid;
    private String partnerUid;
    private String chatId;
    private String currentUserName;
    private String partnerName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Get UIDs from the Intent that started this activity
        currentUserUid = getIntent().getStringExtra("CURRENT_USER_UID");
        partnerUid = getIntent().getStringExtra("PARTNER_UID");

        if (currentUserUid == null || partnerUid == null) {
            Toast.makeText(this, "Error: User information not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Generate a consistent, unique chat ID for the two users
        generateChatId();

        // Setup Firebase Database references
        messagesDbRef = FirebaseDatabase.getInstance().getReference().child("chats").child(chatId).child("messages");
        usersDbRef = FirebaseDatabase.getInstance().getReference().child("users");

        // Setup UI
        initializeUI();
        loadUserNames();
        loadPartnerInfo();

        sendButton.setOnClickListener(v -> {
            String messageText = messageInput.getText().toString().trim();
            if (!messageText.isEmpty()) {
                sendMessage(messageText);
                messageInput.setText("");
            }
        });
    }

    private void generateChatId() {
        // Sort UIDs alphabetically to ensure both users get the same chat ID
        if (currentUserUid.compareTo(partnerUid) > 0) {
            chatId = currentUserUid + "_" + partnerUid;
        } else {
            chatId = partnerUid + "_" + currentUserUid;
        }
    }

    private void initializeUI() {
        chatRecyclerView = findViewById(R.id.chat_recycler_view);
        messageInput = findViewById(R.id.edit_text_chatbox);
        sendButton = findViewById(R.id.button_send);
        partnerNameText = findViewById(R.id.partner_name);
        partnerProfileImage = findViewById(R.id.partner_profile_image);
        onlineStatusText = findViewById(R.id.online_status);

        // Setup back button
        findViewById(R.id.back_button).setOnClickListener(v -> finish());

        // Setup RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // Show latest messages at bottom
        chatRecyclerView.setLayoutManager(layoutManager);

        messageListAdapter = new ChatAdapter(messages, currentUserUid);
        chatRecyclerView.setAdapter(messageListAdapter);
    }

    private void loadUserNames() {
        // Load current user name
        usersDbRef.child(currentUserUid).child("name").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentUserName = snapshot.getValue(String.class);
                if (currentUserName == null) currentUserName = "You";
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                currentUserName = "You";
            }
        });
    }

    private void loadPartnerInfo() {
        // Load partner's information
        usersDbRef.child(partnerUid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                partnerName = snapshot.child("name").getValue(String.class);
                String profileImageUrl = snapshot.child("profileImageUrl").getValue(String.class);

                if (partnerName != null) {
                    partnerNameText.setText(partnerName);
                } else {
                    partnerNameText.setText("Adventure Buddy");
                }

                // Set online status (you can implement real-time presence later)
                onlineStatusText.setText("Online now");
                onlineStatusText.setVisibility(View.VISIBLE);

                // Load profile image if available (you'll need to implement image loading)
                // For now, set a default avatar
                partnerProfileImage.setImageResource(R.drawable.ic_person);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                partnerNameText.setText("Adventure Buddy");
                partnerProfileImage.setImageResource(R.drawable.ic_person);
            }
        });
    }

    private void sendMessage(String messageText) {
        // Create the message object with additional info
        ChatMessage chatMessage = new ChatMessage(
                currentUserUid,
                messageText,
                System.currentTimeMillis(),
                currentUserName != null ? currentUserName : "You"
        );

        // 1. Push the message to Firebase Realtime Database
        messagesDbRef.push().setValue(chatMessage);

        // 2. Send the message over Bluetooth for instant delivery (offline support)
        if (isServiceBound && bluetoothService != null) {
            bluetoothService.sendChatMessage(messageText);
        }
    }

    private void attachDatabaseReadListener() {
        if (mChildEventListener == null) {
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                    // A new message has been added to the database
                    ChatMessage chatMessage = snapshot.getValue(ChatMessage.class);
                    if (chatMessage != null) {
                        // Set sender name if not available
                        if (chatMessage.getSenderName() == null) {
                            if (chatMessage.getSenderId().equals(currentUserUid)) {
                                chatMessage.setSenderName(currentUserName != null ? currentUserName : "You");
                            } else {
                                chatMessage.setSenderName(partnerName != null ? partnerName : "Adventure Buddy");
                            }
                        }

                        // Add the message to the adapter and scroll to the bottom
                        messageListAdapter.add(chatMessage);
                        chatRecyclerView.scrollToPosition(messageListAdapter.getItemCount() - 1);
                    }
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
                @Override
                public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
                @Override
                public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(ChatActivity.this, "Failed to load messages.", Toast.LENGTH_SHORT).show();
                }
            };
            messagesDbRef.addChildEventListener(mChildEventListener);
        }
    }

    private void detachDatabaseReadListener() {
        if (mChildEventListener != null) {
            messagesDbRef.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }
    }

    // --- Service Binding Logic ---

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.LocalBinder binder = (BluetoothService.LocalBinder) service;
            bluetoothService = binder.getService();
            isServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to the BluetoothService
        Intent intent = new Intent(this, BluetoothService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        // Start listening for messages
        attachDatabaseReadListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }
        // Stop listening to prevent memory leaks and unnecessary background activity
        detachDatabaseReadListener();
    }
}