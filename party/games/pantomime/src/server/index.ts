import { createBaseRoundState, transitionRoundState, roundPhaseDurations, type ServerGame, type ServerGameContext } from "@open-party-lab/game-core";
import { pantomimeManifest } from "../manifest.js";
import type { PantomimeInput, PantomimeState, PantomimeTeamId } from "../protocol.js";
import { terms } from "../terms.js";

const inputLockMs = 2000;

function numberSetting(value: unknown, fallback: number, min: number, max: number): number {
  if (value === undefined || value === null || value === "") return fallback;
  const n = Number(value);
  return Number.isFinite(n) ? Math.max(min, Math.min(max, Math.round(n))) : fallback;
}
function connected(context: ServerGameContext, id: string | undefined) {
  return context.players.some((p) => p.id === id && p.connected !== false);
}
function mayGuess(state: PantomimeState, id: string | undefined): boolean {
  const player = state.players.find((p) => p.id === id);
  return Boolean(player && id !== state.actorPlayerId && (state.mode === "solo" || player.teamId === state.actorTeamId));
}
function draw(state: PantomimeState, context: ServerGameContext): PantomimeState {
  const pool = terms.filter((term) => state.category === "all" || term.category === state.category);
  let used = state.usedTermIds ?? [];
  let available = pool.filter((term) => !used.includes(term.id));
  if (!available.length) { used = []; available = [...pool]; }
  const term = available[Math.floor(Math.random() * available.length)]!;
  return { ...state, wordToken: state.wordToken + 1, secretTerm: term[context.language === "en" ? "en" : "de"], usedTermIds: [...used, term.id] };
}
function recalculate(state: PantomimeState): PantomimeState {
  const gameScores = { ...state.turnBaseScores };
  const teamScores = { ...state.turnBaseTeamScores };
  for (const entry of state.entries) {
    if (entry.status !== "guessed" || !entry.guesserPlayerId || !mayGuess(state, entry.guesserPlayerId)) continue;
    if (state.mode === "team" && state.actorTeamId) teamScores[state.actorTeamId]++;
    else {
      gameScores[entry.guesserPlayerId] = (gameScores[entry.guesserPlayerId] ?? 0) + 1;
      if (state.actorPlayerId) gameScores[state.actorPlayerId] = (gameScores[state.actorPlayerId] ?? 0) + 1;
    }
  }
  return { ...state, gameScores, teamScores };
}
function review(state: PantomimeState): PantomimeState {
  const entries = state.secretTerm ? [...state.entries, {
    id: `${state.turnNumber}:${state.wordToken}`, term: state.secretTerm, status: "timeout" as const
  }] : state.entries;
  return { ...state, stage: "review", entries, secretTerm: undefined, finishAt: null, inputLockedUntil: null, lastGuess: undefined };
}
function nextTurn(state: PantomimeState, context: ServerGameContext): PantomimeState {
  // Preserve team membership through reconnects; add late arrivals only between turns.
  const players = state.players.map((p) => ({ ...p, name: context.players.find((current) => current.id === p.id)?.name ?? p.name }));
  for (const player of context.players.filter((p) => p.connected !== false && !players.some((old) => old.id === p.id))) {
    const team1 = players.filter((p) => p.teamId === "team1").length;
    const team2 = players.length - team1;
    players.push({ id: player.id, name: player.name, teamId: team1 <= team2 ? "team1" : "team2" });
  }
  const available = players.filter((p) => connected(context, p.id));
  const team: PantomimeTeamId = state.turnNumber % 2 === 0 ? "team1" : "team2";
  const candidates = state.mode === "team" ? available.filter((p) => p.teamId === team) : available;
  const history = state.actorHistory ?? [];
  const actor = candidates.find((p) => !history.includes(p.id)) ?? candidates[0];
  const actorHistory = actor ? [...(candidates.every((p) => history.includes(p.id))
    ? history.filter((id) => !candidates.some((p) => p.id === id)) : history), actor.id] : history;
  const gameScores = { ...state.gameScores };
  for (const player of players) gameScores[player.id] ??= 0;
  return { ...state, players, actorPlayerId: actor?.id, actorName: actor?.name, actorTeamId: actor?.teamId,
    actorHistory, turnNumber: state.turnNumber + 1, stage: "ready", skipsUsed: 0,
    secretTerm: undefined, finishAt: null, inputLockedUntil: null, lastGuess: undefined, entries: [], actorMissingSince: undefined,
    gameScores, turnBaseScores: { ...gameScores }, turnBaseTeamScores: { ...state.teamScores } };
}

