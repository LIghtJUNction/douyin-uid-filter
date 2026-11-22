#!/usr/bin/env python3
"""Read or advance the Android release version without external dependencies."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path
import re
import sys


MAX_VERSION_CODE = 2_100_000_000
SEMVER = re.compile(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\Z")


@dataclass(frozen=True)
class Version:
    name: str
    code: int

    @property
    def parts(self) -> tuple[int, int, int]:
        match = SEMVER.fullmatch(self.name)
        if not match:
            raise ValueError("VERSION_NAME must use MAJOR.MINOR.PATCH, without a v prefix")
        return tuple(int(part) for part in match.groups())

    def validate(self) -> None:
        self.parts
        if not 1 <= self.code <= MAX_VERSION_CODE:
            raise ValueError(f"VERSION_CODE must be between 1 and {MAX_VERSION_CODE}")


def read_version(text: str) -> Version:
    values: dict[str, str] = {}
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        key, separator, value = line.partition("=")
        key = key.strip()
        if key not in ("VERSION_NAME", "VERSION_CODE"):
            continue
        if not separator or key in values:
            raise ValueError(f"Missing value or duplicate property: {key}")
        values[key] = value.strip()
    if set(values) != {"VERSION_NAME", "VERSION_CODE"}:
        raise ValueError("version.properties must contain VERSION_NAME and VERSION_CODE")
    if not re.fullmatch(r"[1-9][0-9]*", values["VERSION_CODE"]):
        raise ValueError("VERSION_CODE must be a positive decimal integer")
    version = Version(values["VERSION_NAME"], int(values["VERSION_CODE"]))
    version.validate()
    return version


def next_version(current: Version, mode: str, custom: str | None = None) -> Version:
    current.validate()
    if mode == "none":
        if custom:
            raise ValueError("--custom-version is only valid with custom")
        return current
    major, minor, patch = current.parts
    if mode == "patch":
        name = f"{major}.{minor}.{patch + 1}"
    elif mode == "minor":
        name = f"{major}.{minor + 1}.0"
    elif mode == "major":
        name = f"{major + 1}.0.0"
    elif mode == "custom":
        if not custom:
            raise ValueError("custom requires --custom-version")
        name = custom
    else:
        raise ValueError(f"Unknown bump mode: {mode}")
    if mode != "custom" and custom:
        raise ValueError("--custom-version is only valid with custom")
    result = Version(name, current.code + 1)
    result.validate()
    if result.parts <= current.parts:
        raise ValueError("The new release version must be greater than the current version")
    return result


def update_file(path: Path, mode: str, custom: str | None = None) -> tuple[Version, bool]:
    text = path.read_text(encoding="utf-8")
    current = read_version(text)
    result = next_version(current, mode, custom)
    if result == current:
        return result, False
    for key, value in (("VERSION_NAME", result.name), ("VERSION_CODE", str(result.code))):
        text = re.sub(rf"(?m)^([ \t]*{key}[ \t]*=)[^\r\n]*", lambda m: m[1] + value, text)
    path.write_text(text, encoding="utf-8")
    return result, True


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("patch", "minor", "major", "custom", "none"))
    parser.add_argument("--custom-version")
    parser.add_argument("--file", type=Path, default=Path(__file__).resolve().parents[1] / "version.properties")
    parser.add_argument("--expect-tag", help="Fail unless the current version matches this release tag")
    parser.add_argument("--github-output", type=Path, help="Append release metadata to a GitHub Actions output file")
    args = parser.parse_args(argv)
    try:
        if args.expect_tag:
            current = read_version(args.file.read_text(encoding="utf-8"))
            if args.mode != "none" or args.expect_tag != f"v{current.name}":
                raise ValueError("Release tag does not match version.properties")
        result, changed = update_file(args.file, args.mode, args.custom_version)
    except (OSError, ValueError) as error:
        parser.error(str(error))
    output = f"name={result.name}\ncode={result.code}\ntag=v{result.name}\nchanged={str(changed).lower()}\n"
    sys.stdout.write(output)
    if args.github_output:
        with args.github_output.open("a", encoding="utf-8") as stream:
            stream.write(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
