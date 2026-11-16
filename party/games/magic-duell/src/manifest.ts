import { catalogText } from "./catalogText.js";
import type { GameManifest } from "@open-party-lab/game-core";
import {
  defaultMageDuelLoadoutSpellIds,
  getMageDuelShapeIconPath,
  mageDuelLoadoutSize,
  mageDuelMinLoadoutSize,
  mageDuelSpellCatalog
} from "./protocol.js";

const spellAccentByColor = {
  red: "#ef4444",
  blue: "#3b82f6",
  green: "#22c55e"
} as const;

const spellSecondaryByColor = {
  red: "#fecaca",
  blue: "#bfdbfe",
  green: "#bbf7d0"
} as const;

const spellSetupOptions = mageDuelSpellCatalog.map((spell) => ({
  id: spell.id,
  name: spell.displayName,
  title: `${spell.manaCost} maná | ${(spell.cooldownMs / 1000).toFixed(1)}s de enfriamiento`,
  description: spell.description,
  iconPath: getMageDuelShapeIconPath(spell.shape),
  visual: {
    primaryColor: spellAccentByColor[spell.color],
    secondaryColor: spellSecondaryByColor[spell.color],
    accentColor: spellAccentByColor[spell.color]
  }
}));

export const magicDuellManifest = {
  id: "magic-duell",
  translations: catalogText,
  displayName: "Duelo Mágico",
  description: "Un duelo de magos en 3D con hechizos dibujados en el celular.",
  minPlayers: 2,
  maxPlayers: 2,
  hostView: "MageDuel3dHostScene",
  controllerView: "magic-duell",
  controllerLayout: "spell_casting",
  supportsTeams: false,
  estimatedRoundDurationMs: 120_000,
  roundCompletionMode: "wait_for_ready",
  playerSetup: {
    kind: "multi-select",
    selectionKey: "spellLoadout",
    title: "Elige hechizos",
    description: "Elige los hechizos que quieres usar en tu celular.",
    required: true,
    minSelections: mageDuelMinLoadoutSize,
    maxSelections: mageDuelLoadoutSize,
    defaultValue: defaultMageDuelLoadoutSpellIds,
    readyBlockedLabel: "Elige tus hechizos primero",
    options: spellSetupOptions
  },
  phaseDurations: {
    roundIntroMs: 1_600,
    countdownMs: 2_200,
    lockedMs: 2_200,
    resultMs: 5_000,
    scoreboardMs: 5_000
  },

  ownsScreens: ["round_intro", "result"],
  visual: { accent: "#8c5f86", icon: "wand", eyebrow: "Duel" },
  audio: { track: { profile: "mystery", bpm: 100, rootMidi: 47, masterGain: 0.14 } },
} as const satisfies GameManifest;

export const manifest = magicDuellManifest;
