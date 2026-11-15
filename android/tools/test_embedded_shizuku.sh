#!/usr/bin/env bash
# Keep cleanup/logging in one shell: the emulator action executes each script
# input line separately and otherwise exits on the first failing command.
set +e
timeout 360s bash ./gradlew :shizuku-server:connectedDebugAndroidTest --no-daemon \
    -Pandroid.testInstrumentationRunnerArguments.timeout_msec=90000
RESULT=$?
adb logcat -d -v brief \
    -s KProxyXShizukuTest:I KProxyXShizuku:V TestRunner:I AndroidRuntime:E ShizukuServiceStarter:E '*:S' \
    | sed -E 's/[[:xdigit:]]{8}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{12}(-[0-9]+)?/[redacted]/g'
exit "$RESULT"
