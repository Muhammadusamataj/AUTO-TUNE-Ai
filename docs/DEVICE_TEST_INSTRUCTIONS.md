# Real-device test instructions (0.3.4 candidate)

Nothing below has been run by the author of this document: the review environment had no phone, emulator or Android build tools. Every result must come from the device.

## A. Vivo / Android 16 — Lite, own audio only (blind A/B)
**Setup.** Media volume LOW (start near 20 %; the tones are about -30 dBFS at mid frequencies, up to about -18 dBFS for the lowest bands, and hard-capped at -18 dBFS). Wired or Bluetooth headphones recommended: phone speakers cannot reproduce the lowest and highest bands, which would look like "effect not heard". Quiet room. Pause all other audio. Turn off built-in sound enhancement (Dolby / Hi-Fi / "audio effects") if the phone has it and **write down** whether it was on. Use a build that contains the A/B screen (0.3.4); 0.3.3 Lite does not have it.

**Run.** Lite → *Start blind A/B check* → *Start*. The app attaches an Equalizer to **its own** AudioTrack, lists the device's band centre frequencies, then plays ~25 trials. In each trial the same sine tone plays twice (sound 1, sound 2). On a *real* trial one of the two has a 6 dB cut on one hardware band and the other has the effect bypassed; on a *null* trial both are processed identically. Answer: *1st quieter*, *2nd quieter*, or *No difference*. Guessing is detectable (null trials, balanced sides).

**Read the result.** Per band: `AUDIBLE` (all 4 real trials correct), `INCONCLUSIVE`, `NOT HEARD`. Check `null trials: x false alarms`; a `WARNING` means the run is unreliable (noise, bias, unstable level) — repeat it.

| Software line in the report | Audible result | Meaning |
|---|---|---|
| `read-back matches` | AUDIBLE on mid bands | Own-session effect attaches, is controlled, and really changes level. This proves **only** Lite's own tone path. |
| `read-back matches` | NOT HEARD everywhere | Engine accepted the values but something bypasses it (route, OEM DSP, speaker limits). Retry with wired headphones; note the output route. |
| `READ-BACK MISMATCH` or attach failure | any | The effect engine does not apply the settings on this device/route. |
| any | distortion reported | Stop; send the report (levels are capped, so this would be a device/driver problem). |

**Restoration check.** After the A/B run, press *Restoration check*: reference tone (effect bypassed) → cut → the effect is **released while the tone keeps playing**. Tone 3 must sound like tone 1. Answer *Same* or *Different*.

**Send back.** *Copy diagnostic report*, paste it. It contains model, API level, band frequencies, per-presentation read-back and your answers. No audio and no personal data.

**Optional objective check (outside the app).** A second device with a spectrum-analyzer app held near the headphone/speaker can show the band level drop; room noise and microphone AGC make this indicative only.

## B. Android 8.1 car head unit (SAM5355 / MT6580, API 27) — checklist
Park the car, volume low, engine/ACC stable. Confirm the OS: `adb shell getprop ro.build.version.sdk` (expect 27), `adb shell getprop ro.product.cpu.abi` (expect an ARMv7 ABI; the APKs contain no native code).
1. Install **Lite** first (`adb install -r app-lite-review.apk` on your own device; this is a developer install, not a distribution method). Launch, rotate if the unit rotates, check layout on 1280×720.
2. Run section A. Record whether the unit's **built-in sound/EQ app** is on; it may sit after Android's mixer.
3. If the unit has a vendor EQ app: change its EQ while the Lite tone plays and note whether the tone changes. That tells you whether the vendor DSP is downstream of Android's effects.
4. Install **Full** only after step 2. Grant notification access (Settings may be missing on some units — the app falls back to general Settings), grant microphone permission only if you want the FFT screen.
5. Start monitoring, play a local player, YouTube Music, Spotify (one at a time). Watch the line `Player support: …`. Arm *per-app EQ*, **restart playback** (the player only announces its audio session when it creates it), then watch for `Effect attached · control granted`.
6. Pause/resume, skip tracks, switch players, kill the player, revoke notification access, stop monitoring. After each step confirm in the status text and by ear that the effect is released/restored.
7. Leave it running 30 minutes on 2 GB RAM: watch for the service being killed (monitoring stops) and for heat/battery drain.
Record per source: metadata ✔/✘, session event ✔/✘, control ✔/✘, **audible ✔/✘ (by listening/measurement only)**, cleanup ✔/✘.

## C. Modern phone — Full (Android 13–16)
Install Full by the route you intend to ship (Play internal testing), not by file manager, for any distribution test. On a sideloaded copy, Android 13+ requires **App info → ⋮ → Allow restricted settings** before notification access can be enabled. Then repeat B.5–B.6 with YouTube (expected: *unsupported* — verify), YouTube Music, Spotify and a local player.

## D. What a pass means
Only a `read-back matches` + AUDIBLE A/B + restoration "Same" result on the Vivo supports the statement "own-session EQ works on this device". It says nothing about YouTube, Spotify or other apps.

## E. Added in revision 2
**Before installing:** verify each APK's SHA-256 against `SHA256SUMS.txt` (see `GITHUB_ACTIONS_GUIDE.md` §4).
**FullProbe vs Full:** run `PLAY_PROTECT_DIAGNOSTICS.md` steps 1–2 and record the screenshot/result for Lite, FullProbe and Full side by side.
**SAM5355 music-player matrix (per player, one at a time, parked, volume low).** In Full: grant notification access, start monitoring, open *Diagnostics* later to copy the log.
| Player | Detected song? | `Player support:` line | Arm per-app EQ, restart playback → `Effect attached · control granted`? | Audible change when you move a slider and press Save? | Survives pause / skip / resume? | Released after stopping monitoring? |
|---|---|---|---|---|---|---|
| Local player A (note name/version) | | | | | | |
| Local player B | | | | | | |
| YouTube Music | | | | | | |
| Spotify | | | | | | |
| YouTube (expected: unsupported) | | | | | | |
Rules: write ✔/✘ only for what you observed; "Effect attached" is a software state, not proof of sound. Give any ✔ for "audible" only after the same Blind-A/B-style comparison (EQ bypassed vs cut, eyes closed, a second person switching) or a measurement. After each player: *Copy diagnostic log* and paste it with your table.
