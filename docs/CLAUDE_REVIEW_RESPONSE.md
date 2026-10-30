> Historical v0.3 review response below. Current implementation and verified build status are documented in [BUILD_AND_TEST_REPORT.md](BUILD_AND_TEST_REPORT.md). Several pending items below are now implemented; hardware validation remains pending.

# Independent review: issue disposition after v0.3

This is a response to the review text supplied by the user, not a claim that Claude built or ran this revision.

| Issue | Status in source | Evidence / caveat |
|---|---|---|
| No external audio sessions | **Partial experimental** | `MonitorService.sessionReceiver` listens for optional player OPEN/CLOSE broadcasts; attaches only with matching active media package; no arbitrary-session discovery |
| Auto-apply gated on global mode | **Patched** | `applyEnabledEffects` handles attached per-app sessions |
| Unsafe spectrum-flattening heuristic | **Patched** | `BandAnalyzer` only produces visual levels; neutral recommendation |
| Positive EQ gains can clip | **Mitigated** | `EqMath.cutOnlyDb`, `EqController.apply`; no positive gain requested, but other forms of distortion remain possible |
| Thread race | **Partially patched** | FFT callback updates state on main handler; EQ synchronized; no full concurrency stress/instrumented testing |
| Notification settings crash | **Patched** | MainActivity catches missing settings Activity |
| RECORD_AUDIO post-grant | **Patched** | onRequestPermissionsResult triggers FFT retry |
| Main-thread disk commit | **Mitigated** | `ProfileStore.put` now uses async `apply`; no Room/SQLite LRU/TTL yet |
| Service killed by OEM | **Partially mitigated** | START_STICKY and notification; boot startup and guaranteed OEM persistence NOT implemented |
| Track normalization / polling | **Partially patched** | Conservative `TrackIdentity` and 2.5s polling retained; MediaController callbacks not implemented |
| Magic source-string logic | **Patched** | Live FFT does not replace profiles based on source strings |
| Cloud hardening | **Partially patched** | HTTPS only, no redirects, byte limits, identity response check, server-side validation; no retries, POST metadata lookup, CDN, auth/rate limit on GET |
| Python API | **Patched for reported bugs** | Non-dict payload rejection, Unicode token handling, DB close tests |
| Shallow tests | **Improved** | Pure-Java EQ interpolation/cut-only/identity checks plus backend tests; Android instrumentation pending |
| TonePlayer writer cleanup | **Improved** | bounded join on stop; still legacy AudioTrack constructor |
| Notifications access privacy | **Documented** | Explicit permission; notification listener API is powerful and requires review |

## Nonnegotiable release gates

1. Compile with AGP 8.6.1, JDK 17, Gradle 8.7+, Android SDK 35.
2. Run on API 27/32-bit ARM car panel, modern Android device, and a local playback app.
3. Verify actual audio effect on real audio with repeatable measurements, not simply by effect creation or log status.
4. Confirm no distorted sound/volume spikes and no effect survives after disabling or switching player.
5. Replace local cache + optional cloud backend with a robust production design, review permission UX, privacy policy and security.

**No claim is made here that YouTube/Spotify support works.**
