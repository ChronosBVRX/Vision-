@echo off
chcp 65001 >nul
echo ======================================================================
echo   Iniciando Emulador Smart TV / Chromecast (Recursos Limitados 1.5GB)
echo ======================================================================
echo [1/3] Lanzando Smart TV en pantalla limpia 1080p sin marco (-no-skin)...
echo       Control: Usa las flechas del teclado, Enter (OK) y Esc (Atrás).
start "" "%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" -avd Vision_Chromecast_TV -no-skin

echo [2/3] Esperando que el sistema inicie...
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" wait-for-device
echo Emulador detectado. Esperando arranque completo del sistema...
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" wait-for-device shell "while [[ -z `getprop sys.boot_completed` ]]; do sleep 1; done"

echo [3/3] Instalando Vision+ v2.5.47...
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" install -r "%~dp0VisionPlus.apk"

echo.
echo ======================================================================
echo   Abriendo Vision+ en Modo Smart TV...
echo ======================================================================
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" shell monkey -p com.chronosbvrx.visionplus -c android.intent.category.LAUNCHER 1
echo.
echo ======================================================================
echo   ¡Listo! Maneja la app con el teclado como si fuera un control remoto:
echo   - Flechas: Navegar D-pad (Arriba, Abajo, Izquierda, Derecha)
echo   - Enter: Seleccionar / Reproducir (D-Pad Center)
echo   - Escape o Retroceso: Botón Atrás
echo ======================================================================
pause
