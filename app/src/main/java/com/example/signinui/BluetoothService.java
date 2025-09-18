package com.example.signinui;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BluetoothService extends Service {
    // =============================================================================================
    // Constants & Member Variables
    // =============================================================================================
    private static final String TAG = "BluetoothService";
    private static final String APP_NAME = "AdventureApp";
    // Using well-known SPP UUID for better compatibility
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final IBinder binder = new LocalBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDiscoveryListener discoveryListener;
    private BroadcastReceiver discoveryReceiver;

    // Threads for managing Bluetooth connections
    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;

    // Data collections for friend management
    private Set<String> pairedFriends = new HashSet<>();
    private Map<String, Boolean> friendOnlineStatus = new HashMap<>();

    // =============================================================================================
    // Service Lifecycle & Binding
    // =============================================================================================

    @Override
    public void onCreate() {
        super.onCreate();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        initializeDiscoveryReceiver();
        Log.d(TAG, "BluetoothService created");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopServer(); // Clean up everything when the service is destroyed
        Log.d(TAG, "BluetoothService destroyed");
    }

    public class LocalBinder extends Binder {
        BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    public interface BluetoothDiscoveryListener {
        void onDeviceDiscovered(BluetoothDevice device);
        void onDiscoveryFinished();
        void onDeviceConnected(BluetoothDevice device);
        void onConnectionError(String message);
        void onPartnerUIDReceived(String uid);
        void onFriendRequestReceived(String userId, String userName);
        void onFriendPaired(String userId, boolean success);
    }

    // =============================================================================================
    // Public API for Activities
    // =============================================================================================

    public boolean isBluetoothSupported() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    /**
     * Starts the server to listen for incoming connections.
     * This makes the device ready to accept friend requests.
     */
    public synchronized void startServer() {
        Log.d(TAG, "Starting Bluetooth server...");

        // Cancel any thread attempting to make a connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        // Start the thread to listen on a BluetoothServerSocket
        if (acceptThread == null) {
            acceptThread = new AcceptThread();
            acceptThread.start();
        }
        Log.d(TAG, "Bluetooth server started");
    }

    /**
     * Stops all Bluetooth-related threads and operations.
     */
    public synchronized void stopServer() {
        Log.d(TAG, "Stopping Bluetooth server...");
        stopDiscovery();
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        Log.d(TAG, "Bluetooth server stopped");
    }

    /**
     * Starts the device discovery process.
     * @param listener The callback interface to notify of discovery events.
     */
    public void startDiscovery(BluetoothDiscoveryListener listener) {
        this.discoveryListener = listener;

        if (!isBluetoothSupported()) {
            notifyError("Bluetooth not supported");
            return;
        }
        if (!isBluetoothEnabled()) {
            notifyError("Bluetooth not enabled");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            notifyError("BLUETOOTH_SCAN permission required");
            return;
        }

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(discoveryReceiver, filter);

        if (!bluetoothAdapter.startDiscovery()) {
            notifyError("Failed to start discovery");
        } else {
            Log.d(TAG, "Discovery started successfully");
        }
    }

    /**
     * Stops the device discovery process.
     */
    public void stopDiscovery() {
        if (bluetoothAdapter == null) return;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
            Log.d(TAG, "Discovery cancelled");
        }

        try {
            unregisterReceiver(discoveryReceiver);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Discovery receiver was not registered.", e);
        }
    }

    /**
     * Initiates a connection to a remote Bluetooth device.
     * @param device The device to connect to.
     */
    public synchronized void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            notifyError("BLUETOOTH_CONNECT permission required");
            return;
        }

        String deviceName = device.getName() != null ? device.getName() : "Unknown Device";
        Log.d(TAG, "Preparing to connect to device: " + deviceName + " (" + device.getAddress() + ")");

        // Check if device is bonded (paired)
        if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
            Log.w(TAG, "Device is not bonded. This might cause connection issues.");
        }

        // Cancel any thread attempting to make a connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // Add a small delay to ensure cleanup is complete
        handler.postDelayed(() -> {
            // Start the thread to connect with the given device
            connectThread = new ConnectThread(device);
            connectThread.start();
        }, 500);
    }

    /**
     * Manages a successful connection by starting the ConnectedThread.
     * @param socket The connected BluetoothSocket.
     * @param device The connected BluetoothDevice.
     */
    private synchronized void manageConnectedSocket(BluetoothSocket socket, BluetoothDevice device) {
        Log.d(TAG, "Managing connected socket for device: " + device.getAddress());

        // Cancel the thread that completed the connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        // Cancel the accept thread because we only want to connect to one device at a time
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        // Notify the UI that the connection was successful
        handler.post(() -> {
            if (discoveryListener != null) {
                discoveryListener.onDeviceConnected(device);
            }
        });

        // Start the thread to manage the connection and perform transmissions
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();
    }

    // =============================================================================================
    // Data Transmission Methods
    // =============================================================================================

    public void sendFriendRequest(String userId, String userName) {
        if (connectedThread != null) {
            String message = "FRIEND_REQUEST:" + userId + ":" + userName;
            connectedThread.write(message.getBytes());
        }
    }

    public void sendFriendAccept(String userId) {
        if (connectedThread != null) {
            String message = "FRIEND_ACCEPT:" + userId;
            connectedThread.write(message.getBytes());
        }
    }

    public void sendUserUID(String uid) {
        if (connectedThread != null) {
            String message = "UID_EXCHANGE:" + uid;
            connectedThread.write(message.getBytes());
        }
    }

    public void sendChatMessage(String chatMessage) {
        if (connectedThread != null) {
            String message = "CHAT:" + chatMessage;
            connectedThread.write(message.getBytes());
        }
    }

    public void sendHeartbeat(String userId) {
        if (connectedThread != null) {
            String message = "HEARTBEAT:" + userId;
            connectedThread.write(message.getBytes());
        }
    }

    // =============================================================================================
    // Friend Data Getters
    // =============================================================================================

    public Set<String> getPairedFriends() {
        return new HashSet<>(pairedFriends);
    }

    public Map<String, Boolean> getFriendOnlineStatus() {
        return new HashMap<>(friendOnlineStatus);
    }

    public int getNearbyFriendCount() {
        int count = 0;
        for (Boolean online : friendOnlineStatus.values()) {
            if (online != null && online) {
                count++;
            }
        }
        return count;
    }

    // =============================================================================================
    // Private Helper Methods & Receivers
    // =============================================================================================

    private void initializeDiscoveryReceiver() {
        discoveryReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null && discoveryListener != null) {
                        discoveryListener.onDeviceDiscovered(device);
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    Log.d(TAG, "Discovery finished");
                    if (discoveryListener != null) {
                        discoveryListener.onDiscoveryFinished();
                    }
                }
            }
        };
    }

    private void processMessage(String message) {
        Log.d(TAG, "Processing message: " + message);

        if (message.startsWith("FRIEND_REQUEST:")) {
            String[] parts = message.split(":", 3);
            if (parts.length == 3) {
                handler.post(() -> {
                    if (discoveryListener != null) {
                        discoveryListener.onFriendRequestReceived(parts[1], parts[2]);
                    }
                });
            }
        } else if (message.startsWith("FRIEND_ACCEPT:")) {
            String[] parts = message.split(":", 2);
            if (parts.length == 2) {
                String userId = parts[1];
                pairedFriends.add(userId);
                friendOnlineStatus.put(userId, true);
                handler.post(() -> {
                    if (discoveryListener != null) {
                        discoveryListener.onFriendPaired(userId, true);
                    }
                });
            }
        } else if (message.startsWith("HEARTBEAT:")) {
            String[] parts = message.split(":", 2);
            if (parts.length == 2) {
                String userId = parts[1];
                friendOnlineStatus.put(userId, true);
            }
        } else if (message.startsWith("UID_EXCHANGE:")) {
            String uid = message.substring(13);
            handler.post(() -> {
                if (discoveryListener != null) {
                    discoveryListener.onPartnerUIDReceived(uid);
                }
            });
        }
    }

    private void notifyError(String message) {
        Log.e(TAG, "Error: " + message);
        if (discoveryListener != null) {
            handler.post(() -> discoveryListener.onConnectionError(message));
        }
    }

    // =============================================================================================
    // Bluetooth Communication Threads (Inner Classes)
    // =============================================================================================

    /**
     * Enhanced AcceptThread with better error handling
     */
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                if (ActivityCompat.checkSelfPermission(BluetoothService.this, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "BLUETOOTH_CONNECT permission not granted for AcceptThread.");
                    tmp = null;
                } else {
                    // Try insecure connection for better compatibility
                    tmp = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, MY_UUID);
                    Log.d(TAG, "Server socket created successfully with UUID: " + MY_UUID);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket's listen() method failed", e);
            }
            serverSocket = tmp;
        }

        public void run() {
            if (serverSocket == null) {
                Log.e(TAG, "Server socket is null, cannot accept connections");
                return;
            }

            BluetoothSocket socket = null;
            Log.d(TAG, "Server socket listening for connections...");

            // Keep listening until an exception occurs or a socket is returned
            while (true) {
                try {
                    Log.d(TAG, "Waiting for incoming connection...");
                    socket = serverSocket.accept();
                    Log.d(TAG, "Incoming connection accepted from: " + socket.getRemoteDevice().getAddress());
                } catch (IOException e) {
                    Log.d(TAG, "Socket accept() failed or was cancelled.", e);
                    break;
                }

                if (socket != null) {
                    Log.d(TAG, "A connection was accepted from device: " + socket.getRemoteDevice().getAddress());
                    manageConnectedSocket(socket, socket.getRemoteDevice());

                    // Close the server socket as we are connecting to a single device
                    try {
                        serverSocket.close();
                        Log.d(TAG, "Server socket closed after accepting connection");
                    } catch (IOException e) {
                        Log.e(TAG, "Could not close the server socket", e);
                    }
                    break;
                }
            }
        }

        public void cancel() {
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                    Log.d(TAG, "Server socket cancelled");
                }
            } catch (IOException e) {
                Log.e(TAG, "Could not close the server socket", e);
            }
        }
    }

    /**
     * Enhanced ConnectThread with retry logic and fallback socket creation
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket socket;
        private final BluetoothDevice device;
        private static final int MAX_RETRIES = 3;
        private static final long RETRY_DELAY = 1000; // 1 second

        public ConnectThread(BluetoothDevice device) {
            this.device = device;
            BluetoothSocket tmp = null;

            try {
                if (ActivityCompat.checkSelfPermission(BluetoothService.this, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "BLUETOOTH_CONNECT permission not granted for ConnectThread.");
                } else {
                    // Try multiple connection methods in sequence

                    // 1. Try standard secure connection first
                    try {
                        tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                        Log.d(TAG, "Created secure socket for device: " + device.getAddress());
                    } catch (IOException e) {
                        Log.w(TAG, "Secure socket creation failed, trying insecure method", e);

                        // 2. Try insecure connection
                        try {
                            tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
                            Log.d(TAG, "Created insecure socket for device: " + device.getAddress());
                        } catch (IOException e2) {
                            Log.w(TAG, "Insecure socket creation also failed, trying reflection", e2);

                            // 3. Try reflection method with multiple channels
                            tmp = createSocketUsingReflection(device);
                            if (tmp == null) {
                                Log.e(TAG, "All socket creation methods failed");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error in socket creation", e);
            }
            socket = tmp;
        }

        private BluetoothSocket createSocketUsingReflection(BluetoothDevice device) {
            try {
                // Try multiple RFCOMM channels (1-30) as different devices use different channels
                for (int channel = 1; channel <= 30; channel++) {
                    try {
                        Method m = device.getClass().getMethod("createRfcommSocket", int.class);
                        BluetoothSocket socket = (BluetoothSocket) m.invoke(device, channel);
                        Log.d(TAG, "Created reflection socket on channel " + channel + " for device: " + device.getAddress());
                        return socket;
                    } catch (Exception e) {
                        Log.d(TAG, "Failed to create socket on channel " + channel, e);
                    }
                }
                Log.e(TAG, "All reflection socket creation attempts failed");
                return null;
            } catch (Exception e) {
                Log.e(TAG, "Reflection socket creation failed", e);
                return null;
            }
        }

        public void run() {
            if (socket == null) {
                Log.e(TAG, "Socket is null, cannot connect.");
                notifyError("Socket creation failed.");
                return;
            }

            // Add delay before connection attempt
            try {
                Thread.sleep(1000); // Wait 1 second
            } catch (InterruptedException e) {
                return;
            }

            // Stop discovery because it will slow down the connection
            stopDiscovery();

            int retryCount = 0;
            boolean connected = false;

            // Get device name safely with permission check
            String deviceIdentifier = device.getAddress(); // Default to address
            if (ActivityCompat.checkSelfPermission(BluetoothService.this,
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                if (device.getName() != null) {
                    deviceIdentifier = device.getName();
                }
            }

            while (retryCount < MAX_RETRIES && !connected) {
                try {
                    Log.d(TAG, "Connection attempt " + (retryCount + 1) + " to " + deviceIdentifier);

                    socket.connect();
                    connected = true;
                    Log.d(TAG, "Successfully connected on attempt " + (retryCount + 1));

                    // Connection successful, manage the socket
                    manageConnectedSocket(socket, device);

                } catch (IOException connectException) {
                    Log.w(TAG, "Connection attempt " + (retryCount + 1) + " failed", connectException);
                    retryCount++;

                    if (retryCount < MAX_RETRIES) {
                        try {
                            Thread.sleep(RETRY_DELAY);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
            }

            if (!connected) {
                Log.e(TAG, "Failed to connect after " + MAX_RETRIES + " attempts");
                try {
                    socket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                notifyError("Failed to connect to device after multiple attempts");
            }
        }

        public void cancel() {
            try {
                if (socket != null) socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    /**
     * Handles data transmission over an established connection.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error getting streams from socket", e);
            }
            inputStream = tmpIn;
            outputStream = tmpOut;
            Log.d(TAG, "ConnectedThread created for device: " + socket.getRemoteDevice().getAddress());
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            while (true) {
                try {
                    bytes = inputStream.read(buffer);
                    String message = new String(buffer, 0, bytes);
                    processMessage(message);
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    // Connection lost, restart the server to listen again
                    startServer();
                    break;
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
                Log.d(TAG, "Message sent: " + new String(bytes));
            } catch (IOException e) {
                Log.e(TAG, "Error writing to output stream", e);
            }
        }

        public void cancel() {
            try {
                if (socket != null) socket.close();
                Log.d(TAG, "ConnectedThread socket closed");
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }
}