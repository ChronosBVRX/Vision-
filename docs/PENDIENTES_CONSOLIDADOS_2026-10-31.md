# Pendientes consolidados — 2026-10-31

Estado del árbol al publicar la beta **2.5.219**. Lo marcado con ✅ ya viaja en
ese APK; lo demás sigue pendiente y se lista para no perderlo entre agentes.

## Deportes en vivo

- ✅ Panel de estadísticas compacto y adaptable (3 cifras, altura según pantalla).
- ✅ Revisión de señales al abrir un partido y cada 30 s mientras Deportes está abierto.
- ✅ Más señales revisadas por pasada y reintento de sonda (Range) para no marcar
  como caída una fuente que responde.
- ✅ Crash al bajar rápido en la lista de fuentes (FocusRequester no inicializado).
- ⏳ Prueba física con mando en tele de 720p y con letra grande.
- ⏳ Confirmar en la tele de la sala que una señal marcada «Caído» por el servidor
  pero viva en GeeSports se corrige al abrir el selector.
- ⏳ Validar el estado de salud del CMS y el aviso de Telegram durante una jornada
  completa.

## Vision+ Play / Fiesta 3.0

- ✅ Temporada larga (6–12 rondas), giros de ronda, tienda, monedas y ruleta.
- ✅ Plató, arte, carga escalonada y voces de lobby/inicio/podio.
- ⏳ Prueba física con 2–3 teléfonos.
- ⏳ Interrogatorio libre y radar de El Impostor.
- ⏳ Relevo de artistas y maleficio de pintura en Dibujo.
- ⏳ Sindicato de Subasta.
- ⏳ Cazador de Fibbage y doble hueco final.
- ⏳ Tutoriales/TTS/SFX de las reglas nuevas en todas las pantallas.
- ⏳ Escuchar y validar la narradora en la tele.

## Launcher / Inicio en la TV

- ⏳ Prueba física en TCL/caja: sintonizador y HDMI abren la app del fabricante.
- ⏳ Confirmar que Vision+ Home sigue siendo el Inicio tras reiniciar en Android 8/9.
- ⏳ Probar el actualizador interno del launcher en una tele con versión vieja.

## App general

- ⏳ Localización: extraer textos a `strings.xml` (hoy están en el código).
- ⏳ Paginación on-demand al abrir categorías del índice.
- ⏳ Fichas del portal con metadatos dudosos (películas marcadas como serie,
  series de un solo capítulo).
- ⏳ Prueba física de «Omitir intro» con controles cerrados y arrastre exacto en móvil.
- ⏳ Probar «Instalar en TV» en una tele Android 8/9 real.

## Servidor / operación

- ✅ Corrección de listas de canales de GeeSports (`link_names`, cruce por equipos,
  desfase UTC, fusión de tarjetas).
- ✅ Backend de Fiesta y juegos desplegado; `/v1/game/catalog` con los once juegos.
- ⏳ Promover una beta probada a OTA estable cuando cierre la prueba física.
- ⏳ Vigilar `/v1/gee38/health` y los avisos de Telegram en jornadas con muchos eventos.
- ⏳ Revisar el canario de actualizaciones y los kill-switches tras la beta.

## Seguridad / P0

- ✅ Filtraciones y liquidaciones de Vision+ Play corregidas (2.5.201).
- ⏳ Prueba física con 2–3 teléfonos de las reglas v2 y del espectador.
- ⏳ Auditoría de contenido editorial masivo (3,465 preguntas) y ampliación de mazos.

## Regla para cerrar

Cada pendiente que se cierre debe actualizar este archivo y `AGENTS.md`, y traer
su prueba (Android, backend o ambas). La beta no se promueve a estable sin la
prueba física correspondiente.
