import { lightTrailsText } from "./catalogText.js";
import type { GameManifest } from "@open-party-lab/game-core";

export const lightTrailsManifest = {
  id: "light-trails",
  translations: lightTrailsText,
  displayName: "Light Trails",
  description: "Lenke deine Spur durch die Arena und ueberlebe am laengsten.",
  minPlayers: 1,
  maxPlayers: 8,
  hostView: "LightTrailsHostScene",
  controllerView: "light-trails",
  controllerLayout: "left_right_hold",
  supportsTeams: false,
  estimatedRoundDurationMs: 40_000,
  roundCompletionMode: "wait_for_ready",
  phaseDurations: {
    roundIntroMs: 1_500,
    countdownMs: 2_400,
    resultMs: 3_500,
    scoreboardMs: 4_000
  },

  ownsScreens: ["round_intro", "result"],
  visual: { accent: "#7a7cad", icon: "bolt", eyebrow: "Arcade" },
  audio: { track: { profile: "chase", bpm: 144, rootMidi: 57, masterGain: 0.16 } },
  broadcast: { supportsHostPatches: true },
} as const satisfies GameManifest;

export const manifest = lightTrailsManifest;
