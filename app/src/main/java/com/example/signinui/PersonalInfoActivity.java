package com.example.signinui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PersonalInfoActivity extends AppCompatActivity {

    private TextView tvDisplayName, tvEmail, tvLocation, tvMemberSince;
    private TextView tvTotalDistance, tvTotalTours, tvTotalElevation, tvHighlights;
    private ImageView profileImageLarge;
    private ImageButton btnBack;

    // Firebase variables
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_info);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();
            mDatabase = FirebaseDatabase.getInstance().getReference().child("users").child(userId);
        }

        // Initialize views
        initializeViews();

        // Load user data from Firebase
        loadUserData();

        // Set click listeners
        setupClickListeners();
    }

    private void initializeViews() {
        tvDisplayName = findViewById(R.id.tv_display_name);
        tvEmail = findViewById(R.id.tv_email);
        tvLocation = findViewById(R.id.tv_location);
        tvMemberSince = findViewById(R.id.tv_member_since);
        tvTotalDistance = findViewById(R.id.tv_total_distance);
        tvTotalTours = findViewById(R.id.tv_total_tours);
        tvTotalElevation = findViewById(R.id.tv_total_elevation);
        tvHighlights = findViewById(R.id.tv_highlights);
        profileImageLarge = findViewById(R.id.profile_image_large);
        btnBack = findViewById(R.id.btnBack);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());
    }

    private void loadUserData() {
        if (mDatabase != null) {
            mDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        // Load personal info
                        String name = dataSnapshot.child("name").getValue(String.class);
                        String email = dataSnapshot.child("email").getValue(String.class);
                        String location = dataSnapshot.child("location").getValue(String.class);
                        String joinDate = dataSnapshot.child("joinDate").getValue(String.class);
                        String profileImage = dataSnapshot.child("profileImage").getValue(String.class);

                        // Load adventure stats
                        Long distance = dataSnapshot.child("stats/distance").getValue(Long.class);
                        Long tours = dataSnapshot.child("stats/tours").getValue(Long.class);
                        Long elevation = dataSnapshot.child("stats/elevation").getValue(Long.class);
                        Long highlights = dataSnapshot.child("stats/highlights").getValue(Long.class);

                        // Update UI with personal info
                        if (name != null) tvDisplayName.setText(name);
                        if (email != null) tvEmail.setText(email);
                        if (location != null) tvLocation.setText(location);
                        if (joinDate != null) {
                            tvMemberSince.setText(joinDate);
                        } else {
                            // Set default join date if not available
                            String currentDate = new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(new Date());
                            tvMemberSince.setText(currentDate);
                        }

                        // Update UI with adventure stats
                        if (distance != null) tvTotalDistance.setText(formatDistance(distance));
                        if (tours != null) tvTotalTours.setText(String.valueOf(tours));
                        if (elevation != null) tvTotalElevation.setText(formatElevation(elevation));
                        if (highlights != null) tvHighlights.setText(String.valueOf(highlights));

                        // Load profile image
                        if (profileImage != null && !profileImage.isEmpty()) {
                            try {
                                byte[] imageBytes = Base64.decode(profileImage, Base64.DEFAULT);
                                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                                profileImageLarge.setImageBitmap(bitmap);
                            } catch (IllegalArgumentException e) {
                                // Handle invalid base64 string
                                profileImageLarge.setImageResource(R.drawable.ic_person);
                            }
                        }
                    } else {
                        // Set default values if no data exists
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            tvDisplayName.setText(user.getDisplayName() != null ? user.getDisplayName() : "Adventure Seeker");
                            tvEmail.setText(user.getEmail() != null ? user.getEmail() : "explorer@example.com");

                            // Set default join date
                            String currentDate = new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(new Date());
                            tvMemberSince.setText(currentDate);
                        }

                        // Set default values for stats
                        tvTotalDistance.setText("0 km");
                        tvTotalTours.setText("0");
                        tvTotalElevation.setText("0 m");
                        tvHighlights.setText("0");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Toast.makeText(PersonalInfoActivity.this, "Failed to load user data: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private String formatDistance(long meters) {
        if (meters < 1000) {
            return meters + " m";
        } else {
            return String.format(Locale.getDefault(), "%.1f km", meters / 1000.0);
        }
    }

    private String formatElevation(long meters) {
        return String.format(Locale.getDefault(), "%,d m", meters);
    }
}