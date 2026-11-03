# TidyTV: análisis y reutilización para Vision+

**Fecha:** 2026-11-03 · **APK analizada:** TidyTV 0.10.1 (versionCode 13), `com.appfactory.tidytv`
**Origen:** `TidyTV-Clean-Android-TV_0.10.1_apkcube.apks` (base + splits de idioma/densidad/ABI)
**Licencia:** MIT (TidyTV y CleanYourTV). Ver `app/src/main/assets/debloat/LICENSE-*.txt`.

---

## 1. Resumen ejecutivo

TidyTV no inventa un protocolo: **habla ADB desde el teléfono**. Se presenta como un host más
del televisor (llave RSA propia), abre un stream `shell:` y ejecuta los mismos `pm` que se
harían desde una PC. Lo que aporta sobre un script suelto es:

1. **Descubrimiento y emparejamiento** para Android 8/9 (5555) y Android 11+ (TLS + código).
2. **Listas por modelo** (335 archivos) probadas en televisores reales.
3. **Motor de limpieza seguro**: solo `pm disable-user`, nunca desinstala; protege apps del
   sistema, entradas de TV, mando, teclado, Play Store y Play Services; verifica cada lote.
4. **Copia y deshacer**: guarda el estado antes de tocar, escribe la lista de deshacer en el
   propio televisor y puede restaurar aunque se pierda el teléfono.
5. **Cambio de Inicio**: instala su launcher, lo arranca, retira el Inicio de fábrica solo si
   hace falta, comprueba con la tecla Home y revierte si algo sale mal.

Vision+ ya tenía la mitad de esto en **Ajustes → Instalar en TV**: conexión ADB (primero LibADB; el flujo final usa Kadb),
emparejamiento con código, conexión por IP para Android 8/9, instalación de `VisionPlus.apk` y
`VisionPlusHome.apk`, y fijado del Inicio. **Lo que faltaba era la limpieza.** Este trabajo
integra el motor y las listas de TidyTV en ese flujo, sin añadir una segunda app ni un segundo
sistema de conexión.

---

## 2. Qué viaja dentro de la APK

| Parte | Contenido | Para qué sirve |
|---|---|---|
| `classes*.dex` | App Compose + Kadb + PairIP | UI, conexión ADB, pairing |
| `assets/lists/*.txt` | 335 listas por modelo | Debloat seguro y reproducible |
| `assets/launcher/cleanyourtv-home.apk` | Launcher de CleanYourTV (MIT) | Inicio alternativo |
| `assets/launcher/tvhome.apk` | Launcher simple de tv-detox (MIT) | Inicio alternativo |
| `assets/themes/*` | Temas/fondos del launcher | Personalización |
| `assets/icons.json` | Iconos Simple Icons | UI de apps |

La app está protegida con **PairIP** (`com.pairip.application.Application`), así que el DEX no
se lee linealmente. El análisis se hizo con el manifiesto, los recursos, las listas y el código
fuente público de TidyTV (MIT), que es el mismo que publica el APK.

**Dato importante:** los `.apks` son un *split APK*. Para instalar a mano haría falta
`adb install-multiple` con `base.apk` + `split_config.*`; la app real se instala desde Play o
desde el APK universal del release. Para nuestro flujo no hace falta: solo se reutilizan las
listas y el conocimiento del protocolo.

---

## 3. Cómo conecta TidyTV (y por qué detecta “todas” las teles)

### 3.1 Dos mundos de ADB

| Android | Depuración | Puerto | Autorización |
|---|---|---|---|
| 8 / 9 | «Depuración USB» + «ADB por red» | 5555 clásico | Aviso «¿Permitir depuración?» en la tele; la llave se guarda al marcar «Permitir siempre» |
| 11 / 12 / 13 | «Depuración inalámbrica» | TLS efímero + pairing | Código de 6 dígitos; después TLS con la misma llave |

