# Vision+ para Roku

Canal nativo de Roku (BrightScript + SceneGraph) para el catálogo y la TV en vivo de **Vision+**.

No es un APK envuelto ni depende de Android: el Roku habla directamente con el VPS de
Vision+ para el catálogo y con el portal Xuper/XTV para resolver la reproducción, igual
que el port de webOS. No incluye el motor X4K/Titan, que es ARM/bionic y no puede correr
en Roku.

Toma como base el cliente Roku de ejemplo que compartiste (mismo protocolo Xuper y misma
idea de proxy HLS), pero el canal está reimplementado en SceneGraph limpio y conectado al
catálogo real de Vision+ (`/v1/*`), no al kit del revendedor.

## Estado

- Versión **1.0.0**.
- Compila con `brighterscript 0.73.8`: **0 errores**.
- Validado contra las respuestas reales del VPS y del portal Xuper.
- **Pendiente**: prueba en un Roku físico. No hay emulador oficial de Roku; el ZIP se
  sideloadea en un equipo con modo desarrollador.

## Interfaz

La estructura visual sigue la TV de Vision+:

- **Barra superior de 62 dp** (equivalente FHD): marca Vision+ / TV, pestañas Inicio,
  En vivo, Juegos, Películas, Series y Mi lista, con menús para TV/Deportes,
  Series/Anime/Novelas y Mi lista; hora, perfil, buscador y ajustes a la derecha.
- **Héroe cinematográfico** con arte panorámico de `/v1/banners`, badge, título,
  metadatos, sinopsis y acciones Reproducir / Detalles / Favorito.
- **Tarjetas 2:3** con esquinas redondeadas, badge de estado, calificación con estrella,
  número Top 10 y borde/escala de foco.
- **Paleta oficial**: `#070B13`, `#2D7DF6`, `#6BA6FF`, `#F4F7FC`, `#9BA8BD`,
  rating `#F5C518` y vivo `#FF4B4B`.

Roku SceneGraph no permite una réplica pixel-perfect de Compose (blur, gradientes
animados, recorte redondeado nativo). Se usan máscaras PNG, arte 9-patch y foco
D-pad para reproducir la misma jerarquía, medidas y lenguaje visual.

## Qué incluye

| Área | Implementación |
|---|---|
| Inicio | Héroe, filas de Tendencias, Estrenos, Recomendados, TV en vivo, temáticas y géneros |
| TV en vivo | Categorías reales del portal, 1,000+ canales, EPG AHORA/DESPUÉS del VPS y zapping |
| Películas / Series / Novelas / Anime / Infantil | Filas por género/sección desde `/v1/section/*` |
| Búsqueda | Búsqueda indexada en el VPS (`/v1/search`) con teclado de Roku |
| Ficha | Sinopsis, año, duración, score, géneros, temporadas y capítulos |
| Reproducción VOD | `startPlayVOD` + `getSlbInfo` + CDN directo (TS/HLS) con `Content-Auth`/`Content-License` |
| Reproducción en vivo | `startPlayLive` + CDN HLS + proxy local de manifiesto en el puerto 8888 |
| Subtítulos | SRT externos del portal, con ciclo y ajuste fino de sincronía |
| Mi lista | Continuar viendo, favoritos, ver más tarde e historial local |
| Perfiles | 5 perfiles con almacenamiento separado (Principal, Infantil, Invitado, 4 y 5) |
| Deportes | Agenda GeeSports del VPS, señales XTV y enlaces HLS sin DRM |
| Vision+ Play | Catálogo de juegos y reglas; la sala se crea desde el móvil |
| Ajustes | Cuenta Xuper, perfiles, PIN +18, borrar historial, diagnóstico, acerca de y actualización |
| Diagnóstico | Modelo, Roku OS, resolución, códecs, sesión Xuper y endpoint del VPS |

## Arquitectura

```
Roku (SceneGraph)
  ├── /v1/index, /v1/section, /v1/search, /v1/episodes, /v1/gee38/events, /v1/epg/bulk
  │       └── VPS de Vision+ (X-Api-Key de solo lectura)
  ├── /api/portalCore/v3/snToken + /v8/active (sesión Xuper)
  ├── /v15/getSlbInfo + /v4/startPlayLive + /v10/startPlayVOD
  │       └── CDN directo (TS/HLS), sin motor X4K
  └── Proxy HLS local (roStreamSocket, puerto 8888) para estabilizar el vivo
```

