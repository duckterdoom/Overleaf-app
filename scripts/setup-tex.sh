#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
export PYTHONUTF8=1
export PYTHONPATH="$PWD/apps/backend/src${PYTHONPATH:+:$PYTHONPATH}"
tex_python="${LATEX_PYTHON:-$PWD/apps/backend/.venv/bin/python}"
if [ ! -x "$tex_python" ]; then
    echo 'First run sh scripts/run-python-web.sh --build to prepare Python and TeX automatically.'
    exit 1
fi
exec "$tex_python" -X utf8 -m latex_backend.compilation.tex_runtime --ensure "$@"
