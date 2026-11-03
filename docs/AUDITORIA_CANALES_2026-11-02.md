# Auditoría de canales Vision+ — 2 de noviembre de 2026

## Alcance y método

- **1,035 canales** de la categoría `tv-en-vivo` en la base del VPS.
- Cruce con **811 normas de EPG vigentes** (805 normas totales, 7 países) y con la
  taxonomía de la app (`LiveTaxonomy.channelKey`, país, género).
- **Prueba en vivo en el Chromecast HD** con la app 2.5.232 y el reproductor real
  (`visionplus://live/<código>`) sobre una muestra dirigida a los casos de riesgo:
  canales con nombre/código contradictorio, Fox, TNT, Sony, Claro, Star, Lifetime,
  AYM y un canal sin guía.
- No se probaron uno por uno los 1,035 streams: la identidad del resto se auditó
  por nombre, código de portal, país, género y EPG. La prueba en vivo se reservó
  para los casos que podían estar entregando otra cosa.

## Resumen ejecutivo

| Métrica | Valor |
|---|---:|
| Canales en `tv-en-vivo` | **1,035** |
| Claves únicas que pinta la app | **944** |
| Canales ocultos por el deduplicado actual | **91** |
| Grupos de duplicados (calidad/feed) | **70** |
| Canales en esos grupos | **161** |
| Canales con EPG vigente | **234 (22.6 %)** |
| Canales sin EPG | **801 (77.4 %)** |
| Canales “sintonizables” (sin 24/7/alquiler) | **937** |
| 24/7 + alquiler dentro de “TV en vivo” | **98 (9.5 %)** |
| Cobertura EPG top 30 / top 60 / top 120 | **93 % / 77 % / 51 %** |
| Normas EPG con programas solapados | **409** |
| Filas EPG solapadas (futuro) | **24,551** |

**Lo más grave:** `TNT SPORTS HD` (código `cyx_TNTSports3`) **no es TNT Sports:
es TNT (entretenimiento)**. La app lo pinta como deporte y el deduplicado de la
parrilla esconde las señales que sí son TNT Sports (`HD+` y `FHD`). Es un cruce
real, no una sospecha.

## 1. Inventario y estructura

### Países inferidos por el nombre

| País | Canales |
|---|---:|
| Sin pista explícita en el nombre | **741** |
| México | 53 |
| Colombia | 40 |
| EE. UU. | 35 |
| Perú | 20 |
| R. Dominicana | 18 |
| España | 16 |
| Chile | 16 |
| Costa Rica | 16 |
| Ecuador | 12 |
| Paraguay | 11 |
| Bolivia / Nicaragua / El Salvador | 9 cada uno |
| Guatemala | 8 |
| Honduras | 7 |
| Puerto Rico | 5 |
| Canadá / Uruguay | 4 cada uno |
| Panamá / Brasil | 1 cada uno |

Los 741 sin pista no están necesariamente mal: la app infiere por marca. Pero el
filtro **País** no tiene una señal explícita para casi tres cuartas partes de la
parrilla.

### Duplicados

- 70 grupos, 161 canales, **91 tarjetas ocultas** por `distinctBy(channelKey)`.
- La mayoría son variantes de calidad (`HD`, `HD+`, `FHD`) de la misma señal.
- **Pero `+` no siempre es calidad**. `LiveTaxonomy.channelKey` elimina `+` como
  signo no alfanumérico, así que colapsa señales distintas:
  - **`WIN Sports COL HD` vs `WIN Sports+ COL HD`** → el usuario pierde Win Sports+.
  - **`TNT SPORTS HD` (TNT) vs `TNT SPORTS HD+` (TNT Sports)** → se queda el
    equivocado y se esconde el correcto.
  - `ANIMAL PLANET HD+` tiene código `cyx_BitMe☆`; es BitMe, no Animal Planet.
    BitMe existe aparte, así que no se pierde el canal, pero el nombre es falso.
- La app conserva el **primero** de cada grupo; en TNT el primero es justo el
  entretenimiento.

## 2. Hallazgos críticos

### P0 — `TNT SPORTS HD` entrega TNT, no TNT Sports

- Nombre: `TNT SPORTS HD`.
- Código: `xtvlive:cyx_TNTSports3`.
- Posición: 629.
- Prueba en vivo: logo y contenido **TNT** (entretenimiento), no deporte.
- El mapa deportivo lo marca como `TNT Sports`, país `LatAm`, y
  `channels_for("TNT Sports")` lo puede ofrecer para un evento de TNT Sports.
- La app lo clasifica como **Deportes** y el deduplicado esconde `TNT SPORTS HD+`
  y `TNT SPORTS FHD`, que sí mostraron **TNT Sports** en vivo.

