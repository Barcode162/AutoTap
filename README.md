# AutoTap

A floating auto-clicker for Android. Place movable dots anywhere on screen and
it taps them for you, with a small collapsible control bubble that sits over
every app.

## Features
- **Floating bubble ↔ panel** — tap the bubble to open controls, collapse back to a bubble.
- **Movable dots** — add up to 12 dots, drag each one to where you want taps.
- **Speed** — 1–50 CPS slider (clicks per second, per dot).
- **Two modes:**
  - **Toggle** — press START to click continuously. Keeps running even if you
    collapse the menu (the bubble turns red = armed). Press STOP to end.
  - **Hold** — press and hold the HOLD pad to click; release to stop. Collapsing
    the menu stops hold-clicking (only the Toggle's armed state survives collapse).
- Dots become **tap-through** while clicking so the synthetic taps reach the app
  beneath. Move dots while clicking is stopped.

## How it works
The taps are injected via an **AccessibilityService** (`dispatchGesture`) — the
standard, non-root way to tap other apps. The service also hosts the overlay
(`TYPE_APPLICATION_OVERLAY`), so no foreground service is needed. It requests
**gestures only** — it does not read screen content.

## Permissions (granted once)
1. **Display over other apps** (overlay).
2. **Accessibility → AutoTap Clicker** (tap output).

## Build
```
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; $env:ANDROID_HOME='C:\Users\Barcode\AppData\Local\Android\Sdk'
cd G:\AutoTap; .\gradlew.bat assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Project layout
```
app/src/main/java/com/joshua/autotap/
  MainActivity.kt                permission flow + "show controls"
  ClickAccessibilityService.kt   gesture injection + click loop + state
  OverlayUi.kt                   bubble/panel + movable dots + drag
```

## Note
General-purpose utility. Auto-clicking inside online games may violate that
game's terms of service — that's on you to check.
