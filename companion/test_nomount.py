"""Exercise Google mount selection, backend transitions, upgrade settings and package flags."""
import subprocess
import tempfile
import unittest
from pathlib import Path
from build_combined import payload
from test_google_install import BASH, unix


class NoMountRegressionTest(unittest.TestCase):
    def install(self, root, selected, driver, staged=False):
        module = root/'coogle'; module.mkdir(exist_ok=True)
        backend = root/('modules_update' if staged else 'modules')/'nomount'
        (backend/'bin').mkdir(parents=True, exist_ok=True)
        (backend/'module.prop').write_bytes(b'id=nomount\nmetamodule=true\n')
        if not selected:
            (backend/'disable').touch()
        nm = backend/'bin/nm'
        nm.write_bytes(('#!/bin/sh\nexit '+str(0 if driver else 1)+'\n').encode())
        nm.chmod(0o755)
        base = root/'base.apk'; base.write_bytes(b'base')
        split = root/'split.apk'; split.write_bytes(b'split')
        app = module/'system/priv-app/GoogleQuickSearchBox'
        app.mkdir(parents=True, exist_ok=True); (app/'.replace').touch()
        script = payload()['copy-google.sh'].decode().replace('/data/adb', unix(root))
        (module/'mount-google.sh').write_bytes(payload()['mount-google.sh'].replace(b'/data/adb',unix(root).encode()))
        mocks = (f'MODPATH="{unix(module)}"; '
                 'abort() { echo "$*" >&2; exit 42; }; '
                 f'readlink() {{ printf "%s\\n" "{unix(backend) if selected else unix(root / "modules/ordinary")}"; }}; '
                 f'pm() {{ printf "package:{unix(base)}\\npackage:{unix(split)}\\n"; }};\n')
        run = root/'install.sh'; run.write_bytes((mocks+script).encode())
        result = subprocess.run([BASH, str(run)], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((app/'base.apk').read_bytes(), b'base')
        self.assertEqual((app/'split.apk').read_bytes(), b'split')
        return module, app

    def before_mount(self, root, module, backend, expected=0):
        (module/'mount-google.sh').write_bytes(payload()['mount-google.sh'].replace(b'/data/adb',unix(root).encode()))
        (module/'features.sh').write_bytes(payload()['features.sh'])
        (module/'native-translate.sh').write_bytes(b'prepare_native_translation() { return 0; }\n')
        (module/'features.conf').write_bytes(b'hey=0\n')
        boot=payload()['post-fs-data.sh'].decode().replace('MODDIR=${0%/*}',f'MODDIR="{unix(module)}"')
        boot=boot.replace('/data/adb',unix(root))
        mocks=f'readlink() {{ printf "%s\\n" "{unix(backend)}"; }};\n'
        run=root/'boot.sh'; run.write_bytes((mocks+boot).encode())
        result=subprocess.run([BASH,str(run)],capture_output=True,text=True)
        self.assertEqual(result.returncode,expected,result.stderr)

    def test_active_nomount_removes_old_replace_and_preserves_apks(self):
        with tempfile.TemporaryDirectory() as d:
            _, app = self.install(Path(d), selected=True, driver=True)
            self.assertFalse((app/'.replace').exists())

    def test_normal_backend_keeps_replace(self):
        with tempfile.TemporaryDirectory() as d:
            _, app = self.install(Path(d), selected=False, driver=False)
            self.assertTrue((app/'.replace').exists())

    def test_absent_nomount_binary_keeps_replace(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); _,app=self.install(root,selected=False,driver=False)
            (root/'modules/nomount/bin/nm').unlink()
            subprocess.run([BASH,str(root/'install.sh')],check=True,capture_output=True)
            self.assertTrue((app/'.replace').exists())

    def test_selected_nomount_before_driver_load_uses_merge(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); module,app=self.install(root,selected=True,driver=False)
            self.before_mount(root,module,root/'modules/nomount')
            self.assertFalse((app/'.replace').exists(), 'selected NoMount will load its driver in metamount.sh')

    def test_staged_first_install_nomount_uses_merge(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); module,app=self.install(root,selected=True,driver=True,staged=True)
            (root/'modules').mkdir(); (root/'modules_update/nomount').rename(root/'modules/nomount')
            self.before_mount(root,module,root/'modules/nomount')
            self.assertFalse((app/'.replace').exists(), 'staged NoMount becomes active before first mount')

    def test_disabled_nomount_with_responsive_kernel_keeps_replace(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); module,app=self.install(root,selected=False,driver=True)
            self.before_mount(root,module,root/'modules/ordinary')
            self.assertTrue((app/'.replace').exists(), 'nm version is driver availability, not backend selection')

    def test_boot_after_switch_to_nomount_reconciles_replace(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); module, app = self.install(root, selected=False, driver=False)
            backend=root/'modules/nomount'; (backend/'disable').unlink()
            self.before_mount(root,module,backend)
            self.assertFalse((app/'.replace').exists(),'existing install retains marker after mount backend change')

    def test_removed_or_safe_mode_nomount_restores_replace(self):
        for marker in ('remove','safe-mode'):
            with self.subTest(marker=marker),tempfile.TemporaryDirectory() as d:
                root=Path(d); module,app=self.install(root,selected=True,driver=True)
                backend=root/'modules/nomount'
                if marker=='remove': (backend/'remove').touch()
                else:
                    (root/'nomount').mkdir(); (root/'nomount/disable').touch()
                self.before_mount(root,module,backend)
                self.assertTrue((app/'.replace').exists())

    def test_marker_failure_pauses_mount_without_losing_google_apks(self):
        for selected in (False,True):
            with self.subTest(selected=selected),tempfile.TemporaryDirectory() as d:
                root=Path(d); module,app=self.install(root,selected=selected,driver=True)
                marker=app/'.replace'
                if marker.exists(): marker.unlink()
                marker.mkdir()
                backend=root/('modules/nomount' if selected else 'modules/ordinary')
                self.before_mount(root,module,backend,expected=1)
                self.assertTrue((module/'skip_mount').exists())
                self.assertTrue((module/'component-error').exists())
                self.assertEqual((app/'base.apk').read_bytes(),b'base')
                self.assertEqual((module/'applied-features.conf').read_text(),'hey=0\n')

    def test_upgrade_preserves_legacy_hey_choice_with_unchanged_control_protocol(self):
        for hey in (0,1):
            with self.subTest(hey=hey), tempfile.TemporaryDirectory() as d:
                root=Path(d); old=root/'old'; new=root/'new'
                old.mkdir(); new.mkdir()
                (old/'module.prop').write_bytes(b'controlVersion=1\nversion=1.3.2\n')
                (old/'features.conf').write_bytes(f'hey={hey}\n'.encode())
                (new/'features.sh').write_bytes(payload()['features.sh'])
                script=payload()['customize.sh'].decode().split('. "$MODPATH/features.sh"',1)[1].split('MODDIR=$MODPATH',1)[0]
                script=script.replace('/data/adb/modules/coogle_companion',unix(old))
                run=root/'upgrade.sh'
                run.write_bytes((f'MODPATH="{unix(new)}"; abort() {{ exit 42; }}; . "$MODPATH/features.sh";\n'+script).encode())
                subprocess.run([BASH,str(run)],check=True,capture_output=True)
                self.assertEqual((new/'features.conf').read_text(),f'hey={hey}\n')


class GoogleSystemStatusTest(unittest.TestCase):
    def test_only_actual_system_flag_counts(self):
        fixtures = [
            ('    flags=[ SYSTEM HAS_CODE UPDATED_SYSTEM_APP ]\n', True),
            ('    flags=[ HAS_CODE ]\n android.permission.FOO: flags=[ RESTRICTION_SYSTEM_EXEMPT ]\n', False),
            ('    flags=[ HAS_CODE UPDATED_SYSTEM_APP ]\n', False),
            ('', False),
            ('    flags=[ HAS_CODE ]\nHidden system packages:\n    flags=[ SYSTEM HAS_CODE ]\n', False),
        ]
        for text, expected in fixtures:
            with self.subTest(text=text), tempfile.TemporaryDirectory() as d:
                root=Path(d)
                (root/'features.sh').write_bytes(payload()['features.sh'])
                (root/'applied-features.conf').write_bytes(b'hey=0\n')
                (root/'module.prop').write_bytes(b'id=coogle_companion\n')
                script=payload()['status.sh'].decode().replace('MODDIR=${0%/*}',f'MODDIR="{unix(root)}"')
                mocks="dumpsys() { printf '%s' '"+text+"'; }; pm() { echo false; }; cmd() { echo other; };\n"
                run=root/'status.sh'; run.write_bytes((mocks+script).encode())
                result=subprocess.run([BASH,str(run)],check=True,capture_output=True,text=True,encoding='utf-8')
                self.assertEqual('✅Google系统应用' in result.stdout,expected)


if __name__ == '__main__': unittest.main()
