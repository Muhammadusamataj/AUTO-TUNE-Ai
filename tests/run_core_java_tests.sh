#!/bin/sh
set -eu
DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
javac -encoding UTF-8 -d "$TMP" "$DIR"/app/src/full/java/com/autotuneeq/mvp/SessionCoordinator.java \
 "$DIR"/app/src/full/java/com/autotuneeq/mvp/PlaybackPolicy.java \
 "$DIR"/tests/java/com/autotuneeq/mvp/TestSessionCoordinator.java \
 "$DIR"/tests/stubs/android/media/audiofx/Equalizer.java \
 "$DIR"/app/src/main/java/com/autotuneeq/mvp/EqController.java \
 "$DIR"/tests/java/com/autotuneeq/mvp/TestEqController.java \
 "$DIR"/tests/stubs/org/json/*.java \
 "$DIR"/app/src/main/java/com/autotuneeq/mvp/Profile.java \
 "$DIR"/app/src/main/java/com/autotuneeq/mvp/BandAnalyzer.java \
 "$DIR"/app/src/main/java/com/autotuneeq/mvp/EqMath.java \
 "$DIR"/app/src/main/java/com/autotuneeq/mvp/TrackIdentity.java \
 "$DIR"/app/src/main/java/com/autotuneeq/mvp/IdentityCase.java \
 "$DIR"/tests/java/com/autotuneeq/mvp/TestBandAnalyzer.java \
 "$DIR"/tests/java/com/autotuneeq/mvp/TestEqMath.java \
 "$DIR"/app/src/full/java/com/autotuneeq/mvp/SessionHints.java \
 "$DIR"/tests/java/com/autotuneeq/mvp/TestSessionHints.java
java -cp "$TMP" com.autotuneeq.mvp.TestBandAnalyzer

java -cp "$TMP" com.autotuneeq.mvp.TestEqMath

java -cp "$TMP" com.autotuneeq.mvp.TestEqController

java -cp "$TMP" com.autotuneeq.mvp.TestSessionHints

java -cp "$TMP" com.autotuneeq.mvp.TestSessionCoordinator