La sesión Xuper se guarda en el registro de Roku y se renueva sola. El canal no usa la
sesión del teléfono ni el motor de Android.

## Instalar

1. En el Roku: **Ajustes → Sistema → Modo desarrollador** y activa el modo.
2. Anota la IP del Roku.
3. Genera el ZIP:

   ```bash
   python3 roku/pack.py
   ```

   El resultado queda en `roku/dist/VisionPlus-roku-1.0.0.zip`.

4. Abre `http://<IP-DEL-ROKU>` en el navegador, entra a **Development Application
   Installer**, elige el ZIP y pulsa **Install**.
5. El canal aparece en la pantalla de inicio de Roku como **Vision+**.

Para actualizar, repite el paso 4 con el ZIP nuevo.

## Configuración

- VPS: `roku/VisionPlus/source/core.brs` → `VPS_Base()` y `VPS_ApiKey()`.
- Portal Xuper: `roku/VisionPlus/source/xuper.brs` → `X_Hosts()`, `X_KeyHex()` y
  `X_SnSecret()`.
- Puerto del proxy HLS: `roku/VisionPlus/components/Tasks/ManifestProxy.brs` →
  `ProxyPort()`.

La llave del VPS es la misma de solo lectura que ya viaja en el APK de Vision+; no es una
llave de administración. No publiques el ZIP con claves modificadas ni compartas la
configuración del portal.

## Límites reales de Roku

Estas funciones de Vision+ **no se portaron** porque Roku no las permite o exigen otra
arquitectura:

- **Descargas/offline**: Roku no ofrece almacenamiento persistente para video de un canal
  sideloadeado.
- **Vision+ Home / launcher**: Roku no permite que un canal reemplace la pantalla de inicio.
- **Vision+ Play (juegos)**: el catálogo de juegos se ve en el canal, pero la sala se crea y
  se juega desde la app móvil; Roku no tiene el motor de salas/QR de esta entrega.
- **Continuidad entre equipos y control remoto**: el protocolo existe en el VPS, pero esta
  entrega no lo cableó; se puede añadir después.
- **OTA**: Roku no actualiza solo un canal sideloadeado; hay que reinstalar el ZIP.
- **DASH/ClearKey**: el nodo `Video` de Roku no reproduce todos los enlaces DASH con DRM de
  GeeSports. En Deportes se muestran primero las señales XTV y los HLS sin DRM.
- **HEVC**: muchas fichas del portal solo ofrecen H.265 720p. Un Roku sin HEVC mostrará el
  aviso de compatibilidad; se necesita un Roku 4K para esos títulos.
- **EPG**: depende de que el VPS tenga guía para ese canal; si no, la tarjeta no inventa
  programación.

## Validar sin Roku

```bash
cd roku/VisionPlus
bsc --project bsconfig.json
python3 ../pack.py --skip-validate
```

`bsc` valida BrightScript y SceneGraph. Para probar la lógica de red sin el equipo se puede
usar la misma sesión Xuper desde Node/Python y comparar contra el VPS; el canal no requiere
que el teléfono esté encendido.

Verificado en esta entrega:

- `brighterscript 0.73.8`: **0 errores**.
- `/v1/index`, `/v1/section/*`, `/v1/search`, `/v1/episodes`, `/v1/gee38/events` y
  `/v1/epg/bulk`: respuestas reales del VPS.
- `v3/snToken → v8/active → v6/getLiveData → v15/getSlbInfo → v4/startPlayLive`: sesión y
  URL de vivo resueltas contra el portal.
- `v10/startPlayVOD` de película y de capítulo: pista, licencia y CDN resueltos.
- ZIP final con `manifest` en la raíz y 97 archivos.

## Legal

Vision+ es una marca de ChronosBVRX. El catálogo y la reproducción dependen de servicios de
terceros; la legalidad de su uso depende de que el servicio tenga los derechos
correspondientes. Este canal no redistribuye contenido: solo lo reproduce desde el mismo
origen que la app.
