package com.example.signinui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MapsFragment extends Fragment implements TextToSpeech.OnInitListener {

    private static final String TAG = "MapsFragment";
    private MapView mapView;
    private MyLocationNewOverlay myLocationOverlay;
    private FusedLocationProviderClient fusedLocationClient;
    private Spinner trailTypeSpinner;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    private final Gson gson = new Gson();

    // Filter variables
    private String currentFilter = "all";
    private final List<Polyline> allTrailLines = new ArrayList<>();
    private final List<Marker> allTrailMarkers = new ArrayList<>();
    private final Map<String, TrailDetails> trailDetailsMap = new HashMap<>();

    // Navigation related variables
    private boolean isNavigating = false;
    private NavigationData currentNavigation = null;
    private TextToSpeech textToSpeech;
    private LocationCallback navigationLocationCallback;

    // Navigation UI elements
    private CardView navigationPanel;
    private TextView navigationInstruction;
    private TextView distanceToNext;
    private TextView remainingDistance;
    private TextView estimatedTime;
    private ProgressBar navigationProgress;
    private Button stopNavigationButton;

    // Current location tracking for navigation
    private GeoPoint lastKnownLocation;
    private Polyline navigationRoute;
    private List<NavigationStep> navigationSteps;
    private int currentStepIndex = 0;

    // Data classes
    private static class TrailDetails {
        String name, description, difficulty, distance, elevation, type, location;
        double rating;
        List<GeoPoint> routePoints;

        TrailDetails(String name, String description, String difficulty, String distance, String elevation, String type, double rating, String location) {
            this.name = name;
            this.description = description;
            this.difficulty = difficulty;
            this.distance = distance;
            this.elevation = elevation;
            this.type = type;
            this.rating = rating;
            this.location = location;
            this.routePoints = new ArrayList<>();
        }
    }

    private static class NavigationData {
        String trailName, trailType;
        List<GeoPoint> routePoints;
        double totalDistance;

        NavigationData(String trailName, List<GeoPoint> routePoints, String trailType, double totalDistance) {
            this.trailName = trailName;
            this.routePoints = routePoints;
            this.trailType = trailType;
            this.totalDistance = totalDistance;
        }
    }

    private static class NavigationStep {
        GeoPoint point;
        String instruction;
        double distance;
        String direction;

        NavigationStep(GeoPoint point, String instruction, double distance, String direction) {
            this.point = point;
            this.instruction = instruction;
            this.distance = distance;
            this.direction = direction;
        }
    }

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                        permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)) {
                    enableMyLocationOverlay();
                    findAndCenterOnUserLocation();
                } else {
                    Toast.makeText(requireContext(), "Location permission denied.", Toast.LENGTH_LONG).show();
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

        textToSpeech = new TextToSpeech(requireContext(), this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // *** ENHANCEMENT: Use a topographic map style for an outdoor feel ***
        mapView.setTileSource(TileSourceFactory.OpenTopo);
        mapView.setMultiTouchControls(true);
        mapView.getController().setCenter(new GeoPoint(51.1657, 10.4515));
        mapView.getController().setZoom(8.0);

        initNavigationUI(view);
        addMapOverlays();
        setupTrailTypeSpinner(view);
        checkLocationServices();
        requestLocationPermissionIfNeeded();

        return view;
    }

    private void initNavigationUI(View view) {
        navigationPanel = view.findViewById(R.id.navigation_panel);
        navigationInstruction = view.findViewById(R.id.navigation_instruction);
        distanceToNext = view.findViewById(R.id.distance_to_next);
        remainingDistance = view.findViewById(R.id.remaining_distance);
        estimatedTime = view.findViewById(R.id.estimated_time);
        navigationProgress = view.findViewById(R.id.navigation_progress);
        stopNavigationButton = view.findViewById(R.id.stop_navigation_button);
        if (stopNavigationButton != null) {
            stopNavigationButton.setOnClickListener(v -> stopNavigation());
        }
        if (navigationPanel != null) {
            navigationPanel.setVisibility(View.GONE);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.getDefault());
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "TTS Language not supported");
            }
        } else {
            Log.e(TAG, "TTS initialization failed");
        }
    }

    private void setupTrailTypeSpinner(View view) {
        trailTypeSpinner = view.findViewById(R.id.trail_type_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(requireContext(), R.array.trail_types_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        trailTypeSpinner.setAdapter(adapter);
        trailTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilterFromSpinner(parent.getItemAtPosition(position).toString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void applyFilterFromSpinner(String selectedItem) {
        String filter;
        switch (selectedItem) {
            case "Hiking Trails":
                filter = "hiking";
                break;
            case "Running Trails":
                filter = "running";
                break;
            case "Cycling Trails":
                filter = "cycling";
                break;
            default:
                filter = "all";
                break;
        }
        applyFilter(filter);
    }

    private void applyFilter(String filterType) {
        currentFilter = filterType;
        filterTrails();
        Toast.makeText(requireContext(), "Showing " + filterType + " trails", Toast.LENGTH_SHORT).show();
    }

    private void filterTrails() {
        List<org.osmdroid.views.overlay.Overlay> overlaysToKeep = new ArrayList<>();
        if (myLocationOverlay != null) overlaysToKeep.add(myLocationOverlay);
        if (navigationRoute != null) overlaysToKeep.add(navigationRoute);

        mapView.getOverlays().retainAll(overlaysToKeep);

        for (Marker marker : allTrailMarkers) {
            String subDescription = marker.getSubDescription();
            if (shouldShowMarker(subDescription)) {
                mapView.getOverlays().add(marker);
            }
        }

        for (Polyline line : allTrailLines) {
            String snippet = line.getSnippet();
            if (snippet != null && snippet.startsWith("Type: ")) {
                String trailType = snippet.replace("Type: ", "").toLowerCase();
                if (shouldShowPolyline(trailType)) {
                    mapView.getOverlays().add(line);
                }
            }
        }
        mapView.invalidate();
    }

    private boolean shouldShowMarker(String category) {
        if (category == null) return currentFilter.equals("all");
        String lc = category.toLowerCase();
        switch (currentFilter) {
            case "all":
                return true;
            case "hiking":
                return lc.contains("hiking") || lc.contains("nature") || lc.contains("mountain");
            case "running":
                return lc.contains("running") || lc.contains("jogging");
            case "cycling":
                return lc.contains("bike") || lc.contains("cycling");
            default:
                return false;
        }
    }

    private boolean shouldShowPolyline(String trailType) {
        switch (currentFilter) {
            case "all":
                return true;
            case "hiking":
                return trailType.equals("footway") || trailType.equals("path") || trailType.equals("steps");
            case "running":
                return trailType.equals("track") || trailType.equals("path");
            case "cycling":
                return trailType.equals("cycleway");
            default:
                return false;
        }
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
        LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Location Services Disabled")
                    .setMessage("Please enable GPS or Network location in your device settings.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void requestLocationPermissionIfNeeded() {
        if (hasLocationPermissions()) {
            enableMyLocationOverlay();
            findAndCenterOnUserLocation();
        } else {
            requestPermissionsLauncher.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    private boolean hasLocationPermissions() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void findAndCenterOnUserLocation() {
        if (!hasLocationPermissions()) return;
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(requireActivity(), location -> {
                        if (location != null) {
                            GeoPoint currentGeoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                            lastKnownLocation = currentGeoPoint;
                            centerMapOnLocation(currentGeoPoint, "Current location found!");
                        } else {
                            getLastKnownLocation();
                        }
                    })
                    .addOnFailureListener(requireActivity(), e -> getLastKnownLocation());
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while finding location.", e);
        }
    }

    private void getLastKnownLocation() {
        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
                if (location != null) {
                    GeoPoint lastGeoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                    lastKnownLocation = lastGeoPoint;
                    centerMapOnLocation(lastGeoPoint, "Using last known location.");
                } else {
                    Toast.makeText(requireContext(), "Could not determine your location.", Toast.LENGTH_LONG).show();
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException on getLastLocation.", e);
        }
    }

    private void centerMapOnLocation(GeoPoint location, String toastMessage) {
        requireActivity().runOnUiThread(() -> {
            if (!isNavigating) {
                mapView.getController().animateTo(location);
                mapView.getController().setZoom(16.0);
            }
            Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_SHORT).show();
            fetchNearbyTrailheadsFromGoogle(location);
        });
    }

    private void enableMyLocationOverlay() {
        if (myLocationOverlay != null || !hasLocationPermissions()) return;
        try {
            GpsMyLocationProvider provider = new GpsMyLocationProvider(requireContext());
            provider.setLocationUpdateMinTime(2000);
            provider.setLocationUpdateMinDistance(10);
            myLocationOverlay = new MyLocationNewOverlay(provider, mapView);

            try {
                Drawable drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_person_pin);
                Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
                myLocationOverlay.setPersonIcon(bitmap);
                myLocationOverlay.setPersonHotspot(0.5f, 0.95f);
            } catch (Exception e) {
                Log.w(TAG, "Failed to set custom person icon.", e);
            }

            myLocationOverlay.enableMyLocation();
            myLocationOverlay.setDrawAccuracyEnabled(true);
            mapView.getOverlays().add(0, myLocationOverlay);
        } catch (Exception e) {
            Log.e(TAG, "Error enabling MyLocationOverlay", e);
        }
    }

    private void fetchNearbyTrailheadsFromGoogle(GeoPoint center) {
        String apiKey = getString(R.string.map_api);
        String[] searchTerms = {"hiking trail", "nature trail", "bike trail", "running track"};
        int radiusMeters = 15000;

        for (String keyword : searchTerms) {
            String url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                    "?location=" + center.getLatitude() + "," + center.getLongitude() +
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
                    if (!response.isSuccessful()) return;
                    String jsonData = response.body().string();
                    try {
                        JsonObject root = gson.fromJson(jsonData, JsonObject.class);
                        JsonArray results = root.getAsJsonArray("results");
                        if (results != null) {
                            requireActivity().runOnUiThread(() -> addTrailheadsAndQueryOSM(results, keyword));
                        }
                    } catch (Exception ex) {
                        Log.e(TAG, "Places parse error", ex);
                    }
                }
            });
        }
    }

    private void addTrailheadsAndQueryOSM(JsonArray results, String category) {
        int count = Math.min(results.size(), 20);
        for (int i = 0; i < count; i++) {
            try {
                JsonObject place = results.get(i).getAsJsonObject();
                JsonObject geometry = place.getAsJsonObject("geometry");
                JsonObject location = geometry.getAsJsonObject("location");
                double lat = location.get("lat").getAsDouble();
                double lon = location.get("lng").getAsDouble();
                GeoPoint gp = new GeoPoint(lat, lon);
                String name = place.get("name").getAsString();
                String placeId = place.get("place_id").getAsString();
                double rating = place.has("rating") ? place.get("rating").getAsDouble() : 0.0;
                TrailDetails details = generateTrailDetails(name, category, rating, gp);
                trailDetailsMap.put(placeId, details);
                Marker m = new Marker(mapView);
                m.setPosition(gp);
                m.setTitle(name);
                m.setSubDescription(category);
                m.setOnMarkerClickListener((marker, mapView) -> {
                    showTrailDetailsDialog(placeId, details);
                    return true;
                });
                allTrailMarkers.add(m);
                if (shouldShowMarker(category)) {
                    mapView.getOverlays().add(m);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing place result", e);
            }
        }
        mapView.invalidate();
    }

    private TrailDetails generateTrailDetails(String name, String category, double rating, GeoPoint startPoint) {
        Random random = new Random();
        String[] difficulties = {"Easy", "Moderate", "Challenging"};
        String difficulty = difficulties[random.nextInt(difficulties.length)];
        double distanceKm = 2.0 + random.nextDouble() * 8.0;
        String distance = String.format(Locale.US, "%.1f km", distanceKm);
        String elevation = String.format(Locale.US, "%d m", 50 + random.nextInt(450));
        String description = "A scenic " + category + " trail rated as " + difficulty + ".";
        double finalRating = (rating == 0.0) ? (3.0 + random.nextDouble() * 2.0) : rating;
        TrailDetails details = new TrailDetails(name, description, difficulty, distance, elevation, category, finalRating, "Near you");
        details.routePoints.add(startPoint);
        return details;
    }

    private void showTrailDetailsDialog(String placeId, TrailDetails details) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(details.name);
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 30);
        addInfoRow(layout, "Type", details.type, Color.BLACK);
        addInfoRow(layout, "Difficulty", details.difficulty, getDifficultyColor(details.difficulty));
        addInfoRow(layout, "Distance", details.distance, Color.BLACK);
        addInfoRow(layout, "Elevation", details.elevation, Color.BLACK);
        addInfoRow(layout, "Rating", getStarRating(details.rating), Color.parseColor("#FFA500"));
        TextView descriptionView = new TextView(requireContext());
        descriptionView.setText(details.description);
        descriptionView.setPadding(0, 20, 0, 0);
        layout.addView(descriptionView);
        builder.setView(layout);
        builder.setPositiveButton("Start Navigation", (dialog, which) -> startNavigation(details));
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void startNavigation(TrailDetails details) {
        if (!hasLocationPermissions()) {
            Toast.makeText(requireContext(), "Location permission required for navigation", Toast.LENGTH_SHORT).show();
            requestLocationPermissionIfNeeded();
            return;
        }
        fetchRealRouteAndStartNavigation(details);
    }

    private void fetchRealRouteAndStartNavigation(TrailDetails details) {
        if (lastKnownLocation == null || details.routePoints.isEmpty()) {
            Toast.makeText(requireContext(), "Cannot determine start or end point for navigation.", Toast.LENGTH_SHORT).show();
            return;
        }

        GeoPoint startPoint = lastKnownLocation;
        GeoPoint endPoint = details.routePoints.get(0);

        String apiKey = getString(R.string.ors_api_key);
        String url = "https://api.openrouteservice.org/v2/directions/foot-hiking/geojson";

        String postBody = String.format(Locale.US,
                "{\"coordinates\":[[%f,%f],[%f,%f]]}",
                startPoint.getLongitude(), startPoint.getLatitude(),
                endPoint.getLongitude(), endPoint.getLatitude()
        );

        okhttp3.RequestBody body = okhttp3.RequestBody.create(postBody, okhttp3.MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", apiKey)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Openrouteservice fetch failed", e);
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Failed to calculate route.", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e(TAG, "Openrouteservice response error: " + response.code() + " " + response.message());
                    return;
                }

                try {
                    String responseData = response.body().string();
                    JsonObject json = gson.fromJson(responseData, JsonObject.class);

                    JsonArray features = json.getAsJsonArray("features");
                    if (features == null || features.size() == 0) return;

                    JsonObject feature = features.get(0).getAsJsonObject();
                    JsonObject properties = feature.getAsJsonObject("properties");
                    JsonObject geometry = feature.getAsJsonObject("geometry");

                    JsonArray segments = properties.getAsJsonArray("segments");
                    JsonArray stepsJson = segments.get(0).getAsJsonObject().getAsJsonArray("steps");
                    JsonArray coordinates = geometry.getAsJsonArray("coordinates");

                    List<GeoPoint> routePoints = new ArrayList<>();
                    for (JsonElement coord : coordinates) {
                        JsonArray lonLat = coord.getAsJsonArray();
                        routePoints.add(new GeoPoint(lonLat.get(1).getAsDouble(), lonLat.get(0).getAsDouble()));
                    }

                    List<NavigationStep> navigationSteps = parseOrsSteps(stepsJson, routePoints);

                    requireActivity().runOnUiThread(() -> {
                        startNavigationWithRealData(details, routePoints, navigationSteps);
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Error parsing ORS response", e);
                }
            }
        });
    }

    private List<NavigationStep> parseOrsSteps(JsonArray stepsJson, List<GeoPoint> routePoints) {
        List<NavigationStep> steps = new ArrayList<>();
        if (stepsJson == null) return steps;

        for (JsonElement stepElement : stepsJson) {
            JsonObject stepObject = stepElement.getAsJsonObject();
            String instruction = stepObject.get("instruction").getAsString();
            double distance = stepObject.get("distance").getAsDouble();
            JsonArray wayPoints = stepObject.get("way_points").getAsJsonArray();
            int pointIndex = wayPoints.get(0).getAsInt();
            GeoPoint stepLocation = routePoints.get(pointIndex);
            steps.add(new NavigationStep(stepLocation, instruction, distance, ""));
        }
        return steps;
    }

    private void startNavigationWithRealData(TrailDetails details, List<GeoPoint> realRoutePoints, List<NavigationStep> realSteps) {
        try {
            if (realRoutePoints.size() < 2) {
                Toast.makeText(requireContext(), "Invalid route data received.", Toast.LENGTH_SHORT).show();
                return;
            }
            isNavigating = true;
            currentNavigation = new NavigationData(
                    details.name,
                    realRoutePoints,
                    details.type,
                    Double.parseDouble(details.distance.replace(" km", ""))
            );

            this.navigationSteps = realSteps;
            this.currentStepIndex = 0;

            if (navigationPanel != null) {
                navigationPanel.setVisibility(View.VISIBLE);
            }

            drawNavigationRoute(realRoutePoints);
            startNavigationLocationUpdates();
            updateNavigationUI();

            Toast.makeText(requireContext(), "Navigation started for " + details.name, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error starting navigation with real data", e);
        }
    }

    private void drawNavigationRoute(List<GeoPoint> routePoints) {
        if (navigationRoute != null) {
            mapView.getOverlays().remove(navigationRoute);
        }
        navigationRoute = new Polyline(mapView);
        navigationRoute.setPoints(routePoints);
        navigationRoute.setWidth(12f);
        navigationRoute.setColor(Color.parseColor("#4285F4"));
        navigationRoute.setTitle("Navigation Route");
        mapView.getOverlays().add(navigationRoute);
        mapView.invalidate();
    }

    private void startNavigationLocationUpdates() {
        if (navigationLocationCallback != null) {
            fusedLocationClient.removeLocationUpdates(navigationLocationCallback);
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(1000)
                .build();

        navigationLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (!isNavigating) return;
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    lastKnownLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
                    updateNavigation(lastKnownLocation);
                }
            }
        };

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, navigationLocationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException when requesting location updates", e);
        }
    }

    private void updateNavigation(GeoPoint currentLocation) {
        if (navigationSteps == null || navigationSteps.isEmpty() || currentStepIndex >= navigationSteps.size()) {
            return;
        }

        NavigationStep currentStep = navigationSteps.get(currentStepIndex);
        double distanceToStep = calculateDistance(currentLocation, currentStep.point);

        if (distanceToStep < 25 && currentStepIndex < navigationSteps.size() - 1) {
            currentStepIndex++;
            NavigationStep nextStep = navigationSteps.get(currentStepIndex);
            if (textToSpeech != null) {
                textToSpeech.speak(nextStep.instruction, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        }

        updateNavigationUI();
    }

    private void updateNavigationUI() {
        if (navigationSteps == null || currentStepIndex >= navigationSteps.size() || !isNavigating) {
            return;
        }

        final NavigationStep currentStep = navigationSteps.get(currentStepIndex);

        requireActivity().runOnUiThread(() -> {
            navigationInstruction.setText(currentStep.instruction);

            double distanceToNextStep = calculateDistance(lastKnownLocation, currentStep.point);
            distanceToNext.setText(String.format(Locale.US, "%.0f m", distanceToNextStep));

            double remainingDist = calculateRemainingDistance();
            remainingDistance.setText(String.format(Locale.US, "%.1f km", remainingDist / 1000));

            int minutes = (int) ((remainingDist / 1000) / 5 * 60);
            estimatedTime.setText(String.format(Locale.US, "%d min", minutes));

            double totalDistance = currentNavigation.totalDistance * 1000;
            if (totalDistance > 0) {
                double traveled = totalDistance - remainingDist;
                int progress = (int) ((traveled / totalDistance) * 100);
                navigationProgress.setProgress(Math.max(0, Math.min(100, progress)));
            }
        });
    }

    private double calculateRemainingDistance() {
        if (navigationSteps == null || lastKnownLocation == null || currentStepIndex >= navigationSteps.size()) {
            return 0;
        }
        double remaining = 0;
        remaining += calculateDistance(lastKnownLocation, navigationSteps.get(currentStepIndex).point);
        for (int i = currentStepIndex; i < navigationSteps.size() - 1; i++) {
            remaining += calculateDistance(navigationSteps.get(i).point, navigationSteps.get(i + 1).point);
        }
        return remaining;
    }

    private double calculateDistance(GeoPoint p1, GeoPoint p2) {
        float[] results = new float[1];
        Location.distanceBetween(p1.getLatitude(), p1.getLongitude(), p2.getLatitude(), p2.getLongitude(), results);
        return results[0];
    }

    private void stopNavigation() {
        isNavigating = false;
        currentNavigation = null;
        if (navigationRoute != null) {
            mapView.getOverlays().remove(navigationRoute);
            navigationRoute = null;
        }
        if (navigationLocationCallback != null) {
            fusedLocationClient.removeLocationUpdates(navigationLocationCallback);
            navigationLocationCallback = null;
        }
        if (navigationPanel != null) {
            navigationPanel.setVisibility(View.GONE);
        }
        mapView.invalidate();
        Toast.makeText(requireContext(), "Navigation stopped", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (navigationLocationCallback != null) {
            fusedLocationClient.removeLocationUpdates(navigationLocationCallback);
        }
        mapView.onDetach();
    }

    private void addInfoRow(LinearLayout parent, String label, String value, int valueColor) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 8, 0, 8);
        TextView labelView = new TextView(requireContext());
        labelView.setText(label);
        labelView.setTextSize(15);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView valueView = new TextView(requireContext());
        valueView.setText(value);
        valueView.setTextSize(15);
        valueView.setTextColor(valueColor);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        valueView.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
        row.addView(labelView);
        row.addView(valueView);
        parent.addView(row);
    }

    private String getStarRating(double rating) {
        int fullStars = (int) rating;
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < fullStars; i++) {
            stars.append("★");
        }
        if ((rating - fullStars) >= 0.5) {
            stars.append("★");
        }
        while (stars.length() < 5) {
            stars.append("☆");
        }
        return stars.toString();
    }

    private int getDifficultyColor(String difficulty) {
        switch (difficulty.toLowerCase()) {
            case "easy":
                return Color.parseColor("#4CAF50"); // Green
            case "moderate":
                return Color.parseColor("#FF9800"); // Orange
            case "challenging":
                return Color.parseColor("#F44336"); // Red
            case "difficult":
                return Color.parseColor("#9C27B0"); // Purple
            default:
                return Color.BLACK;
        }
    }
}

