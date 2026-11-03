# Vision+ 2.5.203 — Etapa B/D: cazador, tutoriales y celebración

**Fecha:** 30 de octubre de 2026  
**Versión:** 2.5.203 / vc443  
**Launcher:** 1.3.15 / vc38

## Qué cambia

Incluye todo lo de **2.5.202** (Deportes: lista completa de fuentes) y **2.5.201** (blindaje P0 de Vision+ Play), más este lote audiovisual.

- **Fibbage — cazador visible:** durante la votación ya se puede elegir una mentira y señalar a su autor para intentar el bono de +300. Antes la acción existía en el servidor, pero no tenía interfaz.
- **Lobby — tutorial visual:** la tarjeta “Cómo se juega” muestra el tablero de tres pasos del juego (`game_tut_*`) junto a la explicación. Se usan los assets ya generados.
- **Podio — celebración:** el podio estrena partículas de confeti en un solo Canvas, limitadas y con perfil de efectos reducidos.
- **Accesibilidad/rendimiento:** `GamePhaseTransition`, `DramaticRevealCard`, la linterna del lienzo y las partículas respetan `LocalReducedEffects`; en modo ligero no se animan.
- **Dirección de arte:** los acentos de Charadas, Apuesta, Impostor, Dibujo, Subasta y Fibbage se unificaron con la paleta del selector v2.
- **v1:** el cazador de Fibbage solo aparece en v2.

## Pruebas

- Android: **1,148 tests** en verde.
- Backend: **590 tests** en verde.
- Build release R8 y motor X4K inyectado.
- `VisionPlus.apk`: **97,641,971 B**, SHA-256 `1ce038a0fb4506ebeda1714b708b0eef7a2375f73da33fbffd03a8d67a45c860`.
- `VisionPlusHome.apk`: **2,824,059 B**, SHA-256 `caebd556c1a7c2cc11354b53f69a2a08a8e80242389821f83a029dcada0e3ba9`.

## Pendiente

- Contenido editorial masivo (auditoría de los 3,465 registros actuales y ampliación de mazos).
- Interrogatorio libre/radar de Impostor, relevo de artistas y sindicato de Subasta.
- Prueba física con 2–3 teléfonos.
- Integración de los 60 audios v2 restantes y de todas las transiciones de fase.

Actualiza desde el aviso de la app o desde Ajustes → «Buscar actualizaciones».

— ChronosBVRX
