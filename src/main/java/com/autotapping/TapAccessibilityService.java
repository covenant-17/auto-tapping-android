package com.autotapping;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

public class TapAccessibilityService extends AccessibilityService {

    private static TapAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable tapRunnable;
    private volatile boolean isTapping = false;
    private float lastX = 0, lastY = 0;
    private long lastIntervalMs = 500;

    public static TapAccessibilityService getInstance() {
        return instance;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        stopTapping();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // not used in MVP
    }

    @Override
    public void onInterrupt() {
        stopTapping();
    }

    public void startTapping(float x, float y, long intervalMs) {
        stopTapping();
        isTapping = true;
        lastX = x;
        lastY = y;
        lastIntervalMs = intervalMs;

        tapRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isTapping) return;
                performTap(x, y);
                handler.postDelayed(this, intervalMs);
            }
        };

        handler.post(tapRunnable);
    }

    public void updateTarget(float x, float y) {
        // update coordinates by restarting with current interval
        if (isTapping && lastIntervalMs > 0) {
            startTapping(x, y, lastIntervalMs);
        }
        lastX = x;
        lastY = y;
    }

    public boolean isTapping() {
        return isTapping;
    }

    public float getLastX() { return lastX; }
    public float getLastY() { return lastY; }
    public long getLastIntervalMs() { return lastIntervalMs; }

    public void stopTapping() {
        isTapping = false;
        if (tapRunnable != null) {
            handler.removeCallbacks(tapRunnable);
            tapRunnable = null;
        }
    }

    private void performTap(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 1); // 1ms — minimum tap duration

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();

        dispatchGesture(gesture, null, null);
    }
}
