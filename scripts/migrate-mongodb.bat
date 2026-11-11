@echo off
setlocal
cd /d "%~dp0.."
set "PYTHONUTF8=1"
set "MIGRATION_PYTHON=%CD%\target\official-python-web-env\Scripts\python.exe"
if not exist "%MIGRATION_PYTHON%" set "MIGRATION_PYTHON=%CD%\apps\backend\.venv\Scripts\python.exe"
"%MIGRATION_PYTHON%" -X utf8 -m latex_backend.entrypoints.migrate_mongodb %*
exit /b %errorlevel%
