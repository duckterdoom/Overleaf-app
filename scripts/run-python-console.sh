#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
exec apps/backend/.venv/bin/python -m latex_backend.entrypoints.console "$@"
