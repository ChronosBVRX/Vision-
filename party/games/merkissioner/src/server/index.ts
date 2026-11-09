import {
  createBaseRoundState,
  type ServerGame,
  type ServerGameContext,
  type ScoreEntry,
} from "@open-party-lab/game-core";
import type { GameContext, GameStateIn, SeatIndex } from "../merky-sdk.js";
import { initMerkissionerGame, reduceMerkissioner, onTickMerkissioner } from "../engine/logic.js";
import type { MerkissionerPrivateState, MerkissionerPublicState } from "../engine/types.js";
import { merkissionerManifest } from "../manifest.js";
import type {
  MerkissionerControllerView,
  MerkissionerInput,
  MerkissionerPublicView,
  MerkissionerState,
} from "../protocol.js";

function buildGameContext(state: MerkissionerState, context: ServerGameContext): GameContext {
  const seats = Object.entries(state.playerBySeat)
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
      pace: context.roomSettings.merkissionerPace === "relaxed" || context.roomSettings.merkissionerPace === "speedy"
        ? context.roomSettings.merkissionerPace
        : "standard",
      timersEnabled: context.roomSettings.merkissionerTimers !== "off",
      revealVotes: context.roomSettings.merkissionerRevealVotes !== "off",
    },
    now: context.now,
    rng: Math.random,
  };
}

function narratorFor(phase: string): string {
  switch (phase) {
    case "huddle":
      return "Revisen su rol en el celular. Nadie enseñe la pantalla, no sean novatos.";
    case "nominate":
      return "La silla nomina a un comisionado. Piensen bien a quién le dan el poder.";
    case "vote":
      return "Voten SÍ o NO. Y luego no digan que no sabían.";
    case "legislative_chair":
    case "legislative_commissioner":
      return "Sesión legislativa. Tres decretos, uno se descarta y dos van al comisionado.";
    case "veto_pending":
      return "Veto sobre la mesa. La silla decide si se acepta.";
    case "power_audit":
    case "power_snap":
    case "power_peek":
    case "power_banish":
      return "Poder desbloqueado. Alguien va a descubrir la verdad.";
    case "game_over":
      return "Fin del juego. Ahora sí, destapen todos los roles.";
    default:
      return "El Comisionado Merk. Confía en nadie.";
  }
}

function applyResult(state: MerkissionerState, result: ReturnType<typeof reduceMerkissioner>, context: ServerGameContext): MerkissionerState {
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
  MerkissionerState,
  MerkissionerInput,
  MerkissionerPublicView | MerkissionerControllerView
> = {
  manifest: merkissionerManifest,

  handleHostAction(state, action) {
    if (state?.phase === "playing") return null;
    if (!action || typeof action !== "object") return null;
    const values = action as Record<string, unknown>;
    if (values.type !== "configure-lobby") return null;

    const roomSettings: Record<string, unknown> = {};
    if (values.pace === "relaxed" || values.pace === "standard" || values.pace === "speedy") {
      roomSettings.merkissionerPace = values.pace;
    }
    if (values.timers === "on" || values.timers === "off") roomSettings.merkissionerTimers = values.timers;
    if (values.revealVotes === "on" || values.revealVotes === "off") roomSettings.merkissionerRevealVotes = values.revealVotes;
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

    const state: MerkissionerState = {
      ...createBaseRoundState("round_intro", context.now, {
        durationMs: merkissionerManifest.phaseDurations?.roundIntroMs ?? 1_800,
      }),
      roundId: `${context.roundNumber}:${context.now}`,
      match: { publicState: null, privateState: {}, secretState: null, phase: "huddle" },
      timer: null,
      scores: {},
      matchOver: false,
      seatByPlayer,
      playerBySeat,
      events: [],
      narrator: "El Comisionado Merk. Confía en nadie.",
    };

    const result = initMerkissionerGame(buildGameContext(state, context));
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
    switch (input.type) {
      case "ready_up": action = { type: "ready_up", seat }; break;
      case "nominate": action = { type: "nominate", seat, payload: { seat: input.targetSeat } }; break;
      case "cast_vote": action = { type: "cast_vote", seat, payload: { vote: input.vote } }; break;
      case "discard_decree": action = { type: "discard_decree", seat, payload: { index: input.index } }; break;
      case "enact_decree": action = { type: "enact_decree", seat, payload: { index: input.index } }; break;
      case "propose_veto": action = { type: "propose_veto", seat }; break;
      case "resolve_veto": action = { type: "resolve_veto", seat, payload: { agree: input.agree } }; break;
      case "use_power": action = { type: "use_power", seat, payload: input.targetSeat === undefined ? {} : { seat: input.targetSeat } }; break;
    }
    if (!action) return state;

    const result = reduceMerkissioner(buildGameContext(state, context), state.match, action);
    return applyResult(state, result, context);
  },

  tick(state, _deltaMs, context) {
    if (state.phase !== "playing" || state.matchOver) return state;
    if (!state.timer || context.now < state.timer.endsAt) return state;
    const result = onTickMerkissioner(buildGameContext(state, context), state.match);
    return result ? applyResult(state, result, context) : state;
  },

  isRoundFinished(state) {
    return state.matchOver;
  },

  buildScore(state) {
    return Object.entries(state.scores).map(([seat, score]) => ({
      playerId: state.playerBySeat[Number(seat)]?.id ?? String(seat),
      delta: score ?? 0,
      reason: "El Comisionado Merk",
    }));
  },

  toPublicState(state, context): MerkissionerPublicView {
    const publicState = (state.match.publicState ?? {}) as MerkissionerPublicState;
    return {
      ...publicState,
      roundId: state.roundId,
      phase: state.match.phase,
      timer: state.timer,
      matchOver: state.matchOver,
      seatPlayers: Object.entries(state.playerBySeat).map(([seat, player]) => ({
        seat: Number(seat) as SeatIndex,
        ...player,
      })),
      narrator: state.narrator,
    };
  },

  toControllerStateForPlayer(state, context, playerId): MerkissionerControllerView {
    const seat = state.seatByPlayer[playerId] ?? 0;
    return {
      ...this.toPublicState!(state, context),
      seat,
      playerId,
      privateState: (state.match.privateState[seat] ?? {
        role: "merkizen",
        knownMerkites: [],
        knownBoss: null,
        myVote: null,
        hand: null,
        auditResults: [],
        peek: null,
      }) as MerkissionerPrivateState,
    };
  },
};
