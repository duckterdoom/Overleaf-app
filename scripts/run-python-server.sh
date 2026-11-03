#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
exec apps/desktop/.venv/bin/python -m overleaf_desktop.network "$@"
