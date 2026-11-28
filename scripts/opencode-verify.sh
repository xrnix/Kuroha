#!/usr/bin/env bash
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-/opt/android-studio/jbr}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

TASK=":app:compileDebugKotlin"
if [[ "${1:-}" == "--build" ]]; then
  TASK=":app:assembleDebug"
fi

TMP_LOG="$(mktemp)"
trap 'rm -f "$TMP_LOG"' EXIT

# Throttle CPU/IO priority and cap worker/GC threads to prevent laptop overheating.
set +e
nice -n 15 ionice -c 3 ./gradlew "$TASK" \
  --max-workers=1 \
  --no-parallel \
  -Dorg.gradle.workers.max=1 \
  -Dkotlin.daemon.jvm.options="-Xmx2048m,-XX:ActiveProcessorCount=2" \
  --console=plain >"$TMP_LOG" 2>&1
EXIT_CODE=$?
set -e

if [[ $EXIT_CODE -eq 0 ]]; then
  echo "OK: $TASK succeeded with 0 errors."
  exit 0
fi

echo "FAILED: $TASK (exit $EXIT_CODE). First 25 error lines:"
grep -E "^(e:|ERROR:|FAILURE:|> Task .* FAILED)" "$TMP_LOG" | head -n 25 || tail -n 25 "$TMP_LOG"
exit $EXIT_CODE
