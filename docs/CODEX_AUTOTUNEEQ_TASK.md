# CODEX TASK — AutoTune EQ AI v0.3 → verified Android MVP

You are the implementation engineer for an existing Android Studio Java project. Work on the **actual supplied project**, not a new unrelated demo. Unzip `AutoTuneEQ_v0_3_Review_Fixes_Source.zip`; open `AutoTuneEQ_v0_3/` as the project root. Read `README.md`, `START_HERE_ROMAN_URDU.md`, `docs/CLAUDE_REVIEW_RESPONSE.md`, and `docs/DEVICE_TEST_PLAN.md` before editing.

## Product goal and target devices

The project is an *experimental hybrid music EQ* for Android phones and car head units. It should detect playing media metadata, retrieve cached/remote curated EQ profiles, visualize an FFT where permitted, and apply EQ only if a supported audio effect path really exists. First physical target: an unrooted SAM5355/MediaTek MT6580 (ARMv7, 2GB RAM) car panel reporting API 27 (Android 8.1) in CPU-Z despite a display string of 'Android 16'. Also target newer Android phones. Never assume YouTube/Spotify permits external audio session control. Do not root/flash or bypass platform permission/security restrictions.

## Objectives (in order)

1. Inspect all source and identify the actual current build configuration. Make the smallest safe changes that preserve the project's intent. Add the appropriate **Gradle wrapper** and reproducible setup instructions, using versions compatible with Android Gradle Plugin, Android SDK and JDK 17. Build with `assembleDebug` if Android tooling is available. **If building is impossible, explicitly say so and do not claim an APK or Android tests passed.** Preserve build logs/errors.
2. Fix proven compile errors, crashes, lifecycle, threading and permission issues. Pay special attention to Notification Listener settings fallback, RECORD_AUDIO reinitialization, foreground-service restrictions across OS versions, media callbacks and service restart, and releasing all `Visualizer`/`Equalizer`/AudioTrack resources. Avoid privileged permissions and hidden APIs as required dependencies.
3. Review `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` and `ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION` handling, intent extras and session ID ownership; keep external audio effects **opt-in** and explain that player broadcasts are optional. Validate package-to-session association conservatively; reject invalid or spoofable events where practical. Do not claim you can discover arbitrary third-party sessions. Label deprecated session-0 EQ as a separate optional legacy experiment, default OFF.
4. Improve acoustics safely: ensure cut-only / headroom-aware band mapping, clamp values to actual effect band ranges, interpolate mapping correctly and avoid modifying bass/treble automatically from raw low-resolution FFT. A working EQ on the app's own tone session is the baseline test. Make output-control status distinguish 'attached', 'control granted', and 'verified audible effect'.
5. Check network profile lookup, song identity/version normalization, caching, timeout/size limits, HTTPS transport, consent and handling malformed/hostile server data. Profile database will initially be EMPTY: do not fabricate worldwide internet song-frequency profiles or imply trained AI. Do not upload raw audio.
6. Add meaningful Java unit tests (including mapping/clamp/headroom) and backend tests, plus Android instrumented tests if tooling is available. Run available tests and report pass/fail. A test that does not run must be marked untested.
7. Keep the UI simple and usable on 1280×720 Android car panels and phones. Show clear labels for `Detected song`, `Profile found/not found`, `FFT available/unavailable`, `External EQ unsupported/experimental`, `Effect attached`, and `EQ applied (not independently verified)` where relevant.
8. Produce `docs/BUILD_AND_TEST_REPORT.md` with precise commands, environment versions, build/test outputs, list of modifications, known limitations, and a safe test matrix for app test tone / local player / YouTube / YouTube Music / Spotify. On-device audio path effects MUST remain unverified until tested on real hardware with listening/measurement; an effect constructor not throwing is insufficient evidence.

## Hard constraints

- `minSdkVersion` must remain 27 unless a justified change is documented.
- No rooting, firmware flash, untrusted sideload hack, bypass of permissions/DRM, or improper `DUMP` grant assumptions.
- Never assert system-wide equalization of YouTube/Spotify works on all devices.
- Don't equate low-res FFT with a validated per-song mastering EQ.
- Don't deploy a default HTTP backend publicly or embed backend write secrets in the app.
- Make real edits in source; don't just output advice.

## Required final deliverables

- A **revised project source ZIP** or updated repository with all changes.
- A built **debug APK only if you actually compiled it** with Android tooling, otherwise a clear blocker list and reproducible build commands.
- Test results and `BUILD_AND_TEST_REPORT.md`.
- A short change summary and remaining work/compatibility limits.
- Exact source/files/commit or patch, so a different reviewer can audit it independently.

Prioritize a **correct, honest, buildable Android MVP** over an impressive but unverified 'universal AI auto EQ' claim. Begin by reading the repository and executing tests/build where tools allow.
