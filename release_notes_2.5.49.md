# Vision+ v2.5.49 — Modo Offline Blindado y Búnker Criptográfico Anti-Piratería

### 🚀 Novedades y Mejoras Principales

#### 1. Búnker Criptográfico Anti-Piratería (Grado Comercial / Cero Fugas)
- **Cifrado de Hardware AES-256 en Modo CTR**:
  - Implementado `OfflineCryptoVault`: genera y almacena la clave maestra en el chip de hardware seguro (`AndroidKeyStore` / TEE / StrongBox).
  - Encabezado binario propietario de 33 bytes `VPVAULT1` (Magic `VPVAULT1`, Versión 1, Base IV de 16 bytes y longitud original).
  - Si un usuario extrae el archivo contenedor `.vpack` mediante exploradores de archivos o ADB hacia una PC u otro dispositivo, es matemáticamente imposible de abrir o reproducir sin el chip seguro del dispositivo original.
- **Descifrado al Vuelo Directo en Memoria RAM (`EncryptedVaultDataSource`)**:
  - Implementado `EncryptedVaultDataSource` para Media3 ExoPlayer: descifra fragmentos directamente en memoria volátil conforme el decodificador de video solicita bytes.
  - Ningún archivo o frame temporal descifrado toca jamás el disco flash ni almacenamiento del sistema.
  - Búsqueda instantánea $O(1)$ (`computeIvForOffset = baseIv + (offset / 16)`): permite adelantar o atrasar la reproducción a cualquier segundo o minuto sin descifrar el archivo completo ni sufrir congelamientos.
- **Protección contra Grabación y Capturas de Pantalla (`FLAG_SECURE`)**:
  - En `PlayerScreen`, al reproducir cualquier esquema offline `vault:`, se activa `FLAG_SECURE` en la ventana de Android, impidiendo capturas de pantalla o grabadores de video externos.

#### 2. Política Estricta de Almacenamiento en Smart TV ("Política de USB Obligatoria")
- **Prevención de Saturación y Colapso de Smart TV**:
  - Los dispositivos de TV (Chromecast con Google TV, Xiaomi Stick, TV Box ZTE B866F) cuentan con memorias de 4 a 8 GB compartidas con el sistema operativo y caché. Descargar videos en memoria interna provocaría congelamientos del sistema operativo y muertes del proceso por `lmkd`.
  - **Regla Estricta**: En Smart TV (`DeviceKind.isTelevision()`), las descargas están terminantemente deshabilitadas en la memoria interna y exigen obligatoriamente una memoria USB o disco externo extraíble (`isRemovable == true`).
  - **Modal Interactivo `TvNoUsbDialog`**: Si el usuario no tiene una memoria USB conectada al pulsar descargar en TV, se despliega una interfaz explicativa con diseño premium Vision+ indicando el requisito de almacenamiento USB.

#### 3. Experiencia en Teléfonos y Tablets
- Almacenamiento seguro en el sandbox privado de la app (`filesDir/vision_vault/`) o en tarjeta MicroSD si está insertada.
- Barra medidora de almacenamiento en `DownloadsScreen`, indicando el espacio ocupado por Vision+ versus el espacio libre total en el teléfono.

#### 4. Integración UI/UX Completa en Móvil y Smart TV
- **Fichas de Detalle (`TitleScreen` y `TvTitleScreen`)**:
  - Botón de acción `[ ⬇ Descargar ]` / `[ 💾 Descargar en USB ]` con diálogo de selección de calidad (`1080p FHD`, `720p HD`, `480p SD`).
  - Progreso circular porcentual en vivo y cambio automático a estado `[ ✓ Descargado ]`.
- **Navegación Fluida**:
  - Pestaña "Descargas" en el menú lateral principal (Móvil y TV Leanback D-pad).
  - Pestaña "Descargas" en "Mi lista" (`LibraryScreen` y `TvLibraryScreen`).
  - Acceso directo rápido en Home (`SELECTIONS` y `TV_QUICK_HUBS`).
- **Reproducción Transparente**: Al pulsar reproducir en cualquier película descargada, la app utiliza la fuente segura offline sin consumir datos ni requerir conexión a internet.

### 🛡️ Pruebas y Validación
- **176 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) pasando al 100%**.
- Nuevas suites de pruebas: `OfflineCryptoVaultTest` (generación de llaves, cifrado streaming, saltos $O(1)$) y `OfflineRegistryAndStorageTest` (persistencia JSON, esquema `vault:`, borrado físico).
- **34 pruebas de backend (`pytest server/tests/`) pasando al 100%**.
- Binario de producción compilado y minificado: `app-normal-release.apk` (v2.5.49, versionCode 285).
