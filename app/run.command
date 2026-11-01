#!/bin/sh
set -eu
cd "$(dirname "$0")"
MMM_JAVA="${JAVA_HOME:-}/bin/java"
if [ ! -x "$MMM_JAVA" ]; then
  MMM_JAVA=$(find "$PWD/tools" -path '*/Contents/Home/bin/java' -print -quit 2>/dev/null || true)
fi
if [ -z "$MMM_JAVA" ]; then MMM_JAVA=java; fi
if [ ! -f build/MMM.jar ]; then ./build.sh; fi
exec "$MMM_JAVA" -jar build/MMM.jar "$@"
