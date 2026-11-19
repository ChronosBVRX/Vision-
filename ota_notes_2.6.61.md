# Vision+ 2.6.61 — Fuentes 4K que se revisan y actualizan solas

Actualización sobre 2.6.60.

## Módulo 4K externo

- Vision+ ahora consulta un índice externo de pistas **4K/2160p**, las valida y las guarda en el equipo.
- El cruce es por **TMDB/IMDb** y título/año: nada de nombres a medias.
- Solo se aceptan pistas que declaran 2160p/4K/UHD o HLS cuyo manifiesto tiene una variante 4K real.
- Se descartan CAM/TS y las pistas duplicadas; se prioriza **audio latino** y las URLs que funcionan fuera de una red local.
- La ficha muestra **4K disponible** cuando hay una fuente fresca.
- Al dar Play, Vision+ intenta el 4K **dentro del mismo reproductor**; si la fuente no responde, cae a la máxima calidad nativa de Xuper en el minuto en que iba, sin salir de la app.
- Aviso discreto «4K · proveedor» al entrar y «Máxima calidad nativa» si hay respaldo.

## Revisión constante sin gastar de más

- Las fichas abiertas se revisan en segundo plano cada **6 horas**, con red y sin batería crítica.
- Una respuesta positiva se guarda **6 h**; una vacía, **45 min**. Las URLs respetan su propio vencimiento del CDN.
- El worker renueva como máximo 15 títulos por pasada y no repite los que ya están frescos.
- El resto del catálogo se completa al abrir cada ficha.

## Telemetría

- `EXTERNAL_4K_OK`: fuente encontrada y validada (incluye cuántas son locales).
- `EXTERNAL_4K_FALLBACK`: la fuente externa se agotó y se cayó a Xuper.

## Verificación

- Compilación `:app:compileNormalDebugKotlin` correcta.
- **1,347 pruebas** ejecutadas; solo quedan los **3 fallos ambientales conocidos** de la suite local, los mismos de 2.6.60.
- Nuevas pruebas: `External4KParserTest`, `External4KStoreTest` y `External4KWiringTest`.
- No se toca el motor X4K, las licencias, los perfiles ni la firma.

— ChronosBVRX
