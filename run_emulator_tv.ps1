# Lanzador de Emulador Smart TV / Chromecast (Recursos Limitados 1.5GB)
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "  Iniciando Emulador Smart TV / Chromecast (Recursos Limitados 1.5GB)" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

$emulatorPath = "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe"
$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apkPath = "$PSScriptRoot\VisionPlus.apk"

Write-Host "[1/3] Lanzando Smart TV en pantalla limpia 1080p sin marco (-no-skin)..." -ForegroundColor Yellow
Write-Host "      Control: Usa las flechas del teclado, Enter (OK) y Esc (Atrás)." -ForegroundColor Gray
Start-Process -FilePath $emulatorPath -ArgumentList "-avd Vision_Chromecast_TV -no-skin"

Write-Host "[2/3] Esperando conexion ADB..." -ForegroundColor Yellow
& $adbPath wait-for-device
& $adbPath wait-for-device shell "while [[ -z `getprop sys.boot_completed` ]]; do sleep 1; done"

Write-Host "[3/3] Instalando Vision+ v2.5.47..." -ForegroundColor Green
& $adbPath install -r $apkPath

Write-Host "Abriendo Vision+ en Modo Smart TV..." -ForegroundColor Green
& $adbPath shell monkey -p com.chronosbvrx.visionplus -c android.intent.category.LAUNCHER 1

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "  ¡Listo! Maneja la app con el teclado como si fuera un control remoto:" -ForegroundColor Green
Write-Host "  - Flechas: Navegar D-pad (Arriba, Abajo, Izquierda, Derecha)" -ForegroundColor White
Write-Host "  - Enter: Seleccionar / Reproducir (D-Pad Center)" -ForegroundColor White
Write-Host "  - Escape o Retroceso: Botón Atrás" -ForegroundColor White
Write-Host "======================================================================" -ForegroundColor Cyan
