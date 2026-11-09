import type { ControllerLayoutKey } from "@open-party-lab/game-core";
import { schaetzoramaManifest } from "../manifest.js";
import type {
  SchaetzoramaAnswerSet,
  SchaetzoramaCategoryId,
  SchaetzoramaControllerState,
  SchaetzoramaJokerInventory,
  SchaetzoramaJokerPreview,
  SchaetzoramaJokerSelection,
  SchaetzoramaPlayerProgress,
  SchaetzoramaPlayerRoundResult,
  SchaetzoramaPublicQuestion,
  SchaetzoramaRoundContent,
  SchaetzoramaStage,
  SchaetzoramaStanding
} from "../protocol.js";
import { createSchaetzoramaJokerInput, createSchaetzoramaPreviewInput, createSchaetzoramaSubmitInput } from "./schaetzoramaBindings.js";

type SupportedLanguage = "de" | "en";

interface ReadyLayoutModel {
  currentPlayerReady: boolean;
  readyCount: number;
  playerCount: number;
  label: string;
  description: string;
  language?: SupportedLanguage;
  onToggleReady: () => void;
}

interface SchaetzoramaLayoutModel {
  revealStep: number;
  revealAnswersVisible: boolean;
  ownRevealReady: boolean;
  autoContinue: boolean;
  onAutoReady: () => void;
  onContinueReveal: () => void;
  onSetAutoContinue: (enabled: boolean) => void;

  kind: "schaetzorama";
  currentPlayerId: string;
  title: string;
  subtitle?: string;
  helperText?: string;
  language?: SupportedLanguage;
  disabled: boolean;
  ready?: ReadyLayoutModel;
  stage: SchaetzoramaStage;
  resetKey: string;
  roundContent?: SchaetzoramaRoundContent<SchaetzoramaPublicQuestion>;
  ownAnswers: SchaetzoramaAnswerSet;
  ownJokerPreview: SchaetzoramaJokerPreview | null | undefined;
  ownJoker: SchaetzoramaJokerSelection | null | undefined;
  ownInventory: SchaetzoramaJokerInventory;
  copyTargets: Array<{
    playerId: string;
    name: string;
    answers?: SchaetzoramaAnswerSet;
  }>;
  progress: SchaetzoramaPlayerProgress[];
  answerEndsAt: number | null;
  jokerEndsAt: number | null;
  solutions: SchaetzoramaAnswerSet;
  results: SchaetzoramaPlayerRoundResult[];
  standings: SchaetzoramaStanding[];
  categoryLabels: Record<SchaetzoramaCategoryId, string>;
  canSubmitAnswers: boolean;
  canSubmitJoker: boolean;
  onSubmitAnswers: (answers: SchaetzoramaAnswerSet) => void;
  onPreviewJoker: (joker: SchaetzoramaJokerSelection) => void;
  onChooseJoker: (joker: SchaetzoramaJokerSelection | null) => void;
}

interface ControllerGameRenderContext {
  state: {
    preferredLanguage?: SupportedLanguage;
    room?: {
      language?: SupportedLanguage;
      selectedGameId?: string;
      availableGames?: Array<{ id: string; displayName?: string; roundCompletionMode?: string }>;
      players?: Array<{ id: string; name: string; isReady?: boolean }>;
    } | null;
    player?: {
      id: string;
      isReady?: boolean;
    } | null;
    game?: {
      phase?: string;
      roundNumber?: number;
      state?: unknown;
    } | null;
  };
  onInput(input: unknown): void;
  onSetReady?: (isReady: boolean) => void;
}

const categoryLabels = {
  number: "Número",
  percent: "Porcentaje",
  rank: "Orden",
  assign: "Clasificación"
} as const;

function buildReadyModel(context: ControllerGameRenderContext): ReadyLayoutModel | undefined {
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

  return {
    currentPlayerReady,
    readyCount,
    playerCount,
    label: "Próxima ronda",
    description: `${readyCount}/${playerCount} jugadores listos.`,
    language: state.room.language,
    onToggleReady: () => onSetReady(!currentPlayerReady)
  };
}

