# PLAN — Lote «UI Premium + firma del autor»

> Estado: **COMPLETADO — OTA v2.5.81 publicada** (2026-10-15; 356 tests en verde; R8 vc317,
> firma histórica). Prueba física en TV pendiente (el A32 no estaba en red al publicar).
> Documento histórico del lote; ver AGENTS.md para el estado vigente.

## Objetivo
Erradicar todo emoji/glifo genérico de la UI, unificar iconografía premium (Material Symbols
Rounded + vectores XML para el reproductor nativo), rediseñar el selector de episodios
(3 superficies) y dejar la huella de ChronosBVRX sin decir el nombre.

## Decisiones cerradas
- Rating: estrella **dorada** `#F5C518` (único acento fuera del azul).
- Episodios en ficha móvil: **lista vertical premium** (miniatura, título, estado).
- Países en Fútbol: **chips ISO** (MX, AR, US, INT).
- Textos de relleno: se eliminan todos.
- **Sin** codename de versión.
- Pista del oficio en «Acerca de»: **sí** (elegante, sin nombre ni hospital).
- Easter egg «Modo Revelado»: **no**.
- Fila «Selección del autor»: **no**.
- Botón «Compartir Vision+» con mensaje firmado: **sí**.
- Pre-roll end-card 2 s: opcional, pendiente de decisión (NO implementado).
- boot_*.mp4 con sello: opcional (NO implementado).

## Tokens de icono
- Tamaños 14/16/20/24 dp. Tintes: `IconAccent`=VisionBlueBright, `IconIdle`=VisionTextSecondary,
  `IconOnBlue`=blanco, `RatingGold`=#F5C518, `SuccessGreen`=#7AD9A8, `LiveRed`=#FF4B4B.
- Reglas: cero emoji en strings de UI; icono+texto en acciones informativas; icono solo en
  universales; mismo icono = mismo significado. Overlines con letter-spacing.

## Mapa emoji → icono (ejecutable)
- Inicio: TOP 10 → badge azul propio; ✨→AutoAwesome; 📈→TrendingUp; ⏱️→PlayCircleOutline;
  ✨col→Collections; 🎬→Movie; 📺→Theaters; 🌸→Animation; 🧸→ChildCare; ★→estrella dorada.
- Fichas: ★/☆→Star/StarBorder; 🔔→Notifications/NotificationsActive; ⬇→Download; 💾→Usb;
  ✓→CheckCircle/DownloadDone; ●temporada→fuera; ←→ArrowBack; →→ChevronRight;
  ✓/○ toggle→CheckCircle/RadioButtonUnchecked.
- Reproductor: ▤→LiveTv; ☰→PlaylistPlay; ⏭→SkipNext; ◉→VolumeUp; □→Subtitles; ⚙→Tune;
  ⏭ Omitir intro→FastForward; 🎬 post-créditos→Movie; ↺→Replay; ✕→Close; 🔠→FormatSize;
  ⬆⬇→VerticalAlignTop/Bottom; ⏱ sincronía→sin icono; ● EN VIVO→punto dibujado con pulso.
- Resto: ✓ menús→Check; ▾→ExpandMore; ◀▶ ajustes→ChevronLeft/Right; 🔍→SearchOff;
  «Ver ▶»→PlayArrow; ★confirmado→CheckCircle; 🇲🇽→chip ISO; 🌐→Public; 💾 chips→Usb/Download.
- NO se toca: `☆` de códigos del proveedor (ST☆R+…) — es dato; logs internos tampoco.

## Selector de episodios (3 superficies)
- Reproductor: cabecera badge + barra de progreso de vistos; temporadas segmented control;
  filas [C#] + título limpio + píldora acción con icono; acento izquierdo azul en la actual;
  foco 2.5dp blanco; hint «Mantén OK para marcar como visto»; spinner «Preparando capítulos…».
  Conservar IDs y nextFocus D-pad y ListView reciclado.
- Ficha TV: carrusel (D-pad) con miniatura = arte de la serie + «C#»; estado con iconos;
  segmented de temporadas; barra de vistos.
- Ficha móvil: lista vertical full-width (miniatura póster 2:3 56×84, título, sub-línea de
  estado, toggle visto con icono); fila siguiente con acento azul.

## Firma del autor (Fase 5)
- Splash: «por ChronosBVRX» + «EST. 2026».
- Tarjeta de carga del reproductor: micro-línea `VISION+ · por ChronosBVRX` (~55% alpha).
- «Acerca de» reescrito: «El autor — Un estudio de una sola persona.» + historia con la pista
  del oficio + firma dibujada (texto + trazo) + sello + créditos estilo cine.
- Botón «Compartir Vision+» con mensaje firmado.
- Notas de OTA firmadas «— ChronosBVRX» (proceso, sin código).

## Fases
0. Infra: VisionIcons.kt + tokens + ~20 vectores `ic_vp_*` + helper píldoras.
1. Inicio móvil/TV (filas, badge TOP 10, rating dorado, isTopRow por id).
2. Fichas móvil/TV (botones, estados, checks, temporadas).
3. Reproductor + selector de episodios (3 superficies).
4. Barrido: búsqueda, ajustes TV, fútbol ISO, miniplayer, recordatorios, live.
5. Firma: splash, carga, «Acerca de», compartir.
6. Guardián anti-emoji + suite + R8 + prueba física + OTA firmada.

## Guardrails
- Sin nombres propios ni hospital; telemetría sin PII; versionName técnico intacto.
- Modal del reproductor: no tocar IDs/nextFocus/setSelection (D-pad).
- Sin reflection nueva (R8). Vectores baratos para TV baja. Cero cambios a sesión/motor/descargas.
