# Impulse Gate

**[⬇ Download the APK (v1.0)](https://github.com/191-iota/impulse-gate/releases/latest)** — ~620 KB, Android 8.0+.

A tiny Android app (~600 KB, zero dependencies) that puts a wall between you and your
impulses. You pick which apps to gate; from then on, opening one of them shows a
fully opaque full-screen overlay **before you can see anything**, and the app stays
hidden until you type:

```
i want this
```

Leave the app (home, recents, switch away) and the gate re-arms — next open, you type
again. Transient things (keyboard, permission dialogs, share sheet, notification shade)
do **not** re-lock you mid-session.

<p align="center">
  <img src="screenshots/gate.png" width="280" alt="The gate over Chrome" />
  <img src="screenshots/picker.png" width="280" alt="App picker" />
</p>

Verified end-to-end on an Android 15 emulator: gate appears opaque over a gated app,
typing the phrase unlocks, leaving and reopening re-arms it.

## How it works

One `AccessibilityService` listens for window changes. When a gated app reaches the
foreground it attaches a `TYPE_ACCESSIBILITY_OVERLAY` window — opaque, full-screen, no
animation, Back-proof (Home is the escape hatch). No "draw over other apps" permission
needed; accessibility services may add these overlays directly. The service reads
nothing from your screen (`canRetrieveWindowContent="false"`).

## Build

Requires JDK 17 and the Android SDK (platform 35). On this machine both are installed.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=$HOME/Library/Android/sdk
./gradlew assembleRelease
```

APK lands at `app/build/outputs/apk/release/app-release.apk` (debug-signed, sideloadable).

## Install & set up

1. Enable USB debugging on the phone, then:
   ```bash
   $ANDROID_HOME/platform-tools/adb install app/build/outputs/apk/release/app-release.apk
   ```
   (Or copy the APK over and open it on the phone.)
2. Open **Impulse Gate**, tick the apps you want gated.
3. Tap **ENABLE** → Accessibility settings → Impulse Gate → turn it on.

> **Android 13+ note:** if you installed the APK from a file manager or browser
> (not adb), Android blocks the accessibility toggle for sideloaded apps. Fix: go to
> *Settings → Apps → Impulse Gate → ⋮ (top right) → Allow restricted settings*, then
> enable the service. Installing via `adb install` avoids this entirely.

## Notes & known limits

- The overlay appears the moment the system reports the window change — practically
  instant, though a sub-100 ms flash of the app is inherent to the accessibility
  approach on some devices.
- Typing the phrase unlocks that app until you leave it. Each gated app is unlocked
  separately.
- Pasting the phrase doesn't count: autofill is off, the paste menu is disabled, and
  clipboard-sized text jumps are wiped. Type it by hand — that's the point.
- The recents screen shows the gated app's real thumbnail (Android snapshots the app's
  own surfaces; an external overlay can't be part of it). Inherent to this approach.
- If Android force-kills the service (aggressive OEM battery management) while a gated
  app is open, the gate drops until the service auto-rebinds — on stock Android the
  system re-fires the window event on rebind and the gate self-restores in about a
  second. Rare and self-healing; accepted for v1 rather than requesting the
  "read screen content" capability a hard guarantee would need.
- This is self-discipline software, not a parental lock: you can always uninstall it
  or disable the service. Friction is the point, not security.
