import { drawingWordPool } from "./words.js";
import {
  createBaseRoundState,
  resolveRoundPhaseTimings,
  roundPhaseDurations,
  transitionRoundState,
  type ScoreEntry,
  type ServerGame,
  type ServerGameContext,
  type SupportedLanguage
} from "@open-party-lab/game-core";
import type { ZeichnenUndErratenWordCategory } from "../protocol.js";
import { zeichnenUndErratenManifest } from "../manifest.js";
import {
  isZeichnenUndErratenWordCategory,
  resolveZeichnenUndErratenRoundSeconds,
  resolveZeichnenUndErratenWordCategory,
  zeichnenUndErratenRoomSettingKeys
} from "./zeichnenUndErratenConfig.js";
import type {
  DrawingPoint,
  GuessEntry,
  ZeichnenUndErratenControllerState,
  ZeichnenUndErratenInput,
  ZeichnenUndErratenPublicState,
  ZeichnenUndErratenState
} from "./zeichnenUndErratenState.js";
import { drawingColorPalette, resolveDefaultDrawingColor } from "./zeichnenUndErratenState.js";

const maxGuessFeedEntries = 10;
const phaseTimings = resolveRoundPhaseTimings(zeichnenUndErratenManifest.phaseDurations);

const zeichnenUndErratenText = {
  de: {
    drawerFallback: "Zeichner",
    playerFallback: "Spieler",
    ready: (name: string) => `${name} zeichnet gleich. Bereit zum Raten?`,
    drawingNow: (name: string) => `${name} zeichnet jetzt.`,
    cleared: (name: string) => `${name} hat die Zeichnung geloescht.`,
    guessedCorrectly: (name: string, word: string) => `${name} hat "${word}" erraten!`,
    guessed: (name: string, guess: string) => `${name} hat geraten: ${guess}`,
    timeUp: (word: string) => `Zeit abgelaufen. Das Wort war "${word}".`
  },
  en: {
    drawerFallback: "Drawer",
    playerFallback: "Player",
    ready: (name: string) => `${name} will draw next. Ready to guess?`,
    drawingNow: (name: string) => `${name} is drawing now.`,
    cleared: (name: string) => `${name} cleared the drawing.`,
    guessedCorrectly: (name: string, word: string) => `${name} guessed "${word}"!`,
    guessed: (name: string, guess: string) => `${name} guessed: ${guess}`,
    timeUp: (word: string) => `Time is up. The word was "${word}".`
  }
} satisfies Record<"de" | "en",
  {
    drawerFallback: string;
    playerFallback: string;
    ready: (name: string) => string;
    drawingNow: (name: string) => string;
    cleared: (name: string) => string;
    guessedCorrectly: (name: string, word: string) => string;
    guessed: (name: string, guess: string) => string;
    timeUp: (word: string) => string;
  }
>;


function clamp01(value: number): number {
  return Math.max(0, Math.min(1, value));
}

function isDrawingColor(value: string): boolean {
  return drawingColorPalette.includes(value as (typeof drawingColorPalette)[number]);
}

function normalizeGuess(value: string): string {
  return value
    .trim()
    .toLocaleLowerCase("de-DE")
    .replace(/ß/g, "ss")
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]/g, "");
}

function hashStringToSeed(value: string): number {
  let hash = 2_166_136_261;

  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16_777_619);
  }

  return (hash >>> 0) || 1;
}

function nextRandomValue(seed: number): number {
  const nextSeed = (Math.imul(seed, 1_664_525) + 1_013_904_223) >>> 0;
  return nextSeed / 4_294_967_296;
}

function selectDrawerPlayerId(roundNumber: number, playerIds: string[]): string {
  const safeIndex = Math.max(0, (roundNumber - 1) % playerIds.length);
  return playerIds[safeIndex] ?? playerIds[0] ?? "";
}

