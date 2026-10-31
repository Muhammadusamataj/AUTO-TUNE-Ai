# CI failure diagnosis: the "80 Java/Python identity parity mismatches"

**Status of this document:** the cause below was reproduced locally (JDK 17.0.20 + Python 3.12.3, the same pair the workflow uses) and the fix was verified locally. The GitHub Actions run itself could not be read (run logs need a signed-in session), so the *first real CI run after this change* is still the proof.

## Reproduction
`python3 tools/identity_parity_fuzz.py --full` under JDK 17 / Python 3.12 → `258172 cases, 80 mismatches` (40 code points × 2 test strings). Under JDK 21 the same test passed, which is why it was not visible in the earlier review environment.

## Root cause (verified, not a logic bug)
The 40 code points are letters added to Unicode in versions 14–15 (Glagolitic U+2C2F, Latin U+A7C0/U+A7D0/U+A7D6/U+A7D8, Vithkuqi U+10570–U+10595). Python 3.12 knows them (Unicode 15.0) and lower-cases them; JDK 17 implements Unicode 13 and leaves them unchanged. The app and the server therefore computed **different identity keys for the same text**. The same skew exists on phones: Android 8.0 ships Unicode 9, 8.1 Unicode 10, Android 16 Unicode 15+.

A second, hidden divergence of the same kind was found while fixing the first (the old fuzz did not test it): canonical reordering of combining marks added after Unicode 13 (208 cases, e.g. U+0898) differs between JDK 17 and Python 3.12.

## Fix
* `tools/data/unicode10_simple_lowercase.tsv` — Simple_Lowercase_Mapping frozen at Unicode 10.0.0 (1,304 mappings, source and SHA-256 recorded in the file).
* `tools/data/unicode9_assigned_ranges.tsv` — code points assigned in Unicode 9.0.0 (638 ranges). NFC is applied only to text made entirely of those; any other text is left un-normalized on both sides.
* `tools/gen_identity_table.py` generates `IdentityCase.java` (app) and `identity_case_table.py` (server) from those two files; `--check` fails CI if the generated files are stale.
* `TrackIdentity.java` and `server.py` use only the generated tables (no runtime `toLowerCase()`/`lower()`), so keys are identical on every JDK, Python and Android version.
* Old caches: `ProfileStore.get` still finds profiles saved under the previous key format and migrates them on first use.
* Behaviour change to note: `İ` (U+0130) now lower-cases to `i` (simple mapping) instead of the two-character full mapping; Greek final sigma folds to sigma.

## Verification (local, JDK 17 and JDK 21, Python 3.12.3)
`identity_parity_fuzz.py --full`: 2,224,256 code-point cases (every code point U+0000–U+10FFFF except surrogates, in several positions), 9,220 combining-mark order cases, 38,868 YouTube-style noise cases → **0 mismatches** on both JDKs. `--full` is what the workflow runs; the test stays enabled and nothing was skipped or relaxed.

## Other CI hardening in the same change
* `javac -encoding UTF-8` in every script (JDK 17 defaults to the platform charset; a POSIX locale made the sources with `·` fail to compile).
* `tests/run_core_java_tests.sh` now also compiles `IdentityCase.java`.
* Workflow: `gen_identity_table.py --check` added to the source-checks job; stray nested workflow copies under `.github/.github/…` and duplicate root-level files removed.

## What is still unknown
Whether the Android job (Gradle, lint, assemble) passes: it has never been observable. If it fails, send the failing step's last ~60 lines.
