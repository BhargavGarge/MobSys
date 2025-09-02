package com.example.signinui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.signinui.model.Route;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class HomeFragment extends Fragment {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private FusedLocationProviderClient fusedLocationClient;
    private ImageView weatherIcon;
    private TextView temperatureText;
    private TextView userNameHero;
    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    // RecyclerView components for routes
    private RecyclerView recyclerView;
    private FeaturedRouteAdapter routeAdapter;
    private List<Route> routeList = new ArrayList<>();

    // Weather API constants
    private static final String API_KEY = "9bd3c0f117e2da1a013b82af5e348ba8";
    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5/";

    // Trail API constants - Using OpenTrails API (no key required)
    private static final String TRAIL_API_URL = "https://opentrails.wsp.com/api/trails";

    // Alternative API - US Forest Service (no key required)
    private static final String USFS_API_URL = "https://apps.fs.usda.gov/arcx/rest/services/EDW/EDW_RecreationOpportunities_01/MapServer/0/query?where=1%3D1&outFields=*&f=json";

    // Tag for logging
    private static final String TAG = "HomeFragment";

    // Add these new constants for better image variety
    private static final String[] HIKING_IMAGES = {
            "https://images.unsplash.com/photo-1551632811-561732d1e306?w=400&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1418065460487-3e41a6c84dc5?w=400&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=400&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1578662996442-48f60103fc96?w=400&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=400&auto=format&fit=crop"
    };

    private static final String[] CYCLING_IMAGES = {
            "https://images.unsplash.com/photo-1530919424169-4b95f917e937?w=400&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1511994298241-608e28f14fde?w=400&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=400&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1571068316344-75bc76f77890?w=400&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1549476464-37392f717541?w=400&auto=format&fit=crop"
    };

    private static final String[] RUNNING_IMAGES = {
            "https://images.unsplash.com/photo-1571008887538-b36bb32f4571?w=400&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?w=400&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1594882645126-14020914d58d?w=400&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1434682881908-b43d0467b798?w=400&auto=format&fit=crop",
            "https://images.unsplash.com/photo-1476480862126-209bfaa8edc8?w=400&auto=format&fit=crop"
    };

    private Random random = new Random();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Initialize views
        weatherIcon = view.findViewById(R.id.weather_icon);
        temperatureText = view.findViewById(R.id.temperature_text);
        userNameHero = view.findViewById(R.id.user_name_hero);

        // Initialize RecyclerView for routes
        recyclerView = view.findViewById(R.id.featured_routes_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(),
                LinearLayoutManager.HORIZONTAL, false));
        // Make sure to pass context to the adapter
        routeAdapter = new FeaturedRouteAdapter(routeList, requireContext());
        recyclerView.setAdapter(routeAdapter);

        // Load user data
        loadUserData();

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // Check location permission
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Request location permission
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        } else {
            // Permission already granted, get location
            getLocationAndWeather();
        }

        return view;
    }

    private void loadUserData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            mDatabase.child("users").child(currentUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String userName = snapshot.child("name").getValue(String.class);
                        if (userName != null) {
                            userNameHero.setText("Welcome, " + userName + "!");
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(requireContext(), "Failed to load user data", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLocationAndWeather();
            } else {
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show();
                loadMockRoutes(); // Load mock data even without location
            }
        }
    }

    private void getLocationAndWeather() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            loadMockRoutes();
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(requireActivity(), new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            double latitude = location.getLatitude();
                            double longitude = location.getLongitude();
                            getWeatherData(latitude, longitude);
                            fetchTrailData(latitude, longitude);
                        } else {
                            Toast.makeText(requireContext(), "Unable to get location", Toast.LENGTH_SHORT).show();
                            loadMockRoutes();
                        }
                    }
                });
    }

    private void getWeatherData(double lat, double lon) {
        // First try with user's API key
        if (!API_KEY.equals("YOUR_OPENWEATHER_API_KEY")) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            WeatherApiService service = retrofit.create(WeatherApiService.class);
            Call<WeatherResponse> call = service.getCurrentWeather(lat, lon, "metric", API_KEY);

            call.enqueue(new Callback<WeatherResponse>() {
                @Override
                public void onResponse(Call<WeatherResponse> call, Response<WeatherResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        WeatherResponse weatherResponse = response.body();
                        updateWeatherUI(weatherResponse);
                    } else {
                        // Fallback to mock weather data
                        setDefaultWeather();
                        Log.e(TAG, "Weather API failed: " + response.message());
                    }
                }

                @Override
                public void onFailure(Call<WeatherResponse> call, Throwable t) {
                    // Fallback to mock weather data
                    setDefaultWeather();
                    Log.e(TAG, "Weather API call failed", t);
                }
            });
        } else {
            // Use mock weather data if no API key is set
            setDefaultWeather();
            Toast.makeText(requireContext(), "Please set your OpenWeather API key", Toast.LENGTH_LONG).show();
        }
    }

    private void setDefaultWeather() {
        temperatureText.setText("22°C");
        weatherIcon.setImageResource(R.drawable.ic_sunny);
    }

    private void updateWeatherUI(WeatherResponse weatherResponse) {
        // Update temperature
        int temperature = (int) Math.round(weatherResponse.getMain().getTemp());
        temperatureText.setText(temperature + "°C");

        // Update weather icon based on condition
        if (weatherResponse.getWeather().length > 0) {
            String iconCode = weatherResponse.getWeather()[0].getIcon();
            setWeatherIcon(iconCode);
        }
    }

    private void setWeatherIcon(String iconCode) {
        int iconResource;

        switch (iconCode) {
            case "01d": // clear sky (day)
                iconResource = R.drawable.ic_sunny;
                break;
            case "01n": // clear sky (night)
                iconResource = R.drawable.ic_clear_night;
                break;
            case "02d": // few clouds (day)
            case "02n": // few clouds (night)
                iconResource = R.drawable.ic_partly_cloudy;
                break;
            case "03d": // scattered clouds (day)
            case "03n": // scattered clouds (night)
            case "04d": // broken clouds (day)
            case "04n": // broken clouds (night)
                iconResource = R.drawable.ic_cloudy;
                break;
            case "09d": // shower rain (day)
            case "09n": // shower rain (night)
            case "10d": // rain (day)
            case "10n": // rain (night)
                iconResource = R.drawable.ic_rain;
                break;
            case "11d": // thunderstorm (day)
            case "11n": // thunderstorm (night)
                iconResource = R.drawable.ic_thunderstorm;
                break;
            case "13d": // snow (day)
            case "13n": // snow (night)
                iconResource = R.drawable.ic_snow;
                break;
            case "50d": // mist (day)
            case "50n": // mist (night)
                iconResource = R.drawable.ic_mist;
                break;
            default:
                iconResource = R.drawable.ic_sunny;
        }

        weatherIcon.setImageResource(iconResource);
    }

    private void fetchTrailData(double lat, double lon) {
        Log.d(TAG, "Fetching trail data for location: " + lat + ", " + lon);

        // Try multiple APIs with fallback mechanism
        tryOpenTrailsAPI();
    }

    private void tryOpenTrailsAPI() {
        Log.d(TAG, "Trying OpenTrails API");

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                TRAIL_API_URL,
                null,
                response -> {
                    Log.d(TAG, "OpenTrails API response received");
                    try {
                        if (parseOpenTrailsResponse(response)) {
                            Log.d(TAG, "Successfully parsed trails from OpenTrails API");
                        } else {
                            Log.d(TAG, "OpenTrails API returned no data, trying US Forest Service API");
                            tryUSForestServiceAPI();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing OpenTrails response", e);
                        tryUSForestServiceAPI();
                    }
                },
                error -> {
                    Log.e(TAG, "OpenTrails API error: " + error.getMessage());
                    tryUSForestServiceAPI();
                }
        );

        RequestQueue queue = Volley.newRequestQueue(requireContext());
        queue.add(request);
    }

    private void tryUSForestServiceAPI() {
        Log.d(TAG, "Trying US Forest Service API");

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                USFS_API_URL,
                null,
                response -> {
                    Log.d(TAG, "US Forest Service API response received");
                    try {
                        if (parseUSFSResponse(response)) {
                            Log.d(TAG, "Successfully parsed trails from US Forest Service API");
                        } else {
                            Log.d(TAG, "All APIs failed, loading mock data");
                            loadMockRoutes();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing USFS response", e);
                        loadMockRoutes();
                    }
                },
                error -> {
                    Log.e(TAG, "US Forest Service API error: " + error.getMessage());
                    loadMockRoutes();
                }
        );

        RequestQueue queue = Volley.newRequestQueue(requireContext());
        queue.add(request);
    }

    private boolean parseOpenTrailsResponse(JSONObject response) {
        try {
            routeList.clear();

            // Parse the OpenTrails API response
            if (response.has("trails")) {
                JSONArray trails = response.getJSONArray("trails");

                for (int i = 0; i < Math.min(trails.length(), 5); i++) {
                    JSONObject trail = trails.getJSONObject(i);

                    String name = trail.optString("name", "Scenic Trail");
                    String type = determineRouteType(trail); // Improved type detection
                    String imageUrl = getRandomImageForType(type);

                    // Get trail details
                    double length = trail.optDouble("length", 5.0 + random.nextDouble() * 15);
                    double ascent = trail.optDouble("ascent", 100 + random.nextDouble() * 1000);

                    String details = String.format("%.1f km | %.0fm ↗", length, ascent);

                    routeList.add(new Route(name, type, imageUrl, details));
                }

                if (!routeList.isEmpty()) {
                    routeAdapter.notifyDataSetChanged();
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing OpenTrails data", e);
        }
        return false;
    }

    private String determineRouteType(JSONObject trail) {
        // Try to determine route type from various fields
        String activityType = trail.optString("activity_type", "").toLowerCase();
        String name = trail.optString("name", "").toLowerCase();

        if (activityType.contains("cycle") || activityType.contains("bike") ||
                name.contains("cycle") || name.contains("bike")) {
            return "Cycling";
        } else if (activityType.contains("run") || name.contains("run")) {
            return "Running";
        } else if (activityType.contains("hike") || name.contains("hike") ||
                name.contains("mountain") || name.contains("peak")) {
            return "Hiking";
        } else {
            // Default to a random type if we can't determine
            String[] types = {"Hiking", "Cycling", "Running"};
            return types[random.nextInt(types.length)];
        }
    }

    private String getRandomImageForType(String type) {
        switch (type.toLowerCase()) {
            case "cycling":
                return CYCLING_IMAGES[random.nextInt(CYCLING_IMAGES.length)];
            case "running":
                return RUNNING_IMAGES[random.nextInt(RUNNING_IMAGES.length)];
            case "hiking":
            default:
                return HIKING_IMAGES[random.nextInt(HIKING_IMAGES.length)];
        }
    }

    private boolean parseUSFSResponse(JSONObject response) {
        try {
            routeList.clear();

            // Parse the US Forest Service API response
            if (response.has("features")) {
                JSONArray features = response.getJSONArray("features");

                for (int i = 0; i < Math.min(features.length(), 5); i++) {
                    JSONObject feature = features.getJSONObject(i);
                    JSONObject attributes = feature.optJSONObject("attributes");

                    if (attributes != null) {
                        String name = attributes.optString("RECAREANAME", "Forest Trail");
                        String type = determineRouteTypeFromName(name); // Determine type from name
                        String imageUrl = getRandomImageForType(type);

                        // Generate random trail details
                        String details = getRandomTrailDetails();

                        routeList.add(new Route(name, type, imageUrl, details));
                    }
                }

                if (!routeList.isEmpty()) {
                    routeAdapter.notifyDataSetChanged();
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing USFS data", e);
        }
        return false;
    }

    private String determineRouteTypeFromName(String name) {
        String lowerName = name.toLowerCase();

        if (lowerName.contains("bike") || lowerName.contains("cycle") ||
                lowerName.contains("bicycle")) {
            return "Cycling";
        } else if (lowerName.contains("run") || lowerName.contains("jog")) {
            return "Running";
        } else if (lowerName.contains("climb") || lowerName.contains("mountain") ||
                lowerName.contains("peak") || lowerName.contains("summit")) {
            return "Hiking";
        } else {
            // Default to a random type if we can't determine
            String[] types = {"Hiking", "Cycling", "Running"};
            return types[random.nextInt(types.length)];
        }
    }

    private String getRandomTrailDetails() {
        // Generate random trail details
        double distance = 3.0 + random.nextDouble() * 20.0;
        int elevation = 100 + random.nextInt(1500);

        return String.format("%.1f km | %dm ↗", distance, elevation);
    }

    private void loadMockRoutes() {
        routeList.clear();

        // Add some sample routes with different types and images
        routeList.add(new Route("Mountain Peak Trail", "Hiking",
                HIKING_IMAGES[0],
                "15.2 km | 1,240m ↗"));

        routeList.add(new Route("Riverside Bike Path", "Cycling",
                CYCLING_IMAGES[0],
                "32.8 km | 420m ↗"));

        routeList.add(new Route("Forest Run Circuit", "Running",
                RUNNING_IMAGES[0],
                "8.5 km | 180m ↗"));

        routeList.add(new Route("Lakeside Hike", "Hiking",
                HIKING_IMAGES[1],
                "6.3 km | 120m ↗"));

        routeList.add(new Route("Hill Climb Challenge", "Cycling",
                CYCLING_IMAGES[1],
                "18.7 km | 850m ↗"));

        routeAdapter.notifyDataSetChanged();
        Toast.makeText(requireContext(), "Showing sample trails", Toast.LENGTH_SHORT).show();
    }

    // Weather API Service Interface
    public interface WeatherApiService {
        @retrofit2.http.GET("weather")
        Call<WeatherResponse> getCurrentWeather(
                @retrofit2.http.Query("lat") double lat,
                @retrofit2.http.Query("lon") double lon,
                @retrofit2.http.Query("units") String units,
                @retrofit2.http.Query("appid") String appId
        );
    }

    // Weather Response Model Class
    public static class WeatherResponse {
        private Main main;
        private Weather[] weather;
        private String name;

        public Main getMain() {
            return main;
        }

        public Weather[] getWeather() {
            return weather;
        }

        public String getName() {
            return name;
        }

        public static class Main {
            private double temp;

            public double getTemp() {
                return temp;
            }
        }

        public static class Weather {
            private String icon;

            public String getIcon() {
                return icon;
            }
        }
    }
}