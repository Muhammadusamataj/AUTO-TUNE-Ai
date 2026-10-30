# 0.3.4 candidate (revision 2) — remaining limitations and known bugs

**Not a release.** Gradle build, lint, real JUnit, instrumented tests and every device behaviour are unverified. The next milestone is: run the GitHub workflow, then install Lite and run the Blind A/B check.

| Capability | State | Limit |
|---|---|---|
| CI build / APKs | Workflow written and linted (actionlint+ShellCheck) | Never run; first run may fail on SDK names, lint errors, Gradle configuration of `signingConfigs.ci`/`probe` |
| Lite own-tone EQ + blind A/B | Implemented; scoring unit-tested; Android parts compile-checked | Never run on a device; proves only Lite's own audio session |
| Full: song detection | MediaSession + notification listener; simulated lifecycle tests | Sensitive permission; blocked by Play Protect when sideloaded from a file manager (observed for 0.3.3) |
| Full: per-app session EQ | Experimental, opt-in, remembered once armed, cut-only | Only players that publish OPEN; spoofable; YouTube reported unsupported; never run on a device |
| Session-0 legacy EQ | Default-off, never remembered | Deprecated, OEM-dependent |
| FFT | Display only, only while visible | No analysis fallback exists; no EQ derived from it |
| Online profiles | Client + local backend; 6 h not-found cache, 2 min error cache | Empty catalogue; no public deployment; no TTL refresh for found profiles |
| Metadata normalization | Promo suffixes, `- Topic`, repeated-artist prefix; mirrored in server | Cannot split "Artist - Title" when the artist field differs; no fingerprinting; old caches migrate lazily |
| Speaker/car calibration | **Not implemented** | design only |
| Boot start | **Not implemented** | needs RECEIVE_BOOT_COMPLETED + FGS-from-boot validation on the panel |
| Diagnostics | In-memory, title-free | Contains player package names; copy is manual |

## Known bugs / risks
* A guessed CLOSE or a forged OPEN can still disturb a known session when Android gives no sender identity.
* A contested effect (another EQ with higher priority) can be re-attached repeatedly: bounded by back-off only while attaches fail.
* Claims of a non-active player end after 10 minutes; later resume needs a new OPEN.
* Cut-only mapping lowers loudness by up to 3 dB; ≤ 6 dB of shaping; AOSP band overlap makes the response approximate.
* A native/OEM Binder stall can block the worker thread.
* Deprecated `AudioTrack` constructor (works through API 36; lint warning only).
* CI-built evaluation APKs use a throw-away key unless the optional keystore secrets exist ⇒ reinstall conflicts.
* No claim of universal YouTube/Spotify support or AI mastering is made or supported by any test.
