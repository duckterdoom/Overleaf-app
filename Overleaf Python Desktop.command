#!/bin/sh
set -eu
cd "$(dirname "$0")"
exec sh apps/backend/tools/run-python-desktop.sh
