@echo off
setlocal
cd /d "%~dp0.."
set "PYTHONUTF8=1"
set "PYTHONPATH=%CD%\apps\backend\src;%PYTHONPATH%"
if defined LATEX_PYTHON goto explicit_python
if exist "target\official-python-web-env\Scripts\python.exe" goto installed_python
if exist "apps\backend\.venv\Scripts\python.exe" goto venv_python
echo First run scripts\run-python-web.bat --build to prepare Python and TeX automatically.
exit /b 1
:explicit_python
set "TEX_PYTHON=%LATEX_PYTHON%"
goto launch
:installed_python
set "TEX_PYTHON=%CD%\target\official-python-web-env\Scripts\python.exe"
goto launch
:venv_python
set "TEX_PYTHON=%CD%\apps\backend\.venv\Scripts\python.exe"
:launch
"%TEX_PYTHON%" -X utf8 -m latex_backend.compilation.tex_runtime --ensure %*
exit /b %errorlevel%
