package com.example.signinui;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BluetoothDeviceAdapter extends RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder> {
    private List<BluetoothDevice> devices;
    private Map<String, Boolean> onlineStatusMap;
    private OnDeviceClickListener listener;

    public interface OnDeviceClickListener {
        void onDeviceClick(BluetoothDevice device);
    }

    public BluetoothDeviceAdapter(List<BluetoothDevice> devices, Map<String, Boolean> onlineStatusMap, OnDeviceClickListener listener) {
        this.devices = devices;
        this.onlineStatusMap = onlineStatusMap;
        this.listener = listener;
    }

    // Initialize with empty list and online status map
    public static BluetoothDeviceAdapter createWithEmptyList(OnDeviceClickListener listener) {
        Map<String, Boolean> onlineStatusMap = new HashMap<>();
        return new BluetoothDeviceAdapter(new ArrayList<>(), onlineStatusMap, listener);
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bluetooth_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        BluetoothDevice device = devices.get(position);
        // Get online status for this device (default to false if not set)
        boolean isOnline = onlineStatusMap.getOrDefault(device.getAddress(), false);
        holder.bind(device, listener, isOnline);
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    public void updateDevices(List<BluetoothDevice> newDevices, Map<String, Boolean> newOnlineStatusMap) {
        devices.clear();
        devices.addAll(newDevices);
        onlineStatusMap.clear();
        onlineStatusMap.putAll(newOnlineStatusMap);
        notifyDataSetChanged();
    }


    // Add this method to update online status for a specific device
    public void setOnlineStatus(String deviceAddress, boolean isOnline) {
        onlineStatusMap.put(deviceAddress, isOnline);
        notifyDataSetChanged();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        private TextView deviceName;
        private TextView deviceStatus;
        private ImageView deviceIcon;
        private ImageView onlineStatus;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceName = itemView.findViewById(R.id.device_name);
            deviceStatus = itemView.findViewById(R.id.device_status);
            deviceIcon = itemView.findViewById(R.id.device_icon);
            onlineStatus = itemView.findViewById(R.id.online_status);
        }

        public void bind(BluetoothDevice device, OnDeviceClickListener listener, boolean isOnline) {
            String name;

            // Check for BLUETOOTH_CONNECT permission before accessing device name
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(itemView.getContext(),
                        Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    name = device.getName();
                } else {
                    name = "Bluetooth Device";
                }
            } else {
                // For older Android versions, we can access the name directly
                name = device.getName();
            }

            if (name == null || name.isEmpty()) {
                name = "Unknown Device";
            }
            deviceName.setText(name);

            String status = isOnline ? "Online • Tap to connect" : "Offline • Not available";
            deviceStatus.setText(status);

            // Set online status indicator
            if (isOnline) {
                onlineStatus.setImageResource(R.drawable.ic_online); // Green dot
            } else {
                onlineStatus.setImageResource(R.drawable.ic_offline); // Red dot
            }

            itemView.setOnClickListener(v -> {
                if (listener != null && isOnline) {
                    // Check permission before attempting to connect
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        if (ContextCompat.checkSelfPermission(itemView.getContext(),
                                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            listener.onDeviceClick(device);
                        } else {
                            Toast.makeText(itemView.getContext(),
                                    "Bluetooth permission required to connect",
                                    Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        listener.onDeviceClick(device);
                    }
                }
            });

            // Disable click if offline
            itemView.setEnabled(isOnline);
            itemView.setAlpha(isOnline ? 1.0f : 0.6f);
        }
    }
}