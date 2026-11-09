import {
  createBaseRoundState,
  roundPhaseDurations,
  transitionRoundState,
  type ServerGame,
  type ServerGameContext
} from "@open-party-lab/game-core";
import { shuffle } from "@open-party-lab/utils";
import type {
  BuzzwortCard,
  BuzzwortControllerState,
  BuzzwortEvent,
  BuzzwortEventKind,
  BuzzwortHostAction,
  BuzzwortInput,
  BuzzwortMode,
  BuzzwortRole,
  BuzzwortState,
  BuzzwortTeamId
} from "../protocol.js";
import { buzzwortManifest } from "../manifest.js";
import { buildBuzzwortScoreEntries } from "./buzzwortScoring.js";
import { resolveBuzzwortDeck } from "./content/buzzwortCards.js";
import {
  resolveModeLabel,
  resolveRoleLabel,
  resolveBuzzwortTexts,
  resolveTeamLabel,
  type BuzzwortTexts
} from "./buzzwortText.js";

const MODE_SETTING_KEY = "buzzwort_mode";
const WATCHER_SETTING_KEY = "buzzwort_watcher";
const TURN_MS_SETTING_KEY = "buzzwort_turn_ms";

export const BUZZWORT_TURN_DURATION_OPTIONS = [45_000, 60_000, 90_000] as const;
const DEFAULT_TURN_DURATION_MS = 60_000;

const PREVIEW_MS = 4_000;
const HANDOVER_MS = 3_500;
const MAX_SKIPS_PER_TURN = 2;
const TEAM_TURNS_PER_SIDE = 3;
const MAX_DUEL_TURNS = 10;
const MAX_EVENTS = 14;

type PlayerSummary = ServerGameContext["players"][number];

/* -------------------------------------------------------------------------- */
/* Settings                                                                    */
/* -------------------------------------------------------------------------- */

function resolveMode(roomSettings: Readonly<Record<string, unknown>>): BuzzwortMode {
  return roomSettings[MODE_SETTING_KEY] === "team" ? "team" : "duel";
}

function resolveWatcherEnabled(roomSettings: Readonly<Record<string, unknown>>): boolean {
  return roomSettings[WATCHER_SETTING_KEY] !== false && roomSettings[WATCHER_SETTING_KEY] !== "off";
}

function resolveTurnDurationMs(roomSettings: Readonly<Record<string, unknown>>): number {
  const raw = roomSettings[TURN_MS_SETTING_KEY];
  const parsed = typeof raw === "number" ? raw : Number(raw);

  return BUZZWORT_TURN_DURATION_OPTIONS.includes(parsed as (typeof BUZZWORT_TURN_DURATION_OPTIONS)[number])
    ? parsed
    : DEFAULT_TURN_DURATION_MS;
}

/* -------------------------------------------------------------------------- */
/* Helpers                                                                     */
/* -------------------------------------------------------------------------- */

function getPlayerName(players: readonly PlayerSummary[], texts: BuzzwortTexts, playerId?: string): string {
  if (!playerId) {
    return texts.unknownPlayer;
  }

  return players.find((player) => player.id === playerId)?.name ?? texts.unknownPlayer;
}

function buildTeamAssignments(players: readonly PlayerSummary[]): {
  teamByPlayerId: Record<string, BuzzwortTeamId>;
  teamMembersByTeamId: Record<BuzzwortTeamId, string[]>;
} {
  const teamMembersByTeamId: Record<BuzzwortTeamId, string[]> = { team1: [], team2: [] };
  const teamByPlayerId: Record<string, BuzzwortTeamId> = {};

  players.forEach((player, index) => {
    const teamId: BuzzwortTeamId = index % 2 === 0 ? "team1" : "team2";
    teamByPlayerId[player.id] = teamId;
    teamMembersByTeamId[teamId].push(player.id);
  });

  return { teamByPlayerId, teamMembersByTeamId };
}

function livingMembers(memberIds: readonly string[], players: readonly PlayerSummary[]): string[] {
  return memberIds.filter((memberId) => players.some((player) => player.id === memberId));
}

function opposingTeam(teamId: BuzzwortTeamId): BuzzwortTeamId {
  return teamId === "team1" ? "team2" : "team1";
}

function drawCard(queue: BuzzwortCard[], deck: readonly BuzzwortCard[]): { card?: BuzzwortCard; queue: BuzzwortCard[] } {
  const next = queue.length > 0 ? queue : shuffle(deck);

  return { card: next[0], queue: next.slice(1) };
}

