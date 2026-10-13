import subprocess
import tempfile
import unittest
from pathlib import Path

from build_combined import payload

BASH = 'C:/Program Files/Git/bin/bash.exe'


def unix(path):
    value = path.as_posix()
    return '/' + value[0].lower() + value[2:]


class GoogleInstallTest(unittest.TestCase):
    def test_complete_base_and_splits_or_abort_install(self):
        for mode in ('valid', 'query-failure', 'empty', 'missing-split', 'empty-apk', 'malformed', 'copy-failure'):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as directory:
                work = Path(directory)
                for name in ('base.apk', 'split_config.arm64_v8a.apk'):
                    (work/name).write_bytes(name.encode())
                if mode == 'empty-apk':
                    (work/'base.apk').write_bytes(b'')
                files = payload()
                if 'copy-google.sh' in files:
                    (work/'copy-google.sh').write_bytes(files['copy-google.sh'])
                    (work/'mount-google.sh').write_bytes(files['mount-google.sh'])
                # Execute the generated installation path, ending before Android-specific setup.
                script = files['customize.sh'].decode().split('set_perm_recursive "$MODPATH/system"')[0]
                if mode == 'empty':
                    packages = ''
                else:
                    split = work/('missing.apk' if mode == 'missing-split' else 'split_config.arm64_v8a.apk')
                    packages = f'package:{unix(work / "base.apk")}\npackage:{unix(split)}\n'
                if mode == 'malformed':
                    packages = 'unexpected response\n'
                mocks = (f'MODPATH="{unix(work)}"; '
                         'abort() { echo "$*" >&2; exit 42; }; set_perm_recursive() { :; }; '
                         f'pm() {{ printf \'%s\' \'{packages}\'; return {int(mode == "query-failure")}; }};\n')
                if mode == 'copy-failure':
                    mocks += 'cp() { return 1; };\n'
                (work/'run.sh').write_bytes((mocks + script + '\necho continued\n').encode())
                result = subprocess.run([BASH, str(work/'run.sh')], capture_output=True, text=True)
                if mode == 'valid':
                    self.assertEqual(result.returncode, 0, result.stderr)
                    for name in ('base.apk', 'split_config.arm64_v8a.apk'):
                        self.assertEqual((work/'system/priv-app/GoogleQuickSearchBox'/name).read_bytes(), name.encode())
                else:
                    self.assertNotEqual(result.returncode, 0, result.stdout)
                    self.assertNotIn('continued', result.stdout)


if __name__ == '__main__':
    unittest.main()
