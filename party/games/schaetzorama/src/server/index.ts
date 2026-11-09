import {
  createBaseRoundState,
  roundPhaseDurations,
  transitionRoundState,
  type GamePlayerSummary,
  type ServerGame,
  type SupportedLanguage
} from "@open-party-lab/game-core";
import type {
  SchaetzoramaAnswerSet,
  SchaetzoramaControllerState,
  SchaetzoramaCategoryId,
  SchaetzoramaInput,
  SchaetzoramaJokerInventory,
  SchaetzoramaJokerSelection,
  SchaetzoramaNumberQuestion,
  SchaetzoramaPublicQuestion,
  SchaetzoramaPublicState,
  SchaetzoramaQuestion,
  SchaetzoramaRoundContent,
  SchaetzoramaRankQuestion,
  SchaetzoramaAssignQuestion,
  SchaetzoramaState
} from "../protocol.js";
import { schaetzoramaManifest } from "../manifest.js";
import { schaetzoramaRounds } from "./schaetzoramaContent.js";
import { buildSchaetzoramaResults, schaetzoramaCategoryIds } from "./schaetzoramaScoring.js";

import { answersVisibleMs, autoContinueDelayMs, revealCategories } from "../revealTiming.js";
const roundsPerGame = 10;
const startingInventory: SchaetzoramaJokerInventory = {
  copy: 2
};

type UsedQuestionIdsByCategory = Record<SchaetzoramaCategoryId, string[]>;

function createEmptyUsedQuestionIdsByCategory(): UsedQuestionIdsByCategory {
  return { number: [], percent: [], rank: [], assign: [] };
}

const selectableQuestionsByCategory = Object.fromEntries(
  schaetzoramaCategoryIds.map((categoryId) => [
    categoryId,
    schaetzoramaRounds.map((round) => round.questions[categoryId])
  ])
) as Record<SchaetzoramaCategoryId, SchaetzoramaQuestion[]>;

function selectableQuestionsFor(categoryId: SchaetzoramaCategoryId): SchaetzoramaQuestion[] {
  const questions = selectableQuestionsByCategory[categoryId];

  if (questions.length < roundsPerGame) {
    throw new Error(`Schaetzorama needs at least ${roundsPerGame} selectable ${categoryId} questions.`);
  }

  return questions;
}

function randomQuestionFor(categoryId: SchaetzoramaCategoryId, usedQuestionIds: string[]): SchaetzoramaQuestion {
  const questions = selectableQuestionsFor(categoryId);
  const unusedQuestions = questions.filter((question) => !usedQuestionIds.includes(question.id));
  const question = unusedQuestions[Math.floor(Math.random() * unusedQuestions.length)];

  if (!question) {
    throw new Error(`Schaetzorama ran out of unique ${categoryId} questions in this session.`);
  }

  if (question.kind === "rank") {
    const items = shuffled(question.items);
    if (items.every((item, index) => item.id === question.answerOrder[index]) && items.length > 1) {
      [items[0], items[1]] = [items[1], items[0]];
    }
    return { ...question, items };
  }
  if (question.kind === "assign") {
    const terms = shuffled(question.terms);
    const zoneOrder = { left: 0, both: 1, right: 2 };
    const grouped = terms.every((term, index) => index === 0 || zoneOrder[question.answers[terms[index - 1].id]] <= zoneOrder[question.answers[term.id]]);
    if (grouped) {
      const other = terms.findIndex((term) => question.answers[term.id] !== question.answers[terms[0].id]);
      if (other > 0) [terms[0], terms[other]] = [terms[other], terms[0]];
    }
    return { ...question, terms };
  }
  return question;
}

function shuffled<T>(values: readonly T[]): T[] {
  const result = [...values];
  for (let index = result.length - 1; index > 0; index--) {
    const target = Math.floor(Math.random() * (index + 1));
    [result[index], result[target]] = [result[target], result[index]];
  }
  return result;
}

function roundInGame(previousState: SchaetzoramaState | null): number {
  return previousState ? Math.min(roundsPerGame, previousState.roundContent.roundIndex + 1) : 1;
}

