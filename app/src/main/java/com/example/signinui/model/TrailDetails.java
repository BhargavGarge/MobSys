package com.example.signinui.model;

import org.osmdroid.util.GeoPoint;
import java.util.ArrayList;
import java.util.List;

// NOTE: This class is now in its own file to be shared across the app.
public class TrailDetails {
    public String id; // Unique ID from Google Places
    public String name, description, difficulty, distance, elevation, type, location;
    public double rating;
    public boolean isLiked = false;
    public boolean isSaved = false;

    // Marked as transient so Gson ignores it during serialization/deserialization.
    // It is used at runtime by MapsFragment for navigation purposes.
    public transient List<GeoPoint> routePoints = new ArrayList<>();


    // Default constructor required for Gson deserialization
    public TrailDetails() {}

    // Constructor used in MapsFragment
    public TrailDetails(String id, String name, String description, String difficulty, String distance, String elevation, String type, double rating, String location) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.difficulty = difficulty;
        this.distance = distance;
        this.elevation = elevation;
        this.type = type;
        this.rating = rating;
        this.location = location;
    }
    // Overloaded constructor for Liked/Saved fragments which might have less info initially.
    public TrailDetails(String id, String name) {
        this.id = id;
        this.name = name;
        this.description = "Details not fully loaded.";
    }
}