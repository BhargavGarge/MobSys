package com.example.signinui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

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

import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    // UI Components
    private RecyclerView chatRecyclerView;
    private EditText messageInput;
    private Button sendButton;

    // Firebase
    private DatabaseReference messagesDbRef;
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat); // You need to create this layout file

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

        // Setup Firebase Database reference
        messagesDbRef = FirebaseDatabase.getInstance().getReference().child("chats").child(chatId).child("messages");

        // Setup UI
        initializeUI();

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

        // Setup RecyclerView
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messageListAdapter = new ChatAdapter(messages);
        chatRecyclerView.setAdapter(messageListAdapter);
    }

    private void sendMessage(String messageText) {
        // Create the message object
        ChatMessage chatMessage = new ChatMessage(
                currentUserUid,
                messageText,
                System.currentTimeMillis()
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

                    // Add the message to the adapter and scroll to the bottom
                    messageListAdapter.add(chatMessage);
                    chatRecyclerView.scrollToPosition(messageListAdapter.getItemCount() - 1);
                }

                // Other methods are not needed for this simple chat but must be implemented
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
        messages.clear(); // Clear messages when activity is not visible
        messageListAdapter.notifyDataSetChanged();
    }
}