**Impacto:** un partido de Champions/TNT puede abrir entretenimiento; en TV en
vivo se pierden las señales correctas de TNT Sports.

### P0 — El deduplicado por `channelKey` esconde señales distintas

- `TNT SPORTS HD` / `HD+` / `FHD` comparten clave `tntsports`.
- `WIN Sports COL HD` / `WIN Sports+ COL HD` comparten clave `winsportscol`.
- Se conserva la primera tarjeta, no la mejor ni la que corresponde.
- Las variantes `HD+` que sí son el mismo canal (TNT Sports Chile, Premium,
  Comedy Central, HBO, etc.) no son un problema; el problema son las que cambian
  de contenido.

### P1 — EPG: cobertura y calidad

- **234/1,035** con guía (22.6 %); **801 sin guía**.
- **Top 30: 28/30 (93 %)**; top 60: 46/60 (77 %); top 120: 61/120 (51 %).
- En México faltan guías de canales populares:
  - `a mas+ MEXICO HD`
  - `CANAL 6 MX HD`
  - `Televisa Monterrey`, `TELERITMO MEXICO`, `Shark Tank Mexico`, etc.
- **409 normas tienen programas solapados** y hay **24,551 filas solapadas**
  (futuro). El caso más claro: `mx:sony` tiene dos copias desplazadas una hora
  (`Dioses de Egipto` 09:04–11:19 y 10:04–12:19) y el canal `Sony Central HD`
  mostró en vivo **Betty's Call**, no lo que decía la guía.
- **`NICK MX HD` recibe guía de `co:nick` (Colombia)**, no de México. Es el único
  cruce de país explícito detectado, pero es un cruce real.
- No hay guía para `FOX ONE`; tampoco para TNT Sports; no se inventa, pero el
  usuario no ve programación.

### P1 — TNT Sports: país y feeds

- `TNT SPORTS FHD` y `TNT SPORTS HD+` mostraron **TNT Sports de Chile**
  (Colo Colo, Todos Somos Técnicos).
- `TNT SPORTS PREMIUM HD` y `ESTADIO TNT SPORTS` mostraron **TNT Sports Premium**
  (U. de Chile vs U. Católica).
- El mapa los marca como **LatAm**; el feed real es **Chile**. La app no los
  etiqueta como Chile salvo los que dicen `CHILE` en el nombre.
- `TNT SPORTS CHILE HD/2` sí están bien etiquetados.

### P1 — “TV en vivo” incluye 98 canales que no son lineales

- **94** con `24/7` (maratones de series/películas) y **4** `ALQUILER`.
- No tienen parrilla real (`_epg_schedulable` los excluye).
- Deberían estar marcados como **24/7/VOD** o fuera del bloque de canales, no
  mezclados con señal lineal.

### P1 — Señales sin video en la prueba

- **`CONCERT CHANNELS HD`** (código `cyx-FOXPFAMILYHD`): pantalla negra/sin señal
  en la prueba. El código sugiere Fox Family; el nombre dice Concert; no hay EPG.
  No se puede afirmar qué es hasta que vuelva la señal.
- **`AYM SPORTS MEXICO`**: negro en la prueba de esta mañana; en la prueba de
  campo del 1-nov sí mostró Cuadro Titular. Es intermitente o estaba fuera de
  aire; no es un cruce de identidad, pero sí un riesgo si se recomienda para un
  partido.

### P2 — Códigos heredados que no cambian el contenido

Estos canales **sí entregaron lo que dicen**; el código del portal es viejo y no
debe confundir una corrección:

| Canal | Código | Qué se vio |
|---|---|---|
| `Claro Sports 1 HD` | `cyx-FOXPCOMEDYHD` | Claro Sports / Peloteando |
| `LIFETIME US HD` | `cyx_CineSonyUSA` | Lifetime |
| `ST☆R HD` | `cyx-FoxHD` | Star Channel |
| `TUDN MX HD` | `cyx-UnivisionTDNFHD` | TUDN |
| `Elite Sports Hub HD/2/3/4` | `ST☆R+…_cyx` | ESPN/Star+ (la app los desenmascara) |

No hay que “arreglar” el stream por el código; sí conviene limpiar la metadata.

### P2 — Nombres camuflados que la app no traduce

- `ST☆R HD` se muestra literal; el usuario no ve “Star Channel”.
- `Elite Sports Hub` sí se traduce a `ESPN/Star+`; está bien.
- `FOX ONE` no tiene guía; el nombre es correcto y la ficha Información ya dice
  Fox Corporation/FOX+.

## 3. Lo que sí está correcto (muestra en vivo)

