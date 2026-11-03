# Diagnóstico definitivo: de dónde sale la lista de canales de cada evento de GeeSports

**Fecha:** 2026-10-31 · **APK analizada:** `GeeSports_(V3.8).apk` (`com.live.geesports`) · **Servidor:** `visionplusv2.duckdns.org`

## 1. Lo que hace la app original (verificado en el APK, no supuesto)

La lista del diálogo «Multiple links available» **no** sale del archivo `pro` ni de
los nombres de los enlaces. Sale del campo **`link_names` del evento** dentro de
`events.txt`:

| Archivo / clase | Qué hace |
|---|---|
| `rc.C3824l` (fragmento de eventos) | Lee `events.txt`, descarta `visible=false` y guarda `link_names` en `C4077f.f16193n` con `AbstractC5039b.m10606x(...)`. |
| `AbstractC5039b.m10606x` | Solo devuelve nombres si el arreglo tiene **más de un** elemento. |
| `mc.ViewOnClickListenerC2861d` | Al tocar un evento llama `C3471i.m8003R(..., link_names, links, título, true, esTv)`. |
| `pc.C3471i` | Si `link_names` está vacío, **reproduce la posición 0 sin diálogo**. Si tiene nombres, pinta una fila por nombre y al tocar la fila *i* manda `position=i`. |
| `PlayerActivity` | Recibe `links` (referencia al `pro/<…>.txt`) y `position`; al reproducir resuelve `pro[position]`. |

**Regla:** `link_names` = las filas que ve el usuario; `pro` = las direcciones.
El `pro` puede traer nombres de otro partido, venir parcial o cambiar de índices:
**nunca debe definir la lista**.

## 2. Las cuatro causas reales de que Vision+ enseñara menos listas

### 2.1 Cruce CMS ↔ ESPN demasiado estricto

El CMS y la agenda de ESPN escriben los equipos distinto:

- `Olympique Lyonnais` vs `Lyon`
- `Inter` vs `Internazionale`
- `AC Milan` vs `Milan`
- `Brighton & Hove Albion` vs `Brighton`

El código anterior exigía que los tokens de ambos equipos estuvieran contenidos
en el texto del evento. Al no cumplirse, la tarjeta de ESPN se servía **sin
`link_names`** y el evento del CMS se servía aparte **con** la lista: dos tarjetas
del mismo partido, una vacía y otra con canales. El usuario tocaba la vacía.

### 2.2 Fecha UTC del CMS contra fecha local de México

El CMS publica fecha/hora en **UTC**. Un partido de las **19:05** del día *D* en
México se guarda como **01:05 del día D+1 UTC**. La consulta filtraba por la
fecha cruda del payload (`D..D+days`), así que ese partido no se cargaba y su
tarjeta salía sin lista. Afectaba sobre todo a los partidos de la noche y a los
eventos del día siguiente (los «secundarios»).

### 2.3 Filtro de ligas/deportes

Los eventos del CMS de ligas fuera de la lista corta (LaLiga 2, 2. Bundesliga,
A-League, Ekstraklasa…) y de deportes como Cricket/AFL se descartaban aunque el
CMS publicara sus nombres y direcciones. La app original sí los enseña.

### 2.4 Tarjetas duplicadas sin fusionar

Aun cuando el cruce fallaba, el evento del CMS y la tarjeta de ESPN se servían
por separado. La app pinta una tarjeta por `id`, así que el usuario veía dos
partidos iguales: uno con lista y otro sin ella. La pelea UFC aparecía como
«Boxing» y «Mixed Martial Arts» con listas distintas.

## 3. Corrección aplicada (servidor, sin OTA)

Archivo: `server/api/app.py`.

1. **`_gee_team_similarity` / `_gee_team_pair_similarity`**: parecido de los dos
   equipos con alias, contención y prefijo. Rechaza equipos de la misma ciudad
   («Manchester United» ≠ «Manchester City», «Real Madrid» ≠ «Atlético Madrid»).
2. **`_gee_find_match`**: usa ese parecido, el deporte como candado duro y el
   horario (36 h) + liga como desempate.
3. **Ventana de carga con desfase UTC**: la consulta carga
   `día−1 .. día+days+2` y la respuesta se filtra al rango local pedido. Ningún
   partido de la noche pierde sus metadatos.
4. **Eventos con fuentes siempre se sirven**: si el CMS trae `link_names` o
   direcciones, el evento aparece aunque su liga no esté en la agenda principal
   o su deporte no esté en la lista corta.
5. **`_gee_merge_fixture_cards`**: una sola tarjeta por partido (misma familia de
   deporte + mismo día + ambos equipos parecidos), con la lista más rica y los
   enlaces de todas las copias. Incluye la fusión Boxing/MMA.
6. **`_gee_complete_event_links` se aplica antes de cruzar**: las ranuras
   declaradas sin dirección viajan como «Sin señal» y no se pierden.

## 4. Verificación

- **APK**: rutas de código confirmadas (tabla de arriba).
- **Base real del VPS** (dry-run con el `app.py` nuevo antes de desplegar):
  - AC Milan vs Internazionale → **12 filas** (antes la tarjeta ESPN salía con 0).
  - Lyon vs Angers → **1 fila** y una sola tarjeta.
  - Monterrey vs Tijuana → **7 filas**.
  - Partidos de la noche del día siguiente → ya cargan `link_names`.
  - **0 pares exactos duplicados** en 31-oct, 1-nov y 2-nov.
- **Pruebas**: `server/tests` **619/619** en verde, incluidos los casos de
  alias, desfase UTC, liga no principal, fusión de duplicados y no-fusión de
  equipos distintos.
- **Despliegue**: `api/app.py` copiado al VPS con respaldo previo y contenedor
  `streaming-api` reconstruido.

## 5. Invariantes para no volver a romperlo

1. La lista es `link_names` del evento; el `pro` solo aporta direcciones.
2. El cruce CMS↔ESPN es por **parecido de los dos equipos**, nunca por igualdad
   de tokens.
3. La fecha del CMS es UTC: siempre se carga con margen y se filtra después en
   hora de México.
4. Un evento con fuentes del CMS no se descarta por liga/deporte.
5. Un mismo partido se sirve **una sola vez**, con la lista más completa.
6. Los nombres sin dirección se conservan como ranuras «Sin señal».
