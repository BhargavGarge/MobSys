package com.example.signinui.model;

public class ChatMessage {
    private String senderId;
    private String messageText;
    private long timestamp;

    // IMPORTANT: Empty constructor required for Firebase
    public ChatMessage() {
    }

    public ChatMessage(String senderId, String messageText, long timestamp) {
        this.senderId = senderId;
        this.messageText = messageText;
        this.timestamp = timestamp;
    }

    public String getSenderId() { return senderId; }
    public String getMessageText() { return messageText; }
    public long getTimestamp() { return timestamp; }
}