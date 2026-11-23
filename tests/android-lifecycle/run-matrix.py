#!/usr/bin/env python3
"""Real-system lifecycle observations. Runs only on this job's isolated AVD."""
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/android-lifecycle'
ADB = [str(Path(os.environ['ANDROID_HOME']) / 'platform-tools/adb'), '-s', 'emulator-5554']
HOST = 'com.ss.android.ugc.aweme'
PRODUCT = 'io.github.lightjunction.douyinuidfilter'
UNTRUSTED = 'io.github.lightjunction.lifecycle.untrusted'
ACTIVITY = 'io.github.lightjunction.lifecycle.ProbeActivity'
REPORT = {'kind': 'synthetic Android system test; not Douyin/Xposed validation', 'cases': []}
FAILURES = []


def adb(*args, timeout=30, check=True):
    process = subprocess.run([*ADB, *args], capture_output=True, text=True,
                             timeout=timeout, check=check)
    return process.stdout + process.stderr


def save():
    (OUT / 'report.json').write_text(json.dumps(REPORT, indent=2))


def wait_boot():
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        if adb('shell', 'getprop', 'sys.boot_completed', timeout=10, check=False).strip() == '1':
            adb('shell', 'input', 'keyevent', '82')
            return
        time.sleep(2)
    raise RuntimeError('AVD boot exceeded 180 seconds')


def settings(write=False, enabled=True):
    output = adb('shell', 'am', 'instrument', '-w', '-e', 'write', str(write).lower(),
                 '-e', 'enabled', str(enabled).lower(), '-e', 'open_settings', 'true',
                 'io.github.lightjunction.lifecycle.control/io.github.lightjunction.lifecycle.SettingsControl')
    print('settings control:', output, flush=True)
    if 'INSTRUMENTATION_CODE: -1' not in output or 'error=' in output:
        raise AssertionError('test settings control failed')
    return output


def probe(label, package=HOST, expected=True):
    adb('logcat', '-c')
    adb('shell', 'am', 'start', '-W', '--activity-new-task', '--activity-clear-task',
        '-n', f'{package}/{ACTIVITY}')
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        logs = adb('logcat', '-d', '-v', 'brief', '-s', 'LifecycleProbe:I', '*:S')
        for line in logs.splitlines():
            start = line.find('{')
            if start < 0:
                continue
            value = json.loads(line[start:])
            value['case'] = label
            REPORT['cases'].append(value)
            print(json.dumps(value), flush=True)
            if expected is None:
                good = value['state'] == 'unavailable' and value.get('error') == 'SecurityException'
            else:
                good = (value['state'] == 'read' and value['enabled'] == expected
                        and value['visible'] == (1 if expected else 2))
            if not good:
                FAILURES.append(label)
            save()
            return value
        time.sleep(0.5)
    raise AssertionError(f'no probe output for {label}')


def settings_force_stop(package):
    # Exercise Android Settings UI, rather than substituting a process kill.
    adb('shell', 'am', 'start', '-W', '-a', 'android.settings.APPLICATION_DETAILS_SETTINGS',
        '-d', 'package:' + package)
    def click(text):
        adb('shell', 'uiautomator', 'dump', '/sdcard/lifecycle-window.xml')
        xml = adb('shell', 'cat', '/sdcard/lifecycle-window.xml')
        root = ET.fromstring(xml[xml.index('<?xml'):])
        for node in root.iter('node'):
            if node.get('text', '').lower() == text.lower() and node.get('enabled') == 'true':
                nums = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
                if len(nums) == 4:
                    adb('shell', 'input', 'tap', str((nums[0] + nums[2]) // 2),
                        str((nums[1] + nums[3]) // 2))
                    return
        raise AssertionError('Android Settings button unavailable: ' + text)
    click('Force stop')
    click('OK')
    if adb('shell', 'pidof', package, check=False).strip():
        raise AssertionError('Settings force stop left host process running')


def main():
    # Explicit emulator serial prevents accidentally selecting any other device.
    wait_boot()
    REPORT['android'] = adb('shell', 'getprop', 'ro.build.version.release').strip()
    REPORT['api'] = adb('shell', 'getprop', 'ro.build.version.sdk').strip()
    for name in ['settings', 'control', 'host', 'untrusted']:
        output = adb('install', str(OUT / f'{name}.apk'), timeout=60)
        if 'Success' not in output:
            raise AssertionError('fixture install failed: ' + name)
    # The untrusted APK has authority visibility so this tests Binder caller
    # rejection, independently of Android package-visibility denial.
    probe('untrusted-before-grant', UNTRUSTED, None)
    settings(write=True)
    first = probe('first-settings-grant')
    probe('untrusted-after-grant', UNTRUSTED, None)
    adb('shell', 'input', 'keyevent', 'HOME')
    adb('shell', 'am', 'kill', HOST)
    if adb('shell', 'pidof', HOST, check=False).strip():
        raise AssertionError('ordinary background kill did not stop host')
    cold = probe('ordinary-process-cold-start')
    if first.get('pid') == cold.get('pid'):
        raise AssertionError('host PID did not change')
    settings_force_stop(HOST)
    probe('settings-force-stop-direct-host')
    settings()
    recovered = probe('reopen-settings-after-force-stop')
    adb('reboot')
    adb('wait-for-disconnect', timeout=30)
    adb('wait-for-device', timeout=90)
    wait_boot()
    rebooted = probe('device-reboot-direct-host')
    # Do not rewrite settings during recovery: observe persisted revision.
    control = settings()
    persisted = probe('reopen-settings-after-reboot')
    if first.get('revision') != persisted.get('revision'):
        FAILURES.append('persisted-revision')
    if 'enabled=true' not in control:
        FAILURES.append('persisted-switch')
    settings(write=True, enabled=False)
    probe('disabled-visible-data', expected=False)
    settings(write=True, enabled=True)
    probe('reenabled-visible-data')
    REPORT['failed_acceptance_cases'] = FAILURES
    REPORT['conclusion'] = 'synthetic acceptance passed' if not FAILURES else 'lifecycle behavior requires investigation'
    save()
    print(json.dumps(REPORT, indent=2), flush=True)
    if FAILURES:
        raise AssertionError('lifecycle acceptance failed: ' + ', '.join(FAILURES))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        REPORT['runner_error'] = type(error).__name__ + ': ' + str(error)
        save()
        raise
