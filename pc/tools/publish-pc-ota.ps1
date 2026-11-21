param(
    [string]$NotesFile = "",
    [string]$Repo = "ChronosBVRX/Vision-Releases",
    [string]$Tag = "pc-latest"
)

# Vision+ PC - publica el ejecutable portable y su manifiesto en el canal fijo
# `pc-latest` de GitHub Releases. Equivalente a publish-pc-ota.sh para Windows.
# No toca el canal estable de Android.

$ErrorActionPreference = "Stop"

$tools = $PSScriptRoot
$pc = Split-Path -Parent $tools
$repoRoot = Split-Path -Parent $pc
$exe = Join-Path $pc "build\VisionPlusPC-Portable.exe"
$outManifest = Join-Path $pc "build\pc-version.json"
$manualUpdater = Join-Path $pc "tools\Actualizar-VisionPC.bat"

if (-not $NotesFile) { $NotesFile = Join-Path $pc "electron\OTA_NOTES.md" }

if (-not (Test-Path $exe)) { throw "FALTA: $exe" }
if (-not (Test-Path $NotesFile)) { throw "FALTA el archivo de notas: $NotesFile" }

$version = (Get-Content (Join-Path $pc "electron\package.json") -Raw | ConvertFrom-Json).version
$sha = (Get-FileHash $exe -Algorithm SHA256).Hash.ToLower()
$size = (Get-Item $exe).Length
$notes = [System.IO.File]::ReadAllText((Resolve-Path $NotesFile).Path, [System.Text.Encoding]::UTF8)

$manifest = [ordered]@{
    version = $version
    exe     = "VisionPlusPC-Portable.exe"
    sha256  = $sha
    size    = $size
    notes   = $notes
} | ConvertTo-Json -Compress -Depth 3

$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($outManifest, $manifest, $utf8)

Write-Host "==> PC $version - $sha - $size bytes"

& gh release view $Tag -R $Repo *> $null
if ($LASTEXITCODE -ne 0) {
    & gh release create $Tag -R $Repo --prerelease `
        --title "Vision+ PC - canal OTA" `
        --notes "Canal fijo de actualizaciones del ejecutable portable de Vision+ PC."
    if ($LASTEXITCODE -ne 0) { throw "No se pudo crear el canal $Tag" }
}

& gh release edit $Tag -R $Repo --prerelease `
    --title "Vision+ PC - canal OTA" `
    --notes "Canal fijo de actualizaciones del ejecutable portable de Vision+ PC."
if ($LASTEXITCODE -ne 0) { throw "No se pudo actualizar el canal $Tag" }

& gh release upload $Tag $exe $outManifest -R $Repo --clobber
if ($LASTEXITCODE -ne 0) { throw "No se pudo subir la actualizacion" }
if (Test-Path $manualUpdater) {
    & gh release upload $Tag $manualUpdater -R $Repo --clobber
    if ($LASTEXITCODE -ne 0) { throw "No se pudo subir el actualizador manual" }
}

Write-Host "==> Publicado: https://github.com/$Repo/releases/download/$Tag/pc-version.json"
