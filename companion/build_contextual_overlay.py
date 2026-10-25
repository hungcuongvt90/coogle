"""Build the single-resource native contextual-search configuration overlay."""
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent

def build():
    sdk = Path(os.environ['ANDROID_HOME'])
    tools = sdk / 'build-tools/36.0.0'
    java_name = 'java.exe' if os.name == 'nt' else 'java'
    java = Path(os.environ['JAVA_HOME']) / 'bin' / java_name
    aapt2 = tools / ('aapt2.exe' if os.name == 'nt' else 'aapt2')
    apksigner = tools / 'lib/apksigner.jar'
    if not aapt2.exists():
        aapt2 = Path(shutil.which('aapt2') or aapt2)
    if not java.exists():
        java = Path(shutil.which('java') or java)
    scratch = ROOT.parent / 'build/contextual-overlay'
    scratch.mkdir(parents=True, exist_ok=True)
    source = ROOT / 'contextual-overlay'
    compiled = scratch / 'resources.zip'
    unsigned = scratch / 'unsigned.apk'
    output = ROOT / 'module/system/product/overlay/CoogleContextualSearch/CoogleContextualSearch.apk'
    output.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(aapt2), 'compile', '--dir', str(source/'res'),
                    '-o', str(compiled)], check=True)
    subprocess.run([str(aapt2), 'link', '-o', str(unsigned),
                    '-I', str(sdk/'platforms/android-37.0/android.jar'),
                    '--manifest', str(source/'AndroidManifest.xml'),
                    '--auto-add-overlay', str(compiled)], check=True)
    # Use the same local debug certificate as the app; the static product overlay
    # needs no runtime enable command and does not contain executable code.
    subprocess.run([str(java), '-jar', str(apksigner), 'sign',
                    '--ks', str(Path.home()/'.android/debug.keystore'),
                    '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android',
                    '--key-pass', 'pass:android', '--v4-signing-enabled', 'false',
                    '--out', str(output), str(unsigned)], check=True)
    print(output)

if __name__ == '__main__':
    build()
