# AutoTune EQ 0.3.1 — build and test report

Date: 2026-10-29 UTC. Input: `AutoTuneEQ_v0_3_Review_Fixes_Source.zip` and `CODEX_AUTOTUNEEQ_TASK.md`. The supplied project was edited in place; it was not replaced with a demo. All supplied Java, backend, Gradle, manifest, test and documentation files were inspected.

## Result

**Debug application APK built successfully.** Final command completed with `BUILD SUCCESSFUL in 5s`, 71 actionable tasks, 14 executed / 57 up-to-date. Eight JUnit tests and fourteen backend tests passed, along with three standalone Java smoke/fault-injection programs. Lint completed with **0 errors / 13 warnings**. Instrumentation APK compiled, but **no instrumented Android test executed**. No physical Android device, emulator or acoustic verification was available.

This is a buildable experimental MVP, not verified system-wide EQ. YouTube, YouTube Music, Spotify, the app's own tone, and the SAM5355/MT6580 panel remain **unverified for audible EQ**.

Application: `com.autotuneeq.mvp`, version `0.3.1-alpha`, versionCode `4`, minSdk **27**, targetSdk/compileSdk **35**. No ABI filters or native DSP binaries are introduced. ARMv7 API 27 runtime behavior still needs the physical panel test.

Application APK SHA-256:

```
a24c80529617d8cd84997700184e607b27433770fdde2cb2ddf33cf64dd3d485
```

Instrumentation APK SHA-256:

```
d38216e88b674812100a8fa59ee529774d199a969eba2cf4d59b1e33bd80f8c5
```

Debug APK signature was checked with SDK `apksigner verify --verbose`: **Verifies**, APK Signature Scheme v2 true, one signer. AAPT badging confirms minSdk 27 / target 35. This verifies packaging and signature, not installation or device behavior. APKs use a generated development debug key, not a release identity.

## Environment and setup

- Linux x86_64; Python 3.12.14.
- Initial system Java: runtime-only OpenJDK 21; `javac`, Gradle, Android SDK and ADB were not initially available on PATH.
- Build Java: downloaded Eclipse Temurin **17.0.21+9**.
- Android Gradle Plugin **8.6.1**; Gradle **8.7**.
- SDK command-line tools archive `commandlinetools-linux-11076708_latest.zip` (tools 12.0); Android SDK Platform 35 revision 2; Build Tools **34.0.0** used by AGP, also installed 35.0.0 for packaging checks; platform-tools 37.0.1.
- Official Gradle v8.7.0 wrapper scripts/JAR included. Wrapper JAR SHA-256: `cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8`.
- Wrapper distribution SHA-256 pinned: `544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d`, independently matched to downloaded distribution.
- Main dependency: AndroidX Core 1.13.1 for receiver registration compatible with API 27 and newer. Tests: JUnit 4.13.2, real org.json 20240303; AndroidX Test runner 1.6.2 / ext JUnit 1.2.1. Versions are fixed, not dynamic. No release secrets or backend admin keys are shipped.

Tooling was installed under `/workspace/tooling`, outside the delivered source. The environment proxy required sandboxed network access and the system Java CA trust store. Host-specific proxy/trust settings were kept in `/workspace/tooling/gradle-home/gradle.properties`, not embedded in project configuration:

```
systemProp.http.proxyHost=proxy
systemProp.http.proxyPort=8080
systemProp.https.proxyHost=proxy
systemProp.https.proxyPort=8080
systemProp.javax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts
org.gradle.workers.max=2
```

The environment's default Android preferences directory was read-only. `ANDROID_USER_HOME` was redirected to the workspace for Gradle/SDK tooling. SDK setup and early failed build logs are preserved in `docs/logs/`. Two SDK installers briefly installed duplicate platform directories; this was an environment setup warning, not a source dependency or build failure.

## Exact final build/check commands

From `/workspace/AutoTuneEQ_v0_3`:

