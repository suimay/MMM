#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
  MMM_JDK="$JAVA_HOME"
else
  MMM_JDK=$(find "$PWD/tools" -path '*/Contents/Home/bin/javac' -print -quit 2>/dev/null | sed 's|/bin/javac$||')
fi
if [ -z "${MMM_JDK:-}" ]; then
  echo 'Java 21 이상 JDK가 필요합니다. JAVA_HOME을 설정하세요.' >&2
  exit 1
fi
mkdir -p build/classes build/test-classes
find src/main/java -name '*.java' > build/sources.txt
"$MMM_JDK/bin/javac" --release 21 -encoding UTF-8 -d build/classes @build/sources.txt
"$MMM_JDK/bin/jar" --create --file build/MMM.jar --main-class mmm.MMM -C build/classes .
find src/test/java -name '*.java' > build/test-sources.txt
if [ -s build/test-sources.txt ]; then
  "$MMM_JDK/bin/javac" --release 21 -encoding UTF-8 -cp build/classes -d build/test-classes @build/test-sources.txt
  "$MMM_JDK/bin/java" -ea -Djava.awt.headless=true -cp build/classes:build/test-classes mmm.ModelRepositoryTest
  "$MMM_JDK/bin/java" -ea -Djava.awt.headless=true -cp build/classes:build/test-classes mmm.FeatureTest
  "$MMM_JDK/bin/java" -ea -Djava.awt.headless=true -cp build/classes:build/test-classes mmm.SoundShapeTest
fi
printf 'Built %s/build/MMM.jar\n' "$PWD"
