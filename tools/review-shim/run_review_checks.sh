#!/bin/bash
# usage: run_review_checks.sh ANDROID35_JAR ANDROID26_JAR ORG_JSON_SRC_DIR   (needs a JDK or a JRE with jdk.compiler)
set -u
ROOT=$(cd "$(dirname "$0")/../.." && pwd); J35=$1; J26=$2; JSON=$3
JAVAC="java --module jdk.compiler/com.sun.tools.javac.Main"; command -v javac >/dev/null && JAVAC=javac
A=$ROOT/app/src; S=$ROOT/tools/review-shim; W=$(mktemp -d)
echo "== compile full/lite vs API 35 and 26 (errors at API 26 must only be SDK_INT-guarded symbols)"
for fl in full lite; do for J in "$J35" "$J26"; do
  mkdir -p $W/c; rm -rf $W/c/*; echo "-- $fl vs $(basename $J)"
  $JAVAC -source 17 -target 17 -encoding UTF-8 -Xlint:none -cp "$J" -d $W/c $S/stub/androidx/core/content/ContextCompat.java $A/main/java/com/autotuneeq/mvp/*.java $A/$fl/java/com/autotuneeq/mvp/*.java 2>&1 | grep -E "error:|symbol:" | sed "s#$A/##"
  echo "classes: $(ls $W/c/com/autotuneeq/mvp 2>/dev/null | wc -l)"
done; done
echo "== JUnit-shim tests (full source set)"
mkdir -p $W/shim $W/ut; $JAVAC -d $W/shim -encoding UTF-8 -Xlint:none $JSON/*.java $S/org/junit/*.java $S/Run.java 2>&1 | grep -v Note
$JAVAC -source 17 -target 17 -encoding UTF-8 -Xlint:none -cp "$J35:$W/shim" -d $W/ut $S/stub/androidx/core/content/ContextCompat.java $A/main/java/com/autotuneeq/mvp/*.java $A/full/java/com/autotuneeq/mvp/*.java $A/test/java/com/autotuneeq/mvp/*.java $A/testFull/java/com/autotuneeq/mvp/*.java 2>&1 | grep error
java -cp "$W/ut:$W/shim:$J35" Run com.autotuneeq.mvp.CoreRegressionTest com.autotuneeq.mvp.TrackIdentityTest com.autotuneeq.mvp.IdentityCaseTest com.autotuneeq.mvp.PresetsAndExchangeTest com.autotuneeq.mvp.RecommenderTest com.autotuneeq.mvp.AbAndCacheTest com.autotuneeq.mvp.DiagAndPollTest com.autotuneeq.mvp.SessionRegressionTest com.autotuneeq.mvp.PlayerSupportTest | grep -E "FAIL|RESULT"
echo "== standalone Java programs"; sed "s#^javac #$JAVAC #" $ROOT/tests/run_core_java_tests.sh > $ROOT/tests/_r.sh; sh $ROOT/tests/_r.sh 2>&1 | grep -v Note; rm -f $ROOT/tests/_r.sh
echo "== identity parity (Java vs Python)"; PATH="$(dirname "$(command -v javac 2>/dev/null || echo /usr/bin/javac)"):$PATH" python3 "$ROOT/tools/identity_parity_fuzz.py"
echo "== backend + tools"; (cd $ROOT/server && python3 -m unittest test_server 2>&1 | tail -3); (cd $ROOT && python3 -m unittest discover -s tools -p 'test_*.py' 2>&1 | tail -3)
