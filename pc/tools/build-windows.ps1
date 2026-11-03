# Vision+ PC - empaquetado portable para Windows
#
# Uso (PowerShell 5.1 o superior, en Windows):
#   cd repo\pc\tools
#   .\build-windows.ps1
#
# Genera:
#   repo\pc\build\VisionPlusPC\        carpeta portable lista para copiar
#   repo\pc\build\VisionPlusPC.zip     paquete para distribuir
#
# No requiere Android Studio, Node ni FFmpeg instalados: descarga Node y
# FFmpeg para Windows si no están en pc\runtime.

param(
    [string]$Output = "",
    [string]$NodeVersion = "v22.14.0",
    [string]$FfmpegUrl = "https://github.com/BtbN/FFmpeg-Builds/releases/latest/download/ffmpeg-master-latest-win64-gpl.zip",
    [switch]$SkipRuntime,
    [switch]$SkipLauncher,
    [switch]$NoZip
)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Pc = Join-Path $Repo "pc"
if ([string]::IsNullOrWhiteSpace($Output)) {
    $Output = Join-Path $Pc "build\VisionPlusPC"
}
$Output = [IO.Path]::GetFullPath($Output)

if (-not (Test-Path (Join-Path $Repo "webos\app")) -or -not (Test-Path (Join-Path $Repo "webos\service"))) {
    throw "Falta webos/app o webos/service. El port PC reutiliza el port de Smart TV; incluye webos/ en el mismo árbol."
}

function Write-Step([string]$Text) {
    Write-Host ""
    Write-Host "==> $Text" -ForegroundColor Cyan
}

function Download-File([string]$Url, [string]$Destination) {
    Write-Host "    $Url"
    $client = New-Object System.Net.WebClient
    try {
        $client.DownloadFile($Url, $Destination)
    }
    finally {
        $client.Dispose()
    }
}

Write-Step "Preparando carpeta portable"
if (Test-Path $Output) { Remove-Item -Recurse -Force $Output }
New-Item -ItemType Directory -Path $Output | Out-Null
New-Item -ItemType Directory -Path (Join-Path $Output "service") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $Output "runtime") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $Output "data") | Out-Null

Write-Step "Copiando la interfaz de Smart TV (webos/app)"
Copy-Item (Join-Path $Repo "webos\app") (Join-Path $Output "app") -Recurse -Force

Write-Step "Copiando el motor (webos/service)"
Get-ChildItem (Join-Path $Repo "webos\service") -Filter "*.js" -File |
    Where-Object { $_.Name -ne "index.js" -and $_.Name -ne "dev-server.js" } |
    Copy-Item -Destination (Join-Path $Output "service") -Force
Copy-Item (Join-Path $Repo "webos\service\package.json") (Join-Path $Output "service\package.json") -Force
Copy-Item (Join-Path $Pc "server.js") (Join-Path $Output "server.js") -Force
Copy-Item (Join-Path $Pc "LEEME-PC.txt") (Join-Path $Output "LEEME.txt") -Force

$Icon = Join-Path $Repo "vision_icon.ico"
if (Test-Path $Icon) {
    Copy-Item $Icon (Join-Path $Output "icon.ico") -Force
    Copy-Item $Icon (Join-Path $Output "app\images\icon.ico") -Force
}

Write-Step "Copiando runtime (Node + FFmpeg)"
if (-not $SkipRuntime) {
    $NodeExe = Join-Path $Output "runtime\node.exe"
    $LocalNode = Join-Path $Pc "runtime\node.exe"
    if (Test-Path $LocalNode) {
        Write-Host "    Node local: $LocalNode"
        Copy-Item $LocalNode $NodeExe -Force
    }
    else {
        Download-File "https://nodejs.org/dist/$NodeVersion/win-x64/node.exe" $NodeExe
    }
    if (-not (Test-Path $NodeExe)) { throw "No se pudo obtener runtime\node.exe." }

    $NodeLicense = Join-Path $Output "runtime\NODE-LICENSE.txt"
    try {
        Download-File "https://nodejs.org/dist/$NodeVersion/LICENSE" $NodeLicense
    }
    catch {
        Set-Content -Path $NodeLicense -Value "Node.js se distribuye bajo la licencia MIT." -Encoding UTF8
    }

    $FfmpegExe = Join-Path $Output "runtime\ffmpeg.exe"
    $LocalFfmpeg = Join-Path $Pc "runtime\ffmpeg.exe"
    if (Test-Path $LocalFfmpeg) {
        Write-Host "    FFmpeg local: $LocalFfmpeg"
        Copy-Item $LocalFfmpeg $FfmpegExe -Force
    }
    else {
        $TempDir = Join-Path $env:TEMP ("visionpc-ffmpeg-" + [Guid]::NewGuid().ToString("N"))
        New-Item -ItemType Directory -Path $TempDir | Out-Null
        $Zip = Join-Path $TempDir "ffmpeg.zip"
        try {
            Download-File $FfmpegUrl $Zip
            Expand-Archive -Path $Zip -DestinationPath $TempDir -Force
            $Found = Get-ChildItem $TempDir -Recurse -Filter "ffmpeg.exe" -File | Select-Object -First 1
            if (-not $Found) { throw "El paquete de FFmpeg no contiene ffmpeg.exe." }
            Copy-Item $Found.FullName $FfmpegExe -Force
            $License = Get-ChildItem $TempDir -Recurse -Filter "LICENSE*" -File | Select-Object -First 1
            if ($License) {
                Copy-Item $License.FullName (Join-Path $Output "runtime\FFMPEG-LICENSE.txt") -Force
            }
        }
        finally {
            Remove-Item -Recurse -Force $TempDir -ErrorAction SilentlyContinue
        }
    }
    if (-not (Test-Path $FfmpegExe)) { throw "No se pudo obtener runtime\ffmpeg.exe." }
}
else {
    Write-Host "    Omitido por -SkipRuntime (copia node.exe/ffmpeg.exe a runtime manualmente)."
}

