package com.dafa.grannybot;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private String activePreset = "Preset 1";
    private TextView txtStatus;
    private Button[] presetButtons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtStatus = findViewById(R.id.txtStatus);
        Button btnP1 = findViewById(R.id.btnP1);
        Button btnP2 = findViewById(R.id.btnP2);
        Button btnP3 = findViewById(R.id.btnP3);
        Button btnP4 = findViewById(R.id.btnP4);
        Button btnP5 = findViewById(R.id.btnP5);
        presetButtons = new Button[]{btnP1, btnP2, btnP3, btnP4, btnP5};

        for (int i = 0; i < presetButtons.length; i++) {
            final int index = i;
            presetButtons[i].setOnClickListener(v -> selectPreset(index));
        }

        findViewById(R.id.btnRecord).setOnClickListener(v -> {
            txtStatus.setText("🔴 MEREKAM: " + activePreset + " (Buka Game Granny!)");
            txtStatus.setTextColor(Color.parseColor("#FF2A54"));
            Toast.makeText(this, "Mulai merekam sentuhan untuk " + activePreset, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnPlay).setOnClickListener(v -> {
            txtStatus.setText("▶️ AUTO-PLAY BERJALAN: " + activePreset);
            txtStatus.setTextColor(Color.parseColor("#00F59B"));
            Toast.makeText(this, "Menjalankan rute otomatis " + activePreset, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnStop).setOnClickListener(v -> {
            txtStatus.setText("⏹️ Dihentikan (" + activePreset + " Siap)");
            txtStatus.setTextColor(Color.parseColor("#FFD166"));
        });
    }

    private void selectPreset(int selectedIndex) {
        activePreset = "Preset " + (selectedIndex + 1);
        for (int i = 0; i < presetButtons.length; i++) {
            if (i == selectedIndex) {
                presetButtons[i].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF2A54")));
            } else {
                presetButtons[i].setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#252533")));
            }
        }
        txtStatus.setText("🟢 Status: Standby (" + activePreset + " Aktif)");
        txtStatus.setTextColor(Color.parseColor("#00F59B"));
    }
}
