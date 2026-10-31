@echo off
chcp 65001 > nul
echo ====================================================================
echo   KHOI CHAY OVERLEAF DESKTOP CLIENT (JAVAFX EDITION - M1)
echo ====================================================================
echo.

if not exist "bin" mkdir bin
if exist "overleaf-client\src\main\resources" (
    xcopy /y /e /i "overleaf-client\src\main\resources" "bin" > nul 2>&1
)

set "FX_DIR=%USERPROFILE%\.m2\repository\org\openjfx"
set "FX_CP=%FX_DIR%\javafx-base\17.0.8\javafx-base-17.0.8.jar;%FX_DIR%\javafx-base\17.0.8\javafx-base-17.0.8-win.jar;%FX_DIR%\javafx-graphics\17.0.8\javafx-graphics-17.0.8.jar;%FX_DIR%\javafx-graphics\17.0.8\javafx-graphics-17.0.8-win.jar;%FX_DIR%\javafx-controls\17.0.8\javafx-controls-17.0.8.jar;%FX_DIR%\javafx-controls\17.0.8\javafx-controls-17.0.8-win.jar;%FX_DIR%\javafx-fxml\17.0.8\javafx-fxml-17.0.8.jar;%FX_DIR%\javafx-fxml\17.0.8\javafx-fxml-17.0.8-win.jar"

echo [1/2] Dang bien dich overleaf-common va overleaf-client...
javac -encoding UTF-8 -d bin overleaf-common\src\main\java\com\overleaf\common\sync\*.java
javac -encoding UTF-8 -cp "bin;%FX_CP%" -d bin overleaf-client\src\main\java\com\overleaf\client\compiler\*.java overleaf-client\src\main\java\com\overleaf\client\io\*.java overleaf-client\src\main\java\com\overleaf\client\gui\*.java overleaf-client\src\main\java\com\overleaf\client\*.java

if %errorlevel% equ 0 (
    echo [2/2] Khoi chay giao dien Overleaf JavaFX Client...
    start "Overleaf Desktop Client" java -cp "bin;%FX_CP%" com.overleaf.client.ClientApp
) else (
    echo.
    echo [LOI] Khong the bien dich ma nguon JavaFX!
    echo Neu gap loi thu vien, ban co the chay ban Swing cu qua run-client-swing.bat
    pause
)