function usedQuestionIdsForRound(
  previousState: SchaetzoramaState | null,
  visibleRound: number
): UsedQuestionIdsByCategory {
  if (visibleRound === 1) {
    return createEmptyUsedQuestionIdsByCategory();
  }

  return previousState?.usedQuestionIdsByCategory ?? createEmptyUsedQuestionIdsByCategory();
}

function questionRoundFor(
  roundNumber: number,
  language: SupportedLanguage,
  previousState: SchaetzoramaState | null
): {
  roundContent: SchaetzoramaRoundContent;
  usedQuestionIdsByCategory: UsedQuestionIdsByCategory;
} {
  const visibleRound = roundInGame(previousState);
  const previousUsedQuestionIds = usedQuestionIdsForRound(previousState, visibleRound);
  const questions = Object.fromEntries(
    schaetzoramaCategoryIds.map((categoryId) => [
      categoryId,
      randomQuestionFor(categoryId, previousUsedQuestionIds[categoryId])
    ])
  ) as Record<SchaetzoramaCategoryId, SchaetzoramaQuestion>;

  return {
    roundContent: {
      roundIndex: visibleRound,
      roundLabel: `Ronda ${visibleRound}/10`,
      questions
    },
    usedQuestionIdsByCategory: Object.fromEntries(
      schaetzoramaCategoryIds.map((categoryId) => [
        categoryId,
        [...previousUsedQuestionIds[categoryId], questions[categoryId].id].slice(-roundsPerGame)
      ])
    ) as UsedQuestionIdsByCategory
  };
}

function createInventories(
  players: GamePlayerSummary[],
  previousState: SchaetzoramaState | null,
  visibleRound: number
): Record<string, SchaetzoramaJokerInventory> {
  const resetInventory = visibleRound === 1;
  const previousInventories = previousState?.jokerInventoryByPlayerId ?? {};

  return Object.fromEntries(
    players.map((player) => [
      player.id,
      {
        copy: resetInventory ? startingInventory.copy : previousInventories[player.id]?.copy ?? startingInventory.copy
      }
    ])
  );
}

function previousSchaetzoramaState(context: Parameters<ServerGame<SchaetzoramaState>["createInitialState"]>[0]) {
  if (context.previousRound?.gameId !== schaetzoramaManifest.id) {
    return null;
  }

  const state = context.previousRound.state as Partial<SchaetzoramaState> | null;
  return state?.roundContent ? (state as SchaetzoramaState) : null;
}

function allPlayersAnswered(state: SchaetzoramaState, players: GamePlayerSummary[]): boolean {
  return players.every((player) => Boolean(state.answersByPlayerId[player.id]));
}

function allPlayersHandledJoker(state: SchaetzoramaState, players: GamePlayerSummary[]): boolean {
  return players.every((player) => Object.prototype.hasOwnProperty.call(state.jokerByPlayerId, player.id));
}

function enterCopyStage(state: SchaetzoramaState, players: GamePlayerSummary[], now: number, language: SupportedLanguage): SchaetzoramaState {
  const answersByPlayerId = { ...state.answersByPlayerId };

  for (const player of players) {
    if (!answersByPlayerId[player.id]) {
      answersByPlayerId[player.id] = {
        answers: {},
        submittedAt: now
      };
    }
  }

  return {
    ...state,
    stage: "joker",
    answersByPlayerId,
    answerEndsAt: null,
    jokerEndsAt: null,
    updatedAt: now,
    message: language === "en" ? "Copy phase: compare one answer, then keep yours or copy it." : "Abschreiben-Phase: Eine Antwort vergleichen, dann behalten oder abschreiben."
  };
}

function revealState(state: SchaetzoramaState, players: GamePlayerSummary[], now: number, language: SupportedLanguage): SchaetzoramaState {
  if (state.stage === "revealed") {
    return state;
  }

  return {
    ...state,
    stage: "revealed",
    answerEndsAt: null,
    jokerEndsAt: null,
    revealedAt: now,
    revealAnswersVisible: false,
    revealStep: 0,
    revealStepStartedAt: now,
    revealReadyAt: now + answersVisibleMs(state.roundContent.questions.number, players.length),
    revealAdvanceAt: null,
    revealReadyByPlayerId: {},
    results: buildSchaetzoramaResults(players, state.roundContent.questions, state.answersByPlayerId, state.jokerByPlayerId),
    updatedAt: now,
    message: language === "en" ? "Reveal time! The truth hits the big screen." : "Auswertung! Jetzt wird die Wahrheit aufgedreht."
  };
}

