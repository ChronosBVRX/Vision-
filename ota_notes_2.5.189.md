# Vision+ 2.5.189 — Beta: Deportes más completo + integración UX

**Beta para pruebas. No reemplaza la estable 2.5.186.**

## Deportes

### Agenda compacta: hoy y mañana
- La agenda muestra solo **Hoy** y **Mañana**. Los días siguientes ya no ocupan
  espacio en la barra; las ligas y los filtros quedan más a la vista.
- El servidor entrega únicamente esos dos días (`/v1/gee38/events?day=…&days=2`).
- Una selección de día vieja se recorta a mañana; nunca deja la agenda vacía.

### Favoritos
- Nuevo botón **Favorito / Quitar favorito** en el menú de cada partido.
- Al marcar un partido se siguen sus **dos equipos**: sus próximos partidos también
  aparecen en el filtro.
- Chip **Favoritos** en móvil y tele.
- Estrella en las tarjetas de los partidos seguidos.
- Se guarda por perfil, en el equipo; sin cuentas ni servidor.

### Búsqueda
- **Móvil:** lupa en la cabecera de Deportes; busca equipo, liga o canal.
- **Tele:** chip **Buscar** en la fila de filtros que abre el teclado en pantalla de Vision+, pensado para el mando.
- Sin distinguir acentos ni mayúsculas y por palabras: `chivas liga mx`.
- Filtra sobre los eventos ya cargados, sin peticiones extra.

### Recordatorios
- En partidos que aún no comienzan: **10, 30 o 60 minutos antes**.
- Usa el sistema local de avisos de Vision+ (sin push ni cuentas).
- Si ya hay un aviso, aparece **Quitar**.
- No se ofrece en partidos en vivo o terminados.

### Agenda ESPN multideporte
- La agenda ahora también sincroniza **NBA, EuroLeague, NFL, MLB y NHL**.
- Los eventos de GeeSports se cruzan **solo con partidos de su mismo deporte**:
  un partido de básquet no puede quedarse con el marcador de uno de fútbol.
- Las tarjetas de calendario llevan la categoría correcta: Fútbol, Básquet,
  Americano, Béisbol y Hockey.
- Se conserva la regla de una sola fuente para las señales: los enlaces de
  reproducción siguen siendo **solo de GeeSports**.
- `/v1/sports` se mantiene solo para fútbol; la agenda nueva viaja por
  `/v1/gee38/events`.
- **Tenis, peleas, boxeo y MMA**: el soporte de categorías queda listo, pero
  **solo aparecen si GeeSports publica eventos y fuentes** de ese deporte. No se
  inventan partidos ni se rellenan con otras fuentes. Hoy GeeSports no manda
  tenis ni boxeo, así que no se muestran; en cuanto los publique, aparecen solos.
- **Motor completo**: la categoría ya no se llama «F1». Ahora es **Motor** y
  muestra todas las series que manda GeeSports: **Formula 1, MotoGP, NASCAR,
  WRC, WorldWCR…** Cada tarjeta conserva el nombre de su serie.
- Los chips de liga ahora sirven para **todos los deportes**: NBA/NBL/WNBA en
  básquet, NFL en americano, NASCAR/WRC/MotoGP en motor y las ligas de fútbol.

### Atrás en todos los diálogos
- **Todos los diálogos de la app se cierran con la tecla Atrás**: el selector de
  partido, la búsqueda, ajustes, avisos, perfiles, PIN, descargas, fichas y el
  reproductor.
- Se corrigió también el **launcher**: Centro de control, Ajustes, Personalizar,
  guía del Inicio y acciones de tarjeta.
- La tecla se atiende desde la raíz del diálogo, antes que el `BackHandler` de la
  pantalla de atrás; ya no se queda “atrapada” en el modal.

## UX integrada

Se conserva e integra el lote de experiencia de usuario que ya estaba en el árbol:

- Barra superior de la tele con **«En vivo»** como un solo destino y menú
  desplegable **TV en vivo / Deportes en vivo**.
- Pestaña **Juegos** en móvil y tele.
- **Atrás en dos pasos**: primero sube el foco a la barra; después sale.
- **«Seguir viendo»** al tope del Inicio móvil y televisor.
- Hero con velo superior e inferior completo.
- **«Ahora en la tele»** en el móvil y acceso directo al control remoto.
- Búsqueda global, perfiles y fichas con el diseño premium ya integrado.

No se incluye «Compartir partido» ni la importación M3U/JSON.

## Versión

- App **2.5.189 / vc429**.
- Launcher **1.3.5 / vc28** (Atrás en sus diálogos).
- `VisionPlus.apk` **92,100,111 B**, firma histórica `9593cb0d…`, SHA-256
  `7fdc73adf4e45c1166f91d0592e560d993a0727e9d9462635173b8d718a3e005`.

## Evidencia

- Backend: **527 tests**, 0 fallos.
- Android: **1115 tests**, 0 fallos.
- Launcher: **155 tests**, 0 fallos.
- Contrato: **22 tests**, 0 fallos.
- Release R8 compilado y firmado con la llave histórica.

## Cómo probar

1. Instalar `VisionPlus.apk` de la beta.
2. Abrir **Deportes**: la barra de días debe mostrar solo **Hoy** y **Mañana**; cambiar entre los deportes disponibles.
3. Abrir un partido y marcarlo **Favorito**; volver y usar el chip **Favoritos**.
4. Buscar: en el móvil usa la lupa y escribe `america`; en la tele entra al chip **Buscar** y usa el teclado.
5. En un partido próximo, elegir **10 / 30 / 60 min** y comprobar el aviso.
6. En la tele, abrir **En vivo → Deportes en vivo**, entrar a un partido y pulsar **Atrás**: el modal debe cerrarse; repetir en **Buscar**.

— ChronosBVRX
