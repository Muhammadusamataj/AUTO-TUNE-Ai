# AutoTune EQ AI 0.3.3 candidate — preserved build and test report

Packaging completed 2026-10-30 UTC from the existing workspace. Builds and tests below ran on 2026-10-29; they were not restarted or repeated during packaging. **This is a candidate, not a final release.** Neither installation on the user's Vivo nor independently audible EQ has been verified.

## Delivered application identity

| Item | Full | Lite |
|---|---|---|
| Package | `com.autotuneeq.mvp` | `com.autotuneeq.mvp.lite` |
| Version | 0.3.3-candidate, code 6 | 0.3.3-candidate, code 6 |
| Minimum / target / compile SDK | 26 / 35 / 35 | 26 / 35 / 35 |
| Review build | Non-debuggable, development-signed complete APK | Non-debuggable, development-signed complete APK |
| SHA-256 | `6e78a1424a822b08b9acb0303bb6815f072ca1dc6018930a142d91d1cca43312` | `27993583555a42b1494b8508f54f554f9de16c61fcf5ad9d3f61547c7966d688` |
| Bytes | 1,539,073 | 1,519,549 |

Both artifacts passed SDK `apksigner verify --verbose --print-certs` with v2 signing and `zipalign -c -P 16 -v 4`. Their bytes still match the existing Gradle review outputs. The shared certificate SHA-256 is `681b22c401daf9fad6557cf343861d3858f085e2e9a9285eb89733372f594cd7` (Android Debug, RSA 2048). It is the same development identity used for the supplied v0.3.1 and v0.3.2 APKs. This rules out a signer mismatch between those supplied artifacts; it does not rule out a differently signed installation on the phone or another profile. A valid development signature is not production signing or Google approval.

Existing AAPT evidence confirms minSdk 26, targetSdk 35, package names and complete manifests. Neither APK contains native `.so` libraries. Lite's manifest and DEX were inspected: the app's NotificationListenerService is absent, as are its monitor service, RECORD_AUDIO, POST_NOTIFICATIONS and foreground-service permissions. Lite retains INTERNET for explicit manual profile lookup and MODIFY_AUDIO_SETTINGS for its own audio effect, plus AndroidX's normal internal receiver-protection permission. Full retains sensitive capabilities with explicit notification-access consent; target SDK and Full security-related permissions were not changed to evade installation policy.

## Input and review of Claude H1–H3

Input: `AutoTuneEQ-0.3.2-H1H2H3-candidate-source.zip`, SHA-256 `5997bb2f5c7b7f88a79c3b4d60db47d9a1035d554417a0b0983e3629d826251d`, and the supplied candidate patch. Applying the patch to the prior source matched the ZIP for all seven touched implementation/test files. Evidence: `docs/logs/v033/patch-apply-check.txt`. The candidate's four standalone Java programs passed before further edits.

Accepted and extended fixes:

- **H1:** retain known effects through pause/buffering/connecting; stop/error/destroy, access loss and CLOSE release them. The candidate considered any live package sufficient, including STOPPED/ERROR; `PlaybackPolicy` now distinguishes states. Switching active players bypasses the previous player's effect and remembers its still-live claim for switch-back without another OPEN.
- **H2:** early OPEN is a bounded, expiring claim. It is attached only when the matching player plays. A conflicting package cannot overwrite a pending claim. Matching CLOSE cancels it before playback and prevents resurrection.
- **H3:** failed attachment uses six exponential-backoff attempts with a ten-minute pending TTL. Duplicate OPEN does not reset attempt/TTL limits. Successful claims remain known for lifecycle handling; failed, closed, expired and revoked claims are cleaned up.
- **Backend overflow:** retained the candidate's OverflowError handling and added a real HTTP regression using a 1,000-digit gain; it returns 400 instead of terminating the handler.

Regression evidence covers pause/resume, buffering/connecting, same-player track switching to a neutral profile, player switching and switch-back, CLOSE, access/stop cleanup, duplicate/conflicting claims and retry budgets. Native-effect lifecycle tests use an explicitly simulated Equalizer; they do not establish acoustic/device behavior.

