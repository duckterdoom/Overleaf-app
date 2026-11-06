@echo off
setlocal
cd /d "%~dp0.."
set "PYTHONUTF8=1"
apps\desktop\.venv\Scripts\python.exe -X utf8 -m overleaf_desktop.console %*
