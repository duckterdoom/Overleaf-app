@echo off
setlocal
cd /d "%~dp0.."
chcp 65001 > nul
set "PYTHONUTF8=1"
set "NEXT_TELEMETRY_DISABLED=1"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0ensure-python.ps1"
if errorlevel 1 goto failed
set "DESKTOP_PYTHON=%CD%\apps\backend\.venv\Scripts\python.exe"
"%DESKTOP_PYTHON%" -c "import sys; sys.exit(0 if sys.version_info >= (3,12) else 1)"
if errorlevel 1 (
    echo Python 3.12+ is required. See docs\PYTHON_NEXTJS_MIGRATION.md.
    goto failed
)
"%DESKTOP_PYTHON%" -c "import fastapi, uvicorn, webview, PIL, pypdf, pdf2docx, pymupdf, docx, latex_backend" > nul 2>&1
if not errorlevel 1 goto dependencies_ready
"%DESKTOP_PYTHON%" -m pip install -e "apps/backend[desktop]"
if errorlevel 1 goto failed
:dependencies_ready
if /i "%~1"=="--build" goto build_ui
if exist "apps\frontend\out\index.html" goto launch
:build_ui
where npm.cmd > nul 2>&1
if errorlevel 1 (
    echo Install Node.js 22+ to build the UI. See docs\PYTHON_NEXTJS_MIGRATION.md.
    goto failed
)
pushd apps\frontend
call npm.cmd ci
if errorlevel 1 goto frontend_failed
call npm.cmd run build
if errorlevel 1 goto frontend_failed
popd
:launch
if /i "%~1"=="--build" exit /b 0
if /i "%~1"=="--smoke" (
    "%DESKTOP_PYTHON%" -X utf8 -m latex_backend.entrypoints.desktop --smoke
) else (
    "%DESKTOP_PYTHON%" -X utf8 -m latex_backend.entrypoints.desktop
)
if errorlevel 1 goto failed
exit /b 0
:frontend_failed
popd
:failed
echo Python desktop could not start. Read the error above.
exit /b 1
