# android-schedule-app

Tiny Android TV app that, every day at a configured time (default 08:00):

1. wakes the device from standby,
2. presses Home (on Android TV boxes this sends HDMI-CEC "Text View On", so the TV turns on),
3. opens the configured app (if any).

## Install

Build artifacts come from GitHub Actions (`schedule-apk`). With ADB enabled on the device:

```sh
adb connect <device-ip>:5555
adb install app-debug.apk
adb shell settings put secure enabled_accessibility_services net.buznik.schedule/.WakeService
adb shell settings put secure accessibility_enabled 1
adb shell dumpsys deviceidle whitelist +net.buznik.schedule
```

The `enabled_accessibility_services` line replaces the whole list; if other services are enabled, append ours with `:` instead.

Enabling the accessibility service schedules the next run; it reschedules itself after every run and after reboot.

## Configure

Settings are stored on the device; no rebuild needed. Any subset of extras works:

```sh
adb shell am broadcast -n net.buznik.schedule/.AlarmReceiver --ei hour 7 --ei minute 30 --es app <package.name>
```

Find a package name with `adb shell pm list packages | grep -i <name>`. Result code `1` means the values were rejected (out of range, `--es` instead of `--ei`, or app not installed). `--es app ""` clears the app.

Check the saved settings and the next alarm:

```sh
adb shell run-as net.buznik.schedule cat shared_prefs/schedule.xml
adb shell dumpsys alarm | grep -A2 net.buznik.schedule
```

## Test

```sh
adb shell am broadcast -n net.buznik.schedule/.AlarmReceiver                  # run now
adb shell am broadcast -n net.buznik.schedule/.AlarmReceiver --ei in_seconds 120  # run in 2 min
```
