# Verification matrix for this change set (PASS / FAIL / BLOCKED / NOT EXECUTED)

Environment: Linux x86_64, JDK 17.0.20 (CI's version) and JDK 21.0.10, Python 3.12.3 (Unicode 15.0), headless Chromium 153, android.jar stubs for API 26/27/35 (third-party copy). No Android SDK, Gradle, Maven, emulator or device.

| Check requested | Result | Evidence / reason |
|---|---|---|
| Python backend tests | **PASS** 16/16 | `python3 -m unittest discover -s server -v` (JDK 17 replay of the CI step) |
| Python tool tests (manifest verifier, CI summary, install diagnostics) | **PASS** 17/17 | same replay |
| Java regression programs (simulated effects) | **PASS** 5/5 | `bash tests/run_core_java_tests.sh`, POSIX locale |
| Full Unicode parity fuzz | **PASS** | JDK 17 and JDK 21: 2,224,256 + 9,220 + 38,868 cases, 0 mismatches. Before the fix: **FAIL** 80 (JDK 17) |
| Identity table freshness | **PASS** | `gen_identity_table.py --check` |
| Android unit tests (real JUnit via Gradle) | **BLOCKED** | no Gradle/Maven access |
| …same test sources via my JUnit *shim* + real org.json | **PASS** 53/53 | shim re-implements only the JUnit calls used; not a substitute for Gradle |
| Instrumented tests (3 classes + `StoreCacheTest`) | **NOT EXECUTED** | no device/emulator |
| Gradle build / assemble | **BLOCKED** | no SDK/Gradle |
| Android lint | **BLOCKED** | needs AGP |
| Manifest verification script | **PASS** on fixtures (real 0.3.3 Lite manifest + synthetic Full/Probe incl. negative cases); on real built APKs **NOT EXECUTED** | `tools/test_verify_manifests.py` |
| APK signature verification | **BLOCKED** | no APK built |
| Audio-session lifecycle and failure recovery | **PASS** (simulated effects only); on device **NOT EXECUTED** | `TestSessionCoordinator`, `TestEqController`, `TestSessionHints` |
| Performance and memory checks | **NOT EXECUTED** | no hardware; only static reasoning in `SOURCE_AUDIT_0_3_4.md` |
| API 26 compatibility | **PASS (symbol check)**: only SDK_INT-guarded symbols unresolved vs android-26.jar | `run_review_checks.sh`; runtime **NOT EXECUTED** |
| Android 16 compatibility | **NOT EXECUTED**; only the owner's report that Lite 0.3.3 installed and launched |
| Workflow lint | **PASS** | actionlint 1.7.12 + ShellCheck 0.11 |
| UI preview in browser | **PASS** | 2 devices × 2 themes × 9 screens, no JS errors, no overflow; interactions scripted |
| GitHub Actions run | **NOT EXECUTED / UNREADABLE** | run logs not accessible without sign-in |
| Real audio output / audible EQ | **NOT EXECUTED** | requires hardware; nothing is claimed |
