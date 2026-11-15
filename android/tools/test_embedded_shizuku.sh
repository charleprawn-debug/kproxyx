#!/usr/bin/env bash
# Keep cleanup/logging in one shell: the emulator action executes each script
# input line separately and otherwise exits on the first failing command.
set +e
TRANSPORT=/data/local/tmp/kproxyx-transport-test
adb shell rm -f "$TRANSPORT.command" "$TRANSPORT.ready" "$TRANSPORT.closed"
(
    CLIENT=
    trap 'if [[ -n "$CLIENT" ]]; then kill "$CLIENT" 2>/dev/null; fi' EXIT
    for ((i=0; i<720; i++)); do
        adb shell test -f "$TRANSPORT.command" && break
        sleep 0.5
    done
    # A real adbd terminal, not UiAutomation's pipe: disconnecting this
    # channel must not kill a privileged server or any bound UserService.
    adb shell -tt sh "$TRANSPORT.command" </dev/null >/tmp/kproxyx-launch-channel.log 2>&1 &
    CLIENT=$!
    for ((i=0; i<120; i++)); do
        adb shell test -f "$TRANSPORT.ready" && break
        sleep 0.5
    done
    if adb shell test -f "$TRANSPORT.ready"; then
        kill "$CLIENT" 2>/dev/null
        wait "$CLIENT" 2>/dev/null
        CLIENT=
        adb shell touch "$TRANSPORT.closed"
    fi
) &
CONTROLLER=$!
timeout 360s bash ./gradlew :shizuku-server:connectedDebugAndroidTest --no-daemon \
    -Pandroid.testInstrumentationRunnerArguments.timeout_msec=90000
RESULT=$?
kill "$CONTROLLER" 2>/dev/null
wait "$CONTROLLER" 2>/dev/null
adb logcat -d -v brief \
    -s KProxyXShizukuTest:I KProxyXShizuku:V TestRunner:I AndroidRuntime:E ShizukuServiceStarter:E '*:S' \
    | sed -E 's/[[:xdigit:]]{8}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{12}(-[0-9]+)?/[redacted]/g'
exit "$RESULT"
