package com.example.signinui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class IntroActivity extends AppCompatActivity {

    private TextView btnGetStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if user is logged in
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            // User logged in, go to home page
            startActivity(new Intent(IntroActivity.this, MainActivity.class));
            finish();
            return;
        }

        // Show intro layout if no user logged in
        setContentView(R.layout.activity_intro);

        btnGetStarted = findViewById(R.id.button2); // Your Get Started TextView in intro layout

        btnGetStarted.setOnClickListener(v -> {
            // Navigate to signup screen
            startActivity(new Intent(IntroActivity.this, SignActivity.class));
            finish();
        });
    }
}
