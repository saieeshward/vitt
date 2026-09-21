"""Dump the accessibility tree the Simulator publishes for the app under test.

This is the real thing VoiceOver reads: Compose's semantics are bridged into the
Simulator's own AX tree, so a label that shows up here is a label a screen-reader
user gets. Written against the AX API directly because the `System Events`
AppleScript bridge goes stale after the app relaunches and then reports every
role as missing.

    python3 tools/axtree.py                    # interactive elements only
    python3 tools/axtree.py --all              # every labelled node
    python3 tools/axtree.py --grep 'amount|€'  # case-insensitive regex on label/value

Frames are printed in device points relative to the device screen (x y wxh), the
same space the app lays out in; multiply by the device scale for screenshot
pixels (3 on the iPhone 17 Pro).

Why the polling: for a few seconds after the app is (re)launched the Simulator
already shows the window, and its bezel buttons, but the device-screen group has
no children yet, so a one-shot walk found nothing and printed nothing. We wait
for the screen to fill in rather than trust the first answer.
"""
import argparse
import os
import re
import subprocess
import sys
import time

from ApplicationServices import (
    AXIsProcessTrusted,
    AXUIElementCreateApplication,
    AXUIElementCopyAttributeValue,
    AXValueGetValue,
    kAXValueCGPointType,
    kAXValueCGSizeType,
)
from AppKit import NSWorkspace

SIMULATOR_BUNDLE = "com.apple.iphonesimulator"
CONTAINERS = {"AXGroup", "AXScrollArea", "AXWindow"}
INTERACTIVE = {"AXButton", "AXRadioButton", "AXCheckBox", "AXTextField",
               "AXSlider", "AXTabGroup", "AXImage"}


def attr(el, name):
    err, val = AXUIElementCopyAttributeValue(el, name, None)
    return val if err == 0 else None


def frame(el):
    pos, size = attr(el, "AXPosition"), attr(el, "AXSize")
    if pos is None or size is None:
        return None
    _, pt = AXValueGetValue(pos, kAXValueCGPointType, None)
    _, sz = AXValueGetValue(size, kAXValueCGSizeType, None)
    return pt.x, pt.y, sz.width, sz.height


def app_element():
    # By bundle id, not display name: a second Simulator from another Xcode, or a
    # helper whose name merely starts with "Simulator", must not be picked up.
    for a in NSWorkspace.sharedWorkspace().runningApplications():
        if a.bundleIdentifier() == SIMULATOR_BUNDLE:
            return AXUIElementCreateApplication(a.processIdentifier())
    sys.exit("Simulator.app is not running; boot a device and launch the app first.")


def device_name():
    """The booted device's name, which is also its window's title prefix.

    `SIM_DEVICE` overrides, and is required when more than one device is booted.
    """
    forced = os.environ.get("SIM_DEVICE")
    if forced:
        return forced
    out = subprocess.check_output(["xcrun", "simctl", "list", "devices"], text=True)
    booted = [ln.strip().split(" (")[0] for ln in out.splitlines() if "(Booted)" in ln]
    if not booted:
        sys.exit("no booted simulator; boot one first")
    if len(booted) > 1:
        sys.exit("several devices are booted: " + ", ".join(booted) +
                 "\nset SIM_DEVICE to the one you mean")
    return booted[0]


def device_screen(app):
    """The device screen is the one direct AXGroup child of the device window.

    The window also carries the bezel buttons and a toolbar; the screen group is
    the only thing whose children are the app's own semantics, so walking just it
    drops the Simulator's chrome without a hand-kept list of its button names.

    Matched by device name, not taken as the first window. Simulator keeps a
    window per device it has ever shown, including shut-down ones, so the first
    window is whichever opened earliest — which is how this script spent a
    session confidently dumping a different app's tree while `simctl io` was
    screenshotting the right device."""
    want = device_name()
    for window in (attr(app, "AXWindows") or []):
        if not str(attr(window, "AXTitle") or "").startswith(want):
            continue
        for child in (attr(window, "AXChildren") or []):
            if attr(child, "AXRole") == "AXGroup":
                return child
    return None


def walk(el, origin, depth=0, out=None):
    out = [] if out is None else out
    if depth > 60:
        return out
    role = attr(el, "AXRole")
    desc = attr(el, "AXDescription") or attr(el, "AXTitle") or ""
    value = attr(el, "AXValue")
    fr = frame(el)
    if fr is not None:
        fr = (fr[0] - origin[0], fr[1] - origin[1], fr[2], fr[3])
    if role:
        out.append((depth, str(role), str(desc),
                    "" if value is None else str(value), fr))
    for child in (attr(el, "AXChildren") or []):
        walk(child, origin, depth + 1, out)
    return out


def snapshot(timeout):
    """Walk the screen, waiting until the app has actually published something."""
    deadline = time.monotonic() + timeout
    screen = None
    while True:
        screen = device_screen(app_element())
        if screen is not None:
            fr = frame(screen) or (0, 0, 0, 0)
            rows = walk(screen, (fr[0], fr[1]))
            if any(r[1] not in CONTAINERS for r in rows):
                return rows
        if time.monotonic() > deadline:
            break
        time.sleep(0.25)
    if not AXIsProcessTrusted():
        sys.exit("This terminal is not allowed to read the accessibility tree.\n"
                 "System Settings > Privacy & Security > Accessibility: add it, "
                 "then rerun.")
    if screen is None:
        sys.exit("No device window in Simulator.app; is a device booted and "
                 "its window open?")
    sys.exit("The device screen has no accessible content after %.0fs; is the "
             "app running? (xcrun simctl launch booted ie.shoonya.vitt)" % timeout)


def fmt_frame(fr):
    return "" if fr is None else "%4.0f %4.0f %4.0fx%-4.0f" % fr


def main(argv):
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("--all", action="store_true",
                   help="every labelled node, not just interactive ones")
    p.add_argument("--grep", metavar="REGEX",
                   help="case-insensitive regex; keeps rows whose role, label or "
                        "value matches (implies --all)")
    p.add_argument("--timeout", type=float, default=8.0,
                   help="seconds to wait for the app to publish its tree")
    args = p.parse_args(argv)
    pattern = re.compile(args.grep, re.IGNORECASE) if args.grep else None
    shown = 0
    for depth, role, desc, value, fr in snapshot(args.timeout):
        if role in CONTAINERS:
            continue
        if not desc and not value:
            continue
        short = role.replace("AX", "")
        if pattern:
            if not any(pattern.search(s) for s in (short, desc, value)):
                continue
        elif not args.all and role not in INTERACTIVE:
            continue
        shown += 1
        print("%-14s %-18s %-58s %s" % (
            short, fmt_frame(fr), repr(desc), repr(value) if value else ""))
    if pattern and shown == 0:
        sys.exit("No node matches %r on the current screen." % args.grep)


if __name__ == "__main__":
    main(sys.argv[1:])
