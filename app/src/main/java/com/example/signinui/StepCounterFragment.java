package com.example.signinui;

import androidx.fragment.app.Fragment;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

public class StepCounterFragment extends Fragment implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor stepCounterSensor;
    private Sensor accelerometer;
    private int stepCount = 0;
    private int initialStepCount = -1;
    private boolean isTracking = false;
    private boolean useStepCounter = false;

    // Improved accelerometer-based detection
    private float accelCurrent;
    private float accelLast;
    private float accelValue;
    private long lastStepTime = 0;
    private static final int STEP_THRESHOLD = 12; // Reduced threshold
    private static final int MIN_STEP_DELAY = 250; // Minimum 250ms between steps
    private static final float NOISE_THRESHOLD = 0.5f;

    // Moving average filter
    private static final int FILTER_SIZE = 4;
    private float[] accelBuffer = new float[FILTER_SIZE];
    private int bufferIndex = 0;

    private TextView stepsView, xView, yView, zView, sensorTypeView;
    private Button startButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_step_counter, container, false);

        stepsView = view.findViewById(R.id.stepText);
        startButton = view.findViewById(R.id.startButton);
        xView = view.findViewById(R.id.xValue);
        yView = view.findViewById(R.id.yValue);
        zView = view.findViewById(R.id.zValue);
        // Add this TextView to your layout to show which sensor is being used
        sensorTypeView = view.findViewById(R.id.sensorType);

        sensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);

        // Try to use dedicated step counter first
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (stepCounterSensor != null) {
            useStepCounter = true;
            sensorTypeView.setText("Using: Hardware Step Counter");
        } else {
            useStepCounter = false;
            sensorTypeView.setText("Using: Accelerometer (Manual Detection)");
        }

        // Initialize accelerometer values
        accelCurrent = SensorManager.GRAVITY_EARTH;
        accelLast = SensorManager.GRAVITY_EARTH;
        accelValue = 0;

        startButton.setOnClickListener(v -> {
            if (!isTracking) {
                startTracking();
            } else {
                stopTracking();
            }
        });

        return view;
    }

    private void startTracking() {
        stepCount = 0;
        initialStepCount = -1;
        isTracking = true;
        lastStepTime = 0;

        // Reset buffer
        for (int i = 0; i < FILTER_SIZE; i++) {
            accelBuffer[i] = SensorManager.GRAVITY_EARTH;
        }
        bufferIndex = 0;

        stepsView.setText("Steps: 0");

        if (useStepCounter && stepCounterSensor != null) {
            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI);
        } else if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        }

        startButton.setText("Stop Tracking");
    }

    private void stopTracking() {
        isTracking = false;
        sensorManager.unregisterListener(this);
        startButton.setText("Start Tracking");
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isTracking) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isTracking) {
            if (useStepCounter && stepCounterSensor != null) {
                sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI);
            } else if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isTracking) return;

        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            // Hardware step counter (most accurate)
            if (initialStepCount == -1) {
                initialStepCount = (int) event.values[0];
            }
            stepCount = (int) event.values[0] - initialStepCount;
            stepsView.setText("Steps: " + stepCount);

        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Manual accelerometer-based detection
            processAccelerometerData(event);
        }
    }

    private void processAccelerometerData(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        // Show raw values
        xView.setText(String.format("X: %.2f", x));
        yView.setText(String.format("Y: %.2f", y));
        zView.setText(String.format("Z: %.2f", z));

        // Calculate magnitude
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);

        // Apply moving average filter
        accelBuffer[bufferIndex] = magnitude;
        bufferIndex = (bufferIndex + 1) % FILTER_SIZE;

        float filteredMagnitude = 0;
        for (float value : accelBuffer) {
            filteredMagnitude += value;
        }
        filteredMagnitude /= FILTER_SIZE;

        // Calculate acceleration difference
        accelLast = accelCurrent;
        accelCurrent = filteredMagnitude;
        float delta = Math.abs(accelCurrent - accelLast);

        // Apply low-pass filter to reduce noise
        accelValue = accelValue * 0.8f + delta * 0.2f;

        // Step detection with time-based filtering
        long currentTime = System.currentTimeMillis();

        if (accelValue > STEP_THRESHOLD &&
                (currentTime - lastStepTime) > MIN_STEP_DELAY) {

            // Additional validation: check if the change is significant enough
            if (delta > NOISE_THRESHOLD) {
                stepCount++;
                stepsView.setText("Steps: " + stepCount);
                lastStepTime = currentTime;

                // Reset the accumulator to prevent double counting
                accelValue = 0;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // You might want to handle accuracy changes
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            // Handle unreliable sensor data
        }
    }
}