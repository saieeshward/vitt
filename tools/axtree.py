"""Dump the accessibility tree the Simulator publishes for the app under test.

This is the real thing VoiceOver reads: Compose's semantics are bridged into the
Simulator's own AX tree, so a label that shows up here is a label a screen-reader
user gets. Written against the AX API directly because the `System Events`
AppleScript bridge goes stale after the app relaunches and then reports every
role as missing.

    python3 tools/axtree.py            # interactive elements only
    python3 tools/axtree.py --all      # every labelled node
"""
import sys

from ApplicationServices import (
    AXUIElementCreateApplication,
    AXUIElementCopyAttributeValue,
)
from AppKit import NSWorkspace

# The Simulator's own chrome, which is never what we are inspecting.
CHROME = {
    "Action", "Volume Up", "Volume Down", "Sleep/Wake", "Home", "Save Screen",
    "Rotate", "toolbar", "button", "close button", "full screen button",
    "minimize button", "text",
}
INTERACTIVE = {"AXButton", "AXRadioButton", "AXCheckBox", "AXTextField",
               "AXSlider", "AXTabGroup", "AXImage"}


def attr(el, name):
    err, val = AXUIElementCopyAttributeValue(el, name, None)
    return val if err == 0 else None


def app_element():
    pid = next(
        a.processIdentifier()
        for a in NSWorkspace.sharedWorkspace().runningApplications()
        if a.localizedName() == "Simulator"
    )
    return AXUIElementCreateApplication(pid)


def walk(el, depth=0, out=None):
    out = [] if out is None else out
    if depth > 60:
        return out
    role = attr(el, "AXRole")
    desc = attr(el, "AXDescription") or attr(el, "AXTitle") or ""
    value = attr(el, "AXValue")
    if role and str(desc) not in CHROME:
        out.append((depth, str(role), str(desc), "" if value is None else str(value)))
    for child in (attr(el, "AXChildren") or []):
        walk(child, depth + 1, out)
    return out


if __name__ == "__main__":
    show_all = "--all" in sys.argv
    rows = []
    for window in (attr(app_element(), "AXWindows") or []):
        walk(window, 0, rows)
    for depth, role, desc, value in rows:
        if role in ("AXGroup", "AXScrollArea", "AXWindow"):
            continue
        if not show_all and role not in INTERACTIVE:
            continue
        if not desc and not value:
            continue
        short = role.replace("AX", "")
        print("%-11s %-58s %s" % (short, repr(desc), repr(value) if value else ""))
