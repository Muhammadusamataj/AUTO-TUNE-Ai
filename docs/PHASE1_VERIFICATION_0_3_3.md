# Independent verification of the v0.3.3 candidate (auditor's findings)

Legend: **RAN** = executed by the auditor in the review environment (see `BUILD_AND_TEST_REPORT.md` for what that environment was); **STATIC** = read from source, not executed; **CODEX** = reported by the previous engineer, not reproduced.

## Previous HIGH findings
| ID | Status | Evidence |
|---|---|---|
| H1 pause/BUFFERING released the effect | **FIXED** (RAN: simulated lifecycle test; STATIC: code) | `SongMonitor.java:98` marks PAUSED/BUFFERING/CONNECTING/etc. as retained via `PlaybackPolicy.retainsSession` (STOPPED/ERROR/NONE are not); `SessionCoordinator.java:137` keeps effects while nothing is playing; `TestSessionCoordinator` (pause → resume reuses the same effect, track switch applies neutral profile) PASS |
| H2 early OPEN dropped | **FIXED** | `MonitorService.java` receiver no longer requires an active player; `SessionCoordinator.open` + `SessionHints.offer` keep a bounded claim; attach happens when the same package plays (`SessionHints.tryAttach`). Tests: early OPEN survives, spoofed package never reaches the effect |
| H3 hint removed on failed attach | **FIXED** | `SessionHints.java:70-73` back-off (2.5 s × 2ⁿ, 6 attempts, 10 min TTL); duplicate OPEN cannot reset it (`:51-52`); denied-control then success after back-off PASS |

## Codex's additional fixes
Duplicate OPEN (`SessionHints.java:51`, `SessionCoordinator.java:102`), conflicting package claim (rejected, effect never attached for the forger), STOPPED/ERROR cleanup (`PlaybackPolicy`, `SessionCoordinator.java:123`), player switching with bypass/switch-back (`:129-131`) — all present in source and exercised by the simulated tests (RAN). They use a **fake Equalizer**: they prove bookkeeping order, not native or acoustic behaviour.

## Threading (STATIC)
`MonitorService` owns a `HandlerThread` (`:93-95`, `:126-128`): media-session queries, effect IPC, Visualizer, cloud callbacks and `poll` run on it. The broadcast receiver only parses extras and enqueues (bounded to 32, 32 OPEN/10 s). `ToneEngine` moves own-tone AudioTrack/Equalizer work off the UI thread and coalesces sliders at 80 ms. `startForeground` stays on the main thread (platform deadline). Residual: a hung native/OEM Binder call can still stall the worker (cannot be interrupted); main thread still reads `SharedPreferences`.

## Compatibility (RAN compile, STATIC review)
* Compiles against API 35 (full 50 classes, lite 28). Against API 26 the only unresolved symbols are `POST_NOTIFICATIONS` (inside `SDK_INT >= 33`), `getSentFromUid()` (inside `SDK_INT >= 34`) and `isNotificationListenerAccessGranted` (inside `SDK_INT >= 27`, with an API 26 settings-parsing fallback). Lite has none.
* No native code, so ARMv7 ABI is not a risk. One worker thread + one cloud thread + Visualizer thread for Full; low memory use by inspection.
* Android 16 / API 36: targetSdk 35 means edge-to-edge is enforced; both activities apply system-bar insets in code. No Android-16-specific API is used. **Runtime behaviour on API 36 is unverified** except that the supplied Lite APK installed and launched on the owner's Vivo.
* Leak review (STATIC): HandlerThreads are quit on destroy/close, receivers unregistered, media callbacks unregistered in `SongMonitor.stop`, Activity references removed in `onDestroy`. No leak found; none measured.

## Findings in v0.3.3 that remained (addressed in 0.3.4 unless noted)
1. Unknown songs re-requested from the server after every pause/resume (no negative cache) → `MissCache` (6 h).
2. Manual profiles evicted by cached online profiles → pinned via `CachePolicy` / `putManual`.
3. Visualizer capture ran for the whole service lifetime although display-only → now only while the UI is visible.
4. Song title on the lock screen → private lock-screen version.
5. Legacy global EQ rewrote bands on every poll → only when the profile changes.
6. Sideloaded Android 13+ users get no guidance for "Allow restricted settings" → dialog text + "Open app info".
7. **Not fixed:** claims for a player that is not the active one expire after 10 min (resume after that needs a new OPEN); per-app mode is not persisted and nothing starts on boot; session ownership cannot be authenticated (documented); no unit tests for `SongMonitor.snapshot`/`MonitorService`; instrumented tests have never been executed; YouTube has no per-app path.
