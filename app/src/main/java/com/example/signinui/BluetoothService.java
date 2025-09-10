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
import java.util.UUID;

public class BluetoothService extends Service {
    private static final String TAG = "BluetoothService";
    private static final String APP_NAME = "AdventureApp";
    private static final UUID MY_UUID = UUID.fromString("a60f35f0-b93a-11de-8a39-08002009c666");

    private final IBinder binder = new LocalBinder();
    private BluetoothAdapter bluetoothAdapter;
    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private BluetoothDiscoveryListener discoveryListener;
    private BroadcastReceiver discoveryReceiver;

    public interface BluetoothDiscoveryListener {
        void onDeviceDiscovered(BluetoothDevice device);
        void onDiscoveryFinished();
        void onDeviceConnected(BluetoothDevice device);
        void onFriendRequestReceived(String userId, String userName);
        void onConnectionError(String message);
    }

    public class LocalBinder extends Binder {
        BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Initialize the discovery receiver
        discoveryReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null && discoveryListener != null) {
                        // Check for BLUETOOTH_CONNECT permission before accessing device name
                        if (ActivityCompat.checkSelfPermission(BluetoothService.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted, cannot access device name");
                            // Still notify about the device discovery but without name details
                            discoveryListener.onDeviceDiscovered(device);
                            return;
                        }

                        if (device.getName() != null) {
                            Log.d(TAG, "Discovered device: " + device.getName());
                        }
                        discoveryListener.onDeviceDiscovered(device);
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    Log.d(TAG, "Bluetooth discovery finished.");
                    if (discoveryListener != null) {
                        discoveryListener.onDiscoveryFinished();
                    }
                }
            }
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopDiscovery();
        try {
            if (discoveryReceiver != null) {
                unregisterReceiver(discoveryReceiver);
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Receiver was not registered", e);
        }
    }

    public boolean isBluetoothSupported() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public void startDiscovery(BluetoothDiscoveryListener listener) {
        this.discoveryListener = listener;

        if (bluetoothAdapter == null) {
            if (discoveryListener != null) {
                discoveryListener.onConnectionError("Bluetooth not supported");
            }
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            if (discoveryListener != null) {
                discoveryListener.onConnectionError("Bluetooth not enabled");
            }
            return;
        }

        // Check for BLUETOOTH_SCAN permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            if (discoveryListener != null) {
                discoveryListener.onConnectionError("BLUETOOTH_SCAN permission required");
            }
            return;
        }

        // Cancel any ongoing discovery
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        // Register for broadcasts when a device is found
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        try {
            registerReceiver(discoveryReceiver, filter);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register discovery receiver", e);
            if (discoveryListener != null) {
                discoveryListener.onConnectionError("Failed to start discovery");
            }
            return;
        }

        // Start discovery
        boolean started = bluetoothAdapter.startDiscovery();
        if (!started) {
            if (discoveryListener != null) {
                discoveryListener.onConnectionError("Failed to start discovery");
            }
            try {
                unregisterReceiver(discoveryReceiver);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Receiver was not registered", e);
            }
        }
    }

    public void stopDiscovery() {
        if (bluetoothAdapter == null) {
            return;
        }

        // Check for BLUETOOTH_SCAN permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Missing BLUETOOTH_SCAN permission to stop discovery.");
            return;
        }

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
            Log.d(TAG, "Stopped Bluetooth discovery.");
        }

        try {
            if (discoveryReceiver != null) {
                unregisterReceiver(discoveryReceiver);
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Receiver was not registered", e);
        }

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
    }

    public void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission to connect.");
            if (discoveryListener != null) {
                discoveryListener.onConnectionError("BLUETOOTH_CONNECT permission required");
            }
            return;
        }

        String deviceName = device.getName() != null ? device.getName() : "Unknown Device";
        Log.d(TAG, "Connecting to device: " + deviceName);

        stopDiscovery(); // Stop discovery as it's resource-intensive

        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        connectThread = new ConnectThread(device);
        connectThread.start();
    }

    public void sendFriendRequest(String userId, String userName) {
        if (connectedThread != null) {
            String message = "FRIEND_REQUEST:" + userId + ":" + userName;
            connectedThread.write(message.getBytes());
        }
    }

    private class AcceptThread extends Thread {
        private BluetoothServerSocket serverSocket;

        public AcceptThread() {
            try {
                if (ActivityCompat.checkSelfPermission(BluetoothService.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "BLUETOOTH_CONNECT permission not granted for AcceptThread.");
                    return;
                }
                serverSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's listen() method failed", e);
                serverSocket = null;
            }
        }

        public void run() {
            if (serverSocket == null) {
                return;
            }

            BluetoothSocket socket;
            while (true) {
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    Log.d(TAG, "Socket accept() failed or was cancelled.", e);
                    break;
                }

                if (socket != null) {
                    Log.d(TAG, "A connection was accepted.");
                    connectedThread = new ConnectedThread(socket);
                    connectedThread.start();
                    try {
                        serverSocket.close();
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
                }
            } catch (IOException e) {
                Log.e(TAG, "Could not close the server socket", e);
            }
        }
    }

    private class ConnectThread extends Thread {
        private BluetoothSocket socket;
        private final BluetoothDevice device;

        public ConnectThread(BluetoothDevice device) {
            this.device = device;
            try {
                if (ActivityCompat.checkSelfPermission(BluetoothService.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "BLUETOOTH_CONNECT permission not granted for ConnectThread.");
                    return;
                }
                socket = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
                socket = null;
            }
        }

        public void run() {
            if (socket == null) {
                Log.e(TAG, "Socket is null, cannot connect.");
                handler.post(() -> {
                    if (discoveryListener != null) {
                        discoveryListener.onConnectionError("Socket creation failed.");
                    }
                });
                return;
            }

            try {
                socket.connect();
                handler.post(() -> {
                    if (discoveryListener != null) {
                        discoveryListener.onDeviceConnected(device);
                    }
                });

                connectedThread = new ConnectedThread(socket);
                connectedThread.start();

            } catch (IOException connectException) {
                Log.e(TAG, "Failed to connect", connectException);
                try {
                    socket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                handler.post(() -> {
                    if (discoveryListener != null) {
                        discoveryListener.onConnectionError("Failed to connect to device");
                    }
                });
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
                    break;
                }
            }
        }

        private void processMessage(String message) {
            if (message.startsWith("FRIEND_REQUEST:")) {
                String[] parts = message.split(":", 3);
                if (parts.length == 3) {
                    handler.post(() -> {
                        if (discoveryListener != null) {
                            discoveryListener.onFriendRequestReceived(parts[1], parts[2]);
                        }
                    });
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "Error writing to output stream", e);
            }
        }

        public void cancel() {
            try {
                if (socket != null) socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }
}