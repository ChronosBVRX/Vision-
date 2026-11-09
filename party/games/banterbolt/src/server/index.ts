import {
  createBaseRoundState,
  type ServerGame,
  type ServerGameContext,
  type ScoreEntry,
} from "@open-party-lab/game-core";
import { shuffle } from "@open-party-lab/utils";
import { banterboltManifest } from "../manifest.js";
import type {
  BanterboltControllerState,
  BanterboltInput,
  BanterboltMatchup,
  BanterboltPackId,
  BanterboltPlayer,
  BanterboltPrompt,
  BanterboltPublicMatchup,
  BanterboltPublicState,
  BanterboltState,
} from "../protocol.js";
import { promptsFor } from "../content/prompts.js";
import { narratorLine, type NarratorMoment } from "../content/narrator.js";

const maxAnswerLength = 120;
const revealMs = 6_000;
const scoreboardMs = 7_000;
const finaleRevealMs = 8_000;

function numberSetting(value: unknown, fallback: number, min: number, max: number): number {
  if (value === undefined || value === null || value === "") return fallback;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.max(min, Math.min(max, Math.round(parsed))) : fallback;
}

function packSetting(value: unknown): BanterboltPackId {
  return value === "cine" || value === "afterdark" || value === "mixto" || value === "mexico"
    ? value
    : "mexico";
}

function cleanAnswer(value: unknown): string {
  if (typeof value !== "string") return "";
  return value.trim().replace(/\s+/g, " ").slice(0, maxAnswerLength);
}

function activePlayers(context: ServerGameContext): string[] {
  return context.players.filter((player) => player.connected !== false).map((player) => player.id);
}

function playerName(context: ServerGameContext, id: string): string {
  return context.players.find((player) => player.id === id)?.name ?? "Jugador";
}

function playerAvatar(context: ServerGameContext, id: string): string | undefined {
  return context.players.find((player) => player.id === id)?.avatar ?? undefined;
}

function narratorFor(state: Pick<BanterboltState, "round">, moment: NarratorMoment): string {
  return narratorLine(moment, state.round);
}

function scoreEntries(state: BanterboltState): ScoreEntry[] {
  return state.players.map((player) => ({
    playerId: player.id,
    delta: state.totals[player.id] ?? 0,
    reason: "Banterbolt",
  }));
}

function publicPlayers(state: BanterboltState): BanterboltPlayer[] {
  return state.players.map((player) => ({
    ...player,
    points: state.totals[player.id] ?? 0,
  }));
}

function currentMatchup(state: BanterboltState): BanterboltMatchup | undefined {
  return state.matchups[state.matchupIndex];
}

function buildPromptRound(
  context: ServerGameContext,
  packId: BanterboltPackId,
  usedPrompts: string[],
): { promptsByPlayer: Record<string, BanterboltPrompt[]>; usedPrompts: string[] } {
  const players = activePlayers(context);
  const pool = promptsFor(packId);
  let available = pool.filter((prompt) => !usedPrompts.includes(prompt));
  let nextUsed = [...usedPrompts];

  if (available.length < players.length) {
    nextUsed = [];
    available = [...pool];
  }

  const chosen = shuffle(available).slice(0, Math.max(players.length, 3));
  const prompts = chosen.map((text, index) => ({
    index,
    text,
    writers: [players[index % players.length]!, players[(index + 1) % players.length]!] as [string, string],
  }));

  const promptsByPlayer: Record<string, BanterboltPrompt[]> = {};
  players.forEach((playerId, index) => {
    const own = prompts[index]!;
    const previous = prompts[(index - 1 + prompts.length) % prompts.length]!;
    promptsByPlayer[playerId] = [own, previous];
  });

  return { promptsByPlayer, usedPrompts: [...nextUsed, ...chosen] };
}

