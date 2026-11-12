#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
export PYTHONUTF8=1 NEXT_TELEMETRY_DISABLED=1
if [ ! -x apps/backend/.venv/bin/python ]; then
    python3 -c 'import sys; assert sys.version_info >= (3,12), "Python 3.12+ required"'
    python3 -m venv apps/backend/.venv
fi
web_python="$PWD/apps/backend/.venv/bin/python"
"$web_python" -c 'import sys; assert sys.version_info >= (3,12), "Python 3.12+ required"'
if command -v uv >/dev/null 2>&1; then
    uv sync --project apps/backend --frozen --extra web --inexact
elif ! "$web_python" -c 'import google.auth, httpx, requests, fastapi, uvicorn, websockets, httptools, pymongo, PIL, pypdf, pdf2docx, pymupdf, docx, latex_backend.entrypoints.web; from importlib.metadata import version; assert tuple(map(int,version("fastapi").split(".")[:2])) >= (0,162); assert tuple(map(int,version("uvicorn").split(".")[:2])) >= (0,54)' >/dev/null 2>&1; then
    "$web_python" -m pip install -e 'apps/backend[web]'
fi
"$web_python" -X utf8 -m latex_backend.compilation.tex_runtime --ensure
if [ ! -f apps/frontend/out/index.html ] || [ "${1:-}" = --build ]; then
    (cd apps/frontend && npm ci && npm run build)
fi
if [ "${1:-}" = --build ]; then exit 0; fi
exec "$web_python" -X utf8 -m latex_backend.entrypoints.web "$@"