Android 11+ **no** abre el 5555 por defecto. Por eso un flujo que solo pruebe 5555 “no ve” esas
teles: hay que escuchar `_adb-tls-pairing._tcp` y `_adb-tls-connect._tcp` por mDNS y hacer el
pairing SPAKE2. TidyTV usa **Kadb** (`com.flyfishxu.kadb`), que implementa el protocolo ADB y el
pairing en Kotlin, sin binario `adb` ni servidor local.

### 3.2 Descubrimiento real que usa TidyTV

Su pantalla de conexión combina tres fuentes a la vez:

1. **Barrido TCP del /24** en el puerto 5555 (253 hosts, ~64 en paralelo, timeout ~300 ms).
   Encuentra Android 8/9 y también teles nuevas con «ADB por red» clásico.
2. **mDNS `_googlecast._tcp`** para el nombre amigable y el modelo (`fn`, `md`). No es ADB,
   pero sirve para que la tele aparezca con nombre aunque el 5555 esté cerrado.
3. **mDNS `_adb-tls-connect._tcp` / `_adb-tls-pairing._tcp`** para Android 11+.

Mantiene un `MulticastLock` mientras busca: sin él, muchos teléfonos descartan mDNS.

### 3.3 Pairing y autorización

- Android 11+: `Kadb.pair(host, puerto, código)` y después conexión TLS al puerto de
  `_adb-tls-connect`.
- Android 8/9: se conecta al 5555 y **envía la llave pública**; la tele muestra el aviso. Hay
  que esperar a que la persona acepte (TidyTV espera 90 s y avisa en pantalla).
- La llave se guarda en el teléfono (`KadbCert` + almacén de archivo). Vision+ ya tenía
  `AdbKeys`; el flujo final importa esa misma llave en `KadbCert`, así la tele no vuelve a pedir
  permiso al cambiar de cliente.

### 3.4 Lo que ya tenía Vision+

`data/adb/TvAdb.kt` y `TvHomeSetup.kt` ya hacían:

- `AdbDiscovery` por NSD para `_adb-tls-pairing` / `_adb-tls-connect`.
- `connectByAddress` para Android 8/9 (con comprobación previa del puerto 5555).
- `connectAllowingAuthorization` para que el cliente ADB envíe la llave y la tele muestre el aviso.
- Instalación por `exec:pm install -r -S <tamaño>` (streaming, sin cargar 82 MB en memoria).
- Fijado de Inicio con `pm set-home-activity` y respaldo `cmd package set-home-activity`.
- Detección de quién abre realmente el Inicio con `dumpsys activity activities` + `KEYCODE_HOME`.
- Reversión del Inicio y de los launchers retirados.

**Conclusión y cambio de cliente:** el protocolo es el mismo, pero en la TCL Android 9 real
LibADB cerraba la conexión después de dos streams (`shell:`); los comandos siguientes se
quedaban esperando para siempre. El flujo final usa **Kadb 1.3.0**, el mismo cliente de TidyTV:
usa `shell:` clásico en Android 9 y shell v2 en los modernos, y mantiene los streams. Se conserva
la llave de `AdbKeys` (importada a `KadbCert`), así que no hay que aceptar otro permiso.

---

## 4. El motor de limpieza de TidyTV

### 4.1 Formato de las listas

Cada archivo de `assets/lists/` es texto plano:

```text
# TCL Smart TV Pro, US (device G05, product G05_4K_US)
# Android 11 (Google TV), firmware AR2101
# Stock launcher: com.google.android.apps.tv.launcherx
# Keep com.tcl.tvinput, com.tcl.settings, com.tcl.keyhelp (remote).
#
# Ads and tracking
com.tcl.ttvs
com.tcl.messagebox
# Voice (owner's choice)
# com.tcl.smartalexa  # Alexa
```

