import { flatterfluffText } from "./catalogText.js";
import type { GameManifest } from "@open-party-lab/game-core";

export const flatterfluffRoomSettingKeys = {
  mode: "flatterfluffMode",
  roundSeconds: "flatterfluffRoundSeconds"
} as const;

export const flatterfluffManifest = {
  id: "flatterfluff",
  translations: flatterfluffText,
  displayName: "Dulce puntería",
  description:
    "Malvaviscos alados, miras de colores y dedos calientes. Apunta, dispara y recarga antes de que se escapen.",
  minPlayers: 1,
  maxPlayers: 6,
  hostView: "FlatterfluffHostScene",
  controllerView: "flatterfluff",
  controllerLayout: "virtual_joystick",
  supportsTeams: false,
  estimatedRoundDurationMs: 90_000,
  lobbySetup: {
    title: "Prepara la puntería",
    description: "Elige una cacería cronometrada o una ronda abierta sin fin.",
    fields: [
      {
        kind: "select",
        id: "mode",
        settingKey: flatterfluffRoomSettingKeys.mode,
        actionKey: "mode",
        label: "Modo de cacería",
        defaultValue: "timed",
        options: [
          {
            id: "timed",
            label: "Cacería cronometrada",
            description: "Recoge tantos puntos como puedas antes de que se acabe el tiempo."
          },
          {
            id: "endless",
            label: "Sin fin",
            description: "Juega sin límite de tiempo hasta que el anfitrión finalice la ronda."
          }
        ]
      },
      {
        kind: "number",
        id: "roundSeconds",
        settingKey: flatterfluffRoomSettingKeys.roundSeconds,
        actionKey: "roundSeconds",
        label: "Duración de la cacería",
        description: "Cuánto dura la cacería, en segundos.",
        min: 45,
        max: 180,
        step: 15,
        defaultValue: 90
      }
    ]
  },
  phaseDurations: {
    roundIntroMs: 1_500,
    countdownMs: 1_800,
    lockedMs: 2_500,
    resultMs: 4_500,
    scoreboardMs: 4_500
  },

  ownsScreens: ["round_intro", "result"],
  visual: { accent: "#c98a9a", icon: "bird", eyebrow: "Puntería dulce" },
  audio: { track: { profile: "sugarCountry", bpm: 108, rootMidi: 50, masterGain: 0.13 } },
  controllerChrome: { bare: true },
} as const satisfies GameManifest;

export const manifest = flatterfluffManifest;
