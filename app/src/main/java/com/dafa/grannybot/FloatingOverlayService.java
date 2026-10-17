package com.dafa.grannybot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

public class FloatingOverlayService extends Service {
    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;

    private int currentSlot = 1;
    private final int MAX_SLOTS = 10;
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private int secondsElapsed = 0;

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;

    private SharedPreferences prefs;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private TextView txtStatus, txtCurrentSlot;
    private Button btnBubble;
    private LinearLayout panelExpanded;

    private final String[] slotNames = {
        "Rute Pintu Utama", "Rute Mobil Garasi", "Rute Selokan Bawah",
        "Ambil Senjata / Crossbow", "Sapu Bersih Laci", "Preset Item A",
        "Preset Item B", "Preset Item C", "Preset Item D", "Preset Item E"
    };

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRecording || isPlaying) {
                secondsElapsed++;
                int mins = secondsElapsed / 60;
                int secs = secondsElapsed % 60;
                String timeStr = String.format("%02d:%02d", mins, secs);
                if (isRecording) {
                    txtStatus.setText("🔴 REKAM LAYAR SLOT " + currentSlot + " | " + timeStr);
                } else if (isPlaying) {
                    txtStatus.setText("▶️ AUTO-PLAY BERJALAN | " + timeStr);
                }
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("GrannyMacroSaves", Context.MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "granny_macro_channel";
            NotificationChannel channel = new NotificationChannel(channelId, "Granny Macro Background", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
            Notification notification = new NotificationCompat.Builder(this, channelId)
                    .setContentTitle("Granny AutoBot Pro")
                    .setContentText("Overlay & Screen Recorder Aktif")
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .build();
            startForeground(1, notification);
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.layout_floating_overlay, null);

        int layoutFlag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 60;
        params.y = 120;

        windowManager.addView(overlayView, params);
        setupUI();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "START_RECORDING_CONFIRMED".equals(intent.getAction())) {
            int resultCode = intent.getIntExtra("code", RESULT_OK);
            Intent data = intent.getParcelableExtra("data");
            if (projectionManager != null && data != null) {
                mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            }
            startActualRecording();
        }
        return START_STICKY;
    }

    private void setupUI() {
        panelExpanded = overlayView.findViewById(R.id.panelExpanded);
        btnBubble = overlayView.findViewById(R.id.btnBubble);
        txtStatus = overlayView.findViewById(R.id.txtOverlayStatus);
        txtCurrentSlot = overlayView.findViewById(R.id.txtCurrentSlot);

        View headerDrag = overlayView.findViewById(R.id.headerDrag);
        setupDragListener(headerDrag);
        setupDragListener(btnBubble);

        overlayView.findViewById(R.id.btnMinimize).setOnClickListener(v -> {
            panelExpanded.setVisibility(View.GONE);
            btnBubble.setVisibility(View.VISIBLE);
        });

        btnBubble.setOnClickListener(v -> {
            btnBubble.setVisibility(View.GONE);
            panelExpanded.setVisibility(View.VISIBLE);
        });

        overlayView.findViewById(R.id.btnCloseOverlay).setOnClickListener(v -> stopSelf());

        overlayView.findViewById(R.id.btnPrevSave).setOnClickListener(v -> {
            if (currentSlot > 1) currentSlot--;
            else currentSlot = MAX_SLOTS;
            updateSlotDisplay();
        });

        overlayView.findViewById(R.id.btnNextSave).setOnClickListener(v -> {
            if (currentSlot < MAX_SLOTS) currentSlot++;
            else currentSlot = 1;
            updateSlotDisplay();
        });

        // PANGGIL POP-UP PERIZINAN REKAM LAYAR SISTEM
        overlayView.findViewById(R.id.btnStartMacro).setOnClickListener(v -> {
            Intent intent = new Intent(this, ScreenCaptureRequestActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Toast.makeText(this, "Izinkan rekam layar pada pop-up sistem!", Toast.LENGTH_LONG).show();
        });

        overlayView.findViewById(R.id.btnPlayMacro).setOnClickListener(v -> {
            String savedData = prefs.getString("slot_" + currentSlot, null);
            if (savedData == null) {
                txtStatus.setTextColor(Color.parseColor("#FFD166"));
                txtStatus.setText("⚠️ Slot " + currentSlot + " kosong!");
                return;
            }
            stopCurrentTask();
            isPlaying = true;
            secondsElapsed = 0;
            txtStatus.setTextColor(Color.parseColor("#00F59B"));
            txtStatus.setText("▶️ AUTO-PLAY BERJALAN | 00:00");
            timerHandler.postDelayed(timerRunnable, 1000);

            if (GrannyAccessibilityService.getInstance() != null) {
                GrannyAccessibilityService.getInstance().clickAt(540, 1500);
                Toast.makeText(this, "Memutar Macro Slot " + currentSlot, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Error: Aksesibilitas belum aktif!", Toast.LENGTH_LONG).show();
            }
        });

        overlayView.findViewById(R.id.btnSaveMacro).setOnClickListener(v -> {
            stopCurrentTask();
            int recordedDuration = Math.max(secondsElapsed, 10);
            String metaInfo = "Tersimpan (" + recordedDuration + " dtk)";
            prefs.edit().putString("slot_" + currentSlot, metaInfo).apply();
            updateSlotDisplay();
            txtStatus.setTextColor(Color.parseColor("#2196F3"));
            txtStatus.setText("💾 SLOT " + currentSlot + " BERHASIL DISIMPAN!");
            Toast.makeText(this, "Tersimpan di Slot " + currentSlot, Toast.LENGTH_SHORT).show();
        });

        overlayView.findViewById(R.id.btnDeleteSave).setOnClickListener(v -> {
            stopCurrentTask();
            prefs.edit().remove("slot_" + currentSlot).apply();
            updateSlotDisplay();
            txtStatus.setTextColor(Color.parseColor("#FF6D00"));
            txtStatus.setText("🗑️ Slot " + currentSlot + " dikosongkan.");
            Toast.makeText(this, "Dihapus", Toast.LENGTH_SHORT).show();
        });

        overlayView.findViewById(R.id.btnStopAll).setOnClickListener(v -> {
            stopCurrentTask();
            txtStatus.setTextColor(Color.parseColor("#FFD166"));
            txtStatus.setText("⏹️ Dihentikan (Standby)");
        });

        updateSlotDisplay();
    }

    private void startActualRecording() {
        stopCurrentTask();
        isRecording = true;
        secondsElapsed = 0;
        txtStatus.setTextColor(Color.parseColor("#FF2A54"));
        txtStatus.setText("🔴 REKAM LAYAR AKTIF | 00:00");
        timerHandler.postDelayed(timerRunnable, 1000);
        Toast.makeText(this, "Perekaman layar dimulai!", Toast.LENGTH_SHORT).show();
    }

    private void stopCurrentTask() {
        isRecording = false;
        isPlaying = false;
        timerHandler.removeCallbacks(timerRunnable);
    }

    private void updateSlotDisplay() {
        String savedState = prefs.getString("slot_" + currentSlot, "Kosong");
        String title = slotNames[currentSlot - 1];
        txtCurrentSlot.setText("Slot " + currentSlot + ": " + title + "\n[" + savedState + "]");
    }

    private void setupDragListener(View dragView) {
        dragView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(overlayView, params);
                        return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCurrentTask();
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        if (overlayView != null) windowManager.removeView(overlayView);
    }
}
