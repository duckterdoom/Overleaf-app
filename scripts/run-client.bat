@echo off
setlocal
cd /d "%~dp0.."
chcp 65001 > nul
rem Keep the launcher independent of PowerShell profiles and execution policy.
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" goto jdk_ready
set "JAVA_HOME="
for /d %%D in ("%USERPROFILE%\.jdks\*") do if exist "%%~fD\bin\javac.exe" set "JAVA_HOME=%%~fD"
if not defined JAVA_HOME for /d %%D in ("%ProgramFiles%\Java\*" "%ProgramFiles%\Microsoft\jdk*" "%ProgramFiles%\Eclipse Adoptium\*") do if exist "%%~fD\bin\javac.exe" set "JAVA_HOME=%%~fD"
if not defined JAVA_HOME goto missing_jdk
:jdk_ready
set "PATH=%JAVA_HOME%\bin;%PATH%"
if exist "%CD%\target\TinyTeX\bin\windows\latexmk.exe" set "PATH=%PATH%;%CD%\target\TinyTeX\bin\windows"
set "MVN_CMD="
for /f "delims=" %%M in ('where mvn.cmd 2^>nul') do if not defined MVN_CMD set "MVN_CMD=%%M"
if not defined MVN_CMD for /d %%I in ("%ProgramFiles%\JetBrains\IntelliJ IDEA*" "%LOCALAPPDATA%\Programs\IntelliJ IDEA*" "D:\IntelliJ IDEA*") do if exist "%%~fI\plugins\maven\lib\maven3\bin\mvn.cmd" set "MVN_CMD=%%~fI\plugins\maven\lib\maven3\bin\mvn.cmd"
if not defined MVN_CMD goto missing_maven
echo JDK: %JAVA_HOME%
echo Maven: %MVN_CMD%
call "%MVN_CMD%" -B "-Dmaven.repo.local=%CD%\target\maven-cache" -DskipTests install
if errorlevel 1 goto failed
if /i "%~1"=="-BuildOnly" exit /b 0
call "%MVN_CMD%" -B "-Dmaven.repo.local=%CD%\target\maven-cache" -pl client javafx:run
if errorlevel 1 goto failed
exit /b 0
:missing_jdk
echo A full JDK 21+ is required. Set JAVA_HOME to its installation directory.
goto failed
:missing_maven
echo Maven was not found. Install Maven or IntelliJ IDEA with bundled Maven.
:failed
echo Client failed to start. Read the error above.
if /i not "%~1"=="-BuildOnly" pause
exit /b 1
