param(
    [string]$Ffmpeg = "",
    [switch]$SkipInstall
)

# Vision+ PC — construye el ejecutable portable único para Windows.
# Equivalente a build-single-exe.sh, sin depender de bash/7z/curl.
#
#   powershell -ExecutionPolicy Bypass -File .\pc\tools\build-single-exe.ps1
#
# Salida: pc\build\VisionPlusPC-Portable.exe

$ErrorActionPreference = "Stop"
$tools = $PSScriptRoot
$pc = Split-Path -Parent $tools
$repo = Split-Path -Parent $pc
$work = Join-Path $env:TEMP "visionpc-electron-build"
$out = Join-Path $pc "build"

function Step($text) { Write-Host "==> $text" }

if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Item -ItemType Directory -Path $work, (Join-Path $work "runtime"), (Join-Path $work "service") -Force | Out-Null

Step "Copiando el árbol de Electron"
Copy-Item (Join-Path $pc "electron\main.js") $work
Copy-Item (Join-Path $pc "electron\preload.js") $work
Copy-Item (Join-Path $pc "electron\updater.js") $work
Copy-Item (Join-Path $pc "electron\package.json") $work
Copy-Item (Join-Path $pc "electron\FFMPEG-LICENSE.txt") (Join-Path $work "runtime")
Copy-Item (Join-Path $pc "server.js") $work
Copy-Item (Join-Path $repo "vision_icon.ico") (Join-Path $work "icon.ico")
Copy-Item (Join-Path $pc "electron\icon.png") $work
Copy-Item (Join-Path $pc "electron\splash.html") $work
Copy-Item (Join-Path $pc "electron\vision_logo_horizontal.png") $work
Copy-Item (Join-Path $pc "electron\splash.bmp") $work
Copy-Item (Join-Path $pc "LEEME-PC.txt") (Join-Path $work "LEEME.txt")

Step "Copiando la interfaz y el motor"
Copy-Item (Join-Path $repo "webos\app") (Join-Path $work "app") -Recurse
Copy-Item (Join-Path $repo "vision_icon.ico") (Join-Path $work "app\images\icon.ico")
Get-ChildItem (Join-Path $repo "webos\service\*.js") | Copy-Item -Destination (Join-Path $work "service")
Copy-Item (Join-Path $repo "webos\service\package.json") (Join-Path $work "service")

if (-not $Ffmpeg) {
    $local = Join-Path $pc "runtime\ffmpeg.exe"
    if (Test-Path $local) {
        $Ffmpeg = $local
    } else {
        $command = Get-Command ffmpeg.exe -ErrorAction SilentlyContinue
        if ($command) { $Ffmpeg = $command.Source }
    }
}
if (-not $Ffmpeg -or -not (Test-Path $Ffmpeg)) {
    throw "No se encontró ffmpeg.exe. Pásalo con -Ffmpeg C:\ruta\ffmpeg.exe"
}
Step "FFmpeg: $Ffmpeg"
Copy-Item $Ffmpeg (Join-Path $work "runtime\ffmpeg.exe")

if (-not $SkipInstall) {
    Step "Instalando electron/electron-builder"
    Push-Location $work
    try {
        $env:ELECTRON_SKIP_BINARY_DOWNLOAD = "1"
        & npm.cmd install --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw "npm install falló ($LASTEXITCODE)" }
    } finally {
        Pop-Location
    }
}

Step "Empaquetando .exe portable"
Push-Location $work
try {
    $env:ELECTRON_SKIP_BINARY_DOWNLOAD = "1"
    $env:CSC_IDENTITY_AUTO_DISCOVERY = "false"
    & npx.cmd electron-builder --win portable --x64
    if ($LASTEXITCODE -ne 0) { throw "electron-builder falló ($LASTEXITCODE)" }
} finally {
    Pop-Location
}

$exe = Join-Path $work "dist\VisionPlusPC-Portable.exe"
if (-not (Test-Path $exe)) { throw "No se generó $exe" }
New-Item -ItemType Directory -Path $out -Force | Out-Null
Copy-Item $exe (Join-Path $out "VisionPlusPC-Portable.exe") -Force

$file = Get-Item (Join-Path $out "VisionPlusPC-Portable.exe")
$hash = (Get-FileHash $file.FullName -Algorithm SHA256).Hash.ToLower()
Write-Host ""
Write-Host "Listo: $($file.FullName)"
Write-Host "SHA-256: $hash"
Write-Host "Bytes:   $($file.Length)"
