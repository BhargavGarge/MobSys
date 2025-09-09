package com.example.signinui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private NavigationViewModel navigationViewModel;
    private FloatingActionButton fabNavigation;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);
        fabNavigation = findViewById(R.id.fab_navigation);

        // Initialize ViewModel
        navigationViewModel = new ViewModelProvider(this).get(NavigationViewModel.class);

        // Setup Bottom Navigation
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.map) {
                selectedFragment = new MapsFragment();
            } else if (itemId == R.id.nav_profile) {
                selectedFragment = new ProfileFragment();
            }
            // Add other fragments as needed

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment).commit();
            }
            return true;
        });

        // Set default fragment
        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.map);
        }

        // Observe navigation state to show/hide FAB
        navigationViewModel.getIsNavigating().observe(this, isNavigating -> {
            if (isNavigating) {
                fabNavigation.show();
            } else {
                fabNavigation.hide();
            }
        });

        // FAB click listener to return to the maps screen
        fabNavigation.setOnClickListener(view -> {
            bottomNav.setSelectedItemId(R.id.map);
        });
    }
}