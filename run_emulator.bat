@echo off
chcp 65001 >nul
echo ========================================================
echo    Iniciando Emulador Frameless (Pantalla Limpia Vision+)
echo ========================================================
echo [1/3] Lanzando emulador en modo ventana sin marco...
start "" "%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" -avd Vision_Phone -no-skin

echo [2/3] Esperando que el sistema inicie...
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" wait-for-device
echo Emulador detectado. Esperando arranque completo...
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" wait-for-device shell "while [[ -z `getprop sys.boot_completed` ]]; do sleep 1; done"

echo [3/3] Instalando Vision+ v2.5.47...
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" install -r "%~dp0VisionPlus.apk"

echo.
echo ========================================================
echo    Abriendo Vision+...
echo ========================================================
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" shell monkey -p com.chronosbvrx.visionplus -c android.intent.category.LAUNCHER 1
echo.
echo Todo listo. ¡Disfruta Vision+ en pantalla emulada limpia!
pause
