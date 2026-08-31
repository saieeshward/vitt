"""Drive the booted iOS Simulator with synthesized mouse events.

The Simulator maps host mouse input onto touches, so a press-drag-release is a
swipe and a click is a tap. Coordinates are given in *points within the device
screen*, which this script maps onto the Simulator window — so a caller can work
from a screenshot's own coordinate space instead of the host desktop's.
"""
import subprocess
import sys
import time

import Quartz


def screen_frame():
    """The device screen's own bounds on the host desktop.

    Ask the accessibility tree rather than deriving it from the window: the window
    is larger than the screen on every side (title bar plus a bezel), and guessing
    those insets put every tap ~34px high — enough to miss a 48pt button while
    still landing on the much larger keypad keys, which made the app look like it
    was ignoring taps when the aim was simply wrong.

    The Simulator exposes the screen as the one AXGroup whose size matches the
    device's point dimensions.
    """
    out = subprocess.check_output([
        "osascript", "-e",
        'tell application "System Events" to tell process "Simulator" to tell window 1 '
        'to get {position, size} of (first UI element whose role is "AXGroup")',
    ], text=True).strip()
    x, y, w, h = [int(v) for v in out.split(", ")]
    return x, y, w, h


def to_host(px, py, shot_w, shot_h):
    """Map screenshot pixels to host desktop coordinates.

    The screenshot is the screen at its native scale factor, so the ratio of
    screenshot pixels to screen points gives the scale directly — no need to know
    it in advance.
    """
    x, y, w, h = screen_frame()
    return x + px * w / shot_w, y + py * h / shot_h


def post(event):
    Quartz.CGEventPost(Quartz.kCGHIDEventTap, event)


def move(x, y):
    post(Quartz.CGEventCreateMouseEvent(
        None, Quartz.kCGEventMouseMoved, (x, y), Quartz.kCGMouseButtonLeft))


def drag(x1, y1, x2, y2, steps=28, hold=0.004):
    """Press, move in small increments, release. Increments matter: a single jump
    reads as a flick with no travel and Compose ignores it."""
    move(x1, y1)
    time.sleep(0.05)
    post(Quartz.CGEventCreateMouseEvent(
        None, Quartz.kCGEventLeftMouseDown, (x1, y1), Quartz.kCGMouseButtonLeft))
    time.sleep(0.05)
    for i in range(1, steps + 1):
        x = x1 + (x2 - x1) * i / steps
        y = y1 + (y2 - y1) * i / steps
        post(Quartz.CGEventCreateMouseEvent(
            None, Quartz.kCGEventLeftMouseDragged, (x, y), Quartz.kCGMouseButtonLeft))
        time.sleep(hold)
    time.sleep(0.05)
    post(Quartz.CGEventCreateMouseEvent(
        None, Quartz.kCGEventLeftMouseUp, (x2, y2), Quartz.kCGMouseButtonLeft))


def click(x, y, hold=0.18):
    """Press and release with a real dwell.

    A 60ms tap was reliably registered by the app's own keypad keys but ignored by
    Material's Button inside a bottom sheet — the sheet's drag handling appears to
    need the pointer to settle before it yields the gesture. Moving first, then
    holding, fixes it."""
    move(x, y)
    time.sleep(0.12)
    post(Quartz.CGEventCreateMouseEvent(
        None, Quartz.kCGEventLeftMouseDown, (x, y), Quartz.kCGMouseButtonLeft))
    time.sleep(hold)
    post(Quartz.CGEventCreateMouseEvent(
        None, Quartz.kCGEventLeftMouseUp, (x, y), Quartz.kCGMouseButtonLeft))


def activate():
    subprocess.run(["osascript", "-e", 'tell application "Simulator" to activate'])
    time.sleep(0.6)


SHOT = (1206, 2622)  # iPhone 17 Pro screenshot pixels

if __name__ == "__main__":
    activate()
    cmd = sys.argv[1]
    if cmd == "tap":
        px, py = float(sys.argv[2]), float(sys.argv[3])
        click(*to_host(px, py, *SHOT))
    elif cmd == "swipe":
        px1, py1, px2, py2 = [float(v) for v in sys.argv[2:6]]
        x1, y1 = to_host(px1, py1, *SHOT)
        x2, y2 = to_host(px2, py2, *SHOT)
        drag(x1, y1, x2, y2)
    elif cmd == "type":
        subprocess.run([
            "osascript", "-e",
            f'tell application "System Events" to keystroke "{sys.argv[2]}"',
        ])
    time.sleep(0.5)
