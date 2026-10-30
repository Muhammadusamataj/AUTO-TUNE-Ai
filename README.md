> **0.3.4 candidate, revision 2.** Build the APKs with GitHub Actions: see `docs/GITHUB_ACTIONS_GUIDE.md` (workflow `.github/workflows/android-build.yml`, never run yet). What was and was not verified: `docs/BUILD_AND_TEST_REPORT.md`; source audit: `docs/SOURCE_AUDIT_0_3_4.md`; changes: `docs/CHANGELOG.md`; device tests: `docs/DEVICE_TEST_INSTRUCTIONS.md`. No Gradle build, APK, install or audible-EQ result exists yet for this revision.

# AutoTune EQ AI 0.3.3 candidate

This candidate continues the supplied Claude H1–H3 source, with additional session-lifecycle fixes and explicit Full/Lite product flavors. **Android 8.0 / API 26 minimum, target/compile SDK 35.** No final-release, universal third-party EQ, trained-AI or audible-effect claim is made.

| Flavor | Supported implementation | Deliberately unavailable |
|---|---|---|
| Full (`com.autotuneeq.mvp`) | User-consented public media-session metadata; offline/curated profiles; optional HTTPS lookup; diagnostic FFT; own-tone EQ; explicit experimental cooperative-session and legacy session-0 EQ | Arbitrary player-session discovery, guaranteed YouTube/Spotify processing, mastering AI |
| Lite (`com.autotuneeq.mvp.lite`) | Manually entered title/artist, offline profiles, explicit manual HTTPS lookup, five sliders and this app's own quiet test tone/session EQ | Notification access, automatic song detection, audio capture/FFT, background monitor, external/legacy EQ |

Lite is a separately labelled reduced-capability product, not Full disguised to evade a security decision. Both APKs retain Android security checks. Installing Lite does not prove Play Protect approves Full. See [installation diagnostics](docs/INSTALLATION_DIAGNOSTICS.md) and the [candidate report](docs/BUILD_AND_TEST_REPORT.md).

## Build

Use JDK 17, SDK Platform 35 and Build Tools 34.0.0; set `JAVA_HOME` and `ANDROID_HOME` (or an untracked `local.properties`). The official Gradle 8.7 wrapper and distribution checksum are included; AGP is pinned to 8.6.1. First build requires trusted Google/Maven/Gradle downloads; subsequent builds may use `--offline`.

```sh
./gradlew assembleFullReview assembleLiteReview assembleFullDebug assembleLiteDebug \
  testFullDebugUnitTest testLiteDebugUnitTest assembleFullDebugAndroidTest assembleLiteDebugAndroidTest \
  lintFullDebug lintLiteDebug lintFullReview lintLiteReview
bash tests/run_core_java_tests.sh
python3 -m unittest discover -s server -v
python3 -m unittest discover -s tools -p 'test_*.py' -v
# Authorized test device; does not grant listener/capture permissions:
./gradlew connectedFullDebugAndroidTest connectedLiteDebugAndroidTest
```

Windows: `gradlew.bat`. Review APKs are at `app/build/outputs/apk/{full,lite}/review/`; they are complete, non-debuggable **evaluation builds signed with a development certificate**, not production signing identities or Google approvals. The normal release variants remain unsigned; use Android Studio's signed bundle/APK workflow with your own stable upload/release key for production. Never embed signing/backend secrets in source. A new local debug key may conflict with an existing installation; obtain the actual PackageManager reason and compare certificates before changing installations.

## H1–H3 and concurrency

- Pause/buffering/connecting retain a known player's effect; STOPPED/ERROR, destroyed sessions, access loss and CLOSE release it. Track changes load the new recording's profile or neutral fallback.
- Early OPEN claims are bounded and delayed until the package actually plays. Duplicate/conflicting claims cannot reset backoff or overwrite another package. Failed attach uses six bounded exponential-backoff attempts and a ten-minute pending-claim TTL.
- Switching active players bypasses the previous player's effect, retaining its still-live known session claim for resume without a second OPEN. No third-party session scan is used.
- A serialized service worker owns native effects, FFT and media-session queries; a separate activity audio worker owns the original tone. Sliders coalesce. Broadcast callbacks parse only and enqueue bounded worker tasks; they do not perform audio-effect IPC.
- Sender UID is enforced when Android supplies it. Unknown sender identity on older APIs remains spoofable; package matching is not authentication. External experiments remain OFF by default, with explicit disclosure and no universal support claim.

## Consent and audio safety

Full shows a notification-access disclosure before Settings. Granting access is optional and revocable; the implementation uses public media-session metadata and does not inspect notification message bodies/OTPs. `RECORD_AUDIO` is optional for diagnostic Visualizer only. It does not create a microphone-recording pipeline. Lite declares neither capability.

The app's original test tone stops when the activity pauses. Begin parked at low volume. Relative -3…+3 dB sliders are shifted down to cut-only, clamped to hardware ranges; this is not a limiter. Status separates attachment, control and settings submission from independent audible verification. Native/OEM behavior still needs physical listening/measurement.

## Profiles and backend

Default server is empty. Saving an HTTPS origin is explicit metadata-sharing consent; only title/artist are sent, never raw audio. Lite additionally requires pressing Lookup. The persistent cache caps at 200 oldest-write-evicted profiles, has no TTL and may retain stale data offline. Async writes may be lost on abrupt power failure. Ordinary v1 identities migrate conservatively.

```sh
cd server
AUTOTUNEEQ_API_KEY='use-a-long-random-admin-key' python3 server.py
```

The stdlib/SQLite API starts empty on `127.0.0.1:8765`; do not expose plain HTTP publicly. Deploy separately behind HTTPS with production request/worker/rate limits. Admin write secrets never belong in the app. Existing catalogs must use canonical title/artist keys. Curated/licensed/test data only; no worldwide profiles are fabricated.

See [device plan](docs/DEVICE_TEST_PLAN.md), [installation diagnostics](docs/INSTALLATION_DIAGNOSTICS.md), [remaining issues](docs/REMAINING_ISSUES.md), and historical reports for earlier evidence. Real-device installation and audible EQ must succeed before considering a final release.
