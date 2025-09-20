package com.example.signinui.model;

public class ChatMessage {
    private String senderId;
    private String messageText;
    private long timestamp;
    private String senderName;
    private String profileImageUrl;

    // Default constructor required for Firebase
    public ChatMessage() {
    }

    // Constructor with basic info
    public ChatMessage(String senderId, String messageText, long timestamp) {
        this.senderId = senderId;
        this.messageText = messageText;
        this.timestamp = timestamp;
    }

    // Constructor with sender name
    public ChatMessage(String senderId, String messageText, long timestamp, String senderName) {
        this.senderId = senderId;
        this.messageText = messageText;
        this.timestamp = timestamp;
        this.senderName = senderName;
    }

    // Constructor with all fields
    public ChatMessage(String senderId, String messageText, long timestamp, String senderName, String profileImageUrl) {
        this.senderId = senderId;
        this.messageText = messageText;
        this.timestamp = timestamp;
        this.senderName = senderName;
        this.profileImageUrl = profileImageUrl;
    }

    // Getters and Setters
    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
}