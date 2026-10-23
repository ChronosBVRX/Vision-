<#
.SYNOPSIS
  Publica una OTA de Vision+ en UN comando: bump + tests + build firmado (R8) +
  verificación de firma histórica + release en GitHub con el asset VisionPlus.apk.

.DESCRIPTION
  Reemplaza los pasos manuales de siempre. Si CUALQUIER paso falla, restaura
  app/build.gradle.kts y no publica nada. La firma se compara contra la llave
  histórica 9593cb0d… (si no coincide, aborta).

.EXAMPLE
  .\tools\publish_ota.ps1 -Version 2.5.91 -NotesFile ota_notes_2.5.91.md
  .\tools\publish_ota.ps1 -Version 2.5.92 -NotesFile ota_notes_2.5.92.md -SkipPublish  # solo build+verificación
#>
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][string]$NotesFile,
    [string]$Title = "",
    [switch]$SkipTests,
    [switch]$SkipPublish
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$HISTORIC_SIGNER = "9593cb0d2b44b8c5a47c157c13342c3d4a55629843d562d2c049331ec77473f3"
$gradleFile = Join-Path $root "app\build.gradle.kts"
$utf8 = [System.Text.UTF8Encoding]::new($false)

if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw "Version invalida (usa X.Y.Z): $Version" }
if (-not (Test-Path (Join-Path $root $NotesFile))) { throw "No existe el archivo de notas: $NotesFile" }
if (-not $Title) { $Title = "Vision+ $Version" }

# --- Bump (con restauración si algo falla) ---
$original = [System.IO.File]::ReadAllText($gradleFile)
$m = [regex]::Match($original, 'versionCode = (\d+)')
if (-not $m.Success) { throw "No encontre versionCode en app/build.gradle.kts" }
$newCode = [int]$m.Groups[1].Value + 1
$bumped = [regex]::Replace($original, 'versionCode = \d+', "versionCode = $newCode")
$bumped = [regex]::Replace($bumped, 'versionName = "[^"]+"', ('versionName = "' + $Version + '"'))
[System.IO.File]::WriteAllText($gradleFile, $bumped, $utf8)
Write-Host "== Bump: versionName=$Version versionCode=$newCode =="

try {
    # --- Tests (ultima red local; el CI ya los corre en cada push) ---
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
    if (-not $SkipTests) {
        Write-Host "== Tests unitarios =="
        # `cmd /c ... 2>&1`: Gradle escribe progreso por stderr y PowerShell 5 lo convertía
        # en un error terminante (la publicación se caía con la suite en verde).
        & cmd /c ".\gradlew.bat :app:testNormalDebugUnitTest --console=plain 2>&1" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Tests en ROJO: se cancela la publicacion" }
    }

    # --- Build release (R8) ---
    Write-Host "== Build release (R8): Vision+ y Vision+ Home =="
    & cmd /c ".\gradlew.bat :app:assembleNormalRelease :launcher:assembleRelease --console=plain 2>&1" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "El build release fallo" }
    $apk = Join-Path $root "app\build\outputs\apk\normal\release\app-normal-release.apk"
    $homeApk = Join-Path $root "launcher\build\outputs\apk\release\launcher-release.apk"
    if (-not (Test-Path $apk)) { throw "No hay APK en $apk" }
    if (-not (Test-Path $homeApk)) { throw "No hay APK del launcher en $homeApk" }

    # --- Verificacion de firma historica (los DOS APK) ---
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
    $apksigner = Get-ChildItem (Join-Path $sdk "build-tools") -Recurse -Filter "apksigner.bat" |
        Sort-Object FullName | Select-Object -Last 1
    if (-not $apksigner) { throw "No encontre apksigner en $sdk\build-tools" }
    foreach ($pair in @(@("Vision+", $apk), @("Vision+ Home", $homeApk))) {
        $cert = & $apksigner.FullName verify --print-certs $pair[1] | Select-String "SHA-256 digest" | Select-Object -First 1
        if ("$cert" -notmatch $HISTORIC_SIGNER) {
            throw "La firma de $($pair[0]) NO es la historica (apksigner dijo: $cert). Se cancela."
        }
    }
    $size = [Math]::Round((Get-Item $apk).Length / 1MB, 1)
    $homeSize = [Math]::Round((Get-Item $homeApk).Length / 1MB, 1)
    Write-Host "== Firma historica OK en los dos APK; Vision+: $size MB, Vision+ Home: $homeSize MB =="

    # --- Copia y publicacion ---
    Copy-Item $apk (Join-Path $root "VisionPlus.apk") -Force
    Copy-Item $homeApk (Join-Path $root "VisionPlusHome.apk") -Force
    if (-not $SkipPublish) {
        Write-Host "== Publicando release v$Version =="
        gh release create "v$Version" VisionPlus.apk VisionPlusHome.apk -R ChronosBVRX/Vision- --title $Title --notes-file (Join-Path $root $NotesFile)
        if ($LASTEXITCODE -ne 0) { throw "gh release fallo" }
        Write-Host "== OTA v$Version PUBLICADA: https://github.com/ChronosBVRX/Vision-/releases/tag/v$Version =="
    } else {
        Write-Host "== Build listo (SkipPublish): VisionPlus.apk y VisionPlusHome.apk actualizados sin publicar =="
    }
    Write-Host "Pendiente manual (una vez): commitear el bump + AGENTS.md."
} catch {
    [System.IO.File]::WriteAllText($gradleFile, $original, $utf8)
    Write-Host "FALLO: se restauro app/build.gradle.kts ($($_.Exception.Message))" -ForegroundColor Red
    throw
}
