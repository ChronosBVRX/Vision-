import { chaosKommandoText } from "./catalogText.js";
import type { GameManifest } from "@open-party-lab/game-core";

export const chaosKommandoManifest = {
  id: "chaos-kommando",
  translations: chaosKommandoText,
  displayName: "Comando Caos",
  description: "Artillería por turnos de malvaviscos: seis equipos, arenas de islas, minas, cajas y terreno destructible con túneles.",
  minPlayers: 2,
  maxPlayers: 6,
  hostView: "ChaosKommandoHostScene",
  controllerView: "chaos-kommando",
  controllerLayout: "chaos_kommando_controls",
  supportsTeams: false,
  estimatedRoundDurationMs: 600_000,
  roundCompletionMode: "wait_for_ready",
  phaseDurations: {
    roundIntroMs: 1_800,
    countdownMs: 2_200,
    resultMs: 5_200,
    scoreboardMs: 5_000
  },

  ownsScreens: ["round_intro", "result"],
  visual: { accent: "#c4622c", icon: "cannon", eyebrow: "Artillery" },
  audio: { track: { profile: "battle", bpm: 116, rootMidi: 46, masterGain: 0.15 } },
  controllerChrome: { minimal: true },
  broadcast: { controllerStateIntervalMs: 100 },
} as const satisfies GameManifest;

export const manifest = chaosKommandoManifest;
