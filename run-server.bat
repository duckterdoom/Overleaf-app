@echo off
setlocal
cd /d "%~dp0"
chcp 65001 > nul
echo ==================================================
echo   KHOI CHAY OVERLEAF DESKTOP SERVER (TCP SOCKET)
echo ==================================================
if not exist "bin\com\overleaf\server\ServerApp.class" (
    echo Dang bien dich du an truoc khi chay...
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-network.ps1"
    if errorlevel 1 exit /b 1
)
set "JAVA_CMD=java"
if defined JAVA_HOME set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
set "SERVER_PORT=8080"
if not "%~1"=="" set "SERVER_PORT=%~1"
"%JAVA_CMD%" -cp "%~dp0bin" com.overleaf.server.ServerApp %SERVER_PORT%
pause
