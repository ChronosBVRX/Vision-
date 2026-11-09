import {
  createBaseRoundState,
  type ServerGame,
  type ServerGameContext,
  type ScoreEntry,
} from "@open-party-lab/game-core";
import type { GameContext, GameStateIn, SeatIndex } from "../merky-sdk.js";
import { initYougotit, reduceYougotit, onTickYougotit } from "../engine/logic.js";
import { YOUGOTIT_CORE_SPECTRA, YOUGOTIT_AFTERDARK_SPECTRA } from "../engine/packs.js";
import { youGotItManifest } from "../manifest.js";
import type {
  YouGotItControllerState,
  YouGotItInput,
  YouGotItPublicState,
  YouGotItState,
} from "../protocol.js";

function numberSetting(value: unknown, fallback: number, min: number, max: number): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.max(min, Math.min(max, Math.round(parsed))) : fallback;
}

function packSetting(value: unknown): "core" | "afterdark" {
  return value === "afterdark" ? "afterdark" : "core";
}

function buildGameContext(state: YouGotItState, context: ServerGameContext): GameContext {
  const seatPlayers = state.playerBySeat;
  const seats = Object.entries(seatPlayers)
    .map(([seat, player]) => ({
      seatIndex: Number(seat) as SeatIndex,
      displayName: player.name,
      avatarId: player.avatar ?? "",
      role: "player" as const,
      connected: true,
      abandoned: false,
      isHost: false,
    }))
    .sort((a, b) => a.seatIndex - b.seatIndex);

  return {
    matchId: state.roundId,
    roomId: context.roomCode,
    seats,
    settings: {
      targetScore: numberSetting(context.roomSettings.youGotItTargetScore, 10, 5, 30),
      stealEnabled: context.roomSettings.youGotItSteal !== "off",
      guessSeconds: numberSetting(context.roomSettings.youGotItGuessSeconds, 90, 30, 150),
      packId: packSetting(context.roomSettings.youGotItPack),
      _pack: {
        id: `you-got-it-${packSetting(context.roomSettings.youGotItPack)}`,
        payload: {
          prompts:
            packSetting(context.roomSettings.youGotItPack) === "afterdark"
              ? YOUGOTIT_AFTERDARK_SPECTRA
              : YOUGOTIT_CORE_SPECTRA,
        },
      },
    },
    now: context.now,
    rng: Math.random,
  };
}

function narratorFor(phase: string): string {
  switch (phase) {
    case "clue":
      return "Oráculo, suelta una pista. Corta, pícara y que no regale todo.";
    case "guess":
      return "Muevan el dial. Más cerca del centro, más puntos; más lejos, más vergüenza.";
    case "steal":
      return "Undercut abierto: el otro equipo puede robarles el punto.";
    case "reveal":
      return "A ver qué tan cerca le atinaron. Sin llorar.";
    case "game_over":
      return "¡Fin del juego! El equipo ganador invita los tacos.";
    default:
      return "Bienvenidos al espectro. Aquí se viene a adivinar lo que piensa el Oráculo.";
  }
}

function applyResult(state: YouGotItState, result: ReturnType<typeof reduceYougotit>, context: ServerGameContext): YouGotItState {
  if ("error" in result) return state;

  const privateState = result.privateState
    ? { ...state.match.privateState, ...result.privateState }
    : state.match.privateState;

  return {
    ...state,
    match: {
      publicState: result.publicState,
      privateState,
      secretState: result.secretState !== undefined ? result.secretState : state.match.secretState,
      phase: result.phase,
    },
    timer: result.timer === undefined ? state.timer : result.timer,
    scores: result.scores ? { ...state.scores, ...result.scores } : state.scores,
    matchOver: result.matchOver ?? state.matchOver,
    events: result.events,
    narrator: narratorFor(result.phase),
    updatedAt: context.now,
  };
}

export const serverGame: ServerGame<
  YouGotItState,
  YouGotItInput,
  YouGotItPublicState | YouGotItControllerState
