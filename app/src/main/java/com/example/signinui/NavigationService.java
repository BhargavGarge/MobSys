package com.example.signinui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.osmdroid.util.GeoPoint;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NavigationService extends Service implements TextToSpeech.OnInitListener {

    private static final String TAG = "NavigationService";
    public static final String ACTION_STOP_NAVIGATION = "com.example.signinui.ACTION_STOP_NAVIGATION";
    private static final String CHANNEL_ID = "NavigationServiceChannel";
    private static final int NOTIFICATION_ID = 12345;

    // Overlay UI
    private WindowManager windowManager;
    private View floatingView;
    private TextView overlayInstruction;
    private TextView overlayDistance;

    // Location & Navigation
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private TextToSpeech textToSpeech;
    private List<NavigationStep> navigationSteps;
    private int currentStepIndex = 0;
    private GeoPoint lastKnownLocation;

    // Data class for navigation instructions
    public static class NavigationStep {
        GeoPoint point;
        String instruction;

        NavigationStep(GeoPoint point, String instruction) {
            this.point = point;
            this.instruction = instruction;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        textToSpeech = new TextToSpeech(this, this);
        initializeOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_NAVIGATION.equals(intent.getAction())) {
            stopSelf(); // Stop the service if the stop action is received
            return START_NOT_STICKY;
        }

        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Trail Navigation Active")
                .setContentText("Tracking your route.")
                .setSmallIcon(R.drawable.ic_navigation)
                .build();
        startForeground(NOTIFICATION_ID, notification);

        // Retrieve route data from the intent
        String stepsJson = intent.getStringExtra("NAVIGATION_STEPS_JSON");
        if (stepsJson != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<NavigationStep>>() {}.getType();
            navigationSteps = gson.fromJson(stepsJson, type);
            if (navigationSteps != null && !navigationSteps.isEmpty()) {
                startLocationUpdates();
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        if (windowManager != null && floatingView != null) {
            windowManager.removeView(floatingView);
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }

    private void initializeOverlay() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_navigation_overlay, null);
        overlayInstruction = floatingView.findViewById(R.id.overlay_instruction);
        overlayDistance = floatingView.findViewById(R.id.overlay_distance);

        int layout_params_type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layout_params_type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layout_params_type = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layout_params_type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (windowManager != null) {
            windowManager.addView(floatingView, params);
        }
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    lastKnownLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
                    updateNavigation();
                }
            }
        };

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission not granted, cannot start updates.", e);
        }
    }

    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    private void updateNavigation() {
        if (navigationSteps == null || navigationSteps.isEmpty() || currentStepIndex >= navigationSteps.size()) {
            overlayInstruction.setText("Route complete!");
            overlayDistance.setText("");
            stopSelf(); // Stop the service when navigation is done
            return;
        }

        NavigationStep currentStep = navigationSteps.get(currentStepIndex);
        float[] results = new float[1];
        Location.distanceBetween(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude(),
                currentStep.point.getLatitude(), currentStep.point.getLongitude(), results);
        double distanceToStep = results[0];

        // Update overlay UI
        overlayInstruction.setText(currentStep.instruction);
        overlayDistance.setText(String.format(Locale.US, "%.0f meters", distanceToStep));

        // Check if user is close enough to the next point to advance
        if (distanceToStep < 25 && currentStepIndex < navigationSteps.size() - 1) {
            currentStepIndex++;
            NavigationStep nextStep = navigationSteps.get(currentStepIndex);
            speak(nextStep.instruction);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.getDefault());
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "TTS Language not supported");
            } else {
                speak("Navigation started.");
            }
        } else {
            Log.e(TAG, "TTS initialization failed");
        }
    }

    private void speak(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Navigation Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}