# Installation diagnostics: actual Vivo Android 16 / API 36

**The exact Vivo PackageManager reason is unknown until phone logs are supplied.** “App not installed” is a generic installer message, not an INSTALL_FAILED code. The earlier Play Protect block was observed in a separate screenshot; it cannot be assumed to explain v0.3.2's different message. Reference-emulator results, if present in the current report, do not identify Vivo policy or establish Play Protect approval.

Minimum API 26 / target API 35 satisfy basic version bounds for an API 36 device. Target SDK was not changed to evade checks. APK files are complete single APKs, signed with a valid development v2 signature and aligned; no native .so libraries are bundled. There is no evidence yet justifying a new certificate, permission removal from Full, target downgrade, security-toggle change or firmware modification.

## Exact phone commands

Install current official Android Platform Tools on your computer. Connect the Vivo phone and authorize normal USB debugging. With multiple devices use `adb -s PHONE_SERIAL` for each phone command; do not accidentally diagnose an emulator.

Rename the **Full evaluation APK** being tested to `app-review.apk` (keep the checksum) and run:

```sh
adb version
adb devices -l
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.release
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell pm list packages | grep autotune
adb shell pm list users
adb shell pm path com.autotuneeq.mvp
adb shell dumpsys package com.autotuneeq.mvp > package-before.txt
adb shell date > phone-time-before.txt
adb install -r app-review.apk > install-result.txt 2>&1
adb shell date > phone-time-after.txt
adb logcat -d -b all > install-logcat-all.txt
adb shell dumpsys package com.autotuneeq.mvp > package-after.txt
```

Windows Command Prompt: replace `grep autotune` with `findstr /i autotune`. PowerShell: `adb shell pm list packages | Select-String autotune`.

Share the **complete `install-result.txt`**, device API/manufacturer/model and installation-related log lines near the recorded time. Full logcat can include unrelated private data; retain it locally and share a relevant excerpt. Do not clear logs or uninstall the previous app before collecting evidence, because that can erase the signature-conflict evidence and user data.

For a file-manager/Vivo-installer failure, record that flow separately from ADB. ADB may use a different installation path:

```sh
# Start this in a separate terminal, reproduce the normal UI install, then Ctrl+C:
adb logcat -v threadtime -b all > vivo-ui-install-live.txt
```

A successful ADB install proves only that installation path on that device; it does not prove the Vivo UI accepted the same file, nor that Google approved it.

## One-command evidence collector

The source includes `tools/collect_install_diagnostics.py` (Python 3, no third-party dependencies):

```sh
python3 tools/collect_install_diagnostics.py \
  --apk app-review.apk --package com.autotuneeq.mvp \
  --expected-sha256 FULL_APK_SHA256_FROM_SHA256SUMS \
  --serial PHONE_SERIAL --out vivo-full-evidence --install
```

Replace the checksum and serial with actual values. On Windows use `python` and enter the command on one line. The output directory must be empty to preserve earlier evidence. Without `--install` it only collects evidence. The script never uninstalls an app, clears data/logs, grants runtime permissions or changes verifier/security settings. `--install` performs only normal `adb install -r`. For Lite, use its checksum and `--package com.autotuneeq.mvp.lite` with a different output directory.

The collector saves APK integrity, commands, phone version, package state, raw install output, raw logcat and filtered excerpts; `summary.json` reports the primary observed INSTALL_FAILED code or UNKNOWN. It never infers a reason from the generic screenshot. Optional `--apksigner /path/to/SDK/build-tools/35.0.0/apksigner` also pulls an accessible installed base APK and records both certificates for comparison. This may be restricted by the device; a failed pull is recorded, not bypassed.

## Verify the exact downloaded file and installed signer

```sh
sha256sum app-review.apk
apksigner verify --verbose --print-certs app-review.apk
aapt dump badging app-review.apk
zipalign -c -P 16 -v 4 app-review.apk
```

PowerShell checksum: `Get-FileHash .\app-review.apk -Algorithm SHA256`. Compare against `SHA256SUMS` or `APK_IDENTITY.json` supplied with the same candidate.

To check an existing installation:

```sh
adb shell pm path com.autotuneeq.mvp
# Substitute the literal base.apk path reported above:
adb pull /data/app/REPORTED_PATH/base.apk installed-base.apk
apksigner verify --verbose --print-certs installed-base.apk
```

Use the real reported path; `REPORTED_PATH` is a placeholder. Compare certificate SHA-256 fingerprints, not only app names or APK hashes. Supplied v0.3.1/v0.3.2/v0.3.3 Full artifacts use the same development certificate (`681b22c401daf9fad6557cf343861d3858f085e2e9a9285eb89733372f594cd7`). That rules out a conflict **between those supplied files**, not with an independently signed build installed on the phone or another user/profile. VersionCodes are 4, 5, 6 respectively, so the supplied sequence is not a version downgrade.

## Interpret evidence without assuming a cause

| Observed result | What it establishes / next investigation |
|---|---|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Compare existing and incoming signing certificates/package identity across relevant users. Do not change keys or delete data blindly. |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | Compare installed and incoming versionCode. No `-d` workaround is prescribed. |
| `INSTALL_PARSE_FAILED_NO_CERTIFICATES` or related parse error | Verify exact downloaded hash and signature, including transfer corruption. Preserve full error detail. |
| `INSTALL_FAILED_OLDER_SDK` | Compare phone API with actual packaged minSdk. No target-SDK guessing. |
| `INSTALL_FAILED_VERIFICATION_FAILURE` | A verifier rejected installation; inspect logs for the responsible verifier. The code alone does not prove Google versus Vivo. |
| `INSTALL_FAILED_USER_RESTRICTED` / policy rejection | Inspect the authorized user/work-profile/device policy and Vivo installer logs. Do not override security policy. |
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` | Check actual device storage and install-session errors. |
| `Success` via ADB, UI still fails | Compare timestamps/artifact hashes and installer paths; investigate Vivo UI/verifier separately. |
| Generic UI text / no ADB output | **Unknown.** No PackageManager cause can honestly be assigned. |

The sample errors above are a diagnostic mapping, not errors observed on the user's Vivo. A reference emulator has different firmware, policies and Play Protect coverage. No real-device installability, third-party audible EQ or Google approval is inferred from it.

For confirmed Google detection, the owner can use Google's [Play Protect appeal process](https://support.google.com/googleplay/android-developer/contact/protectappeals) with the exact signed artifact and logs. No submission or approval is claimed here. A valid signature is necessary but does not itself establish safety or guaranteed installability.
