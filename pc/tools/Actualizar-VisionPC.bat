@echo off
setlocal EnableExtensions
title Vision+ PC - Actualizacion manual
set "URL=https://github.com/ChronosBVRX/Vision-Releases/releases/download/pc-latest/VisionPlusPC-Portable.exe"
set "TMPEXE=%TEMP%\VisionPlusPC-Portable-new.exe"
set "TARGET="

if exist "%~dp0VisionPlusPC-Portable.exe" set "TARGET=%~dp0VisionPlusPC-Portable.exe"

echo.
echo  Vision+ PC - actualizacion manual
echo.
if not defined TARGET (
  echo  Arrastra aqui VisionPlusPC-Portable.exe y pulsa Enter.
  echo  Tambien puedes escribir la ruta completa.
  set /p "TARGET=Ejecutable: "
)
set "TARGET=%TARGET:"=%"
if not exist "%TARGET%" (
  echo.
  echo  No se encontro VisionPlusPC-Portable.exe.
  pause
  exit /b 1
)

echo.
echo  Cerrando Vision+ PC...
taskkill /IM "Vision+ PC.exe" /F >nul 2>&1
taskkill /IM "VisionPlusPC-Portable.exe" /F >nul 2>&1
timeout /t 6 /nobreak >nul

echo  Descargando la ultima version...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; try { Invoke-WebRequest -Uri '%URL%' -OutFile '%TMPEXE%' -UseBasicParsing -TimeoutSec 600; if ((Get-Item -LiteralPath '%TMPEXE%').Length -lt 50000000) { throw 'descarga incompleta' } } catch { Write-Host ('ERROR: ' + $_.Exception.Message); exit 1 }"
if errorlevel 1 (
  echo.
  echo  No se pudo descargar la actualizacion.
  pause
  exit /b 1
)

echo  Reemplazando el ejecutable...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ok=$false; for($i=0;$i -lt 120;$i++){ try { Copy-Item -LiteralPath '%TMPEXE%' -Destination '%TARGET%' -Force -ErrorAction Stop; if ((Get-Item -LiteralPath '%TARGET%').Length -eq (Get-Item -LiteralPath '%TMPEXE%').Length) { $ok=$true; break } } catch { Start-Sleep -Milliseconds 500 } }; if (-not $ok) { Write-Host 'No se pudo reemplazar el archivo (puede estar bloqueado).'; exit 1 }; Start-Process -FilePath '%TARGET%'; Write-Host 'Listo. Vision+ PC se esta abriendo.'"
if errorlevel 1 (
  echo.
  echo  Cierra Vision+ PC por completo, espera 10 segundos y vuelve a intentarlo.
  pause
  exit /b 1
)

timeout /t 3 /nobreak >nul
del /q "%TMPEXE%" >nul 2>&1
exit /b 0
