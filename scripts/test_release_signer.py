#!/usr/bin/env python3
"""Exercise the real signing gate with a public APK and disposable build outputs."""
import argparse
from pathlib import Path
import subprocess
import unittest
from check_release_signer import verify


class ReleaseSignerTests(unittest.TestCase):
    def test_accepts_published_v010_certificate(self):
        verify(OPTIONS.apksigner, OPTIONS.published)

    def test_rejects_valid_debug_apk_with_different_certificate(self):
        # Establish signature validity separately, then prove continuity is rejected.
        subprocess.run([str(OPTIONS.apksigner), 'verify', str(OPTIONS.debug)], check=True)
        with self.assertRaises(ValueError):
            verify(OPTIONS.apksigner, OPTIONS.debug)

    def test_rejects_unsigned_release_apk(self):
        with self.assertRaises(subprocess.CalledProcessError):
            verify(OPTIONS.apksigner, OPTIONS.unsigned)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['apksigner', 'published', 'debug', 'unsigned']:
        parser.add_argument('--' + name, type=Path, required=True)
    OPTIONS = parser.parse_args()
    unittest.main(argv=['test_release_signer.py'], verbosity=2)
