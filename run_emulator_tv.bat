@echo off
setlocal
title Vision+ Smart TV (Chromecast)

echo ======================================================================
echo   Iniciando Emulador Smart TV / Chromecast (Recursos Limitados 1.5GB)
echo ======================================================================

set "EMU=%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe"
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "APK=%~dp0VisionPlus.apk"

if not exist "%EMU%" (
    echo Error: No se encontro el emulador en %EMU%
    pause
    exit /b 1
)

echo [1/3] Lanzando Smart TV en pantalla limpia 1080p sin marco...
start "" "%EMU%" -avd Vision_Chromecast_TV -no-skin

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

echo Abriendo Vision+ en modo Smart TV...
"%ADB%" shell monkey -p com.chronosbvrx.visionplus -c android.intent.category.LAUNCHER 1

echo.
echo ======================================================================
echo   Listo. Usa las flechas del teclado, Enter (OK) y Esc (Atras).
echo ======================================================================
pause