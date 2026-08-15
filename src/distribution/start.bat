@echo off
setlocal

title Mini Server

where javaw >nul 2>&1
if errorlevel 1 (
    echo Mini Server requires a Java 8 compatible javaw runtime available on PATH.
    pause
    exit /b 1
)

start "" javaw -cp "%~dp0mini-server.jar;%~dp0lib\*" io.github.madebyzwen.miniserver.MiniServer
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo Mini Server could not be launched. Exit code %EXIT_CODE%.
    pause
)

exit /b %EXIT_CODE%
