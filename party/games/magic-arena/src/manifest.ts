import { catalogText } from "./catalogText.js";
import type { GameManifest } from "@open-party-lab/game-core";
import {
  MAGIC_ARENA_DEFAULT_PLANNING_MODE,
  magicArenaRoomSettingKeys
} from "./server/magicArenaConfig.js";

export const magicArenaManifest = {
  id: "magic-arena",
  translations: catalogText,
  displayName: "Hexagonia",
  description: "Hexágonos, hechizos y una arena que no perdona errores. Planea en secreto, lanza magia y sobrevive al cierre.",
  minPlayers: 2,
  maxPlayers: 4,
  hostView: "MagicArenaHostScene",
  controllerView: "magic-arena",
  controllerLayout: "magic_arena",
  supportsTeams: false,
  estimatedRoundDurationMs: 240_000,
  roundCompletionMode: "wait_for_ready",
  lobbySetup: {
    title: "Configura Hexagonia",
    description: "Elige cómo se juegan los movimientos.",
    fields: [
      {
        kind: "select",
        id: "planningMode",
        settingKey: magicArenaRoomSettingKeys.planningMode,
        actionKey: "planningMode",
        label: "Planificación de trenes",
        defaultValue: MAGIC_ARENA_DEFAULT_PLANNING_MODE,
        options: [
          {
            id: "planned",
            label: "Planifique con anticipación",
            description: "Los cuatro movimientos se planifican de forma encubierta y luego se ejecutan juntos."
          },
          {
            id: "sequential",
            label: "Tren en tren",
            description: "Los jugadores pueden jugar de otras maneras: el movimiento se puede realizar inmediatamente."
          }
        ]
      }
    ]
  },
  phaseDurations: {
    roundIntroMs: 1_600,
    countdownMs: 1_600,
    lockedMs: 3_000,
    resultMs: 5_000,
    scoreboardMs: 5_000
  },

  ownsScreens: ["round_intro", "result"],
  visual: { accent: "#6f6aa0", icon: "sparkles", eyebrow: "Hexagonia" },
  audio: { track: { profile: "battle", bpm: 104, rootMidi: 48, masterGain: 0.15 } },
} as const satisfies GameManifest;

export const manifest = magicArenaManifest;
