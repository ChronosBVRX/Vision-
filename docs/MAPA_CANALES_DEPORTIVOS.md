# Mapa de canales deportivos XTV + cruce con la agenda ESPN

Mapa propio de las **263 señales deportivas/deportivas-generalistas** de la
parrilla XTV (de 1,035 canales), con país, marca, feed y dueño. Es una **segunda
opción** junto a la agenda de GeeSports: no la reemplaza ni la modifica.

## Lo importante: ESPN sí está, como «Elite Sports Hub»

En la parrilla XTV **no existe ningún canal llamado ESPN**. Los cuatro feeds de
ESPN/Star+ se publican camuflados:

| Nombre en XTV | Código | Identidad real | Guía |
|---|---|---|---|
| Elite Sports Hub HD | `ST☆R+HD_cyx` | **ESPN / Star+ HD** | `mx:espn` |
| Elite Sports Hub 2 HD | `ST☆R+ 2 HD_cyx` | **ESPN 2 / Star+ 2 HD** | `mx:espn2` |
| Elite Sports Hub 3 HD | `ST☆R+ 3 HD_cyx` | **ESPN 3 / Star+ 3 HD** | `mx:espn3` |
| Elite Sports Hub 4 HD | `ST☆R+ 4 HD_cyx` | **ESPN 4 / Star+ 4 HD** | `co:espn4` |

Evidencia:

1. **El código**: `ST☆R+…` es la marca interna de Star+ (Disney), no de un canal
   llamado Elite.
2. **La app**: `X4KApi.displayChannelName()` los muestra como «ESPN / Star+ HD»
   y `epgNameFor()` pide la guía como `ESPN`, `ESPN 2`, `ESPN 3` y `ESPN 4`.
3. **La guía en vivo (2026-11-01)**: con el nombre real + código, el VPS responde
   `mx:espn` → *Live: Fútbol Americano NFL*, `mx:espn2` → *Live: SportsCenter*,
   `mx:espn3` → *Swiss Indoors Basilea* y `co:espn4` → *Fútbol LaLiga*. Sin el
   alias (`Elite Sports Hub HD`) no hay guía: el VPS solo conoce la identidad
   ESPN.
4. **El logo**: el portal entrega un PNG que dice «Elite Sports Hub» a propósito;
   es el disfraz, no la identidad de la señal.

Conclusión: **sí tienes ESPN en tus canales nativos**, pero el portal lo rotula
como Elite Sports Hub. El mapa lo deja explícito en `displayName`, `aliases`,
`camouflaged: true` y `epgNorm`.

## Prueba de campo 2026-11-01: el portal no es lo que dice

Se probaron los canales en vivo con un partido real (**Xolos vs Atlas**). El
resultado corrige los rumores de la comunidad:

| Canal en XTV | Lo que realmente se ve | Lectura |
|---|---|---|
| **FOX ONE** | **Xolos vs Atlas** | **Feed de eventos de Fox Corporation; verificado** |
| **AYM Sports México** | **Cuadro Titular** | Bloque de estudio/lineal de FOX One; **no los partidos** |
| **Fox Sports Premium MX HD** | **Peleas** | Fox Sports Premium de **Grupo Lauman**; **no es FOX+** |
| **Sony Central HD** | **Películas** | Canal de cine de Sony; **no es ESPN** |

Por lo tanto:

- **FOX ONE** es el feed correcto para los eventos de Fox Corporation
  (Xolos vs Atlas lo confirma). El mapa lo marca `verified: true`.
- **AYM** puede emitir bloques de FOX One (`Cuadro Titular`), pero **no** es el
  feed del partido: `notEventFeed: true`.
- **Fox Sports Premium** es la señal vieja de Lauman (peleas); **no** es FOX+:
  `notFoxPlus: true`, `legacy: true`.
- **Sony Central** es cine; no entra al mapa deportivo ni a ESPN.
- Los rumores “AYM = FOX One”, “Fox Sports Premium = FOX+” y “Sony Central =
  ESPN 1” eran **parciales o falsos**. La prueba real manda.