- La primera línea identifica modelo/código/Android: es lo que usa el matcher.
- Las líneas activas son paquetes que se apagan.
- Las comentadas con un paquete son **opcionales**: se ofrecen, pero no se marcan solas.
- «Keep …» protege paquetes aunque aparezcan en la lista.
- «Stock launcher: …» dice cuál es el Inicio de fábrica.
- Los encabezados explican límites, modelos hermanos, paquetes protegidos por firmware y
  resultados reales. Son documentación, no relleno.

### 4.2 Selección por modelo

`ModelMatcher` puntúa:

- **100** si el `ro.product.device` o `ro.product.name` aparece en el título/ID de la lista.
- **95** si el modelo específico (no genérico) aparece en el título.
- **40–60** si coincide la marca y la versión mayor de Android. Es «modelo parecido, no
  probado», nunca exacto.
- Modelos genéricos («Smart TV», «Android TV», «TV») no bastan solos.

Entre candidatas, ordena por cobertura: cuántas apps de la lista están instaladas en esa tele.
Así, dos listas de la misma marca se diferencian por firmware.

### 4.3 Qué nunca se apaga

TidyTV protege, y Vision+ copia esa lista:

- Android, System UI, Ajustes de TV, instalador de paquetes, control de permisos, shell.
- Play Store, Play Services, Google Services Framework, WebView.
- Proveedores del sistema (settings, TV, media, downloads, external storage).
- Bluetooth, teclado/IME, servicios de entrada de TV, sintonizador, HDMI.
- Servicios de accesibilidad e IME **en uso ahora** (se leen de `settings` y `dumpsys tv_input`).
- Todo Inicio conocido (Google TV, Android TV, Amazon, CVTE, Vision+ Home, etc.).
- Lo que la lista dice «Keep».
- Apps marcadas `PERSISTENT` (siempre corriendo).

Además, Vision+ protege `com.chronosbvrx.visionplus` y
`com.chronosbvrx.visionplus.home`: son el objetivo del flujo.

### 4.4 Ejecución segura

Por cada lote de 5 paquetes:

1. Escribe la lista de deshacer **en el televisor** (`/data/local/tmp/tidytv/disabled.txt`),
   antes de tocar nada. Si no puede escribirla, se detiene.
2. Ejecuta `pm disable-user --user 0 <pkg>` uno por uno.
3. Comprueba con `pm list packages -d` que de verdad quedaron apagados.
4. Si un paquete protegido por el firmware se resiste, lo anota y sigue. Si la tele deja de
   contestar, corta.
5. Al final espera 15 s antes de reiniciar o cambiar el Inicio: Android guarda el estado de los
   paquetes con retraso y reiniciar antes puede deshacerlo.

**Nunca usa `pm uninstall` para el bloatware.** TidyTV tiene un modo «remove» opcional para
apps del dueño, pero el flujo normal y el de Vision+ solo apagan. Eso es lo que hace que
«Restaurar» sea real.

### 4.5 Copia y restauración

Antes de cambiar:

- `getprop` completo.
- `pm list packages` y `pm list packages -d`.
- Inicio actual (`resolve-activity`).
- `settings list global/secure/system`.

TidyTV guarda todo en el teléfono y, además, la lista de paquetes apagados en el televisor.
Restaurar une el registro del teléfono + la lista del televisor, reactiva con `pm enable`,
reinstala con `cmd package install-existing` si algo se había quitado para el usuario, y
devuelve el Inicio original. Si el teléfono se perdió, la lista del televisor sigue allí.

### 4.6 Cambio de Inicio (handover)

El orden probado es:

1. Instalar el APK solo. Si falla, no se toca el Inicio.
2. Arrancarlo y comprobar que abre (`am start -W` → `Status: ok`, sin líneas `Error`).
3. Si Google TV tiene `com.google.android.tungsten.setupwraith` como candidato a Inicio,
   apagarlo **antes** de retirar el launcher de fábrica, y solo si es seguro (no es dueño del
   Wi-Fi guardado y el setup ya terminó).
