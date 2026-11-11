@echo off
setlocal
cd /d "%~dp0.."
set "PYTHONUTF8=1"
if not exist apps\backend\.venv\Scripts\python.exe (
  echo Create Python 3.12+ venv: python -m venv apps\backend\.venv
  echo Then: apps\backend\.venv\Scripts\python.exe -m pip install -e apps/backend
  exit /b 1
)
apps\backend\.venv\Scripts\python.exe -X utf8 -m latex_backend.collaboration.network %*
