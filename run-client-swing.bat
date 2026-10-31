@echo off
chcp 65001 > nul
echo ==================================================
echo   KHOI CHAY OVERLEAF DESKTOP (JAVA SWING BACKUP)
echo ==================================================
if not exist "bin" mkdir bin
javac -encoding UTF-8 -d bin overleaf-common\src\main\java\com\overleaf\common\sync\*.java
javac -encoding UTF-8 -cp "bin" -d bin overleaf-client\src\main\java\com\overleaf\client\gui\OverleafDesktopApp.java overleaf-client\src\main\java\com\overleaf\client\ClientApp.java
if %errorlevel% equ 0 (
    start "Overleaf Desktop Client (Swing)" java -cp bin com.overleaf.client.ClientApp --swing
) else (
    echo [LOI] Khong the bien dich ma nguon GUI Swing!
    pause
)
