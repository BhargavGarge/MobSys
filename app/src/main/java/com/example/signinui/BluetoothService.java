package com.example.signinui;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    private Handler handler = new Handler(Looper.getMainLooper());

    private List<BluetoothDevice> discoveredDevices = new ArrayList<>();
    private BluetoothDiscoveryListener discoveryListener;

    public interface BluetoothDiscoveryListener {
        void onDeviceDiscovered(BluetoothDevice device);
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
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public void enableBluetooth() {
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter.enable();
            } else {
                Log.w(TAG, "BLUETOOTH_CONNECT permission required to enable Bluetooth");
                // You might want to notify the user or request permission here
            }
        }
    }

    public void startDiscovery(BluetoothDiscoveryListener listener) {
        this.discoveryListener = listener;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            if (discoveryListener != null) {
                discoveryListener.onConnectionError("Bluetooth permissions not granted");
            }
            return;
        }

        // Get already paired devices
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        discoveredDevices.clear();
        discoveredDevices.addAll(pairedDevices);

        // Start discovering new devices
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        bluetoothAdapter.startDiscovery();

        // Start accepting incoming connections
        acceptThread = new AcceptThread();
        acceptThread.start();
    }

    public void stopDiscovery() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
    }

    public void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Cancel any ongoing discovery
        stopDiscovery();

        // Cancel any thread attempting to make a connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Start the thread to connect with the given device
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
            BluetoothServerSocket tmp = null;
            try {
                if (ActivityCompat.checkSelfPermission(BluetoothService.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's listen() method failed", e);
            }
            serverSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket;
            while (true) {
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket's accept() method failed", e);
                    break;
                }

                if (socket != null) {
                    // A connection was accepted
                    connectedThread = new ConnectedThread(socket);
                    connectedThread.start();

                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Could not close the connect socket", e);
                    }
                    break;
                }
            }
        }

        public void cancel() {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

    private class ConnectThread extends Thread {
        private BluetoothSocket socket;
        private BluetoothDevice device;

        public ConnectThread(BluetoothDevice device) {
            this.device = device;
            BluetoothSocket tmp = null;

            try {
                if (ActivityCompat.checkSelfPermission(BluetoothService.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            socket = tmp;
        }

        public void run() {
            if (ActivityCompat.checkSelfPermission(BluetoothService.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            bluetoothAdapter.cancelDiscovery();

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
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private BluetoothSocket socket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;

            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error getting streams from socket", e);
            }
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
                    Log.e(TAG, "Input stream was disconnected", e);
                    break;
                }
            }
        }

        private void processMessage(String message) {
            if (message.startsWith("FRIEND_REQUEST:")) {
                String[] parts = message.split(":");
                if (parts.length >= 3) {
                    String userId = parts[1];
                    String userName = parts[2];

                    handler.post(() -> {
                        if (discoveryListener != null) {
                            discoveryListener.onFriendRequestReceived(userId, userName);
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
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }
}