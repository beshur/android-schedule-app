"""Tap UI elements by visible text via uiautomator. Usage: ui.py tap|long|next <text> (next = the element after it)"""
import re, subprocess, sys, time, xml.etree.ElementTree as ET

def nodes():
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
    xml = subprocess.run(["adb", "shell", "cat", "/sdcard/ui.xml"], capture_output=True, text=True).stdout
    for n in ET.fromstring(xml).iter("node"):
        t = n.get("text") or n.get("content-desc")
        if t:
            yield t, n.get("bounds")

cmd, text = sys.argv[1], sys.argv[2]
for _ in range(5):
    found = list(nodes())
    if any(t == text for t, _ in found):
        break
    time.sleep(1)
for i, (t, b) in enumerate(found):
    if t == text:
        if cmd == "next":
            t, b = found[i + 1]
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", b))
        x, y = str((x1 + x2) // 2), str((y1 + y2) // 2)
        args = ["swipe", x, y, x, y, "1200"] if cmd == "long" else ["tap", x, y]
        subprocess.run(["adb", "shell", "input", *args])
        sys.exit(0)
sys.exit(f"not found: {text!r}; on screen: {[t for t, _ in found]}")
