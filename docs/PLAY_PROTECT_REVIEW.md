# Full app: installation block and owner review

## Observed issue

The user supplied a screenshot labelled **AutoTune EQ AI** showing Google Play Protect: “App blocked to protect your device” / “This app can request access to sensitive data.” This is an installation/security-policy block, not evidence of an unsupported Android version or broken APK signature. The device's exact APK hash and Google's detection classification were not supplied, so the specific trigger cannot be established here.

The shipped 0.3.1 APK's v2 signature verified correctly. Notification-listener capability is a plausible sensitive-access signal, but it is not a confirmed explanation of Google's decision. A valid signature does not establish safety or approval.

The user chose to retain the **full app**, and requested Android 8 support. Version 0.3.2 retains all features and lowers minimum API from 27 to **26 / Android 8.0**, with a guarded permission-query fallback. This explicitly supersedes the original task's minimum-27 constraint at the user's request.

## What changed, and what did not get verified

- Prominent disclosure appears before opening notification-listener settings; cancellation leaves access unchanged.
- Full song metadata detection, original test tone, cut-only EQ, optional external/legacy experiments, FFT, HTTPS lookup and offline cache remain.
- The `review` build is non-debuggable and development-signed using the existing debug certificate. It remains an evaluation build, not a production signing identity.
- `release` remains unsigned by default, ready for owner signing. No signing secrets or backend admin secrets are included.
- No permissions were hidden or removed to evade detection. Package identity and security checks remain unchanged. No instructions to disable protection or bypass blocked installation are provided.
- **Google review has not been submitted or approved. Installation and audible EQ on physical devices remain unverified.** Re-signing or changing debuggability does not guarantee that Play Protect will accept the app.

## Owner actions required for full-app distribution

1. In Android Studio, use **Build → Generate Signed Bundle / APK → Android App Bundle**, choose the `release` variant, and sign using your own secured upload key. Keep that key stable and private for updates. The supplied `app-release-unsigned.aab` is unsigned and must be signed before Play Console upload; it is not installable as an APK.
2. Use your [Google Play Console](https://play.google.com/console/) account to configure the app, owner details, privacy policy, data-safety information and required foreground-service declarations, then create an internal-testing release. Distribution/review decisions are made by Google; this workflow does not guarantee approval.
3. If Google Play Protect flags the legitimate app, submit the exact signed artifact and detection details through Google's [Play Protect appeal form](https://support.google.com/googleplay/android-developer/contact/protectappeals). Provide the version/package/certificate fingerprint, a reproducible screenshot, source explanation and consent flow. Google may require additional details or changes.
4. After any approval, test the exact distributed version on API 26, API 27 and a recent phone. Record installation, consent, metadata and acoustic results independently.

No Play Console account or distribution tools are connected in this workspace, and no submission to Google was authorized/performed. Owner signing and Google review are the remaining installation/distribution steps. A new download alone cannot be described as an unblock.

## Accurate technical explanation for the reviewer

- Package: `com.autotuneeq.mvp`; version 0.3.2-alpha, versionCode 5; minSdk 26, target/compileSdk 35.
- Notification listener service is protected by `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`. Access is granted by the user in Android Settings after disclosure. The app uses `MediaSessionManager` for playing title, artist and player; its listener class does not inspect notification message content, OTPs or credentials.
- `RECORD_AUDIO` is requested separately for optional, low-resolution Android Visualizer diagnostics. No microphone-recording pipeline or raw-audio storage/upload is implemented.
- `INTERNET` is used for optional, explicitly configured HTTPS-origin profile lookup. Only title and artist are sent to that endpoint. Default endpoint is empty. There is no embedded analytics endpoint or backend admin key.
- A user-started special-use foreground service monitors media metadata and optional diagnostics. API 33+ notification permission is requested for its notification. Foreground-service/store policy declarations still require owner review.
- Equalizer control uses documented Android audio effects; third-party session events are optional, opt-in, bounded and conservatively matched. No arbitrary-session discovery, accessibility service, device-admin capability, SMS/contact access, DRM bypass, rooting or hidden-API dependency is included.
- No trained model or worldwide music profile catalog is claimed. Constructor/control success is not proof of an audible audio effect.

The source ZIP contains the manifest, permission disclosure, implementation, tests and build results for independent review. Application hash and development certificate fingerprint are recorded in the current build report and integrity files.
