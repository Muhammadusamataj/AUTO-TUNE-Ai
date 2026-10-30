# Android 16 (Vivo) installation diagnostics — evidence, inference, next experiment

## Evidence supplied by the owner (three screenshots)
| # | Time | What it shows | Notes |
|---|---|---|---|
| 1 | 9:14 | Google Play Protect: **"App blocked to protect your device"** for **AutoTune EQ AI Full** — "This app can request access to sensitive data. This can increase the risk of identity theft or financial fraud." | File manager, `storage/emulated/0/Download` visible behind the dialog |
| 2 | 9:14 (same minute) | Installer dialog **"AutoTune EQ AI Full — App not installed."** | Same file-manager background |
| 3 | 9:37 | **AutoTune EQ AI Lite · 0.3.3 candidate** running (five sliders, own tone, manual profiles) | Lite installed and launched on the same phone |

## What is established
* **VERIFIED (screenshot 1 text vs Google's developer documentation):** the block is Play Protect's *sensitive-permission install block*, not an INSTALL_FAILED signature/version error. Google documents this exact prompt for apps that arrive from "internet-sideloading sources" (browser, messaging app, file manager) and declare certain sensitive permissions, including a notification listener.
* **VERIFIED (APK and merged-manifest comparison, `SECURITY_PERMISSION_COMPARISON.md`):** Lite and Full have the same signer (`CN=Android Debug`, SHA-256 `681b22c4…cd7`), minSdk 26, targetSdk 35, no native code. They differ only in: 4 extra `uses-permission` entries, 2 extra services (`MonitorService`, `MediaNotificationListener`) and the launcher activity. Only **one** of those differences (`BIND_NOTIFICATION_LISTENER_SERVICE`) appears in Google's documented list.
* **INFERENCE (supported, not proven):** "App not installed" in screenshot 2 is the installer's generic message after the Play Protect dialog was dismissed — same app label, same minute, same background. Codex's earlier reading that the generic message "cannot be assumed to be explained" by the block is too cautious for *this* v0.3.3 Full attempt; it remains unknown for the older v0.3.2 failure.
* **STRONG HYPOTHESIS (not isolated):** the declaration of `MediaNotificationListener` is the trigger. Not proven because the other Full-only differences were not removed one at a time.

## What is NOT known
Google's internal classification, whether other Full-only items contribute, behaviour with a different install path (Play internal testing, adb), and the Vivo installer's own policy.

## One-variable experiment (diagnostic, not a bypass)
`assembleFullProbe` (new in 0.3.4) is the Full review build with **only** the notification-listener service removed from the merged manifest (`app/src/fullProbe/AndroidManifest.xml`; package `com.autotuneeq.mvp.probe`). It has no song detection and is never offered for distribution.
1. Build `assembleFullReview` and `assembleFullProbe`; record both SHA-256 values.
2. Put both in the same folder and install each through the **same** file-manager flow.
3. Record: Play Protect outcome, screenshot, time.

| Outcome | Conclusion |
|---|---|
| Probe installs, Full blocked | The listener declaration is the trigger for this phone/path (one-variable result; still scoped to this device and install source) |
| Both blocked | Another declaration/behaviour contributes; remove `RECORD_AUDIO`, then the foreground-service permissions, one overlay at a time (`<uses-permission android:name="..." tools:node="remove"/>`) |
| Both install | The earlier block did not reproduce; capture ADB evidence (below) |

## Evidence collection if anything is still unclear
Use `tools/collect_install_diagnostics.py` and the commands in `INSTALLATION_DIAGNOSTICS.md` on the phone: `adb install -r`, full `Failure [INSTALL_FAILED_…]` text, `logcat -b all`. On Android 13+ a sideloaded app must also be given **App info → ⋮ → Allow restricted settings** before the notification-access switch can be enabled; 0.3.4's disclosure dialog now says so and has an "Open app info" button.

## What this release does NOT do
It does not hide or rename the permission, change target SDK, re-sign to evade, or advise disabling Play Protect.
