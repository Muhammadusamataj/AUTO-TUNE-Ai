# AutoTune EQ AI 0.3.4 candidate, revision 2 — build and test report

**No Gradle build, no APK, no real JUnit/lint run, and no device test exist for this revision.** The four evidence classes below are kept separate on purpose.

## 1. Source compilation (javac against android.jar stubs) — RAN
| Target | Result |
|---|---|
| Full vs API 35 | compiles, 53 classes |
| Lite vs API 35 / API 26 | compiles, 31 classes each |
| Full vs API 26 | only 4 unresolved symbols, all inside `SDK_INT` guards: `POST_NOTIFICATIONS` (≥33, two uses), `getSentFromUid()` (≥34), `isNotificationListenerAccessGranted` (≥27, with an API 26 fallback) |
This is a type-check against a third-party copy of the SDK stubs (Sable/android-platforms) with a 6-line `ContextCompat` stub. It is **not** AGP: resources, manifest merging, D8, lint and signing are untested. `app/build.gradle` (new `signingConfigs.ci`, `probe` build type) and `app/src/fullProbe/AndroidManifest.xml` were never evaluated by Gradle.

## 2. Mocked / pure-logic tests — RAN (log: `docs/logs/v034r2/review-env-checks.txt`)
| Suite | Result | Notes |
|---|---|---|
| JUnit-style tests through my **JUnit shim** (not real JUnit): `CoreRegressionTest` 8, `TrackIdentityTest` 6, `AbAndCacheTest` 8, `DiagAndPollTest` 4, `SessionRegressionTest` 8, `PlayerSupportTest` 3 | 37 / 37 passed | real org.json 20240303 |
| Standalone Java programs (`tests/run_core_java_tests.sh`) | 5 / 5 PASS | fake Equalizer: lifecycle bookkeeping, new restore-guard and claim-flood cases |
| Backend `unittest` | 16 / 16 OK | includes the YouTube-style identity test |
| Tool tests (`tools/`) | 17 / 17 OK | manifest verifier (8, incl. the decoded real Lite manifest), CI summary (5), install diagnostics (4) |
| Java↔Python identity parity (`tools/identity_parity_fuzz.py --full`) | 258,172 code-point cases + 15,653 YouTube-style cases, 0 mismatches | found and fixed three real divergences (A6) |
| `actionlint` 1.7.12 with ShellCheck 0.11 on `android-build.yml` | no findings | validates syntax/expressions/shell, not that the build succeeds |
Mocked tests prove bookkeeping order, parsing and scoring. They prove nothing about native effects, audio, Android lifecycle timing or the A/B screen's UI.

## 3. Real Gradle builds — NOT RUN
Gradle/AGP 8.6.1, `testFull/LiteDebugUnitTest` (real JUnit), `lint*`, `assemble*`, `*AndroidTest` (instrumented, three classes incl. new `StoreCacheTest`), `apkanalyzer`/`apksigner` on built APKs. The review environment could not reach Google/Maven/Gradle hosts. These steps are exactly what `.github/workflows/android-build.yml` performs; **the first workflow run is the first real build of this revision.**

## 4. Physical-device verification — NOT RUN
Installation of any 0.3.4 APK, the Blind A/B check, FullProbe-vs-Full Play Protect comparison, any audible EQ, any SAM5355 test. The only device fact on record is the owner's report that **0.3.3 Lite** installed and launched on a Vivo (Android 16) and 0.3.3 Full was blocked by Play Protect.

## Environment used for sections 1–2
Linux x86_64; OpenJDK 21 runtime with `jdk.compiler`; Python 3.12; android.jar API 35/27/26 stubs; shim sources in `tools/review-shim/` (run with `tools/review-shim/run_review_checks.sh`).

## What the CI workflow will do (unrun)
1. *Source-level checks*: Gradle-wrapper validation, backend + tool tests, simulated-effect Java programs, full identity parity scan.
2. *Android job*: JDK 17, Android SDK (platform 35, build-tools 34.0.0, cmdline-tools), `chmod +x gradlew`, optional stable keystore from secrets, `testFullDebugUnitTest testLiteDebugUnitTest`, `lint{Full,Lite}{Debug,Review}`, `assembleLiteReview assembleFullProbe assembleFullReview` + compile of both instrumented-test APKs, APK signature check, merged-manifest verification (fails unless the probe differs from Full only by the listener), SHA-256 checksums, job summary, artifact upload (APK artifact fails the job if missing; reports uploaded even on failure).

## Integrity
`docs/SOURCE_SHA256SUMS` lists every file in the archive except itself. The wrapper JAR is byte-identical to the Gradle v8.7.0 tag (checked in an earlier review; `gradle/actions/wrapper-validation` re-checks it in CI).
