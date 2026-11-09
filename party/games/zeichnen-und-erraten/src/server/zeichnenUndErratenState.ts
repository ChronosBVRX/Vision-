import type { BaseRoundState, GameHostThemeName, PlayerInput } from "@open-party-lab/game-core";
import type { ZeichnenUndErratenWordCategory } from "../protocol.js";

export interface DrawingPoint {
  x: number;
  y: number;
}

export interface DrawingStroke {
  id: string;
  clientStrokeId?: string;
  color: string;
  points: DrawingPoint[];
}

export interface GuessEntry {
  playerId: string;
  playerName: string;
  guess: string;
  at: number;
  correct: boolean;
}

export interface DrawingScoreEntry {
  playerId: string;
  playerName: string;
  total: number;
  delta: number;
}

export interface DrawStartInput extends PlayerInput {
  type: "draw:start";
  clientStrokeId?: string;
  x: number;
  y: number;
}

export interface DrawMoveInput extends PlayerInput {
  type: "draw:move";
  x: number;
  y: number;
}

export interface DrawEndInput extends PlayerInput {
  type: "draw:end";
}

export interface DrawClearInput extends PlayerInput {
  type: "draw:clear";
}

export interface DrawSetColorInput extends PlayerInput {
  type: "draw:set-color";
  color: string;
}

export interface DrawSetCanvasAspectInput extends PlayerInput {
  type: "draw:set-canvas-aspect";
  aspectRatio: number;
}

export interface GuessSubmitInput extends PlayerInput {
  type: "guess:submit";
  guess: string;
}

export type ZeichnenUndErratenInput =
  | DrawStartInput
  | DrawMoveInput
  | DrawEndInput
  | DrawClearInput
  | DrawSetColorInput
  | DrawSetCanvasAspectInput
  | GuessSubmitInput;

/**
 * The neutral pen each theme starts with.
 *
 * The board follows the room's skin, so a single fixed default cannot work:
 * the palette used to open on white, which is invisible on a light board. The
 * drawer can still pick either — this only decides which one is already in
 * hand.
 */
const INK_BLACK = "#101418";
const INK_WHITE = "#f8fafc";

export const drawingInkByTheme: Readonly<Record<GameHostThemeName, string>> = {
  light: INK_BLACK,
  dark: INK_WHITE
};

export const drawingColorPalette = [
  INK_BLACK,
  INK_WHITE,
  "#ef4444",
  "#f97316",
  "#eab308",
  "#22c55e",
  "#06b6d4",
  "#3b82f6",
  "#a855f7",
  "#ec4899"
] as const;

export function resolveDefaultDrawingColor(theme: GameHostThemeName): string {
  return drawingInkByTheme[theme] ?? drawingInkByTheme.light;
}

export interface ZeichnenUndErratenState extends BaseRoundState {
  gameScores: DrawingScoreEntry[];
  drawerPlayerId: string;
  drawerName: string;
  secretWord: string;
  wordCategory: ZeichnenUndErratenWordCategory;
  maskedWord: string;
  finishAt: number | null;
  roundDurationMs: number | null;
  canvasAspectRatio: number;
  currentColor: string;
  availableColors: readonly string[];
  strokes: DrawingStroke[];
  activeStrokeId: string | null;
  guesses: GuessEntry[];
  winnerPlayerId?: string;
  winnerName?: string;
}

export interface ZeichnenUndErratenPublicState {
  gameScores: DrawingScoreEntry[];
  drawerPlayerId: string;
  drawerName: string;
  wordCategory: ZeichnenUndErratenWordCategory;
  maskedWord: string;
  finishAt: number | null;
  roundDurationMs: number | null;
  canvasAspectRatio: number;
  currentColor: string;
  availableColors: readonly string[];
  strokes: DrawingStroke[];
  guesses: GuessEntry[];
  winnerPlayerId?: string;
  winnerName?: string;
  revealedWord?: string;
}

export interface ZeichnenUndErratenControllerState extends ZeichnenUndErratenPublicState {
  isDrawer: boolean;
  secretWord?: string;
}