function pushEvent(
  events: readonly BuzzwortEvent[],
  event: Omit<BuzzwortEvent, "id">
): BuzzwortEvent[] {
  const id = `${event.kind}-${event.at}-${events.length}`;
  return [{ ...event, id }, ...events].slice(0, MAX_EVENTS);
}

function totalTurnsFor(mode: BuzzwortMode, playerCount: number): number {
  if (mode === "team") {
    return TEAM_TURNS_PER_SIDE * 2;
  }

  return Math.max(1, Math.min(playerCount, MAX_DUEL_TURNS));
}

/* -------------------------------------------------------------------------- */
/* Turn assignment                                                             */
/* -------------------------------------------------------------------------- */

interface TurnAssignment {
  explainerId?: string;
  teamId?: BuzzwortTeamId;
  watcherId?: string;
  teamTurnCursor: Record<BuzzwortTeamId, number>;
  watcherCursor: number;
}

/**
 * Bestimmt Erklaerer und Waechter fuer den Zug `turnIndex`.
 *
 * Ein Waechter wird nur vergeben, wenn danach noch mindestens ein Rater uebrig
 * bleibt. Sonst wuerde ein Zug mit niemandem zum Raten entstehen.
 */
function assignTurn(state: BuzzwortState, context: ServerGameContext, turnIndex: number): TurnAssignment {
  const players = context.players;
  const teamTurnCursor = { ...state.teamTurnCursor };
  let watcherCursor = state.watcherCursor;

  if (state.mode === "team") {
    const teamId: BuzzwortTeamId = turnIndex % 2 === 0 ? "team1" : "team2";
    const ownMembers = livingMembers(state.teamMembersByTeamId[teamId] ?? [], players);
    const enemyMembers = livingMembers(state.teamMembersByTeamId[opposingTeam(teamId)] ?? [], players);
    const explainerId = ownMembers.length > 0 ? ownMembers[teamTurnCursor[teamId] % ownMembers.length] : players[0]?.id;

    teamTurnCursor[teamId] = (teamTurnCursor[teamId] ?? 0) + 1;

    const canWatch = state.watcherEnabled && ownMembers.length >= 2 && enemyMembers.length >= 1;
    const watcherId = canWatch ? enemyMembers[watcherCursor % enemyMembers.length] : undefined;

    if (canWatch) {
      watcherCursor += 1;
    }

    return { explainerId, teamId, watcherId, teamTurnCursor, watcherCursor };
  }

  const order = state.turnOrderPlayerIds.filter((playerId) => players.some((player) => player.id === playerId));
  const explainerId = order.length > 0 ? order[turnIndex % order.length] : players[0]?.id;
  const canWatch = state.watcherEnabled && order.length >= 3;
  const watcherId = canWatch ? order[(turnIndex + 1) % order.length] : undefined;

  return { explainerId, teamId: undefined, watcherId, teamTurnCursor, watcherCursor };
}

/* -------------------------------------------------------------------------- */
/* Turn lifecycle                                                              */
/* -------------------------------------------------------------------------- */

function beginTurn(state: BuzzwortState, context: ServerGameContext, turnIndex: number): BuzzwortState {
  const texts = resolveBuzzwortTexts(context.language);
  const deck = resolveBuzzwortDeck(context.language);
  const assignment = assignTurn(state, context, turnIndex);
  const { card, queue } = drawCard(state.cardQueue, deck);
  const explainerName = getPlayerName(context.players, texts, assignment.explainerId);

  return {
    ...state,
    turnIndex,
    turnPhase: "preview",
    turnPhaseEndsAt: context.now + PREVIEW_MS,
    turnEndsAt: null,
    turnSolved: 0,
    turnSkipsUsed: 0,
    turnViolations: 0,
    currentTurnPlayerId: assignment.explainerId,
    currentTurnTeamId: assignment.teamId,
    currentWatcherPlayerId: assignment.watcherId,
    teamTurnCursor: assignment.teamTurnCursor,
    watcherCursor: assignment.watcherCursor,
    cardQueue: queue,
    currentCard: card,
    remainingCards: queue.length,
    message: texts.getReady(explainerName)
  };
}

