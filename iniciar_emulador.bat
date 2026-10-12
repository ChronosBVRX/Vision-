@echo off
setlocal
cls
echo ======================================================================
echo                VISION+ - SELECCION DE EMULADOR
echo ======================================================================
echo.
echo   [1] Smart TV / Chromecast (1080p, 1.5 GB RAM, D-Pad / Teclado)
echo   [2] Movil / Telefono (Pixel Vertical, Pantalla Limpia)
echo   [3] Salir
echo.
echo ======================================================================
set /p opt="Selecciona una opcion (1 o 2): "

if "%opt%"=="1" (
    call "%~dp0run_emulator_tv.bat"
    exit /b
)
if "%opt%"=="2" (
    call "%~dp0run_emulator.bat"
    exit /b
)
exit /b