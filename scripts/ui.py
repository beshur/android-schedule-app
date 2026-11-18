"""Tap UI elements by visible text via uiautomator. Usage: ui.py tap|long <text>"""
import re, subprocess, sys, xml.etree.ElementTree as ET

def nodes():
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
    xml = subprocess.run(["adb", "shell", "cat", "/sdcard/ui.xml"], capture_output=True, text=True).stdout
    for n in ET.fromstring(xml).iter("node"):
        t = n.get("text") or n.get("content-desc")
        if t:
            yield t, n.get("bounds")

cmd, text = sys.argv[1], sys.argv[2]
for t, b in nodes():
    if t == text:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", b))
        x, y = str((x1 + x2) // 2), str((y1 + y2) // 2)
        args = ["tap", x, y] if cmd == "tap" else ["swipe", x, y, x, y, "1200"]
        subprocess.run(["adb", "shell", "input", *args])
        sys.exit(0)
sys.exit(f"not found: {text!r}; on screen: {[t for t, _ in nodes()]}")