function activateTurn(state: BuzzwortState, context: ServerGameContext): BuzzwortState {
  const texts = resolveBuzzwortTexts(context.language);
  const explainerName = getPlayerName(context.players, texts, state.currentTurnPlayerId);
  const teamLabel = resolveTeamLabel(texts, state.currentTurnTeamId);
  const watcherName = state.currentWatcherPlayerId
    ? getPlayerName(context.players, texts, state.currentWatcherPlayerId)
    : undefined;
  const message = watcherName
    ? texts.turnStartWithWatcher(explainerName, watcherName, teamLabel)
    : texts.turnStart(explainerName, teamLabel);

  return {
    ...state,
    turnPhase: "active",
    turnPhaseEndsAt: context.now + state.turnDurationMs,
    turnEndsAt: context.now + state.turnDurationMs,
    message,
    events: pushEvent(state.events, {
      kind: "turn_start",
      at: context.now,
      actorName: explainerName,
      targetName: watcherName,
      teamId: state.currentTurnTeamId
    })
  };
}

function endTurn(state: BuzzwortState, context: ServerGameContext): BuzzwortState {
  const texts = resolveBuzzwortTexts(context.language);
  const explainerName = getPlayerName(context.players, texts, state.currentTurnPlayerId);
  const summary = texts.turnSummary(explainerName, state.turnSolved, state.turnViolations);

  return {
    ...state,
    turnPhase: "handover",
    turnPhaseEndsAt: context.now + HANDOVER_MS,
    turnEndsAt: null,
    lastTurnSummary: summary,
    message: summary,
    events: pushEvent(state.events, {
      kind: "turn_end",
      at: context.now,
      actorName: explainerName,
      teamId: state.currentTurnTeamId,
      points: state.turnSolved
    })
  };
}

function completeRound(state: BuzzwortState, context: ServerGameContext): BuzzwortState {
  const texts = resolveBuzzwortTexts(context.language);

  if (state.mode === "team") {
    const a = state.scoreByTeamId.team1 ?? 0;
    const b = state.scoreByTeamId.team2 ?? 0;
    const winner = a === b ? null : resolveTeamLabel(texts, a > b ? "team1" : "team2") ?? null;

    return {
      ...state,
      roundComplete: true,
      turnPhase: "handover",
      turnPhaseEndsAt: null,
      turnEndsAt: null,
      currentCard: undefined,
      message: texts.finalTeam(winner, a, b)
    };
  }

  const ranked = Object.entries(state.scoreByPlayerId).sort((left, right) => right[1] - left[1]);
  const leader = ranked[0];

  return {
    ...state,
    roundComplete: true,
    turnPhase: "handover",
    turnPhaseEndsAt: null,
    turnEndsAt: null,
    currentCard: undefined,
    message: texts.finalDuel(leader ? getPlayerName(context.players, texts, leader[0]) : null, leader?.[1] ?? 0)
  };
}

/* -------------------------------------------------------------------------- */
/* Player actions                                                              */
/* -------------------------------------------------------------------------- */

function addTeamScore(
  scoreByTeamId: Record<BuzzwortTeamId, number>,
  teamId: BuzzwortTeamId | undefined,
  delta: number
): Record<BuzzwortTeamId, number> {
  if (!teamId) {
    return scoreByTeamId;
  }

  return { ...scoreByTeamId, [teamId]: (scoreByTeamId[teamId] ?? 0) + delta };
}

function addPlayerScore(
  scoreByPlayerId: Record<string, number>,
  playerId: string | undefined,
  delta: number
): Record<string, number> {
  if (!playerId) {
    return scoreByPlayerId;
  }

  return { ...scoreByPlayerId, [playerId]: (scoreByPlayerId[playerId] ?? 0) + delta };
}

function nextCardState(state: BuzzwortState, context: ServerGameContext): Pick<BuzzwortState, "cardQueue" | "currentCard" | "remainingCards"> {
  const deck = resolveBuzzwortDeck(context.language);
  const { card, queue } = drawCard(state.cardQueue, deck);

  return { cardQueue: queue, currentCard: card, remainingCards: queue.length };
}

