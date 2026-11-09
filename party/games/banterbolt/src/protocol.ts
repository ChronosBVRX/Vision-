import type { BaseRoundState, PlayerInput } from "@open-party-lab/game-core";

export type BanterboltStage =
  | "write"
  | "vote"
  | "reveal"
  | "scoreboard"
  | "finale_write"
  | "finale_vote"
  | "finale_reveal"
  | "finished";

export type BanterboltPackId = "mexico" | "cine" | "afterdark" | "mixto";

export interface BanterboltPrompt {
  index: number;
  text: string;
  writers: [string, string];
}

export interface BanterboltAnswer {
  text: string;
  writerId: string;
}

export interface BanterboltMatchup {
  promptIndex: number;
  promptText: string;
  answers: [BanterboltAnswer, BanterboltAnswer];
  writers: [string, string];
  voters: string[];
  votes: Record<string, 0 | 1>;
  revealed?: {
    votes: [number, number];
    points: [number, number];
    zapWriterId: string | null;
  };
}

export interface BanterboltFinale {
  promptText: string;
  answers: Record<string, string>;
  submitted: string[];
  votes: Record<string, string>;
  results?: Array<{
    playerId: string;
    text: string;
    votes: number;
    points: number;
    rank: number;
  }>;
}

export interface BanterboltPlayer {
  id: string;
  name: string;
  avatar?: string;
  points: number;
  delta: number;
}

export interface BanterboltState extends BaseRoundState {
  roundId: string;
  stage: BanterboltStage;
  round: number;
  totalRounds: number;
  packId: BanterboltPackId;
  players: BanterboltPlayer[];
  promptsByPlayer: Record<string, BanterboltPrompt[]>;
  answersByPlayer: Record<string, Record<number, string>>;
  submitted: string[];
  matchups: BanterboltMatchup[];
  matchupIndex: number;
  totals: Record<string, number>;
  finale?: BanterboltFinale;
  finishAt: number | null;
  gameScores: Record<string, number>;
  usedPrompts: string[];
  narrator: string;
}

export interface BanterboltPublicMatchup {
  promptIndex: number;
  promptText: string;
  answers: Array<{
    text: string;
    writerId?: string;
    writerName?: string;
  }>;
  votes?: [number, number];
  points?: [number, number];
  zapWriterId?: string | null;
  votedPlayerIds: string[];
  voterIds: string[];
}

export interface BanterboltPublicState {
  roundId: string;
  stage: BanterboltStage;
  round: number;
  totalRounds: number;
  players: BanterboltPlayer[];
  submittedCount: number;
  submittedPlayerIds: string[];
  playerCount: number;
  currentMatchup: BanterboltPublicMatchup | null;
  matchupIndex: number;
  totalMatchups: number;
  finishAt: number | null;
  remainingMs: number | null;
  finale: {
    promptText: string;
    submittedCount: number;
    submittedPlayerIds: string[];
    playerCount: number;
    options?: Array<{ playerId: string; text: string }>;
    answers?: Array<{ playerId: string; text: string; votes: number; points: number; rank: number }>;
    votedCount: number;
  } | null;
  winnerIds: string[];
  narrator: string;
}

export interface BanterboltControllerState extends BanterboltPublicState {
  ownPrompts: BanterboltPrompt[];
  ownAnswers: Record<number, string>;
  ownSubmitted: boolean;
  ownVote?: 0 | 1;
  isWriter: boolean;
  ownFinaleAnswer?: string;
  ownFinaleVote?: string;
}

export type BanterboltInput = PlayerInput & { roundId: string } &
  (
    | { type: "submit_answers"; values: string[]; submit: boolean }
    | { type: "vote"; answerIndex: 0 | 1 }
    | { type: "finale_submit"; text: string }
    | { type: "finale_vote"; targetPlayerId: string }
  );
