package com.example.signinui.model;

public class Route {
    private String name;
    private String type;
    private String imageUrl;
    private String details;

    public Route() {
        // Default constructor required for Firebase
    }

    public Route(String name, String type, String imageUrl, String details) {
        this.name = name;
        this.type = type;
        this.imageUrl = imageUrl;
        this.details = details;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}