## Full and Lite architecture

Full-only activity, media monitor, notification listener, session coordinator, hint policy, FFT and spectrum live in `app/src/full`. Lite has its own manifest/activity in `app/src/lite` and a separate package for explicit co-installation. It supports manual title/artist, offline profiles, explicit HTTPS lookup, sliders and this app's own quiet original tone. It does not offer automatic detection, capture/FFT, background monitoring, external-session or legacy-global EQ. It is a reduced product, not Full disguised to bypass an installation block.

The serialized Full service worker owns audio effects, Visualizer and media-session queries. Its main-thread foreground startup meets the platform deadline. `ToneEngine` moves own-tone/Equalizer operations and AudioTrack shutdown to an activity-scoped audio worker and coalesces sliders at 80 ms. Receiver callbacks only parse/capture sender information and enqueue bounded work; native audio IPC is not performed in onReceive. Queued events are capped at 32 and OPEN processing at 32 per ten seconds. Known sender UIDs must match package claims when Android supplies identity. Unknown sender identity remains untrusted and spoofable; active-package matching is not proof of arbitrary audio-session ownership.

Native/OEM Binder calls can still stall a worker and delay serialized cleanup; no hard interruption or hang-free claim is made. Under overload events can be dropped. Package-level media-session liveness is not per-audio-session authentication. See `REMAINING_ISSUES.md`.

## Preserved environment and exact successful commands

Linux x86_64; Temurin JDK 17.0.21+9; Python 3.12.14; Gradle wrapper 8.7; Android Gradle Plugin 8.6.1; SDK platform 35; AGP default Build Tools 34.0.0; inspection tools 35.0.0. Main AndroidX Core 1.13.1; JUnit 4.13.2; org.json 20240303; AndroidX test runner 1.6.2 / ext JUnit 1.2.1. Dependencies are pinned. SDK/JDK, generated keys and host proxy settings remain outside the source archive.

From `/workspace/AutoTuneEQ_v0_3_3`:

```sh
ANDROID_USER_HOME=/workspace/tooling/android-user \
JAVA_HOME=/workspace/tooling/jdk-17.0.21+9 \
ANDROID_HOME=/workspace/tooling/android-sdk \
GRADLE_USER_HOME=/workspace/tooling/gradle-home \
./gradlew --offline assembleFullReview assembleLiteReview assembleFullDebug assembleLiteDebug \
  testFullDebugUnitTest testLiteDebugUnitTest assembleFullDebugAndroidTest assembleLiteDebugAndroidTest \
  lintFullDebug lintLiteDebug lintFullReview lintLiteReview --console=plain \
  > docs/logs/v033/gradle-final.txt 2>&1

PATH=/workspace/tooling/jdk-17.0.21+9/bin:$PATH \
  bash tests/run_core_java_tests.sh > docs/logs/v033/core.txt 2>&1
python3 -m unittest discover -s server -v > docs/logs/v033/backend.txt 2>&1
python3 -m unittest discover -s tools -p 'test_*.py' -v \
  > docs/logs/v033/diagnostic-tool-tests.txt 2>&1
```

Final Gradle output: **BUILD SUCCESSFUL in 15s**, 241 actionable tasks: 29 executed / 212 up-to-date. Previous successful passes/logs are retained. Packaging on 2026-10-30 verified file hashes and ZIP integrity only; it did not rebuild or rerun passing tests.

| Check | Recorded result |
|---|---|
| Full/Lite review and debug APK assembly | PASS |
| Full JUnit | 16 passed: 8 common core + 8 session/policy regressions; 0 failures/errors/skips |
| Lite JUnit | 8 common core tests passed; 0 failures/errors/skips |
| Standalone Java | 5 programs passed, including simulated effect/coordinator lifecycle |
| Backend | 15 passed, real loopback HTTP and temporary SQLite |
| Diagnostic classification tool | 4 passed; generic screenshot remains UNKNOWN |
| Full debug / review lint | 0 errors, 9 warnings each |
| Lite debug / review lint | 0 errors, 11 warnings each |
| Full / Lite instrumented tests | APKs compiled; 3 Full + 2 Lite test methods **not executed** |
| Signature/alignment/manifest checks | PASS for both evaluation APKs |
| Vivo installation / actual audible EQ | **UNVERIFIED** |

