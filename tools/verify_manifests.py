#!/usr/bin/env python3
"""Verify the merged-manifest expectations of the three evaluation APKs.

Input is the decoded merged manifest of each APK (CI uses `apkanalyzer manifest print app.apk`). Checks:
  Lite   : no notification listener, no services, none of Full's sensitive permissions.
  Full   : notification listener present and protected by BIND_NOTIFICATION_LISTENER_SERVICE, monitor service present.
  Probe  : identical to Full except that the notification-listener service is gone (and the package suffix).
Exit status 1 on any violation. This inspects declarations only; it says nothing about runtime behaviour.
"""
import argparse, sys
import xml.etree.ElementTree as ET

NS = "{http://schemas.android.com/apk/res/android}"
BIND = "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
LISTENER = "com.autotuneeq.mvp.MediaNotificationListener"
MONITOR = "com.autotuneeq.mvp.MonitorService"
FULL_ONLY = {"android.permission.RECORD_AUDIO", "android.permission.FOREGROUND_SERVICE",
             "android.permission.FOREGROUND_SERVICE_SPECIAL_USE", "android.permission.POST_NOTIFICATIONS"}
COMMON = {"android.permission.INTERNET", "android.permission.MODIFY_AUDIO_SETTINGS"}
SENSITIVE_LIST = {BIND, "android.permission.RECEIVE_SMS", "android.permission.READ_SMS",
                  "android.permission.BIND_ACCESSIBILITY_SERVICE"}   # Google's documented Play Protect install-block list
COMPONENT_TAGS = ("activity", "activity-alias", "service", "receiver", "provider")


class Manifest:
    def __init__(self, text):
        root = ET.fromstring(text.encode("utf-8") if text.lstrip().startswith("<?xml") else text)
        self.package = root.get("package", "")
        sdk = root.find("uses-sdk")
        self.min_sdk = int(sdk.get(NS + "minSdkVersion", "0")) if sdk is not None else 0
        self.target_sdk = int(sdk.get(NS + "targetSdkVersion", "0")) if sdk is not None else 0
        self.permissions = {e.get(NS + "name") for e in root.findall("uses-permission")}
        self.declared = {e.get(NS + "name") for e in root.findall("permission")}
        app = root.find("application")
        self.app_attrs = {k.replace(NS, ""): v for k, v in (app.attrib.items() if app is not None else [])}
        self.components = {}
        if app is not None:
            for tag in COMPONENT_TAGS:
                for e in app.findall(tag):
                    attrs = {k.replace(NS, ""): v for k, v in e.attrib.items()}
                    self.components[(tag, attrs.get("name", ""))] = attrs

    def norm(self, name):
        """Replace this APK's package prefix so package-scoped names (custom permission) compare equal."""
        return "<PKG>" + name[len(self.package):] if self.package and name.startswith(self.package + ".") else name

    def norm_perms(self):
        return {self.norm(p) for p in self.permissions}

    def services(self):
        return {n for (t, n) in self.components if t == "service"}


def load(path):
    with open(path, encoding="utf-8") as f:
        return Manifest(f.read())


def check_lite(m):
    errors = []
    if not m.package.endswith(".lite"): errors.append(f"Lite package should end with .lite, got {m.package}")
    if m.services(): errors.append(f"Lite must declare no services, found {sorted(m.services())}")
    for p in sorted((FULL_ONLY | SENSITIVE_LIST) & m.permissions): errors.append(f"Lite must not request {p}")
    if not COMMON <= m.permissions: errors.append("Lite lost a permission it needs: " + ", ".join(sorted(COMMON - m.permissions)))
    if m.min_sdk != 26: errors.append(f"Lite minSdk expected 26, got {m.min_sdk}")
    return errors