function handleCorrect(state: BuzzwortState, input: BuzzwortInput, context: ServerGameContext): BuzzwortState {
  if (state.currentTurnPlayerId !== input.playerId || !state.currentCard) {
    return state;
  }

  const texts = resolveBuzzwortTexts(context.language);
  const term = state.currentCard.term;
  let scoreByPlayerId = state.scoreByPlayerId;
  let scoreByTeamId = state.scoreByTeamId;
  let solverName: string;

  if (state.mode === "team") {
    scoreByTeamId = addTeamScore(scoreByTeamId, state.currentTurnTeamId, 1);
    scoreByPlayerId = addPlayerScore(scoreByPlayerId, input.playerId, 1);
    solverName = resolveTeamLabel(texts, state.currentTurnTeamId) ?? getPlayerName(context.players, texts, input.playerId);
  } else {
    const guesser = context.players.find(
      (player) => player.id === input.guessedPlayerId && player.id !== input.playerId
    );

    if (!guesser) {
      return state;
    }

    if (guesser.id === state.currentWatcherPlayerId) {
      // Der Waechter kennt die Karte und darf nicht punkten.
      return state;
    }

    scoreByPlayerId = addPlayerScore(scoreByPlayerId, guesser.id, 1);
    scoreByPlayerId = addPlayerScore(scoreByPlayerId, input.playerId, 1);
    solverName = guesser.name;
  }

  return {
    ...state,
    ...nextCardState(state, context),
    scoreByPlayerId,
    scoreByTeamId,
    solvedTerms: state.solvedTerms + 1,
    turnSolved: state.turnSolved + 1,
    lastSolvedTerm: term,
    lastSolverName: solverName,
    message: texts.solved(term, solverName),
    events: pushEvent(state.events, {
      kind: "solved",
      at: context.now,
      term,
      actorName: solverName,
      teamId: state.currentTurnTeamId,
      points: 1
    })
  };
}

function handleSkip(state: BuzzwortState, input: BuzzwortInput, context: ServerGameContext): BuzzwortState {
  if (state.currentTurnPlayerId !== input.playerId || !state.currentCard) {
    return state;
  }

  if (state.turnSkipsUsed >= state.maxSkipsPerTurn) {
    return state;
  }

  const texts = resolveBuzzwortTexts(context.language);
  const term = state.currentCard.term;

  return {
    ...state,
    ...nextCardState(state, context),
    turnSkipsUsed: state.turnSkipsUsed + 1,
    message: texts.skipped(term),
    events: pushEvent(state.events, {
      kind: "skipped",
      at: context.now,
      term,
      actorName: getPlayerName(context.players, texts, input.playerId)
    })
  };
}

function handleViolation(state: BuzzwortState, input: BuzzwortInput, context: ServerGameContext): BuzzwortState {
  if (state.currentWatcherPlayerId !== input.playerId || !state.currentCard) {
    return state;
  }

  const texts = resolveBuzzwortTexts(context.language);
  const term = state.currentCard.term;
  const watcherName = getPlayerName(context.players, texts, input.playerId);
  const watcherTeamId = state.mode === "team" ? state.teamByPlayerId[input.playerId] : undefined;

  return {
    ...state,
    ...nextCardState(state, context),
    turnViolations: state.turnViolations + 1,
    scoreByTeamId: addTeamScore(state.scoreByTeamId, watcherTeamId, 1),
    scoreByPlayerId: addPlayerScore(state.scoreByPlayerId, input.playerId, 1),
    message: texts.violation(term, watcherName),
    events: pushEvent(state.events, {
      kind: "buzz",
      at: context.now,
      term,
      actorName: watcherName,
      teamId: watcherTeamId,
      points: 1
    })
  };
}

/* -------------------------------------------------------------------------- */
/* View state                                                                  */
/* -------------------------------------------------------------------------- */

function resolveRole(state: BuzzwortState, playerId?: string): BuzzwortRole {
  if (!playerId) {
    return "bench";
  }

  if (state.currentTurnPlayerId === playerId) {
    return "explainer";
  }

  if (state.currentWatcherPlayerId === playerId) {
    return "watcher";
  }

  if (state.mode === "team") {
    return state.teamByPlayerId[playerId] === state.currentTurnTeamId ? "guesser" : "bench";
  }

  return "guesser";
}

function remainingMs(endsAt: number | null, now: number): number | null {
  return endsAt === null ? null : Math.max(0, endsAt - now);
}

