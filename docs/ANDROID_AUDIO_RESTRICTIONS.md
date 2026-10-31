# What Android allows (and what this app therefore does not claim)

* **No universal third-party EQ.** Android audio effects attach to an *audio session*. A normal app may attach to its own session, or to a session ID a player announces on purpose (`ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`). It cannot enumerate other apps' sessions, and the old global "session 0" path is deprecated and OEM-dependent.
* **YouTube / Spotify.** Spotify and YouTube Music have been reported to announce sessions; the main YouTube app has been reported not to. Neither is verified by this project. The app shows a per-player status derived from events it actually received and says "unsupported" or "unverified" otherwise.
* **Android 8.1 car head unit.** Many units route audio through a vendor DSP/MCU that sits after Android's mixer; Android effects may then have no audible result. Only an on-device test can tell.
* **More bands, preamp, balance.** `Equalizer` exposes the device's own band count (usually 5). More bands/preamp need `DynamicsProcessing` (API 28+, not on 8.x) and are only realistic for the app's own session first. Balance is only possible on the app's own `AudioTrack`. These are **not implemented** and not promised.
* **Safety.** All gains are applied cut-only (the largest boost is subtracted from every band), so the overall level never rises and clipping from the EQ itself cannot occur; there is no limiter and none is claimed.
* **Notification access.** Required by Android to read media sessions of other apps; it is a sensitive permission, which Play Protect treats specially for sideloaded installs. Lite exists for users who do not want to grant it.
* **Microphone.** `Visualizer` needs it; the app never records.
