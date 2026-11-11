@echo off
setlocal
cd /d "%~dp0.."
set "PYTHONUTF8=1"
if exist "target\official-python-web-env\Scripts\python.exe" (
    set "GOOGLE_SETUP_PYTHON=%CD%\target\official-python-web-env\Scripts\python.exe"
    goto launch
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0ensure-python.ps1"
if errorlevel 1 exit /b 1
set "GOOGLE_SETUP_PYTHON=%CD%\apps\backend\.venv\Scripts\python.exe"
:launch
set "PYTHONPATH=%CD%\apps\backend\src;%PYTHONPATH%"
"%GOOGLE_SETUP_PYTHON%" -X utf8 -m latex_backend.auth.google_config %*
exit /b %errorlevel%
