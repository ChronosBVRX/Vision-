import type {
  MagicArenaControllerState,
  MagicArenaPlannedAction
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

export interface MagicArenaLayoutModel {
  kind: "magic_arena";
  title: string;
  subtitle?: string;
  helperText?: string;
  language?: "de" | "en";
  disabled: boolean;
  ready?: ReadyLayoutModel;
  resetKey: string;
  currentPlayerId: string;
  state: MagicArenaControllerState;
  onPlanSlot: (
    slotIndex: number,
    action: Omit<MagicArenaPlannedAction, "playerId" | "slotIndex"> | null
  ) => void;
  onReady: () => void;
  /** Sequential mode: submit and immediately execute the current turn. */
  onPlayTurn: (action: Omit<MagicArenaPlannedAction, "playerId" | "slotIndex">) => void;
}
