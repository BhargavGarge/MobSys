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
        return new BluetoothDeviceAdapter(new ArrayList<>(), new HashMap<>(), listener);
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

        public void bind(final BluetoothDevice device, final OnDeviceClickListener listener, boolean isOnline) {
            String name = "Unknown Device";
            String bondStateText = "";

            // Check for BLUETOOTH_CONNECT permission before accessing device name
            if (ContextCompat.checkSelfPermission(itemView.getContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                String deviceNameStr = device.getName();
                if (deviceNameStr != null && !deviceNameStr.isEmpty()) {
                    name = deviceNameStr;
                }

                // Add bond state information
                switch (device.getBondState()) {
                    case BluetoothDevice.BOND_BONDED:
                        bondStateText = " (Paired)";
                        break;
                    case BluetoothDevice.BOND_BONDING:
                        bondStateText = " (Pairing...)";
                        break;
                    case BluetoothDevice.BOND_NONE:
                        bondStateText = " (Not paired)";
                        break;
                }
            } else {
                name = "Permission required";
            }

            deviceName.setText(name + bondStateText);

            // Enhanced status text
            String status;
            if (isOnline) {
                if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                    status = "Online • Tap to connect";
                } else {
                    status = "Online • Will pair & connect";
                }
            } else {
                status = "Offline • Not available";
            }
            deviceStatus.setText(status);

            // Set appropriate online/offline status icon
            if (isOnline) {
                onlineStatus.setImageResource(R.drawable.ic_online);
            } else {
                onlineStatus.setImageResource(R.drawable.ic_offline);
            }

            // Enhanced click handling with bond state awareness
            itemView.setOnClickListener(v -> {
                if (listener != null && isOnline) {
                    listener.onDeviceClick(device);
                } else if (!isOnline) {
                    Toast.makeText(itemView.getContext(), "Device is offline", Toast.LENGTH_SHORT).show();
                }
            });

            // Visual feedback based on status
            itemView.setEnabled(isOnline);
            itemView.setAlpha(isOnline ? 1.0f : 0.6f);

            // Add visual distinction for paired devices
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                itemView.setBackgroundResource(R.drawable.bg_paired_device); // You'll need to create this drawable
            } else {
                itemView.setBackgroundResource(R.drawable.bg_unpaired_device); // You'll need to create this drawable
            }
        }
    }
}