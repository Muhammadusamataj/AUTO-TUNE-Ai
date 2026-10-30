# AutoTune EQ 0.3.2: full app, Android 8.0+, installation review

Date: 2026-10-29 UTC. This follow-up retains the complete app after the user reported a Google Play Protect installation block, chose “full app,” and explicitly requested support from Android 8.

## Outcome

**The full evaluation APK, debug APK and unsigned release app bundle compiled successfully.** Minimum SDK is now **26 / Android 8.0**; target and compile SDK remain 35. Package remains `com.autotuneeq.mvp`, version **0.3.2-alpha**, versionCode **5**.

**The installation block is not verified as resolved.** No Google approval was obtained, no review was submitted, and no physical device installation was available. Signing/build success does not establish Play Protect acceptance. Full notification-listener metadata detection is retained, so the app still requests sensitive access. See [PLAY_PROTECT_REVIEW.md](PLAY_PROTECT_REVIEW.md) for the owner signing/distribution/appeal process.

The original task specified minimum API 27. The user's latest explicit Android 8 request supersedes that constraint; API 26 compatibility is addressed in source and lint. API 26 and API 27 hardware behavior and audible effects remain unverified.

## Changes since 0.3.1

- `app/build.gradle`: minSdk 27 → **26**, versionCode 4 → **5**, versionName **0.3.2-alpha**. Added a **review** build type inheriting release behavior (`debuggable=false`) and signed using the existing **development** certificate. Normal release remains unsigned so the owner can use their own production/upload signing identity.
- `SongMonitor.java`: guard the API 27 `NotificationManager.isNotificationListenerAccessGranted` call. On API 26, read enabled-listener settings and compare exact parsed `ComponentName` values, including short-form component names. Permission read failures remain fail-closed.
- `MainActivity.java`: show a prominent consent/privacy disclosure before opening notification access Settings; Cancel does not open Settings. Retain Settings fallback. Update Android/version labels.
- `PlatformSmokeTest.java`: add exact listener-component matching tests for the API 26 fallback. The three platform test methods compiled but **did not execute** on Android.
- README, Roman Urdu instructions and device test plan now describe Android 8.0, the evaluation build, owner release signing and Play Protect review. Earlier report remains available as [BUILD_AND_TEST_REPORT_0_3_1.md](BUILD_AND_TEST_REPORT_0_3_1.md) with the original broader fix/test details.

No full-app capability was removed. Metadata callbacks/fallback polling, local profiles, HTTPS profile lookup, offline cache, diagnostic FFT, own-tone cut-only EQ and opt-in external/legacy experiments remain. Package identity, declared sensitive access and Android security checks were not hidden or altered to evade detection. No app data, audio, signing secrets or review submission was uploaded.

## Environment and exact checks

Linux x86_64, Eclipse Temurin **17.0.21+9**, Python **3.12.14**, Gradle wrapper **8.7**, AGP **8.6.1**, compile platform **35**, AGP default Build Tools **34.0.0**, packaging inspection tools **35.0.0**. Existing pinned dependencies were cached, so builds used `--offline`.

From the project root:

```sh
ANDROID_USER_HOME=/workspace/tooling/android-user \
JAVA_HOME=/workspace/tooling/jdk-17.0.21+9 \
ANDROID_HOME=/workspace/tooling/android-sdk \
GRADLE_USER_HOME=/workspace/tooling/gradle-home \
./gradlew --offline assembleDebug assembleReview bundleRelease testDebugUnitTest \
  assembleDebugAndroidTest lintDebug lintReview --console=plain \
  > docs/logs/gradle-android8-retry.txt 2>&1

PATH=/workspace/tooling/jdk-17.0.21+9/bin:$PATH \
  bash tests/run_core_java_tests.sh > docs/logs/core-java-android8.txt 2>&1
python3 -m unittest discover -s server -v > docs/logs/backend-android8.txt 2>&1

JAVA_HOME=/workspace/tooling/jdk-17.0.21+9 \
/workspace/tooling/android-sdk/build-tools/35.0.0/apksigner verify --verbose --print-certs \
  app/build/outputs/apk/review/app-review.apk
/workspace/tooling/android-sdk/build-tools/35.0.0/aapt dump badging \
  app/build/outputs/apk/review/app-review.apk
/workspace/tooling/android-sdk/build-tools/35.0.0/aapt dump xmltree \
  app/build/outputs/apk/review/app-review.apk AndroidManifest.xml
/workspace/tooling/jdk-17.0.21+9/bin/jarsigner -verify \
  app/build/outputs/bundle/release/app-release.aab
```

The first sandboxed attempt failed before compilation with `Could not determine a usable wildcard IP for this machine`; log preserved in `gradle-android8.txt`. Retry used network permission for Gradle's local daemon communication. It completed **BUILD SUCCESSFUL in 1m 40s**, **158 actionable tasks: 126 executed, 32 up-to-date**. A non-fatal metrics warning references the environment's read-only default Android directory. The configured workspace Android directory supplies the evaluation signing key; that private key is not in the source ZIP.

