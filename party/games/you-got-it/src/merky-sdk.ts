export type SeatIndex = 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11;

export interface SeatPublic {
  seatIndex: SeatIndex;
  displayName: string;
  avatarId: string;
  role: "player" | "spectator";
  connected: boolean;
  abandoned: boolean;
  isHost: boolean;
}

export interface TimerInfo {
  endsAt: number;
  kind: string;
  durationMs: number;
}

export interface GameContext {
  matchId: string;
  roomId: string;
  seats: SeatPublic[];
  settings: Record<string, unknown>;
  now: number;
  rng: () => number;
}

export interface GameStateIn {
  publicState: unknown;
  privateState: Partial<Record<SeatIndex, unknown>>;
  secretState?: unknown;
  phase: string;
}

export interface GameEvent {
  type: string;
  payload?: unknown;
}

export interface ReduceResult {
  publicState: unknown;
  privateState?: Partial<Record<SeatIndex, unknown>>;
  secretState?: unknown;
  phase: string;
  scores?: Partial<Record<SeatIndex, number>>;
  events: GameEvent[];
  timer?: TimerInfo | null;
  matchOver?: boolean;
}

export interface ReduceError {
  error: string;
  code: string;
}

export function isReduceError(result: ReduceResult | ReduceError): result is ReduceError {
  return typeof result === "object" && result !== null && "error" in result;
}

export type GameAction = {
  type: string;
  seat: SeatIndex | "system";
  payload?: unknown;
};