La **API pública de FOX One** (`foxone.mx/calendariofoxone`) confirma eventos
como Champions, Bundesliga, Premier, Ligue 1, Liga MX Femenil, NFL, NASCAR y
LMP; su parrilla lineal del día mostró *FOX 365*, *Cuadro Titular* y partidos
femeniles. **No** listó el Xolos vs Atlas de la señal lineal, así que la prueba
de campo es la confirmación más fuerte para el feed de XTV.

### Qué canal nuevo de FOX es cada uno

| Canal nuevo | Qué emite | Evidencia | Espejo en XTV |
|---|---|---|---|
| **FOX** (básico) | NFL, NASCAR, Liga MX selecta, **Cuadro Titular**, **FOX 365**, El Chiringuito | EPG público de FOX (`FOXCAL`): Chargers–Rams, Pachuca–Tigres, Cuadro Titular/FOX 365 | **AYM Sports** (verificado por Cuadro Titular) |
| **FOX+** (premium) | Champions, Liga MX, Premier, Bundesliga, Ligue 1 | Fox One event API + **Xolos vs Atlas** | **FOX ONE** (verificado) |
| **FOX | Tubi** (gratuito) | NFL, Premier, Liga MX, NASCAR, Cuadro Titular | EPG público de FOX|Tubi (`FENTUBI`): Xfinity 500, Everton–ManU, Tijuana–Cruz Azul, Querétaro–Pachuca, Necaxa–Santos, Fiorentina–Juventus | **sin espejo XTV confirmado** |
| **FOX One** (plataforma/eventos) | Eventos + canales lineales | API Fox One + Xolos vs Atlas | **FOX ONE** |

**Cuadro Titular** es un programa de estudio de **FOX (básico)**, no un partido.
El EPG de FOX lo pone a las 21:00 y 23:00 CDMX (03:00/05:00 UTC del 2-nov),
mientras que AYM estaba mostrando Cuadro Titular: por eso AYM = FOX básico. El
mismo programa también aparece en FOX|Tubi a las 02:00 y 12:20 CDMX, pero a la
hora de la prueba AYM coincidía con FOX, no con Tubi.

Para eventos futuros:

- Evento en **FOX básico** → **AYM**.
- Evento en **FOX+** → **FOX ONE**.
- Evento en **FOX|Tubi** → sin espejo XTV confirmado; se marca “sin canal XTV”.
- Evento solo en **FOX One** → **FOX ONE**.
- **Fox Sports Premium** (Lauman) = peleas; no es FOX+.
- **Sony Central** = películas; no es ESPN.

El bloque `foxLinear` de `/v1/sports/channels` expone esta relación con
`mirror`/`mirrorName`/`note`, y `sports_map.channels_for("FOX")` devuelve AYM,
`channels_for("FOX+")` devuelve FOX ONE, `channels_for("FOX | Tubi")` vacío y
`channels_for("FOX One")` devuelve FOX ONE.

## Cómo se usa el mapa

### 1. Inventario completo

```
GET /v1/sports/channels
GET /v1/sports/channels?country=México&general=0
GET /v1/sports/channels?brand=fox
GET /v1/sports/channels?q=elite
```

Campos por canal: `name` (nombre XTV), `displayName` (identidad real cuando el
portal la camufla), `code`, `country`, `brand`, `feed`, `owner`, `general`
(emisora generalista que también transmite deporte), `aliases`, `epgNorm`, `verified`, `verifiedBy`, `rights`, `notEventFeed`, `notFoxPlus`, `legacy`.

### 2. Opciones por partido

```
GET /v1/sports/options?days=2
GET /v1/sports/options?days=2&country=EE.%20UU.
```

Por cada partido devuelve:

- `broadcasts`: cada emisora que publica ESPN con su tipo (`channel`/`ott`), país
  y los canales XTV que le corresponden **en todos los países**.
- `available`: la unión de canales XTV que sí tienes, ordenada por país
  preferido (México primero).
- `alternatives`: las señales hermanas de otros países (p. ej. `TUDN MX` ↔
  `TUDN USA`).
- `noXtv`: `true` si el evento solo va por OTT y no hay canal en la parrilla.

Ejemplos reales del mapa:

- **América / Chivas en Liga MX**: ESPN publica `TUDN`, `Univision`, `ViX`; el
  mapa devuelve `TUDN MX` (México), `TUDN USA` (EE. UU.), `Univision` (EE. UU.)
  y `ViX Deportes` (México). Si no tienes el canal mexicano, tienes la señal
  hermana de EE. UU.