def check_full(m):
    errors = []
    if m.package != "com.autotuneeq.mvp": errors.append(f"Full package expected com.autotuneeq.mvp, got {m.package}")
    listener = m.components.get(("service", LISTENER))
    if listener is None: errors.append("Full must declare MediaNotificationListener")
    elif listener.get("permission") != BIND: errors.append("MediaNotificationListener must be protected by BIND_NOTIFICATION_LISTENER_SERVICE")
    if ("service", MONITOR) not in m.components: errors.append("Full must declare MonitorService")
    elif m.components[("service", MONITOR)].get("exported") not in ("false", "0x0", "0"): errors.append("MonitorService must not be exported")
    for p in sorted((FULL_ONLY | COMMON) - m.permissions): errors.append(f"Full is missing {p}")
    return errors


def check_probe(m):
    errors = []
    if not m.package.endswith(".probe"): errors.append(f"Probe package should end with .probe, got {m.package}")
    if ("service", LISTENER) in m.components: errors.append("Probe must NOT declare MediaNotificationListener")
    if BIND in m.permissions: errors.append("Probe must not request BIND_NOTIFICATION_LISTENER_SERVICE")
    for (t, n), a in m.components.items():
        if a.get("permission") == BIND: errors.append(f"Probe component {n} is still a notification listener")
    return errors


def compare_probe_to_full(probe, full):
    """The probe must differ from Full in exactly one respect: the notification-listener service."""
    errors = []
    if probe.norm_perms() != full.norm_perms():
        errors.append("permission sets differ: only-in-full=%s only-in-probe=%s" %
                      (sorted(full.norm_perms() - probe.norm_perms()), sorted(probe.norm_perms() - full.norm_perms())))
    only_full = set(full.components) - set(probe.components)
    only_probe = set(probe.components) - set(full.components)
    if only_full != {("service", LISTENER)}: errors.append(f"components removed from Full must be exactly the listener, got {sorted(only_full)}")
    if only_probe: errors.append(f"probe adds components: {sorted(only_probe)}")
    for key in set(full.components) & set(probe.components):
        for attr in ("exported", "permission", "foregroundServiceType", "enabled"):
            if full.components[key].get(attr) != probe.components[key].get(attr):
                errors.append(f"{key[1]}: attribute {attr} differs ({full.components[key].get(attr)} vs {probe.components[key].get(attr)})")
    if (probe.min_sdk, probe.target_sdk) != (full.min_sdk, full.target_sdk): errors.append("SDK levels differ between probe and Full")
    for attr in ("allowBackup", "usesCleartextTraffic", "debuggable"):
        if full.app_attrs.get(attr) != probe.app_attrs.get(attr): errors.append(f"application attribute {attr} differs")
    return errors


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--lite", required=True); ap.add_argument("--full", required=True); ap.add_argument("--probe", required=True)
    ap.add_argument("--summary-md")
    a = ap.parse_args(argv)
    lite, full, probe = load(a.lite), load(a.full), load(a.probe)
    results = [("Lite", check_lite(lite)), ("Full", check_full(full)), ("Probe", check_probe(probe)),
               ("Probe vs Full (only the listener removed)", compare_probe_to_full(probe, full))]
    lines, failed = [], False
    for name, errs in results:
        if errs:
            failed = True; lines.append(f"FAIL {name}"); lines += [f"  - {e}" for e in errs]
        else: lines.append(f"PASS {name}")
    lines.append("")
    lines.append("Declared Play-Protect-listed sensitive permissions: Lite=%s Full=%s Probe=%s" % (
        sorted(SENSITIVE_LIST & lite.permissions) or "none",
        sorted(SENSITIVE_LIST & full.permissions) or "none (listener is a service binding)",
        sorted(SENSITIVE_LIST & probe.permissions) or "none"))
    print("\n".join(lines))
    if a.summary_md:
        with open(a.summary_md, "w", encoding="utf-8") as f:
            f.write("### Merged-manifest verification\n\n```\n" + "\n".join(lines) + "\n```\n")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
