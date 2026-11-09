import type { PlayerInput } from "@open-party-lab/game-core";
import type { TransportMode } from "../config.js";

export interface SchattenjagdMovePlayerInput extends PlayerInput {
  type: "move";
  stationId: number;
  mode: TransportMode;
}

export interface SchattenjagdToggleShroudInput extends PlayerInput {
  type: "toggle_shroud";
}

export interface SchattenjagdToggleDoubleMoveInput extends PlayerInput {
  type: "toggle_double_move";
}

export function createMoveInput(
  playerId: string,
  stationId: number,
  mode: TransportMode
): SchattenjagdMovePlayerInput {
  return {
    type: "move",
    playerId,
    stationId,
    mode,
    sentAt: Date.now()
  };
}

export function createToggleShroudInput(playerId: string): SchattenjagdToggleShroudInput {
  return {
    type: "toggle_shroud",
    playerId,
    sentAt: Date.now()
  };
}

export function createToggleDoubleMoveInput(playerId: string): SchattenjagdToggleDoubleMoveInput {
  return {
    type: "toggle_double_move",
    playerId,
    sentAt: Date.now()
  };
}
