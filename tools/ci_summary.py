#!/usr/bin/env python3
"""Summarise JUnit XML and Android lint XML into Markdown for the GitHub Actions job summary.

Exit status is 1 when any test failed/errored or lint reported an Error, so the step also fails the job.
Usage: ci_summary.py --tests DIR [--lint DIR] [--out FILE]
"""
import argparse, glob, os, sys
import xml.etree.ElementTree as ET


def junit_totals(directory):
    tests = failures = errors = skipped = 0
    suites = []
    for path in sorted(glob.glob(os.path.join(directory, "**", "*.xml"), recursive=True)):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        nodes = [root] if root.tag == "testsuite" else root.findall("testsuite")
        for s in nodes:
            t, f, e, k = (int(s.get(x, "0")) for x in ("tests", "failures", "errors", "skipped"))
            tests += t; failures += f; errors += e; skipped += k
            suites.append((s.get("name", os.path.basename(path)), t, f, e, k))
    return tests, failures, errors, skipped, suites


def lint_totals(directory):
    errors = warnings = 0
    files = sorted(glob.glob(os.path.join(directory, "**", "lint-results-*.xml"), recursive=True))
    for path in files:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for issue in root.iter("issue"):
            sev = issue.get("severity", "")
            if sev in ("Error", "Fatal"): errors += 1
            elif sev == "Warning": warnings += 1
    return errors, warnings, [os.path.basename(f) for f in files]


def main(argv=None):
    ap = argparse.ArgumentParser(); ap.add_argument("--tests", required=True); ap.add_argument("--lint"); ap.add_argument("--out")
    a = ap.parse_args(argv)
    t, f, e, k, suites = junit_totals(a.tests)
    md = ["### Unit tests (Gradle, real JUnit)", "", f"**{t} tests, {f} failures, {e} errors, {k} skipped** in {len(suites)} suites", ""]
    md += [f"- {n}: {tt} tests, {ff} failures, {ee} errors" for n, tt, ff, ee, _ in suites]
    bad = (f + e) > 0 or t == 0
    if t == 0: md.append("\n**No test results found: treated as a failure.**")
    if a.lint:
        le, lw, files = lint_totals(a.lint)
        md += ["", "### Android lint", "", f"**{le} errors, {lw} warnings** across {len(files)} reports (" + ", ".join(files) + ")"]
        if le or not files: bad = True
        if not files: md.append("\n**No lint reports found: treated as a failure.**")
    text = "\n".join(md) + "\n"
    print(text)
    if a.out:
        with open(a.out, "a", encoding="utf-8") as fh: fh.write(text)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
