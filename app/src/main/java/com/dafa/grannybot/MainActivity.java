package com.dafa.grannybot;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnLaunch = findViewById(R.id.btnLaunchOverlay);
        Button btnStop = findViewById(R.id.btnStopOverlay);

        btnLaunch.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Aktifkan izin 'Tampilkan di atas aplikasi lain' dulu!", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                startService(new Intent(this, FloatingOverlayService.class));
                Toast.makeText(this, "Panel Overlay Aktif! Silakan buka Granny.", Toast.LENGTH_SHORT).show();
                moveTaskToBack(true);
            }
        });

        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, FloatingOverlayService.class));
            Toast.makeText(this, "Panel Overlay Dimatikan", Toast.LENGTH_SHORT).show();
        });
    }
}
