package com.example.signinui;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";

    // UI Components
    private TextView userNameHero;
    private TextView temperatureText;
    private ImageView weatherIcon;
    private TextView friendCountText;
    private TextView streakText; // Added for streak display
    private RecyclerView recyclerView;
    private FeaturedRouteAdapter routeAdapter;
    private List<Route> routeList = new ArrayList<>();

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private FusedLocationProviderClient fusedLocationClient;

    // Bluetooth
    private String partnerUid = null;
    private BluetoothService bluetoothService;
    private boolean isServiceBound = false;
    private boolean startDiscoveryAfterBind = false;
    private List<BluetoothDevice> nearbyDevices = new ArrayList<>();
    private Map<String, Boolean> onlineStatusMap = new HashMap<>();
    private AlertDialog discoveryDialog;
    private BluetoothDeviceAdapter devicesAdapter;
    private RecyclerView devicesRecyclerView;
    private TextView searchingText;
    private ProgressBar progressBar;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<Intent> enableBluetoothLauncher;
    private Handler heartbeatHandler = new Handler();
    private Runnable heartbeatRunnable;

    // Streak System
    private SharedPreferences streakPrefs;
    private static final String STREAK_PREFS = "streak_preferences";
    private static final String KEY_CURRENT_STREAK = "current_streak";
    private static final String KEY_LAST_VISIT_DATE = "last_visit_date";
    private static final String KEY_LONGEST_STREAK = "longest_streak";
    private static final String KEY_TOTAL_DAYS_ACTIVE = "total_days_active";

    // API Constants
    private static final String API_KEY = "9bd3c0f117e2da1a013b82af5e348ba8";
    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5/";
    private static final String TRAIL_API_URL = "https://opentrails.wsp.com/api/trails";
    private static final String USFS_API_URL = "https://apps.fs.usda.gov/arcx/rest/services/EDW/EDW_RecreationOpportunities_01/MapServer/0/query?where=1%3D1&outFields=*&f=json";

    // Image Arrays
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
    private boolean isDiscoveryInProgress = false;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private Map<String, String> deviceNameMap = new HashMap<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeLaunchers();
        initializeStreakSystem();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        initializeFirebase();
        initializeViews(view);
        loadUserData();
        updateStreakDisplay(); // Update streak when view is created
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        checkLocationPermissionAndFetchData();
        view.findViewById(R.id.find_friends_button).setOnClickListener(v -> checkPermissionsAndStartDiscovery());
        bindBluetoothService();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Check and update streak every time the fragment resumes
        checkAndUpdateDailyStreak();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection);
            isServiceBound = false;
        }
        if (heartbeatHandler != null && heartbeatRunnable != null) {
            heartbeatHandler.removeCallbacks(heartbeatRunnable);
        }
    }

    // =============================================================================================
    // Streak System Implementation
    // =============================================================================================

    private void initializeStreakSystem() {
        streakPrefs = requireContext().getSharedPreferences(STREAK_PREFS, Context.MODE_PRIVATE);
        Log.d(TAG, "Streak system initialized");
    }

    private void checkAndUpdateDailyStreak() {
        String today = getTodayDateString();
        String lastVisitDate = streakPrefs.getString(KEY_LAST_VISIT_DATE, "");
        int currentStreak = streakPrefs.getInt(KEY_CURRENT_STREAK, 0);

        Log.d(TAG, "Checking streak - Today: " + today + ", Last visit: " + lastVisitDate + ", Current streak: " + currentStreak);

        if (lastVisitDate.isEmpty()) {
            // First time opening the app
            startNewStreak(today);
            showStreakMessage("Welcome! Your adventure streak begins today!", false);
        } else if (lastVisitDate.equals(today)) {
            // Already visited today, no change needed
            Log.d(TAG, "Already visited today, streak remains: " + currentStreak);
        } else if (isConsecutiveDay(lastVisitDate, today)) {
            // Visited yesterday, continue streak
            continueStreak(today, currentStreak);
            showStreakMessage("Streak continued! " + (currentStreak + 1) + " days strong!", false);
        } else {
            // Missed a day, reset streak
            resetStreak(today);
            showStreakMessage("Streak reset. Let's start fresh!", true);
        }

        updateStreakDisplay();
    }

    private void startNewStreak(String today) {
        SharedPreferences.Editor editor = streakPrefs.edit();
        editor.putInt(KEY_CURRENT_STREAK, 1);
        editor.putString(KEY_LAST_VISIT_DATE, today);
        editor.putInt(KEY_TOTAL_DAYS_ACTIVE, streakPrefs.getInt(KEY_TOTAL_DAYS_ACTIVE, 0) + 1);
        editor.apply();

        Log.d(TAG, "Started new streak");
    }

    private void continueStreak(String today, int currentStreak) {
        int newStreak = currentStreak + 1;
        int longestStreak = streakPrefs.getInt(KEY_LONGEST_STREAK, 0);

        SharedPreferences.Editor editor = streakPrefs.edit();
        editor.putInt(KEY_CURRENT_STREAK, newStreak);
        editor.putString(KEY_LAST_VISIT_DATE, today);
        editor.putInt(KEY_TOTAL_DAYS_ACTIVE, streakPrefs.getInt(KEY_TOTAL_DAYS_ACTIVE, 0) + 1);

        // Update longest streak if current streak is higher
        if (newStreak > longestStreak) {
            editor.putInt(KEY_LONGEST_STREAK, newStreak);
            Log.d(TAG, "New longest streak record: " + newStreak);
        }

        editor.apply();

        Log.d(TAG, "Continued streak to: " + newStreak);
    }

    private void resetStreak(String today) {
        SharedPreferences.Editor editor = streakPrefs.edit();
        editor.putInt(KEY_CURRENT_STREAK, 1);
        editor.putString(KEY_LAST_VISIT_DATE, today);
        editor.putInt(KEY_TOTAL_DAYS_ACTIVE, streakPrefs.getInt(KEY_TOTAL_DAYS_ACTIVE, 0) + 1);
        editor.apply();

        Log.d(TAG, "Reset streak to 1");
    }

    private void updateStreakDisplay() {
        if (streakText != null) {
            int currentStreak = streakPrefs.getInt(KEY_CURRENT_STREAK, 0);
            streakText.setText(String.valueOf(currentStreak));
            Log.d(TAG, "Updated streak display: " + currentStreak);
        }
    }

    private void showStreakMessage(String message, boolean isReset) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();

            // You can add more elaborate streak notifications here
            // For example, special messages for milestone streaks
            int currentStreak = streakPrefs.getInt(KEY_CURRENT_STREAK, 0);
            if (!isReset && (currentStreak == 7 || currentStreak == 30 || currentStreak == 100)) {
                Toast.makeText(getContext(), "🎉 " + currentStreak + " day milestone! Amazing dedication!", Toast.LENGTH_LONG).show();
            }
        }
    }

    private String getTodayDateString() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return dateFormat.format(new Date());
    }

    private boolean isConsecutiveDay(String lastVisitDate, String today) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date lastDate = dateFormat.parse(lastVisitDate);
            Date todayDate = dateFormat.parse(today);

            if (lastDate != null && todayDate != null) {
                long diffInMillis = todayDate.getTime() - lastDate.getTime();
                long diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis);
                return diffInDays == 1; // Exactly one day difference
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing dates", e);
        }
        return false;
    }

    // Public method to get streak statistics (optional, for debugging or display)
    public Map<String, Integer> getStreakStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("current_streak", streakPrefs.getInt(KEY_CURRENT_STREAK, 0));
        stats.put("longest_streak", streakPrefs.getInt(KEY_LONGEST_STREAK, 0));
        stats.put("total_days_active", streakPrefs.getInt(KEY_TOTAL_DAYS_ACTIVE, 0));
        return stats;
    }

    // =============================================================================================
    // Existing Methods (unchanged from original)
    // =============================================================================================

    private void bindBluetoothService() {
        Intent intent = new Intent(requireContext(), BluetoothService.class);
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.LocalBinder binder = (BluetoothService.LocalBinder) service;
            bluetoothService = binder.getService();
            isServiceBound = true;
            Log.d(TAG, "BluetoothService connected.");
            bluetoothService.startServer();
            if (startDiscoveryAfterBind) {
                startDiscoveryAfterBind = false;
                checkPermissionsAndStartDiscovery();
            }
            startHeartbeat();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            bluetoothService = null;
            Log.d(TAG, "BluetoothService disconnected.");
        }
    };

    private void checkPermissionsAndStartDiscovery() {
        if (isDiscoveryInProgress) {
            Toast.makeText(requireContext(), "Discovery already in progress", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> requiredPermissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN);
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            requiredPermissions.add(Manifest.permission.BLUETOOTH);
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        }
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);

        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (permissionsToRequest.isEmpty()) {
            startBluetoothDiscoveryFlow();
        } else {
            permissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
        }
    }

    private void startBluetoothDiscoveryFlow() {
        if (!isServiceBound) {
            startDiscoveryAfterBind = true;
            Toast.makeText(requireContext(), "Initializing Bluetooth service...", Toast.LENGTH_SHORT).show();
            return;
        }

        if (bluetoothService == null || !bluetoothService.isBluetoothSupported()) {
            Toast.makeText(requireContext(), "Bluetooth not supported.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothService.isBluetoothEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBluetoothLauncher.launch(enableBtIntent);
        } else {
            startDiscoveryWithService();
        }
    }

    private void startDiscoveryWithService() {
        if (!isServiceBound || bluetoothService == null) {
            Toast.makeText(requireContext(), "Bluetooth service not ready.", Toast.LENGTH_SHORT).show();
            return;
        }

        nearbyDevices.clear();
        onlineStatusMap.clear();
        showDiscoveryDialog();

        bluetoothService.startDiscovery(new BluetoothService.BluetoothDiscoveryListener() {
            @Override
            public void onDeviceDiscovered(BluetoothDevice device) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    if (device.getName() == null || device.getName().isEmpty()) {
                        return;
                    }

                    String deviceName = device.getName();
                    deviceNameMap.put(device.getAddress(), deviceName);

                    boolean deviceExists = false;
                    for (BluetoothDevice existingDevice : nearbyDevices) {
                        if (existingDevice.getAddress().equals(device.getAddress())) {
                            deviceExists = true;
                            break;
                        }
                    }

                    if (!deviceExists) {
                        nearbyDevices.add(device);
                        onlineStatusMap.put(device.getAddress(), true);
                        updateDiscoveryDialog();
                    }
                });
            }

            @Override
            public void onDiscoveryFinished() {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (searchingText != null && nearbyDevices.isEmpty()) {
                        searchingText.setText("No adventurers found nearby.");
                    }
                });
            }

            @Override
            public void onDeviceConnected(BluetoothDevice device) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Connected! Exchanging info...", Toast.LENGTH_SHORT).show();
                    FirebaseUser currentUser = mAuth.getCurrentUser();
                    if (currentUser != null && bluetoothService != null) {
                        bluetoothService.sendUserUID(currentUser.getUid());
                    }
                });
            }

            @Override
            public void onConnectionError(String message) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onPartnerUIDReceived(String uid) {
                HomeFragment.this.partnerUid = uid;
                FirebaseUser currentUser = mAuth.getCurrentUser();
                if (currentUser != null && HomeFragment.this.partnerUid != null) {
                    Intent intent = new Intent(requireContext(), ChatActivity.class);
                    intent.putExtra("CURRENT_USER_UID", currentUser.getUid());
                    intent.putExtra("PARTNER_UID", HomeFragment.this.partnerUid);
                    startActivity(intent);
                }
            }

            @Override
            public void onFriendRequestReceived(String userId, String userName) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> showFriendRequestDialog(userId, userName));
            }

            @Override
            public void onFriendPaired(String userId, boolean success) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(requireContext(), "Friend paired successfully!", Toast.LENGTH_SHORT).show();
                        updateFriendCount();
                    } else {
                        Toast.makeText(requireContext(), "Failed to pair with friend", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showDiscoveryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog);
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_bluetooth_discovery, null);
        builder.setView(dialogView);

        searchingText = dialogView.findViewById(R.id.searching_text);
        progressBar = dialogView.findViewById(R.id.progress_bar);
        devicesRecyclerView = dialogView.findViewById(R.id.devices_recycler);

        devicesAdapter = BluetoothDeviceAdapter.createWithEmptyList(device -> {
            if (isServiceBound && bluetoothService != null) {
                bluetoothService.connectToDevice(device);
                if (discoveryDialog != null && discoveryDialog.isShowing()) {
                    discoveryDialog.dismiss();
                }
            }
        });

        devicesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        devicesRecyclerView.setAdapter(devicesAdapter);

        discoveryDialog = builder.create();
        discoveryDialog.setCanceledOnTouchOutside(false);
        discoveryDialog.show();

        updateDiscoveryDialog();

        Window window = discoveryDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        dialogView.findViewById(R.id.cancel_button).setOnClickListener(v -> {
            if (isServiceBound && bluetoothService != null) {
                bluetoothService.stopDiscovery();
            }
            if (discoveryDialog != null && discoveryDialog.isShowing()) {
                discoveryDialog.dismiss();
            }
        });
    }

    private void updateDiscoveryDialog() {
        if (discoveryDialog != null && discoveryDialog.isShowing()) {
            if (nearbyDevices.isEmpty()) {
                searchingText.setText("Searching for nearby adventurers...");
                devicesRecyclerView.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);
            } else {
                searchingText.setText(nearbyDevices.size() + " adventurer(s) nearby");
                devicesRecyclerView.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                devicesAdapter.updateDevices(nearbyDevices, onlineStatusMap);
            }
        }
    }

    private void showFriendRequestDialog(String userId, String userName) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Friend Request")
                .setMessage(userName + " wants to be your friend.")
                .setPositiveButton("Accept", (dialog, which) -> acceptFriendRequest(userId))
                .setNegativeButton("Decline", null)
                .show();
    }

    private void updateFriendCount() {
        if (bluetoothService != null) {
            int count = bluetoothService.getNearbyFriendCount();
            friendCountText.setText(count + " friends nearby");
        }
    }

    private void acceptFriendRequest(String userId) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String currentUid = currentUser.getUid();
            mDatabase.child("friends").child(currentUid).child(userId).setValue(true);
            mDatabase.child("friends").child(userId).child(currentUid).setValue(true);

            if (bluetoothService != null) {
                bluetoothService.sendFriendAccept(userId);
            }

            Toast.makeText(requireContext(), "Friend added!", Toast.LENGTH_SHORT).show();
            updateFriendCount();
        }
    }

    private void startHeartbeat() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (bluetoothService != null) {
                    bluetoothService.sendHeartbeat(currentUser.getUid());
                }
                heartbeatHandler.postDelayed(this, 30000);
            }
        };
        heartbeatHandler.postDelayed(heartbeatRunnable, 30000);
    }

    private void initializeViews(View view) {
        weatherIcon = view.findViewById(R.id.weather_icon);
        temperatureText = view.findViewById(R.id.temperature_text);
        userNameHero = view.findViewById(R.id.user_name_hero);
        friendCountText = view.findViewById(R.id.friend_count_text);
        streakText = view.findViewById(R.id.stat_streak); // Make sure this ID matches your XML
        recyclerView = view.findViewById(R.id.featured_routes_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        routeAdapter = new FeaturedRouteAdapter(routeList, requireContext());
        recyclerView.setAdapter(routeAdapter);
    }

    private void initializeFirebase() {
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
    }

    private void initializeLaunchers() {
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean allGranted = true;
                    for(Boolean granted : permissions.values()){
                        if(!granted){
                            allGranted = false;
                            break;
                        }
                    }

                    if (allGranted) {
                        startBluetoothDiscoveryFlow();
                    } else {
                        Toast.makeText(requireContext(), "Bluetooth & Location permissions are required.", Toast.LENGTH_LONG).show();
                    }
                }
        );

        enableBluetoothLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == getActivity().RESULT_OK) {
                        startDiscoveryWithService();
                    } else {
                        Toast.makeText(requireContext(), "Bluetooth must be enabled to find friends.", Toast.LENGTH_SHORT).show();
                    }
                }
        );
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

    private void checkLocationPermissionAndFetchData() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        } else {
            getLocationAndWeather();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLocationAndWeather();
            } else {
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show();
                loadMockRoutes();
            }
        }
    }

    private void getLocationAndWeather() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            loadMockRoutes();
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(requireActivity(), location -> {
                    if (location != null) {
                        getWeatherData(location.getLatitude(), location.getLongitude());
                        fetchTrailData(location.getLatitude(), location.getLongitude());
                    } else {
                        Toast.makeText(requireContext(), "Unable to get location", Toast.LENGTH_SHORT).show();
                        loadMockRoutes();
                    }
                });
    }

    private void getWeatherData(double lat, double lon) {
        if (API_KEY.equals("YOUR_OPENWEATHER_API_KEY")) {
            setDefaultWeather();
            Toast.makeText(requireContext(), "Please set your OpenWeather API key", Toast.LENGTH_LONG).show();
            return;
        }

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
                    updateWeatherUI(response.body());
                } else {
                    setDefaultWeather();
                    Log.e(TAG, "Weather API failed: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<WeatherResponse> call, Throwable t) {
                setDefaultWeather();
                Log.e(TAG, "Weather API call failed", t);
            }
        });
    }

    private void setDefaultWeather() {
        temperatureText.setText("22°C");
        weatherIcon.setImageResource(R.drawable.ic_sunny);
    }

    private void updateWeatherUI(WeatherResponse weatherResponse) {
        int temperature = (int) Math.round(weatherResponse.getMain().getTemp());
        temperatureText.setText(temperature + "°C");

        if (weatherResponse.getWeather().length > 0) {
            setWeatherIcon(weatherResponse.getWeather()[0].getIcon());
        }
    }

    private void setWeatherIcon(String iconCode) {
        int iconResource;
        switch (iconCode) {
            case "01d": iconResource = R.drawable.ic_sunny; break;
            case "01n": iconResource = R.drawable.ic_clear_night; break;
            case "02d": case "02n": iconResource = R.drawable.ic_partly_cloudy; break;
            case "03d": case "03n": case "04d": case "04n": iconResource = R.drawable.ic_cloudy; break;
            case "09d": case "09n": case "10d": case "10n": iconResource = R.drawable.ic_rain; break;
            case "11d": case "11n": iconResource = R.drawable.ic_thunderstorm; break;
            case "13d": case "13n": iconResource = R.drawable.ic_snow; break;
            case "50d": case "50n": iconResource = R.drawable.ic_mist; break;
            default: iconResource = R.drawable.ic_sunny;
        }
        weatherIcon.setImageResource(iconResource);
    }

    private void fetchTrailData(double lat, double lon) {
        tryOpenTrailsAPI();
    }

    private void tryOpenTrailsAPI() {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, TRAIL_API_URL, null,
                response -> {
                    try {
                        if (!parseOpenTrailsResponse(response)) {
                            tryUSForestServiceAPI();
                        }
                    } catch (Exception e) {
                        tryUSForestServiceAPI();
                    }
                },
                error -> tryUSForestServiceAPI()
        );
        Volley.newRequestQueue(requireContext()).add(request);
    }

    private void tryUSForestServiceAPI() {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, USFS_API_URL, null,
                response -> {
                    try {
                        if (!parseUSFSResponse(response)) {
                            loadMockRoutes();
                        }
                    } catch (Exception e) {
                        loadMockRoutes();
                    }
                },
                error -> loadMockRoutes()
        );
        Volley.newRequestQueue(requireContext()).add(request);
    }

    private boolean parseOpenTrailsResponse(JSONObject response) {
        try {
            routeList.clear();
            if (response.has("trails")) {
                JSONArray trails = response.getJSONArray("trails");
                for (int i = 0; i < Math.min(trails.length(), 5); i++) {
                    JSONObject trail = trails.getJSONObject(i);
                    String name = trail.optString("name", "Scenic Trail");
                    String type = determineRouteType(trail);
                    String imageUrl = getRandomImageForType(type);
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

    private boolean parseUSFSResponse(JSONObject response) {
        try {
            routeList.clear();
            if (response.has("features")) {
                JSONArray features = response.getJSONArray("features");
                for (int i = 0; i < Math.min(features.length(), 5); i++) {
                    JSONObject feature = features.getJSONObject(i);
                    JSONObject attributes = feature.optJSONObject("attributes");
                    if (attributes != null) {
                        String name = attributes.optString("RECAREANAME", "Forest Trail");
                        String type = determineRouteTypeFromName(name);
                        String imageUrl = getRandomImageForType(type);
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

    private String determineRouteType(JSONObject trail) {
        String activityType = trail.optString("activity_type", "").toLowerCase();
        String name = trail.optString("name", "").toLowerCase();
        if (activityType.contains("cycle") || name.contains("bike")) return "Cycling";
        if (activityType.contains("run") || name.contains("run")) return "Running";
        return "Hiking";
    }

    private String determineRouteTypeFromName(String name) {
        String lowerName = name.toLowerCase();
        if (lowerName.contains("bike") || lowerName.contains("cycle")) return "Cycling";
        if (lowerName.contains("run") || lowerName.contains("jog")) return "Running";
        return "Hiking";
    }

    private String getRandomImageForType(String type) {
        switch (type) {
            case "Cycling": return CYCLING_IMAGES[random.nextInt(CYCLING_IMAGES.length)];
            case "Running": return RUNNING_IMAGES[random.nextInt(RUNNING_IMAGES.length)];
            default: return HIKING_IMAGES[random.nextInt(HIKING_IMAGES.length)];
        }
    }

    private String getRandomTrailDetails() {
        double distance = 3.0 + random.nextDouble() * 20.0;
        int elevation = 100 + random.nextInt(1500);
        return String.format("%.1f km | %dm ↗", distance, elevation);
    }

    private void loadMockRoutes() {
        routeList.clear();
        routeList.add(new Route("Mountain Peak Trail", "Hiking", HIKING_IMAGES[0], "15.2 km | 1,240m ↗"));
        routeList.add(new Route("Riverside Bike Path", "Cycling", CYCLING_IMAGES[0], "32.8 km | 420m ↗"));
        routeList.add(new Route("Forest Run Circuit", "Running", RUNNING_IMAGES[0], "8.5 km | 180m ↗"));
        routeList.add(new Route("Lakeside Hike", "Hiking", HIKING_IMAGES[1], "6.3 km | 120m ↗"));
        routeList.add(new Route("Hill Climb Challenge", "Cycling", CYCLING_IMAGES[1], "18.7 km | 850m ↗"));
        routeAdapter.notifyDataSetChanged();
        Toast.makeText(requireContext(), "Showing sample trails", Toast.LENGTH_SHORT).show();
    }

    public interface WeatherApiService {
        @retrofit2.http.GET("weather")
        Call<WeatherResponse> getCurrentWeather(
                @retrofit2.http.Query("lat") double lat,
                @retrofit2.http.Query("lon") double lon,
                @retrofit2.http.Query("units") String units,
                @retrofit2.http.Query("appid") String appId
        );
    }

    public static class WeatherResponse {
        private Main main;
        private Weather[] weather;
        public Main getMain() { return main; }
        public Weather[] getWeather() { return weather; }
        public static class Main {
            private double temp;
            public double getTemp() { return temp; }
        }
        public static class Weather {
            private String icon;
            public String getIcon() { return icon; }
        }
    }
}