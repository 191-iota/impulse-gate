# Impulse Gate

> An Android accessibility service that drops a full-screen wall in front of apps chosen for gating and keeps them hidden until the phrase `i want this` is typed by hand.

![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)

**[Download the APK (v1.1)](https://github.com/lambdaf-org/impulse-gate/releases/latest)** (~620 KB, Android 8.0+)

Your phone is full of apps engineered by very smart people whose paycheck depends on
you opening them. Willpower does nothing against a feed tuned by ten thousand A/B
tests. So stop fighting fair.

A tiny Android app (~600 KB, zero dependencies) that puts a wall between you and your
impulses. You pick which apps to gate; from then on, opening one of them shows a
fully opaque full-screen overlay **before you can see anything**, and the app stays
hidden until you type:

```
i want this
```

Putting the app away, by going to the home screen or recents, re-arms the gate, so the
next open asks for the phrase again. Stepping out for a moment into the keyboard, a
permission dialog, the camera, or a picker does not re-lock the session.

<p align="center">
  <img src="screenshots/gate.png" width="280" alt="The gate over Chrome" />
  <img src="screenshots/picker.png" width="280" alt="App picker" />
</p>

Verified end-to-end on an Android 15 emulator, from the opaque gate through phrase
unlock and re-lock on leave.

## Quickstart

```bash
git clone https://github.com/lambdaf-org/impulse-gate
cd impulse-gate
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=$HOME/Library/Android/sdk
./gradlew assembleRelease
```

Building requires JDK 17 and the Android SDK (platform 35). The APK lands at
`app/build/outputs/apk/release/app-release.apk`, debug-signed so it sideloads directly.

Install it with adb:

```bash
$ANDROID_HOME/platform-tools/adb install app/build/outputs/apk/release/app-release.apk
```

Then open the app, tick the apps to gate, tap ENABLE, and turn on the service under
Accessibility settings.

> **Android 13+ note:** if the APK was installed from a file manager or browser rather
> than adb, Android blocks the accessibility toggle for sideloaded apps. Go to
> *Settings, Apps, Impulse Gate, top-right menu, Allow restricted settings*, then enable
> the service. Installing via `adb install` avoids this.

## Features

- **Phrase gate**: a gated app opens behind an opaque full-screen overlay, hidden until the exact phrase `i want this` is typed (whitespace and case are normalized).
- **Typed entry, paste blocked**: the input field blocks autofill and the long-press menu, and a clipboard-sized text jump (8 or more inserted characters) gets wiped, so the phrase has to be typed by hand.
- **Per-app, per-visit unlock**: typing the phrase unlocks that one app until you put it away. Each gated app is tracked separately, and going to the home screen or recents re-arms the gate; briefly stepping into the camera, a picker, or another app does not.
- **No screen reading**: the service declares `canRetrieveWindowContent="false"` and reads nothing from the screen. It only listens for window-state changes.
- **No overlay permission**: it draws a `TYPE_ACCESSIBILITY_OVERLAY` window, which an accessibility service may add without the "draw over other apps" grant.
- **Zero dependencies**: the whole app is two Kotlin files building Views in code, with no third-party libraries.

## How it works

One `AccessibilityService` listens for `TYPE_WINDOW_STATE_CHANGED` events. When a gated
package reaches the foreground, it attaches an opaque, full-screen overlay window with no
animation, so nothing of the app shows through. The Back button is swallowed inside the
overlay, and Home stays as the way out. Stepping out of a gated app for a moment, whether
into a permission dialog, a share sheet, the volume panel, the camera, a photo picker, or
another app, is not treated as leaving, so the unlock survives it. Only going to the home
screen or the recents view re-arms the gate, so the next deliberate open asks for the phrase.

The overlay is rebuilt on rotation and on fold or unfold, and the service swallows its own
exceptions so a single failure cannot get it disabled by the system. If an aggressive OEM
battery manager force-kills the service while a gated app is open, the gate drops until
the system re-binds the service and re-fires the window event, which on stock Android
self-restores in about a second.

The chosen apps are stored in `SharedPreferences`. There are no environment variables or
config files to set.

## Notes and known limits

- The overlay appears the moment the system reports the window change, which is effectively instant, though a sub-100 ms flash of the app is inherent to the accessibility approach on some devices.
- The recents screen shows the gated app's real thumbnail, because Android snapshots the app's own surfaces and an external overlay cannot be part of that snapshot.
- This is self-discipline software. The service can always be disabled or the app uninstalled. The gate only needs to make opening an app a conscious act.

## Contributing

See [lambdaf-org/contributing](https://github.com/lambdaf-org/contributing).

## License

MIT, see the [LICENSE](LICENSE) file.