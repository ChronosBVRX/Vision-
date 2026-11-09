import { schaetzoramaText } from "./catalogText.js";
import type { GameManifest } from "@open-party-lab/game-core";

export const schaetzoramaManifest = {
  id: "schaetzorama",
  translations: schaetzoramaText,
  displayName: "Cifras salvajes",
  description: "Números al aire, estimaciones dudosas y cero miedo al ridículo. Estima, ordena y apuesta.",
  minPlayers: 1,
  maxPlayers: 16,
  hostView: "SchaetzoramaHostScene",
  controllerView: "schaetzorama",
  controllerLayout: "schaetzorama",
  supportsTeams: false,
  estimatedRoundDurationMs: 145_000,
  roundCompletionMode: "wait_for_ready",
  phaseDurations: {
    roundIntroMs: 1_500,
    countdownMs: 2_000,
    lockedMs: 1000,
    resultMs: 50_000,
    scoreboardMs: 5_000
  },

  ownsScreens: ["round_intro", "result"],
  hostChrome: { joinOverlay: false, hud: false, roomCode: false, joinOverlayWhenFinished: false },
  controllerChrome: { hideSubtitle: true, hideScore: true },
  visual: { accent: "#c8873a", icon: "question", eyebrow: "Cifras salvajes" },
  audio: {
    track: { profile: "calmFocus", bpm: 100, rootMidi: 48, masterGain: 0.19, crossfadeSeconds: 3.2 },
    trackByStage: {
      revealed: { profile: "calmReveal", bpm: 104, rootMidi: 48, masterGain: 0.075, crossfadeSeconds: 3.2 }
    }
  },
} as const satisfies GameManifest;

export const manifest = schaetzoramaManifest;
