> Current target: 0.3.3 candidate Full/Lite. Old entries below are historical test plans, not observed passes. See current BUILD_AND_TEST_REPORT.md for actual results.

# Android audio compatibility test plan — v0.3.2

Test device: SAM5355 / MT6580 / API 27 reported by CPU-Z, or another Android phone.

**Safety:** Park car, start at low sound volume, do not root/flash, and do not change MCU settings. Inform driver about testing.

## Preparation

- Verify device API: `adb shell getprop ro.build.version.sdk` (optional, only if USB debugging is enabled).
- Build APK with Android Studio using README instructions, install, allow the requested notification listener.
- `RECORD_AUDIO` is optional; it only powers the low-resolution spectrum. Leave cloud URL empty for offline privacy.
- Start monitor explicitly. Note visible app version, Android API, app used for playback.

## Matrix for each music source

Test: own test tone, offline third-party player, YouTube main app, YouTube Music, Spotify. For each record:

1. Whether playback metadata appears (song, artist, package).
2. Whether low-res spectrum moves (it can fail independently of metadata).
3. Whether per-app `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` causes attachment after playback restart (experimental mode, explicit ON).
4. Whether effect changes *audible/measured audio*, not just Android effect object's `hasControl` state.
5. Whether EQ OFF and player switch restore initial sound, and whether a new song gets its own stored profile.
6. Whether service notification survives app switching, device sleep, and 10+ minutes of playback.

| Player | Metadata | Spectrum | Session event | EQ audible? | Safe cleanup? |
|---|---|---|---|---|---|
| Own test tone | N/A | ? | N/A | ? | ? |
| Local media player | ? | ? | ? | ? | ? |
| YouTube | ? | ? | ? | ? | ? |
| YouTube Music | ? | ? | ? | ? | ? |
| Spotify | ? | ? | ? | ? | ? |

`?` means untested. Fill this table only from observed results.

## Optional ADB diagnostics (developer only)

```bash
adb shell getprop ro.build.version.sdk
adb shell dumpsys media_session
adb shell dumpsys media.audio_flinger
adb logcat -d -t 1000 | grep -Ei 'autotune|visualizer|equalizer|audioeffect|AudioFlinger'
```

`dumpsys` output may be restricted/redacted. It is a *computer-side diagnostic*, not a permission granted to the app. Never install an unsupported custom ROM merely to enable it.

## Revision regression checks (all hardware checks still untested)

- API 26 Android 8.0, API 27 ARMv7 SAM5355 plus a recent API 34/35 phone: deny/grant/revoke notification access and audio permission; return from Settings; listener disconnect/reconnect. Confirm metadata callbacks and 2.5-second polling recover without duplicate callbacks.
- Test display-title and album-artist fallback, multiple playing sessions, artist absent, rapid A→B→A changes, pause, player switch, process kill and sticky restart. Experiments must remain off on process restart.
- Start/stop tone rapidly; leave activity; rotate; change route/Bluetooth; introduce navigation/call audio. Confirm no writer survives stop and no session effect remains.
- External experiment: malformed/negative/zero session IDs, wrong package OPEN/CLOSE, conflicting session claims, more than eight sessions, permission revocation, loss of control. Optional events arriving before playback is active may be rejected; restart playback if needed. Session extras do not authenticate ownership, especially on API 27.
- First save a manual profile while a lookup is delayed, then change tracks or disable the endpoint. A late reply must not replace the manual profile or current song. Try offline relaunch, corrupted cache and eviction past 200 writes.
- Fetch unknown track from empty backend: neutral profile, clear not-found status. Reject wrong recording, strings/NaN/oversized gains, non-JSON, redirects, oversized or slow replies and non-HTTPS endpoints.
- Record attached/control/applied states separately from listening or loopback measurements. Confirm bypass/restoration after disable/stop/source change; restoration is best effort when the player has already destroyed its effect/session.
- At 1280×720 and on a phone, verify scrolling, touch targets, font scaling and system-bar insets. Never perform touchscreen tests while driving.

The supplied instrumentation tests cover platform JSON and cache persistence/corruption only. They do not measure sound or establish player compatibility. Execute with `./gradlew connectedDebugAndroidTest` after connecting an authorized test device.

## Android 8.0 and installation regression

- Confirm the packaged minimum is API 26. On API 26, grant/revoke the exact notification-listener component in system settings; the API 27-only NotificationManager method must never execute. Exercise full/short component names and a similarly named unrelated package.
- Confirm notification-access disclosure appears before system Settings and Cancel does not request access. Notification messages and OTPs must not be inspected; only public media-session metadata is used by the app.
- Install the full evaluation APK through the normal supported installer and record any Google Play Protect detection; do not record a successful build/signature as successful installation or security approval. If blocked, use the owner review/distribution steps in PLAY_PROTECT_REVIEW.md.

## 0.3.3 gates

Test Full and Lite independently on API 26, API 27 head unit and Vivo API 36. Full: early OPEN, duplicate/conflicting OPEN, six failed retries, pause/resume, buffering/connecting, neutral track switch, player switch and switch-back without OPEN, matching/wrong-package CLOSE, stopped/error/destroy, access loss, disable and process restart. Verify no native effect IPC on the main thread using a profiler and vendor-route stress. Lite: inspect installed permissions/services, enter identities manually, save/reload offline, cancel stale HTTPS requests, test own tone lifecycle and sliders. Record actual audible/loopback processing separately from effect constructor/control/status. Keep Play Protect enabled; capture exact PackageManager errors for any install failure.
