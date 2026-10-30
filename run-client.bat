@echo off
chcp 65001 > nul
echo ==================================================
echo   KHOI CHAY OVERLEAF DESKTOP CLIENT (GUI SWING)
echo ==================================================
if not exist "bin" mkdir bin
javac -encoding UTF-8 -d bin overleaf-client\src\main\java\com\overleaf\client\gui\*.java overleaf-client\src\main\java\com\overleaf\client\*.java
if %errorlevel% equ 0 (
    start "Overleaf Desktop Client" java -cp bin com.overleaf.client.ClientApp
) else (
    echo [LOI] Khong the bien dich ma nguon GUI!
    pause
)
