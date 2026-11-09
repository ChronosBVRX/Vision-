import { buzzwortText } from "./catalogText.js";
import type { GameManifest } from "@open-party-lab/game-core";

export const buzzwortManifest = {
  id: "buzzwort",
  translations: buzzwortText,
  displayName: "Palabra maldita",
  description: "Explica el término sin soltar las palabras que queman. Un guardián vigila cada error.",
  minPlayers: 3,
  maxPlayers: 20,
  hostView: "BuzzwortHostScene",
  controllerView: "buzzwort",
  controllerLayout: "secret_card",
  supportsTeams: true,
  estimatedRoundDurationMs: 420_000,
  roundCompletionMode: "wait_for_ready",

  ownsScreens: ["round_intro", "result"],
  visual: { accent: "#5b7f9c", icon: "chat", eyebrow: "Labia maldita" },
  audio: { track: { profile: "arcade", bpm: 124, rootMidi: 57, masterGain: 0.13 } },
} as const satisfies GameManifest;

export const manifest = buzzwortManifest;
