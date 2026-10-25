import subprocess
import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).parent
BASH = 'C:/Program Files/Git/bin/bash.exe'
FEATURE = 'system/etc/permissions/coogle-live-translate.xml'
OVERLAY = 'system/product/overlay/CoogleContextualSearch/CoogleContextualSearch.apk'


def unix(path):
    value = path.as_posix()
    return '/' + value[0].lower() + value[2:]


class NativeTranslationTest(unittest.TestCase):
    def test_declaration_has_only_native_version_zero_capability(self):
        root = ET.parse(ROOT/'module'/FEATURE).getroot()
        self.assertEqual(root.tag, 'permissions')
        self.assertEqual([(node.tag, node.attrib) for node in root], [
            ('feature', {'name': 'com.google.android.feature.CONTEXTUAL_SEARCH_LIVE_TRANSLATE', 'version': '0'})])

    def test_preflight_parks_only_feature_and_recovers(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            for name in (FEATURE, OVERLAY, 'system/priv-app/GoogleQuickSearchBox/base.apk'):
                path = work/name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b'preserved')
            script = (ROOT/'module/native-translate.sh').read_text(encoding='utf-8').replace('/system/bin/idmap2', 'idmap2')
            (work/'native-translate.sh').write_bytes(script.encode())
            def run(mode):
                mocks = ('idmap2() { '
                         'case "$1" in create) [ "$mode" != failure ];; '
                         'lookup) [ "$mode" != lookup-failure ] || return 1; if [ "$mode" = wrong ]; then echo \'cookie=1 config= value="other.provider\'; '
                         'else echo \'cookie=1 config= value="com.google.android.googlequicksearchbox\'; fi;; esac; }; ')
                command = (f'MODDIR="{unix(work)}"; mode={mode}; '
                           f'. "{unix(ROOT / "module/features.sh")}"; '
                           f'. "{unix(work / "native-translate.sh")}"; {mocks}prepare_native_translation')
                return subprocess.run([BASH, '-c', command], check=True, capture_output=True)
            run('valid')
            self.assertTrue((work/FEATURE).exists())
            for mode in ('failure', 'lookup-failure', 'wrong'):
                run(mode)
                self.assertFalse((work/FEATURE).exists())
                self.assertEqual((work/'optional'/FEATURE).read_bytes(), b'preserved')
                self.assertTrue((work/'translation-error').exists())
                self.assertEqual((work/OVERLAY).read_bytes(), b'preserved')
                self.assertEqual((work/'system/priv-app/GoogleQuickSearchBox/base.apk').read_bytes(), b'preserved')
            run('valid')
            self.assertEqual((work/FEATURE).read_bytes(), b'preserved')
            self.assertFalse((work/'translation-error').exists())
            self.assertFalse((work/'native-translate.idmap.tmp').exists())

    def test_status_requires_both_native_capability_and_provider(self):
        for capability, provider, ready in ((True, 'com.google.android.googlequicksearchbox', True),
                                            (False, 'com.google.android.googlequicksearchbox', False),
                                            (True, 'other.provider', False)):
            with self.subTest(capability=capability, provider=provider), tempfile.TemporaryDirectory() as directory:
                work = Path(directory)
                (work/'features.sh').write_bytes((ROOT/'module/features.sh').read_bytes().replace(b'\r\n', b'\n'))
                (work/'applied-features.conf').write_bytes(b'hey=0\n')
                (work/'module.prop').write_bytes(b'id=coogle_companion\n')
                script = (ROOT/'integration/status.sh').read_text(encoding='utf-8').replace(
                    'MODDIR=${0%/*}', f'MODDIR="{unix(work)}"')
                mocks = (f'dumpsys() {{ return 0; }}; pm() {{ echo {str(capability).lower()}; }}; '
                         f'cmd() {{ echo {provider}; }};\n')
                (work/'status.sh').write_bytes((mocks+script).encode())
                result = subprocess.run([BASH, str(work/'status.sh')], check=True,
                                        capture_output=True, text=True, encoding='utf-8')
                self.assertEqual('✅Live translation configuration active' in result.stdout, ready)


if __name__ == '__main__':
    unittest.main()
