package com.example.signinui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MapsFragment extends Fragment {

    private static final String TAG = "MapsFragment";
    private MapView mapView;
    private MyLocationNewOverlay myLocationOverlay;
    private FusedLocationProviderClient fusedLocationClient;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private final Gson gson = new Gson();

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                        permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)) {
                    Log.d(TAG, "Location permission granted");
                    // Permissions granted, now find location
                    enableMyLocationOverlay();
                    findAndCenterOnUserLocation();
                } else {
                    Toast.makeText(requireContext(), "Location permission denied. Cannot show your location.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Context ctx = requireContext().getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(ctx.getPackageName());
        Configuration.getInstance().setOsmdroidBasePath(new File(ctx.getCacheDir(), "osmdroid"));
        Configuration.getInstance().setOsmdroidTileCache(new File(ctx.getCacheDir(), "tiles"));

        View view = inflater.inflate(R.layout.fragment_maps, container, false);
        mapView = view.findViewById(R.id.map);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // Basic OSMdroid setup
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setCenter(new GeoPoint(51.1657, 10.4515)); // Germany center
        mapView.getController().setZoom(8.0);

        // Overlays
        addMapOverlays();

        // Check services and request permissions
        checkLocationServices();
        requestLocationPermissionIfNeeded();

        return view;
    }

    private void addMapOverlays() {
        CompassOverlay compassOverlay = new CompassOverlay(requireContext(), new InternalCompassOrientationProvider(requireContext()), mapView);
        compassOverlay.enableCompass();
        mapView.getOverlays().add(compassOverlay);

        ScaleBarOverlay scaleBarOverlay = new ScaleBarOverlay(mapView);
        scaleBarOverlay.setAlignBottom(true);
        mapView.getOverlays().add(scaleBarOverlay);
    }

    private void checkLocationServices() {
        LocationManager locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (!isGpsEnabled && !isNetworkEnabled) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Location Services Disabled")
                    .setMessage("Please enable GPS or Network location in your device settings.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void requestLocationPermissionIfNeeded() {
        if (hasLocationPermissions()) {
            Log.d(TAG, "Location permissions already granted");
            enableMyLocationOverlay();
            findAndCenterOnUserLocation();
        } else {
            Log.d(TAG, "Requesting location permissions");
            requestPermissionsLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private boolean hasLocationPermissions() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * The main method to find the user's location. It prioritizes a fresh, high-accuracy
     * location. If that fails, it falls back to the last known location.
     */
    private void findAndCenterOnUserLocation() {
        if (!hasLocationPermissions()) {
            Log.w(TAG, "Cannot find location without permissions.");
            return;
        }

        try {
            // 1. A-Try to get a FRESH, CURRENT location
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(requireActivity(), location -> {
                        if (location != null) {
                            GeoPoint currentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
                            Log.d(TAG, "SUCCESS: Found current location: " + currentLocation);
                            centerMapOnLocation(currentLocation, "Current location found!");
                        } else {
                            // 2. B-Current location is null, fall back to LAST KNOWN location
                            Log.w(TAG, "Current location is null, trying last known location.");
                            getLastKnownLocation();
                        }
                    })
                    .addOnFailureListener(requireActivity(), e -> {
                        // 3. C-Failed to get current location, fall back to LAST KNOWN location
                        Log.e(TAG, "Failed to get current location, trying last known.", e);
                        getLastKnownLocation();
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while finding location.", e);
            showLocationError("Location permissions were denied.");
        }
    }

    /**
     * Fallback method to get the last known location.
     */
    private void getLastKnownLocation() {
        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
                if (location != null) {
                    GeoPoint lastKnownLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
                    Log.d(TAG, "SUCCESS (Fallback): Found last known location: " + lastKnownLocation);
                    centerMapOnLocation(lastKnownLocation, "Using last known location.");
                } else {
                    Log.e(TAG, "FAILURE: Last known location is also null. Cannot find user.");
                    showLocationError("Could not determine your location. Please ensure location services are enabled.");
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException on getLastLocation.", e);
        }
    }

    /**
     * Helper method to move the map and fetch trail data.
     */
    private void centerMapOnLocation(GeoPoint location, String toastMessage) {
        requireActivity().runOnUiThread(() -> {
            mapView.getController().animateTo(location);
            mapView.getController().setZoom(16.0);
            Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_SHORT).show();
            fetchNearbyTrailheadsFromGoogle(location);
        });
    }

    private void showLocationError(String message) {
        requireActivity().runOnUiThread(() ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        );
    }

    /**
     * Sets up the blue dot overlay on the map to show the user's live position.
     * This method no longer centers the map; it only handles the visual indicator.
     */
    private void enableMyLocationOverlay() {
        if (myLocationOverlay != null) {
            return; // Already enabled
        }
        if (!hasLocationPermissions()) {
            return;
        }
        try {
            GpsMyLocationProvider provider = new GpsMyLocationProvider(requireContext());
            provider.setLocationUpdateMinTime(2000);
            provider.setLocationUpdateMinDistance(10);
            myLocationOverlay = new MyLocationNewOverlay(provider, mapView);

            // Set a custom icon for the location marker
            try {
                Drawable drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_person_pin);
                Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
                myLocationOverlay.setPersonIcon(bitmap);
                myLocationOverlay.setPersonHotspot(0.5f, 0.95f); // Center bottom
            } catch (Exception e) {
                Log.w(TAG, "Failed to set custom person icon.", e);
            }

            myLocationOverlay.enableMyLocation();
            myLocationOverlay.setDrawAccuracyEnabled(true);
            mapView.getOverlays().add(0, myLocationOverlay); // Add at the bottom
        } catch (Exception e) {
            Log.e(TAG, "Error enabling MyLocationOverlay", e);
        }
    }

    private void fetchNearbyTrailheadsFromGoogle(GeoPoint center) {
        String apiKey = getString(R.string.map_api);
        double lat = center.getLatitude();
        double lng = center.getLongitude();

        Log.d(TAG, "Fetching trailheads near: " + lat + ", " + lng);

        String[] searchTypes = {"hiking trail", "running track", "walking trail"};
        int radiusMeters = 5000;

        for (String keyword : searchTypes) {
            String url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                    "?location=" + lat + "," + lng +
                    "&radius=" + radiusMeters +
                    "&type=park&keyword=" + keyword +
                    "&key=" + apiKey;

            Request request = new Request.Builder().url(url).build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Places fetch failed: " + keyword, e);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        Log.w(TAG, "Places API response not successful: " + response.code());
                        return;
                    }
                    String jsonData = response.body().string();

                    try {
                        JsonObject root = gson.fromJson(jsonData, JsonObject.class);
                        JsonArray results = root.has("results") ? root.getAsJsonArray("results") : null;
                        if (results == null || results.size() == 0) {
                            Log.d(TAG, "No results found for keyword: " + keyword);
                            return;
                        }

                        Log.d(TAG, "Found " + results.size() + " places for keyword: " + keyword);
                        requireActivity().runOnUiThread(() -> addTrailheadsAndQueryOSM(results, keyword));
                    } catch (Exception ex) {
                        Log.e(TAG, "Places parse error for keyword: " + keyword, ex);
                    }
                }
            });
        }
    }

    private void addTrailheadsAndQueryOSM(JsonArray results, String category) {
        int count = Math.min(results.size(), 10);
        Log.d(TAG, "Adding " + count + " trailheads for category: " + category);

        for (int i = 0; i < count; i++) {
            try {
                JsonObject place = results.get(i).getAsJsonObject();
                JsonObject geometry = place.getAsJsonObject("geometry");
                if (geometry == null) continue;
                JsonObject location = geometry.getAsJsonObject("location");
                if (location == null) continue;

                double lat = location.get("lat").getAsDouble();
                double lon = location.get("lng").getAsDouble();
                GeoPoint gp = new GeoPoint(lat, lon);

                String name = place.has("name") ? place.get("name").getAsString() : "Trail";
                String placeId = place.has("place_id") ? place.get("place_id").getAsString() : "";

                int iconRes = R.drawable.ic_trail;
                String lc = category.toLowerCase();
                if (lc.contains("hiking")) iconRes = R.drawable.ic_hiking;
                else if (lc.contains("running")) iconRes = R.drawable.ic_running;
                else if (lc.contains("walking")) iconRes = R.drawable.ic_walk;

                Marker m = new Marker(mapView);
                m.setPosition(gp);
                m.setTitle(name);
                m.setSubDescription(category);
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

                try {
                    m.setIcon(ContextCompat.getDrawable(requireContext(), iconRes));
                } catch (Exception e) {
                    Log.w(TAG, "Icon not found, using default marker", e);
                }

                m.setOnMarkerClickListener((marker, mapView) -> {
                    showPlaceDetails(placeId, name, category);
                    return true;
                });

                mapView.getOverlays().add(m);
                fetchTrailsFromOverpass(lat, lon);
            } catch (Exception e) {
                Log.e(TAG, "Error processing place result", e);
            }
        }
        mapView.invalidate();
    }

    private void showPlaceDetails(String placeId, String name, String category) {
        new AlertDialog.Builder(requireContext())
                .setTitle(name)
                .setMessage("Category: " + category + "\nPlace ID: " + placeId)
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Fetches trail geometry from Overpass API and draws them with type-specific colors.
     */
    private void fetchTrailsFromOverpass(double lat, double lon) {
        // Footpaths, paths, tracks, cycleways near the point (1km radius)
        String overpassUrl = "https://overpass-api.de/api/interpreter?data=[out:json];" +
                "way[\"highway\"~\"path|footway|track|cycleway\"](around:1000," + lat + "," + lon + ");out geom;";

        Request request = new Request.Builder().url(overpassUrl).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Overpass fetch failed", e);
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) return;
                String jsonData = response.body().string();

                try {
                    JsonObject json = gson.fromJson(jsonData, JsonObject.class);
                    JsonArray elements = json.getAsJsonArray("elements");
                    if (elements == null) return;

                    List<Polyline> polylines = new ArrayList<>();

                    for (JsonElement el : elements) {
                        JsonObject way = el.getAsJsonObject();
                        if (!way.has("geometry")) continue;

                        JsonArray geom = way.getAsJsonArray("geometry");
                        List<GeoPoint> pts = new ArrayList<>();
                        for (JsonElement cEl : geom) {
                            JsonObject c = cEl.getAsJsonObject();
                            pts.add(new GeoPoint(c.get("lat").getAsDouble(), c.get("lon").getAsDouble()));
                        }
                        if (pts.size() < 2) continue;

                        // Get the trail type from OSM tags
                        String highwayType = way.has("tags") && way.getAsJsonObject("tags").has("highway")
                                ? way.getAsJsonObject("tags").get("highway").getAsString()
                                : "trail";

                        // *** CHOOSE COLOR BASED ON TRAIL TYPE ***
                        int trailColor;
                        switch (highwayType) {
                            case "footway":
                            case "path":
                                // Brown for hiking/walking paths 🚶
                                trailColor = Color.parseColor("#8B4513"); // SaddleBrown
                                break;
                            case "track":
                                // Blue for wider tracks, good for running 🏃
                                trailColor = Color.parseColor("#4169E1"); // RoyalBlue
                                break;
                            case "cycleway":
                                // Purple for dedicated cycle paths 🚲
                                trailColor = Color.parseColor("#9932CC"); // DarkOrchid
                                break;
                            default:
                                // Dark Gray for any other type
                                trailColor = Color.parseColor("#2F4F4F"); // DarkSlateGray
                                break;
                        }

                        Polyline line = new Polyline(mapView);
                        line.setPoints(pts);
                        line.setWidth(8f);
                        line.setColor(trailColor); // Set the dynamic color here

                        // Make polyline clickable with trail information
                        line.setTitle("Trail Path");
                        line.setSnippet("Type: " + highwayType);

                        line.setOnClickListener((polyline, mapView, eventPos) -> {
                            Toast.makeText(requireContext(), polyline.getSnippet(), Toast.LENGTH_SHORT).show();
                            return true;
                        });

                        polylines.add(line);
                    }

                    requireActivity().runOnUiThread(() -> {
                        mapView.getOverlays().addAll(polylines);
                        mapView.invalidate();
                    });
                } catch (Exception ex) {
                    Log.e(TAG, "Overpass parse error", ex);
                }
            }
        });
    }

    // --- Lifecycle methods for OSMdroid MapView ---
    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        if (myLocationOverlay != null && !myLocationOverlay.isMyLocationEnabled() && hasLocationPermissions()) {
            myLocationOverlay.enableMyLocation();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
        if (myLocationOverlay != null) {
            myLocationOverlay.disableMyLocation();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (myLocationOverlay != null) {
            myLocationOverlay.disableMyLocation();
            myLocationOverlay = null;
        }
        if (mapView != null) {
            mapView.onDetach();
        }
    }
}