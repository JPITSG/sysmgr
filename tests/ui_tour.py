#!/usr/bin/env python3
"""Visual tour of every System Manager page, for checking layout in both themes.

Drives an already-running emulator or phone over adb: it opens each feature page
from the overview, scrolls to the end of it, and saves one stitched screenshot
per page. It only navigates and scrolls; it never taps a switch or a button.

    python3 tests/ui_tour.py --out /tmp/tour
    python3 tests/ui_tour.py --out /tmp/tour --themes light dark --pages "Remote Link" Beacon
    python3 tests/ui_tour.py --serial emulator-5554 --out /tmp/tour

Requires Python 3, adb on the PATH and Pillow (for the stitched images). The app
must be installed. Pages are found by the content description each overview tile
carries, so a page whose tile is hidden (Upgrade, until the server offers an
APK) is reported as missing rather than failing the run.
"""
import argparse
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

from PIL import Image, ImageChops, ImageStat

PACKAGE = "com.jpitsg.sysman"
PAGES = ["Remote Link", "OpenVPN", "VNC Server", "Beacon", "GPS Logger", "Notification History",
         "Notification Backup", "High Priority Alerts", "Battery Alerts", "Volume Control",
         "Reboot Automation", "Permissions", "Backup", "Upgrade", "Log", "Appearance"]


class Device:
    def __init__(self, serial):
        self.base = ["adb"] + (["-s", serial] if serial else [])
        size = re.search(r"(\d+)x(\d+)", self.run("shell", "wm", "size"))
        self.width, self.height = (int(size.group(1)), int(size.group(2))) if size else (1080, 2400)

    def run(self, *args, binary=False):
        out = subprocess.run(self.base + list(args), capture_output=True).stdout
        return out if binary else out.decode("utf-8", "replace")

    def screenshot(self, path):
        data = self.run("exec-out", "screencap", "-p", binary=True)
        with open(path, "wb") as handle:
            handle.write(data)

    def nodes(self):
        self.run("shell", "uiautomator", "dump", "/sdcard/ui_tour.xml")
        xml = self.run("shell", "cat", "/sdcard/ui_tour.xml")
        root = ET.fromstring(xml[xml.index("<"):])
        for node in root.iter("node"):
            match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
            if match:
                yield node, tuple(int(v) for v in match.groups())

    def tap(self, x, y):
        self.run("shell", "input", "tap", str(x), str(y))

    def tap_description(self, prefix, top=None, bottom=None):
        top = self.height * 0.10 if top is None else top
        bottom = self.height * 0.97 if bottom is None else bottom
        for node, (x1, y1, x2, y2) in self.nodes():
            if node.get("content-desc", "").startswith(prefix) and y1 > top and y2 < bottom:
                self.tap((x1 + x2) // 2, (y1 + y2) // 2)
                return True
        return False

    def swipe(self, up):
        """Scroll the page by about 70% of the screen; slow, so it does not fling."""
        x = self.width // 2
        near, far = int(self.height * 0.88), int(self.height * 0.18)
        a, b = (near, far) if up else (far, near)
        self.run("shell", "input", "swipe", str(x), str(a), str(x), str(b), "650")

    def back(self):
        self.run("shell", "input", "keyevent", "KEYCODE_BACK")

    def launch(self):
        self.run("shell", "monkey", "-p", PACKAGE, "-c", "android.intent.category.LAUNCHER", "1")


def content(path, device):
    """The part of a screenshot that carries page content, shrunk for comparison."""
    image = Image.open(path).convert("L")
    box = (0, int(device.height * 0.14), image.width - int(image.width * 0.04), int(device.height * 0.95))
    return image.crop(box).resize((72, 160))


def same(a, b):
    return a is not None and b is not None and ImageStat.Stat(ImageChops.difference(a, b)).mean[0] < 2.5


def open_page(device, title):
    for _ in range(4):
        device.swipe(up=False)
    time.sleep(0.4)
    for _ in range(10):
        if device.tap_description(title + ". "):
            return True
        device.swipe(up=True)
        time.sleep(0.3)
    return False


def capture(device, prefix, title, out_dir, max_screens=12):
    paths, last = [], None
    for index in range(max_screens):
        time.sleep(1.0 if index == 0 else 0.7)
        path = os.path.join(out_dir, "%s_%s_%02d.png" % (prefix, title.replace(" ", "_"), index))
        device.screenshot(path)
        current = content(path, device)
        if same(current, last):
            os.remove(path)
            break
        paths.append(path)
        last = current
        device.swipe(up=True)
    return paths


def stitch(paths, out_path, width=420):
    images = [Image.open(p).convert("RGB") for p in paths]
    height = int(images[0].height * width / images[0].width)
    sheet = Image.new("RGB", (width * len(images) + 10 * (len(images) - 1), height), (128, 128, 128))
    for i, image in enumerate(images):
        sheet.paste(image.resize((width, height), Image.LANCZOS), (i * (width + 10), 0))
    sheet.save(out_path)


def set_theme(device, theme):
    """Uses the header button, which names the theme it switches to."""
    for _ in range(4):
        device.swipe(up=False)
    time.sleep(0.4)
    # The header button sits above the area where page tiles are searched for.
    if device.tap_description("Switch to the %s theme" % theme, top=0):
        time.sleep(3.0)
    device.launch()
    time.sleep(1.5)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"), help="adb device (default: the only one)")
    parser.add_argument("--out", required=True, help="directory for the screenshots")
    parser.add_argument("--themes", nargs="*", choices=["light", "dark"], help="visit each page in these themes")
    parser.add_argument("--pages", nargs="*", default=PAGES, help="page titles to visit (default: all)")
    args = parser.parse_args()

    os.makedirs(args.out, exist_ok=True)
    device = Device(args.serial)
    device.launch()
    time.sleep(2.5)
    missing = []
    for theme in (args.themes or [None]):
        if theme:
            set_theme(device, theme)
        prefix = theme or "tour"
        for title in args.pages:
            if not open_page(device, title):
                missing.append(title)
                print("missing: %s" % title)
                continue
            paths = capture(device, prefix, title, args.out)
            out_path = os.path.join(args.out, "%s_%s.png" % (prefix, title.replace(" ", "_")))
            stitch(paths, out_path)
            for path in paths:
                os.remove(path)
            print("%-22s %d screens -> %s" % (title, len(paths), out_path))
            device.back()
            time.sleep(0.8)
    crash = device.run("logcat", "-d", "-b", "crash").strip()
    if crash:
        print("\ncrash buffer is not empty:\n" + crash[-1200:])
    return 1 if crash else 0


if __name__ == "__main__":
    sys.exit(main())