function buildStandings(players: GamePlayerSummary[], state: SchaetzoramaState) {
  const roundScoresByPlayerId = new Map(state.results.map((result) => [result.playerId, result.total]));

  return players
    .map((player) => {
      const roundScore = roundScoresByPlayerId.get(player.id) ?? 0;
      const projectedScore = state.stage === "revealed" && state.phase === "playing"
        ? player.score + roundScore
        : player.score;

      return {
        playerId: player.id,
        name: player.name,
        color: player.color,
        score: player.score,
        projectedScore,
        roundScore
      };
    })
    .sort((left, right) => right.projectedScore - left.projectedScore);
}

function stripQuestion(question: SchaetzoramaQuestion): SchaetzoramaPublicQuestion {
  if ("answer" in question) {
    const { answer: _answer, ...publicQuestion } = question;
    // Keep the authored scoring span private while allowing every estimate to start at zero.
    return { ...publicQuestion, min: Math.min(0, question.min) };
  }

  if ("answerOrder" in question) {
    const { answerOrder: _answerOrder, ...publicQuestion } = question;
    return publicQuestion;
  }

  const { answers: _answers, ...publicQuestion } = question;
  return publicQuestion;
}

function localizeQuestion(question: SchaetzoramaPublicQuestion, _language: SupportedLanguage): SchaetzoramaPublicQuestion {
  return question;
}

function toPublicSchaetzoramaState(state: SchaetzoramaState, players: GamePlayerSummary[], language: SupportedLanguage, now: number): SchaetzoramaPublicState {
  const numberQuestion = state.roundContent.questions.number as SchaetzoramaNumberQuestion;
  const percentQuestion = state.roundContent.questions.percent as SchaetzoramaNumberQuestion;
  const rankQuestion = state.roundContent.questions.rank as SchaetzoramaRankQuestion;
  const assignQuestion = state.roundContent.questions.assign as SchaetzoramaAssignQuestion;
  const solutions: SchaetzoramaAnswerSet =
    state.stage === "revealed"
      ? {
          number: {
            kind: "number",
            value: numberQuestion.answer
          },
          percent: {
            kind: "number",
            value: percentQuestion.answer
          },
          rank: {
            kind: "rank",
            order: rankQuestion.answerOrder
          },
          assign: {
            kind: "assign",
            assignments: assignQuestion.answers
          }
        }
      : {};

  return {
    stage: state.stage,
    roundContent: {
      ...state.roundContent,
      questions: {
        number: localizeQuestion(stripQuestion(state.roundContent.questions.number), language),
        percent: localizeQuestion(stripQuestion(state.roundContent.questions.percent), language),
        rank: localizeQuestion(stripQuestion(state.roundContent.questions.rank), language),
        assign: localizeQuestion(stripQuestion(state.roundContent.questions.assign), language)
      }
    },
    progress: players.map((player) => ({
      playerId: player.id,
      name: player.name,
      color: player.color,
      answered: Boolean(state.answersByPlayerId[player.id]),
      jokerReady: Object.prototype.hasOwnProperty.call(state.jokerByPlayerId, player.id)
    })),
    answerEndsAt: state.answerEndsAt,
    jokerEndsAt: state.jokerEndsAt,
    revealedAt: state.revealedAt,
    revealElapsedMs: Math.max(0, now - (state.revealStepStartedAt ?? now)),
    revealAnswersVisible: state.revealAnswersVisible,
    revealStep: state.revealStep,
    revealStepStartedAt: state.revealStepStartedAt,
    revealReadyAt: state.revealReadyAt,
    revealAdvanceAt: state.revealAdvanceAt,
    revealReadyByPlayerId: state.revealReadyByPlayerId,
    autoContinueByPlayerId: state.autoContinueByPlayerId,
    solutions,
    results: state.stage === "revealed" ? state.results : [],
    standings: buildStandings(players, state)
  };
}

