@echo off
chcp 65001 > nul
echo ====================================================================
echo   KHOI CHAY OVERLEAF DESKTOP CLIENT (JAVAFX EDITION - M1)
echo ====================================================================
echo.
echo Dang kiem tra moi truong va bien dich qua Maven JavaFX Plugin...
echo.

call mvn compile javafx:run -pl overleaf-client

if %errorlevel% neq 0 (
    echo.
    echo ====================================================================
    echo [THONG BAO] Neu ban chua cai dat Maven, hoac muon chay ban Swing cu:
    echo   Chay: run-client-swing.bat
    echo ====================================================================
    pause
)