function buildViewState(
  state: BuzzwortState,
  context: ServerGameContext,
  playerId?: string
): BuzzwortControllerState {
  const texts = resolveBuzzwortTexts(context.language);
  const role = playerId ? resolveRole(state, playerId) : "bench";
  const canSeeCard = role === "explainer" || role === "watcher";
  const guessTargetPlayerIds =
    state.mode === "duel" && role === "explainer"
      ? context.players
          .map((player) => player.id)
          .filter((candidate) => candidate !== playerId && candidate !== state.currentWatcherPlayerId)
      : [];

  return {
    mode: state.mode,
    modeLabel: resolveModeLabel(texts, state.mode),
    watcherEnabled: state.watcherEnabled,

    turnPhase: state.turnPhase,
    turnPhaseRemainingMs: remainingMs(state.turnPhaseEndsAt, context.now),
    turnDurationMs: state.turnDurationMs,
    turnEndsAt: state.turnEndsAt,
    turnRemainingMs: remainingMs(state.turnEndsAt, context.now),

    turnIndex: state.turnIndex,
    totalTurns: state.totalTurns,

    role,
    roleLabel: resolveRoleLabel(texts, role),
    isExplainer: role === "explainer",
    isWatcher: role === "watcher",

    card:
      canSeeCard && state.currentCard
        ? {
            term: state.currentCard.term,
            forbidden: state.currentCard.forbidden,
            category: state.currentCard.category,
            difficulty: state.currentCard.difficulty
          }
        : undefined,

    turnSolved: state.turnSolved,
    skipsLeft: Math.max(0, state.maxSkipsPerTurn - state.turnSkipsUsed),
    turnViolations: state.turnViolations,

    currentTurnPlayerId: state.currentTurnPlayerId,
    currentTurnTeamId: state.currentTurnTeamId,
    currentWatcherPlayerId: state.currentWatcherPlayerId,
    activeTeamLabel: resolveTeamLabel(texts, state.currentTurnTeamId),
    myTeamId: playerId ? state.teamByPlayerId[playerId] : undefined,

    scoreByPlayerId: state.scoreByPlayerId,
    scoreByTeamId: state.scoreByTeamId,
    teamByPlayerId: state.teamByPlayerId,
    teamMembersByTeamId: state.teamMembersByTeamId,

    guessTargetPlayerIds,
    remainingCards: state.remainingCards,
    solvedTerms: state.solvedTerms,
    lastSolvedTerm: state.lastSolvedTerm,
    lastSolverName: state.lastSolverName,
    lastTurnSummary: state.lastTurnSummary,
    events: state.events
  };
}

/* -------------------------------------------------------------------------- */
/* Server game                                                                 */
/* -------------------------------------------------------------------------- */

