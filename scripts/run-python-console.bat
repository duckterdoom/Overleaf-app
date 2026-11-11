@echo off
setlocal
cd /d "%~dp0.."
set "PYTHONUTF8=1"
apps\backend\.venv\Scripts\python.exe -X utf8 -m latex_backend.entrypoints.console %*
