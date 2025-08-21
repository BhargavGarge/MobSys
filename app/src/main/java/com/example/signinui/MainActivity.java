package com.example.signinui;


import android.app.Activity;
import android.os.Bundle;


import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;


public class MainActivity extends AppCompatActivity {


    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views

        bottomNav = findViewById(R.id.bottom_nav);

        // Set up bottom navigation
        setupBottomNavigation();



    }

    private void setupBottomNavigation() {
        // Set listener for navigation items
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;

            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.nav_routes) {
                selectedFragment = new MapsFragment();
            } else if (itemId == R.id.nav_profile) {

                
                selectedFragment = new ProfileFragment();
            }
            else if (itemId == R.id.nav_steps) {
                selectedFragment = new StepCounterFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
            }

            return true;
        });

        // Set default selection
        bottomNav.setSelectedItemId(R.id.nav_home);
    }
}