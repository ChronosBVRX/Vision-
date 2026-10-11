## Novedades de Vision+ v2.5.23

### ⚽ Estabilidad Total en Fútbol y Navegación TV con D-pad
- **Eliminación de crasheos por scroll rápido en Fútbol**: Corregido el cierre inesperado al navegar velozmente hacia arriba o abajo con el control remoto por el menú de partidos de fútbol y ligas.
- **Blindaje de memoria gráfica y decodificación de logos (`VisionApp.kt`)**:
  - Se configuró Coil con `.allowHardware(!useModestMemory)` en Smart TV y dispositivos de rendimiento modesto (Chromecast con Google TV, Fire TV, TV Boxes). Al desplazarse a gran velocidad entre partidos con decenas de escudos de equipos provenientes de la CDN de ESPN, la GPU Mali agotaba los descriptores nativos `AHardwareBuffer` / `GraphicBuffer`, provocando fallos de segmentación (`SIGSEGV` en `libhwui.so`). Ahora los logos se decodifican en memoria RAM estándar (Dalvik heap protegido de 512 MB).
  - Implementado muestreo descendente estricto (`size(56, 56)`) en todas las peticiones de escudos (`TvSportsScreen` y `SportsScreen`), evitando decodificaciones a tamaño completo y multiplicando la velocidad de dibujado.
  - Añadido indicador de iniciales de equipo mientras el escudo termina de descargarse, evitando saltos visuales en blanco.
- **Eliminación del secuestro de foco durante el desplazamiento**:
  - En `TvSportsScreen.kt`, se retiró `state.links.size` del `LaunchedEffect` de foco inicial. Previamente, la llegada asíncrona de canales candidatos o confirmados disparaba de nuevo el `landing.request()` hacia la primera tarjeta (incluso cuando ya había salido de pantalla por el scroll del usuario), corrompiendo la navegación de Compose y congelando el cursor.
- **Tarjetas con altura fija de 86 dp y eliminación de "Jitter" en `LazyColumn`**:
  - Se fijó `height(86.dp)` en `TvMatchCard` y se estructuró `TvChannelInfo` en 3 líneas delimitadas con elipsis (`maxLines = 1`). Esto suprime las variaciones bruscas de altura (de 70 dp a 110 dp) que desestabilizaban las mediciones de la lista durante el scroll rápido.
- **Memoización y claves seguras anti-colisión**:
  - `byLeague` ahora se memoiza con `remember(state.matches)`, eliminando re-agrupaciones síncronas costosas por cada evento de foco o recomposición.
  - Claves compuestas robustas en `items` (`league_${leagueId.ifBlank { "idx_$leagueIndex" }}_$leagueIndex` y `m_${match.id}`) con salvaguarda `firstOrNull()`.
- **Protección preventiva global**: La configuración de memoria gráfica optimizada resguarda de forma idéntica la navegación rápida por los más de 1000 canales de TV en Vivo y las repisas de películas y series.
- **Cero regresiones**: Confinamiento de foco en modales (v2.5.21), arranque instantáneo en Chromecast (v2.5.22), carga en 0 ms y reproducción intacta. 105 tests unitarios de Android y 28 tests de backend al 100% en verde.
