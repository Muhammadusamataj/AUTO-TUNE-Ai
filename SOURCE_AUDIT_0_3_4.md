# Source-level audit of the 0.3.4 candidate (revision 2)

**Scope and limits.** Static review of every main-source file plus the checks listed in `BUILD_AND_TEST_REPORT.md`. Evidence classes used below:
**SRC** = read in source; **COMPILED** = compiled with `javac` against `android.jar` stubs (API 35 and 26); **MOCKED** = executed against fake effects / pure-Java logic (and a JUnit *shim*); **GRADLE** = real Gradle/AGP (none yet); **DEVICE** = physical device (none yet). Nothing here is DEVICE- or GRADLE-verified.

## Confirmed defects fixed in this revision
| ID | Severity | Where | Problem (evidence) | Fix | Regression test |
|---|---|---|---|---|---|
| A1 | Medium | `SessionCoordinator.open/expire` (`:17-39`) | A hostile app could send OPEN for many fake packages; 16 unverified claims filled the table for 10 minutes and blocked a legitimate player's OPEN (SRC; reproduced in MOCKED test) | ≤ 3 claims per package; claims of packages that never showed a live media session expire after 90 s; packages with a live session keep the 10-minute window | `TestSessionCoordinator` "0.3.4 rev" block |
| A2 | Medium | `EqController.restore` (`:42-50`) | Release rewrote the *attach-time* snapshot even if the player/another EQ changed the bands afterwards (SRC) | Remember the levels we wrote; restore only if bands are unchanged, otherwise just release | `TestEqController` "never overwrite settings changed by someone else" |
| A3 | Medium | `MonitorService.poll` (`:290-296`) | Any exception inside the poll ended the self-rescheduling loop: monitoring silently stopped (SRC) | `run()` wraps `pollOnce()` in try/catch/finally that always reschedules and logs the exception class | covered by compile + review; not unit-testable without Android |
| A4 | Low-Med | `MonitorService.mediaChanged` + `PollGate` | Every `MediaController` callback forced an immediate poll (IPC + effect checks); players that update state many times a second cause a poll storm on a 2 GB ARMv7 unit (SRC) | `PollGate` coalesces bursts to one extra poll per 250 ms | `DiagAndPollTest.pollGateCoalesces…` |
| A5 | Low | `MonitorService.onStartCommand` (`:180`) | `startForeground` re-ran on every command and replaced the notification text with "Checking media playback" (SRC) | called once (`foregroundStarted`) | review |
| A6 | Medium | `TrackIdentity` | Java `String.trim()` removes all control characters, Python `strip()` does not; Java and Python `toLowerCase`/`lower` differ in Greek final-sigma context; Python treats U+001C–1F and U+0085 as whitespace, Java did not. Result: silent lookup misses for such strings (found by differential fuzz, MOCKED) | `trimSpaces`, sigma folding, explicit whitespace class on both sides | `tools/identity_parity_fuzz.py --full` (CI), `TrackIdentityTest` |
| A7 | Low | network errors | A failed lookup was retried on every song event while offline (SRC) | in-memory 2-minute error cache (`cloudErrors`) | `DiagAndPollTest.missCacheHonoursItsOwnTtl` |
| A8 | Low | `AbSession.summary` | Result text named bands by index only; real band frequencies are device-specific (SRC) | `summary(int[] bandHz)` labels each band; cut limited by the device range is flagged in the report | `DiagAndPollTest.abSummaryLabels…` |
| A9 | Medium | CI signing | A fresh runner generates a new debug key each run; installing a later build over an earlier one fails with a signature conflict ("App not installed") (SRC, AGP behaviour) | optional stable development keystore from repository secrets (`signingConfigs.ci`), otherwise explicit warning in the job summary | n/a (Gradle) |

## Design checks that found no defect (SRC + MOCKED)
* **Lifecycle:** pause/resume/BUFFERING/CONNECTING retain the effect, STOPPED/ERROR/NONE do not, CLOSE cancels pending and attached claims, player switch bypasses and later re-attaches without a new OPEN, access loss clears everything, duplicate OPEN cannot reset back-off or TTL (`SessionCoordinator`, `SessionHints`, `PlaybackPolicy`).
* **A/B test:** plan is balanced (4 real trials per band, exactly 2 ON-first), null trials carry identical processing in both presentations, tone amplitude is capped at 0.12 (-18.4 dBFS) and nominally 0.03 (-30.5 dBFS), the effect state changes only while the gate is closed (8 ms ramps), a side-biased listener cannot reach `AUDIBLE` (scores 2/4) and is flagged by null trials. *Not verified:* what the hardware does.
* **Threading:** effect/Visualizer/media-session IPC runs on the service `HandlerThread`; the receiver only parses and enqueues (queue ≤ 32, OPEN ≤ 32/10 s).
* **Network:** HTTPS origin only, no redirects, 16 KiB cap, strict JSON and gain validation, identity echo check; server rejects huge integers and non-objects.

## Remaining risks (not fixed; see `REMAINING_ISSUES.md`)
Spoofable OPEN/CLOSE when the platform gives no sender identity (a guessed CLOSE for a known session can still detach it); effect attach/loss flapping is bounded by back-off only while attaches fail (a contested effect can still be re-attached each time it is lost); a hung native/OEM Binder call can stall the worker; claims of a non-active player end after 10 minutes; AudioTrack uses the deprecated constructor (works through API 36); no boot start.

## Compatibility notes
Android 8.0–8.1: API-26-safe (guards for `isNotificationListenerAccessGranted`, notification permission, sender UID); no native code (ARMv7 irrelevant). Android 13+: restricted-settings guidance added. Android 14+: `specialUse` FGS with declared subtype. Android 15/16: edge-to-edge insets are applied in code; no API-36-specific call exists; **runtime on API 36 is verified only by the owner's successful Lite 0.3.3 install/launch**.
