import unittest
from collect_install_diagnostics import classify_install_output

class ClassificationTests(unittest.TestCase):
    def test_primary_failure_not_nested_cause(self):
        text='adb: failed to install app.apk: Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: details INSTALL_FAILED_INTERNAL_ERROR]'
        self.assertEqual(classify_install_output(text),'INSTALL_FAILED_UPDATE_INCOMPATIBLE')
    def test_real_adb_failure_classes(self):
        for reason in ['INSTALL_FAILED_VERIFICATION_FAILURE','INSTALL_FAILED_USER_RESTRICTED','INSTALL_PARSE_FAILED_NO_CERTIFICATES','INSTALL_FAILED_OLDER_SDK']:
            self.assertEqual(classify_install_output('Failure ['+reason+': detail]'),reason)
    def test_generic_screenshot_is_not_a_diagnosis(self):
        self.assertTrue(classify_install_output('App not installed.').startswith('UNKNOWN:'))
    def test_success_is_scoped_to_adb_not_play_protect(self):
        self.assertTrue(classify_install_output('Performing Streamed Install\nSuccess\n').startswith('ADB_INSTALL_SUCCESS'))
        self.assertIn('Vivo',classify_install_output('Success\n'))

if __name__=='__main__':unittest.main()
