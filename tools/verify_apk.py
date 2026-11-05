#!/usr/bin/env python3
"""Fail the build if the on-device helper did not end up inside the APK."""
import sys
import zipfile
from pathlib import Path

APK = Path("app/build/outputs/apk/debug/app-debug.apk")
HELPER = "assets/ezadb-helper.jar"


def main() -> int:
    if not APK.exists():
        print(f"error: APK not found at {APK}")
        return 1
    with zipfile.ZipFile(APK) as archive:
        names = archive.namelist()
        if HELPER not in names:
            assets = [n for n in names if n.startswith("assets/")]
            print(f"error: {HELPER} is missing from the APK; assets present: {assets[:20]}")
            return 1
        entry = archive.getinfo(HELPER)
        with archive.open(entry) as stream:
            payload = stream.read()
        if not zipfile.is_zipfile(__import__("io").BytesIO(payload)):
            print("error: helper asset is not a valid jar")
            return 1
        with zipfile.ZipFile(__import__("io").BytesIO(payload)) as jar:
            if "classes.dex" not in jar.namelist():
                print("error: helper jar has no classes.dex")
                return 1
            dex_size = jar.getinfo("classes.dex").file_size
    print(f"APK verified: {APK.stat().st_size} bytes, helper {entry.file_size} bytes, classes.dex {dex_size} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
