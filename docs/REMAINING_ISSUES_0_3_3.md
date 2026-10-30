# 0.3.3 candidate remaining issues and feature status

**Not a final release.** Real Vivo installation and real-device audible EQ are outstanding gates. See current build report for actual test results.

| Capability | Implementation and evidence | Limit |
|---|---|---|
| Offline manual/curated five-band profiles | Implemented Full/Lite; JSON, identity, limits and store code tested; platform test status in build report | SharedPreferences cap 200 oldest writes; no TTL; abrupt-power-loss persistence risk |
| Online profile service | Local stdlib HTTP/SQLite backend tested, including hostile data/huge numbers; app HTTPS endpoint and parsing tested | No live public HTTPS deployment test or worldwide catalog; production hosting/rate/worker limits remain |
| Song metadata | Full only, explicit listener disclosure and public MediaSession API | Requires user grant/cooperative metadata; no fingerprinting; real Vivo lifecycle unverified |
| Diagnostic FFT | Full only, optional RECORD_AUDIO/Visualizer | Route/OEM may block; never used to derive mastering EQ; no raw audio saved/uploaded |
| Own generated tone + EQ | Implemented in Full/Lite with serialized audio worker, cut-only mapping and lifecycle cleanup | Native effect/device-dependent. Constructor/control/status is not audible verification. No calibrated limiter or audio-focus/call/route proof |
| Cooperative external-session EQ | Full opt-in, bounded claims, active-player match, pause/buffer/switch/CLOSE cleanup and bounded retries covered by simulated regressions | Broadcasts optional; unknown sender identity is spoofable. Active package is not authenticated ownership of an arbitrary session ID. No universal YouTube/Spotify support |
| Session-0 legacy EQ | Full separate default-OFF experiment, never stacked with per-app mode | Deprecated, OEM-specific and acoustically unverified |
| Lite privacy/capability boundary | Separate source set/package/manifest, no listener/service/capture/notification runtime permissions; APK manifest/Dex checked | It is a reduced product, not a claimed bypass of Full's installation block |

## Residual session and concurrency risks

- UID/package enforcement works only when Android supplies sender identity. API 26–33 and API 34+ senders not sharing identity can still spoof declared package/session extras. Same-package guessed CLOSE or malicious OPEN can disrupt the experiment. No public API here proves arbitrary third-party session ownership.
- Receiver callbacks perform no audio/media IPC. They enqueue at most 32 pending tasks; OPEN processing is additionally limited to 32 per ten seconds. Under overload events, including CLOSE, may be dropped. Control/liveness polling remains a cleanup fallback. A malicious broadcaster can still cause denial of service; no perfect broadcast authentication is claimed.
- Session liveness is observable at player-package level, not per media-session-to-audio-session identity. A package with another paused/playing session can keep a stale claim alive until CLOSE/control loss/pending TTL. Physical cooperative-player tests remain necessary.
- Pending/released claims expire after ten minutes and six failed attempts. A source resumed after expiry may need a new OPEN; no hidden session scan is performed.
- Audio-effect, Visualizer, AudioTrack start/stop and media-session queries are moved off the main thread. Sliders coalesce at 80 ms; unchanged profiles are not rewritten every poll. Platform notification/service startup still occurs normally on the main thread.
- A native/OEM Binder call can still stall the worker. Cleanup is serialized after in-flight work and cannot forcibly interrupt native IPC safely. Moving it off UI reduces main-thread ANR exposure but does not prove hang-free native behavior, broadcast delivery or immediate restoration on broken hardware. Profile/cache operations remain bounded but not a Room production architecture.
- Media metadata transitions can precede/follow actual audio transitions. The model tests establish software ordering, not glitch-free/acoustically correct handoff on a physical player.

## Installation and release gates

- Vivo API 36's exact rejection remains unknown until its own install output/logs arrive. Do not assume the earlier Play Protect message explains the generic v0.3.2 failure.
- Both evaluation APKs are validly development-signed, non-debuggable, minSdk 26 / targetSdk 35. Production upload signing, Play Console policy/privacy declarations and any Google review remain owner work.
- Reference-emulator install/test evidence, if obtained, is scoped to that image. It does not establish Vivo/Play Protect acceptance or API 26/27 ARMv7 head-unit operation.
- Final release requires successful installation on the actual Vivo and API 27 panel, safe metadata/permission/background/route stress, repeatable original/local audio OFF/ON listening or loopback measurement, and cleanup/restoration verification. Test YouTube, YouTube Music and Spotify separately without promises of compatible sessions.

## Recorded Android 16 reference outcome (packaging update, 2026-10-30)

No successful emulator installation is recorded. The API 36 image reached `sys.boot_completed=1`, but PackageManager calls returned `Broken pipe (32)` or `Can't find service: package` during the preserved v0.3.1/v0.3.2 attempts. Those are test-environment errors, not the user's Vivo INSTALL_FAILED reason. The v0.3.3 instrumented tests compiled but did not execute. Actual Vivo installation, Play Protect acceptance and audible EQ remain unverified. No development or passing tests were restarted to package these artifacts.