function buildMatchups(state: BanterboltState): BanterboltMatchup[] {
  const active = new Set(state.players.map((player) => player.id));
  const matchups: BanterboltMatchup[] = [];
  const prompts = new Map<number, BanterboltPrompt>();
  for (const list of Object.values(state.promptsByPlayer)) {
    for (const prompt of list) {
      if (!prompts.has(prompt.index)) prompts.set(prompt.index, prompt);
    }
  }

  for (const prompt of [...prompts.values()].sort((a, b) => a.index - b.index)) {
    const [writerA, writerB] = prompt.writers;
    if (!active.has(writerA) || !active.has(writerB)) continue;
    const answerA = cleanAnswer(state.answersByPlayer[writerA]?.[prompt.index]);
    const answerB = cleanAnswer(state.answersByPlayer[writerB]?.[prompt.index]);
    if (!answerA && !answerB) continue;

    const voters = [...active].filter((id) => id !== writerA && id !== writerB);
    if (!voters.length) continue;

    const order = Math.random() < 0.5 ? 0 : 1;
    const firstWriter = order === 0 ? writerA : writerB;
    const secondWriter = order === 0 ? writerB : writerA;
    const firstText = order === 0 ? answerA : answerB;
    const secondText = order === 0 ? answerB : answerA;

    matchups.push({
      promptIndex: prompt.index,
      promptText: prompt.text,
      answers: [
        { text: firstText || "…", writerId: firstWriter },
        { text: secondText || "…", writerId: secondWriter },
      ],
      writers: [firstWriter, secondWriter],
      voters,
      votes: {},
    });
  }

  return matchups;
}

function beginVote(state: BanterboltState, context: ServerGameContext): BanterboltState {
  const matchups = buildMatchups(state);
  if (!matchups.length) return finishGame(state, context);

  return {
    ...state,
    matchups,
    matchupIndex: 0,
    stage: "vote",
    finishAt: context.now + numberSetting(context.roomSettings.banterboltVoteSeconds, 25, 10, 60) * 1000,
    narrator: narratorFor(state, "vote"),
    updatedAt: context.now,
  };
}

function revealMatchup(state: BanterboltState, context: ServerGameContext): BanterboltState {
  const matchup = currentMatchup(state);
  if (!matchup || matchup.revealed) return advanceMatchup(state, context);

  let votesA = 0;
  let votesB = 0;
  for (const vote of Object.values(matchup.votes)) {
    if (vote === 0) votesA += 1;
    else votesB += 1;
  }

  let pointsA = votesA * 100;
  let pointsB = votesB * 100;
  let zapWriterId: string | null = null;
  const totalVotes = votesA + votesB;

  if (totalVotes >= 2) {
    if (votesA === totalVotes && votesB === 0) {
      zapWriterId = matchup.writers[0];
      pointsA += 50;
    } else if (votesB === totalVotes && votesA === 0) {
      zapWriterId = matchup.writers[1];
      pointsB += 50;
    }
  }

  const totals = { ...state.totals };
  totals[matchup.writers[0]] = (totals[matchup.writers[0]] ?? 0) + pointsA;
  totals[matchup.writers[1]] = (totals[matchup.writers[1]] ?? 0) + pointsB;

  const matchups = state.matchups.map((entry, index) =>
    index === state.matchupIndex
      ? { ...entry, revealed: { votes: [votesA, votesB] as [number, number], points: [pointsA, pointsB] as [number, number], zapWriterId } }
      : entry,
  );

  return {
    ...state,
    matchups,
    totals,
    stage: "reveal",
    finishAt: context.now + revealMs,
    narrator: narratorLine(zapWriterId ? "zap" : "reveal", state.round),
    updatedAt: context.now,
  };
}

