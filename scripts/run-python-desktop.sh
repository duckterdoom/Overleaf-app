#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
export PYTHONUTF8=1 NEXT_TELEMETRY_DISABLED=1
if [ ! -x apps/backend/.venv/bin/python ]; then
    python3 -c 'import sys; assert sys.version_info >= (3, 12), "Python 3.12+ required"'
    python3 -m venv apps/backend/.venv
fi
desktop_python="$PWD/apps/backend/.venv/bin/python"
"$desktop_python" -c 'import sys; assert sys.version_info >= (3, 12), "Python 3.12+ required"'
if ! "$desktop_python" -c 'import fastapi, uvicorn, webview, PIL, pypdf, pdf2docx, pymupdf, docx, latex_backend' >/dev/null 2>&1; then
    "$desktop_python" -m pip install -e 'apps/backend[desktop]'
fi
if [ ! -f apps/frontend/out/index.html ] || [ "${1:-}" = --build ]; then
    (cd apps/frontend && npm ci && npm run build)
fi
if [ "${1:-}" = --build ]; then exit 0; fi
if [ "${1:-}" = --smoke ]; then
    exec "$desktop_python" -X utf8 -m latex_backend.entrypoints.desktop --smoke
fi
exec "$desktop_python" -X utf8 -m latex_backend.entrypoints.desktop
