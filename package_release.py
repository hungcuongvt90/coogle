"""Package the built APK and its embedded companion as COS16 release artifacts."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import shutil
import zipfile

ROOT = Path(__file__).resolve().parent


def package_release(root=ROOT, output_dir=None):
    root = Path(root)
    apk_dir = root/'app/build/outputs/apk/release'
    metadata = json.loads((apk_dir/'output-metadata.json').read_text(encoding='utf-8'))
    if len(metadata['elements']) != 1:
        raise ValueError('Expected a single release APK')
    element = metadata['elements'][0]
    version = element['versionName']
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._+-]*', version):
        raise ValueError('Invalid release version')
    source = apk_dir/element['outputFile']
    with zipfile.ZipFile(source) as archive:
        companion_bytes = archive.read('assets/coogle-companion.zip')
    with zipfile.ZipFile(io.BytesIO(companion_bytes)) as archive:
        props = dict(line.split('=', 1) for line in archive.read('module.prop').decode().splitlines()
                     if '=' in line)
        if props.get('version') != version:
            raise ValueError('Companion version does not match the APK')

    output = Path(output_dir) if output_dir is not None else root/'build/releases'/version
    output.mkdir(parents=True, exist_ok=True)
    apk = output/f'COS16-Coogle-{version}.apk'
    companion = output/f'COS16-Coogle-Companion-{version}.zip'
    shutil.copyfile(source, apk)
    companion.write_bytes(companion_bytes)
    sums = output/'SHA256SUMS'
    sums.write_text(''.join(
        f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n'
        for path in (apk, companion)), encoding='utf-8')
    return apk, companion, sums


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-dir', type=Path, help='Override build/releases/<version>')
    args = parser.parse_args()
    for artifact in package_release(output_dir=args.output_dir):
        print(artifact)
