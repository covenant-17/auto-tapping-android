package com.autotapping;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;

public class FloatingOverlayService extends Service {

    public static final String ACTION_STOP = "com.autotapping.STOP_OVERLAY";

    private static final String CHANNEL_ID = "autotapping_overlay";
    private static final int NOTIFICATION_ID = 1;

    private WindowManager windowManager;

    // Crosshair overlay
    private View crosshairView;
    private WindowManager.LayoutParams crosshairParams;

    // Settings popup overlay
    private View menuView;
    private WindowManager.LayoutParams menuParams;
    private boolean menuVisible = false;

    // Current speed: taps per second (default 2.0)
    private float tapsPerSec = 2.0f;

    // Pause countdown (when user touches background app)
    private final Handler pauseHandler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;
    private int countdownRemaining = 0;

    // Receiver: stop tapping when screen turns off or device locks
    private final BroadcastReceiver screenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                // Screen turned off — stop tapping
                stopTapping();
                updateStatusLabel();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                // Screen turned on — if still locked, stop tapping
                KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                if (km != null && km.isKeyguardLocked()) {
                    stopTapping();
                    updateStatusLabel();
                }
            }
        }
    };

    // -----------------------------------------------------------------------

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        startForegroundWithNotification();
        showCrosshair();
        // Register screen-off / lock screen receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenOffReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(screenOffReceiver);
        stopTapping();
        if (crosshairView != null) windowManager.removeView(crosshairView);
        if (menuVisible && menuView != null) windowManager.removeView(menuView);
    }

    // -----------------------------------------------------------------------
    // Notification
    // -----------------------------------------------------------------------

    private void startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "AutoTapping Overlay", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Оверлей прицела и настроек");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(channel);
        }

        Intent stopIntent = new Intent(this, FloatingOverlayService.class);
        stopIntent.setAction(ACTION_STOP);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent stopPending = PendingIntent.getService(this, 0, stopIntent, flags);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("AutoTapping активен")
                .setContentText("Перетащите прицел на нужную точку")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Остановить", stopPending)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    // -----------------------------------------------------------------------
    // Crosshair overlay
    // -----------------------------------------------------------------------

    private void showCrosshair() {
        crosshairView = LayoutInflater.from(this).inflate(R.layout.overlay_crosshair, null);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        crosshairParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);

        crosshairParams.gravity = Gravity.TOP | Gravity.START;
        crosshairParams.x = 200;
        crosshairParams.y = 400;
        // Receive ACTION_OUTSIDE when user touches outside the crosshair
        crosshairParams.flags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

        // Drag logic
        crosshairView.setOnTouchListener(new CrosshairDragListener());

        // Open menu on gear button
        View btnMenu = crosshairView.findViewById(R.id.btn_open_menu);
        btnMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMenu();
            }
        });

        windowManager.addView(crosshairView, crosshairParams);
    }

    // -----------------------------------------------------------------------
    // Drag touch listener
    // -----------------------------------------------------------------------

    private class CrosshairDragListener implements View.OnTouchListener {
        private int initialX, initialY;
        private float initialTouchX, initialTouchY;
        private long touchDownTime;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_OUTSIDE:
                    // User touched the background app (not our crosshair)
                    onUserTouchedBackground();
                    return false;

                case MotionEvent.ACTION_DOWN:
                    initialX = crosshairParams.x;
                    initialY = crosshairParams.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    touchDownTime = System.currentTimeMillis();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (event.getRawX() - initialTouchX);
                    int dy = (int) (event.getRawY() - initialTouchY);
                    crosshairParams.x = initialX + dx;
                    crosshairParams.y = initialY + dy;
                    windowManager.updateViewLayout(crosshairView, crosshairParams);
                    updateCoordsLabel();
                    // If tapping, update target in real time
                    updateTapTargetIfActive();
                    return true;

                case MotionEvent.ACTION_UP:
                    // Treat as click if barely moved and fast
                    long elapsed = System.currentTimeMillis() - touchDownTime;
                    float movedTotal = Math.abs(event.getRawX() - initialTouchX)
                            + Math.abs(event.getRawY() - initialTouchY);
                    if (elapsed < 300 && movedTotal < 10) {
                        v.performClick();
                    }
                    return true;
            }
            return false;
        }
    }

    private class MenuDragListener implements View.OnTouchListener {
        private int initialX, initialY;
        private float initialTouchX, initialTouchY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = menuParams.x;
                    initialY = menuParams.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) (event.getRawX() - initialTouchX);
                    int dy = (int) (event.getRawY() - initialTouchY);
                    menuParams.x = initialX + dx;  // gravity START: x is left margin, same direction
                    menuParams.y = initialY + dy;
                    windowManager.updateViewLayout(menuView, menuParams);
                    return true;
                case MotionEvent.ACTION_UP:
                    return true;
            }
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Settings menu popup
    // -----------------------------------------------------------------------

    private void toggleMenu() {
        if (menuVisible) {
            hideMenu();
        } else {
            showMenu();
        }
    }

    private void showMenu() {
        if (menuVisible) return;
        // Opening the popup intentionally stops tapping
        stopTapping();
        menuView = LayoutInflater.from(this).inflate(R.layout.overlay_menu, null);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        menuParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                // NOT_FOCUSABLE   = don't steal focus from background app
                // NOT_TOUCH_MODAL = touches outside popup bounds pass through
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);

        menuParams.gravity = Gravity.TOP | Gravity.START;
        menuParams.x = 16;
        menuParams.y = 16;

        bindMenuViews();
        windowManager.addView(menuView, menuParams);
        menuVisible = true;
        updateStatusLabel(); // update after menuVisible = true so label shows correct state
    }

    private void hideMenu() {
        if (!menuVisible || menuView == null) return;
        windowManager.removeView(menuView);
        menuView = null;
        menuVisible = false;
    }

    private void bindMenuViews() {
        // Drag handle — move popup by dragging the header
        menuView.findViewById(R.id.menu_drag_handle).setOnTouchListener(new MenuDragListener());

        // Close button
        menuView.findViewById(R.id.btn_close_menu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideMenu();
            }
        });

        // Coords label
        updateCoordsLabel();

        // Speed SeekBar — progress 0..195 → taps/sec 0.5..20.0
        SeekBar seekBar = menuView.findViewById(R.id.seekbar_speed);
        TextView tvSpeed = menuView.findViewById(R.id.tv_speed_value);

        // Set initial progress from current tapsPerSec (progress = tapsPerSec - 1)
        seekBar.setProgress(Math.max(0, Math.round(tapsPerSec) - 1));
        tvSpeed.setText(formatSpeed(tapsPerSec));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                tapsPerSec = 1 + progress;  // 1–1000 tap/sec
                tvSpeed.setText(formatSpeed(tapsPerSec));
                // Only update on the fly when user actually moves the slider
                if (fromUser && isTapping()) {
                    TapAccessibilityService svc = TapAccessibilityService.getInstance();
                    if (svc != null) {
                        svc.startTapping(crosshairParams.x, crosshairParams.y, speedToInterval());
                    }
                }
            }

            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Start button
        menuView.findViewById(R.id.btn_start).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TapAccessibilityService svc = TapAccessibilityService.getInstance();
                if (svc != null) {
                    svc.startTapping(crosshairParams.x, crosshairParams.y, speedToInterval());
                    hideMenu();
                }
            }
        });

        // Stop button
        menuView.findViewById(R.id.btn_stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopTapping();
                updateStatusLabel();
            }
        });

        // Close crosshair button — stops tapping and shuts down the service
        menuView.findViewById(R.id.btn_close_crosshair).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopTapping();
                stopSelf();
            }
        });

        updateStatusLabel();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void updateCoordsLabel() {
        if (menuVisible && menuView != null) {
            TextView tv = menuView.findViewById(R.id.tv_coords);
            if (tv != null) {
                tv.setText("Цель: X " + crosshairParams.x + "  Y " + crosshairParams.y);
            }
        }
    }

    private void updateStatusLabel() {
        if (menuVisible && menuView != null) {
            TextView tv = menuView.findViewById(R.id.tv_status);
            if (tv != null) {
                if (isTapping()) {
                    tv.setText("● Тапинг активен");
                    tv.setTextColor(0xFF7BFF9F);
                } else {
                    tv.setText("● Остановлен");
                    tv.setTextColor(0xAA607080);
                }
            }
        }
    }

    private void updateTapTargetIfActive() {
        TapAccessibilityService svc = TapAccessibilityService.getInstance();
        if (svc != null && svc.isTapping()) {
            svc.startTapping(crosshairParams.x, crosshairParams.y, speedToInterval());
        }
    }

    private void stopTapping() {
        cancelCountdown();
        TapAccessibilityService svc = TapAccessibilityService.getInstance();
        if (svc != null) svc.stopTapping();
    }

    // -----------------------------------------------------------------------
    // Pause countdown (triggered when user touches background app)
    // -----------------------------------------------------------------------

    private void onUserTouchedBackground() {
        if (isTapping() || countdownRemaining > 0) {
            startPauseCountdown();
        }
    }

    private void startPauseCountdown() {
        // Stop auto-tap
        TapAccessibilityService svc = TapAccessibilityService.getInstance();
        if (svc != null) svc.stopTapping();
        updateStatusLabel();

        // Cancel any running countdown and restart from 3
        if (countdownRunnable != null) {
            pauseHandler.removeCallbacks(countdownRunnable);
        }
        countdownRemaining = 3;
        showCountdown(countdownRemaining);

        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                countdownRemaining--;
                if (countdownRemaining <= 0) {
                    countdownRemaining = 0;
                    countdownRunnable = null;
                    hideCountdown();
                    // Resume auto-tap
                    TapAccessibilityService s = TapAccessibilityService.getInstance();
                    if (s != null) {
                        s.startTapping(crosshairParams.x, crosshairParams.y, speedToInterval());
                        updateStatusLabel();
                    }
                } else {
                    showCountdown(countdownRemaining);
                    pauseHandler.postDelayed(this, 1000);
                }
            }
        };
        pauseHandler.postDelayed(countdownRunnable, 1000);
    }

    private void cancelCountdown() {
        if (countdownRunnable != null) {
            pauseHandler.removeCallbacks(countdownRunnable);
            countdownRunnable = null;
        }
        countdownRemaining = 0;
        hideCountdown();
    }

    private void showCountdown(int n) {
        if (crosshairView != null) {
            TextView tv = crosshairView.findViewById(R.id.tv_countdown);
            if (tv != null) {
                tv.setText(String.valueOf(n));
                tv.setVisibility(View.VISIBLE);
            }
        }
    }

    private void hideCountdown() {
        if (crosshairView != null) {
            TextView tv = crosshairView.findViewById(R.id.tv_countdown);
            if (tv != null) tv.setVisibility(View.GONE);
        }
    }

    private boolean isTapping() {
        TapAccessibilityService svc = TapAccessibilityService.getInstance();
        return svc != null && svc.isTapping();
    }

    private long speedToInterval() {
        return Math.max(1, Math.round(1000.0 / tapsPerSec));
    }

    private String formatSpeed(float tps) {
        return (int) tps + " tap/sec";
    }
}
