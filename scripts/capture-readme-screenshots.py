"""Run the screenshot suite on one emulator and collect its output before cleanup."""

import argparse
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "cum.ahfu.mobi.material"
NAMES = ("apps", "release", "downloads", "appearance")
parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True)
parser.add_argument("--adb", default="adb")
args = parser.parse_args()
if not re.fullmatch(r"emulator-\d+", args.serial):
    parser.error("Select an emulator, not a physical device.")


def adb(*command):
    return subprocess.run([args.adb, "-s", args.serial, *command], check=True,
                          capture_output=True, text=True, encoding="utf-8", timeout=180).stdout


if adb("shell", "getprop", "ro.kernel.qemu").strip() != "1":
    raise RuntimeError("The selected device is not an emulator.")

version = re.search(r'^\s*versionName\s*=\s*"([0-9.]+)"',
                    (ROOT / "app/build.gradle.kts").read_text(), re.MULTILINE).group(1)
apk_dir = ROOT / "app/build/outputs/apk"
adb("install", "-r", str(apk_dir / f"debug/MobilismMaterial_v{version}-debug.apk"))
adb("install", "-r", str(apk_dir / f"androidTest/debug/MobilismMaterial_v{version}-debug-androidTest.apk"))
output = f"/sdcard/Android/data/{PACKAGE}/files/readme-screenshots"
adb("shell", "rm", "-f", *(f"{output}/{name}.png" for name in NAMES))
result = adb("shell", "am", "instrument", "-w", "-r", "-e", "class",
             "org.mobilism.material.ReadmeScreenshots",
             f"{PACKAGE}.test/org.mobilism.material.ReadmeScreenshotRunner")
passed = re.search(r"OK \(([1-9][0-9]*) tests?\)", result)
# adb can exit successfully even when JUnit fails.
if not passed or "FAILURES!!!" in result or "INSTRUMENTATION_FAILED" in result:
    raise RuntimeError(result)
print(f"Passed {passed.group(1)} emulator checks.")
for name in NAMES:
    adb("pull", f"{output}/{name}.png", str(ROOT / f"docs/images/{name}.png"))
    print(f"Captured {name}.png")