function advanceMatchup(state: BanterboltState, context: ServerGameContext): BanterboltState {
  const nextIndex = state.matchupIndex + 1;
  if (nextIndex < state.matchups.length) {
    return {
      ...state,
      matchupIndex: nextIndex,
      stage: "vote",
      finishAt: context.now + numberSetting(context.roomSettings.banterboltVoteSeconds, 25, 10, 60) * 1000,
      narrator: narratorFor(state, "vote"),
      updatedAt: context.now,
    };
  }

  return {
    ...state,
    stage: "scoreboard",
    finishAt: context.now + scoreboardMs,
    narrator: narratorFor(state, "scoreboard"),
    updatedAt: context.now,
  };
}

function beginNextRound(state: BanterboltState, context: ServerGameContext): BanterboltState {
  const nextRound = state.round + 1;
  const packId = packSetting(context.roomSettings.banterboltPack);
  const { promptsByPlayer, usedPrompts } = buildPromptRound(context, packId, state.usedPrompts);

  return {
    ...state,
    round: nextRound,
    stage: "write",
    packId,
    promptsByPlayer,
    answersByPlayer: {},
    submitted: [],
    matchups: [],
    matchupIndex: 0,
    finishAt: context.now + numberSetting(context.roomSettings.banterboltWriteSeconds, 90, 30, 180) * 1000,
    usedPrompts,
    narrator: narratorFor({ round: nextRound }, "write"),
    updatedAt: context.now,
  };
}

function beginFinale(state: BanterboltState, context: ServerGameContext): BanterboltState {
  const players = activePlayers(context);
  if (players.length < 2 || context.roomSettings.banterboltLightning === "off") {
    return finishGame(state, context);
  }

  const pool = promptsFor(state.packId).filter((prompt) => !state.usedPrompts.includes(prompt));
  const promptText = (pool.length ? shuffle(pool) : shuffle(promptsFor(state.packId)))[0] ?? "La peor idea para una fiesta: _____";

  return {
    ...state,
    stage: "finale_write",
    finale: {
      promptText,
      answers: {},
      submitted: [],
      votes: {},
    },
    finishAt: context.now + 60_000,
    usedPrompts: [...state.usedPrompts, promptText],
    narrator: narratorFor(state, "finale"),
    updatedAt: context.now,
  };
}

function beginFinaleVote(state: BanterboltState, context: ServerGameContext): BanterboltState {
  const answers = Object.values(state.finale?.answers ?? {}).filter((text) => cleanAnswer(text).length > 0);
  if (answers.length < 2) return finishGame(state, context);

  return {
    ...state,
    stage: "finale_vote",
    finishAt: context.now + 30_000,
    updatedAt: context.now,
  };
}

function revealFinale(state: BanterboltState, context: ServerGameContext): BanterboltState {
  const finale = state.finale;
  if (!finale) return finishGame(state, context);

  const counts = new Map<string, number>();
  for (const target of Object.values(finale.votes)) {
    counts.set(target, (counts.get(target) ?? 0) + 1);
  }

  const totals = { ...state.totals };
  const sorted = [...counts.entries()].sort((a, b) => b[1] - a[1]);
  const topVotes = sorted[0]?.[1] ?? 0;
  const results = [...counts.entries()]
    .sort((a, b) => b[1] - a[1])
    .map(([playerId, votes], index) => {
      const points = votes * 100 + (votes === topVotes && topVotes > 0 ? 50 : 0);
      totals[playerId] = (totals[playerId] ?? 0) + points;
      return {
        playerId,
        text: finale.answers[playerId] ?? "",
        votes,
        points,
        rank: sorted.findIndex((entry) => entry[1] === votes),
      };
    });

  return {
    ...state,
    totals,
    finale: { ...finale, results },
    stage: "finale_reveal",
    finishAt: context.now + finaleRevealMs,
    narrator: narratorFor(state, "podium"),
    updatedAt: context.now,
  };
}

function finishGame(state: BanterboltState, context: ServerGameContext): BanterboltState {
  return {
    ...state,
    stage: "finished",
    phase: "finished",
    finishAt: null,
    narrator: narratorFor(state, "podium"),
    updatedAt: context.now,
  };
}