Write-Step "Compilando el lanzador (bandeja + ventana Edge/Chrome)"
if (-not $SkipLauncher) {
    $Csc = $null
    $Candidates = @(
        (Join-Path $env:WINDIR "Microsoft.NET\Framework64\v4.0.30319\csc.exe"),
        (Join-Path $env:WINDIR "Microsoft.NET\Framework\v4.0.30319\csc.exe")
    )
    foreach ($Candidate in $Candidates) {
        if (Test-Path $Candidate) { $Csc = $Candidate; break }
    }
    if ($Csc) {
        $LauncherOut = Join-Path $Output "VisionPlusPC.exe"
        $Manifest = Join-Path $Pc "launcher\app.manifest"
        $Prebuilt = Join-Path $Pc "launcher\VisionPlusPC.exe"
        Write-Host "    csc: $Csc"
        & $Csc /nologo /target:winexe /win32icon:"$Icon" /win32manifest:"$Manifest" /out:"$LauncherOut" `
            (Join-Path $Pc "launcher\VisionPlusPC.cs") `
            /r:System.Windows.Forms.dll /r:System.Drawing.dll
        if ($LASTEXITCODE -ne 0) {
            if (Test-Path $Prebuilt) {
                Write-Warning "csc falló; se usa el lanzador precompilado."
                Copy-Item $Prebuilt $LauncherOut -Force
            }
            else {
                throw "csc terminó con código $LASTEXITCODE."
            }
        }
    }
    else {
        $Prebuilt = Join-Path $Pc "launcher\VisionPlusPC.exe"
        if (Test-Path $Prebuilt) {
            Copy-Item $Prebuilt (Join-Path $Output "VisionPlusPC.exe") -Force
            Write-Host "    csc no disponible; se usó el lanzador precompilado."
        }
        else {
            Write-Warning "No se encontró csc.exe ni un lanzador precompilado. Se usará INICIAR-VISIONPLUS.bat."
        }
    }
}

Write-Step "Creando iniciador de respaldo (.bat)"
$Bat = @'
@echo off
setlocal EnableExtensions
title Vision+ PC
cd /d "%~dp0"

set "NODE=%~dp0runtime\node.exe"
if not exist "%NODE%" set "NODE=node"

set "BROWSER="
if exist "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" set "BROWSER=%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe"
if not defined BROWSER if exist "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" set "BROWSER=%ProgramFiles%\Microsoft\Edge\Application\msedge.exe"
if not defined BROWSER if exist "%ProgramFiles%\Google\Chrome\Application\chrome.exe" set "BROWSER=%ProgramFiles%\Google\Chrome\Application\chrome.exe"
if not defined BROWSER if exist "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" set "BROWSER=%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe"

start "" /b "%NODE%" "%~dp0server.js"

echo [..] Esperando el servidor de Vision+...
powershell -NoProfile -Command "for ($i=0; $i -lt 40; $i++) { try { $r = (Invoke-WebRequest -Uri 'http://127.0.0.1:8910/ping' -Method POST -Body '{}' -ContentType 'application/json' -UseBasicParsing -TimeoutSec 1).StatusCode; if ($r -eq 200) { exit 0 } } catch {} Start-Sleep -Milliseconds 250 }; exit 1" >nul 2>&1

if not exist "%~dp0data" mkdir "%~dp0data"
set "PROFILE=%~dp0data\browser-profile"
if not exist "%PROFILE%" mkdir "%PROFILE%"

if defined BROWSER (
  start "" "%BROWSER%" --app="http://127.0.0.1:8910/?platform=pc" --user-data-dir="%PROFILE%" --window-size=1280,720 --no-first-run --no-default-browser-check --disable-session-crashed-bubble --autoplay-policy=no-user-gesture-required --disable-background-timer-throttling --disable-renderer-backgrounding --disable-backgrounding-occluded-windows
) else (
  start "" "http://127.0.0.1:8910/?platform=pc"
)

echo.
echo Vision+ PC abierto. Cierra esta ventana solo si quieres detener el servidor.
echo La aplicacion seguira en la bandeja si usas VisionPlusPC.exe.
pause >nul
powershell -NoProfile -Command "$p='%~dp0data\port.json'; if (Test-Path $p) { try { $j=Get-Content $p -Raw | ConvertFrom-Json; if ($j.pid) { Stop-Process -Id $j.pid -Force -ErrorAction SilentlyContinue } } catch {} }" >nul 2>&1
'@
Set-Content -Path (Join-Path $Output "INICIAR-VISIONPLUS.bat") -Value $Bat -Encoding ASCII

Write-Step "Resumen"
$Files = Get-ChildItem $Output -Recurse -File
$SizeMb = [Math]::Round((($Files | Measure-Object Length -Sum).Sum / 1MB), 1)
Write-Host "    Carpeta: $Output"
Write-Host "    Archivos: $($Files.Count) · $SizeMb MB"

if (-not $NoZip) {
    Write-Step "Creando ZIP"
    $ZipPath = Join-Path (Split-Path $Output -Parent) "VisionPlusPC.zip"
    if (Test-Path $ZipPath) { Remove-Item $ZipPath -Force }
    Compress-Archive -Path (Join-Path $Output "*") -DestinationPath $ZipPath -CompressionLevel Optimal
    Write-Host "    ZIP: $ZipPath"
}

Write-Host ""
Write-Host "Listo. Copia la carpeta a una PC Windows y ejecuta VisionPlusPC.exe." -ForegroundColor Green
