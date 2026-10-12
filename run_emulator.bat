@echo off
setlocal
title Vision+ Movil (Pixel)

echo ======================================================================
echo   Iniciando Emulador Movil (Pixel Pantalla Limpia)
echo ======================================================================

set "EMU=%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe"
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "APK=%~dp0VisionPlus.apk"

if not exist "%EMU%" (
    echo Error: No se encontro el emulador en %EMU%
    pause
    exit /b 1
)

echo [1/3] Lanzando emulador en modo ventana sin marco...
start "" "%EMU%" -avd Vision_Phone -no-skin

echo [2/3] Esperando conexion ADB...
"%ADB%" wait-for-device

echo [3/3] Esperando arranque completo de Android...
:wait_boot
for /f "tokens=*" %%a in ('"%ADB%" shell getprop sys.boot_completed 2^>nul') do set "BOOT=%%a"
if not "%BOOT:~0,1%"=="1" (
    ping -n 3 127.0.0.1 >nul
    goto wait_boot
)

echo Instalando Vision+ v2.5.47...
"%ADB%" install -r "%APK%"

echo Abriendo Vision+...
"%ADB%" shell monkey -p com.chronosbvrx.visionplus -c android.intent.category.LAUNCHER 1

echo.
echo ======================================================================
echo   Listo. Usa el mouse para interactuar en modo tactil.
echo ======================================================================
pause