function winnerIds(state: BanterboltState): string[] {
  const best = Math.max(...state.players.map((player) => state.totals[player.id] ?? 0), 0);
  return state.players.filter((player) => (state.totals[player.id] ?? 0) === best).map((player) => player.id);
}

function publicState(state: BanterboltState, context: ServerGameContext): BanterboltPublicState {
  const matchup = currentMatchup(state);
  const revealed = state.stage === "reveal" || state.stage === "scoreboard" || state.stage === "finished";
  const active = activePlayers(context);
  const submitted = state.stage === "finale_write"
    ? state.finale?.submitted.length ?? 0
    : state.submitted.length;
  const submittedPlayerIds = state.stage === "finale_write"
    ? [...(state.finale?.submitted ?? [])]
    : [...state.submitted];

  let current: BanterboltPublicMatchup | null = null;
  if (matchup && (state.stage === "vote" || state.stage === "reveal" || state.stage === "scoreboard")) {
    current = {
      promptIndex: matchup.promptIndex,
      promptText: matchup.promptText,
      answers: matchup.answers.map((answer) => ({
        text: answer.text,
        ...(revealed ? { writerId: answer.writerId, writerName: playerName(context, answer.writerId) } : {}),
      })),
      ...(revealed && matchup.revealed
        ? {
            votes: matchup.revealed.votes,
            points: matchup.revealed.points,
            zapWriterId: matchup.revealed.zapWriterId,
          }
        : {}),
      votedPlayerIds: Object.keys(matchup.votes),
      voterIds: matchup.voters,
    };
  }

  const finale = state.finale
    ? {
        promptText: state.finale.promptText,
        submittedCount: state.finale.submitted.length,
        submittedPlayerIds: [...state.finale.submitted],
        playerCount: active.length,
        ...(state.stage === "finale_vote"
          ? {
              options: Object.entries(state.finale.answers).map(([playerId, text]) => ({ playerId, text })),
            }
          : {}),
        ...(state.finale.results
          ? {
              answers: state.finale.results.map((result) => ({
                playerId: result.playerId,
                text: result.text,
                votes: result.votes,
                points: result.points,
                rank: result.rank,
              })),
            }
          : {}),
        votedCount: Object.keys(state.finale.votes).length,
      }
    : null;

  return {
    roundId: state.roundId,
    stage: state.stage,
    round: state.round,
    totalRounds: state.totalRounds,
    players: publicPlayers(state),
    submittedCount: submitted,
    submittedPlayerIds,
    playerCount: active.length,
    currentMatchup: current,
    matchupIndex: state.matchupIndex,
    totalMatchups: state.matchups.length,
    finishAt: state.finishAt,
    remainingMs: state.finishAt === null ? null : Math.max(0, state.finishAt - context.now),
    finale,
    winnerIds: state.stage === "finished" ? winnerIds(state) : [],
    narrator: state.narrator,
  };
}

function controllerState(
  state: BanterboltState,
  context: ServerGameContext,
  playerId: string,
): BanterboltControllerState {
  const publicStateValue = publicState(state, context);
  const ownPrompts = state.promptsByPlayer[playerId] ?? [];
  const ownAnswers = state.answersByPlayer[playerId] ?? {};
  const matchup = currentMatchup(state);
  const ownVote = matchup?.votes[playerId];

  return {
    ...publicStateValue,
    ownPrompts,
    ownAnswers,
    ownSubmitted: state.submitted.includes(playerId),
    ...(ownVote !== undefined ? { ownVote } : {}),
    isWriter: Boolean(matchup && matchup.writers.includes(playerId)),
    ...(state.finale?.answers[playerId] ? { ownFinaleAnswer: state.finale.answers[playerId] } : {}),
    ...(state.finale?.votes[playerId] ? { ownFinaleVote: state.finale.votes[playerId] } : {}),
  };
}

export const serverGame: ServerGame<
  BanterboltState,
  BanterboltInput,
  BanterboltPublicState | BanterboltControllerState
