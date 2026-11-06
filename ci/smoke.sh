#!/bin/bash
# Дымовой тест на эмуляторе: установка, сканирование тестовых треков, скриншоты всех экранов.
set -x
PKG=su.radiogram.grom
OUT=out/shots
mkdir -p "$OUT"
adb wait-for-device
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
adb install -r out/Grom.apk > "$OUT/install.txt" 2>&1 || { cat "$OUT/install.txt"; exit 0; }

adb shell mkdir -p /sdcard/Music/Grom /sdcard/Recordings
adb push ci/audio/voice/*.m4a /sdcard/Recordings/
for f in ci/audio/*.mp3; do adb push "$f" /sdcard/Music/Grom/; sleep 1; done
adb shell content call --uri content://media --method scan_volume --arg external_primary || true
sleep 6
adb shell content query --uri content://media/external/audio/media --projection _id:title:artist:duration:_data > "$OUT/media.txt" 2>&1

shot() { sleep "${2:-3}"; adb exec-out screencap -p > "$OUT/$1.png"; }
start() { adb shell am start -n $PKG/.MainActivity --es ci "$1" > /dev/null; }

adb logcat -c
adb shell am start -n $PKG/.MainActivity > /dev/null
shot 00_permission 6
adb shell am force-stop $PKG
adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell am start -n $PKG/.MainActivity > /dev/null
shot 01_library 7
start "play,fav"
shot 02_library_playing 6
start "player"
shot 03_player 5
shot 03b_player_later 3
start "8d,preset"
shot 04_player_8d 4
start "lyrics"
shot 05_lyrics 5
start "nolyrics,eq"
shot 06_eq 4
adb shell input swipe 540 1900 540 500 600
shot 07_eq_faders 3
adb shell input swipe 540 1900 540 500 600
shot 08_eq_bottom 3
start "lists"
shot 09_lists 4
start "settings"
shot 10_settings 4
start "driving"
shot 11_driving 4
start "home,tracks,sleep"
shot 12_sleep 4
start "dismiss,queue"
shot 13_queue 4
start "dismiss,actions"
shot 14_actions 4
start "dismiss,player"
shot 15_player_final 4
adb shell cmd statusbar expand-notifications
shot 16_notification 3
adb shell cmd statusbar collapse

adb shell dumpsys media_session > "$OUT/media_session.txt" 2>&1
adb shell dumpsys activity services $PKG > "$OUT/services.txt" 2>&1
adb logcat -d > "$OUT/logcat.txt"
grep -E "AndroidRuntime|FATAL|ExoPlayer|grom|Grom|MediaCodec.*rror|AudioTrack" "$OUT/logcat.txt" | tail -400 > "$OUT/important.txt"
exit 0
