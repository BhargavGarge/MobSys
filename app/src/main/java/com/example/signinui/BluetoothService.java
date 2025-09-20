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
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

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
    // Using well-known SPP UUID for better compatibility. Both client and server MUST use the same UUID.
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final IBinder binder = new LocalBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDiscoveryListener discoveryListener;
    private BroadcastReceiver discoveryReceiver;
    private BroadcastReceiver pairingReceiver;

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
        initializePairingReceiver();

        // Register pairing receiver
        IntentFilter pairingFilter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(pairingReceiver, pairingFilter);

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

        // Unregister pairing receiver
        try {
            unregisterReceiver(pairingReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver was not registered
        }

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
            // This is expected if the receiver was already unregistered.
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

        // Log detailed device information
        logDeviceInfo(device);

        String deviceName = device.getName() != null ? device.getName() : "Unknown Device";
        Log.d(TAG, "Preparing to connect to device: " + deviceName + " (" + device.getAddress() + ")");

        // Check if device is paired first
        if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
            Log.w(TAG, "Device is not paired. Current bond state: " + getBondStateString(device.getBondState()));

            // Show message to user about pairing
            handler.post(() -> {
                if (discoveryListener != null) {
                    Toast.makeText(BluetoothService.this,
                            "Device must be paired first. Please pair in Bluetooth settings and try again.",
                            Toast.LENGTH_LONG).show();
                }
            });

            // Optionally attempt to trigger pairing
            try {
                Log.d(TAG, "Attempting to create bond...");
                boolean result = device.createBond();
                Log.d(TAG, "createBond() result: " + result);
                if (!result) {
                    notifyError("Failed to initiate pairing. Please pair manually in Bluetooth settings.");
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception during createBond()", e);
                notifyError("Failed to pair with device. Please try pairing manually in Bluetooth settings.");
                return;
            }
            return; // Don't proceed with connection until paired
        }

        // Cancel any thread attempting to make a connection
        if (connectThread != null) {
            Log.d(TAG, "Cancelling existing connect thread");
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            Log.d(TAG, "Cancelling existing connected thread");
            connectedThread.cancel();
            connectedThread = null;
        }

        Log.d(TAG, "Starting new connection thread");
        // Start the thread to connect with the given device
        connectThread = new ConnectThread(device);
        connectThread.start();
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
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                discoveryListener.onDeviceConnected(device);
            }
        });

        // Start the thread to manage the connection and perform transmissions
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();
    }

    private void connectionFailed() {
        Log.e(TAG, "Connection failed. Restarting server to listen for new connections.");
        // Connection failed, restart the server to listen again
        notifyError("Failed to connect to device");
        startServer();
    }

    private void connectionLost() {
        Log.e(TAG, "Connection lost. Restarting server to listen for new connections.");
        // Connection was lost, restart the server to listen again
        notifyError("Device connection was lost");
        startServer();
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

    private void logDeviceInfo(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "=== Device Information ===");
            Log.d(TAG, "Name: " + device.getName());
            Log.d(TAG, "Address: " + device.getAddress());
            Log.d(TAG, "Type: " + device.getType());
            Log.d(TAG, "Bond State: " + getBondStateString(device.getBondState()));
            Log.d(TAG, "Class: " + device.getBluetoothClass());

            // Get UUIDs if available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                android.os.ParcelUuid[] uuids = device.getUuids();
                if (uuids != null) {
                    Log.d(TAG, "Available UUIDs:");
                    for (android.os.ParcelUuid uuid : uuids) {
                        Log.d(TAG, "  - " + uuid.toString());
                    }
                } else {
                    Log.d(TAG, "No UUIDs available - this might cause connection issues");
                }
            }
            Log.d(TAG, "=========================");
        }
    }

    private String getBondStateString(int bondState) {
        switch (bondState) {
            case BluetoothDevice.BOND_NONE: return "BOND_NONE";
            case BluetoothDevice.BOND_BONDING: return "BOND_BONDING";
            case BluetoothDevice.BOND_BONDED: return "BOND_BONDED";
            default: return "UNKNOWN";
        }
    }

    private void initializePairingReceiver() {
        pairingReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                    int previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE);

                    if (device != null) {
                        Log.d(TAG, "Bond state changed for " + device.getAddress() +
                                " from " + previousBondState + " to " + bondState);

                        if (bondState == BluetoothDevice.BOND_BONDED) {
                            Log.d(TAG, "Device paired successfully. You can now connect.");
                            handler.post(() -> {
                                if (discoveryListener != null) {
                                    Toast.makeText(BluetoothService.this, "Device paired! Tap to connect.", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else if (bondState == BluetoothDevice.BOND_NONE && previousBondState == BluetoothDevice.BOND_BONDING) {
                            Log.d(TAG, "Pairing failed");
                            handler.post(() -> {
                                if (discoveryListener != null) {
                                    notifyError("Pairing failed. Please try again.");
                                }
                            });
                        }
                    }
                }
            }
        };
    }

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
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client.
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
                    // Use insecure connection for better compatibility across different Android devices.
                    tmp = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, MY_UUID);
                    Log.d(TAG, "Server socket created successfully (insecure) with UUID: " + MY_UUID);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket's listen() method failed", e);
                tmp = null;
            }
            serverSocket = tmp;
        }

        public void run() {
            if (serverSocket == null) {
                Log.e(TAG, "Server socket is null, cannot accept connections.");
                return;
            }
            BluetoothSocket socket = null;
            Log.d(TAG, "Server socket listening for connections...");
            try {
                // This is a blocking call and will only return on a successful connection or an exception
                socket = serverSocket.accept();
            } catch (IOException e) {
                Log.d(TAG, "Socket accept() failed or was cancelled.", e);
                return;
            }

            // A connection was accepted
            if (socket != null) {
                synchronized (BluetoothService.this) {
                    manageConnectedSocket(socket, socket.getRemoteDevice());
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
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private BluetoothSocket socket;
        private final BluetoothDevice device;

        public ConnectThread(BluetoothDevice device) {
            this.device = device;
            BluetoothSocket tmp = null;

            try {
                if (ActivityCompat.checkSelfPermission(BluetoothService.this, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "BLUETOOTH_CONNECT permission not granted for ConnectThread.");
                    tmp = null;
                } else {
                    // Try insecure connection first (most compatible)
                    tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
                    Log.d(TAG, "Created insecure client socket.");
                }
            } catch (IOException e) {
                Log.e(TAG, "Insecure socket creation failed, trying secure", e);
                try {
                    if (ActivityCompat.checkSelfPermission(BluetoothService.this, Manifest.permission.BLUETOOTH_CONNECT)
                            == PackageManager.PERMISSION_GRANTED) {
                        tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                        Log.d(TAG, "Created secure client socket as fallback.");
                    }
                } catch (IOException e2) {
                    Log.e(TAG, "Secure socket creation also failed", e2);
                    tmp = null;
                }
            }
            socket = tmp;
        }

        public void run() {
            if (socket == null) {
                Log.e(TAG, "Socket is null, cannot connect.");
                connectionFailed();
                return;
            }

            // Always cancel discovery because it will slow down a connection
            stopDiscovery();

            // First attempt with the created socket
            if (attemptConnection(socket)) {
                return; // Success
            }

            // If first attempt failed, try reflection method as fallback
            Log.d(TAG, "Standard connection failed, trying reflection method");
            BluetoothSocket fallbackSocket = createFallbackSocket();
            if (fallbackSocket != null && attemptConnection(fallbackSocket)) {
                socket = fallbackSocket; // Update socket reference
                return; // Success
            }

            // All connection attempts failed
            connectionFailed();
        }

        private boolean attemptConnection(BluetoothSocket socketToTry) {
            try {
                Log.d(TAG, "Attempting to connect...");
                socketToTry.connect();
                Log.d(TAG, "Connection successful!");

                // Reset the ConnectThread because we're done
                synchronized (BluetoothService.this) {
                    connectThread = null;
                }

                // Start the connected thread
                manageConnectedSocket(socketToTry, device);
                return true;
            } catch (IOException connectException) {
                Log.e(TAG, "Connection attempt failed: " + connectException.getMessage());
                try {
                    socketToTry.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the socket", closeException);
                }
                return false;
            }
        }

        private BluetoothSocket createFallbackSocket() {
            try {
                // Use reflection to create socket on channel 1 (common for SPP)
                Method method = device.getClass().getMethod("createRfcommSocket", int.class);
                BluetoothSocket fallbackSocket = (BluetoothSocket) method.invoke(device, 1);
                Log.d(TAG, "Created fallback socket using reflection");
                return fallbackSocket;
            } catch (Exception e) {
                Log.e(TAG, "Reflection socket creation failed", e);
                return null;
            }
        }

        public void cancel() {
            try {
                if (socket != null) {
                    socket.close();
                }
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
            Log.d(TAG, "ConnectedThread created.");
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
                    connectionLost();
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
                if (socket != null) {
                    socket.close();
                }
                Log.d(TAG, "ConnectedThread socket closed");
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }
}