export function buildSchaetzoramaControllerModel(context: ControllerGameRenderContext): SchaetzoramaLayoutModel {
  const { state, onInput } = context;
  const language = state.room?.language ?? state.preferredLanguage;
  const playerId = state.player?.id ?? "";
  const guessState = (state.game?.state ?? null) as SchaetzoramaControllerState | null;
  const phase = state.game?.phase ?? "round_intro";
  const stage = guessState?.stage ?? "answering";
  const answered = Boolean(guessState?.ownAnswers && Object.keys(guessState.ownAnswers).length > 0);
  const waitingCount = guessState?.progress.filter((entry) => !entry.answered).length ?? 0;

  return {
    kind: "schaetzorama",
    revealStep: guessState?.revealStep ?? 0,
    revealAnswersVisible: Boolean(guessState?.revealAnswersVisible),
    ownRevealReady: Boolean(guessState?.revealReadyByPlayerId[playerId]),
    autoContinue: Boolean(guessState?.autoContinueByPlayerId[playerId]),
    onAutoReady: () => context.onSetReady?.(true),
    onContinueReveal: () => onInput({ type: "reveal_ready", playerId, step: guessState?.revealStep ?? 0 }),
    onSetAutoContinue: (enabled) => onInput({ type: "set_auto_continue", playerId, enabled }),
    currentPlayerId: playerId,
    title: "Cifras salvajes",
    subtitle:
      stage === "revealed"
        ? "Resultados"
        : stage === "joker"
          ? "Hora de copiar"
          : answered
            ? "Respuestas fijas"
            : "Ajusta todos los controles",
    helperText:
      stage === "revealed"
        ? guessState?.roundContent.roundIndex === 10
          ? "Sesión completa. La clasificación final está en la tele."
          : "La verdad salió. Marquen listos cuando terminen de ver las respuestas."
        : stage === "joker"
          ? guessState?.canSubmitJoker
            ? "Elige persona y tarea, compara ambas respuestas y luego decide."
            : "Copia bloqueada. Esperando al resto."
          : answered
            ? `Tu panel está cerrado. Esperando a ${waitingCount}.`
            : "Estima, ordena y clasifica. La copia se abre cuando todos cierren.",
    disabled: phase !== "playing",
    ready: guessState?.roundContent.roundIndex === 10 ? undefined : buildReadyModel(context),
    stage,
    resetKey: `${state.game?.roundNumber ?? 0}:${stage}:${guessState?.roundContent.roundIndex ?? 0}`,
    roundContent: guessState?.roundContent,
    ownAnswers: guessState?.ownAnswers ?? {},
    ownJokerPreview: guessState?.ownJokerPreview,
    ownJoker: guessState?.ownJoker,
    ownInventory: guessState?.ownInventory ?? { copy: 0 },
    copyTargets: guessState?.copyTargets ?? [],
    progress: guessState?.progress ?? [],
    answerEndsAt: guessState?.answerEndsAt ?? null,
    jokerEndsAt: guessState?.jokerEndsAt ?? null,
    solutions: guessState?.solutions ?? {},
    results: guessState?.results ?? [],
    standings: guessState?.standings ?? [],
    categoryLabels,
    language,
    canSubmitAnswers: Boolean(guessState?.canSubmitAnswers),
    canSubmitJoker: Boolean(guessState?.canSubmitJoker),
    onSubmitAnswers: (answers) => onInput(createSchaetzoramaSubmitInput(playerId, answers)),
    onPreviewJoker: (joker) => onInput(createSchaetzoramaPreviewInput(playerId, joker)),
    onChooseJoker: (joker) => onInput(createSchaetzoramaJokerInput(playerId, joker))
  };
}

export const controllerGame = {
  id: schaetzoramaManifest.id,
  layoutKey: "schaetzorama" as ControllerLayoutKey,
  buildLayout(context: ControllerGameRenderContext) {
    return buildSchaetzoramaControllerModel(context);
  }
} as const;
