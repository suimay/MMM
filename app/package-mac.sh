#!/bin/sh
set -eu
cd "$(dirname "$0")"
./build.sh
MMM_JDK="${JAVA_HOME:-}"
if [ ! -x "$MMM_JDK/bin/jpackage" ]; then MMM_JDK=$(find "$PWD/tools" -path '*/Contents/Home/bin/jpackage' -print -quit 2>/dev/null | sed 's|/bin/jpackage$||'); fi
if [ -z "$MMM_JDK" ]; then echo 'jpackage를 포함한 Java 21 JDK가 필요합니다.' >&2; exit 1; fi
mkdir -p dist
MMM_STAGE=$(mktemp -d "${TMPDIR:-/private/tmp}/mmm-package.XXXXXX")
mkdir "$MMM_STAGE/input" "$MMM_STAGE/output"
cp build/MMM.jar "$MMM_STAGE/input/MMM.jar"
if ! "$MMM_JDK/bin/jpackage" --type app-image --name MMM --input "$MMM_STAGE/input" --main-jar MMM.jar --main-class mmm.MMM --dest "$MMM_STAGE/output" --app-version 2.3.1 --vendor MMM --mac-package-identifier org.mmm.desktop --icon resources/MMM.icns --arguments --demo-date=2026-11-01 --java-options -Dapple.laf.useScreenMenuBar=true; then
  if [ ! -x "$MMM_STAGE/output/MMM.app/Contents/MacOS/MMM" ]; then exit 1; fi
  # Finder may attach presentation metadata while jpackage signs the new bundle.
  if xattr -p com.apple.FinderInfo "$MMM_STAGE/output/MMM.app" >/dev/null 2>&1; then
    xattr -d com.apple.FinderInfo "$MMM_STAGE/output/MMM.app"
  fi
  codesign --force --sign - "$MMM_STAGE/output/MMM.app"
fi
codesign --verify --deep --strict "$MMM_STAGE/output/MMM.app"
ditto -c -k --norsrc --keepParent "$MMM_STAGE/output/MMM.app" dist/MMM-2.3.1-macos-arm64.zip
if [ -d dist/MMM.app ]; then mv dist/MMM.app "dist/MMM.previous.$(date +%Y%m%d-%H%M%S).app"; fi
mv "$MMM_STAGE/output/MMM.app" dist/MMM.app
printf 'Built %s/dist/MMM.app (시연 모드, 런타임 포함)\n' "$PWD"
