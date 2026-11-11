@echo off
setlocal
cd /d "%~dp0.."
set "PYTHONUTF8=1"
set "SHARED_PYTHON=%CD%\target\official-python-web-env\Scripts\python.exe"
if not exist "%SHARED_PYTHON%" set "SHARED_PYTHON=%CD%\apps\backend\.venv\Scripts\python.exe"
"%SHARED_PYTHON%" -X utf8 -m latex_backend.entrypoints.publish_shared %*
exit /b %errorlevel%
