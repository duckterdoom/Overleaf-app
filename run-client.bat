@echo off
chcp 65001 > nul
echo ==================================================
echo   KHOI CHAY OVERLEAF DESKTOP CLIENT (GUI SWING)
echo ==================================================
if not exist "bin\com\overleaf\client\ClientApp.class" (
    echo Dang bien dich du an truoc khi chay...
    call build.bat
)
start "Overleaf Desktop Client" java -cp bin com.overleaf.client.ClientApp
