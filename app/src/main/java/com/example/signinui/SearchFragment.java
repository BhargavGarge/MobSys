package com.example.signinui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class SearchFragment extends Fragment {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final long LOCATION_UPDATE_INTERVAL = 10000;
    private static final long FASTEST_LOCATION_UPDATE_INTERVAL = 5000;
    private static final String TAG = "LocationDebug";

    // Expanded Germany boundaries with buffer zones
    private static final double GERMANY_MIN_LAT = 46.0;
    private static final double GERMANY_MAX_LAT = 56.0;
    private static final double GERMANY_MIN_LNG = 4.0;
    private static final double GERMANY_MAX_LNG = 16.0;

    private FusedLocationProviderClient fusedLocationClient;
    private LinearLayout locationPermissionLayout;
    private LinearLayout currentLocationLayout;
    private Button enableLocationBtn;
    private ImageButton refreshLocationBtn;
    private TextView locationAddressText;
    private Geocoder geocoder;
    private LocationCallback locationCallback;
    private boolean isLocationRequestActive = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        initializeViews(view);
        setupLocationClient();
        setupClickListeners();
        checkLocationPermission();

        return view;
    }

    private void initializeViews(View view) {
        locationPermissionLayout = view.findViewById(R.id.location_permission_layout);
        currentLocationLayout = view.findViewById(R.id.current_location_layout);
        enableLocationBtn = view.findViewById(R.id.enable_location_btn);
        refreshLocationBtn = view.findViewById(R.id.refresh_location_btn);
        locationAddressText = view.findViewById(R.id.location_address_text);
    }

    private void setupLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        geocoder = new Geocoder(requireContext(), Locale.getDefault());

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) {
                    showLocationError("Null location result");
                    return;
                }

                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        Log.d(TAG, "New location from: " + location.getProvider() +
                                " Accuracy: " + location.getAccuracy() + "m");
                        updateLocationDisplay(location);
                        stopLocationUpdates();
                        break;
                    }
                }
            }
        };
    }

    private void setupClickListeners() {
        enableLocationBtn.setOnClickListener(v -> {
            if (isMockLocationEnabled()) {
                Toast.makeText(requireContext(),
                        "Please disable Mock Locations in Developer Options",
                        Toast.LENGTH_LONG).show();
            }
            requestLocationPermission();
        });

        refreshLocationBtn.setOnClickListener(v -> {
            if (isMockLocationEnabled()) {
                showLocationError("Mock locations enabled");
                return;
            }
            getCurrentLocation();
        });
    }

    private void checkLocationPermission() {
        if (hasLocationPermission()) {
            showCurrentLocationCard();
            getCurrentLocation();
        } else {
            showPermissionRequestCard();
        }
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            Toast.makeText(requireContext(),
                    "Location access is required for accurate route finding",
                    Toast.LENGTH_LONG).show();
        }

        requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }, LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showCurrentLocationCard();
                getCurrentLocation();
            } else {
                showPermissionRequestCard();
                Toast.makeText(requireContext(),
                        "Location permission denied - some features unavailable",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void getCurrentLocation() {
        if (!hasLocationPermission()) return;

        locationAddressText.setText("Detecting location...");
        Log.d(TAG, "Requesting location update");

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        Log.d(TAG, "Last known location from: " + location.getProvider());
                        updateLocationDisplay(location);
                    } else {
                        Log.d(TAG, "No cached location, requesting fresh update");
                        requestFreshLocation();
                    }
                })
                .addOnFailureListener(e -> {
                    showLocationError("Location request failed: " + e.getMessage());
                    Log.e(TAG, "Location error", e);
                });
    }

    @SuppressLint("MissingPermission")
    private void requestFreshLocation() {
        locationAddressText.setText("Getting precise location...");
        startLocationUpdates();
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        if (isLocationRequestActive || !hasLocationPermission()) return;

        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
                .setMinUpdateIntervalMillis(FASTEST_LOCATION_UPDATE_INTERVAL)
                .setWaitForAccurateLocation(true)
                .build();

        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                requireActivity().getMainLooper()
        );

        isLocationRequestActive = true;
        Log.d(TAG, "Started active location updates");
    }

    private void stopLocationUpdates() {
        if (!isLocationRequestActive) return;

        fusedLocationClient.removeLocationUpdates(locationCallback);
        isLocationRequestActive = false;
        Log.d(TAG, "Stopped location updates");
    }

    private void updateLocationDisplay(Location location) {
        String coordinates = String.format(Locale.getDefault(),
                "Lat: %.6f\nLng: %.6f\nAccuracy: %.1fm\nProvider: %s",
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy(),
                location.getProvider());

        locationAddressText.setText(coordinates);
        Log.d(TAG, "Raw location: " + coordinates);

        if (isMockLocationEnabled()) {
            showLocationError("Mock location detected!");
            return;
        }

        try {
            List<Address> addresses = geocoder.getFromLocation(
                    location.getLatitude(),
                    location.getLongitude(),
                    1);

            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                String addressText = formatAddress(address);

                if (!isWithinGermany(location)) {
                    addressText += "\n\n⚠️ Location outside Germany bounds";
                }

                locationAddressText.setText(addressText);
                Log.d(TAG, "Geocoded to: " + addressText);
            } else {
                showCoordinateFallback(location, "No address found");
            }
        } catch (IOException e) {
            showCoordinateFallback(location, "Geocoding failed: " + e.getMessage());
            Log.e(TAG, "Geocoding error", e);
        }
    }

    private boolean isWithinGermany(Location location) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        return lat >= GERMANY_MIN_LAT && lat <= GERMANY_MAX_LAT &&
                lng >= GERMANY_MIN_LNG && lng <= GERMANY_MAX_LNG;
    }

    private String formatAddress(Address address) {
        StringBuilder sb = new StringBuilder();

        if (address.getThoroughfare() != null) {
            sb.append(address.getThoroughfare());
            if (address.getSubThoroughfare() != null) {
                sb.append(" ").append(address.getSubThoroughfare());
            }
            sb.append("\n");
        }

        if (address.getPostalCode() != null) {
            sb.append(address.getPostalCode()).append(" ");
        }

        if (address.getLocality() != null) {
            sb.append(address.getLocality()).append("\n");
        }

        if (address.getCountryName() != null) {
            sb.append(address.getCountryName());
        }

        return sb.toString();
    }

    private void showCoordinateFallback(Location location, String reason) {
        String text = String.format(Locale.getDefault(),
                "Coordinates:\n%.6f, %.6f\n\n%s\n\nProvider: %s\nAccuracy: %.1fm",
                location.getLatitude(),
                location.getLongitude(),
                reason,
                location.getProvider(),
                location.getAccuracy());

        locationAddressText.setText(text);
    }

    private void showLocationError(String message) {
        locationAddressText.setText("Error: " + message);
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
        Log.e(TAG, message);
    }

    private boolean isMockLocationEnabled() {
        return Settings.Secure.getString(
                requireContext().getContentResolver(),
                Settings.Secure.ALLOW_MOCK_LOCATION
        ).equals("1");
    }

    private void showPermissionRequestCard() {
        locationPermissionLayout.setVisibility(View.VISIBLE);
        currentLocationLayout.setVisibility(View.GONE);
    }

    private void showCurrentLocationCard() {
        locationPermissionLayout.setVisibility(View.GONE);
        currentLocationLayout.setVisibility(View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        checkLocationPermission();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
    }
}