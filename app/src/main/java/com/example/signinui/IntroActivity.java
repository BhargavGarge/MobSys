
package com.example.signinui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;


import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

public class IntroActivity extends AppCompatActivity {

    private MaterialButton btnGetStarted;
    private View heroImageCard;
    private View contentCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if user is logged in
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            // User logged in, go to main activity (home page)
            startActivity(new Intent(IntroActivity.this, MainActivity.class));
            finish();
            return;
        }

        // Set up immersive status bar with green color
        setupStatusBar();

        // Show intro layout if no user logged in
        setContentView(R.layout.activity_intro);

        // Initialize views
        initializeViews();

        // Set up modern back press handling
        setupBackPressHandling();

        // Add entrance animations
        addEntranceAnimations();
    }

    private void setupBackPressHandling() {
        // Handle back press with OnBackPressedDispatcher
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Exit app when back is pressed on intro screen
                finish();
            }
        });
    }

    private void setupStatusBar() {
        // Make status bar match the adventure theme
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.adventure_primary));

        // Make status bar icons light (white) for better visibility
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private void initializeViews() {
        // Find views from the new adventure layout
        btnGetStarted = findViewById(R.id.get_started_button);
        heroImageCard = findViewById(R.id.hero_image_card);
        contentCard = findViewById(R.id.content_card);

        // Set up click listener with animation
        btnGetStarted.setOnClickListener(v -> {
            // Add button press animation
            v.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction(() -> {
                        v.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(100)
                                .withEndAction(this::navigateToSignup)
                                .start();
                    })
                    .start();
        });
    }

    private void addEntranceAnimations() {
        // Initially hide elements
        heroImageCard.setScaleX(0f);
        heroImageCard.setScaleY(0f);
        contentCard.setTranslationY(300f);
        contentCard.setAlpha(0f);

        // Animate hero image with scale effect
        heroImageCard.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(800)
                .setStartDelay(300)
                .start();

        // Animate floating activity icons with stagger effect
        animateFloatingIcon(R.id.hiking_icon, 500);
        animateFloatingIcon(R.id.cycling_icon, 700);
        animateFloatingIcon(R.id.running_icon, 900);

        // Animate content card from bottom
        contentCard.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(600)
                .setStartDelay(600)
                .start();
    }

    private void animateFloatingIcon(int viewId, int delay) {
        View icon = findViewById(viewId);
        if (icon != null) {
            icon.setScaleX(0f);
            icon.setScaleY(0f);
            icon.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(400)
                    .setStartDelay(delay)
                    .start();

            // Add continuous floating animation
            icon.postDelayed(() -> startFloatingAnimation(icon), delay + 400);
        }
    }

    private void startFloatingAnimation(View view) {
        view.animate()
                .translationY(-15f)
                .setDuration(2000)
                .withEndAction(() -> {
                    view.animate()
                            .translationY(0f)
                            .setDuration(2000)
                            .withEndAction(() -> startFloatingAnimation(view))
                            .start();
                })
                .start();
    }

    private void navigateToSignup() {
        // Navigate to signup screen with smooth transition
        Intent intent = new Intent(IntroActivity.this, SignActivity.class);
        startActivity(intent);

        // Add custom transition animation
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_in_left);

        // Finish this activity so user can't go back to intro
        finish();
    }
}


