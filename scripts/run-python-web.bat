@echo off
setlocal
cd /d "%~dp0.."
chcp 65001 > nul
set "PYTHONUTF8=1"
set "NEXT_TELEMETRY_DISABLED=1"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0ensure-python.ps1"
if errorlevel 1 goto failed
set "WEB_PYTHON=%CD%\apps\desktop\.venv\Scripts\python.exe"
set "UV_CACHE_DIR=%CD%\target\uv-cache"
set "WEB_UV=%CD%\target\uv-python-migration\uv.exe"
if exist "%WEB_UV%" goto sync_uv
where uv > nul 2>&1
if errorlevel 1 goto sync_pip
set "WEB_UV=uv"
:sync_uv
"%WEB_UV%" sync --project apps/desktop --frozen --extra web --inexact
if errorlevel 1 goto failed
goto dependencies_ready
:sync_pip
"%WEB_PYTHON%" -c "import fastapi, uvicorn, httptools, websockets, pymongo, PIL, pypdf, pdf2docx, pymupdf, docx, overleaf_desktop.web; from importlib.metadata import version; assert tuple(map(int,version('fastapi').split('.')[:2])) >= (0,162); assert tuple(map(int,version('uvicorn').split('.')[:2])) >= (0,54)" > nul 2>&1
if not errorlevel 1 goto dependencies_ready
"%WEB_PYTHON%" -m pip install -e "apps/desktop[web]"
if errorlevel 1 goto failed
:dependencies_ready
if /i "%~1"=="--build" goto build_ui
if exist "apps\frontend\out\index.html" goto launch
:build_ui
where npm.cmd > nul 2>&1
if errorlevel 1 (
    echo Install Node.js 22.13+ to build the web UI.
    goto failed
)
pushd apps\frontend
call npm.cmd ci
if errorlevel 1 goto frontend_failed
call npm.cmd run build
if errorlevel 1 goto frontend_failed
popd
if /i "%~1"=="--build" exit /b 0
:launch
"%WEB_PYTHON%" -X utf8 -m overleaf_desktop.web %*
if errorlevel 1 goto failed
exit /b 0
:frontend_failed
popd
:failed
echo Local web could not start. Read the error above.
exit /b 1
