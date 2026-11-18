# android-schedule-app

Android TV app that wakes the device on a schedule, turns the TV on and opens an app.

Each run:

1. wakes the device from standby,
2. presses Home (on Android TV boxes this sends HDMI-CEC "Text View On", so the TV turns on),
3. opens the schedule's app (optional).

Schedules are managed in the app (it shows up on the TV home screen): pick the app, date, time and a repeat rule
like Google Calendar's (does not repeat, daily, weekly, monthly on the Nth weekday, annually, every weekday, or
custom: every N days/weeks/months/years, weekdays, monthly by day or weekday, ending never / on a date / after N times).
If several schedules fall on the same minute, the first one in the list runs.

## Install

Every push to `main` builds a signed APK and publishes it as the latest [release](../../releases/latest) (`v1.0.<build number>`). Other branches upload a debug APK as a workflow artifact.

With ADB enabled on the device:

```sh
adb connect <device-ip>:5555
adb install -r schedule-<version>.apk
adb shell settings put secure enabled_accessibility_services net.buznik.schedule/.WakeService
adb shell settings put secure accessibility_enabled 1
adb shell dumpsys deviceidle whitelist +net.buznik.schedule
```

The `enabled_accessibility_services` line replaces the whole list; if other services are enabled, append ours with `:` instead.
The accessibility service can also be turned on from the app.

## adb interface

All commands go to one receiver and print their result; result code `1` means the request was rejected.

```sh
R="-n net.buznik.schedule/.AlarmReceiver"
adb shell am broadcast $R                                    # list schedules (JSON)
adb shell am broadcast $R --es action add --es app <package> --ei hour 7 --ei minute 30 \
    --es date 2026-11-18 --es rrule "'FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,FR'"   # prints the new id
adb shell am broadcast $R --es action update --es id <id> --ez enabled false
adb shell am broadcast $R --es action delete --es id <id>
adb shell am broadcast $R --es action run [--es id <id>]                  # run now
adb shell am broadcast $R --es action test --ei in_seconds 120 [--es id <id>]  # run once in 2 min
```

`add`/`update` accept any of `app`, `hour`, `minute` (`--ei`), `date` (`YYYY-MM-DD`), `rrule`, `enabled` (`--ez`).
`rrule` is an RFC 5545 subset: `FREQ` (DAILY/WEEKLY/MONTHLY/YEARLY), `INTERVAL`, `BYDAY` (`MO,WE` for weekly; `3WE` or
`-1FR` for monthly), `UNTIL=YYYYMMDD`, `COUNT`. An empty `rrule` means "does not repeat". `adb shell` runs the command through the device shell again, so a rule containing
`;` needs the inner single quotes shown above. Find package names with
`adb shell pm list packages | grep -i <name>`.

## Release signing

`main` builds need two repository secrets: `KEYSTORE_BASE64` (a base64-encoded PKCS12 keystore with key alias `schedule`) and `KEYSTORE_PASSWORD` (used for both the store and the key). Keep the keystore: updates signed with a different key cannot be installed over the existing app.