```sh
ANDROID_USER_HOME=/workspace/tooling/android-user \
JAVA_HOME=/workspace/tooling/jdk-17.0.21+9 \
ANDROID_HOME=/workspace/tooling/android-sdk \
GRADLE_USER_HOME=/workspace/tooling/gradle-home \
./gradlew assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug --console=plain \
  > docs/logs/gradle-final.txt 2>&1

PATH=/workspace/tooling/jdk-17.0.21+9/bin:$PATH \
  bash tests/run_core_java_tests.sh > docs/logs/core-java.txt 2>&1
python3 -m unittest discover -s server -v > docs/logs/backend.txt 2>&1

JAVA_HOME=/workspace/tooling/jdk-17.0.21+9 \
/workspace/tooling/android-sdk/build-tools/35.0.0/apksigner verify --verbose \
  app/build/outputs/apk/debug/app-debug.apk > docs/logs/apk-signature.txt 2>&1
/workspace/tooling/android-sdk/build-tools/35.0.0/aapt dump badging \
  app/build/outputs/apk/debug/app-debug.apk > docs/logs/apk-badging.txt 2>&1
```

Use normal local SDK/JDK paths on another machine; environment-specific proxy settings are unnecessary unless that machine requires them. See README for portable commands.

Attempted Android execution:

```sh
ANDROID_USER_HOME=/workspace/tooling/android-user \
JAVA_HOME=/workspace/tooling/jdk-17.0.21+9 \
ANDROID_HOME=/workspace/tooling/android-sdk \
GRADLE_USER_HOME=/workspace/tooling/gradle-home \
timeout 45 ./gradlew connectedDebugAndroidTest --console=plain \
  > docs/logs/instrumentation-attempt.txt 2>&1
```

This failed in 17s at `connectedDebugAndroidTest`: `Could not create ADB Bridge` / `Could not determine adb version`. Direct ADB attempts aborted trying to create `/home/agent/.android` on a read-only filesystem. `ANDROID_USER_HOME` and `ANDROID_SDK_HOME` did not redirect this ADB binary's preferences directory. No device was supplied; `/dev/kvm` is absent. This is **blocked/untested**, never a device-test pass. The supplied test APK can run later on an authorized device.

## Test evidence

| Check | Result | Scope |
|---|---|---|
| `assembleDebug` | PASS | Java/resources/D8/package/debug signature |
| `testDebugUnitTest` | PASS: 8 tests, 0 failures/errors/skips | Real JSON parser, hostile values, endpoint validation, recording identity, log interpolation, cut-only shifts, hardware clamp/range rejection, neutral FFT fallback |
| `tests/run_core_java_tests.sh` | PASS: 3 programs | Existing clamp/FFT checks plus simulated hardware lifecycle faults; JSON/Equalizer stubs are test-only |
| Python unittest | PASS: 14 tests | Real loopback HTTP + temporary SQLite; GET/POST, empty lookup, auth fail-closed, Unicode auth, malformed bodies/gains, SQL input, size/source validation, normalization and distinct remixes |
| `assembleDebugAndroidTest` | PASS (compilation only) | Builds 2 platform JSON/cache integration tests |
| `connectedDebugAndroidTest` | BLOCKED; 0 tests run | ADB bridge could not initialize; no device verification |
| `lintDebug` | PASS: 0 errors, 13 warnings | Full text in `docs/logs/lint-final.txt` |
| `apksigner verify` | PASS | Development v2 APK signature; no release certification |
| Device installation, UI, metadata lifecycle, acoustic/DSP response | UNTESTED | Requires API 27 panel and recent phone |
| Actual deployed HTTPS server transport/cancellation stress | UNTESTED | Endpoint unit tests and local backend HTTP tests do not establish remote integration |

JUnit XML is retained in `docs/test-results/`. Lint warnings concern pinned dependency updates and untranslated/concatenated English UI strings. They were not suppressed by a baseline. The receiver error was fixed using explicit AndroidX `RECEIVER_EXPORTED` registration, required because cooperative player events originate in other apps.

