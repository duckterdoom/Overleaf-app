@echo off
setlocal
cd /d "%~dp0.."
chcp 65001 > nul
rem Build all modules (no tests), then launch the JavaFX client.
call mvn -q -DskipTests install
if errorlevel 1 exit /b 1
call mvn -q -pl client javafx:run
