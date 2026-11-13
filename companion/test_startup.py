import os
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).parent
BASH = Path(os.environ.get('COOGLE_TEST_BASH', 'bash'))


def unix(path):
    text = path.as_posix()
    if text.startswith('/'):
        return text
    if ':' not in text:
        return text
    return '/' + text[0].lower() + text[2:]


class StartupTest(unittest.TestCase):
    def test_play_store_repair_only_reinstalls_unregistered_user_update(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            apk = work/'data/app/~~token==/com.android.vending-update/base.apk'
            apk.parent.mkdir(parents=True)
            apk.write_bytes(b'play-store')
            calls = work/'calls'
            bin_dir = work/'bin'
            bin_dir.mkdir()
            (bin_dir/'pm').write_text(
                '#!/bin/sh\n'
                'printf "pm:%s\\n" "$*" >> "' + unix(calls) + '"\n'
                'if [ "$1" = path ]; then printf "package:/product/priv-app/GooglePlayServicesUpdater/GooglePlayServicesUpdater.apk\\n"; fi\n'
                'exit 0\n',
                encoding='utf-8')
            (bin_dir/'cmd').write_text(
                '#!/bin/sh\nprintf "cmd:%s\\n" "$*" >> "' + unix(calls) + '"\n', encoding='utf-8')
            for command in ('pm', 'cmd'):
                (bin_dir/command).chmod(0o755)
            script = (ROOT/'module/repair-play-store.sh').read_text(encoding='utf-8')
            script = script.replace('/data/app', unix(work/'data/app')).replace(
                'MODDIR=${0%/*}', f'MODDIR="{unix(work)}"')
            run = work/'repair.sh'; run.write_text(script, encoding='utf-8')
            env = dict(os.environ, PATH=f'{unix(bin_dir)}:/usr/bin:/bin')
            subprocess.run([str(BASH), str(run)], check=True, env=env, capture_output=True)
            self.assertEqual(calls.read_text(),
                             'pm:path com.android.vending\n'
                             'cmd:package install -r -d ' + unix(apk) + '\n'
                             'pm:enable --user 0 com.android.vending\n')

    def test_failed_preparation_preserves_requested_hey_switch(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            (work/'features.sh').write_text(
                'load_features() { hey=0; while IFS= read -r line; do [ "$line" = hey=1 ] && hey=1; done < "$1"; }\n'
                'save_features() { printf "hey=%s\\n" "$hey" > "$1"; }\n'
                'apply_features() { return 0; }\n', encoding='utf-8')
            (work/'native-translate.sh').write_text('prepare_native_translation() { return 1; }\n', encoding='utf-8')
            (work/'mount-google.sh').write_text('prepare_google_mount() { return 0; }\n', encoding='utf-8')
            (work/'features.conf').write_text('hey=1\n', encoding='utf-8')
            script = (ROOT/'module/post-fs-data.sh').read_text(encoding='utf-8').replace(
                'MODDIR=${0%/*}', f'MODDIR="{unix(work)}"')
            run = work/'post-fs-data.sh'; run.write_text(script, encoding='utf-8')
            result = subprocess.run([str(BASH), str(run)], capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual((work/'applied-features.conf').read_text(), 'hey=1\n')
            self.assertTrue((work/'component-error').exists())
            self.assertTrue((work/'skip_mount').exists())

    def test_boot_wait_and_setup_only_for_enabled_healthy_support(self):
        for enabled, failed, expected in ((False, False, 'status\n'),
                                          (True, False, 'sleep:30\nsetup\nstatus\n'),
                                          (True, True, 'status\n')):
            with self.subTest(enabled=enabled, failed=failed), tempfile.TemporaryDirectory() as directory:
                work = Path(directory)
                (work/'features.sh').write_bytes((ROOT/'module/features.sh').read_bytes().replace(b'\r\n', b'\n'))
                (work/'applied-features.conf').write_bytes(f'hey={int(enabled)}\n'.encode())
                if failed:
                    (work/'component-error').touch()
                for name in ('repair-play-store', 'setup', 'status'):
                    (work/f'{name}.sh').write_bytes(f'printf "{name}\\n" >> "{unix(work)}/calls"\n'.encode())
                script = (ROOT/'module/boot-completed.sh').read_text(encoding='utf-8').replace(
                    '/system/bin/sh', f'"{unix(BASH)}"').replace('MODDIR=${0%/*}', f'MODDIR="{unix(work)}"')
                mocks = f'sleep() {{ printf "sleep:%s\\n" "$1" >> "{unix(work)}/calls"; }};\n'
                (work/'boot.sh').write_bytes((mocks+script).encode())
                subprocess.run([str(BASH), str(work/'boot.sh')], check=True, capture_output=True)
                expected = 'repair-play-store\n' + expected
                self.assertEqual((work/'calls').read_text(), expected)

    def test_failed_component_status_skips_android_queries_and_unchanged_writes(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            (work/'features.sh').write_bytes((ROOT/'module/features.sh').read_bytes().replace(b'\r\n', b'\n'))
            (work/'applied-features.conf').write_bytes(b'hey=1\n')
            (work/'component-error').touch()
            prop = work/'module.prop'
            prop.write_bytes(b'id=coogle_companion\ndescription=old\n')
            mocks = f'query() {{ touch "{unix(work)}/queried"; exit 9; }}; dumpsys() {{ query; }}; pm() {{ query; }}; cmd() {{ query; }};\n'
            script = (ROOT/'integration/status.sh').read_text(encoding='utf-8').replace('MODDIR=${0%/*}',
                                                                        f'MODDIR="{unix(work)}"')
            (work/'status.sh').write_bytes((mocks+script).encode('utf-8'))
            run = lambda: subprocess.run([str(BASH), str(work/'status.sh')], check=True,
                                         capture_output=True, text=True, encoding='utf-8')
            self.assertIn('Component preparation failed; mounting paused', run().stdout)
            os.utime(prop, ns=(1_000_000_000, 1_000_000_000))
            timestamp = prop.stat().st_mtime_ns
            run()
            self.assertEqual(prop.stat().st_mtime_ns, timestamp)
            self.assertFalse((work/'module.prop.tmp').exists())
            self.assertFalse((work/'queried').exists())


if __name__ == '__main__':
    unittest.main()
