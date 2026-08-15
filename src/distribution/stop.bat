@echo off
setlocal

title Stop Mini Server

where java >nul 2>&1
if errorlevel 1 (
    echo Mini Server requires a Java 8 compatible runtime available on PATH.
    pause
    exit /b 1
)

java -cp "%~dp0mini-server.jar;%~dp0lib\*" io.github.madebyzwen.miniserver.MiniServer stop
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo Mini Server stop command exited with code %EXIT_CODE%.
    pause
)

exit /b %EXIT_CODE%