function sanitizeAnswers(answers: SchaetzoramaAnswerSet, questions: Record<string, SchaetzoramaQuestion>): SchaetzoramaAnswerSet {
  const nextAnswers: SchaetzoramaAnswerSet = {};

  for (const categoryId of schaetzoramaCategoryIds) {
    const answer = answers[categoryId];
    const question = questions[categoryId];

    if (!answer) {
      continue;
    }

    if ((question.kind === "number" || question.kind === "percent") && answer.kind === "number") {
      if (!Number.isFinite(answer.value)) {
        continue;
      }
      nextAnswers[categoryId] = {
        kind: "number",
        value: Math.round(Math.max(Math.min(0, question.min), Math.min(question.max, answer.value)))
      };
      continue;
    }

    if (question.kind === "rank" && answer.kind === "rank") {
      const allowedIds = new Set(question.items.map((item) => item.id));
      const order = answer.order.filter((itemId, index, list) => allowedIds.has(itemId) && list.indexOf(itemId) === index);

      if (order.length !== question.items.length) {
        continue;
      }

      nextAnswers[categoryId] = {
        kind: "rank",
        order
      };
      continue;
    }

    if (question.kind === "assign" && answer.kind === "assign") {
      nextAnswers[categoryId] = {
        kind: "assign",
        assignments: Object.fromEntries(
          question.terms.map((term) => {
            const zone = answer.assignments[term.id];
            return [term.id, zone === "left" || zone === "right" || zone === "both" ? zone : "left"];
          })
        )
      };
    }
  }

  return nextAnswers;
}

function isValidJoker(
  joker: SchaetzoramaJokerSelection | null,
  playerId: string,
  inventory: SchaetzoramaJokerInventory,
  players: GamePlayerSummary[]
): boolean {
  if (joker === null) {
    return true;
  }

  if (!schaetzoramaCategoryIds.includes(joker.categoryId)) {
    return false;
  }

  return inventory.copy > 0 && isValidCopyTarget(joker, playerId, players);
}

function isValidCopyTarget(joker: SchaetzoramaJokerSelection, playerId: string, players: GamePlayerSummary[]): boolean {
  return (
    joker.kind === "copy" &&
    Boolean(joker.targetPlayerId) &&
    joker.targetPlayerId !== playerId &&
    players.some((player) => player.id === joker.targetPlayerId)
  );
}

function spendJoker(inventory: SchaetzoramaJokerInventory, joker: SchaetzoramaJokerSelection | null): SchaetzoramaJokerInventory {
  if (joker?.kind === "copy") {
    return {
      ...inventory,
      copy: Math.max(0, inventory.copy - 1)
    };
  }

  return inventory;
}

function isSameJokerSelection(left: SchaetzoramaJokerSelection | undefined, right: SchaetzoramaJokerSelection): boolean {
  return (
    left?.kind === right.kind &&
    left.categoryId === right.categoryId &&
    left.targetPlayerId === right.targetPlayerId
  );
}

function updateRevealGate(state: SchaetzoramaState, players: GamePlayerSummary[], now: number): SchaetzoramaState {
  if (state.revealStep >= 4 || state.revealReadyAt === null) return state;
  const visible = now >= state.revealReadyAt;
  const ready = players.length > 0 && players.every((player) => state.autoContinueByPlayerId[player.id] || state.revealReadyByPlayerId[player.id]);
  const advanceAt = ready ? state.revealAdvanceAt ?? Math.max(now, state.revealReadyAt) + autoContinueDelayMs : null;
  return advanceAt === state.revealAdvanceAt && visible === state.revealAnswersVisible ? state : { ...state, revealAnswersVisible: visible, revealAdvanceAt: advanceAt, updatedAt: now };
}

