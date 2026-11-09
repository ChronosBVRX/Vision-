import { schattenjagdCatalogText } from "./text.js";
import type { GameManifest } from "@open-party-lab/game-core";
import { schattenjagdRoomSettingKeys, schattenjagdSetupConfig } from "./config.js";

export const schattenjagdManifest = {
  id: "schattenjagd",
  translations: schattenjagdCatalogText,
  displayName: "Sombra fugitiva",
  description: "Una sombra se oculta y se mueve por la ciudad. Los detectives deben cerrar el cerco antes de que escape.",
  minPlayers: 3,
  minPlayersBySetting: {
    settingKey: schattenjagdRoomSettingKeys.shadowMode,
    values: { ai: 2 }
  },
  maxPlayers: 8,
  hostView: "SchattenjagdHostScene",
  controllerView: "schattenjagd",
  controllerLayout: "choice",
  supportsTeams: false,
  estimatedRoundDurationMs: 900_000,
  roundCompletionMode: "wait_for_ready",
  lobbySetup: {
    title: "Prepara la fuga",
    description: "Decide quién interpreta la sombra. Todos los jugadores restantes investigan juntos.",
    fields: [
      {
        kind: "select",
        id: "shadowMode",
        settingKey: schattenjagdRoomSettingKeys.shadowMode,
        actionKey: "shadowMode",
        label: "sombra",
        description: "Aleatorio, por asiento o completamente controlado por el servidor.",
        defaultValue: schattenjagdSetupConfig.shadowMode.defaultValue,
        options: [
          { id: "random", label: "Jugador aleatorio", description: "Un jugador será sorteado en secreto." },
          { id: "seat", label: "Asiento fijo", description: "Jugador con el número de asiento elegido." },
          { id: "ai", label: "IA del servidor", description: "Todos los jugadores investigan contra la IA." }
        ]
      },
      {
        kind: "select",
        id: "turnTime",
        settingKey: schattenjagdRoomSettingKeys.turnTime,
        actionKey: "turnTime",
        label: "Duración del turno",
        description: "Tiempo disponible para cada turno de la Sombra o de los detectives.",
        defaultValue: schattenjagdSetupConfig.turnTime.defaultValue,
        options: [
          { id: "20", label: "20 segundos", description: "Ritmo muy rápido." },
          { id: "30", label: "30 segundos", description: "Ritmo ágil." },
          { id: "45", label: "45 segundos", description: "Equilibrado." },
          { id: "60", label: "60 segundos", description: "Relajado." },
          { id: "90", label: "90 segundos", description: "Mucho tiempo para pensar." },
          { id: "off", label: "Sin límite de tiempo", description: "Sin cuenta atrás: se espera a que cada jugador cierre su turno." }
        ]
      },
      {
        kind: "number",
        id: "shadowSeat",
        settingKey: schattenjagdRoomSettingKeys.shadowSeat,
        actionKey: "shadowSeat",
        label: "Asiento de la Sombra",
        description: "Solo se usa con asiento fijo. El asiento 1 es quien se unió primero.",
        min: schattenjagdSetupConfig.shadowSeat.min,
        max: schattenjagdSetupConfig.shadowSeat.max,
        step: schattenjagdSetupConfig.shadowSeat.step,
        defaultValue: schattenjagdSetupConfig.shadowSeat.defaultValue
      }
    ]
  },
  phaseDurations: {
    roundIntroMs: 2_000,
    countdownMs: 2_400,
    resultMs: 7_000,
    scoreboardMs: 5_000
  },

  ownsScreens: ["round_intro", "result"],
  visual: { accent: "#5d6f80", icon: "ghost", eyebrow: "Fuga nocturna" },
  audio: { track: { profile: "mystery", bpm: 96, rootMidi: 45, masterGain: 0.12 } },
} as const satisfies GameManifest;

export const manifest = schattenjagdManifest;
