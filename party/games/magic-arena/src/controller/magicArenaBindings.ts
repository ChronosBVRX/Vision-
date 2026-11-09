import type {
  MagicArenaPlanSlotInput,
  MagicArenaPlannedAction,
  MagicArenaPlayTurnInput,
  MagicArenaReadyInput
} from "../protocol.js";

export function createMagicArenaPlanSlotInput(
  playerId: string,
  slotIndex: number,
  action: Omit<MagicArenaPlannedAction, "playerId" | "slotIndex"> | null
): MagicArenaPlanSlotInput {
  return {
    type: "magic-arena:plan-slot",
    playerId,
    slotIndex,
    action,
    sentAt: Date.now()
  };
}

export function createMagicArenaReadyInput(playerId: string): MagicArenaReadyInput {
  return {
    type: "magic-arena:ready",
    playerId,
    sentAt: Date.now()
  };
}

export function createMagicArenaPlayTurnInput(
  playerId: string,
  action: Omit<MagicArenaPlannedAction, "playerId" | "slotIndex">
): MagicArenaPlayTurnInput {
  return {
    type: "magic-arena:play-turn",
    playerId,
    action,
    sentAt: Date.now()
  };
}
