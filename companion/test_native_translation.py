import subprocess
import tempfile
import unittest
from pathlib import Path
import os
import xml.etree.ElementTree as ET

ROOT = Path(__file__).parent
BASH = 'C:/Program Files/Git/bin/bash.exe' if os.name == 'nt' else '/bin/bash'
FEATURE = 'system/etc/permissions/coogle-live-translate.xml'
OVERLAY = 'system/product/overlay/CoogleContextualSearch/CoogleContextualSearch.apk'


def unix(path):
    value = path.as_posix()
    if os.name != 'nt':
        return value
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

    def test_metamodule_uses_runtime_overlay_and_keeps_capability_for_boot_scan(self):
        for marker in (b'metamodule=true\n', b'metamodule=1\n'):
            with self.subTest(marker=marker), tempfile.TemporaryDirectory() as directory:
                work = Path(directory)
                backend = work/'metamodule'
                backend.mkdir()
                (backend/'module.prop').write_bytes(b'id=nomount\n' + marker)
                for name in (FEATURE, OVERLAY, 'system/priv-app/GoogleQuickSearchBox/base.apk'):
                    path = work/name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(b'preserved')
                script = (ROOT/'module/native-translate.sh').read_text(encoding='utf-8').replace('/system/bin/idmap2', 'idmap2')
                (work/'native-translate.sh').write_bytes(script.encode())
                command = (f'MODDIR="{unix(work)}"; COOGLE_METAMODULE="{unix(backend)}"; '
                           f'. "{unix(ROOT / "module/features.sh")}"; '
                           f'. "{unix(work / "native-translate.sh")}"; '
                           'idmap2() { [ "$1" != lookup ] || echo \'value="com.google.android.googlequicksearchbox\'; }; '
                           'prepare_native_translation; mkdir -p "$MODDIR/system/product/overlay/CoogleContextualSearch"; '
                           'load_features /dev/null; apply_features; prepare_native_translation')
                subprocess.run([BASH, '-c', command], check=True, capture_output=True)
                self.assertFalse((work/OVERLAY).exists())
                self.assertFalse((work/'system/product/overlay/CoogleContextualSearch').exists())
                self.assertEqual((work/FEATURE).read_bytes(), b'preserved')
                self.assertEqual((work/'optional'/OVERLAY).read_bytes(), b'preserved')
                self.assertEqual((work/'translation-mode').read_text(), 'runtime\n')
                self.assertEqual((work/'translation-error').read_text(), 'runtime-pending\n')
                self.assertTrue((work/'system/priv-app/GoogleQuickSearchBox/base.apk').exists())
                # Switching away from the backend restores the static path.
                (backend/'disable').touch()
                subprocess.run([BASH, '-c', command], check=True, capture_output=True)
                self.assertEqual((work/OVERLAY).read_bytes(), b'preserved')
                self.assertTrue((work/FEATURE).exists())
                self.assertEqual((work/'translation-mode').read_text(), 'static\n')
                self.assertFalse((work/'translation-error').exists())

    def test_metamodule_preflight_failure_keeps_both_translation_payloads_parked(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            backend = work/'metamodule'
            backend.mkdir()
            (backend/'module.prop').write_text('metamodule=true\n')
            for name in (FEATURE, OVERLAY):
                path = work/name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b'preserved')
            script = (ROOT/'module/native-translate.sh').read_text().replace('/system/bin/idmap2', 'idmap2')
            (work/'native-translate.sh').write_text(script)
            command = (f'MODDIR="{unix(work)}"; COOGLE_METAMODULE="{unix(backend)}"; '
                       f'. "{unix(ROOT / "module/features.sh")}"; '
                       f'. "{unix(work / "native-translate.sh")}"; '
                       'idmap2() { return 1; }; prepare_native_translation')
            subprocess.run([BASH, '-c', command], check=True, capture_output=True)
            for name in (FEATURE, OVERLAY):
                self.assertFalse((work/name).exists())
                self.assertEqual((work/'optional'/name).read_bytes(), b'preserved')
            self.assertEqual((work/'translation-error').read_text(), 'overlay-incompatible\n')

    def run_runtime(self, work, capability=True, failure='', resource_ready=False):
        script = (ROOT/'module/runtime-translate.sh').read_text().replace(
            'MODDIR=${0%/*}', f'MODDIR="{unix(work)}"')
        mocks = f'''work="{unix(work)}"
cmd() {{
    printf '%s\\n' "$*" >> "$work/calls"
    case "$1 $2" in
        'overlay lookup')
            if [ -f "$work/enabled" ] || [ "{resource_ready}" = True ]; then
                echo com.google.android.googlequicksearchbox
            else echo other.provider; fi ;;
        'overlay fabricate') [ "{failure}" != fabricate ] ;;
        'overlay enable')
            [ "{failure}" != enable ] || return 1
            [ "{failure}" = lookup ] || touch "$work/enabled" ;;
        'overlay disable') rm -f "$work/enabled" ;;
        *) return 9 ;;
    esac
}}
pm() {{
    printf 'pm:%s\\n' "$*" >> "$work/calls"
    echo {str(capability).lower()}
}}
'''
        run = work/'run.sh'
        run.write_text(mocks+script)
        return subprocess.run([BASH, str(run)], capture_output=True, text=True)

    def test_runtime_overlay_verifies_provider_and_capability_and_is_idempotent(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            (work/'translation-mode').write_text('runtime\n')
            (work/'translation-error').write_text('runtime-pending\n')
            result = self.run_runtime(work, capability=False)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual((work/'translation-error').read_text(), 'capability-pending\n')
            calls = (work/'calls').read_text()
            self.assertIn('overlay fabricate --target android --name CoogleContextualSearch '
                          'android:string/config_defaultContextualSearchPackageName 0x03 '
                          'com.google.android.googlequicksearchbox\n', calls)
            self.assertIn('overlay enable --user 0 com.android.shell:CoogleContextualSearch\n', calls)
            result = self.run_runtime(work, capability=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertFalse((work/'translation-error').exists())
            self.assertEqual((work/'calls').read_text().count('overlay fabricate '), 1)

    def test_runtime_keeps_an_existing_native_provider_without_overlay_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            (work/'translation-mode').write_text('runtime\n')
            result = self.run_runtime(work, resource_ready=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            calls = (work/'calls').read_text()
            self.assertNotIn('overlay fabricate', calls)
            self.assertNotIn('overlay enable', calls)
            self.assertNotIn('overlay disable', calls)
            self.assertFalse((work/'translation-error').exists())

    def test_runtime_failures_disable_only_own_overlay_and_report_failure(self):
        for failure in ('fabricate', 'enable', 'lookup'):
            with self.subTest(failure=failure), tempfile.TemporaryDirectory() as directory:
                work = Path(directory)
                (work/'translation-mode').write_text('runtime\n')
                result = self.run_runtime(work, failure=failure)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual((work/'translation-error').read_text(), 'runtime-overlay-failed\n')
                self.assertFalse((work/'enabled').exists())
                self.assertIn('overlay disable --user 0 com.android.shell:CoogleContextualSearch\n',
                              (work/'calls').read_text())
                self.assertNotIn('pm:has-feature', (work/'calls').read_text())

    def test_runtime_respects_disabled_failed_missing_and_static_modes(self):
        for marker in ('disable', 'remove', 'component-error', 'skip_mount', 'overlay-incompatible', 'static', 'missing'):
            with self.subTest(marker=marker), tempfile.TemporaryDirectory() as directory:
                work = Path(directory)
                if marker != 'missing':
                    (work/'translation-mode').write_text('static\n' if marker == 'static' else 'runtime\n')
                if marker == 'overlay-incompatible':
                    (work/'translation-error').write_text(marker+'\n')
                elif marker not in ('static', 'missing'):
                    (work/marker).touch()
                result = self.run_runtime(work)
                self.assertEqual(result.returncode, 0, result.stderr)
                calls = (work/'calls').read_text() if (work/'calls').exists() else ''
                self.assertNotIn('overlay fabricate', calls)
                if marker != 'missing':
                    self.assertEqual(calls, 'overlay disable --user 0 com.android.shell:CoogleContextualSearch\n')

    def test_runtime_status_reports_pending_and_failed_without_claiming_success(self):
        for marker, description in (
                ('capability-pending', 'Live translation capability pending module activation'),
                ('runtime-pending', 'Live translation runtime overlay pending'),
                ('runtime-overlay-failed', 'Live translation runtime overlay failed')):
            with self.subTest(marker=marker), tempfile.TemporaryDirectory() as directory:
                work = Path(directory)
                (work/'features.sh').write_bytes((ROOT/'module/features.sh').read_bytes())
                (work/'applied-features.conf').write_text('hey=0\n')
                (work/'module.prop').write_text('id=coogle_companion\n')
                (work/'translation-error').write_text(marker+'\n')
                script = (ROOT/'integration/status.sh').read_text().replace(
                    'MODDIR=${0%/*}', f'MODDIR="{unix(work)}"')
                mocks = 'dumpsys() { :; }; pm() { echo true; }; cmd() { echo com.google.android.googlequicksearchbox; };\n'
                (work/'run.sh').write_text(mocks+script)
                result = subprocess.run([BASH, str(work/'run.sh')], check=True, capture_output=True, text=True)
                self.assertIn(description, result.stdout)
                self.assertNotIn('✅Live translation configuration active', result.stdout)

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
