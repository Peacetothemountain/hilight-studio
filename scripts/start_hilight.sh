#!/system/bin/sh
pkill -TERM -f 'com.hilight.core.AdbHelper|com.hilight.studio:hilight' 2>/dev/null
sleep 0.2
APK=$(pm path com.hilight.studio 2>/dev/null | head -1 | cut -d: -f2)
if [ -z "$APK" ]; then
    echo "ERROR: com.hilight.studio is not installed on this device."
    exit 1
fi
INSTANCE="adb-$(cat /proc/sys/kernel/random/uuid 2>/dev/null)"
CLASSPATH="$APK" nohup setsid app_process / com.hilight.core.AdbHelper --owner adb --instance "$INSTANCE" --exclusive </dev/null >/data/local/tmp/hilight.log 2>&1 &
sleep 0.6
PID=$(pgrep -f com.hilight.core.AdbHelper)
if [ -n "$PID" ]; then
    echo "SUCCESS: PID $PID"
else
    echo "FAILED: Could not start AdbHelper"
    cat /data/local/tmp/hilight.log 2>/dev/null
    exit 1
fi