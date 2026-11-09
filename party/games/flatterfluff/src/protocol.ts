import type { PlayerInput } from "@open-party-lab/game-core";

export type FlatterfluffMode = "timed" | "endless";
export type FlatterfluffTargetKind = "scout" | "flier" | "captain" | "jumbo" | "golden" | "crate";
export type FlatterfluffEventKind =
  | "shot"
  | "miss"
  | "empty"
  | "hit"
  | "armor"
  | "reload"
  | "reload-complete"
  | "ammo";

export interface FlatterfluffAimInput extends PlayerInput {
  type: "aim";
  aimX: number;
  aimY: number;
}

export interface FlatterfluffShootInput extends PlayerInput {
  type: "shoot";
}

export interface FlatterfluffReloadInput extends PlayerInput {
  type: "reload";
}

export type FlatterfluffInput =
  | FlatterfluffAimInput
  | FlatterfluffShootInput
  | FlatterfluffReloadInput;

export interface FlatterfluffTargetState {
  id: string;
  kind: FlatterfluffTargetKind;
  x: number;
  y: number;
  radius: number;
  scale: number;
  lane: number;
  points: number;
  hp: number;
  maxHp: number;
  facing: -1 | 1;
}

export interface FlatterfluffPlayerState {
  playerId: string;
  name: string;
  color: string;
  aimX: number;
  aimY: number;
  ammo: number;
  maxAmmo: number;
  reloadEndsAt: number | null;
  score: number;
  hits: number;
  shots: number;
  streak: number;
}

export interface FlatterfluffGameEvent {
  id: number;
  kind: FlatterfluffEventKind;
  at: number;
  x: number;
  y: number;
  playerId?: string;
  targetId?: string;
  points?: number;
}

export interface FlatterfluffState {
  mode: FlatterfluffMode;
  roundDurationMs: number | null;
  elapsedMs: number;
  remainingMs: number | null;
  serverNow: number;
  targets: FlatterfluffTargetState[];
  players: FlatterfluffPlayerState[];
  events: FlatterfluffGameEvent[];
  totalHits: number;
}
