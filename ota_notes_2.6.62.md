# Vision+ 2.6.62 — Deportes a la medida de cada pantalla

Actualización sobre 2.6.61.

## Agenda de Deportes responsiva

- La agenda ahora se calcula por **ventana**, no por tipo de aparato: teléfono vertical, teléfono horizontal y tablet comparten el mismo código y cada uno recibe su tamaño.
- **Teléfono vertical:** tarjetas compactas de 126 dp y hasta 2 columnas legibles (3 en pantallas grandes).
- **Teléfono horizontal:** cabecera y chips más ajustados para ganar altura; la rejilla llega a 5 columnas sin miniaturas ilegibles.
- **Tablet:** márgenes, tipografía y tarjetas crecen; la rejilla usa 3–5 columnas según el ancho real.
- **Tablet horizontal:** tarjetas de 192 dp, 32 dp de margen y hasta 8 columnas en ventanas grandes.
- Los chips de filtro, la fila de días/ligas, las cabeceras de sección y la pestaña **Canales** escalan con la misma regla.
- La tarjeta de partido separa liga y estado en dos filas para que no se recorten en anchos pequeños; el pie y los escudos mantienen su proporción.
- La TV conserva su pantalla propia (`TvSportsScreen`): este cambio es solo móvil/tablet y no toca el D-pad de la tele.

## Se conserva de 2.6.61

- El módulo de **fuentes 4K externas** sigue activo: cruce por TMDB/IMDb, validación HLS 2160p, caché de 6 h, revisión cada 6 h y caída automática a Xuper si la fuente no responde.
- La cadena de respaldo de calidad y el ranker de pistas de 2.6.60 siguen igual.

## Verificación

- Compilación `:app:compileNormalDebugKotlin` correcta.
- **1,352 pruebas** ejecutadas; solo quedan los **3 fallos ambientales conocidos** de la suite local, los mismos de 2.6.61.
- Nuevas pruebas: `SportsLayoutTest` (teléfono, tablet, horizontal y techo de 8 columnas).
- No se toca el motor X4K, las licencias, los perfiles ni la firma.

— ChronosBVRX