| Canal | Resultado |
|---|---|
| `FOX ONE` | Caliente TV / Fox One, partido TIJ 3–0 CAZ |
| `Fox Sports Premium MX HD` | CMLL, Fox Sports MX (Lauman); no FOX+ |
| `Fox Sports Central HD` | Fox Sports/Lauman, Fox Gol México |
| `Sony Central HD` | Sony Channel / Betty's Call (cine/series) |
| `Claro Sports 1 HD` | Claro Sports / Peloteando |
| `LIFETIME US HD` | Lifetime |
| `ST☆R HD` | Star Channel |
| `TNT SPORTS HD+` / `FHD` | TNT Sports (Chile) |
| `TNT SPORTS PREMIUM HD` / `ESTADIO` | TNT Sports Premium (Chile) |
| `Elite Sports Hub` (app: ESPN) | señal ESPN/Star+ camuflada |

## 4. Recomendaciones

### P0 — antes de otra OTA

1. **Mapa del servidor:** excluir `cyx_TNTSports3` de `sports_channels.json`
   (o marcarlo `TNT`/entretenimiento, no deporte). Que `channels_for("TNT
   Sports")` no devuelva el TNT.
2. **App:** `channelKey` debe conservar el `+` (por ejemplo `+` → `plus`) para no
   colapsar `Win Sports+`, `TNT Sports HD+`, etc.
3. **App:** alias de identidad por código:
   - `cyx_TNTSports3` → `TNT HD` (entretenimiento),
   - `cyx_BitMe☆` → `BitMe`,
   - `cyx-FoxHD` → `Star Channel`,
   - `ST☆R+…` → ESPN (ya existe).
4. **Mapa:** TNT Sports FHD/HD+/Premium/Estadio → **Chile**, no LatAm.

### P1 — guía y salud

5. **Regla de país:** si el nombre dice `NICK MX`, no usar `co:nick`; mejor sin
   guía que guía de otro país.
6. **Solapamientos:** al servir `/v1/epg`, quedarse con una sola fuente por
   norma/ventana (la más reciente o la del feed correcto), no concatenar copias
   desplazadas. `Sony Central` es el caso de prueba.
7. **Cobertura:** agregar guía a `a mas+ MEXICO HD`, `CANAL 6 MX HD` y los
   canales mexicanos populares sin EPG; no marcar cobertura por tener una norma
   si la guía es de otro país.
8. **Salud:** marcar/ocultar `CONCERT CHANNELS HD` mientras no tenga señal;
   monitorear AYM; separar 24/7/alquiler del bloque lineal.
9. **Duplicados:** no deduplicar por nombre si el código/feed es distinto; usar
   una clave que conserve `+`, país y feed.

### P2 — presentación

10. Mostrar `Star Channel` en vez de `ST☆R`, y `TNT`/`BitMe` en vez de nombres
    heredados.
11. En la ficha Información, mantener las notas Fox/AYM/Fox Sports Premium/Sony.

## 5. Conclusión

La parrilla tiene **1,035 canales**, pero no todos son lineales ni todos tienen
guía: **98 son 24/7/alquiler**, **801 no tienen EPG** y **91 tarjetas se ocultan**
por el deduplicado. La identidad de la muestra es mayoritariamente correcta,
incluidos los casos de código heredado (Claro, Lifetime, Star). El fallo que sí
entrega otra cosa es **`TNT SPORTS HD` = TNT**, agravado porque el deduplicado
esconde las señales correctas de TNT Sports.

Antes de otra OTA conviene corregir el mapa de TNT y el `channelKey`; después,
la guía (Sony/Nick/solapamientos) y la salud de canales sin señal.

---

## Correcciones aplicadas en 2.5.233

- `TNT SPORTS HD` (`cyx_TNTSports3`) eliminado del mapa deportivo; la app lo muestra como **TNT HD** (entretenimiento).
- TNT Sports HD+/FHD/Premium/Estadio etiquetados como **Chile**.
- `channelKey` conserva el `+`: Win Sports+ y TNT Sports HD+ ya no se ocultan.
- Alias reales para Star Channel, BitMe, ESPN/Star+ y TNT.
- EPG: país explícito para NICK MX; Canal 6 MX usa `mx:multimedios`; Sony Central ya no recibe `mx:sony`; `_epg_unique` descarta copias solapadas.
- Buscador de TV en vivo con resultados aproximados, predicciones, país y voz.
- Inicio de TV: el scroll vertical solo cambia al cambiar de fila; hero con altura mínima.

---

## Corrección 2.5.234

- Elite Sports Hub se probó en Chromecast: muestra **Disney+**, no ESPN. Eliminado del mapa deportivo (258 canales) y de las fuentes XTV de los partidos.
- GeeSports es la fuente principal; la casa aparece solo como respaldo si GeeSports no tiene señal sintonizable.
- Buscador TV: foco explícito para bajar al teclado y subir a Dictar.
