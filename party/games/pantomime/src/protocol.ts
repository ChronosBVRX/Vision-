import type { BaseRoundState, PlayerInput } from "@open-party-lab/game-core";
export type PantomimeTeamId = "team1" | "team2";
export interface PantomimeInput extends PlayerInput {
  type: "pantomime:reveal" | "pantomime:claim" | "pantomime:skip" | "pantomime:assign" | "pantomime:continue";
  roundId: string;
  turnNumber: number;
  wordToken?: number;
  entryId?: string;
  guesserPlayerId?: string | null;
}
export interface PantomimePlayer { id: string; name: string; teamId: PantomimeTeamId; }
export interface PantomimeEntry {
  id: string; term: string; status: "guessed" | "skipped" | "timeout";
  guesserPlayerId?: string;
}
export interface PantomimeState extends BaseRoundState {
  roundId: string;
  stage: "ready" | "acting" | "review";
  mode: "solo" | "team";
  category: "standard" | "adult" | "all";
  durationMs: number;
  skipLimit: number;
  skipsUsed: number;
  turnNumber: number;
  wordToken: number;
  players: PantomimePlayer[];
  actorPlayerId?: string;
  actorName?: string;
  actorTeamId?: PantomimeTeamId;
  secretTerm?: string;
  finishAt: number | null;
  inputLockedUntil: number | null;
  lastGuess?: { term: string; playerId: string };
  entries: PantomimeEntry[];
  actorMissingSince?: number;
  actorHistory?: string[];
  usedTermIds?: string[];
  gameScores: Record<string, number>;
  teamScores: Record<PantomimeTeamId, number>;
  turnBaseScores: Record<string, number>;
  turnBaseTeamScores: Record<PantomimeTeamId, number>;
}