> = {
  manifest: banterboltManifest,

  handleHostAction(state, action) {
    if (state?.phase === "playing") return null;
    if (!action || typeof action !== "object") return null;
    const values = action as Record<string, unknown>;
    if (values.type !== "configure-lobby") return null;

    const roomSettings: Record<string, unknown> = {};
    if (["mexico", "cine", "afterdark", "mixto"].includes(String(values.pack))) {
      roomSettings.banterboltPack = String(values.pack);
    }
    if (values.rounds !== undefined) {
      roomSettings.banterboltRounds = numberSetting(values.rounds, 2, 1, 3);
    }
    if (values.writeSeconds !== undefined) {
      roomSettings.banterboltWriteSeconds = numberSetting(values.writeSeconds, 90, 30, 180);
    }
    if (values.voteSeconds !== undefined) {
      roomSettings.banterboltVoteSeconds = numberSetting(values.voteSeconds, 25, 10, 60);
    }
    if (values.lightning === "on" || values.lightning === "off") {
      roomSettings.banterboltLightning = values.lightning;
    }
    return Object.keys(roomSettings).length ? { roomSettings } : null;
  },

  createInitialState(context) {
    const players = activePlayers(context);
    const packId = packSetting(context.roomSettings.banterboltPack);
    const totalRounds = numberSetting(context.roomSettings.banterboltRounds, 2, 1, 3);
    const { promptsByPlayer, usedPrompts } = buildPromptRound(context, packId, []);
    const base = createBaseRoundState("round_intro", context.now, {
      durationMs: banterboltManifest.phaseDurations?.roundIntroMs ?? 1_800,
    });

    return {
      ...base,
      roundId: `${context.roundNumber}:${context.now}`,
      stage: "write",
      round: 1,
      totalRounds,
      packId,
      players: players.map((id) => ({
        id,
        name: playerName(context, id),
        avatar: playerAvatar(context, id),
        points: 0,
        delta: 0,
      })),
      promptsByPlayer,
      answersByPlayer: {},
      submitted: [],
      matchups: [],
      matchupIndex: 0,
      totals: Object.fromEntries(players.map((id) => [id, 0])),
      finishAt: null,
      gameScores: Object.fromEntries(players.map((id) => [id, 0])),
      usedPrompts,
      narrator: narratorFor({ round: 1 }, "welcome"),
    };
  },

  startRound(state, context) {
    const writeSeconds = numberSetting(context.roomSettings.banterboltWriteSeconds, 90, 30, 180);
    return {
      ...state,
      phase: "playing",
      stage: "write",
      startedAt: context.now,
      phaseStartedAt: context.now,
      phaseEndsAt: null,
      finishAt: context.now + writeSeconds * 1000,
      narrator: narratorFor(state, "write"),
      updatedAt: context.now,
    };
  },

  handleInput(state, input, context) {
    if (input.roundId !== state.roundId || state.phase !== "playing") return state;
    if (!activePlayers(context).includes(input.playerId)) return state;

    if (input.type === "submit_answers" && state.stage === "write") {
      const ownPrompts = state.promptsByPlayer[input.playerId] ?? [];
      if (ownPrompts.length < 2 || !Array.isArray(input.values)) return state;
      const values = input.values.map(cleanAnswer);
      const answers = { ...(state.answersByPlayer[input.playerId] ?? {}) };
      ownPrompts.forEach((prompt, index) => {
        answers[prompt.index] = values[index] ?? "";
      });
      const answersByPlayer = { ...state.answersByPlayer, [input.playerId]: answers };
      const complete = ownPrompts.every((prompt) => cleanAnswer(answers[prompt.index]).length > 0);
      const submitted = input.submit && complete
        ? [...new Set([...state.submitted, input.playerId])]
        : state.submitted.filter((id) => id !== input.playerId);

      const next = { ...state, answersByPlayer, submitted, updatedAt: context.now };
      return submitted.length >= activePlayers(context).length ? beginVote(next, context) : next;
    }

    if (input.type === "vote" && state.stage === "vote") {
      const matchup = currentMatchup(state);
      if (!matchup || !matchup.voters.includes(input.playerId)) return state;
      if (input.answerIndex !== 0 && input.answerIndex !== 1) return state;
      const votes = { ...matchup.votes, [input.playerId]: input.answerIndex };
      const matchups = state.matchups.map((entry, index) =>
        index === state.matchupIndex ? { ...entry, votes } : entry,
      );
      const next = { ...state, matchups, updatedAt: context.now };
      return matchup.voters.every((id) => votes[id] !== undefined)
        ? revealMatchup(next, context)
        : next;
    }

    if (input.type === "finale_submit" && state.stage === "finale_write" && state.finale) {
      const text = cleanAnswer(input.text);
      if (!text) return state;
      const answers = { ...state.finale.answers, [input.playerId]: text };
      const submitted = [...new Set([...state.finale.submitted, input.playerId])];
      const next = {
        ...state,
        finale: { ...state.finale, answers, submitted },
        updatedAt: context.now,
      };
      return submitted.length >= activePlayers(context).length
        ? beginFinaleVote(next, context)
        : next;
    }

    if (input.type === "finale_vote" && state.stage === "finale_vote" && state.finale) {
      if (input.targetPlayerId === input.playerId) return state;
      if (!state.finale.answers[input.targetPlayerId]) return state;
      const votes = { ...state.finale.votes, [input.playerId]: input.targetPlayerId };
      const next = { ...state, finale: { ...state.finale, votes }, updatedAt: context.now };
      const voters = activePlayers(context);
      return voters.every((id) => votes[id] !== undefined)
        ? revealFinale(next, context)
        : next;
    }

    return state;
  },

  tick(state, _deltaMs, context) {
    if (state.phase !== "playing" || state.finishAt === null || context.now < state.finishAt) {
      return state;
    }

    if (state.stage === "write") {
      const answersByPlayer = { ...state.answersByPlayer };
      const submitted = [...state.submitted];
      for (const playerId of activePlayers(context)) {
        const prompts = state.promptsByPlayer[playerId] ?? [];
        const answers = { ...(answersByPlayer[playerId] ?? {}) };
        prompts.forEach((prompt) => {
          if (!cleanAnswer(answers[prompt.index])) answers[prompt.index] = "…";
        });
        answersByPlayer[playerId] = answers;
        if (!submitted.includes(playerId)) submitted.push(playerId);
      }
      return beginVote({ ...state, answersByPlayer, submitted, updatedAt: context.now }, context);
    }

    if (state.stage === "vote") return revealMatchup(state, context);
    if (state.stage === "reveal") return advanceMatchup(state, context);

    if (state.stage === "scoreboard") {
      if (state.round < state.totalRounds) return beginNextRound(state, context);
      return beginFinale(state, context);
    }

    if (state.stage === "finale_write" && state.finale) {
      const answers = { ...state.finale.answers };
      const submitted = [...state.finale.submitted];
      for (const playerId of activePlayers(context)) {
        if (!cleanAnswer(answers[playerId])) answers[playerId] = "…";
        if (!submitted.includes(playerId)) submitted.push(playerId);
      }
      return beginFinaleVote({ ...state, finale: { ...state.finale, answers, submitted } }, context);
    }

    if (state.stage === "finale_vote") return revealFinale(state, context);
    if (state.stage === "finale_reveal") return finishGame(state, context);

    return state;
  },

  isRoundFinished(state) {
    return state.stage === "finished";
  },

  buildScore(state) {
    return state.stage === "finished" ? scoreEntries(state) : [];
  },

  toPublicState(state, context) {
    return publicState(state, context);
  },

  toControllerStateForPlayer(state, context, playerId) {
    return controllerState(state, context, playerId);
  },
};