function getPreviousSecretWord(context: ServerGameContext): string | undefined {
  if (context.previousRound?.gameId !== zeichnenUndErratenManifest.id) {
    return undefined;
  }

  const previousState = context.previousRound.state as Partial<ZeichnenUndErratenState>;
  return typeof previousState.secretWord === "string" ? previousState.secretWord : undefined;
}

function pickWord(context: ServerGameContext, category: ZeichnenUndErratenWordCategory): string {
  const pool = drawingWordPool(context.language, category);
  const previousWord = getPreviousSecretWord(context);
  const seed = hashStringToSeed(`${context.roomCode}:${context.roundNumber}:${context.now}:${category}`);
  const index = Math.floor(nextRandomValue(seed) * pool.length);
  const selected = pool[index] ?? pool[0] ?? "Sonne";

  if (selected === previousWord && pool.length > 1) {
    return pool[(index + 1) % pool.length] ?? "Sonne";
  }

  return selected;
}

function createMaskedWord(word: string): string {
  return word
    .split("")
    .map((char) => (char.trim() ? "_" : " "))
    .join(" ");
}

function shouldRevealWord(state: ZeichnenUndErratenState): boolean {
  return (
    state.phase === "locked" ||
    state.phase === "result" ||
    state.phase === "scoreboard" ||
    state.phase === "finished"
  );
}

function buildScore(state: ZeichnenUndErratenState, language: SupportedLanguage): ScoreEntry[] {
  if (!state.winnerPlayerId) {
    return [];
  }

  const entries: ScoreEntry[] = [
    {
      playerId: state.winnerPlayerId,
      delta: 1,
      reason: language === "en" ? "Correct word guess" : "Wort korrekt geraten"
    }
  ];

  if (state.drawerPlayerId !== state.winnerPlayerId) {
    entries.push({
      playerId: state.drawerPlayerId,
      delta: 1,
      reason: language === "en" ? "Word successfully drawn" : "Wort erfolgreich gezeichnet"
    });
  }

  return entries;
}

function toPublicState(state: ZeichnenUndErratenState, context: ServerGameContext): ZeichnenUndErratenPublicState {
  const scoresById = new Map(state.gameScores.map((entry) => [entry.playerId, entry]));
  return {
    gameScores: context.players.map((player) => ({
      playerId: player.id,
      playerName: player.name,
      total: scoresById.get(player.id)?.total ?? 0,
      delta: scoresById.get(player.id)?.delta ?? 0
    })),
    drawerPlayerId: state.drawerPlayerId,
    drawerName: state.drawerName,
    wordCategory: state.wordCategory,
    maskedWord: state.maskedWord,
    finishAt: state.finishAt,
    roundDurationMs: state.roundDurationMs,
    canvasAspectRatio: state.canvasAspectRatio,
    currentColor: state.currentColor,
    availableColors: state.availableColors,
    strokes: state.strokes,
    guesses: state.guesses,
    winnerPlayerId: state.winnerPlayerId,
    winnerName: state.winnerName,
    revealedWord: shouldRevealWord(state) ? state.secretWord : undefined
  };
}

function toControllerStateForPlayer(
  state: ZeichnenUndErratenState,
  context: ServerGameContext,
  playerId: string
): ZeichnenUndErratenControllerState {
  const isDrawer = state.drawerPlayerId === playerId;

  return {
    ...toPublicState(state, context),
    isDrawer,
    secretWord: isDrawer ? state.secretWord : undefined
  };
}

export const serverGame: ServerGame<
  ZeichnenUndErratenState,
  ZeichnenUndErratenInput,
  ZeichnenUndErratenPublicState | ZeichnenUndErratenControllerState
