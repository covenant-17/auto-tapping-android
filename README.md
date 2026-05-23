# auto-tapping-android

An Android app that performs automated screen taps at a configurable rate using the Accessibility API.

## Features

- **Floating crosshair overlay** — drag to position the tap target anywhere on screen
- **Adjustable tap speed** — 0.5 to 10 taps per second via a floating settings menu
- **Pause on interaction** — automatically pauses for 3 seconds when you touch the screen, then resumes
- **Auto-stop on lock** — stops tapping when the screen turns off or the device locks
- **Foreground service** — keeps running while you use other apps

## Requirements

- Android 7.0+ (API 24)
- Accessibility Service permission
- Draw over other apps permission

## Setup

1. Install the APK
2. Open the app and tap **Enable Accessibility** → enable *auto-tapping-android* in system settings
3. Tap **Launch Overlay** → grant *Draw over other apps* permission if prompted
4. Drag the crosshair to the desired tap location
5. Tap the crosshair to open the settings menu, set speed, then tap **START**

## Build

```bash
mvn package
```

Produces `target/auto-tapping-android-*.ap_`.

## Permissions

| Permission | Purpose |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw floating crosshair and menu overlay |
| `FOREGROUND_SERVICE` | Keep overlay service alive in background |
| `BIND_ACCESSIBILITY_SERVICE` | Dispatch tap gestures via `GestureDescription` |

## Architecture

| Class | Role |
|---|---|
| `MainActivity` | Entry point; checks and requests permissions |
| `FloatingOverlayService` | Foreground service managing the crosshair and settings overlay |
| `TapAccessibilityService` | Dispatches periodic taps using `GestureDescription` |
