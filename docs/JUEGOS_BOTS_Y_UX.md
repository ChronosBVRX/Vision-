# Bots competitivos y controlador por páginas

## Bots

El servidor puede sentar **bots como jugadores reales** (ocupan slot, cuentan para el mínimo/máximo,
aparecen en el roster y en el marcador). No usan socket: el `BotManager` les inyecta inputs por el
mismo `handleInput` que un celular.

### Cómo se usan

En el lobby, quien tiene el control del anfitrión entra a **Anfitrión → Jugadores → Bots**:

- Elige dificultad **Normal**, **Difícil** o **Experto** (por defecto Difícil).
- **+1 bot** / **+2 bots** para sentarlos.
- **Quitar** en cada bot, o la × del roster.

Solo aparece en los juegos con adaptador; el servidor rechaza el resto con un mensaje claro.

### Juegos con bots hoy

| Juego | Qué hace el bot |
|---|---|
| **Light Trails** | Raycast de estelas y paredes, espacio libre con lookahead y evasión de choques frontales. |
| **Flatterfluff** | Elige el objetivo de mayor valor, corrige la mira y dispara; recarga solo. |
| **Arena Survivor** | Kiting de enemigos, esquiva proyectiles, recoge vida/materiales, compra y resuelve level-ups/shop/forja/cofre. |
| **Comando Caos** | Resuelve balística con gravedad y viento, elige el mejor ángulo/carga y dispara por turnos. |

Los juegos de texto, dibujo o actuación (Banterbolt, Zeichnen, Pantomime, etc.) no tienen bots
todavía: un bot ahí sería un tramposo o un compañero aburrido, no un rival.

### Arquitectura

```
apps/server/src/bots/
  botManager.ts        # altas, memoria por sala/bot, tick y limpieza
  botRegistry.ts       # gameId -> adaptador + BOT_GAME_IDS
  types.ts             # contrato BotAdapter / BotThinkContext
  random.ts            # RNG determinista por sala/bot/ronda
  adapters/
    lightTrailsBot.ts
    flatterfluffBot.ts
    arenaSurvivorBot.ts
    chaosKommandoBot.ts
```

Puntos de integración:

- `packages/protocol`: `PlayerSnapshot.isBot/botDifficulty`, `AvailableGameDto.supportsBots` y eventos
  `bot:add` / `bot:remove`.
- `PlayerManager.addBot()` crea el `PlayerRecord` sin socket y `connected: true`.
- `GameRuntime.applyPlayerInput()` aplica input sin transición ni broadcast.
- `RoundTimerService` llama `botManager.stepRoom()` antes del tick del juego; el tick hace un solo
  broadcast.
- `roomLifecycle.toRoomSnapshot()` publica `isBot`; `RoomCleanupService` no cuenta bots como sala viva.

### Agregar un adaptador

1. Crea `apps/server/src/bots/adapters/<juego>Bot.ts` implementando `BotAdapter`.
2. Lee solo el estado autoritativo y emite inputs con `context.input({ type, ... })`.
3. Respeta los rangos que valida el juego y usa `thinkEveryMs` para no saturar (40–120 ms).
4. Regístralo en `botRegistry.ts`; `supportsBots` y la UI se encienden solos.
5. Añade el juego a `scripts/smoke-bots.mjs` o crea su propio smoke.

## Controlador por páginas

El lobby dejó de ser una lista vertical:

- **Jugar / Sala** para quien no es anfitrión.
- **Juego / Ajustes / Jugadores** para quien tiene el control.
- Selección de personaje en **grid de 3 columnas** con retrato, nombre y arquetipo; el detalle del
  elegido aparece debajo, no en una tarjeta gigante por opción.
- Selección de juego en **grid de 2 columnas** con portada.
- Ajustes del lobby agrupados en **tabs por grupo** (por ejemplo, las 20+ reglas de Card Table ya no
  se apilan).
- Los bots viven en **Jugadores**, junto al roster.

## Verificación

```bash
npm run typecheck
npm run build:hosted
SMOKE_SERVER=https://party.visionplusv2.duckdns.org npm run smoke:bots
```

El smoke crea una sala, sienta dos bots Expert, arranca Light Trails, comprueba que se mueven y
sobreviven, quita uno y verifica que un juego sin adaptador rechaza la petición.

## Alcance y pendientes

- Es un cambio **solo web**: se despliega en el VPS, no requiere OTA del APK.
- Faltan páginas dentro de algunos layouts largos (tienda de Arena Survivor, torres de Minions TD,
  tablero de Magic Arena). El siguiente paso natural es declarar `pages` en esos controladores y
  reutilizar el mismo patrón de tabs.
