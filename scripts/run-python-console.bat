@echo off
setlocal
cd /d "%~dp0.."
set "PYTHONUTF8=1"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0ensure-python.ps1"
if errorlevel 1 exit /b 1
set "CONSOLE_PYTHON=%CD%\apps\backend\.venv\Scripts\python.exe"
"%CONSOLE_PYTHON%" -c "import latex_backend.entrypoints.console" > nul 2>&1
if not errorlevel 1 goto launch
"%CONSOLE_PYTHON%" -m pip install -e apps/backend
if errorlevel 1 exit /b 1
:launch
"%CONSOLE_PYTHON%" -X utf8 -m latex_backend.entrypoints.console %*
