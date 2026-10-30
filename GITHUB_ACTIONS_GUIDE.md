# Building the APKs with GitHub Actions (step by step)

The workflow `.github/workflows/android-build.yml` builds **Lite**, **FullProbe** and **Full** evaluation APKs on GitHub's servers, runs the unit tests and lint, checks the merged manifests, and publishes the APKs with SHA-256 checksums as downloadable artifacts. It has **never been run yet**: the first run is the first real Gradle build of this version. If a step fails, the log shows which one; send it to the engineer.

## 1. Put the project on GitHub
1. Create a free GitHub account, then **New repository** → name it (e.g. `autotune-eq`) → **Private** → create. (Private repos get a monthly allowance of free Actions minutes; public repos are free.)
2. **Upload the *contents* of the project folder, not the folder itself**, so that `gradlew`, `settings.gradle` and the `.github` folder sit at the top of the repository.
   * Web: unzip the source ZIP on your computer → in the new repository click **uploading an existing file** → drag everything inside `AutoTuneEQ_v0_3_4/` (the `.github` folder is hidden on some systems: enable "show hidden files", or use git below) → **Commit changes**.
   * Git: `cd AutoTuneEQ_v0_3_4 && git init && git add -A && git commit -m "AutoTune EQ 0.3.4 candidate" && git branch -M main && git remote add origin https://github.com/YOURNAME/autotune-eq.git && git push -u origin main`
3. Never upload keystore files or passwords (`.gitignore` already excludes `*.jks`, `*.keystore`, `local.properties`).
4. Check that `.github/workflows/android-build.yml` appears in the repository file list.

## 2. Run the workflow
* It starts automatically on every push to `main`/`master`. To start it by hand: repository → **Actions** tab → **Android build and verify** → **Run workflow** → **Run workflow**.
* Two jobs run: **Source-level checks** (about 2 minutes; Python/Java checks that need no Android SDK) and then **Build APKs, unit tests, lint** (about 10–25 minutes the first time). Both must be green. A red job shows the failing step; open it and read the last lines. Common first-run causes: an SDK package name changed, a lint **error**, a failing unit test, or a Gradle/AGP configuration error. None of these has been observed yet because the build has not been run.

## 3. Download the results
Open the finished run → **Artifacts** at the bottom:
* `autotuneeq-apks-N` — `AutoTuneEQ-Lite-review.apk`, `AutoTuneEQ-FullProbe-review.apk`, `AutoTuneEQ-Full-review.apk`, `SHA256SUMS.txt`, `BUILD_INFO.txt`, `SIGNING.txt`, `manifest-verification.txt`, decoded manifests.
* `autotuneeq-reports-N` — Gradle logs, JUnit XML, lint reports (also available when the run failed).
The run page's **Summary** shows the checksums, the manifest verification and the test/lint totals.
Download from your own repository while signed in to GitHub; the artifact downloads as a ZIP.

## 4. Verify the SHA-256 checksums
Compare each APK's hash with `SHA256SUMS.txt` **before installing**.
* Windows PowerShell: `Get-FileHash .\AutoTuneEQ-Lite-review.apk -Algorithm SHA256` (or `certutil -hashfile AutoTuneEQ-Lite-review.apk SHA256`)
* Linux: `sha256sum -c SHA256SUMS.txt` · macOS: `shasum -a 256 -c SHA256SUMS.txt`
* Android (Termux): `sha256sum file.apk`
A mismatch means the file changed in transit: delete it and download again. A matching hash proves the file equals what the workflow produced; it does not prove the app is safe or approved.

## 5. Stable signing (optional but recommended)
Without it, **every run signs with a different throw-away key**, and Android refuses to install a new build over an older one (generic "App not installed"). Uninstall the old copy first, or set up a stable *development* key once:
1. On your computer (JDK installed): `bash tools/make_dev_keystore.sh` — it prints three values.
2. Repository → **Settings → Secrets and variables → Actions → New repository secret**: create `AUTOTUNE_DEV_KEYSTORE_B64`, `AUTOTUNE_DEV_KEYSTORE_PASSWORD` (and optionally `AUTOTUNE_DEV_KEY_PASSWORD`, `AUTOTUNE_DEV_KEY_ALIAS` if you changed the alias).
3. Keep the `.jks` file and password private and backed up. This is a development identity, **not** the Play upload key.
The job summary states which signing mode a run used. Pull requests from forks never receive secrets.

## 6. Install and test
* **Lite on the Vivo (Android 16):** copy `AutoTuneEQ-Lite-review.apk` to the phone and open it from the file manager (or `adb install -r`). If it was signed with a different key than the Lite already installed, uninstall that one first. Then follow `DEVICE_TEST_INSTRUCTIONS.md` section A (Blind A/B).
* **FullProbe vs Full:** follow `PLAY_PROTECT_DIAGNOSTICS.md`.
* **SAM5355 car panel:** follow section B of `DEVICE_TEST_INSTRUCTIONS.md`.
