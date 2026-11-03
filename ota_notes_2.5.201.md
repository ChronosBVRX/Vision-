# Vision+ 2.5.201 — Blindaje de Vision+ Play (P0)

**Fecha:** 30 de octubre de 2026  
**Versión:** 2.5.201 / vc441  
**Launcher:** 1.3.15 / vc38

## Qué cambia

- **El Impostor:** un espectador ya no recibe la palabra secreta, la categoría ni la pista. El público ve una tarjeta de audiencia.
- **Apuesta a la pista:** la URL completa del póster ya no viaja al jugador en las pistas 1–2. Solo se entrega al comprar la etapa 3 o al revelar. La TV la recibe con el token de host.
- **Charadas:** el actor ya no cobra dos veces cuando acierta por timbre; la ronda se liquida una sola vez en el reveal.
- **Dibujo:** la adivinanza temprana exige que el artista haya empezado a dibujar, usa palabras completas y descarta stopwords como `que`, `una` o `con`. La tinta se valida en el servidor, no solo en la pantalla.
- **v1/v2:** las salas v1 ya no activan cómplice/detective, tinta, linterna, elección de consigna, falsa alarma, ayudas/timbre de Charadas, escáner, veneno, certeza ni cazador.
- **Trivia:** la TV envía un `X-Sync-Code` persistente para que la rotación de preguntas de 90 días vuelva a funcionar.
- **Reportes:** se deduplican por jugador, tienen rate limit y ya no borran preguntas automáticamente; quedan marcadas para revisión editorial.
- **Subasta:** una puja no puede superar el saldo después del redondeo; con menos de 50 monedas no se registra una puja positiva.
- **Fibbage:** el escáner ya no permite votar opciones tachadas desde un cliente modificado.
- **Público:** ya no puede responder Trivia y tiene un tope de 8 espectadores.
- **TV:** `/state?role=display` exige el token de host de la TV.
- **Acciones:** la idempotencia solo memoriza acciones aceptadas; un rechazo ya no se convierte en `True` al reintentar.
- **UI:** Trivia muestra todos los objetivos de ataque y Apuesta todos los objetivos de apuesta lateral, incluso en salas de 8.

## Importante para la tele

Esta versión manda el host token al leer el estado de display. El backend ya fue desplegado con esta versión. Las teles viejas 2.5.200 siguen funcionando por compatibilidad, pero no reciben el póster público de Apuesta hasta actualizar; la app nueva sí lo recibe con su token.

## Pruebas

- Android: **1,148 tests** en verde.
- Backend: **590 tests** en verde.
- Build release R8 con motor X4K inyectado (`classes3/4/5.dex`).
- Firma histórica `9593cb0d…` en app y launcher.
- `VisionPlus.apk`: **97,641,971 B**, SHA-256 `bd5217da285a34593eae6f7020da96bc2b9bc63fe89bf5982a5d836956eda5d5`.
- `VisionPlusHome.apk`: **2,824,059 B**, SHA-256 `caebd556c1a7c2cc11354b53f69a2a08a8e80242389821f83a029dcada0e3ba9`.

## Pendiente

- Prueba física con 2–3 teléfonos.
- Verificar el despliegue del backend y la rotación `trivia_served`.
- Completar la expansión editorial de Fibbage, Subasta, Dibujo e Impostor.
- Integrar tutoriales, transiciones y los 60 audios v2.

Actualiza desde el aviso de la app o desde Ajustes → «Buscar actualizaciones».

— ChronosBVRX
