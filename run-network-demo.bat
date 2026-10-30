@echo off
setlocal
cd /d "%~dp0"
if not exist "bin\com\overleaf\client\NetworkDemo.class" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-network.ps1"
    if errorlevel 1 exit /b 1
)
set "JAVA_CMD=java"
if defined JAVA_HOME set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
if "%~1"=="" (
    echo Usage: run-network-demo.bat host port username room
    exit /b 1
)
"%JAVA_CMD%" -cp "%~dp0bin" com.overleaf.client.NetworkDemo %*
