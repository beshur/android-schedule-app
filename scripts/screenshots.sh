#!/usr/bin/env bash
set -euo pipefail
out=screenshots
mkdir -p "$out"
R="-n net.buznik.schedule/.AlarmReceiver"
shot() { sleep 2; adb exec-out screencap -p > "$out/$1.png"; }
tap() { python3 scripts/ui.py tap "$1" || echo "skipped tap: $1"; }

adb install -r app/build/outputs/apk/debug/*.apk
adb shell am broadcast $R --es action add --es app com.android.tv.settings --ei hour 8 --ei minute 0 \
  --es rrule "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
adb shell am broadcast $R --es action add --ei hour 7 --ei minute 30 --es rrule "FREQ=MONTHLY;BYDAY=-1FR"
adb shell am broadcast $R --es action add --es app com.android.tv.settings --ei hour 10 --ei minute 15 \
  --es rrule "FREQ=WEEKLY;INTERVAL=2;BYDAY=SA,SU;COUNT=10" --ez enabled false
adb shell am broadcast $R

adb shell am start -W -n net.buznik.schedule/.MainActivity
shot 01-list-accessibility-off

adb shell settings put secure enabled_accessibility_services net.buznik.schedule/.WakeService
adb shell settings put secure accessibility_enabled 1
adb shell am force-stop net.buznik.schedule
sleep 3
adb shell am start -W -n net.buznik.schedule/.MainActivity
shot 02-list

tap "Every weekday (Monday to Friday)"
shot 03-editor
tap "Every weekday (Monday to Friday)"
shot 04-repeat-menu
tap "Custom…"
shot 05-custom-weekly
tap "week"
shot 06-custom-unit-menu
tap "month"
shot 07-custom-monthly
tap "Cancel"
tap "Settings"
shot 08-app-picker
tap "Cancel"
tap "$(date +'%A, %B %-d')"
shot 09-date-picker
tap "Cancel"
tap "08:00"
shot 10-time-picker
