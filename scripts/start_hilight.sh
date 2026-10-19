#!/system/bin/sh
set -eu

APK=$(pm path com.hilight.studio 2>/dev/null | head -1 | tr -d '\r' | cut -d: -f2)
if [ -z "$APK" ]; then
    echo "ERROR: com.hilight.studio is not installed on this device."
    exit 1
fi

INSTANCE="adb-$(cat /proc/sys/kernel/random/uuid 2>/dev/null | tr -d '\r\n')"
if [ "$INSTANCE" = "adb-" ]; then
    echo "ERROR: could not create a renderer instance ID."
    exit 1
fi

live=1
i=0
while [ "$i" -lt 65 ] && [ -n "$live" ]; do
    live=""
    for d in /proc/[0-9]*; do
        p=${d#/proc/}
        c=$(tr '\000' ' ' < "$d/cmdline" 2>/dev/null || true)
        if [ -z "$c" ]; then
            e=$(readlink "$d/exe" 2>/dev/null || true)
            x=${e##*/}
            if [ "$x" = app_process ] || [ "$x" = app_process32 ] || [ "$x" = app_process64 ]; then
                echo "ERROR: unresolved app_process $p"
                exit 1
            fi
            continue
        fi
        set -- $c
        x=${1##*/}
        if { { [ "$x" = app_process ] || [ "$x" = app_process32 ] || [ "$x" = app_process64 ]; } && [ "${2:-x}" = / ] && [ "${3:-x}" = com.hilight.core.AdbHelper ]; } || [ "${1:-x}" = com.hilight.studio:hilight ]; then
            kill -TERM "$p" 2>/dev/null || { echo "ERROR: could not stop existing renderer $p"; exit 1; }
            live=1
        fi
    done
    [ -n "$live" ] && sleep 0.1
    i=$((i + 1))
done
if [ -n "$live" ]; then
    echo "ERROR: existing renderer did not exit in time."
    exit 1
fi

CLASSPATH="$APK" nohup setsid app_process / com.hilight.core.AdbHelper --owner adb --instance "$INSTANCE" --exclusive </dev/null >/data/local/tmp/hilight.log 2>&1 &
sleep 4
PID=$(pgrep -f "com.hilight.core.AdbHelper.*--instance $INSTANCE" 2>/dev/null || true)
if [ -n "$PID" ]; then
    echo "SUCCESS: PID $PID"
else
    echo "FAILED: Could not start AdbHelper"
    cat /data/local/tmp/hilight.log 2>/dev/null || true
    exit 1
fi