Early logs preserve initial `javac: command not found`, proxy/certificate issues, read-only Android preferences, one callback-field forward-reference compile error during implementation, and the first lint failure. Those failures were corrected before the final build. `baseline-backend.txt` runs the original nine test cases; the expanded final suite is `backend.txt`.

## Source changes

| Files | Changes / reason |
|---|---|
| `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `.gitignore` | Official wrapper and checksum pin; reproducible JDK17/AGP8.6.1/Gradle8.7 setup; ignore machine-local/generated files |
| `app/build.gradle` | Keep API 27/35; bump version; add AndroidX receiver support, real JUnit/JSON tests and instrumentation runner |
| `SongMonitor.java` | Public media-session callbacks plus 2.5s fallback polling; register/unregister across permission changes; display-title/album-artist fallback; most recently updated playing session selection; safe exceptions and shutdown |
| `MonitorService.java` | Serialize state on main handler; cancel/version cloud lookups; retain manual edits even before reload intent arrives; bound hints/effects; validate current player, content and optional API34 sender UID; reject malformed extras; package-matched CLOSE; clear hints/effects on source loss; refresh control status; separate FFT status; prevent per-app/legacy stacking; callback cleanup |
| `EqController.java` | Snapshot and best-effort restore external/legacy effects; structured success results; refuse conflicting package/session replacement; compute/clamp all levels before touching hardware; bypass during initial preparation, enable after cut-only settings; release/restore on failed apply, enable or control loss; no inference of audible processing |
| `EqMath.java` | Reject hardware ranges with no safe attenuation range; retain tested log-frequency interpolation and whole-curve headroom shift |
| `TonePlayer.java` | Per-run atomic stop token prevents old writers reviving on restart; nonblocking writes with partial-write handling; bounded join before AudioTrack release |
| `OutputAnalyzer.java` | Reinitialization generation guard, fresh smoother, valid capture rate, return-code checks, stale-frame suppression and native resource cleanup |
| `CloudClient.java` | HTTPS-origin validation; bounded latest-request executor; cancellation/disconnect; connect/read and total-response deadline checks; strict recording match/JSON content type, 16 KiB byte cap, redirects refused; no audio or admin secret |
| `Profile.java`, `TrackIdentity.java` | Reject nonnumeric/nonfinite/out-of-range JSON rather than silently accepting hostile data; source bound; Unicode/whitespace normalization; matched promo delimiters; preserve remix/remaster versions; collision-safe cache key and conservative artist identity |
| `ProfileStore.java` | Remove synchronous `commit()` from UI path; cap at 200 writes, oldest-write eviction; remove corrupt entries; conservatively migrate ordinary v1 profiles and valid endpoint consent |
| `MainActivity.java`, manifest, resource XML | Persistent own-effect/FFT/profile status, honest curated-profile labels and cut-only explanation; stop service directly; tone cleanup on pause; Android15 system-bar insets; protected system listener binding; local launcher vector; explicit backup/transfer exclusions |
| `server/server.py`, `server/test_server.py` | Match Android canonical recording normalization, retain remix distinctions; socket timeout, transfer-encoding rejection; expanded adversarial/regression tests |
| `app/src/test/*`, `app/src/androidTest/*`, `tests/*` | Real-parser JUnit tests, platform cache smoke tests, deterministic effect fault injection; fake effects are never included in APK |
| README, Roman Urdu starter, device plan, report/logs | Current build commands, results, migration/privacy details and safe device matrix; older review/roadmap marked historical |

The supplied empty catalog remains empty in the deliverable; test data lives only in temporary test databases. No fake worldwide profiles, trained model, raw audio collection, privileged permissions, hidden API dependencies, backend write credentials, root/firmware changes or DRM workarounds were added.

## Remaining limitations and compatibility gates

- API 27 is configured and checked by build/lint, but API 27 ARMv7 installation, OEM behavior, Bluetooth/routes, UI scaling and foreground-service survival are untested. A special-use foreground service still needs policy/release review; compiling it is not Play approval.
- Metadata requires user-enabled notification listener access and cooperative media-session metadata. Missing metadata and ambiguous multiple playback sources remain possible. No fingerprinting is implemented. Callbacks and permission/kill/reconnect behavior require device validation.
- Audio session OPEN/CLOSE broadcasts are optional. The app cannot discover arbitrary YouTube/Spotify sessions. It rejects OPEN before an active matching player exists; a cooperative player may need playback restarted. This deliberately trades compatibility for conservative attachment.
- Package extras are unauthenticated. API34 sender identity is checked **only if supplied by the platform/sender**; API27 has no equivalent authenticated package-to-session binding here. Active-session matching, bounds and conflict checks reduce abuse, but do not establish session ownership. Keep external EQ off unless intentionally testing a cooperative player. There is no universal external EQ support claim.
- `Effect attached`, `control granted` and `EQ applied (not independently verified)` are software states. No UI code marks an effect as independently audible/verified. Actual processing may be bypassed by OEM DSP, routes or players. Legacy session zero is deprecated and default OFF.
- Settings restoration is best effort when control is lost or a session is already destroyed. No output limiter, calibrated loudness compensation, speaker/cabin calibration or validated mastering inference exists. The FFT is display-only.
- Visualizer session zero may be unavailable even with audio permission. No DUMP grant or microphone capture fallback is assumed. Permission and FFT errors do not block metadata/profile use.
- Cache is SharedPreferences, capped at 200 profiles, not Room/LRU; no TTL. Manual entries can eventually be evicted and asynchronous persistence can be lost on abrupt power failure. Ambiguous legacy identities are retained in old preferences but not reused automatically. Older server catalogs need reimport under new canonical keys.
- HTTPS origin configuration is explicit metadata-sharing consent; no privacy-policy/store release work is complete. Existing cached profiles avoid repeated requests; retries/TTL refresh and production backend hosting/rate limits/worker bounds are not implemented. No live public HTTPS deployment was tested.
- UI is English and scrollable, with insets handled in code. Car-panel touch sizing, accessibility/font scaling and Android15 edge-to-edge appearance remain device-test gates. The test tone stops on activity pause; audio-focus/call/route behavior still needs hardware review.

## Safe physical-device matrix

Start parked, low volume; confirm `adb shell getprop ro.build.version.sdk` when permitted. Test on the API 27 panel and a modern phone independently. Record software states separately from audio measurements.

| Source | Metadata | FFT | Session event/control | Audible/measured EQ | Cleanup |
|---|---|---|---|---|---|
| Own app tone | N/A, known generated signal | Untested | Own session path compiled; hardware untested | **Unverified** — baseline listening/measurement required | Untested on hardware |
| Cooperative local player | Untested | Untested | Untested; optional event | **Unverified** | Untested |
| YouTube | Untested | Untested | Unsupported unless a usable cooperative path is observed | **Unverified** | Untested |
| YouTube Music | Untested | Untested | Unsupported unless a usable cooperative path is observed | **Unverified** | Untested |
| Spotify | Untested | Untested | Unsupported unless a usable cooperative path is observed | **Unverified** | Untested |

For each: test pause/resume, song/player switch, permission denial/regrant, output route change, screen off, process restart, experiment OFF and effect restoration. Measure controlled EQ OFF/ON using known original/local content and a repeatable listening/loopback setup. A constructor or `hasControl()` success alone never fills the audible column. See `DEVICE_TEST_PLAN.md` for regressions and diagnostics.

## Deliverables and audit

The full revised ZIP contains the same `AutoTuneEQ_v0_3/` project root, wrapper, backend, tests, documentation and raw logs. Generated build caches, local SDK/JDK, local paths, debug keystore and databases are excluded. Application and instrumentation APKs are separate artifacts. A text changes patch accompanies the ZIP; the binary wrapper JAR is included in the full ZIP and identified by hash above. `docs/SOURCE_SHA256SUMS` identifies every delivered source/document/log file except itself. Top-level `SHA256SUMS` covers deliverable archives, APKs, patch and standalone report.
