# Feature-by-feature gap analysis and implementation plan

Status words: **DONE+TESTED (logic)** = implemented and unit/mock tested here; **BUILT, UNRUN** = compiled but no Gradle/device run; **PARTIAL**; **NOT BUILT**; **NOT POSSIBLE** (platform). "Verified on hardware" appears nowhere.

## 1. Critical problems
| Item | State |
|---|---|
| 80 Java/Python parity mismatches | **DONE+TESTED**: root cause = Unicode-version skew; frozen tables; full-range parity fuzz enabled in CI (`CI_DIAGNOSIS_PARITY.md`) |
| Compilation / Gradle / lint / manifest / Actions | javac vs API 35/26 PASS; Gradle, lint, AGP manifest merge **BLOCKED here** (no SDK/Maven access); Actions run unreadable |
| Crashes, leaks, session lifecycle, threading | reviewed in `SOURCE_AUDIT_0_3_4.md`; mock tests pass; no device run |
| Older saved profiles preserved | **DONE+TESTED (logic)**: legacy-key migration in `ProfileStore.get`; instrumented test exists (unrun) |
| Android 8.1 + 16 | API-26 symbol check PASS; Android 16 only via owner's Lite 0.3.3 install report |

## 2. Equalizer
| Requirement | State |
|---|---|
| Five-band EQ, precise dB | BUILT, UNRUN: ±3.0 dB in 0.1 steps (Lite, Full). Range intentionally ±3 (cut-only shift ⇒ ≤ 6 dB attenuation) |
| Presets Flat/Bass/Vocal/Rock/Pop/Jazz/Custom | **DONE+TESTED (logic)**; wired into Lite (buttons, selection restored at start). Full UI: not yet |
| Save / edit / delete / import / export | save & load per song BUILT; **import/export validation engine DONE+TESTED**; list/edit/delete/import UI **NOT BUILT** |
| Restore selected profile | Lite BUILT, UNRUN (preset + custom values); Full not yet |
| Safe low-volume test | BUILT: own tone ≈ -30 dBFS, capped; blind A/B check (Lite) unrun on device |
| Session detection/management | BUILT, UNRUN, experimental, opt-in (see `ANDROID_AUDIO_RESTRICTIONS.md`) |
| Auto profile from authorized metadata | BUILT, UNRUN (Full) |
| Neutral fallback | BUILT + mock-tested |
| Effect status attached / configured / audibly verified | strings BUILT; "audibly verified" only possible through the Lite A/B check |
| Spectrum | BUILT (Full, optional mic permission, only while visible) |
| More bands, preamp, balance | **NOT BUILT**; feasibility in `ANDROID_AUDIO_RESTRICTIONS.md` |

## 3. Intelligence
| Requirement | State |
|---|---|
| Rule-based recommender: saved → online → genre → neutral, confidence + explanation | **DONE+TESTED (logic)**; shown as text in Full; honest label "rule-based, not a trained model" |
| Genre suggestions | uses the player's own genre metadata only (often empty); whole-word, rightmost-keyword mapping to 5 classes |
| Preference learning | **DONE+TESTED (logic)**: bounded per-class offsets (±1.5 dB, rate 0.3), "not for me", reset; learns only from an explicit Save |
| Local-first, optional online lookup | BUILT (HTTPS only, explicit server entry, negative cache 6 h / error cache 2 min) |
| Trained ML model | **NOT BUILT, deliberately.** No training data, licence or evaluation exists; nothing is called AI that is not. A future model would need a documented dataset, licence, held-out evaluation and on-device size/latency budget |
| Song analysis / worldwide coverage | **NOT POSSIBLE here / not claimed**: the catalogue is empty; Android gives no raw audio of other apps |

## 4. Premium UI (9 screens, phone portrait + car landscape, light/dark)
Interactive **mockup** delivered (`preview/`); the Android implementation is **PARTIAL**: a simple single-screen Full UI and a Lite UI exist. Per-screen status is printed in the preview's side panel. Building the nine native screens is deliberately scheduled **after** the first green CI run, because a large UI rewrite that cannot be compiled by Gradle or run on a device here would risk the first real build.

## 5. Previews
HTML interactive simulation (DONE, tested in headless Chromium: 2 devices × 2 themes × 9 screens, no JS errors, no horizontal overflow); 24 PNG renders of it; **no real Android screenshots exist** (no emulator/device).

## 6. Security and privacy
No new permissions; manifests of Lite/Full unchanged; no hidden capture; no notification-body reading (listener class is empty); HTTPS-only lookup; no embedded secrets; import is a validated trust boundary; diagnostic log is title-free. Stable signing: optional keystore secrets in CI. Install-failure diagnostics: FullProbe + `PLAY_PROTECT_DIAGNOSTICS.md`.

## 7. Verification matrix
See `TEST_MATRIX.md`.

## 8. Plan (priority order) and where we are
1. **Done this change:** CI failure root cause + fix, repo hygiene, frozen identity tables, parity fuzz across all Unicode, UTF-8 scripts, workflow table check.
2. **Done this change:** presets, import/export engine, recommender + learning, Lite preset wiring, Full genre suggestion wiring, UI preview + renders.
3. **Next (needs a green CI first):** run the workflow, fix whatever Gradle/lint/AGP reports; install Lite on the Vivo and run the blind A/B; FullProbe vs Full install comparison.
4. **Then:** native screens in this order — Equalizer (curve + presets, car layout), Saved Song Profiles (list/edit/delete/import/export with SAF), Settings & Permissions, Diagnostics, Now Playing, AutoTune AI, Home, Preset Library, Compatibility — each with instrumented tests, built on CI before the next one.
5. **Then:** `DynamicsProcessing` (API 28+) for own-session extra bands/preamp, only after the 5-band path is audibly verified.
6. **Release gates:** real-device installs on API 26/27 and 36, audible verification (A/B + measurement), cleanup verification, Play internal testing, privacy/data-safety forms.
