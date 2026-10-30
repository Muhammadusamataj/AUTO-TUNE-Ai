> Historical notes supplied with the H1–H3 input. Current v0.3.3 results and extensions are in BUILD_AND_TEST_REPORT.md.

# 0.3.2 + independent-audit candidate patch (NOT a release)
Changes: H1 (keep per-app EQ across pause/BUFFERING; release only when the player's MediaSession is gone or notification access is lost),
H2 (early OPEN kept as bounded hint), H3 (failed attach retried with back-off), M9 (server OverflowError).
Verified by the auditor: compiles with javac against android.jar API 35 (API 26 errors only in SDK_INT-guarded calls); 4 standalone Java test programs pass; 14 backend tests pass.
NOT verified: Gradle/AGP build, lint, JUnit, any device/audible behaviour.
