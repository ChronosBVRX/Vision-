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
    [switch]$SkipPublish,
    # stable = release normal (la ven todos) · beta = pre-release `beta` (solo equipos de pruebas)
    [ValidateSet("stable", "beta")][string]$Channel = "stable"
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
    $zipalign = Get-ChildItem (Join-Path $sdk "build-tools") -Recurse -Filter "zipalign.exe" |
        Sort-Object FullName | Select-Object -Last 1
    if (-not $zipalign) { throw "No encontre zipalign en $sdk\build-tools" }

    # --- Motor dentro del APK (classes2/3/4.dex, estilo Crystal) ---
    # Se inyecta DESPUES del build (AGP ya firmo) y se vuelve a firmar con la MISMA llave:
    # asi el motor queda verificado al instalar y la app no extrae 17 MB ni lo verifica en
    # runtime. Los assets se conservan como respaldo (rollback) en esta version.
    $props = @{}
    $propsFile = Join-Path $root "keystore.properties"
    Get-Content $propsFile | Where-Object { $_ -match "=" -and $_ -notmatch "^\s*#" } | ForEach-Object {
        $kv = $_ -split "=", 2
        $props[$kv[0].Trim()] = $kv[1].Trim()
    }
    if (-not $props.ContainsKey("storeFile") -or -not $props.ContainsKey("storePassword")) {
        throw "keystore.properties incompleto: no puedo re-firmar el APK con el motor dentro"
    }
    $inject = Join-Path $root "tools\inject_engine_dex.py"
    # Los DEX de respaldo viven SOLO en debug: el release los lleva dentro del APK.
    $assetsDir = Join-Path $root "app\src\debug\assets"
    $engineApk = Join-Path $root "app\build\outputs\apk\normal\release\app-normal-release-engine.apk"
    $engineAligned = Join-Path $root "app\build\outputs\apk\normal\release\app-normal-release-engine-aligned.apk"
    $engineSigned = Join-Path $root "app\build\outputs\apk\normal\release\app-normal-release-engine-signed.apk"
    Write-Host "== Motor dentro del APK (classesN.dex despues de los propios) =="
    $dexCounter = "import sys,zipfile;z=zipfile.ZipFile(sys.argv[1]);print(sum(1 for e in z.infolist() if e.filename.startswith('classes') and e.filename.endswith('.dex')))"
    $dexBefore = [int](& python -c $dexCounter $apk)
    & python $inject $apk $engineApk $assetsDir
    if ($LASTEXITCODE -ne 0) { throw "La inyeccion del motor fallo" }
    & $zipalign.FullName -p -f 4 $engineApk $engineAligned
    if ($LASTEXITCODE -ne 0) { throw "zipalign fallo con el motor dentro" }
    & $apksigner.FullName sign --ks $props["storeFile"] --ks-key-alias $props["keyAlias"] `
        --ks-pass ("pass:" + $props["storePassword"]) --key-pass ("pass:" + $props["keyPassword"]) `
        --out $engineSigned $engineAligned
    if ($LASTEXITCODE -ne 0) { throw "La re-firma con la llave historica fallo" }
    # Candado: el motor tiene que SUMAR 3 DEX, nunca comerse los de la app (la 2.5.151 no abria
    # porque el inyector escribia en classes2/3/4 y borraba el classes2.dex propio).
    $dexAfter = [int](& python -c $dexCounter $engineSigned)
    if ($dexAfter -ne ($dexBefore + 3)) {
        throw "El APK perdio DEX propios al inyectar el motor: antes $dexBefore, despues $dexAfter (esperado $($dexBefore + 3))"
    }
    $apk = $engineSigned

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
    # Manifiesto del canal: version.json dentro del release (el actualizador lo lee sin la API).
    $versionFile = Join-Path $root "app\build\outputs\version.json"
    # ReadAllText devuelve un string plano: `Get-Content -Raw` se serializaba como objeto.
    $notesText = [string][System.IO.File]::ReadAllText((Join-Path $root $NotesFile), $utf8)
    $manifest = [ordered]@{
        version = $Version
        versionCode = $newCode
        apk = "VisionPlus.apk"
        notes = $notesText
    }
    [System.IO.File]::WriteAllText($versionFile, ($manifest | ConvertTo-Json -Compress -Depth 3), $utf8)
    if (-not $SkipPublish) {
        if ($Channel -eq "beta") {
            # Canal de PRUEBAS: release con tag fijo `beta` marcada como pre-release. GitHub
            # excluye las pre-releases de `/releases/latest`, así que los usuarios normales
            # no la ven; los equipos marcados en `beta_device_ids` sí.
            Write-Host "== Publicando BETA en el tag fijo 'beta' =="
            # `gh` escribe a stderr cuando el tag aún no existe y PowerShell 5 lo trata como
            # error terminante (misma lección que con Gradle): va por `cmd /c` y se ignora.
            & cmd /c "gh release delete beta -R ChronosBVRX/Vision- --cleanup-tag -y 2>&1" | Out-Null
            & gh release create "beta" VisionPlus.apk VisionPlusHome.apk $versionFile -R ChronosBVRX/Vision- `
                --title "Vision+ $Version (pruebas)" --notes-file (Join-Path $root $NotesFile) --prerelease
            if ($LASTEXITCODE -ne 0) { throw "gh release (beta) fallo" }
            Write-Host "== BETA $Version publicada: https://github.com/ChronosBVRX/Vision-/releases/tag/beta =="
        } else {
            Write-Host "== Publicando release v$Version =="
            gh release create "v$Version" VisionPlus.apk VisionPlusHome.apk $versionFile -R ChronosBVRX/Vision- --title $Title --notes-file (Join-Path $root $NotesFile)
            if ($LASTEXITCODE -ne 0) { throw "gh release fallo" }
            Write-Host "== OTA v$Version PUBLICADA: https://github.com/ChronosBVRX/Vision-/releases/tag/v$Version =="
        }
    } else {
        Write-Host "== Build listo (SkipPublish): VisionPlus.apk y VisionPlusHome.apk actualizados sin publicar =="
    }
    Write-Host "Pendiente manual (una vez): commitear el bump + AGENTS.md."
} catch {
    [System.IO.File]::WriteAllText($gradleFile, $original, $utf8)
    Write-Host "FALLO: se restauro app/build.gradle.kts ($($_.Exception.Message))" -ForegroundColor Red
    throw
}
