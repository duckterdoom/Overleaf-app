@echo off
setlocal
cd /d "%~dp0"
if not exist "dist\overleaf-network-demo.jar" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-network.ps1"
    if errorlevel 1 exit /b 1
)
set "JAVA_CMD=java"
if defined JAVA_HOME set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
"%JAVA_CMD%" -jar "%~dp0dist\overleaf-network-demo.jar" %*
