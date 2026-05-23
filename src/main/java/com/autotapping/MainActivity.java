package com.autotapping;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int REQUEST_OVERLAY_PERMISSION = 100;

    private TextView btnLaunchOverlay;
    private TextView btnAccessibility;
    private TextView tvAccessibilityStatus;
    private TextView tvOverlayStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnLaunchOverlay      = findViewById(R.id.btn_launch_overlay);
        btnAccessibility      = findViewById(R.id.btn_accessibility);
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status);
        tvOverlayStatus       = findViewById(R.id.tv_overlay_status);

        btnAccessibility.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });

        btnLaunchOverlay.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                launchOverlay();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatuses();
    }

    private void launchOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            return;
        }
        if (TapAccessibilityService.getInstance() == null) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        startService(new Intent(this, FloatingOverlayService.class));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            updateStatuses();
        }
    }

    private void updateStatuses() {
        boolean accReady = TapAccessibilityService.getInstance() != null;
        tvAccessibilityStatus.setText(accReady ? "✔ Включён" : "✘ Выключен");
        tvAccessibilityStatus.setTextColor(accReady ? 0xFF7BFF9F : 0xFFFF6B6B);

        boolean overlayOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(this);
        tvOverlayStatus.setText(overlayOk ? "✔ Разрешён" : "✘ Нет разрешения");
        tvOverlayStatus.setTextColor(overlayOk ? 0xFF7BFF9F : 0xFFFF6B6B);

        btnLaunchOverlay.setEnabled(accReady && overlayOk);
        btnLaunchOverlay.setAlpha(accReady && overlayOk ? 1.0f : 0.45f);
    }
}
