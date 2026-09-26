#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
BUILD="$ROOT/build/host-classes"
DIST="$ROOT/dist"
rm -rf "$BUILD"
mkdir -p "$BUILD" "$DIST"
javac -source 7 -target 7 -Xlint:-options -Xlint:-unchecked \
  -cp "/home/ubuntu/j2me-tools/microemu-cldc.jar:/home/ubuntu/j2me-tools/microemu-midp.jar" \
  -d "$BUILD" $(find "$ROOT/src" -name '*.java' | sort)
rm -f "$DIST/ebuddy.jar"
jar cfm "$DIST/ebuddy.jar" "$ROOT/MANIFEST.MF" -C "$BUILD" . -C "$ROOT/res" .
SIZE=$(wc -c < "$DIST/ebuddy.jar" | tr -d ' ')
sed "s/^MIDlet-Jar-Size:.*/MIDlet-Jar-Size: $SIZE/" "$ROOT/ebuddy.jad" > "$DIST/ebuddy.jad"
printf 'JAVA_ME_BUILD_OK jar=%s bytes classes=%s\n' "$SIZE" "$(find "$BUILD" -name '*.class' | wc -l)"
