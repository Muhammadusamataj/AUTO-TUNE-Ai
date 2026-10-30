# In-app permission disclosure texts (draft; the Full app already shows the first one)

**Notification access (Full) — shown before the system settings screen, with Cancel / Open app info / Open system settings**
> Android notification access is a sensitive permission. AutoTune EQ uses it only to read the playing song's title, artist and player through Android media sessions. It does not read the text of your notifications, messages, one-time codes or account data. If you save an optional HTTPS server address, the song title and artist are sent to that server to look up an EQ profile; no audio is uploaded. On Android 13 and newer, an app installed from a file or browser may need App info › menu › Allow restricted settings before the notification-access switch can be turned on. You can revoke access at any time in Android settings; without it, manual profiles and the app's own test tone still work.

**Microphone (Full, optional)**
> The microphone permission is requested only because Android's Visualizer API requires it to draw a low-resolution spectrum on this screen. AutoTune EQ never records, stores or uploads audio. The spectrum runs only while this screen is visible. You can decline: everything else keeps working.

**Per-app EQ experiment (Full)**
> Some music players announce their audio session to equalizer apps; others do not. AutoTune EQ then applies a gentle, cut-only EQ to that player only. Announcements can be forged by other apps, so enable this only for a player you trust. The choice is remembered until you press Disable. The deprecated global test always starts off.

**Online profile lookup (Full and Lite)**
> When you save a server address, the song title and artist are sent to that server to look up an EQ profile. Clear the address to stay offline.
