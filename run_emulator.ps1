# Lanzador de Emulador Frameless (Pantalla Limpia Vision+)
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "   Iniciando Emulador Frameless (Pantalla Limpia Vision+)" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

$emulatorPath = "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe"
$adbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apkPath = "$PSScriptRoot\VisionPlus.apk"

Write-Host "[1/3] Lanzando emulador en modo ventana sin marco (-no-skin)..." -ForegroundColor Yellow
Start-Process -FilePath $emulatorPath -ArgumentList "-avd Vision_Phone -no-skin"

Write-Host "[2/3] Esperando conexion ADB..." -ForegroundColor Yellow
& $adbPath wait-for-device
& $adbPath wait-for-device shell "while [[ -z `getprop sys.boot_completed` ]]; do sleep 1; done"

Write-Host "[3/3] Instalando Vision+ v2.5.47..." -ForegroundColor Green
& $adbPath install -r $apkPath

Write-Host "Abriendo Vision+..." -ForegroundColor Green
& $adbPath shell monkey -p com.chronosbvrx.visionplus -c android.intent.category.LAUNCHER 1
Write-Host "Listo. Vision+ ejecutandose en emulador." -ForegroundColor Cyan
