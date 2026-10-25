from pathlib import Path
import hashlib
import unittest
import zipfile
from build_combined import ROOT, OUT

class CombinedTest(unittest.TestCase):
    def test_supplied_systemizer_used_without_bundled_google_apks(self):
        with zipfile.ZipFile(OUT) as z, zipfile.ZipFile(ROOT/'googlequicksearchbox-systemizer.zip') as source:
            marker='system/priv-app/GoogleQuickSearchBox/.replace'
            self.assertEqual(source.read(marker),z.read(marker))
            self.assertFalse(any(n.startswith('system/priv-app/GoogleQuickSearchBox/') and n.endswith('.apk') for n in z.namelist()))
            self.assertIn(b'. "$MODPATH/copy-google.sh"', z.read('customize.sh'))
            self.assertEqual((ROOT/'module/copy-google.sh').read_bytes().replace(b'\r\n', b'\n'), z.read('copy-google.sh'))

    def test_complete_payload_integrity_and_no_unsafe_overlays(self):
        with zipfile.ZipFile(OUT) as z:
            self.assertIsNone(z.testzip())
            from build_combined import payload
            expected = payload()
            self.assertEqual(set(expected),set(z.namelist()))
            for name,data in expected.items():
                self.assertEqual(hashlib.sha256(data).digest(),hashlib.sha256(z.read(name)).digest())
            listed = {n for n in z.namelist() if n.startswith('system/')}
            self.assertFalse(any(n.startswith('system/product/etc/') or n.startswith('system/vendor/') for n in listed))
            self.assertNotIn('system/odm/etc/resourcemanager.xml',z.namelist())

    def test_no_gemini_systemization(self):
        with zipfile.ZipFile(OUT) as z:
            self.assertNotIn('systemize-gemini.sh', z.namelist())
            for name in z.namelist():
                self.assertNotIn('gemini', name.lower())
                if name.endswith('.sh'):
                    self.assertNotIn(b'com.google.android.apps.bard', z.read(name))
                    self.assertNotIn(b'Gemini', z.read(name))

    def test_component_selection_initialization_cleanup_and_permissions(self):
        with zipfile.ZipFile(OUT) as z:
            self.assertIn(b'author=zopulus\n',z.read('module.prop'))
            self.assertIn(b'id=coogle_companion\n',z.read('module.prop'))
            self.assertIn('name=Coogle Companion Module\n'.encode(),z.read('module.prop'))
            self.assertNotIn('Google系统应用 + Hotword + DSP'.encode(), z.read('module.prop'))
            self.assertNotIn('Google系统应用 + Hotword + DSP'.encode(), z.read('status.sh'))
            installer = z.read('customize.sh')
            self.assertIn(b'apply_features || abort',installer)
            self.assertIn(b'load_features /data/adb/modules/coogle_companion/features.conf',installer)
            self.assertNotIn(b'getprop',installer)
            self.assertNotIn(b'dumpsys',installer)
            self.assertNotIn(b'prepare-audio.sh', installer)
            self.assertNotIn('prepare-audio.sh', z.namelist())
            self.assertNotIn('patch-audio.awk', z.namelist())
            for old in (b'coogle_hotword_enrollment', b'coogle_hotword_hal_probe', b'google_system_app'):
                self.assertNotIn(old,installer)
            self.assertNotIn(b'rm -rf',installer)
            for removed in ('rescue.sh','diagnose.sh','payload.sha256','action.sh'):
                self.assertNotIn(removed,z.namelist())
            self.assertIn(b'cat > /dev/null',z.read('boot-completed.sh'))
            self.assertIn(b'Existing model 101 retained',z.read('setup.sh'))
            self.assertIn(b'am compat reset',z.read('uninstall.sh'))
            self.assertNotIn(b'getevent', installer)
            self.assertIn(b'controlVersion=1', z.read('module.prop'))
            from build_combined import PAYLOAD_REVISION
            self.assertIn(f'payloadRevision={PAYLOAD_REVISION}\n'.encode(),z.read('module.prop'))
            source=(ROOT.parent/'app/src/main/java/io/github/zopulus/coogle/CompanionInstaller.java').read_text(encoding='utf-8')
            self.assertIn(f'REQUIRED_PAYLOAD_REVISION = {PAYLOAD_REVISION};',source)
            self.assertIn('mount-google.sh',z.namelist())
            self.assertIn(b'apply_features', z.read('post-fs-data.sh'))
            self.assertIn(b'[ "$hey" = 1 ] || exit 0', z.read('setup.sh'))

if __name__ == '__main__': unittest.main()
