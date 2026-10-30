# Legitimate distribution checklist (owner actions; nothing here was submitted)

Why this matters: Play Protect's "App blocked" dialog is tied to **where the app is installed from** and **which sensitive permissions it declares**. Google documents it for browser/messaging/file-manager installs of apps declaring, among others, a notification listener. Changing signing, minSdk or in-app text does not change that decision.

1. **Production signing.** Create a private upload key (Android Studio → Generate Signed Bundle) and enrol in Play App Signing. Never reuse the shared `Android Debug` certificate for distribution. Keep keystore and passwords out of the repository. `release` is intentionally unsigned in this project.
2. **Play Console app.** Create the app, add owner/contact details, privacy-policy URL (host `PRIVACY_POLICY_DRAFT.md` after legal review), and complete **App content** forms (Data safety, ads, target audience).
3. **Foreground service declaration.** Targeting Android 14+ requires declaring each foreground-service type under *App content*: a description of the feature, the user impact if it is interrupted, and a link to a video showing the user starting the feature. This app uses `specialUse` (user-started media monitoring); the manifest already carries the required subtype property. Google states all types are subject to review.
4. **Prominent disclosure for notification access.** The in-app dialog (Full `MainActivity`) states what is read (title, artist, player via media sessions), what is sent (title+artist only, only to the user's own HTTPS origin), that no audio is uploaded, and how to revoke. Keep wording identical to the Data-safety answers. Check Google Play's current User Data and "Permissions and APIs that Access Sensitive Information" policies before submitting (not verified here).
5. **Microphone permission.** Declared only for the optional low-resolution Visualizer; state this in the Data-safety form ("not collected, processed on device"). Consider removing `RECORD_AUDIO` from the shipped Full variant if the FFT view is not essential.
6. **Test track first.** Upload the signed bundle to **internal testing**, install through the Play Store link on the Vivo and on the car panel. Installing from Play is a different source than a file manager; record the result separately from sideload results.
7. **If Play Protect still flags a Play-distributed or correctly signed build**, submit the exact signed artifact, package, version, certificate fingerprint and screenshot through Google's Play Protect appeal form, with the disclosure text and a description of the feature. Do not disable Play Protect or advise users to.
8. **Fallback product.** Keep Lite (no listener, no microphone, no foreground service) as the sideload-friendly edition; it is a smaller product, not a workaround.
9. **Before any release label:** real-device installs on API 26/27 and a current phone, audible EQ verification (A/B + measurement), cleanup verification, and the privacy/data-safety forms matching the shipped manifest.

## Internal-testing minimum (what Play Console will ask for)
1. Signed **Android App Bundle** (`bundleFullRelease`) from your own upload key (Play App Signing enabled). The workflow's APKs are evaluation builds and cannot be uploaded.
2. App content: privacy-policy URL, Data safety form (title/artist optionally sent to a user-configured server; no audio collected; microphone used on-device for the Visualizer only), target audience, ads (none).
3. Foreground-service declaration for `specialUse`: description of the user-started media monitoring, user impact if interrupted, and a short screen-recording URL showing how the user starts it.
4. Notification-access prominent disclosure (`PERMISSION_DISCLOSURE_DRAFT.md`) shown before the system settings screen. Verify Google's current policy text for notification-listener use before submitting (not verified in this project).
5. Testers list (email addresses) on the internal track; install through the Play Store link they receive.
6. Pre-launch report and any policy warnings reviewed before moving beyond internal testing.
