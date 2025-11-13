package com.hpp.daftree.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hpp.daftree.R;
import com.hpp.daftree.database.DeviceInfo;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * محول لعرض قائمة الأجهزة المرتبطة بالترخيص
 */
public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {
    
    private final List<DeviceInfo> devices;
    private final OnDeviceRemoveListener removeListener;
    private final String currentDeviceId;
    private Context context;
    public interface OnDeviceRemoveListener {
        void onRemoveDevice(DeviceInfo device);
    }
    
//    public DeviceAdapter(List<DeviceInfo> devices, OnDeviceRemoveListener removeListener) {
//        this.devices = devices;
//        this.removeListener = removeListener;
//    }
public DeviceAdapter(List<DeviceInfo> devices, String currentDeviceId, OnDeviceRemoveListener removeListener) {
    this.context = context;
        this.devices = devices;
    this.currentDeviceId = currentDeviceId;
    this.removeListener = removeListener;
}
    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_device, parent, false);
        return new DeviceViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
       try {
           DeviceInfo device = devices.get(position);

           if ((device != null) && device.getDeviceId().equals(currentDeviceId)) {
               holder.removeButton.setVisibility(View.GONE);
           } else {
               holder.removeButton.setVisibility(View.VISIBLE);
           }
           assert device != null;
           holder.bind(device);
       } catch (Exception e) {
           Log.e("DeviceAdapter",e.getMessage());
       }
    }
    
    @Override
    public int getItemCount() {
        return devices.size();
    }
    
    class DeviceViewHolder extends RecyclerView.ViewHolder {
        private final ImageView deviceIcon;
        private final TextView deviceName;
        private final TextView deviceDetails;
        private final TextView lastActive;
        private final Button removeButton;
        
        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceIcon = itemView.findViewById(R.id.iv_device_icon);
            deviceName = itemView.findViewById(R.id.tv_device_name);
            deviceDetails = itemView.findViewById(R.id.tv_device_details);
            lastActive = itemView.findViewById(R.id.tv_last_active);
            removeButton = itemView.findViewById(R.id.btn_remove_device);
        }
        
        public void bind(DeviceInfo device) {
            // Set device icon based on type
            if ((device!= null) && device.getDeviceName().toLowerCase().contains("tablet")) {
                deviceIcon.setImageResource(R.drawable.ic_tablet);
            } else {
                deviceIcon.setImageResource(R.drawable.ic_smartphone);
            }
            
            // Set device name
            deviceName.setText(device.getDisplayName());
            
            // Set device details
            String details = device.getDeviceModel() + " • Android " + device.getAndroidVersion();
            deviceDetails.setText(details);
            
            // Set last active time
            if (device.getLastActiveAt() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("en"));
//                String lastActiveText =context.getString(R.string.device_last_activate)  + ((device.getLastActiveAt()));

                String lastActiveText =((device.getLastActiveAt()));
                lastActive.setText(lastActiveText);
            } else {
                lastActive.setText("آخر نشاط: غير محدد");
            }
            
            // Set remove button click listener
            removeButton.setOnClickListener(v -> {
                if (removeListener != null) {
                    removeListener.onRemoveDevice(device);
                }
            });
        }
    }
}