Warnings concern pinned-dependency updates and UI localization/text composition; raw lint reports are included. No suppression baseline was used to hide errors. JUnit XML and original test outputs are retained under `docs/test-results/v033` and `docs/logs/v033`.

## Actual Android 16 emulator result

A software-emulated Google APIs x86_64 Android 16 reference image was started without KVM in a container. SDK API query returned **36**. Fingerprint:

```
google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys
```

Initial install attempts ran before readiness and returned `Can't find service: package` or `Error: device is still booting.` Later `sys.boot_completed` returned **1** and `service check package` reported **found**, but subsequent ordinary ADB install attempts still failed at the shell/PackageManager IPC layer:

```
# v0.3.1 attempt after recorded boot completion
Performing Streamed Install
adb: failed to install .../AutoTuneEQ-0.3.1-debug.apk:
cmd: Failure calling service package: Broken pipe (32)

# v0.3.2 attempt after recorded boot completion
Performing Streamed Install
adb: failed to install .../AutoTuneEQ-0.3.2-full-android8.apk:
cmd: Can't find service: package
```

**No successful reference-emulator installation is recorded.** These are unstable test-environment/service errors, not a confirmed APK-specific INSTALL_FAILED result. No v0.3.3 installation success or instrumented execution was obtained or claimed. Prior attempt logs, boot/service queries, fingerprint and raw logcat are preserved in `docs/logs/v033/emulator/`. Long-running emulator setup was not restarted during final packaging.

This reference image has different firmware and security services from a genuine Vivo. Even a reference-image success would not establish Vivo installer or Play Protect acceptance. The user's generic “App not installed” screenshot still contains no PackageManager reason. There are **no actual Vivo ADB logs** in this workspace, so its rejection remains **UNKNOWN**. No target downgrade, certificate replacement, Full permission removal or security bypass was made on that assumption.

## Installation diagnosis and remaining gates

Use the exact commands and evidence collector in `INSTALLATION_DIAGNOSTICS.md` on the **actual Vivo**. The collector preserves raw output and can compare the incoming and installed base-APK certificates; it never uninstalls, clears logs/data, grants permissions or changes verification settings. The essential commands are:

```sh
adb install -r app-review.apk
adb shell pm list packages | grep autotune
adb logcat -d -b all > install-logcat-all.txt
```

Use the Full APK renamed to `app-review.apk`, record SHA-256 first, select the correct phone serial if multiple devices are attached, and preserve complete `Failure [INSTALL_FAILED_...]` output. Windows alternatives and a live capture for Vivo's UI flow are documented. A verifier failure must be attributed from logs, not automatically to Google; a USER_RESTRICTED result must be attributed to the actual policy/UI path. Do not disable Play Protect or bypass device policy.

Normal release variants remain unsigned for the owner to use a stable private upload/release identity and legitimate distribution/review. No Google submission or approval is claimed. Final-release gates remain: successful real-device installation, permission/metadata/background/route stress, original/local tone OFF/ON listening or loopback measurement, and effect cleanup/restoration verification. YouTube, YouTube Music and Spotify remain separately unverified. FFT is diagnostic only; no trained AI, worldwide catalog, calibrated limiter, arbitrary-player-session control or guaranteed system-wide EQ is claimed.

## Audit and delivery

The source ZIP contains all current source/flavors, official wrapper, backend, tests, documentation and preserved logs; it excludes build caches, SDK/JDK, private keys and local configuration. `docs/SOURCE_SHA256SUMS` identifies archive contents except itself. A text patch against the user-supplied H1–H3 ZIP supports review; the full ZIP carries binaries such as the wrapper JAR. The standalone test-log ZIP includes existing build/lint/JUnit/backend/diagnostic and emulator evidence. `APK_IDENTITY.json` and top-level `SHA256SUMS` identify the delivered bytes. Historical reports and original candidate notes remain explicitly historical; they are not current pass claims.