export const serverGame: ServerGame<BuzzwortState, BuzzwortInput, BuzzwortControllerState> = {
  manifest: buzzwortManifest,
  createInitialState(context) {
    const mode = resolveMode(context.roomSettings);
    const texts = resolveBuzzwortTexts(context.language);

    return {
      ...createBaseRoundState("round_intro", context.now, {
        durationMs: roundPhaseDurations.roundIntroMs,
        message: texts.preparing
      }),
      mode,
      watcherEnabled: resolveWatcherEnabled(context.roomSettings),

      turnPhase: "preview",
      turnPhaseEndsAt: null,
      turnDurationMs: resolveTurnDurationMs(context.roomSettings),
      turnEndsAt: null,

      turnIndex: 0,
      totalTurns: totalTurnsFor(mode, context.players.length),
      roundComplete: false,

      turnSolved: 0,
      turnSkipsUsed: 0,
      turnViolations: 0,
      maxSkipsPerTurn: MAX_SKIPS_PER_TURN,

      currentTurnPlayerId: undefined,
      currentTurnTeamId: undefined,
      currentWatcherPlayerId: undefined,

      scoreByPlayerId: {},
      scoreByTeamId: { team1: 0, team2: 0 },
      teamByPlayerId: {},
      teamMembersByTeamId: { team1: [], team2: [] },

      turnOrderPlayerIds: [],
      teamTurnCursor: { team1: 0, team2: 0 },
      watcherCursor: 0,

      cardQueue: [],
      currentCard: undefined,
      remainingCards: 0,

      solvedTerms: 0,
      lastSolvedTerm: undefined,
      lastSolverName: undefined,
      lastTurnSummary: undefined,
      events: []
    };
  },
  handleHostAction(_state, action, _context) {
    const hostAction = action as Partial<BuzzwortHostAction> | null;

    if (hostAction?.type === "set_mode" && (hostAction.mode === "duel" || hostAction.mode === "team")) {
      return { roomSettings: { [MODE_SETTING_KEY]: hostAction.mode } };
    }

    if (hostAction?.type === "set_watcher" && typeof hostAction.watcherEnabled === "boolean") {
      return { roomSettings: { [WATCHER_SETTING_KEY]: hostAction.watcherEnabled } };
    }

    if (
      hostAction?.type === "set_turn_duration" &&
      typeof hostAction.turnDurationMs === "number" &&
      BUZZWORT_TURN_DURATION_OPTIONS.includes(hostAction.turnDurationMs as (typeof BUZZWORT_TURN_DURATION_OPTIONS)[number])
    ) {
      return { roomSettings: { [TURN_MS_SETTING_KEY]: hostAction.turnDurationMs } };
    }

    return {};
  },
  startRound(state, context) {
    const mode = resolveMode(context.roomSettings);
    const texts = resolveBuzzwortTexts(context.language);
    const deck = resolveBuzzwortDeck(context.language);
    const teamAssignments = buildTeamAssignments(context.players);
    const cardQueue = shuffle(deck);

    const base: BuzzwortState = {
      ...state,
      mode,
      // Im Teammodus gehoert der Waechter zu den Regeln und ist immer aktiv.
      watcherEnabled: mode === "team" ? true : resolveWatcherEnabled(context.roomSettings),
      turnDurationMs: resolveTurnDurationMs(context.roomSettings),
      turnIndex: 0,
      totalTurns: totalTurnsFor(mode, context.players.length),
      roundComplete: false,
      turnSolved: 0,
      turnSkipsUsed: 0,
      turnViolations: 0,
      maxSkipsPerTurn: MAX_SKIPS_PER_TURN,
      scoreByPlayerId: {},
      scoreByTeamId: { team1: 0, team2: 0 },
      teamByPlayerId: mode === "team" ? teamAssignments.teamByPlayerId : {},
      teamMembersByTeamId:
        mode === "team" ? teamAssignments.teamMembersByTeamId : { team1: [], team2: [] },
      turnOrderPlayerIds: shuffle(context.players.map((player) => player.id)),
      teamTurnCursor: { team1: 0, team2: 0 },
      watcherCursor: 0,
      cardQueue,
      currentCard: undefined,
      remainingCards: cardQueue.length,
      solvedTerms: 0,
      lastSolvedTerm: undefined,
      lastSolverName: undefined,
      lastTurnSummary: undefined,
      events: []
    };

    const opened = beginTurn(base, context, 0);
    const startMessage = opened.currentCard ? opened.message : texts.noCards;

    return transitionRoundState({ ...opened, message: startMessage }, "playing", context.now, {
      startedAt: context.now,
      message: startMessage
    });
  },
  handleInput(state, input, context) {
    if (state.phase !== "playing" || state.turnPhase !== "active" || state.roundComplete) {
      return state;
    }

    switch (input.type) {
      case "buzzwort_correct":
        return handleCorrect(state, input, context);
      case "buzzwort_skip":
        return handleSkip(state, input, context);
      case "buzzwort_violation":
        return handleViolation(state, input, context);
      default:
        return state;
    }
  },
  tick(state, _deltaMs, context) {
    if (state.phase !== "playing" || state.roundComplete) {
      return state;
    }

    if (!state.currentCard && state.turnPhase !== "handover") {
      return completeRound(state, context);
    }

    if (state.turnPhaseEndsAt === null || context.now < state.turnPhaseEndsAt) {
      return state;
    }

    if (state.turnPhase === "preview") {
      return activateTurn(state, context);
    }

    if (state.turnPhase === "active") {
      return endTurn(state, context);
    }

    const nextTurnIndex = state.turnIndex + 1;

    if (nextTurnIndex >= state.totalTurns) {
      return completeRound(state, context);
    }

    return beginTurn(state, context, nextTurnIndex);
  },
  isRoundFinished(state) {
    return state.phase === "playing" && state.roundComplete;
  },
  buildScore(state) {
    return buildBuzzwortScoreEntries({
      mode: state.mode,
      scoreByPlayerId: state.scoreByPlayerId,
      scoreByTeamId: state.scoreByTeamId,
      teamMembersByTeamId: state.teamMembersByTeamId
    });
  },
  toPublicState(state, context) {
    return buildViewState(state, context);
  },
  toControllerStateForPlayer(state, context, playerId) {
    return buildViewState(state, context, playerId);
  }
};

export type { BuzzwortState, BuzzwortControllerState, BuzzwortEventKind };
