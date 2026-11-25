#!/bin/sh
set -eu
cd "$(dirname "$0")"
./build.sh
MMM_JAVA=java
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  MMM_JAVA="$JAVA_HOME/bin/java"
elif [ "$(uname -s)" = Darwin ] && [ -x /usr/libexec/java_home ]; then
  MMM_GUI_JDK=$(/usr/libexec/java_home -v 21+ 2>/dev/null || true)
  if [ -n "$MMM_GUI_JDK" ]; then MMM_JAVA="$MMM_GUI_JDK/bin/java"; fi
fi
if [ "$MMM_JAVA" = java ] && [ -d "$PWD/tools" ]; then
  MMM_BUNDLED_JAVA=$(find "$PWD/tools" -path '*/Contents/Home/bin/java' -print -quit 2>/dev/null || true)
  if [ -n "$MMM_BUNDLED_JAVA" ]; then MMM_JAVA="$MMM_BUNDLED_JAVA"; fi
fi
exec "$MMM_JAVA" -ea -cp build/classes:build/test-classes mmm.EmotionGuiTest
