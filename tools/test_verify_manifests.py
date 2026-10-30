import os, tempfile, unittest, contextlib, io
import verify_manifests as vm

HERE = os.path.dirname(os.path.abspath(__file__))

def full_xml(package="com.autotuneeq.mvp", listener=True, drop_perms=(), extra_perms=(), listener_perm=vm.BIND,
             monitor_exported="false", extra_service=False, backup="false"):
    perms = ["android.permission.INTERNET", "android.permission.MODIFY_AUDIO_SETTINGS", "android.permission.RECORD_AUDIO",
             "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
             "android.permission.POST_NOTIFICATIONS", package + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"]
    perms = [p for p in perms if p not in drop_perms] + list(extra_perms)
    uses = "\n".join(f'  <uses-permission android:name="{p}"/>' for p in perms)
    lst = (f'<service android:name="com.autotuneeq.mvp.MediaNotificationListener" android:permission="{listener_perm}" android:exported="true"/>'
           if listener else "")
    extra = '<service android:name="com.example.Extra" android:exported="false"/>' if extra_service else ""
    return f'''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}">
  <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/>
{uses}
  <permission android:name="{package}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" android:protectionLevel="0x00000002"/>
  <application android:allowBackup="{backup}" android:usesCleartextTraffic="false">
    <activity android:name="com.autotuneeq.mvp.MainActivity" android:exported="true"/>
    <service android:name="com.autotuneeq.mvp.MonitorService" android:exported="{monitor_exported}" android:foregroundServiceType="specialUse"/>
    {lst}
    {extra}
    <provider android:name="androidx.startup.InitializationProvider" android:exported="false" android:authorities="{package}.androidx-startup"/>
    <receiver android:name="androidx.profileinstaller.ProfileInstallReceiver" android:permission="android.permission.DUMP" android:enabled="true" android:exported="true"/>
  </application>
</manifest>'''

def write(tmp, name, text):
    p = os.path.join(tmp, name)
    with open(p, "w", encoding="utf-8") as f: f.write(text)
    return p

class VerifyManifests(unittest.TestCase):
    def setUp(self):
        self.lite = vm.load(os.path.join(HERE, "fixtures", "manifests", "lite.xml"))   # decoded from the real 0.3.3 Lite APK
        self.full = vm.Manifest(full_xml())
        self.probe = vm.Manifest(full_xml("com.autotuneeq.mvp.probe", listener=False))

    def test_real_lite_manifest_passes(self):
        self.assertEqual(vm.check_lite(self.lite), [])
        self.assertEqual(self.lite.min_sdk, 26); self.assertEqual(self.lite.services(), set())

    def test_expected_full_and_probe_pass(self):
        self.assertEqual(vm.check_full(self.full), [])
        self.assertEqual(vm.check_probe(self.probe), [])
        self.assertEqual(vm.compare_probe_to_full(self.probe, self.full), [])
        self.assertEqual(self.full.services() - self.probe.services(), {vm.LISTENER})

    def test_lite_with_a_sensitive_permission_or_service_fails(self):
        bad = vm.Manifest(full_xml("com.autotuneeq.mvp.lite", listener=False))      # a Full-like manifest under the Lite package
        errs = vm.check_lite(bad)
        self.assertTrue(any("must declare no services" in e for e in errs)); self.assertTrue(any("RECORD_AUDIO" in e for e in errs))

    def test_probe_that_still_has_the_listener_fails(self):
        p = vm.Manifest(full_xml("com.autotuneeq.mvp.probe", listener=True))
        self.assertTrue(vm.check_probe(p)); self.assertTrue(vm.compare_probe_to_full(p, self.full))

    def test_probe_that_also_dropped_another_permission_fails_the_one_variable_rule(self):
        p = vm.Manifest(full_xml("com.autotuneeq.mvp.probe", listener=False, drop_perms=("android.permission.RECORD_AUDIO",)))
        errs = vm.compare_probe_to_full(p, self.full)
        self.assertTrue(any("permission sets differ" in e for e in errs))

    def test_probe_with_extra_component_or_changed_export_fails(self):
        self.assertTrue(vm.compare_probe_to_full(vm.Manifest(full_xml("com.autotuneeq.mvp.probe", listener=False, extra_service=True)), self.full))
        self.assertTrue(vm.compare_probe_to_full(vm.Manifest(full_xml("com.autotuneeq.mvp.probe", listener=False, monitor_exported="true")), self.full))
        self.assertTrue(vm.compare_probe_to_full(vm.Manifest(full_xml("com.autotuneeq.mvp.probe", listener=False, backup="true")), self.full))

    def test_full_without_bind_protection_or_with_exported_monitor_fails(self):
        self.assertTrue(vm.check_full(vm.Manifest(full_xml(listener_perm="android.permission.INTERNET"))))
        self.assertTrue(vm.check_full(vm.Manifest(full_xml(monitor_exported="true"))))
        self.assertTrue(vm.check_full(vm.Manifest(full_xml(drop_perms=("android.permission.POST_NOTIFICATIONS",)))))

    def test_cli_exit_codes_and_summary(self):
        with tempfile.TemporaryDirectory() as d:
            lite = os.path.join(HERE, "fixtures", "manifests", "lite.xml")
            f = write(d, "full.xml", full_xml()); p = write(d, "probe.xml", full_xml("com.autotuneeq.mvp.probe", listener=False))
            md = os.path.join(d, "s.md")
            with contextlib.redirect_stdout(io.StringIO()) as out:
                self.assertEqual(vm.main(["--lite", lite, "--full", f, "--probe", p, "--summary-md", md]), 0)
            self.assertIn("PASS Probe vs Full", out.getvalue()); self.assertIn("Merged-manifest verification", open(md).read())
            bad = write(d, "bad.xml", full_xml("com.autotuneeq.mvp.probe", listener=True))
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(vm.main(["--lite", lite, "--full", f, "--probe", bad]), 1)

if __name__ == "__main__":
    unittest.main()
