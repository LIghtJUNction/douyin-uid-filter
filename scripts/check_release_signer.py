#!/usr/bin/env python3
"""Reject APKs not signed by the published v0.1.0 certificate, without loading keys."""
import argparse
from pathlib import Path
import re
import subprocess

PUBLISHED_CERTIFICATE = '7b852dc7c018d02cd70382e6fa8df0e743a00e787b4d8a22ad4097eeb866ebec'


def verify(apksigner: Path, apk: Path) -> None:
    result = subprocess.run([str(apksigner), 'verify', '--print-certs', str(apk)],
                            capture_output=True, text=True, check=True)
    certificates = re.findall(r'^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]+)$',
                              result.stdout, re.MULTILINE)
    if [value.lower() for value in certificates] != [PUBLISHED_CERTIFICATE]:
        raise ValueError('APK signer differs from the published v0.1.0 certificate')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apksigner', type=Path, required=True)
    parser.add_argument('--apk', type=Path, required=True)
    args = parser.parse_args()
    try:
        verify(args.apksigner, args.apk)
    except (OSError, subprocess.CalledProcessError, ValueError) as error:
        parser.exit(1, f'Release signing gate rejected APK: {type(error).__name__}\n')
    print('Release signing certificate matches published v0.1.0')
