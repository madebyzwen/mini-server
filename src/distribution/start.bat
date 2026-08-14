@echo off
setlocal

title Mini Server

pushd "%~dp0" >nul 2>&1
if errorlevel 1 (
    echo Mini Server could not access its installation directory.
    pause
    exit /b 1
)

where java >nul 2>&1
if errorlevel 1 (
    echo Mini Server requires a Java 8 compatible runtime available on PATH.
    popd
    pause
    exit /b 1
)

java -cp "mini-server.jar;lib\*" io.github.madebyzwen.miniserver.MiniServer
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo Mini Server exited with code %EXIT_CODE%.
    pause
)

popd
exit /b %EXIT_CODE%
