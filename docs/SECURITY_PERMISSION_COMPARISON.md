# Security and permission comparison (0.3.3 evaluation APKs and 0.3.4 sources)

Sources of evidence: **[APK]** parsed from the supplied Lite APK by the auditor (androguard); **[LOG]** Codex's `aapt` dump of the Full APK (`docs/logs/v033/apk/full-manifest.txt`, not re-parsed — the Full APK was not supplied); **[SRC]** source manifests.

| Item | Lite `com.autotuneeq.mvp.lite` [APK] | Full `com.autotuneeq.mvp` [LOG/SRC] | FullProbe `…mvp.probe` [SRC, not built] |
|---|---|---|---|
| Version | 0.3.3 (6) → 0.3.4 (7) | same | same |
| minSdk / targetSdk | 26 / 35 | 26 / 35 | 26 / 35 |
| Signer | CN=Android Debug, SHA-256 `681b22c4…594cd7` | same | same (development key) |
| Native libs | none | none | none |
| INTERNET | yes (optional manual lookup) | yes | yes |
| MODIFY_AUDIO_SETTINGS | yes (own effect) | yes | yes |
| RECORD_AUDIO | **no** | yes (diagnostic FFT) | yes |
| FOREGROUND_SERVICE / _SPECIAL_USE | **no** | yes | yes |
| POST_NOTIFICATIONS | **no** | yes | yes |
| Services | **none** | `MonitorService` (not exported); **`MediaNotificationListener` (exported, `BIND_NOTIFICATION_LISTENER_SERVICE`)** | `MonitorService` only |
| Activities | `LiteActivity` | `MainActivity` | `MainActivity` |
| AndroidX extras | `ProfileInstallReceiver` (protected by `DUMP`), `InitializationProvider` (not exported) | same | same |
| Play Protect "sensitive permission" list (SMS, notification listener, accessibility) | none declared | **notification listener declared** | none declared |

## Threat notes (Full)
* **Receiver spoofing:** the audio-session receiver is registered exported (other apps must be able to send OPEN/CLOSE). Sender identity is available only on API 34+ when the sender opts in; otherwise events are untrusted claims. Mitigations present: bounded queue, rate limit, package must be the *playing* player before attach, conflicting claims rejected, cut-only gains. Residual: a hostile app can cause a denial of service or attach the cut-only effect to a session ID of its choice.
* **Notification access** is a broad capability. The listener class is empty (no `onNotificationPosted`); song data comes from `MediaSessionManager`. Lock-screen notification no longer contains the song title (0.3.4).
* **Network:** HTTPS origin only, no redirects, 16 KiB cap, strict JSON, hostile values rejected. Title+artist are the only data sent; unknown songs are no longer re-requested on every pause/resume (0.3.4 negative cache, 6 h).
* **Storage:** SharedPreferences, backup/transfer excluded. Manual profiles are now pinned against eviction.
* **No** logging of song data (no `Log` calls in main sources), no dynamic code loading seen in the sources read.

## Automated verification (revision 2)
`tools/verify_manifests.py` runs in the CI workflow on the **merged manifests decoded from the built APKs** (`apkanalyzer manifest print`) and fails the job unless: Lite has no services and none of Full's sensitive permissions; Full declares `MediaNotificationListener` protected by `BIND_NOTIFICATION_LISTENER_SERVICE` and a non-exported `MonitorService`; the probe has no listener and **equal permissions, components, export flags, SDK levels and backup/cleartext settings to Full except the listener service**. The script itself is tested against the decoded manifest of the real 0.3.3 Lite APK and synthetic Full/Probe manifests (including negative cases). It has not yet run on real CI output.