## Test results

| Check | Result |
|---|---|
| Full non-debuggable evaluation APK (`assembleReview`) | PASS |
| Debug APK (`assembleDebug`) | PASS |
| Unsigned release AAB (`bundleRelease`) | PASS; owner must sign before submission |
| JUnit (`testDebugUnitTest`) | **8 passed**, 0 failures/errors/skips; XML retained |
| Standalone Java tests | **3 programs passed**; includes simulated effect fault injection |
| Backend unittest | **14 passed**, real loopback HTTP + temporary SQLite |
| Instrumentation APK (`assembleDebugAndroidTest`) | Compiled, **3 test methods not executed** |
| Debug lint | **0 errors, 13 warnings** |
| Review lint | **0 errors, 10 warnings** |
| `apksigner` | **Verifies**, APK Signature Scheme v2 true |
| AAPT | minSdk **26**, targetSdk **35**, versionCode **5**; no debuggable flag in review manifest (defaults false) |
| AAB signature inspection | **jar is unsigned**; intentionally not upload-ready until owner signing |
| Installation / Play Protect acceptance | **UNVERIFIED** |
| API 26 / API 27 runtime, metadata permission lifecycle and acoustics | **UNTESTED on device** |

No new instrumented execution was claimed. The prior connected test attempt could not create an ADB bridge due to a read-only default ADB directory; no physical device/emulator was added since that attempt. The new API 26 component-parsing test needs execution on the device together with the retained cache tests. Lint's API check and successful compilation cannot prove runtime compatibility.

Warnings are dependency-version notices and English UI localization/string concatenation. No lint baseline or permission-check suppression was added. Fault-injection tests simulate effect failures and never prove native effect support.

## Artifact identity and signing

Full evaluation APK SHA-256:

```
a61688306676c5f8b4b2a2c2f5c88cb65121d94f3ea306169cbf3ed79f1c6e11
```

Unsigned release AAB SHA-256:

```
69a306f1af92f88607a8c50fdf81257bd7aa8ef24de5d5edc98290feb194811e
```

Development certificate SHA-256:

```
681b22c401daf9fad6557cf343861d3858f085e2e9a9285eb89733372f594cd7
```

The full evaluation APK uses the **same development certificate** as the previous 0.3.1 debug artifact. This preserves development update identity; it does not guarantee installability and is not a production credential or security endorsement. A user's locally rebuilt evaluation APK can have a different generated development key. The normal unsigned release AAB is intended for owner signing with a stable secured upload key, with the Play Console setup and policy declarations completed separately.

## Installation blocker and required next step

The screenshot proves a Play Protect block for an app labelled AutoTune EQ AI, but does not provide the screened file's hash, Android version or Google's detection classification. Notification-listener access is a plausible sensitive capability involved, **not a confirmed root cause**. The earlier APK signature verified correctly.

There is no connected Play Console account and no Google review result available. The publisher must sign the normal release bundle, configure Play Console/privacy/foreground-service declarations, use legitimate testing distribution, and appeal any remaining Play Protect detection through Google's form. This task has not bypassed the block or disabled device protection. Rebuilding, changing debuggability or adding disclosure cannot be claimed to remove Google's decision.

## Remaining full-app limits and device matrix

The original detailed limitations remain: optional/untrusted external-session broadcasts, no arbitrary YouTube/Spotify session discovery, deprecated default-OFF session zero, best-effort effect restoration, capped SharedPreferences cache without TTL, no worldwide profile catalog, no trained AI and no FFT-derived mastering EQ. The empty backend stays empty outside temporary tests. No raw audio is uploaded.

| Device/source | Installation | Metadata | FFT | Audible EQ / cleanup |
|---|---|---|---|---|
| Android 8.0 / API 26 | Untested; minSdk/guard checked by build/lint | Untested; new exact-component fallback | Untested | Unverified |
| SAM5355 / MT6580 / API 27 | Untested | Untested | Untested | Unverified |
| Recent Android phone | Prior user screenshot shows a blocked installation; new artifact untested | Untested | Untested | Unverified |
| App's original test tone | Device installation required | Known generated signal | Untested | **Unverified baseline** |
| Cooperative local player | Device installation required | Untested | Untested | Unverified |
| YouTube / YouTube Music / Spotify | Device installation required | Untested | Untested | **No universal support claim** |

Test parked at low volume. Complete owner review/installation first, then check notification consent and revocation, audio permission regrant, metadata callbacks, pause/resume, track/player/route changes, service restart, rapid tone stop/start and effect restoration. Keep software states (`attached`, `control granted`, `applied`) separate from controlled listening/measurement. See `DEVICE_TEST_PLAN.md`.

The new source ZIP contains the complete project, wrapper, backend, tests, current/historical reports, installation-review instructions and raw build/test logs. `docs/SOURCE_SHA256SUMS` identifies files; artifact checksums are supplied separately. SDK/JDK/build caches/private signing material are excluded.
