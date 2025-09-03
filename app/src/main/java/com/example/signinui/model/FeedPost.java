package com.example.signinui.model;

import java.util.HashMap;
import java.util.Map;

public class FeedPost {
    private String postId;
    private String userId;
    private String username;
    private String location;
    private double latitude;
    private double longitude;
    private long timestamp;
    private String postImageUrl;
    private String activityType;
    private int likeCount;
    private int commentCount;
    private String caption;
    private Map<String, Boolean> likes;
    private Map<String, Comment> comments;

    public FeedPost() {
        likes = new HashMap<>();
        comments = new HashMap<>();
    }

    public FeedPost(String postId, String userId, String username, String location,
                    double latitude, double longitude, long timestamp, String postImageUrl,
                    String activityType, int likeCount, int commentCount, String caption) {
        this();
        this.postId = postId;
        this.userId = userId;
        this.username = username;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.postImageUrl = postImageUrl;
        this.activityType = activityType;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.caption = caption;
    }

    // Getters and Setters
    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getPostImageUrl() { return postImageUrl; }
    public void setPostImageUrl(String postImageUrl) { this.postImageUrl = postImageUrl; }

    public String getActivityType() { return activityType; }
    public void setActivityType(String activityType) { this.activityType = activityType; }

    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }

    public int getCommentCount() { return commentCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public Map<String, Boolean> getLikes() { return likes; }
    public void setLikes(Map<String, Boolean> likes) { this.likes = likes; }

    public Map<String, Comment> getComments() { return comments; }
    public void setComments(Map<String, Comment> comments) { this.comments = comments; }

    // Comment inner class
    public static class Comment {
        private String userId;
        private String username;
        private String commentText;
        private long timestamp;

        public Comment() {}

        public Comment(String userId, String username, String commentText, long timestamp) {
            this.userId = userId;
            this.username = username;
            this.commentText = commentText;
            this.timestamp = timestamp;
        }

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getCommentText() { return commentText; }
        public void setCommentText(String commentText) { this.commentText = commentText; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
}