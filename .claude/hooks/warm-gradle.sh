#!/bin/bash
# SessionStart hook: in Claude Code cloud sessions, start downloading Gradle dependencies (and the
# IntelliJ Platform) in the background so they are ready when the agent first builds.
# It never blocks or fails session start: it returns at once and always exits 0.

[ "${CLAUDE_CODE_REMOTE:-}" = "true" ] || exit 0

cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}" 2>/dev/null || exit 0
[ -x ./gradlew ] || exit 0

nohup ./gradlew compileKotlin --console=plain > /tmp/gradle-warmup.log 2>&1 < /dev/null &
exit 0
