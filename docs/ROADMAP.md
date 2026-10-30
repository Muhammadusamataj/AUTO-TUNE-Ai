> Update: the revised debug APK, JVM tests and lint now build successfully. Device/acoustic verification and production work remain. See [BUILD_AND_TEST_REPORT.md](BUILD_AND_TEST_REPORT.md).

# Production roadmap after this MVP

## Stage 1: compile + device compatibility proof
- Build debug APK with SDK 35 and exercise on a real API 27 SAM5355 and at least one recent Android phone.
- Validate app lifecycle, background monitoring, notification listener access, and recorded/actual hardware API version.
- Establish whether external session-0 Equalizer actually alters output on each device; do not infer from success code alone.

## Stage 2: audio profiles and calibration
- Create licensed/reference music test set and analyze original PCM at server using high-resolution FFT, LUFS, crest factor, dynamic range, reference-normalized spectra.
- Define reproducible speaker/headphone calibration and tests. A song waveform alone cannot determine correct EQ for a car cabin.
- Per-recording ID and version matching, explicit source provenance and confidence.
- Cache profiles for offline mode and support manual corrections.

## Stage 3: hardware adapters
- Android media-session-specific effects for cooperative local players.
- Verified OEM head-unit DSP vendor integrations (no speculative hidden MCU commands).
- Additional Android 10+ capture route via MediaProjection/AudioPlaybackCapture for sources that allow capture; respect user consent every session, privacy and SDK requirements.
- If global integration unavailable, present settings as a recommendation, never pretend they were applied.

## Stage 4: privacy, reliability & distribution
- Secure HTTPS profile service and optional account/auth; privacy notice with listening metadata handling.
- Test thermal load on weak quad-core CPUs, background restrictions, Bluetooth routing, phone calls and navigation prompts.
- Accessibility, localized UI, crash reports with opt-in, signed builds, Play Store policy review for foreground services and audio permissions.
- Automated Android instrumented tests and audio-quality A/B measurements.