4. `cmd package set-home-activity` (y `cmd role add-role-holder` en Android 10–13; en Android 14
   algunas teles cuelgan con el rol, así que TidyTV solo usa la preferencia).
5. Retirar el launcher de fábrica solo si sigue robando el Inicio y no es `PERSISTENT`.
6. Comprobar: resolver el Inicio y **pulsar Home desde Ajustes**, leyendo el paquete que queda
   al frente. Si no es el nuestro, revertir (reactivar fábrica, devolver rol/preferencia,
   reactivar setupwraith si lo apagamos nosotros).

En **Fire TV** no se puede cambiar el Inicio: Amazon lo impone. TidyTV limpia, pero deja el
launcher de Amazon. Vision+ hace lo mismo y lo dice explícitamente.

---

## 5. Qué se integró en Vision+

### 5.1 Nuevo paquete `data/debloat/`

| Archivo | Responsabilidad |
|---|---|
| `DebloatCatalog.kt` | Modelo de lista, parser, catálogo de assets, `TvInfo` y `ModelMatcher` |
| `DebloatRules.kt` | Paquetes protegidos, Inicios, grupos de revisión, nombres legibles |
| `DebloatEngine.kt` | Comandos, snapshot, copia, motor de apagado, restauración y reglas de plan |

Las 335 listas viajan en `app/src/main/assets/debloat/` (2.3 MB) con sus dos licencias MIT.

### 5.2 Flujo nuevo en «Instalar en TV»

1. La persona entra en **Mi tele** y pulsa **«Mi tele está lista»**. La app busca en la red
   las teles con depuración: barrido del /24 en 5555 (Android 8/9) + `_adb-tls-connect` /
   `_adb-tls-pairing` (Android 11+) + nombre de Google Cast, con `MulticastLock`.
2. Aparecen todas como tarjetas; si una pide código, se escribe en el momento. Si una solo
   publica Google Cast, se ofrece «Activa la depuración».
3. Lee el televisor (`getprop`, paquetes, apagados, Inicio, IMEs, accesibilidad, TV inputs).
4. Elige la lista por modelo y calcula el plan.
5. **Revisión:** modelo, Android, lista probada/parecida, grupos con contador y total exacto.
   Por defecto solo «Anuncios y rastreo» + «Apps preinstaladas».
6. Al confirmar: copia obligatoria → lista de deshacer en la tele → apagado por lotes →
   verificación → instalación de Vision+ → instalación de Vision+ Home → Inicio → prueba real.
   Antes de retirar un Inicio de fábrica se comprueba que no sea `PERSISTENT` y, si es la app de
   setup de Google TV, que no sea dueña del Wi-Fi ni tenga la configuración inicial a medias.
7. Si algo se corta, no se instala a medias; lo apagado queda registrado para «Restaurar». El
   Inicio de fábrica retirado también se anota en la lista de deshacer del televisor.

### 5.3 Cambios de estado y datos

`TvRecord` guarda ahora:

- `cleaned`: apps apagadas por la limpieza (distintas de los Inicios retirados).
- `cleanupList`: id de la lista aplicada (p. ej. `tcl-g05-google-tv`).
- `backupFile`: copia en `filesDir/tv_backups/<id>.json`.
- `cleanupAt`: fecha de la limpieza.

`restore` reactiva `cleaned + disabled +` la lista de deshacer del televisor, devuelve el Inicio
original y limpia la lista de deshacer. La interfaz muestra «N apps apagadas» y «copia guardada».

### 5.4 Lo que se dejó fuera a propósito

- **No** se desinstala nada. Solo `pm disable-user`.
- **No** se apagan apps de streaming, voz, casting ni actualizaciones por defecto. Están en
  grupos que la persona puede marcar.
