@echo off
chcp 65001 > nul
echo ==================================================
echo   KHOI CHAY OVERLEAF DESKTOP SERVER (TCP SOCKET)
echo ==================================================
if not exist "bin\com\overleaf\server\ServerApp.class" (
    echo Dang bien dich du an truoc khi chay...
    call build.bat
)
java -cp bin com.overleaf.server.ServerApp 8080
pause
