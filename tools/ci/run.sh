#!/usr/bin/env bash
# Usage: tools/ci/run.sh "<step name>" <command...>
#
# Runs a CI command, and on failure emits a GitHub error annotation with the
# relevant log lines. Annotations are readable through the public Checks API
# (no login needed), so a failed build can be diagnosed without the raw log.
set -o pipefail
name="$1"; shift
log="$(mktemp)"
"$@" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
if [ "$status" -ne 0 ]; then
  {
    echo "--- matching lines ---"
    grep -nE "error|Error|FAILED|Exception|^e: |What went wrong|Because|version solving" "$log" | head -n 60 || true
    echo "--- last 80 lines ---"
    tail -n 80 "$log"
  } > "$log.summary"
  msg=$(sed -e 's/%/%25/g' -e 's/\r/%0D/g' "$log.summary" | awk 'BEGIN{ORS="%0A"} {print}')
  echo "::error title=${name} failed::${msg}"
fi
# Surface analyzer hints (infos/warnings) as a warning annotation too.
if [ "$status" -eq 0 ] && [ "$name" = "analyze" ] && grep -qE " (info|warning) [-•] " "$log"; then
  msg=$(grep -E " (info|warning) [-•] " "$log" | head -n 50 | sed -e 's/%/%25/g' | awk 'BEGIN{ORS="%0A"} {print}')
  echo "::warning title=analyzer hints::${msg}"
fi
exit "$status"
