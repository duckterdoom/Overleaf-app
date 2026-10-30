@echo off
chcp 65001 > nul
echo ==================================================
echo   DANG BIEN DICH OVERLEAF DESKTOP (PURE JAVA)
echo ==================================================
if not exist "bin" mkdir bin
javac -encoding UTF-8 -d bin src\com\overleaf\collab\*.java src\com\overleaf\database\*.java src\com\overleaf\compiler\*.java src\com\overleaf\server\*.java src\com\overleaf\client\gui\*.java src\com\overleaf\client\*.java
if %errorlevel% equ 0 (
    echo [THANH CONG] Toan bo 5 modules da duoc bien dich vao thu muc bin/
) else (
    echo [THAT BAI] Co loi xay ra trong qua trinh bien dich!
)
pause
