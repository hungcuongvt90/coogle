import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from package_release import package_release


class PackageReleaseTest(unittest.TestCase):
    def fixture(self, root, version='1.3.10', companion_version=None):
        apk_dir = root/'app/build/outputs/apk/release'
        apk_dir.mkdir(parents=True)
        companion = io.BytesIO()
        with zipfile.ZipFile(companion, 'w') as archive:
            archive.writestr('module.prop',
                             f'id=coogle_companion\nversion={companion_version or version}\n')
            archive.writestr('boot-completed.sh', '#!/system/bin/sh\n')
        with zipfile.ZipFile(apk_dir/'app-release.apk', 'w') as archive:
            archive.writestr('classes.dex', b'apk fixture')
            archive.writestr('assets/coogle-companion.zip', companion.getvalue())
        (apk_dir/'output-metadata.json').write_text(json.dumps({
            'elements': [{'versionName': version, 'outputFile': 'app-release.apk'}]
        }))
        return apk_dir/'app-release.apk', companion.getvalue()

    def test_versioned_cos16_names_and_exact_embedded_companion(self):
        for version in ('1.3.10', '2.4.0'):
            with self.subTest(version=version), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                source, embedded = self.fixture(root, version)
                apk, companion, sums = package_release(root)
                self.assertEqual(apk.parent, root/'build/releases'/version)
                self.assertEqual(apk.name, f'COS16-Coogle-{version}.apk')
                self.assertEqual(companion.name, f'COS16-Coogle-Companion-{version}.zip')
                self.assertEqual(apk.read_bytes(), source.read_bytes())
                self.assertEqual(companion.read_bytes(), embedded)
                self.assertEqual(set(apk.parent.iterdir()), {apk, companion, sums})
                records = {name: digest for digest, name in
                           (line.split('  ') for line in sums.read_text().splitlines())}
                self.assertEqual(set(records), {apk.name, companion.name})
                for artifact in (apk, companion):
                    self.assertEqual(records[artifact.name], hashlib.sha256(artifact.read_bytes()).hexdigest())

    def test_ci_output_directory_uses_the_same_names(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root)
            output = root/'dist'
            artifacts = package_release(root, output)
            self.assertEqual({path.name for path in artifacts}, {
                'COS16-Coogle-1.3.10.apk', 'COS16-Coogle-Companion-1.3.10.zip', 'SHA256SUMS'})
            self.assertTrue(all(path.parent == output for path in artifacts))

    def test_mismatched_companion_is_rejected_before_publishing_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root, companion_version='1.3.9')
            with self.assertRaisesRegex(ValueError, 'Companion version'):
                package_release(root)
            self.assertFalse((root/'build/releases').exists())


if __name__ == '__main__':
    unittest.main()