export const serverGame: ServerGame<PantomimeState, PantomimeInput, Omit<PantomimeState, "turnBaseScores" | "turnBaseTeamScores">> = {
  manifest: pantomimeManifest,
  handleHostAction(state, action) {
    if (state?.phase === "playing" || !action || typeof action !== "object" || !("type" in action) || action.type !== "configure-lobby") return null;
    const values = action as Record<string, unknown>;
    const roomSettings: Record<string, unknown> = {};
    for (const field of pantomimeManifest.lobbySetup.fields) {
      const value = values[field.id];
      if (value === undefined) continue;
      if (field.kind === "number") {
        roomSettings[field.settingKey] = numberSetting(value, field.defaultValue, field.min, field.max);
      } else if (typeof value === "string" && field.options.some((option) => option.id === value)) {
        roomSettings[field.settingKey] = value;
      }
    }
    return Object.keys(roomSettings).length ? { roomSettings } : null;
  },
  createInitialState(context) {
    const setting = context.roomSettings ?? {};
    return { ...createBaseRoundState("round_intro", context.now, { durationMs: roundPhaseDurations.roundIntroMs }),
      roundId: `${context.roundNumber}:${context.now}`, stage: "ready", mode: setting.pantomimeMode === "team" ? "team" : "solo",
      category: setting.pantomimeWordCategory === "adult" || setting.pantomimeWordCategory === "all" ? setting.pantomimeWordCategory : "standard",
      durationMs: numberSetting(setting.pantomimeDurationSeconds, 60, 15, 300) * 1000,
      skipLimit: numberSetting(setting.pantomimeSkips, 3, 0, 10), skipsUsed: 0, turnNumber: 0, wordToken: 0,
      players: context.players.filter((p) => p.connected !== false).map((p, index) => ({ id: p.id, name: p.name, teamId: (index % 2 === 0 ? "team1" : "team2") as PantomimeTeamId })),
      finishAt: null, inputLockedUntil: null, entries: [], gameScores: Object.fromEntries(context.players.map((p) => [p.id, 0])), teamScores: { team1: 0, team2: 0 },
      turnBaseScores: {}, turnBaseTeamScores: { team1: 0, team2: 0 }, actorHistory: [], usedTermIds: [] };
  },
  startRound(state, context) {
    return nextTurn(transitionRoundState(state, "playing", context.now, { startedAt: context.now }), context);
  },
  handleInput(state, input, context) {
    if (state.phase !== "playing" || input.roundId !== state.roundId || input.turnNumber !== state.turnNumber ||
      !connected(context, input.playerId)) return state;
    const actor = input.playerId === state.actorPlayerId;
    if (state.stage === "ready") {
      if (!actor || input.type !== "pantomime:reveal" || !state.players.some((p) => mayGuess(state, p.id) && connected(context, p.id))) return state;
      return draw({ ...state, stage: "acting", finishAt: context.now + state.durationMs }, context);
    }
    if (state.stage === "review") {
      if (!actor) return state;
      if (input.type === "pantomime:continue") return nextTurn(state, context);
      if (input.type !== "pantomime:assign" || !state.entries.some((e) => e.id === input.entryId)) return state;
      const target = input.guesserPlayerId;
      if (target !== null && (!target || !mayGuess(state, target))) return state;
      return recalculate({ ...state, entries: state.entries.map((entry) => entry.id === input.entryId
        ? { ...entry, status: target ? "guessed" : "skipped", guesserPlayerId: target ?? undefined } : entry) });
    }
    if (state.finishAt === null || context.now >= state.finishAt || input.wordToken !== state.wordToken) return state;
    if (state.inputLockedUntil !== null && context.now < state.inputLockedUntil) return state;
    if (input.type === "pantomime:skip") {
      if (!actor || state.skipsUsed >= state.skipLimit) return state;
      return draw({ ...state, inputLockedUntil: context.now + inputLockMs, lastGuess: undefined, skipsUsed: state.skipsUsed + 1, entries: [...state.entries, {
        id: `${state.turnNumber}:${state.wordToken}`, term: state.secretTerm!, status: "skipped"
      }] }, context);
    }
    if (input.type !== "pantomime:claim" || !mayGuess(state, input.playerId)) return state;
    return draw(recalculate({ ...state, inputLockedUntil: context.now + inputLockMs,
      lastGuess: { term: state.secretTerm!, playerId: input.playerId }, entries: [...state.entries, {
      id: `${state.turnNumber}:${state.wordToken}`, term: state.secretTerm!, status: "guessed", guesserPlayerId: input.playerId
    }] }), context);
  },
  tick(state, _deltaMs, context) {
    if (state.phase !== "playing") return state;
    if (state.stage === "acting" && state.finishAt !== null && context.now >= state.finishAt) return review(state);
    if (state.inputLockedUntil !== null && context.now >= state.inputLockedUntil) {
      state = { ...state, inputLockedUntil: null, lastGuess: undefined };
    }
    if (state.stage === "acting" && !connected(context, state.actorPlayerId)) {
      if (state.actorMissingSince === undefined) return { ...state, actorMissingSince: context.now };
      if (context.now - state.actorMissingSince >= 15_000) return review(state);
    } else if (state.actorMissingSince !== undefined) return { ...state, actorMissingSince: undefined };
    return state;
  },
  isRoundFinished() { return false; }, // Actor controls turn handover inside the running game.
  buildScore() { return []; }, // Editable game-local totals are calculated from the turn ledger.
  toPublicState(state, context) {
    const { secretTerm, usedTermIds, actorHistory, turnBaseScores, turnBaseTeamScores, ...publicState } = state;
    // The authoritative room clock excludes pauses; clients display wall-clock deadlines.
    const wallNow = Date.now();
    const inputLockedUntil = state.inputLockedUntil !== null && state.inputLockedUntil > context.now
      ? wallNow + state.inputLockedUntil - context.now : null;
    return { ...publicState, inputLockedUntil, lastGuess: inputLockedUntil === null ? undefined : state.lastGuess,
      finishAt: state.finishAt === null ? null : wallNow + Math.max(0, state.finishAt - context.now) };
  },
  toControllerStateForPlayer(state, context, playerId) {
    const publicState = this.toPublicState!(state, context);
    return playerId === state.actorPlayerId && state.stage === "acting" ? { ...publicState, secretTerm: state.secretTerm } : publicState;
  }
};
