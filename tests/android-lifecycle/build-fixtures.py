#!/usr/bin/env python3
"""Build test-only APKs with SDK tools, no third-party runtime or release keys."""
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts' / 'android-lifecycle'
SDK = Path(os.environ['ANDROID_HOME'])
TOOLS = SDK / 'build-tools' / '35.0.0'
ANDROID = SDK / 'platforms' / 'android-35' / 'android.jar'
PRODUCT = 'io.github.lightjunction.douyinuidfilter'

def run(*args):
    subprocess.run([str(x) for x in args], check=True)


def build(name, package, control=False):
    work = OUT / name
    work.mkdir(parents=True, exist_ok=True)
    key = work / 'test.jks'
    if not key.exists():
        run('keytool', '-genkeypair', '-keystore', key, '-storepass', 'android',
            '-keypass', 'android', '-alias', 'fixture', '-keyalg', 'RSA',
            '-validity', '2', '-dname', 'CN=Disposable Lifecycle Fixture')
    if control:
        component = ('<instrumentation android:name="io.github.lightjunction.lifecycle.SettingsControl" '
                     f'android:targetPackage="{PRODUCT}" />')
        app = '<application android:label="Lifecycle test control" android:debuggable="true" />'
        # Android requires instrumentation and target to share a signer. This key is
        # generated for this run and used to re-sign only the disposable product APK.
        source = ROOT / 'tests/android-lifecycle/SettingsControl.java'
    else:
        component = ''
        app = ('<application android:label="Synthetic lifecycle probe" android:debuggable="true">'
               '<activity android:name="io.github.lightjunction.lifecycle.ProbeActivity" '
               'android:exported="true" /></application>')
        source = ROOT / 'tests/android-lifecycle/ProbeActivity.java'
    manifest = work / 'AndroidManifest.xml'
    queries = ('<queries><provider android:authorities="io.github.lightjunction.douyinuidfilter.settings" /></queries>'
               if name == 'untrusted' else '')
    manifest.write_text(f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                        f'package="{package}"><uses-sdk android:minSdkVersion="27" '
                        f'android:targetSdkVersion="35" />{queries}{app}{component}</manifest>')
    classes = work / 'classes'
    classes.mkdir(exist_ok=True)
    sources = [source]
    if not control:
        sources.append(ROOT / 'app/src/main/java/io/github/lightjunction/douyinuidfilter/FilterConfig.java')
    run('javac', '-source', '8', '-target', '8', '-bootclasspath', ANDROID,
        '-d', classes, *sources)
    dex = work / 'dex'
    dex.mkdir(exist_ok=True)
    run(TOOLS / 'd8', '--min-api', '27', '--lib', ANDROID, '--output', dex,
        *sorted(classes.rglob('*.class')))
    apk = work / 'unsigned.apk'
    run(TOOLS / 'aapt2', 'link', '--manifest', manifest, '-I', ANDROID, '-o', apk)
    with zipfile.ZipFile(apk, 'a') as archive:
        archive.write(dex / 'classes.dex', 'classes.dex')
    aligned = work / 'aligned.apk'
    run(TOOLS / 'zipalign', '-f', '4', apk, aligned)
    run(TOOLS / 'apksigner', 'sign', '--ks', key, '--ks-key-alias', 'fixture',
        '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
        '--out', OUT / f'{name}.apk', aligned)
    if control:
        run(TOOLS / 'apksigner', 'sign', '--ks', key, '--ks-key-alias', 'fixture',
            '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
            '--out', OUT / 'settings.apk',
            ROOT / 'app/build/outputs/apk/debug/app-debug.apk')


if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    build('control', 'io.github.lightjunction.lifecycle.control', True)
    build('host', 'com.ss.android.ugc.aweme')
    build('untrusted', 'io.github.lightjunction.lifecycle.untrusted')
    certificates = []
    for name in ['settings', 'control', 'host', 'untrusted']:
        output = subprocess.check_output([str(TOOLS / 'apksigner'), 'verify', '--print-certs',
                                         str(OUT / f'{name}.apk')], text=True)
        digest = next(line.split(': ', 1)[1] for line in output.splitlines()
                      if 'certificate SHA-256 digest:' in line)
        certificates.append(digest)
        print(f'{name} signer SHA-256: {digest}', flush=True)
    assert certificates[0] == certificates[1]
    assert len({certificates[0], certificates[2], certificates[3]}) == 3
