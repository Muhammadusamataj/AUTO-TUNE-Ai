import contextlib, io, os, shutil, tempfile, unittest
import ci_summary as cs

FIX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixtures", "ci")

def stage(*names):
    d = tempfile.mkdtemp()
    for n in names: shutil.copy(os.path.join(FIX, n), d)
    return d

class CiSummary(unittest.TestCase):
    def run_cli(self, *args):
        with contextlib.redirect_stdout(io.StringIO()) as out:
            code = cs.main(list(args))
        return code, out.getvalue()
    def test_passing_tests_and_warning_only_lint_succeed(self):
        code, out = self.run_cli("--tests", stage("TEST-ok.xml"), "--lint", stage("lint-results-fullDebug.xml"))
        self.assertEqual(code, 0); self.assertIn("8 tests, 0 failures", out); self.assertIn("0 errors, 2 warnings", out)
    def test_a_test_failure_fails_the_job(self):
        code, out = self.run_cli("--tests", stage("TEST-ok.xml", "TEST-fail.xml"))
        self.assertEqual(code, 1); self.assertIn("1 failures", out)
    def test_a_lint_error_fails_the_job(self):
        code, _ = self.run_cli("--tests", stage("TEST-ok.xml"), "--lint", stage("lint-results-liteDebug-bad.xml"))
        self.assertEqual(code, 1)
    def test_missing_results_are_failures_not_silent_passes(self):
        empty = tempfile.mkdtemp()
        self.assertEqual(self.run_cli("--tests", empty)[0], 1)
        self.assertEqual(self.run_cli("--tests", stage("TEST-ok.xml"), "--lint", empty)[0], 1)
    def test_real_codex_junit_files_parse(self):
        root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        d = os.path.join(root, "docs", "test-results", "v033", "full")
        if os.path.isdir(d):
            t, f, e, k, suites = cs.junit_totals(d); self.assertEqual((t, f, e), (16, 0, 0))

if __name__ == "__main__":
    unittest.main()
