import { zeichnenUndErratenText } from "./catalogText.js";
import type { GameManifest } from "@open-party-lab/game-core";

export const zeichnenUndErratenManifest = {
  id: "zeichnen-und-erraten",
  translations: zeichnenUndErratenText,
  displayName: "Garabato mental",
  description: "Dibujos feos, mentes retorcidas y un celular de por medio. Traza, adivina y ríete del desastre.",
  minPlayers: 2,
  maxPlayers: 20,
  hostView: "ZeichnenUndErratenHostScene",
  controllerView: "zeichnen-und-erraten",
  controllerLayout: "drawing_guess",
  supportsTeams: false,
  estimatedRoundDurationMs: 95_000,
  roundCompletionMode: "wait_for_ready",
  lobbySetup: {
    title: "Prepara tu garabato",
    description: "Elige qué palabras pueden salir en la ronda.",
    fields: [
      {
        kind: "select",
        id: "category",
        settingKey: "zeichnenUndErratenWordCategory",
        actionKey: "category",
        label: "Palabras",
        defaultValue: "standard",
        options: [
          {
            id: "standard",
            label: "Familiar",
            description: "Palabras familiares para dibujar."
          },
          {
            id: "adult",
            label: "18+",
            description: "Palabras picantes para grupos sin niños."
          },
          {
            id: "all",
            label: "Todo mezclado",
            description: "Mezcla familiar y picante."
          }
        ]
      },
      {
        kind: "number",
        id: "roundSeconds",
        settingKey: "zeichnenUndErratenRoundSeconds",
        actionKey: "roundSeconds",
        label: "Tiempo para adivinar (0 = sin límite)",
        description: "Cuánto tiempo tienen para adivinar el garabato.",
        min: 0,
        max: 180,
        step: 15,
        defaultValue: 90
      }
    ]
  },
  phaseDurations: {
    roundIntroMs: 800,
    countdownMs: 900,
    resultMs: 4_000,
    scoreboardMs: 4_000
  },

  ownsScreens: ["round_intro", "result"],
  visual: { accent: "#4f7d8c", icon: "brush", eyebrow: "Garabato" },
  audio: { track: { profile: "gentle", bpm: 90, rootMidi: 62, masterGain: 0.11 } },
  hostChrome: { hud: false, roomCode: false },
  controllerChrome: { bare: true, hideSubtitle: true, hideScore: true },
} as const satisfies GameManifest;

export const manifest = zeichnenUndErratenManifest;