- **No** se aplica una lista de otro modelo sin avisar: si no es exacta, empieza con 0 grupos.
- **No** se toca el Inicio en Fire TV.
- **Sí** se reemplazó LibADB por Kadb: en la TCL real LibADB moría tras dos comandos. Kadb
  es el cliente que TidyTV ya probó en esa tele; se conserva la llave existente.
- **No** se añade más infraestructura de la necesaria: Kadb + Conscrypt + BouncyCastle.

---

## 6. Licencias y atribución

TidyTV y CleanYourTV son **MIT**. Se incluyen:

- `app/src/main/assets/debloat/LICENSE-TidyTV.txt`
- `app/src/main/assets/debloat/LICENSE-CleanYourTV.txt`
- Entrada en `THIRD_PARTY_NOTICES.md`.

Las listas se distribuyen tal cual, sin cambios de contenido, para no perder la trazabilidad de
lo que se probó en cada modelo.

---

## 7. Pruebas

Se añadieron pruebas JVM (sin televisor):

- Parseo de las 335 listas y rechazo de listas con comandos o duplicados.
- Emparejamiento exacto por código de placa y versión de Android; modelo genérico no exacto.
- Protección de Android, Play, IMEs, entradas de TV, Inicios y Vision+.
- Selección por defecto: solo ads + preinstaladas; voz/casting/updates/streaming/opcionales
  apagados.
- Motor: escribe la lista de deshacer antes del primer `pm disable-user`, verifica con
  `pm list packages -d`, salta protegidos por firmware, se detiene si no puede escribir la
  lista, no toca ya apagados ni persistentes.
- Copia JSON ida/vuelta.
- Barrido: solo /24 privadas, 253 hosts, puerto abierto/cerrado.
- Cableado del flujo y de la UI (revisión, confirmación, restauración, Fire TV).

**Pendiente de prueba física** (no hay televisor conectado en este entorno): ejecutar el flujo
completo en una TCL/Google TV Android 11 y en una Android 8/9, revisar que la limpieza no rompa
Wi-Fi, mando, HDMI ni Play Store, y que el Inicio quede tras reiniciar. La lista y el motor son
los mismos que TidyTV ya probó en esos modelos; el riesgo nuevo es la integración con el flujo
de Vision+ y el orden copia → limpieza → instalación.

---

## 8. Cómo usarlo

1. En el teléfono: **Ajustes → Instalar en TV**.
2. Activa «Limpiar antes de instalar» (viene activo).
3. Para Android 11+: abre «Emparejar dispositivo con código» en la tele y escribe el código.
   Para Android 8/9: pulsa «Buscar teles en la red» o escribe la IP; acepta «¿Permitir
   depuración?» en la tele.
4. Revisa la lista, marca o desmarca grupos y pulsa **«Limpiar e instalar»**.
5. Al terminar, la tele abre Vision+ Home con Vision+ instalada.
6. Si algo no gusta: **«Restaurar»** reactiva lo apagado y devuelve el Inicio original.

---

## 9. Riesgos y siguientes pasos

- **Firmware imprevisible**: una lista probada en un modelo puede no cubrir una variante. Por
  eso la revisión y la copia son obligatorias, y por eso el flujo no desinstala.
- **Google TV setupwraith**: es la causa clásica de que el Inicio caiga en una pantalla negra.
  Vision+ ya espera el Inicio de respaldo y no lo apaga; TidyTV además apaga setupwraith antes
  de retirar el launcher de fábrica. Queda como mejora probar ese orden en la TCL real.
- **Detección Android 11+**: ya funciona por NSD; el barrido 5555 es solo para 8/9 y teles con
  ADB clásico. Un televisor con «Depuración inalámbrica» cerrada no aparecerá hasta que la
  persona abra el pairing.
- **Siguiente paso recomendado**: probar en la TCL `192.168.1.109` (Android 9/11 según la
  unidad) y en el Chromecast/Google TV, con `logcat` y una captura después de cada lote. Si una
  lista falla, basta con `Restaurar` y anotar el modelo para ajustar la lista.