export const serverGame: ServerGame<SchaetzoramaState, SchaetzoramaInput, SchaetzoramaPublicState> = {
  manifest: schaetzoramaManifest,
  createInitialState(context) {
    const previousState = previousSchaetzoramaState(context);
    const questionRound = questionRoundFor(context.roundNumber, context.language, previousState);

    return {
      ...createBaseRoundState("round_intro", context.now, {
        durationMs: roundPhaseDurations.roundIntroMs,
        message: context.language === "en" ? "Guess-o-Rama is warming up the sliders." : "Schaetzorama faehrt die Regler hoch."
      }),
      stage: "answering",
      roundContent: questionRound.roundContent,
      usedQuestionIdsByCategory: questionRound.usedQuestionIdsByCategory,
      answersByPlayerId: {},
      jokerPreviewByPlayerId: {},
      jokerByPlayerId: {},
      jokerInventoryByPlayerId: createInventories(context.players, previousState, questionRound.roundContent.roundIndex),
      answerEndsAt: null,
      jokerEndsAt: null,
      revealedAt: null,
      revealAnswersVisible: false,
      revealStep: 0,
      revealStepStartedAt: null,
      revealReadyAt: null,
      revealAdvanceAt: null,
      revealReadyByPlayerId: {},
      autoContinueByPlayerId: questionRound.roundContent.roundIndex === 1 ? {} : previousState?.autoContinueByPlayerId ?? {},
      results: []
    };
  },
  startRound(state, context) {
    return transitionRoundState(
      {
        ...state,
        stage: "answering",
        answersByPlayerId: {},
        jokerPreviewByPlayerId: {},
        jokerByPlayerId: {},
        answerEndsAt: null,
        jokerEndsAt: null,
        revealedAt: null,
        revealAnswersVisible: false,
        revealStep: 0,
        revealStepStartedAt: null,
        revealReadyAt: null,
        revealAdvanceAt: null,
        revealReadyByPlayerId: {},
        results: []
      },
      "playing",
      context.now,
      {
        startedAt: context.now,
        message: context.language === "en" ? "Set all four panels. Copying comes after lock-in." : "Alle vier Pulte einstellen. Abschreiben kommt nach dem Einloggen."
      }
    );
  },
  handleInput(state, input, context) {
    if (!context.players.some((player) => player.id === input.playerId)) return state;
    if (input.type === "set_auto_continue" && state.stage === "revealed" && typeof input.enabled === "boolean") {
      if (state.autoContinueByPlayerId[input.playerId] === input.enabled) return state;
      const next = { ...state, autoContinueByPlayerId: { ...state.autoContinueByPlayerId, [input.playerId]: input.enabled }, updatedAt: context.now };
      return state.phase === "playing" ? updateRevealGate(next, context.players, context.now) : next;
    }
    if (state.phase !== "playing") return state;
    if (input.type === "reveal_ready" && state.stage === "revealed" && input.step === state.revealStep && state.revealStep < 4 && state.revealReadyAt !== null && context.now >= state.revealReadyAt) {
      if (state.revealReadyByPlayerId[input.playerId]) return state;
      return updateRevealGate({ ...state, revealReadyByPlayerId: { ...state.revealReadyByPlayerId, [input.playerId]: true }, updatedAt: context.now }, context.players, context.now);
    }

    if (input.type === "submit_answers" && state.stage === "answering") {
      if (Object.prototype.hasOwnProperty.call(state.answersByPlayerId, input.playerId)) {
        return state;
      }

      const nextState: SchaetzoramaState = {
        ...state,
        answersByPlayerId: {
          ...state.answersByPlayerId,
          [input.playerId]: {
            answers: sanitizeAnswers(input.answers, state.roundContent.questions),
            submittedAt: context.now
          }
        },
        updatedAt: context.now
      };

      return allPlayersAnswered(nextState, context.players) ? enterCopyStage(nextState, context.players, context.now, context.language) : nextState;
    }

    if (input.type === "preview_joker" && state.stage === "joker") {
      if (
        Object.prototype.hasOwnProperty.call(state.jokerByPlayerId, input.playerId) ||
        Object.prototype.hasOwnProperty.call(state.jokerPreviewByPlayerId, input.playerId)
      ) {
        return state;
      }

      const inventory = state.jokerInventoryByPlayerId[input.playerId] ?? startingInventory;

      if (!isValidJoker(input.joker, input.playerId, inventory, context.players)) {
        return state;
      }

      return {
        ...state,
        jokerPreviewByPlayerId: {
          ...state.jokerPreviewByPlayerId,
          [input.playerId]: input.joker
        },
        jokerInventoryByPlayerId: {
          ...state.jokerInventoryByPlayerId,
          [input.playerId]: spendJoker(inventory, input.joker)
        },
        updatedAt: context.now
      };
    }

    if (input.type === "choose_joker" && state.stage === "joker") {
      if (Object.prototype.hasOwnProperty.call(state.jokerByPlayerId, input.playerId)) {
        return state;
      }

      const preview = state.jokerPreviewByPlayerId[input.playerId];
      const selectedJoker = input.joker?.kind === "copy" ? input.joker : null;
      const joker = selectedJoker && preview && isSameJokerSelection(preview, selectedJoker) && isValidCopyTarget(selectedJoker, input.playerId, context.players)
        ? selectedJoker
        : null;
      const nextState: SchaetzoramaState = {
        ...state,
        jokerByPlayerId: {
          ...state.jokerByPlayerId,
          [input.playerId]: joker
        },
        updatedAt: context.now
      };

      return allPlayersHandledJoker(nextState, context.players)
        ? revealState(nextState, context.players, context.now, context.language)
        : nextState;
    }

    return state;
  },
  tick(state, _deltaMs, context) {
    if (state.phase !== "playing") {
      return state;
    }

    if (state.stage === "answering" && allPlayersAnswered(state, context.players)) return enterCopyStage(state, context.players, context.now, context.language);
    if (state.stage === "joker" && allPlayersHandledJoker(state, context.players)) return revealState(state, context.players, context.now, context.language);
    if (state.stage === "revealed") {
      const next = updateRevealGate(state, context.players, context.now);
      if (next.revealAdvanceAt !== null && context.now >= next.revealAdvanceAt) {
        const step = next.revealStep + 1;
        return { ...next, revealAnswersVisible: false, revealStep: step, revealStepStartedAt: context.now,
          revealReadyAt: step < 4 ? context.now + answersVisibleMs(next.roundContent.questions[revealCategories[step]], next.results.length) : null,
          revealReadyByPlayerId: {}, revealAdvanceAt: step === 4 ? context.now + 4000 : step === 5 ? context.now + 5000 : null, updatedAt: context.now };
      }
      return next;
    }

    return state;
  },
  isRoundFinished(state) {
    return state.stage === "revealed" && state.revealStep >= 6;
  },
  buildScore(state) {
    return state.results.map((result) => ({
      playerId: result.playerId,
      delta: result.total,
      reason: "Schaetzorama"
    }));
  },
  toPublicState(state, context) {
    return toPublicSchaetzoramaState(state, context.players, context.language, context.now);
  },
  toControllerStateForPlayer(state, context, playerId) {
    const publicState = toPublicSchaetzoramaState(state, context.players, context.language, context.now);
    const inventory = state.jokerInventoryByPlayerId[playerId] ?? startingInventory;
    const preview = state.jokerPreviewByPlayerId[playerId];

    return {
      ...publicState,
      results: [],
      solutions: {},
      standings: [],
      playerId,
      ownAnswers: state.answersByPlayerId[playerId]?.answers ?? {},
      ownJokerPreview: preview ?? null,
      ownJoker: Object.prototype.hasOwnProperty.call(state.jokerByPlayerId, playerId)
        ? state.jokerByPlayerId[playerId]
        : undefined,
      ownInventory: inventory,
      canSubmitAnswers:
        state.phase === "playing" &&
        state.stage === "answering" &&
        !Object.prototype.hasOwnProperty.call(state.answersByPlayerId, playerId),
      canSubmitJoker:
        state.phase === "playing" &&
        state.stage === "joker" &&
        !Object.prototype.hasOwnProperty.call(state.jokerByPlayerId, playerId),
      copyTargets: context.players
        .filter((player) => player.id !== playerId)
        .map((player) => {
          const previewAnswer =
            state.stage === "joker" && preview?.targetPlayerId === player.id
              ? state.answersByPlayerId[player.id]?.answers[preview.categoryId]
              : undefined;

          return {
            playerId: player.id,
            name: player.name,
            answers: previewAnswer ? ({ [preview.categoryId]: previewAnswer } as SchaetzoramaAnswerSet) : undefined
          };
        })
    } satisfies SchaetzoramaControllerState;
  }
};
