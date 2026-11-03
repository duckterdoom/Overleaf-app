#!/bin/sh
set -eu
cd "$(dirname "$0")"
exec sh scripts/run-python-desktop.sh
