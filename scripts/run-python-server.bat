@echo off
setlocal
cd /d "%~dp0.."
set "PYTHONUTF8=1"
if not exist apps\desktop\.venv\Scripts\python.exe (
  echo Create Python 3.12+ venv: python -m venv apps\desktop\.venv
  echo Then: apps\desktop\.venv\Scripts\python.exe -m pip install -e apps/desktop
  exit /b 1
)
apps\desktop\.venv\Scripts\python.exe -X utf8 -m overleaf_desktop.network %*
