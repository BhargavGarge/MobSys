package com.example.signinui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

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
    // Replace with your OpenWeatherMap API key
    private static final String API_KEY = "9bd3c0f117e2da1a013b82af5e348ba8";
    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5/";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // Initialize views
        weatherIcon = view.findViewById(R.id.weather_icon);
        temperatureText = view.findViewById(R.id.temperature_text);
        userNameHero = view.findViewById(R.id.user_name_hero);

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
            }
        }
    }

    private void getLocationAndWeather() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
                        } else {
                            Toast.makeText(requireContext(), "Unable to get location", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void getWeatherData(double lat, double lon) {
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
                    Toast.makeText(requireContext(), "Failed to get weather data", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<WeatherResponse> call, Throwable t) {
                Toast.makeText(requireContext(), "Weather API call failed", Toast.LENGTH_SHORT).show();
            }
        });
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

        // Update location name if needed
        if (weatherResponse.getName() != null) {
            // You can use this to show the location name somewhere if you want
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
}