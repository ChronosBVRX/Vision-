import {
  createBaseRoundState,
  roundPhaseDurations,
  type GamePlayerSummary,
  type ScoreEntry,
  type ServerGame,
  type ServerGameContext
} from "@open-party-lab/game-core";
import {
  nextRevealTurn,
  resolveTurnDurationMs,
  schattenjagdConfig,
  schattenjagdRoomSettingKeys,
  schattenjagdSetupConfig,
  shadowAssignmentModes,
  turnTimeOptions,
  type ShadowAssignmentMode,
  type TurnTimeOption
} from "../config.js";
import { generateTransitMap } from "../map/generateTransitMap.js";
import { createSeededRandom } from "../map/rng.js";
import { schattenjagdManifest } from "../manifest.js";
import type {
  SchattenjagdConfigureLobbyHostAction,
  SchattenjagdControllerState,
  SchattenjagdInput,
  SchattenjagdPublicState,
  SchattenjagdState
} from "../protocol.js";
import { getSchattenjagdText, type SchattenjagdLanguage } from "../text.js";
import { decideShadowMove } from "./ai.js";
import {
  applyDetectiveMove,
  applyShadowMove,
  beginShadowTurn,
  createTicketWallet,
  finishDetectivePhase,
  listDetectiveOptions,
  listShadowOptions,
} from "./rules.js";
import { chooseStartPositions } from "./setup.js";

function readShadowMode(settings: Readonly<Record<string, unknown>>): ShadowAssignmentMode {
  const raw = settings[schattenjagdRoomSettingKeys.shadowMode];

  return shadowAssignmentModes.includes(raw as ShadowAssignmentMode)
    ? (raw as ShadowAssignmentMode)
    : schattenjagdSetupConfig.shadowMode.defaultValue;
}

function readShadowSeat(settings: Readonly<Record<string, unknown>>): number {
  const raw = settings[schattenjagdRoomSettingKeys.shadowSeat];
  const value = typeof raw === "number" ? raw : Number.parseInt(String(raw ?? ""), 10);

  if (!Number.isFinite(value)) {
    return schattenjagdSetupConfig.shadowSeat.defaultValue;
  }

  return Math.min(
    schattenjagdSetupConfig.shadowSeat.max,
    Math.max(schattenjagdSetupConfig.shadowSeat.min, Math.round(value))
  );
}

function readTurnDurationMs(settings: Readonly<Record<string, unknown>>): number | null {
  const raw = settings[schattenjagdRoomSettingKeys.turnTime];

  return resolveTurnDurationMs(
    raw === undefined || raw === null ? schattenjagdSetupConfig.turnTime.defaultValue : raw
  );
}

function resolveShadowPlayerId(
  players: GamePlayerSummary[],
  mode: ShadowAssignmentMode,
  seat: number,
  seed: string
): string | null {
  if (mode === "ai" || players.length < 2) {
    return null;
  }

  if (mode === "seat") {
    const index = Math.min(players.length, Math.max(1, seat)) - 1;
    return players[index]?.id ?? players[0]?.id ?? null;
  }

  return createSeededRandom(`${seed}:shadow`).pick(players).id;
}

function connectedPlayerIds(context: ServerGameContext): Set<string> {
  return new Set(context.players.filter((player) => player.connected).map((player) => player.id));
}

function buildNameResolver(context: ServerGameContext): (playerId: string) => string {
  const names = new Map(context.players.map((player) => [player.id, player.name]));
  return (playerId: string) => names.get(playerId) ?? "?";
}

function language(context: ServerGameContext): SchattenjagdLanguage {
  return context.language === "en" ? "en" : "de";
}

function isAiShadow(state: SchattenjagdState): boolean {
  return state.shadowPlayerId === null;
}

/** Advance in seating order; skipped seats cannot re-enter this turn. */
function advanceDetective(state: SchattenjagdState, context: ServerGameContext): SchattenjagdState {
  if (state.stage !== "detective_move" || state.outcome !== null) return state;
  const connected = connectedPlayerIds(context);
  let next = state;
  for (const detective of state.detectives) {
    if (detective.movedInTurn >= state.turn) continue;
    if (!connected.has(detective.playerId) || !listDetectiveOptions(next, detective).some((option) => !option.blocked)) {
      next = { ...next, detectives: next.detectives.map((entry) => entry.playerId === detective.playerId ? { ...entry, movedInTurn: state.turn } : entry) };
      continue;
    }
    return {
      ...next,
      activeDetectivePlayerId: detective.playerId,
      turnEndsAt: next.turnDurationMs === null ? null : context.now + next.turnDurationMs,
      updatedAt: context.now
    };
  }
  return finishDetectivePhase({ ...next, activeDetectivePlayerId: null }, context.now, language(context), isAiShadow(next), buildNameResolver(context));
}

