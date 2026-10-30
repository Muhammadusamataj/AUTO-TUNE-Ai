# Review-environment check harness (NOT part of the app)
Used by the 0.3.4 auditor because Gradle, AGP and Maven Central were unreachable. It is no substitute for `./gradlew`.
* `javac` runs against `android.jar` stubs (API 35/27/26; third-party repo Sable/android-platforms) with a 6-line `ContextCompat` stub.
* `org/junit/{Test,Assert}.java` + `Run.java` re-implement just the JUnit 4 calls the project uses. Passing here means the assertions held under that shim, not that real JUnit passed.
* org.json is the real 20240303 source from github.com/stleary/JSON-java.
Run `bash tools/review-shim/run_review_checks.sh /path/to/android-35.jar /path/to/android-26.jar /path/to/org-json-src`.
