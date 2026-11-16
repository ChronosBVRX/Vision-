import type {
  MageDuelGestureColor,
  MageDuelGesturePoint,
  MageDuelGestureShape,
  MageDuelSpellId
} from "../protocol.js";

export interface ReadyLayoutModel {
  currentPlayerReady: boolean;
  readyCount: number;
  playerCount: number;
  label: string;
  description?: string;
  language?: "de" | "en";
  onToggleReady: () => void;
}

export interface SpellCastingSpellModel {
  id: MageDuelSpellId;
  label: string;
  description: string;
  color: MageDuelGestureColor;
  shape: MageDuelGestureShape;
  iconPath: string;
  manaCost: number;
  cooldownMs: number;
  cooldownRemainingMs: number;
  castMs: number;
  disabled: boolean;
}

export interface SpellCastingDuelistModel {
  playerId: string;
  name: string;
  color: string;
  hp: number;
  maxHp: number;
  mana: number;
  maxMana: number;
  shieldHp: number;
  shieldRemainingMs: number;
  activeCastLabel?: string;
  lastGesture?: {
    spellLabel: string;
    confidence: number;
    reason: string;
  };
}

export interface SpellCastingLayoutModel {
  kind: "spell_casting";
  title: string;
  subtitle?: string;
  helperText?: string;
  language?: "de" | "en";
  disabled: boolean;
  ready?: ReadyLayoutModel;
  resetKey: string;
  nextSpellReadyMs: number;
  nextSpellTotalMs: number;
  selectedSpellId: MageDuelSpellId | null;
  self: SpellCastingDuelistModel | null;
  opponent: SpellCastingDuelistModel | null;
  spells: SpellCastingSpellModel[];
  actionLog: string[];
  onCastGesture: (spellId: MageDuelSpellId, points: MageDuelGesturePoint[]) => void;
}
