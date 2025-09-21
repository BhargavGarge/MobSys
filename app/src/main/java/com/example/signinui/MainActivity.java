package com.example.signinui;

import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private OnBackPressedCallback backPressedCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up adventure theme status bar
        setupStatusBar();

        setContentView(R.layout.activity_main);

        // Initialize views
        initializeViews();

        // Set up bottom navigation
        setupBottomNavigation();

        // Set up modern back press handling
        setupBackPressHandling();
    }

    private void setupBackPressHandling() {
        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Check if we're on the home fragment
                Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                if (currentFragment instanceof HomeFragment) {
                    // If on home fragment, exit the app
                    finish();
                } else {
                    // Navigate back to home fragment
                    bottomNav.setSelectedItemId(R.id.nav_home);
                }
            }
        };

        // Add the callback to the dispatcher
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
    }

    private void setupStatusBar() {
        // Set status bar to match the adventure theme
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.adventure_primary));

        // Make status bar icons light for better visibility on green background
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(decorView.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private void initializeViews() {
        bottomNav = findViewById(R.id.bottom_nav);

        // Apply adventure theme colors to bottom navigation
        bottomNav.setItemIconTintList(ContextCompat.getColorStateList(this, R.color.green));
        bottomNav.setItemTextColor(ContextCompat.getColorStateList(this, R.color.green));
        bottomNav.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white));
    }

    private void setupBottomNavigation() {
        // Set listener for navigation items
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            String fragmentTag = "";

            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                selectedFragment = new HomeFragment();
                fragmentTag = "HOME";
            } else if (itemId == R.id.nav_routes) {
                selectedFragment = new MapsFragment();
                fragmentTag = "MAPS";
            } else if (itemId == R.id.nav_feed) {
                selectedFragment = new FeedFragment();
                fragmentTag = "FEED";
            } else if (itemId == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
                fragmentTag = "PROFILE";
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment, fragmentTag)
                        .commit();
            }

            return true;
        });

        // Set default selection to home
        bottomNav.setSelectedItemId(R.id.nav_home);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the current fragment if needed
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof HomeFragment) {
            // Refresh home fragment streak if returning from another activity
            ((HomeFragment) currentFragment).onResume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up callback when activity is destroyed
        if (backPressedCallback != null) {
            backPressedCallback.remove();
        }
    }
}