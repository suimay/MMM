#!/bin/sh
set -eu
cd "$(dirname "$0")"
./build.sh
MMM_JAVA="${JAVA_HOME:-}/bin/java"
if [ ! -x "$MMM_JAVA" ]; then MMM_JAVA=$(find "$PWD/tools" -path '*/Contents/Home/bin/java' -print -quit 2>/dev/null || true); fi
"$MMM_JAVA" -cp build/classes:build/test-classes mmm.GuiFlowTest
"$MMM_JAVA" -cp build/classes:build/test-classes mmm.ExpressionGuiTest
"$MMM_JAVA" -cp build/classes:build/test-classes mmm.PreviewGuiTest
"$MMM_JAVA" -cp build/classes:build/test-classes mmm.InstrumentGuiTest
exec "$MMM_JAVA" -cp build/classes:build/test-classes mmm.LiveSoundGuiTest
