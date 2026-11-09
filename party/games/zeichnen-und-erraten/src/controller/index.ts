import type { ControllerLayoutKey } from "@open-party-lab/game-core";
import { zeichnenUndErratenManifest } from "../manifest.js";
import {
  createDrawingClearInput,
  createDrawingEndInput,
  createDrawingMoveInput,
  createDrawingSetCanvasAspectInput,
  createDrawingSetColorInput,
  createDrawingStartInput,
  createGuessSubmitInput
} from "./zeichnenUndErratenBindings.js";

type SupportedLanguage = "de" | "en";

interface ReadyLayoutModel {
  currentPlayerReady: boolean;
  readyCount: number;
  playerCount: number;
  label: string;
  description?: string;
  language?: SupportedLanguage;
  onToggleReady: () => void;
}

interface DrawingGuessLayoutModel {
  kind: "drawing_guess";
  title: string;
  subtitle?: string;
  helperText?: string;
  language?: SupportedLanguage;
  disabled: boolean;
  ready?: ReadyLayoutModel;
  guessResetKey?: string;
  isDrawer: boolean;
  wordMask: string;
  secretWord?: string;
  currentColor?: string;
  canvasAspectRatio: number;
  availableColors?: string[];
  strokes: Array<{ id: string; clientStrokeId?: string; color: string; points: Array<{ x: number; y: number }> }>;
  guessFeed: Array<{ playerName: string; guess: string; correct: boolean }>;
  winnerName?: string;
  onDrawStart: (x: number, y: number, clientStrokeId?: string) => void;
  onDrawMove: (x: number, y: number) => void;
  onDrawEnd: () => void;
  onClearDrawing: () => void;
  onSelectColor?: (color: string) => void;
  onSetCanvasAspect: (aspectRatio: number) => void;
  onSubmitGuess: (guess: string) => void;
}

interface ControllerGameRenderContext {
  state: {
    preferredLanguage?: SupportedLanguage;
    room?: {
      language?: SupportedLanguage;
      selectedGameId?: string | null;
      players?: Array<{ id: string; name: string; isReady?: boolean }>;
      availableGames?: Array<{ id: string; displayName: string; roundCompletionMode?: string }>;
    } | null;
    player?: {
      id: string;
      isReady?: boolean;
    } | null;
    game?: {
      phase?: string;
      message?: string;
      roundNumber?: number;
      state?: unknown;
    } | null;
  };
  onInput(input: unknown): void;
  onSetReady?: (isReady: boolean) => void;
}

function formatPhase(phase: string | undefined, en: boolean): string {
  switch (phase) {
    case "round_intro":
      return en ? "Round intro" : "Preparando ronda";
    case "countdown":
      return "Countdown";
    case "playing":
      return en ? "Drawing" : "Dibujando";
    case "locked":
      return en ? "Locked" : "Bloqueado";
    case "result":
      return en ? "Result" : "Resultado";
    case "scoreboard":
      return en ? "Scoreboard" : "Marcador";
    case "finished":
      return en ? "Round finished" : "Ronda terminada";
    default:
      return en ? "Waiting" : "Esperando";
  }
}

function buildReadyModel(context: ControllerGameRenderContext, en: boolean): ReadyLayoutModel | undefined {
  const { state, onSetReady } = context;
  const gameId = state.room?.selectedGameId;
  const selectedGame = gameId ? state.room?.availableGames?.find((entry) => entry.id === gameId) : undefined;

  if (
    !selectedGame ||
    selectedGame.roundCompletionMode !== "wait_for_ready" ||
    state.game?.phase !== "finished" ||
    !state.room ||
    !state.player ||
    !onSetReady
  ) {
    return undefined;
  }

  const playerId = state.player.id;
  const players = state.room.players ?? [];
  const currentPlayerReady = Boolean(
    players.find((player) => player.id === playerId)?.isReady ?? state.player.isReady
  );
  const readyCount = players.filter((player) => player.isReady).length;
  const playerCount = players.length;
  const label = en ? "Next Round" : "próxima ronda";

  return {
    currentPlayerReady,
    readyCount,
    playerCount,
    label,
    description: en
      ? `Tap "${label}". Starts when ${readyCount}/${playerCount} are ready.`
      : `Grifo "${label}". Empezar en ${readyCount}/${playerCount} listo.`,
    language: state.room.language,
    onToggleReady: () => onSetReady(!currentPlayerReady)
  };
}

export function buildZeichnenUndErratenControllerModel(
  context: ControllerGameRenderContext
): DrawingGuessLayoutModel {
  const { state, onInput } = context;
  const language = state.room?.language ?? state.preferredLanguage ?? "de";
  const en = language === "en";
  const playerId = state.player?.id ?? "";
  const drawState = (state.game?.state ?? {}) as {
    isDrawer?: boolean;
    maskedWord?: string;
    secretWord?: string;
    currentColor?: string;
    canvasAspectRatio?: number;
    availableColors?: string[];
    strokes?: Array<{ id: string; clientStrokeId?: string; color: string; points: Array<{ x: number; y: number }> }>;
    guesses?: Array<{ playerName: string; guess: string; correct: boolean }>;
    winnerName?: string;
  };

  return {
    kind: "drawing_guess",
    title:
      state.room?.availableGames?.find((game) => game.id === zeichnenUndErratenManifest.id)?.displayName ??
      (en ? "Garabato mental" : "Garabato mental"),
    subtitle: formatPhase(state.game?.phase, en),
    helperText:
      state.game?.message ?? (en ? "One player draws while the others guess." : "Una persona dibuja y el resto adivina."),
    language,
    disabled: state.game?.phase !== "playing",
    ready: buildReadyModel(context, en),
    guessResetKey: `${state.game?.roundNumber ?? 0}:${state.game?.phase ?? "idle"}:${drawState.maskedWord ?? ""}`,
    isDrawer: Boolean(drawState.isDrawer),
    wordMask: drawState.maskedWord ?? "_ _ _",
    secretWord: drawState.secretWord,
    currentColor: drawState.currentColor,
    canvasAspectRatio: drawState.canvasAspectRatio ?? 4 / 3,
    availableColors: drawState.availableColors,
    strokes: (drawState.strokes ?? []).map((stroke) => ({
      ...stroke,
      color: stroke.color ?? "#f8fafc"
    })),
    guessFeed: drawState.guesses ?? [],
    winnerName: drawState.winnerName,
    onDrawStart: (x, y, clientStrokeId) => onInput(createDrawingStartInput(playerId, x, y, clientStrokeId)),
    onDrawMove: (x, y) => onInput(createDrawingMoveInput(playerId, x, y)),
    onDrawEnd: () => onInput(createDrawingEndInput(playerId)),
    onClearDrawing: () => onInput(createDrawingClearInput(playerId)),
    onSelectColor: (color) => onInput(createDrawingSetColorInput(playerId, color)),
    onSetCanvasAspect: (aspectRatio) => onInput(createDrawingSetCanvasAspectInput(playerId, aspectRatio)),
    onSubmitGuess: (guess) => onInput(createGuessSubmitInput(playerId, guess))
  };
}

export const controllerGame = {
  id: zeichnenUndErratenManifest.id,
  layoutKey: "drawing_guess" as ControllerLayoutKey,
  buildLayout(context: ControllerGameRenderContext) {
    return buildZeichnenUndErratenControllerModel(context);
  }
} as const;