function autoPlayShadow(
  state: SchattenjagdState,
  context: ServerGameContext
): SchattenjagdState {
  const text = getSchattenjagdText(language(context));
  const decision = decideShadowMove(state, context.roomCode);

  if (!decision) {
    return {
      ...state,
      stage: "caught",
      outcome: "caught",
      captureStationId: state.shadowStationId,
      turnEndsAt: null,
      aiDecideAt: null,
      updatedAt: context.now,
      message: text.shadowStuck
    };
  }

  const armed: SchattenjagdState = {
    ...state,
    shroudArmed: state.shroudArmed || decision.armShroud,
    doubleArmed: state.doubleArmed || decision.armDouble
  };

  return applyShadowMove(
    armed,
    decision.stationId,
    decision.mode,
    context.now,
    language(context),
    isAiShadow(state)
  );
}

export const serverGame: ServerGame<
  SchattenjagdState,
  SchattenjagdInput,
  SchattenjagdPublicState | SchattenjagdControllerState
> = {
  manifest: schattenjagdManifest,
  createInitialState(context) {
    const text = getSchattenjagdText(language(context));
    const seed = `${context.roomCode}:${context.roundNumber}`;
    const map = generateTransitMap(seed);
    const random = createSeededRandom(`${seed}:setup`);
    const shadowMode = readShadowMode(context.roomSettings);
    const shadowPlayerId = resolveShadowPlayerId(
      context.players,
      shadowMode,
      readShadowSeat(context.roomSettings),
      seed
    );
    const detectivePlayers = context.players.filter((player) => player.id !== shadowPlayerId);
    const positions = chooseStartPositions(map, random, detectivePlayers.length);

    return {
      ...createBaseRoundState("round_intro", context.now, {
        durationMs: schattenjagdManifest.phaseDurations?.roundIntroMs ?? roundPhaseDurations.roundIntroMs,
        message: text.preparing
      }),
      stage: "shadow_move",
      activeDetectivePlayerId: null,
      turn: 1,
      totalTurns: schattenjagdConfig.totalTurns,
      mapVariant: context.theme === "light" ? "day" : "night",
      map,
      shadowMode: shadowPlayerId === null ? "ai" : shadowMode,
      shadowPlayerId,
      shadowStationId: positions.shadowStation,
      shadowShroudsLeft: schattenjagdConfig.shadowShrouds,
      shadowDoublesLeft: schattenjagdConfig.shadowDoubleMoves,
      shroudArmed: false,
      doubleArmed: false,
      doubleMoveStep: 1,
      lastRevealedStationId: null,
      lastRevealTurn: null,
      detectives: detectivePlayers.map((player, index) => ({
        playerId: player.id,
        stationId: positions.detectiveStations[index] ?? positions.detectiveStations[0] ?? 1,
        tickets: createTicketWallet(),
        movedInTurn: 0,
        lastMode: null
      })),
      travelLog: [],
      turnDurationMs: readTurnDurationMs(context.roomSettings),
      turnEndsAt: null,
      aiDecideAt: null,
      capturedByPlayerId: null,
      captureStationId: null,
      outcome: null
    };
  },
  handleHostAction(state, action, context) {
    const hostAction = action as Partial<SchattenjagdConfigureLobbyHostAction> | null;

    if (!hostAction?.type || state) {
      return {};
    }

    if (hostAction.type !== "configure-lobby") {
      return {};
    }

    const nextSettings: Record<string, string | number> = {};

    if (
      typeof hostAction.shadowMode === "string" &&
      shadowAssignmentModes.includes(hostAction.shadowMode as ShadowAssignmentMode)
    ) {
      nextSettings[schattenjagdRoomSettingKeys.shadowMode] = hostAction.shadowMode;
    }

    if (
      hostAction.turnTime !== undefined &&
      turnTimeOptions.includes(String(hostAction.turnTime) as TurnTimeOption)
    ) {
      nextSettings[schattenjagdRoomSettingKeys.turnTime] = String(hostAction.turnTime);
    }

    if (typeof hostAction.shadowSeat === "number" && Number.isFinite(hostAction.shadowSeat)) {
      nextSettings[schattenjagdRoomSettingKeys.shadowSeat] = Math.min(
        schattenjagdSetupConfig.shadowSeat.max,
        Math.max(schattenjagdSetupConfig.shadowSeat.min, Math.round(hostAction.shadowSeat))
      );
    }

    void context;

    return Object.keys(nextSettings).length > 0 ? { roomSettings: nextSettings } : {};
  },
  startRound(state, context) {
    const started: SchattenjagdState = {
      ...state,
      phase: "playing",
      startedAt: context.now,
      phaseStartedAt: context.now,
      phaseEndsAt: null,
      updatedAt: context.now
    };

    return beginShadowTurn(started, context.now, language(context), isAiShadow(state));
  },
  handleInput(state, input, context) {
    if (state.phase !== "playing" || state.outcome !== null) {
      return state;
    }

    const text = getSchattenjagdText(language(context));
    const now = context.now;
    const isShadowPlayer = state.shadowPlayerId !== null && input.playerId === state.shadowPlayerId;

    if (input.type === "toggle_shroud") {
      if (!isShadowPlayer || state.stage !== "shadow_move" || state.shadowShroudsLeft <= 0) {
        return state;
      }

      const shroudArmed = !state.shroudArmed;

      return {
        ...state,
        shroudArmed,
        updatedAt: now,
        message: shroudArmed ? text.shroudArmed : text.shroudDisarmed
      };
    }

    if (input.type === "toggle_double_move") {
      if (
        !isShadowPlayer ||
        state.stage !== "shadow_move" ||
        state.doubleMoveStep !== 1 ||
        state.shadowDoublesLeft <= 0
      ) {
        return state;
      }

      const doubleArmed = !state.doubleArmed;

      return {
        ...state,
        doubleArmed,
        updatedAt: now,
        message: doubleArmed ? text.doubleArmed : text.doubleDisarmed
      };
    }

    if (input.type !== "move") {
      return state;
    }

    if (state.stage === "shadow_move" && isShadowPlayer) {
      const moved = applyShadowMove(state, input.stationId, input.mode, now, language(context), false);
      return moved !== state ? advanceDetective(moved, context) : state;
    }

    if (state.stage === "detective_move") {
      const movedState = applyDetectiveMove(
        state,
        input.playerId,
        input.stationId,
        input.mode,
        now,
        language(context),
        buildNameResolver(context)
      );

      if (movedState === state || movedState.outcome !== null) {
        return movedState;
      }

      return advanceDetective(movedState, context);
    }

    return state;
  },
  tick(state, _deltaMs, context) {
    if (state.phase !== "playing" || state.outcome !== null) {
      return state;
    }

    const now = context.now;
    const connected = connectedPlayerIds(context);

    if (state.stage === "shadow_move") {
      const shadowConnected = state.shadowPlayerId === null || connected.has(state.shadowPlayerId);
      const aiDue = state.aiDecideAt !== null && now >= state.aiDecideAt;
      const timeoutDue = state.turnEndsAt !== null && now >= state.turnEndsAt;

      if (aiDue || timeoutDue || !shadowConnected) {
        return advanceDetective(autoPlayShadow(state, context), context);
      }

      return state;
    }

    if (state.stage === "detective_move") {
      const active = state.detectives.find((entry) => entry.playerId === state.activeDetectivePlayerId);
      const timeoutDue = state.turnEndsAt !== null && now >= state.turnEndsAt;

      if (!active || !connected.has(active.playerId) || !listDetectiveOptions(state, active).some((option) => !option.blocked) || timeoutDue) {
        return advanceDetective({ ...state, detectives: state.detectives.map((entry) => entry.playerId === active?.playerId ? { ...entry, movedInTurn: state.turn } : entry) }, context);
      }
    }

    return state;
  },
  isRoundFinished(state) {
    return state.stage === "caught" || state.stage === "escaped";
  },
  buildScore(state, context): ScoreEntry[] {
    const text = getSchattenjagdText(language(context));

    if (state.outcome === "caught") {
      return state.detectives.map((detective) => ({
        playerId: detective.playerId,
        delta:
          schattenjagdConfig.score.detectiveWin +
          (detective.playerId === state.capturedByPlayerId
            ? schattenjagdConfig.score.detectiveCaptureBonus
            : 0),
        reason:
          detective.playerId === state.capturedByPlayerId ? text.scoreCapture : text.scoreDetectiveWin
      }));
    }

    if (state.outcome === "escaped" && state.shadowPlayerId) {
      return [
        {
          playerId: state.shadowPlayerId,
          delta: schattenjagdConfig.score.shadowEscape,
          reason: text.scoreShadowEscape
        }
      ];
    }

    return [];
  },
  toPublicState(state, context): SchattenjagdPublicState {
    const text = getSchattenjagdText(language(context));
    const resolveName = buildNameResolver(context);
    const connected = connectedPlayerIds(context);
    const players = new Map(context.players.map((player) => [player.id, player]));
    const finished = state.outcome !== null;

    return {
      stage: state.stage,
      turn: state.turn,
      totalTurns: state.totalTurns,
      mapVariant: state.mapVariant,
      revealTurns: schattenjagdConfig.revealTurns,
      nextRevealTurn: nextRevealTurn(state.turn),
      map: state.map,
      activeDetectivePlayerId: state.stage === "detective_move" && !finished ? state.activeDetectivePlayerId : null,
      reachableOptions: state.stage === "detective_move" && !finished
        ? state.detectives.filter((entry) => entry.playerId === state.activeDetectivePlayerId).flatMap((entry) => listDetectiveOptions(state, entry).filter((option) => !option.blocked))
        : [],
      detectives: state.detectives.map((detective) => ({
        playerId: detective.playerId,
        name: resolveName(detective.playerId),
        color: players.get(detective.playerId)?.color ?? "#38bdf8",
        stationId: detective.stationId,
        tickets: detective.tickets,
        lastMode: detective.lastMode,
        hasMoved: detective.movedInTurn >= state.turn,
        connected: connected.has(detective.playerId),
        isCapturer: detective.playerId === state.capturedByPlayerId
      })),
      shadow: {
        name:
          state.shadowPlayerId === null
            ? text.aiShadowName
            : finished
              ? resolveName(state.shadowPlayerId)
              : text.shadowUnknown,
        isAi: state.shadowPlayerId === null,
        lastKnownStationId: state.lastRevealedStationId,
        lastRevealTurn: state.lastRevealTurn,
        currentStationId: finished ? state.shadowStationId : null,
        shroudsLeft: state.shadowShroudsLeft,
        doublesLeft: state.shadowDoublesLeft,
        shroudArmed: state.shroudArmed,
        doubleArmed: state.doubleArmed
      },
      travelLog: state.travelLog,
      turnEndsAt: state.turnEndsAt,
      turnDurationMs: state.turnDurationMs,
      outcome: state.outcome,
      capturedByName: state.capturedByPlayerId ? resolveName(state.capturedByPlayerId) : null,
      captureStationId: state.captureStationId,
      message: state.message
    };
  },
  toControllerStateForPlayer(state, context, playerId): SchattenjagdControllerState {
    const text = getSchattenjagdText(language(context));
    const resolveName = buildNameResolver(context);
    const isShadow = state.shadowPlayerId !== null && state.shadowPlayerId === playerId;
    const detective = state.detectives.find((entry) => entry.playerId === playerId);
    const finished = state.outcome !== null;

    const role: SchattenjagdControllerState["role"] = isShadow
      ? "shadow"
      : detective
        ? "detective"
        : "spectator";

    const isMyTurn =
      (isShadow && state.stage === "shadow_move") ||
      (Boolean(detective) && state.stage === "detective_move" && state.activeDetectivePlayerId === playerId && !finished);

    const options = isShadow
      ? state.stage === "shadow_move"
        ? listShadowOptions(state)
        : []
      : detective && isMyTurn
        ? listDetectiveOptions(state, detective)
        : [];

    return {
      role,
      activeDetectiveName: state.stage === "detective_move" && state.activeDetectivePlayerId ? resolveName(state.activeDetectivePlayerId) : null,
      roleLabel: isShadow ? text.shadowRole : detective ? text.detectiveRole : text.spectatorRole,
      stage: state.stage,
      turn: state.turn,
      totalTurns: state.totalTurns,
      nextRevealTurn: nextRevealTurn(state.turn),
      isMyTurn,
      hasMoved: Boolean(detective && detective.movedInTurn >= state.turn),
      currentStationId: isShadow ? state.shadowStationId : detective?.stationId ?? null,
      options,
      tickets: detective ? detective.tickets : null,
      shroudsLeft: state.shadowShroudsLeft,
      doublesLeft: state.shadowDoublesLeft,
      shroudArmed: state.shroudArmed,
      doubleArmed: state.doubleArmed,
      doubleMoveStep: state.doubleMoveStep,
      lastKnownShadowStationId: state.lastRevealedStationId,
      lastRevealTurn: state.lastRevealTurn,
      detectiveStations: state.detectives.map((entry) => ({
        playerId: entry.playerId,
        name: resolveName(entry.playerId),
        stationId: entry.stationId,
        hasMoved: entry.movedInTurn >= state.turn
      })),
      travelLog: state.travelLog,
      turnEndsAt: state.turnEndsAt,
      turnDurationMs: state.turnDurationMs,
      outcome: state.outcome,
      shadowName:
        state.shadowPlayerId === null
          ? text.aiShadowName
          : finished
            ? resolveName(state.shadowPlayerId)
            : text.shadowUnknown,
      message: state.message
    };
  }
};
