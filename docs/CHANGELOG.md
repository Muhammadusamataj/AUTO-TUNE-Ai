# Changelog

## 0.3.4-candidate, revision 2 (this archive) — versionCode 7 unchanged
Revision 2 contains real fixes on top of the revision-1 archive, so it is labelled a revision, not a new version.
**Fixes** (details and evidence in `SOURCE_AUDIT_0_3_4.md`)
* SessionCoordinator: per-package claim cap (3) and 90 s expiry for claims from packages with no live media session.
* EqController: release no longer overwrites bands changed by someone else after our apply.
* MonitorService: poll cannot die from an exception; media callbacks coalesced (`PollGate`); foreground start once; explicit "notification access not granted" status; remembered per-app arming; 2-minute error cache; diagnostic events.
* TrackIdentity + server.py: richer, mirrored normalization (see below) and parity fixes (control characters, final sigma, whitespace set); legacy cache keys migrate on first use.
* AbSession/AbRig/LiteActivity: band frequencies in the result, device-limited cuts flagged.
* PlayerSupport: after 20 s of playback with no session event the status says per-app EQ is not active for that player.
**Features**
* Normalization: more promo suffixes (`(Lyrics)`, `[4K]`, `(HD)`, `(Audio)`, `[Full Song]`, `(Official Lyric Video)` …, repeated), ` - Official Video` / ` | Official Music Video` suffixes, YouTube ` - Topic` artist suffix, and an `Artist - ` title prefix **only when it repeats the artist field**. Remix/live/remaster/feat/edit/acoustic versions are never merged.
* `AppSettings`: per-app EQ arming is remembered until the user disables it (legacy global EQ is never remembered).
* `DiagLog`: in-memory diagnostics without song titles/artists (per-run salted references) + "Copy diagnostic log" button.
**Build/CI**
* `.github/workflows/android-build.yml` (source checks job + Android job), `tools/verify_manifests.py`, `tools/ci_summary.py`, `tools/identity_parity_fuzz.py`, `tools/make_dev_keystore.sh`, optional `signingConfigs.ci`, `.gitattributes`.
**Tests added:** `TrackIdentityTest` (6), `DiagAndPollTest` (4), `PlayerSupportTest` (+1), coordinator/EqController regression blocks, `AbAndCacheTest` updated, server identity test, `test_verify_manifests` (8), `test_ci_summary` (5), identity parity fuzz.
**Not changed:** manifests of Lite and Full, minSdk/targetSdk, dependencies.

## 0.3.4-candidate, revision 1
Blind A/B listening check (Lite), negative lookup cache, pinned manual profiles, Visualizer only while visible, private lock-screen notification, probe build type, documentation set. See `CHANGES_0_3_4.md`.
