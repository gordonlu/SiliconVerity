#!/usr/bin/env bash
set -uo pipefail

# Runs one Android instrumentation target with a host-side deadline and always
# releases the phone afterwards. Example:
#   scripts/run-device-test.sh \
#     com.siliconverity.BenchmarkCorrectnessTest#gpuBufferCanRunTwiceInSameProcess 30

readonly APP_PACKAGE="com.siliconverity"
readonly TEST_PACKAGE="com.siliconverity.test"
readonly RUNNER="${TEST_PACKAGE}/androidx.test.runner.AndroidJUnitRunner"
readonly TEST_TARGET="${1:-com.siliconverity.BenchmarkCorrectnessTest}"
readonly TIMEOUT_SECONDS="${2:-45}"

cleanup() {
  adb shell am force-stop "${TEST_PACKAGE}" >/dev/null 2>&1 || true
  adb shell am force-stop "${APP_PACKAGE}" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

if ! [[ "${TIMEOUT_SECONDS}" =~ ^[1-9][0-9]*$ ]]; then
  echo "timeout must be a positive integer (seconds)" >&2
  exit 2
fi

cleanup
timeout --foreground "${TIMEOUT_SECONDS}s" \
  adb shell am instrument -w -r \
  -e class "${TEST_TARGET}" \
  "${RUNNER}"
status=$?

if [[ ${status} -eq 124 ]]; then
  echo "device test exceeded ${TIMEOUT_SECONDS}s; test processes were stopped" >&2
fi
exit "${status}"
