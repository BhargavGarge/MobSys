package com.example.signinui.model;

public class User {
    public String uid;
    public String name;
    public String email;

    public User() {
        // Default constructor required for Firebase
    }

    public User(String uid, String name, String email) {
        this.uid = uid;
        this.name = name;
        this.email = email;
    }
}