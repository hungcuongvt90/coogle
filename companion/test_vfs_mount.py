import os
import subprocess
import tempfile
import unittest
from pathlib import Path

from build_combined import payload


ROOT = Path(__file__).parent
BASH = os.environ.get(
    "COOGLE_TEST_BASH",
    "C:/Program Files/Git/bin/bash.exe" if os.name == "nt" else "/bin/bash",
)


def unix(path):
    value = path.as_posix()
    if value.startswith("/"):
        return value
    return "/" + value[0].lower() + value[2:]


class VfsMountTest(unittest.TestCase):
    def test_metamodule_backend_without_nomount_id_removes_replace_marker(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            module = root / "coogle"
            app = module / "system/priv-app/GoogleQuickSearchBox"
            app.mkdir(parents=True)
            (app / ".replace").touch()
            backend = root / "modules/vfs"
            backend.mkdir(parents=True)
            (backend / "module.prop").write_text("id=vfs_mount\nmetamodule=true\n", encoding="utf-8")
            script = payload()["mount-google.sh"].decode().replace("/data/adb", unix(root))
            run = root / "mount.sh"
            run.write_text(
                f'readlink() {{ printf "%s\\n" "{unix(backend)}"; }};\n'
                f'MODDIR="{unix(module)}"; {script}\nprepare_google_mount "$MODDIR"\n',
                encoding="utf-8",
            )
            result = subprocess.run([BASH, str(run)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertFalse((app / ".replace").exists())


if __name__ == "__main__":
    unittest.main()
