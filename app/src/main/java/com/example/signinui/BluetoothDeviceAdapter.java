package com.example.signinui;

import android.bluetooth.BluetoothDevice;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class BluetoothDeviceAdapter extends RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder> {
    private List<BluetoothDevice> devices;
    private OnDeviceClickListener listener;

    public interface OnDeviceClickListener {
        void onDeviceClick(BluetoothDevice device);
    }

    public BluetoothDeviceAdapter(List<BluetoothDevice> devices, OnDeviceClickListener listener) {
        this.devices = devices;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bluetooth_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        BluetoothDevice device = devices.get(position);
        holder.bind(device, listener);
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    public static class DeviceViewHolder extends RecyclerView.ViewHolder {
        private TextView deviceName;
        private TextView deviceStatus;
        private ImageView deviceIcon;
        private View deviceCard;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceName = itemView.findViewById(R.id.device_name);
            deviceStatus = itemView.findViewById(R.id.device_status);
            deviceIcon = itemView.findViewById(R.id.device_icon);
            deviceCard = itemView.findViewById(R.id.device_card);
        }

        public void bind(BluetoothDevice device, OnDeviceClickListener listener) {
            String name;
            try {
                name = device.getName();
                if (name == null || name.isEmpty()) {
                    name = "Unknown Adventurer";
                }
            } catch (SecurityException e) {
                name = "Unknown Adventurer";
            }

            deviceName.setText(name);
            deviceStatus.setText("Nearby • Tap to connect");

            // Set different icons based on device type if available
            if (name.toLowerCase().contains("phone") || name.toLowerCase().contains("mobile")) {
                deviceIcon.setImageResource(R.drawable.ic_phone);
            } else {
                deviceIcon.setImageResource(R.drawable.ic_person);
            }

            deviceCard.setOnClickListener(v -> listener.onDeviceClick(device));
        }
    }
}