import type { BaseRoundState, PlayerInput } from "@open-party-lab/game-core";
import type { GameStateIn, SeatIndex, TimerInfo } from "./merky-sdk.js";
import type { YougotitPublicState } from "./engine/logic.js";

export type YouGotItInput = PlayerInput & { roundId: string } &
  (
    | { type: "submit_clue"; text: string }
    | { type: "move_pointer"; angle: number }
    | { type: "lock_pointer" }
    | { type: "guess_direction"; dir: "left" | "right" }
  );

export interface YouGotItState extends BaseRoundState {
  roundId: string;
  match: GameStateIn;
  timer: TimerInfo | null;
  scores: Partial<Record<SeatIndex, number>>;
  matchOver: boolean;
  seatByPlayer: Record<string, SeatIndex>;
  playerBySeat: Record<number, { id: string; name: string; avatar?: string }>;
  events: Array<{ type: string; payload?: unknown }>;
  narrator: string;
}

export interface YouGotItPublicState extends YougotitPublicState {
  roundId: string;
  phase: string;
  narrator: string;
  timer: TimerInfo | null;
  matchOver: boolean;
  seatPlayers: Array<{ seat: SeatIndex; id: string; name: string; avatar?: string }>;
}

export interface YouGotItControllerState extends YouGotItPublicState {
  seat: SeatIndex;
  playerId: string;
  privateState: Record<string, unknown>;
}
