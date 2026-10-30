# Play Protect install-block diagnostics (no bypass)

Observed on the owner's Vivo (Android 16) for **Full 0.3.3**: Play Protect "App blocked to protect your device — This app can request access to sensitive data", then the installer's "App not installed", when installing from the file manager. **Lite installs and runs.** Google documents this block for apps from browsers, messaging apps and file managers that declare certain sensitive permissions, including a notification listener. The only member of that list that Full declares is the notification-listener service.

**Status:** the *type* of block is verified from the dialog text; that the notification-listener declaration is the trigger is a **strong, still unisolated hypothesis**. The steps below isolate it. They do not hide, rename or disable anything, and nothing here asks you to turn Play Protect off.

## Step 1 — get three comparable APKs from CI
`AutoTuneEQ-Lite-review.apk`, `AutoTuneEQ-Full-review.apk`, `AutoTuneEQ-FullProbe-review.apk` from the same workflow run (same signing key). The workflow's **Merged-manifest verification** must say `PASS Probe vs Full`: that proves, by inspecting the built APKs, that the probe differs from Full only by the removed `MediaNotificationListener` service (same permissions, same components, same SDK levels, same export settings).

## Step 2 — verify checksums, then install each the same way
Same phone, same file manager, same folder, one after another. Photograph/record each result.

| Build | Declares the notification listener? | Expected if the hypothesis is right |
|---|---|---|
| Lite | no | installs (already observed for 0.3.3) |
| FullProbe | **no** (everything else as Full) | installs |
| Full | yes | blocked |

| Result pattern | Meaning |
|---|---|
| Lite ✔, Probe ✔, Full ✘ | The listener declaration is the trigger on this phone/path (one-variable result; scoped to this device and install source) |
| Probe ✘ too | Something else in Full contributes. Next probes remove one thing at a time (`RECORD_AUDIO`, then the foreground-service permissions) using another overlay under `app/src/fullProbe/` |
| Probe ✔, Full ✔ | The block did not reproduce; collect evidence below |

## Step 3 — if installation fails without a Play Protect dialog
Use `tools/collect_install_diagnostics.py` or the commands in `INSTALLATION_DIAGNOSTICS.md` (`adb install -r`, complete `Failure [INSTALL_FAILED_…]` text, `adb logcat -b all`). Different signer than an already-installed copy ⇒ uninstall that copy first (see `GITHUB_ACTIONS_GUIDE.md` §5).

## Step 4 — legitimate distribution of Full
Google Play internal testing with your own upload key, the declarations and disclosures in `PLAY_DISTRIBUTION_CHECKLIST.md`, and an appeal through Google's Play Protect appeal form if a correctly signed, correctly disclosed build is still flagged. A developer install onto your own phone through Android Studio / `adb` is a normal development workflow, not a way to distribute the app to others.

## What not to do
Do not turn off Play Protect, use third-party installers to avoid the dialog, strip or rename the notification listener to hide it from review, or redistribute the probe build. The probe has no song detection and exists only for this experiment.