> = {
  manifest: zeichnenUndErratenManifest,
  createInitialState(context) {
    const text = zeichnenUndErratenText[context.language === "en" ? "en" : "de"];
    const drawerPlayerId = selectDrawerPlayerId(
      context.roundNumber,
      context.players.map((player) => player.id)
    );
    const drawerName =
      context.players.find((player) => player.id === drawerPlayerId)?.name ?? text.drawerFallback;
    const wordCategory = resolveZeichnenUndErratenWordCategory(context.roomSettings);
    const roundSeconds = resolveZeichnenUndErratenRoundSeconds(context.roomSettings);
    const secretWord = pickWord(context, wordCategory);
    const previous = context.previousRound?.gameId === zeichnenUndErratenManifest.id
      && context.previousRound.phase === "finished"
      ? context.previousRound.state as Partial<ZeichnenUndErratenState> | null
      : null;
    const previousTotals = new Map((previous?.gameScores ?? []).map((entry) => [entry.playerId, entry.total]));

    return {
      ...createBaseRoundState("round_intro", context.now, {
        durationMs: phaseTimings.roundIntroMs,
        message: text.ready(drawerName)
      }),
      gameScores: context.players.map((player) => ({
        playerId: player.id,
        playerName: player.name,
        total: previousTotals.get(player.id) ?? 0,
        delta: 0
      })),
      drawerPlayerId,
      drawerName,
      secretWord,
      wordCategory,
      maskedWord: createMaskedWord(secretWord),
      finishAt: null,
      roundDurationMs: roundSeconds === null ? null : roundSeconds * 1_000,
      canvasAspectRatio: 4 / 3,
      currentColor: resolveDefaultDrawingColor(context.theme),
      availableColors: drawingColorPalette,
      strokes: [],
      activeStrokeId: null,
      guesses: []
    };
  },
  startRound(state, context) {
    const text = zeichnenUndErratenText[context.language === "en" ? "en" : "de"];

    return transitionRoundState(
      {
        ...state,
        finishAt: state.roundDurationMs === null ? null : context.now + state.roundDurationMs,
        currentColor: resolveDefaultDrawingColor(context.theme),
        strokes: [],
        activeStrokeId: null,
        guesses: [],
        winnerPlayerId: undefined,
        winnerName: undefined
      },
      "playing",
      context.now,
      {
        startedAt: context.now,
        message: text.drawingNow(state.drawerName)
      }
    );
  },
  handleHostAction(_state, action) {
    if (!action || typeof action !== "object") {
      return null;
    }

    const typedAction = action as { type?: unknown; category?: unknown; roundSeconds?: unknown };

    if (typedAction.type !== "configure-lobby") {
      return null;
    }

    const roomSettings: Record<string, unknown> = {};
    if (isZeichnenUndErratenWordCategory(typedAction.category)) {
      roomSettings[zeichnenUndErratenRoomSettingKeys.wordCategory] = typedAction.category;
    }
    if (typeof typedAction.roundSeconds === "number" && Number.isFinite(typedAction.roundSeconds)) {
      const seconds = Math.round(typedAction.roundSeconds / 15) * 15;
      if (seconds === 0 || (seconds >= 15 && seconds <= 180)) {
        roomSettings[zeichnenUndErratenRoomSettingKeys.roundSeconds] = seconds;
      }
    }
    if (Object.keys(roomSettings).length === 0) return null;

    return {
      roomSettings
    };
  },
  handleInput(state, input, context) {
    if (state.phase !== "playing") {
      return state;
    }

    if (input.playerId === state.drawerPlayerId) {
      if (input.type === "draw:clear") {
        return {
          ...state,
          strokes: [],
          activeStrokeId: null,
          updatedAt: context.now,
          message: zeichnenUndErratenText[context.language === "en" ? "en" : "de"].cleared(state.drawerName)
        };
      }

      if (input.type === "draw:set-color") {
        if (!isDrawingColor(input.color)) {
          return state;
        }

        return {
          ...state,
          currentColor: input.color,
          updatedAt: context.now
        };
      }

      if (input.type === "draw:set-canvas-aspect") {
        const aspectRatio = input.aspectRatio;
        if (!Number.isFinite(aspectRatio) || aspectRatio < 0.4 || aspectRatio > 2.5) {
          return state;
        }
        if (Math.abs(state.canvasAspectRatio - aspectRatio) < 0.025) return state;
        return { ...state, canvasAspectRatio: aspectRatio, updatedAt: context.now };
      }

      if (input.type === "draw:start") {
        const point: DrawingPoint = { x: clamp01(input.x), y: clamp01(input.y) };
        const strokeId = `stroke-${context.now}-${state.strokes.length + 1}`;

        return {
          ...state,
          activeStrokeId: strokeId,
          strokes: [...state.strokes, {
            id: strokeId,
            clientStrokeId: typeof input.clientStrokeId === "string" && /^[a-zA-Z0-9-]{1,80}$/.test(input.clientStrokeId)
              ? input.clientStrokeId : undefined,
            color: state.currentColor,
            points: [point]
          }],
          updatedAt: context.now
        };
      }

      if (input.type === "draw:move") {
        if (!state.activeStrokeId || state.strokes.length === 0) {
          return state;
        }

        const point: DrawingPoint = { x: clamp01(input.x), y: clamp01(input.y) };
        const strokes = [...state.strokes];
        const lastStroke = strokes[strokes.length - 1];

        if (!lastStroke || lastStroke.id !== state.activeStrokeId) {
          return state;
        }

        strokes[strokes.length - 1] = {
          ...lastStroke,
          points: [...lastStroke.points, point]
        };

        return {
          ...state,
          strokes,
          updatedAt: context.now
        };
      }

      if (input.type === "draw:end") {
        return {
          ...state,
          activeStrokeId: null,
          updatedAt: context.now
        };
      }

      return state;
    }

    if (input.type !== "guess:submit") {
      return state;
    }

    const guess = input.guess.trim();

    if (!guess) {
      return state;
    }

    const playerName =
      context.players.find((player) => player.id === input.playerId)?.name ??
      zeichnenUndErratenText[context.language === "en" ? "en" : "de"].playerFallback;
    const isCorrect = normalizeGuess(guess) === normalizeGuess(state.secretWord);
    const feedEntry: GuessEntry = {
      playerId: input.playerId,
      playerName,
      guess,
      at: context.now,
      correct: isCorrect
    };
    const guesses = [...state.guesses, feedEntry].slice(-maxGuessFeedEntries);

    if (isCorrect) {
      return transitionRoundState(
        {
          ...state,
          guesses,
          winnerPlayerId: input.playerId,
          winnerName: playerName,
          gameScores: context.players.map((player) => {
            const delta = player.id === input.playerId || player.id === state.drawerPlayerId ? 1 : 0;
            const total = state.gameScores.find((entry) => entry.playerId === player.id)?.total ?? 0;
            return { playerId: player.id, playerName: player.name, total: total + delta, delta };
          })
        },
        "locked",
        context.now,
        {
          durationMs: roundPhaseDurations.lockedMs,
          message: zeichnenUndErratenText[context.language === "en" ? "en" : "de"].guessedCorrectly(playerName, state.secretWord)
        }
      );
    }

    return {
      ...state,
      guesses,
      updatedAt: context.now,
      message: zeichnenUndErratenText[context.language === "en" ? "en" : "de"].guessed(playerName, guess)
    };
  },
  tick(state, _deltaMs, context) {
    if (state.phase !== "playing" || state.finishAt === null || context.now < state.finishAt) {
      return state;
    }

    return transitionRoundState(state, "locked", context.now, {
      durationMs: roundPhaseDurations.lockedMs,
      message: zeichnenUndErratenText[context.language === "en" ? "en" : "de"].timeUp(state.secretWord)
    });
  },
  isRoundFinished(state) {
    return state.phase === "locked";
  },
  buildScore(state, context) {
    return buildScore(state, context.language);
  },
  toPublicState(state, context) {
    return toPublicState(state, context);
  },
  toControllerStateForPlayer(state, context, playerId) {
    return toControllerStateForPlayer(state, context, playerId);
  }
};
