import os
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).parent
BASH = Path('C:/Program Files/Git/bin/bash.exe')


def unix(path):
    text = path.as_posix()
    return '/' + text[0].lower() + text[2:]


class StartupTest(unittest.TestCase):
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
                for name in ('setup', 'status'):
                    (work/f'{name}.sh').write_bytes(f'printf "{name}\\n" >> "{unix(work)}/calls"\n'.encode())
                script = (ROOT/'module/boot-completed.sh').read_text(encoding='utf-8').replace(
                    '/system/bin/sh', f'"{unix(BASH)}"').replace('MODDIR=${0%/*}', f'MODDIR="{unix(work)}"')
                mocks = f'sleep() {{ printf "sleep:%s\\n" "$1" >> "{unix(work)}/calls"; }};\n'
                (work/'boot.sh').write_bytes((mocks+script).encode())
                subprocess.run([str(BASH), str(work/'boot.sh')], check=True, capture_output=True)
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
