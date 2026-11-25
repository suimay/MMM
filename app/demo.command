#!/bin/sh
set -eu
cd "$(dirname "$0")"
exec ./run.command --demo-date=2026-11-01 "$@"