> = {
  manifest: youGotItManifest,

  handleHostAction(state, action) {
    if (state?.phase === "playing") return null;
    if (!action || typeof action !== "object") return null;
    const values = action as Record<string, unknown>;
    if (values.type !== "configure-lobby") return null;

    const roomSettings: Record<string, unknown> = {};
    if (values.targetScore === "7" || values.targetScore === "10" || values.targetScore === "15") {
      roomSettings.youGotItTargetScore = values.targetScore;
    }
    if (values.steal === "on" || values.steal === "off") {
      roomSettings.youGotItSteal = values.steal;
    }
    if (values.guessSeconds !== undefined) {
      roomSettings.youGotItGuessSeconds = numberSetting(values.guessSeconds, 90, 30, 150);
    }
    if (values.pack === "core" || values.pack === "afterdark") {
      roomSettings.youGotItPack = values.pack;
    }
    return Object.keys(roomSettings).length ? { roomSettings } : null;
  },

  createInitialState(context) {
    const players = context.players.filter((player) => player.connected !== false);
    const seatByPlayer: Record<string, SeatIndex> = {};
    const playerBySeat: Record<number, { id: string; name: string; avatar?: string }> = {};
    players.forEach((player, index) => {
      const seat = index as SeatIndex;
      seatByPlayer[player.id] = seat;
      playerBySeat[seat] = { id: player.id, name: player.name, avatar: player.avatar ?? undefined };
    });

    const state: YouGotItState = {
      ...createBaseRoundState("round_intro", context.now, {
        durationMs: youGotItManifest.phaseDurations?.roundIntroMs ?? 1_800,
      }),
      roundId: `${context.roundNumber}:${context.now}`,
      match: { publicState: null, privateState: {}, secretState: null, phase: "clue" },
      timer: null,
      scores: {},
      matchOver: false,
      seatByPlayer,
      playerBySeat,
      events: [],
      narrator: "Bienvenidos al espectro. Aquí se viene a adivinar lo que piensa el Oráculo.",
    };

    const gameContext = buildGameContext(state, context);
    const result = initYougotit(gameContext);
    return applyResult(state, result, context);
  },

  startRound(state, context) {
    return {
      ...state,
      phase: "playing",
      startedAt: context.now,
      phaseStartedAt: context.now,
      phaseEndsAt: null,
      updatedAt: context.now,
    };
  },

  handleInput(state, input, context) {
    if (input.roundId !== state.roundId || state.phase !== "playing" || state.matchOver) return state;
    const seat = state.seatByPlayer[input.playerId];
    if (seat === undefined) return state;

    let action: { type: string; seat: SeatIndex | "system"; payload?: unknown } | null = null;
    if (input.type === "submit_clue") {
      action = { type: "submit_clue", seat, payload: { text: input.text } };
    } else if (input.type === "move_pointer") {
      action = { type: "move_pointer", seat, payload: { angle: input.angle } };
    } else if (input.type === "lock_pointer") {
      action = { type: "lock_pointer", seat };
    } else if (input.type === "guess_direction") {
      action = { type: "guess_direction", seat, payload: { dir: input.dir } };
    }
    if (!action) return state;

    const result = reduceYougotit(buildGameContext(state, context), state.match, action);
    return applyResult(state, result, context);
  },

  tick(state, _deltaMs, context) {
    if (state.phase !== "playing" || state.matchOver) return state;
    if (!state.timer || context.now < state.timer.endsAt) return state;
    const result = onTickYougotit(buildGameContext(state, context), state.match);
    return result ? applyResult(state, result, context) : state;
  },

  isRoundFinished(state) {
    return state.matchOver;
  },

  buildScore(state) {
    return Object.entries(state.scores).map(([seat, score]) => ({
      playerId: state.playerBySeat[Number(seat)]?.id ?? String(seat),
      delta: score ?? 0,
      reason: "¿Le atinaste?",
    }));
  },

  toPublicState(state, context): YouGotItPublicState {
    const publicState = (state.match.publicState ?? {}) as Record<string, unknown>;
    return {
      ...(publicState as object),
      roundId: state.roundId,
      phase: state.match.phase,
      narrator: state.narrator,
      timer: state.timer,
      matchOver: state.matchOver,
      seatPlayers: Object.entries(state.playerBySeat).map(([seat, player]) => ({
        seat: Number(seat) as SeatIndex,
        ...player,
      })),
    } as YouGotItPublicState;
  },

  toControllerStateForPlayer(state, context, playerId): YouGotItControllerState {
    const seat = state.seatByPlayer[playerId] ?? 0;
    return {
      ...this.toPublicState!(state, context),
      seat,
      playerId,
      privateState: (state.match.privateState[seat] ?? {}) as Record<string, unknown>,
    };
  },
};
