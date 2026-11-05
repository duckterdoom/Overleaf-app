#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
export PYTHONUTF8=1 NEXT_TELEMETRY_DISABLED=1
if [ ! -x apps/desktop/.venv/bin/python ]; then
    python3 -c 'import sys; assert sys.version_info >= (3, 12), "Python 3.12+ required"'
    python3 -m venv apps/desktop/.venv
fi
desktop_python="$PWD/apps/desktop/.venv/bin/python"
"$desktop_python" -c 'import sys; assert sys.version_info >= (3, 12), "Python 3.12+ required"'
if ! "$desktop_python" -c 'import fastapi, uvicorn, webview, PIL, pypdf, pdf2docx, pymupdf, docx, overleaf_desktop' >/dev/null 2>&1; then
    "$desktop_python" -m pip install -e 'apps/desktop[desktop]'
fi
if [ ! -f apps/frontend/out/index.html ] || [ "${1:-}" = --build ]; then
    (cd apps/frontend && npm ci && npm run build)
fi
if [ "${1:-}" = --build ]; then exit 0; fi
if [ "${1:-}" = --smoke ]; then
    exec "$desktop_python" -X utf8 -m overleaf_desktop.main --smoke
fi
exec "$desktop_python" -X utf8 -m overleaf_desktop.main
