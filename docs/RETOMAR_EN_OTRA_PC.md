# Retomar Vision+ en otra PC

Todo el código de la app, los 18 juegos, las portadas, el audio de la narradora, los parches de
Vision+ Party, el broker y las notas de OTA viven en este repositorio. Un clon limpio más un
comando deja el entorno listo.

## 1. Requisitos

- **Git** y **GitHub CLI** (`gh auth login` para publicar OTAs).
- **JDK 17+** (el JBR de Android Studio sirve) y **Android SDK** (API 35/36).
- **Node.js 22+** (probado con 24) y **Python 3** (para inyectar el motor DEX).
- En Windows: PowerShell 5.1+ y Git para Windows. En Linux/macOS/WSL: `bash`.

Variables de ejemplo en Windows:

```powershell
$env:JAVA_HOME    = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

## 2. Clonar

```bash
git clone https://github.com/ChronosBVRX/Vision-.git
cd Vision-
```

> El submódulo `party/platform/open-party-lab` se clona y parchea en el paso 3. No hace falta
> `--recurse-submodules`, aunque también funciona.

## 3. Preparar Vision+ Party (una sola vez)

Windows:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\bootstrap_party.ps1
```

Linux/macOS/WSL:

```bash
./tools/bootstrap_party.sh
```

El bootstrap:

1. Clona Open Party Lab en el **pin histórico** `9d94b50`.
2. Aplica los parches de `party/platform/patches` (incluye el tipo `GameHostContract` con
   `landscape` y el `package-lock` usados por los juegos nuevos).
3. Copia las 30 portadas a `apps/host/public/party-covers`, el `es.json` y el audio de la narradora.
4. Copia los **18 juegos** de `party/games` a `local-games`.
5. Ejecuta `npm install`, `npm run build:hosted` y empaqueta el servidor en
   `artifacts/hosted/party-server.mjs`.

Si el submódulo ya tiene cambios locales, el script se detiene para no perder trabajo. Usa
`-Force` / `--force` solo si quieres descartarlos y regenerar todo. También acepta
`-SkipInstall` / `--skip-install` y `-SkipBuild` / `--skip-build`.

### Desarrollo local de Party

```bash
cd party/platform/open-party-lab
npm run dev:server      # puerto 3000
npm run dev:host        # puerto 5173
npm run dev:controller  # puerto 5174
```

Abre `http://localhost:5173/?game=light-trails` y el controlador en `http://localhost:5174`.

## 4. Firma de la OTA (transferencia segura, no va en Git)

El repositorio **no** contiene la llave de firma ni `keystore.properties` (están en `.gitignore`).
Sin la llave histórica, `publish_ota.ps1` aborta a propósito para no romper las actualizaciones.

En la PC nueva:

1. Copia el archivo histórico `debug.keystore` desde la PC actual o desde un respaldo seguro a
   `%USERPROFILE%\.android\debug.keystore` (Linux/macOS: `~/.android/debug.keystore`).
2. Copia `keystore.properties.example` como `keystore.properties` y ajusta `storeFile`.
3. Verifica que la huella sea la histórica:

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -storepass android |
  Select-String "SHA256:"
```

Debe contener `95:93:CB:0D:2B:44:B8:C5:A4:7C:15:7C:13:34:2C:3D:4A:55:62:98:43:D5:62:D2:C0:49:33:1E:C7:74:73:F3`.

Nunca subas `debug.keystore`, `*.jks`, `*.keystore` ni `keystore.properties`.

## 5. Compilar y probar

```powershell
.\gradlew.bat :app:compileNormalDebugKotlin --console=plain
.\gradlew.bat :app:testNormalDebugUnitTest --console=plain
```

Fallos conocidos y ambientales de la suite (no los introduce este proyecto):

- `EncryptedVaultDataSourcePortableTest > streamsAndSeeksPortableContainer` (runtime nativo de Robolectric).
- `TvProfilesRenameTest > theTvProfilesScreenIsDarkGlassWithoutFakeActionBars` (colores viejos del diálogo).

`LiveSportsPlayerUiTest` ya pasa: guarda que Deportes no muestre canales de la casa. No uses ADB
sobre la TV del dueño; valida en emulador/JVM. Reglas: `docs/INVARIANTES_REGRESION.md`.

## 6. Publicar una OTA

1. Escribe `ota_notes_X.Y.Z.md`.
2. Ejecuta:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\publish_ota.ps1 -Version X.Y.Z -NotesFile ota_notes_X.Y.Z.md -SkipTests
```

El script sube el `versionCode`, compila R8, inyecta los 3 DEX del motor, re-firma y valida la
huella histórica en los dos APK. Publica en `ChronosBVRX/Vision-Releases`.

## 7. VPS de Vision+ Party

El host público vive en `party.visionplusv2.duckdns.org` y el contenedor en
`/opt/vision-party` del VPS. Para desplegar cambios web:

```bash
cd party/platform/open-party-lab
npm run build:hosted
node .vision-bundle-server.cjs   # o el comando de bootstrap
```

Sube `artifacts/hosted/party-server.mjs` y `artifacts/hosted/web/`, reconstruye la imagen
`vision-party:local` y reinicia el contenedor. Haz un respaldo de `/opt/vision-party` antes de
reemplazar archivos. El despliegue del 2.6.53 dejó respaldo en
`/opt/vision-party.bak-pre-juegos-20261116`.

## 8. Qué no está en Git (a propósito)

- APKs: se publican en `ChronosBVRX/Vision-Releases`.
- `node_modules`, `artifacts`, builds de Gradle y caches.
- `keystore.properties`, `*.keystore`, `*.jks`, `.env`, `local.properties`.
- `AGENTS.md` (notas internas de operación).

Con el bootstrap y la llave copiada de forma segura, el clon queda equivalente al entorno de
producción actual (OTA 2.6.53 / vc549).
