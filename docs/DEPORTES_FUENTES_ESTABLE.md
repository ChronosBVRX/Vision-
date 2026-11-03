# Deportes · contrato de fuentes por evento (para no volver a tocarlo)

## La regla que manda

La app original de GeeSports obtiene la lista de canales de cada evento de:

- **`events.txt` → `link_names`**: los nombres y el orden de las filas que ve el usuario.
- **`pro/<…>.txt`**: las direcciones reales; al tocar la fila *i* reproduce `pro[i]`.

El archivo `pro` **no define la lista**. Puede traer nombres de otro partido, venir
parcial o cambiar de índices. Por eso Vision+ nunca debe usar los nombres del `pro`
como fuente de la UI: los nombres salen del evento y los enlaces se alinean por
nombre/ranura.

## Cruce CMS ↔ agenda (la causa de las listas vacías)

El CMS y ESPN escriben los equipos distinto («Olympique Lyonnais» / «Lyon»,
«Inter» / «Internazionale», «AC Milan» / «Milan»). El cruce **no** puede exigir
tokens exactos: usa `_gee_team_similarity` / `_gee_team_pair_similarity`
(alias, contención y prefijo) y exige parecido de **los dos** equipos. Así no
confunde Manchester United con Manchester City ni Real Madrid con Atlético.

Además, el CMS publica la fecha en **UTC** y la app pide en hora de México. Un
partido de las 19:05 del día D se guarda como 01:05 del D+1 UTC: la consulta
carga `D−1 .. D+days+2` y la respuesta se filtra al rango local pedido. Si se
vuelve a filtrar solo por la fecha cruda, los partidos de la noche vuelven a
salir sin lista.

Un evento del CMS con `link_names` o direcciones **siempre** se sirve, aunque su
liga no esté en la agenda principal o su deporte no esté en la lista corta.
Después se fusionan las tarjetas del mismo partido (`_gee_merge_fixture_cards`):
una sola tarjeta, con la lista más rica y todos los enlaces. Los nombres sin
dirección siguen como «Sin señal».

## Pipeline (servidor)

1. **Ciclo completo** (`sync/gee38.py::sync`): cada 30 min baja grupos y eventos,
   fusiona lecturas parciales de `events.txt` y no poda si el archivo vino a medias.
2. **Metadatos** (`_refresh_event_metadata`): cada **2 min** consulta
   `events.txt` con `If-None-Match`; si el CMS responde **304** no se descarga ni
   procesa nada (solo se sella `meta_at`). Si cambió, actualiza
   `link_names`/`links_ref` y baja el `pro` de los eventos que cambiaron.
3. **Enlaces calientes** (`_refresh_hot_links`): cada **5 min** renueva y sondea
   los enlaces de la ventana (hoy primero, luego los más viejos; los eventos con
   menos filas que nombres entran primero). Deja `hot_at`.
4. **API** (`api/app.py::gee38_events`): al servir vuelve a alinear cada evento;
   una fila por nombre declarado, en el orden del evento, conservando nombres
   repetidos y dos nombres distintos que compartan URL. Los nombres sin dirección
   se sirven como ranuras `pending` («Sin señal») y no se sintonizan.

## Invariantes que no se deben romper

- `link_names` es la lista; `pro` son las direcciones.
- El cruce CMS↔ESPN es por parecido de los DOS equipos, no por tokens exactos.
- La fecha del CMS es UTC: se carga con margen y se filtra después en hora de México.
- Un evento con fuentes no se descarta por liga/deporte.
- Un mismo partido se sirve una sola vez, con la lista más completa.
- Nunca se reemplaza una lista guardada por una copia más pobre del CMS.
- Los enlaces se emparejan por **nombre actual**, no por slots viejos.
- Se conservan las repeticiones del mismo evento (p. ej. dos «ESPN HD»).
- Un evento con `link_names` siempre devuelve una fila por nombre, aunque falte URL.
- La UI no muestra el nombre de la fuente; las no disponibles dicen «Sin señal».
- La app pide eventos frescos al abrir Deportes y al volver del reproductor.

## Consumo medido (para que nadie lo confunda con una saturación)

- `events.txt` cifrado: **374,588 B**. El chequeo normal con ETag es **304** y no
  baja cuerpo; una descarga real tarda ~0.16 s y la segunda consulta ~0.02 s.
- Cada `pro/*.txt` ronda **2 KB** (muestra de 20: media 1,996 B, máx 4.5 KB).
- El pase de enlaces (60 eventos) baja ~120 KB de `pro` + ~400 KB de sondeos cada
  5 min. Es el mismo pase que ya existía.
- Estimación conservadora del CMS: **8–22 GB/mes** (≈25–70 kbps medios), muy por
  debajo de los 10 TB/mes del plan del VPS.
- La app recibe `/v1/gee38/events` comprimido: **~30 KB** para 163 eventos. Abrir
  Deportes y refrescar cada 5 min no llega ni a 1 MB por sesión normal.
- CPU del latido de metadatos: ~0.02–0.16 s cada 2 min; RAM y disco no cambian de
  forma apreciable. El ciclo grande de catálogo es el que consume CPU, no esto.

## Guardianes automáticos

- `server/tests/test_gee38.py`: slots, nombres desplazados, listas que crecen,
  duplicados, tarjetas de agenda, endpoint de salud.
- `server/tests/test_diag.py`: aviso humano cuando `events.txt` deja de refrescarse.
- `app/.../GeePicksTest.kt`, `GeePickDialogDuplicateTest.kt`,
  `GeeSportsSourceTest.kt`: filas completas, pendientes no sintonizables, sin
  atribución a GeeSports.

## Salud y alertas

`GET /v1/gee38/health` (requiere `X-Api-Key`):

```json
{
  "ok": true,
  "events": 2125,
  "withNames": 1988,
  "withLinks": 2069,
  "rawMismatched": 625,
  "groups": 32,
  "metaAt": "2026-10-31T15:51:33",
  "hotAt": "2026-10-31T15:52:02",
  "fullAt": "...",
  "ageSeconds": 31,
  "toleranceSeconds": 900
}
```

- `ok` mide la **frescura de `meta_at`** (≤ 15 min) y que existan eventos con
  nombres. `rawMismatched` es informativo del payload crudo: la API completa las
  filas al servir y los tests fijan esa garantía.
- `sync/diag.py::gee38_health` avisa por Telegram si `meta_at`/`hot_at`/`full_at`
  superan 15 min, con enfriamiento de 2 h. El aviso dice qué revisar y enlaza al
  endpoint; no espera a que un usuario reporte fuentes faltantes.

## Si algún día hay que diagnosticar

1. `curl -H "X-Api-Key: …" https://visionplusv2.duckdns.org/v1/gee38/health`
2. `docker compose logs --tail 120 sync | grep -i gee38`
3. Confirmar que el worker está vivo: `docker compose ps sync`.
4. Si `metaAt` está viejo: revisar `events.txt`, el dominio (Firebase RC) y la clave
   del CMS; el worker ya rota dominio/clave solo.
5. Si `rawMismatched` crece pero `ok=true`: es normal en payloads viejos; la API
   los completa al servir. Si la app mostrara menos filas, revisar el build y los
   tests de los guardianes de arriba.

## Regla para futuras sesiones

No cambiar el modelo «nombres del evento + `pro` por ranura» por el modelo
«nombres del `pro`» ni volver a deduplicar solo por URL. Eso fue exactamente lo
que hacía que Vision+ enseñara menos fuentes que la app original.
