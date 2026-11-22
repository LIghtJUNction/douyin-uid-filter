import contextlib
import io
from pathlib import Path
import tempfile
import unittest

from bump_version import MAX_VERSION_CODE, Version, main, next_version, read_version, update_file


class VersionTests(unittest.TestCase):
    def test_bumps_reset_lower_components_and_increment_android_code(self):
        current = Version("1.2.9", 42)
        for mode, expected in (("patch", "1.2.10"), ("minor", "1.3.0"), ("major", "2.0.0")):
            with self.subTest(mode=mode):
                self.assertEqual(next_version(current, mode), Version(expected, 43))

    def test_custom_version_cannot_downgrade_or_reuse_release(self):
        for name in ("1.2.9", "1.2.8", "0.9.9", "v2.0.0", "2.00.0", "2.0.0-beta.1", "2.0.0\nmalicious=value"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                next_version(Version("1.2.9", 42), "custom", name)
        self.assertEqual(next_version(Version("1.2.9", 42), "custom", "2.1.0"), Version("2.1.0", 43))

    def test_android_code_exhaustion_fails_before_write(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "version.properties"
            original = f"VERSION_NAME=1.0.0\nVERSION_CODE={MAX_VERSION_CODE}\n"
            path.write_text(original)
            with self.assertRaises(ValueError):
                update_file(path, "patch")
            self.assertEqual(path.read_text(), original)

    def test_invalid_properties_are_rejected(self):
        for text in (
            "VERSION_NAME=0.1.0\n",
            "VERSION_NAME=0.1.0\nVERSION_CODE=1\nVERSION_CODE=2\n",
            "VERSION_NAME=0.1.0\nVERSION_CODE=0\n",
            "VERSION_NAME=0.1.0\nVERSION_CODE=1.0\n",
            "VERSION_NAME=0.1.0\nVERSION_CODE=١\n",
        ):
            with self.subTest(text=text), self.assertRaises(ValueError):
                read_version(text)

    def test_none_preserves_file_and_bump_preserves_other_properties(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "version.properties"
            original = "# release metadata\nVERSION_NAME = 0.1.0\nVERSION_CODE = 1\nOTHER=keep\n"
            path.write_text(original)
            self.assertEqual(update_file(path, "none"), (Version("0.1.0", 1), False))
            self.assertEqual(path.read_text(), original)
            self.assertEqual(update_file(path, "minor"), (Version("0.2.0", 2), True))
            self.assertIn("OTHER=keep\n", path.read_text())
            self.assertIn("# release metadata\n", path.read_text())
            self.assertEqual(read_version(path.read_text()), Version("0.2.0", 2))

    def test_tag_mismatch_fails_without_modification(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "version.properties"
            original = "VERSION_NAME=0.1.0\nVERSION_CODE=1\n"
            path.write_text(original)
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                main(["none", "--file", str(path), "--expect-tag", "v0.2.0"])
            self.assertEqual(path.read_text(), original)

    def test_cli_produces_the_metadata_consumed_by_release_workflow(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "version.properties"
            output = Path(directory) / "output"
            path.write_text("VERSION_NAME=0.1.0\nVERSION_CODE=1\n")
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(main(["patch", "--file", str(path), "--github-output", str(output)]), 0)
            self.assertEqual(output.read_text(), "name=0.1.1\ncode=2\ntag=v0.1.1\nchanged=true\n")


if __name__ == "__main__":
    unittest.main()
