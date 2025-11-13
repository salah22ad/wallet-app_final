package com.hpp.daftree.ui;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.hpp.daftree.R;
import com.hpp.daftree.databinding.ActivityProfileBinding;
import com.hpp.daftree.databinding.ActivityWebServerBinding;
import com.hpp.daftree.models.LocalWebServer;
import com.hpp.daftree.utils.EdgeToEdgeUtils;

import java.io.IOException;
import java.util.Locale;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.IOException;
import java.util.Locale;

public class WebServerActivity extends BaseActivity {
    private ActivityWebServerBinding binding;
    private LocalWebServer server;
//    private TextView tvStatus, tvIpAddress;
//    private Button btnStartStop;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWebServerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeUtils.applyEdgeToEdge(this, binding.toolbar);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        binding.btnStartStop.setOnClickListener(v -> {

            if (server != null && server.isAlive()) {
                stopServer();
            } else {
              if(!isConnectedToWifi(this)) {
                  Toast.makeText(this, getString(R.string.ar_long_text_19), Toast.LENGTH_SHORT).show();
                  return;
              }
                startServer();
            }
        });
    }
    private boolean isConnectedToWifi(Context context) {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                // تحقق هل نوع الاتصال Wi-Fi
                return networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
            }
        }
        return false;
    }

    private void startServer() {
        try {
            server = new LocalWebServer(8080, this);
            server.start();
            binding.tvStatus.setText(getString(R.string.start_server));
            binding.btnStartStop.setText(getString(R.string.server_stopped));
            binding.tvIpAddress.setText(String.format(Locale.US, "http://%s:8080", getIpAddress()));
        } catch (IOException e) {
            e.printStackTrace();
            binding.tvStatus.setText(getString(R.string.error));
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
        binding.tvStatus.setText(getString(R.string.server_stopped));
        binding.btnStartStop.setText(getString(R.string.start_server));
        binding.tvIpAddress.setText("");
    }

    private String getIpAddress() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        return String.format(Locale.US, "%d.%d.%d.%d",
                (ipAddress & 0xff),
                (ipAddress >> 8 & 0xff),
                (ipAddress >> 16 & 0xff),
                (ipAddress >> 24 & 0xff));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopServer();
    }
}