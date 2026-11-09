import type { BaseRoundState, PlayerInput } from "@open-party-lab/game-core";
import type { GameStateIn, SeatIndex, TimerInfo } from "./merky-sdk.js";
import type { MerkissionerPrivateState, MerkissionerPublicState, Vote } from "./engine/types.js";

export type MerkissionerInput = PlayerInput & { roundId: string } &
  (
    | { type: "ready_up" }
    | { type: "nominate"; targetSeat: number }
    | { type: "cast_vote"; vote: Vote }
    | { type: "discard_decree"; index: number }
    | { type: "enact_decree"; index: number }
    | { type: "propose_veto" }
    | { type: "resolve_veto"; agree: boolean }
    | { type: "use_power"; targetSeat?: number }
  );

export interface MerkissionerState extends BaseRoundState {
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

export interface MerkissionerPublicView extends MerkissionerPublicState {
  roundId: string;
  phase: string;
  timer: TimerInfo | null;
  matchOver: boolean;
  seatPlayers: Array<{ seat: SeatIndex; id: string; name: string; avatar?: string }>;
  narrator: string;
}

export interface MerkissionerControllerView extends MerkissionerPublicView {
  seat: SeatIndex;
  playerId: string;
  privateState: MerkissionerPrivateState;
}