- **Chivas solo por Prime Video**: `Prime Video` se marca `ott` y **sin canal
  XTV**; si el mismo evento lista una emisora de EE. UU. (`Telemundo`,
  `Universo`), el mapa la ofrece como alternativa. Si no la lista, `noXtv=true`
  (honesto: no se inventa una señal).
- **Fox**: `Fox Sports` se separa por país y dueño; `Fox Sports Central/2/3/
  Premium MX` → México (Lauman); `FOX SPORTS 1/2 USA`, `Fox Deportes` y
  `Fox Soccer Plus` → EE. UU. (Disney); `FOX ONE` → **México (Fox Corporation)**; es el feed de eventos, verificado con Xolos vs Atlas.

## Cobertura

- 263 señales en total: **182 deportivas** + **81 emisoras generalistas** que
  también transmiten deporte (Canal 5, Azteca 7, Telemundo, Univision…).
- Países: México, EE. UU., LatAm, Colombia, Ecuador, Chile, Bolivia, Costa Rica,
  Perú, R. Dominicana, Canadá, Nicaragua, El Salvador, Honduras, Puerto Rico,
  Reino Unido, Guatemala, Uruguay, España, Panamá e Internacional.
- Marcas principales: MLB Pass, NFL Pass, TNT Sports, Bally Sports, NBA Pass,
  Telemundo, Fox Sports, DAZN, Win Sports, Claro Sports, ESPN/Elite, TUDN,
  TVC Deportes, Gol Perú, AYM Sports, Fox Deportes, ViX Deportes, etc.
- **No están en XTV** (el mapa las marca sin canal): TyC Sports, beIN Sports,
  Sky Sports, CBS Sports Network, Space, NBCSN y las OTT Prime Video,
  Paramount+, Peacock, Max, Disney+, fuboTV, Fanatiz, izzi, Sky+.

## Regenerar el mapa

La fuente es la lista `tv-en-vivo` del VPS. Se exporta el TSV y se corre:

```bash
python3 tools/build_sports_map.py parrilla.tsv server/api/sports_channels.json
```

El generador no toca GeeSports ni la base: solo clasifica nombres/códigos y
escribe el JSON que sirve `/v1/sports/channels`.

## Segunda opción en la app: «En tu TV»

Desde la 2.5.232 el selector de un partido muestra primero los canales XTV
propios y debajo, sin modificarlas, las señales de GeeSports. El servidor añade
un campo **aditivo** `xtv` a `/v1/gee38/events`; la app no pide nada nuevo.

- **Recomendación solo con confirmación**: Fox One se confirma con el calendario
  público de Fox One y FOX básico con el EPG FOXCAL (AYM es el espejo). Si no
  hay confirmación, se muestra la posibilidad con nota «sin confirmación de
  EPG», sin marcarla como recomendada.
- **Fox One** = FOX+ (eventos); **AYM** = FOX básico, no FOX+; **Fox Sports
  Lauman/Star** nunca se cruzan con los derechos del Fox nuevo; **FOX | Tubi**
  no tiene espejo XTV y no se inventa.
- **OTT fuera**: Prime Video, Paramount+, etc. no se ofrecen como canal XTV.
- La agenda y los enlaces de GeeSports no se tocaron: la segunda opción es
  independiente.

### Fuentes de confirmación

| Fuente | Uso | Estado |
|---|---|---|
| Calendario público Fox One (`foxone-cached` GraphQL) | FOX+ / FOX ONE | La API responde en equipos locales, pero **403 desde el VPS**; se empaqueta `fox_one_schedule.json` con la ventana vigente. |
| EPG FOX básico FOXCAL (Viewlift/TMS) | AYM | El key TMS de Viewlift está «Developer Inactive»; se empaqueta `fox_basic_epg.json` (40 eventos, hasta 17-nov-2026). |
| Prueba de campo 1-nov | Xolos vs Atlas → FOX ONE | Verificada. |

La app instala el APK con las **notas de identidad** en la ficha Información:
FOX ONE (Fox Corporation), AYM (FOX básico), Fox Sports Premium (Lauman) y
Sony Central (cine, no deporte).
