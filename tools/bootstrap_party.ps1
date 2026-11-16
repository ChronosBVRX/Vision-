<#
.SYNOPSIS
  Deja Vision+ Party (Open Party Lab + 18 juegos) listo desde un clon limpio.

.DESCRIPTION
  Reconstruye exactamente el estado de party/platform/open-party-lab:
  clona el submódulo en el pin histórico, aplica los parches de Vision+,
  copia portadas, i18n, los 18 juegos y el audio de la narradora, e instala
  dependencias y construye el host/controlador/servidor.

  Es idempotente. Si el submódulo tiene cambios locales se detiene para no
  perder trabajo; usa -Force solo si quieres descartarlos y regenerarlo.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\tools\bootstrap_party.ps1

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\tools\bootstrap_party.ps1 -SkipBuild
#>
param(
    [switch]$Force,
    [switch]$SkipInstall,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$Repo = Split-Path -Parent $PSScriptRoot
$OplUrl = "https://github.com/Hartwich/Open-Party-Lab.git"
$OplPin = "9d94b502a334afdd1a6bcb532b7cda7595d2bca9"
$Opl = Join-Path $Repo "party\platform\open-party-lab"
$Patches = Join-Path $Repo "party\platform\patches"
$Games = Join-Path $Repo "party\games"
$Content = Join-Path $Repo "party\content"

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$WorkDir
    )
    Push-Location $WorkDir
    try {
        & $Command @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$Command $($Arguments -join ' ') terminó con código $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

Write-Host "== Vision+ Party: bootstrap =="
Write-Host "Repo: $Repo"

if (-not (Test-Path (Join-Path $Opl ".git"))) {
    if (Test-Path $Opl) {
        $existing = @(Get-ChildItem -Force $Opl)
        if ($existing.Count -gt 0) {
            throw "La carpeta $Opl existe y no está vacía. Muévela o bórrala antes de continuar."
        }
    }
    Write-Host "== Clonando Open Party Lab =="
    Invoke-Checked -Command "git" -Arguments @("clone", $OplUrl, $Opl) -WorkDir $Repo
}

$dirty = (@(& git -C $Opl status --porcelain) -join "`n").Trim()
if ($dirty) {
    if (-not $Force) {
        throw "El submódulo tiene cambios locales. Usa -Force solo si quieres descartarlos y regenerar todo."
    }
    Write-Host "== Descartando cambios locales del submódulo (-Force) =="
    Invoke-Checked -Command "git" -Arguments @("-C", $Opl, "checkout", "--force", $OplPin) -WorkDir $Repo
    Invoke-Checked -Command "git" -Arguments @("-C", $Opl, "clean", "-fdx") -WorkDir $Repo
} else {
    Write-Host "== Fijando Open Party Lab en $OplPin =="
    Invoke-Checked -Command "git" -Arguments @("-C", $Opl, "checkout", $OplPin) -WorkDir $Repo
}

Write-Host "== Aplicando parches de Vision+ =="
Copy-Item -Path (Join-Path $Patches "*") -Destination $Opl -Recurse -Force

Write-Host "== Portadas e i18n =="
$hostPublic = Join-Path $Opl "apps\host\public"
$controllerPublic = Join-Path $Opl "apps\controller\public"
New-Item -ItemType Directory -Force (Join-Path $hostPublic "party-covers") | Out-Null
New-Item -ItemType Directory -Force (Join-Path $hostPublic "party-i18n") | Out-Null
New-Item -ItemType Directory -Force (Join-Path $controllerPublic "party-i18n") | Out-Null
Copy-Item -Path (Join-Path $Repo "party\assets\covers\*.webp") -Destination (Join-Path $hostPublic "party-covers") -Force
Copy-Item -Path (Join-Path $Content "i18n\es.json") -Destination (Join-Path $hostPublic "party-i18n\es.json") -Force
Copy-Item -Path (Join-Path $Content "i18n\es.json") -Destination (Join-Path $controllerPublic "party-i18n\es.json") -Force

Write-Host "== Copiando los 18 juegos a local-games =="
$knownGames = Get-Content (Join-Path $Patches "config\known-games.json") -Raw | ConvertFrom-Json
$gameIds = @($knownGames | ForEach-Object { [string]$_.id })
$localGames = Join-Path $Opl "local-games"
New-Item -ItemType Directory -Force $localGames | Out-Null
foreach ($id in $gameIds) {
    $source = Join-Path $Games $id
    if (-not (Test-Path $source)) {
        throw "Falta el juego $id en party/games"
    }
    $target = Join-Path $localGames $id
    if (Test-Path $target) { Remove-Item -Recurse -Force $target }
    Copy-Item -Path $source -Destination $target -Recurse -Force
}
Write-Host "   juegos copiados: $($gameIds.Count)"

Write-Host "== Audio de la narradora =="
$audioRoot = Join-Path $Content "narrator\audio"
$audioDirs = @(Get-ChildItem $audioRoot -Directory)
foreach ($dir in $audioDirs) {
    $target = Join-Path $hostPublic "party-audio\$($dir.Name)"
    New-Item -ItemType Directory -Force $target | Out-Null
    Copy-Item -Path (Join-Path $dir.FullName "*.m4a") -Destination $target -Force
}
Write-Host "   paquetes de audio: $($audioDirs.Count)"

if (-not $SkipInstall) {
    Write-Host "== npm install =="
    Invoke-Checked -Command "npm" -Arguments @("install", "--no-audit", "--no-fund") -WorkDir $Opl
}

if (-not $SkipBuild) {
    Write-Host "== Build web (host, controlador y servidor) =="
    Invoke-Checked -Command "npm" -Arguments @("run", "build:hosted") -WorkDir $Opl

    Write-Host "== Empaquetando el servidor =="
    $bundler = Join-Path $Opl ".vision-bundle-server.cjs"
    @'
const esbuild = require("esbuild");
esbuild.buildSync({
  entryPoints: ["apps/server/src/main.ts"],
  bundle: true,
  platform: "node",
  format: "esm",
  target: "node22",
  outfile: "artifacts/hosted/party-server.mjs",
  banner: {
    js: 'import { createRequire } from "node:module"; const require = createRequire(import.meta.url);'
  }
});
'@ | Set-Content -Path $bundler -Encoding UTF8
    try {
        Invoke-Checked -Command "node" -Arguments @(".vision-bundle-server.cjs") -WorkDir $Opl
    } finally {
        Remove-Item $bundler -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ""
Write-Host "Listo. Artefactos en party/platform/open-party-lab/artifacts/hosted"
Write-Host "Para desarrollo local: npm run dev:server / dev:host / dev:controller"
exit 0
