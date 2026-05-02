#!/usr/bin/env bash
# Build the WAR, unpack it into dist/, and serve on http://localhost:$1 (default 8080).
set -euo pipefail

PORT="${1:-8080}"
ROOT="$(cd "$(dirname "$0")" && pwd)"
WAR="$ROOT/build/libs/concurrency-cafe-1.0-SNAPSHOT.war"
DIST="$ROOT/dist"

cd "$ROOT"
./gradlew build

rm -rf "$DIST"
mkdir -p "$DIST"
( cd "$DIST" && jar -xf "$WAR" )

echo
echo "Serving http://localhost:$PORT (Ctrl-C to stop)"
exec python3 -m http.server --directory "$DIST" "$PORT"
