package com.example.signinui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MapsFragment extends Fragment implements OnMapReadyCallback {
    private Polyline currentPolyline;
    private static final String TAG = "MapsFragment";
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private Location currentLocation;
    private LocationCallback locationCallback;
    private Marker userMarker;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private final Gson gson = new Gson();
    private final List<Marker> trailMarkers = new ArrayList<>();

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    if (mMap != null) getCurrentLocation();
                } else {
                    Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show();
                }
            });

    public MapsFragment() { }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_maps, container, false);
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        SupportMapFragment mapFragment =
                (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        return view;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            getCurrentLocation();
            mMap.setMyLocationEnabled(true);
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        // Marker click listener for showing route
        mMap.setOnMarkerClickListener(marker -> {
            if (!marker.equals(userMarker)) {
                if (currentLocation != null) {
                    drawRouteToMarker(marker.getPosition());
                }
            }
            return false;
        });
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 10000
        ).setMinUpdateIntervalMillis(5000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) return;

                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        currentLocation = location;
                        updateUserMarker();

                        fetchNearbyTrails(currentLocation);
                        fetchNearbyParksAndRecreation(currentLocation);
                    }
                }
            }
        };

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());

        fusedLocationProviderClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                currentLocation = location;
                updateUserMarker();
                fetchNearbyTrails(location);
                fetchNearbyParksAndRecreation(location);
            }
        });
    }

    private void updateUserMarker() {
        if (mMap != null && currentLocation != null) {
            LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

            if (userMarker == null) {
                userMarker = mMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title("You are here")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
            } else {
                userMarker.setPosition(latLng);
            }

            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 13f));
        }
    }

    private void fetchNearbyTrails(Location location) {
        String apiKey = getString(R.string.map_api);
        double lat = location.getLatitude();
        double lng = location.getLongitude();

        String[] searchTypes = {
                "hiking trail", "running track", "walking trail", "nature trail", "bike trail", "jogging track"
        };

        for (String searchType : searchTypes) {
            String url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                    "?location=" + lat + "," + lng +
                    "&radius=10000&type=park&keyword=" + searchType +
                    "&key=" + apiKey;

            makeTrailRequest(url, searchType);
        }
    }

    private void fetchNearbyParksAndRecreation(Location location) {
        String apiKey = getString(R.string.map_api);
        double lat = location.getLatitude();
        double lng = location.getLongitude();

        String url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                "?location=" + lat + "," + lng +
                "&radius=15000&type=park|tourist_attraction" +
                "&keyword=national park|state park|forest|nature reserve|recreation area" +
                "&key=" + apiKey;

        makeTrailRequest(url, "Parks & Recreation");
    }

    private void makeTrailRequest(String url, String category) {
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to fetch " + category, e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) return;

                String jsonData = response.body().string();

                try {
                    JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
                    if (!jsonObject.has("results")) return;

                    JsonArray results = jsonObject.getAsJsonArray("results");
                    if (results != null && results.size() > 0) {
                        requireActivity().runOnUiThread(() -> addTrailMarkers(results, category));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing JSON for " + category, e);
                }
            }
        });
    }

    private void addTrailMarkers(JsonArray results, String category) {
        if (mMap == null) return;

        for (int i = 0; i < Math.min(results.size(), 20); i++) {
            try {
                JsonObject place = results.get(i).getAsJsonObject();
                JsonObject geometry = place.getAsJsonObject("geometry");
                if (geometry == null) continue;

                JsonObject locationObj = geometry.getAsJsonObject("location");
                if (locationObj == null) continue;

                double lat = locationObj.get("lat").getAsDouble();
                double lng = locationObj.get("lng").getAsDouble();
                LatLng latLng = new LatLng(lat, lng);

                String name = place.has("name") ? place.get("name").getAsString() : "Unknown Trail";

                float markerColor = getMarkerColor(category);

                Marker marker = mMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title(name)
                        .snippet(category)
                        .icon(BitmapDescriptorFactory.defaultMarker(markerColor)));

                trailMarkers.add(marker);
            } catch (Exception ignored) {}
        }
    }

    private float getMarkerColor(String category) {
        if (category.toLowerCase().contains("trail")) return BitmapDescriptorFactory.HUE_GREEN;
        if (category.toLowerCase().contains("park")) return BitmapDescriptorFactory.HUE_ORANGE;
        return BitmapDescriptorFactory.HUE_RED;
    }

    private void drawRouteToMarker(LatLng destination) {
        if (currentLocation == null) return;

        // Remove previous polyline if it exists
        if (currentPolyline != null) {
            currentPolyline.remove();
            currentPolyline = null;
        }

        String apiKey = getString(R.string.map_api);
        String origin = currentLocation.getLatitude() + "," + currentLocation.getLongitude();
        String dest = destination.latitude + "," + destination.longitude;

        String url = "https://maps.googleapis.com/maps/api/directions/json" +
                "?origin=" + origin + "&destination=" + dest +
                "&mode=walking&key=" + apiKey;

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Directions API call failed", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) return;

                String jsonData = response.body().string();
                try {
                    JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
                    JsonArray routes = jsonObject.getAsJsonArray("routes");
                    if (routes.size() == 0) return;

                    JsonObject route = routes.get(0).getAsJsonObject();
                    JsonObject overviewPolyline = route.getAsJsonObject("overview_polyline");
                    String points = overviewPolyline.get("points").getAsString();

                    List<LatLng> path = decodePoly(points);

                    requireActivity().runOnUiThread(() -> {
                        PolylineOptions polylineOptions = new PolylineOptions()
                                .addAll(path)
                                .color(0xFF0000FF)
                                .width(10f);

                        // Store the reference to the new polyline
                        currentPolyline = mMap.addPolyline(polylineOptions);
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Error parsing Directions API response", e);
                }
            }
        });
    }

    private List<LatLng> decodePoly(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            poly.add(new LatLng(lat / 1E5, lng / 1E5));
        }
        return poly;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (fusedLocationProviderClient != null && locationCallback != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        }
    }
}
