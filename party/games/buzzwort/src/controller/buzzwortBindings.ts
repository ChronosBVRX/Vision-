import type { BuzzwortInputType } from "../protocol.js";

function createInput(type: BuzzwortInputType, playerId: string, guessedPlayerId?: string) {
  const now = Date.now();

  return {
    type,
    playerId,
    sentAt: now,
    pressedAt: now,
    guessedPlayerId
  };
}

export function createBuzzwortCorrectInput(playerId: string, guessedPlayerId?: string) {
  return createInput("buzzwort_correct", playerId, guessedPlayerId);
}

export function createBuzzwortSkipInput(playerId: string) {
  return createInput("buzzwort_skip", playerId);
}

export function createBuzzwortViolationInput(playerId: string) {
  return createInput("buzzwort_violation", playerId);
}
