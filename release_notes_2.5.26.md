# Vision+ v2.5.26 — Detección Precisa de Canales y Países en Fútbol

Esta actualización resuelve con máxima precisión el emparejamiento de canales de transmisión en vivo para los partidos de fútbol, asegurando que cada opción sintonice la señal correcta de tu región sin cruzarse con señales de otros países ni ediciones numéricas equivocadas.

### ⚽ Novedades y Mejoras Clave

1. **Aislamiento Estricto de Ediciones de Canales**:
   - Resuelto el problema donde cadenas base como `ESPN` o `Fox Sports` sintonizaban erróneamente señales secundarias como `ESPN 2`, `ESPN 3` o `Fox Sports 2/3`.
   - Implementado un reconocedor canónico y clasificador de marcas (`BrandInfo` y `scoreChannel`) que garantiza que cada edición solicitada (`1`/base, `2`, `3`, `premium`, `extra`) se empareje estrictamente con su correspondiente.

2. **Correspondencia y Priorización Rigurosa por País**:
   - Los canales deportivos se evalúan mediante la taxonomía geográfica en vivo (`LiveTaxonomy.countryFor`):
     - **Preferencia Regional**: Las señales de México (`🇲🇽 México`) reciben la máxima prioridad de emparejamiento, seguidas por señales internacionales (`🌐 Internacional`) y de Estados Unidos (`🇺🇸 EE. UU.`).
     - **Protección contra señales sudamericanas cruzadas**: Canales de Argentina (`🇦🇷`), Chile (`🇨🇱`), Colombia (`🇨🇴`), etc., reciben penalización cuando se buscan señales mexicanas, evitando que transmitan programas locales alternativos (como programas de debate o ligas locales) en lugar del partido programado.
     - **Feeds específicos respetados**: Si un partido o cadena especifica una región concreta (ej. `TUDN USA`), se sintoniza con precisión la señal del país indicado.

3. **Insignias Visuales de País con Bandera Nacional**:
   - En el selector de canales tanto de Smart TV (`TvMatchChannelsModal`) como de Móvil (`MobileMatchChannelsDialog`), cada canal incluye ahora una insignia estilizada con la bandera de su país (`🇲🇽 México`, `🇦🇷 Argentina`, `🇺🇸 EE. UU.`, `🇨🇴 Colombia`, `🇨🇱 Chile`, `🌐 Internacional`, etc.), brindando certeza absoluta de qué señal se está eligiendo.

4. **Escaneo EPG Profundo (Título y Sinopsis)**:
   - La confirmación EPG ahora inspecciona tanto el nombre del programa como su descripción detallada (donde los feeds XMLTV colocan los nombres de los equipos y la jornada).
   - Cuando se detecta emisión en curso, el modal muestra el subtítulo dinámico del programa (ej. `⚽ En vivo: [Equipos]` o `Ahora: [Programa]`) y promueve el canal confirmado al primer lugar de la lista.

5. **100% de Pruebas en Verde y Cero Regresiones**:
   - 111 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) y 28 pruebas de backend (`pytest server/tests`) pasando con éxito.
   - Navegación D-pad, retorno con tecla Atrás y carga instantánea (0 ms) preservados intactos.
