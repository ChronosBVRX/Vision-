import { isRevealTurn, schattenjagdConfig, transportModes, type TransportMode } from "../config.js";
import { listConnections } from "../map/graph.js";
import type {
  DetectiveState,
  SchattenjagdMoveOption,
  SchattenjagdState,
  TicketWallet
} from "../protocol.js";
import { getSchattenjagdText, type SchattenjagdLanguage } from "../text.js";

const AI_THINK_MS = schattenjagdConfig.aiThinkMs;

export const UNLIMITED_TICKETS = -1;

/** Zugende gemaess Lobby-Einstellung. Ohne Zeitlimit oder fuer die KI gibt es keinen Countdown. */
function resolveTurnEndsAt(state: SchattenjagdState, now: number, skipTimer: boolean): number | null {
  if (skipTimer || state.turnDurationMs === null) {
    return null;
  }

  return now + state.turnDurationMs;
}

export function createTicketWallet(): TicketWallet {
  return {
    taxi: schattenjagdConfig.detectiveTickets.taxi,
    bus: schattenjagdConfig.detectiveTickets.bus,
    metro: schattenjagdConfig.detectiveTickets.metro
  };
}

export function occupiedStations(state: SchattenjagdState, exceptPlayerId?: string): Set<number> {
  const occupied = new Set<number>();

  for (const detective of state.detectives) {
    if (detective.playerId === exceptPlayerId) {
      continue;
    }

    occupied.add(detective.stationId);
  }

  return occupied;
}

function dedupeOptions(options: SchattenjagdMoveOption[]): SchattenjagdMoveOption[] {
  const seen = new Set<string>();
  const result: SchattenjagdMoveOption[] = [];

  for (const option of options) {
    const key = `${option.stationId}:${option.mode}`;

    if (seen.has(key)) {
      continue;
    }

    seen.add(key);
    result.push(option);
  }

  return result.sort((left, right) => {
    const modeOrder = transportModes.indexOf(left.mode) - transportModes.indexOf(right.mode);
    return modeOrder !== 0 ? modeOrder : left.stationId - right.stationId;
  });
}

export function listShadowOptions(state: SchattenjagdState): SchattenjagdMoveOption[] {
  const blockedStations = occupiedStations(state);

  return dedupeOptions(
    listConnections(state.map, state.shadowStationId).map((connection) => ({
      stationId: connection.stationId,
      mode: connection.mode,
      ticketsLeft: UNLIMITED_TICKETS,
      blocked: blockedStations.has(connection.stationId),
      blockedReason: blockedStations.has(connection.stationId) ? ("occupied" as const) : undefined
    }))
  );
}

export function listDetectiveOptions(
  state: SchattenjagdState,
  detective: DetectiveState
): SchattenjagdMoveOption[] {
  const blockedStations = occupiedStations(state, detective.playerId);

  return dedupeOptions(
    listConnections(state.map, detective.stationId).map((connection) => {
      const ticketsLeft = detective.tickets[connection.mode];
      const occupied = blockedStations.has(connection.stationId);

      return {
        stationId: connection.stationId,
        mode: connection.mode,
        ticketsLeft,
        blocked: occupied || ticketsLeft <= 0,
        blockedReason: occupied ? ("occupied" as const) : ticketsLeft <= 0 ? ("no_ticket" as const) : undefined
      };
    })
  );
}

export function hasLegalMove(options: SchattenjagdMoveOption[]): boolean {
  return options.some((option) => !option.blocked);
}

export function activeDetectives(
  state: SchattenjagdState,
  connectedPlayerIds: ReadonlySet<string>
): DetectiveState[] {
  return state.detectives.filter((detective) => connectedPlayerIds.has(detective.playerId));
}

export function pendingDetectives(
  state: SchattenjagdState,
  connectedPlayerIds: ReadonlySet<string>
): DetectiveState[] {
  return activeDetectives(state, connectedPlayerIds).filter(
    (detective) => detective.movedInTurn < state.turn && hasLegalMove(listDetectiveOptions(state, detective))
  );
}

export function beginShadowTurn(
  state: SchattenjagdState,
  now: number,
  language: SchattenjagdLanguage,
  isAiShadow: boolean
): SchattenjagdState {
  const text = getSchattenjagdText(language);
  const options = listShadowOptions(state);

  if (!hasLegalMove(options)) {
    return {
      ...state,
      stage: "caught",
      outcome: "caught",
      turnEndsAt: null,
      aiDecideAt: null,
      captureStationId: state.shadowStationId,
      updatedAt: now,
      message: text.shadowStuck
    };
  }

  return {
    ...state,
    stage: "shadow_move",
    activeDetectivePlayerId: null,
    doubleMoveStep: 1,
    turnEndsAt: resolveTurnEndsAt(state, now, isAiShadow),
    aiDecideAt: isAiShadow ? now + AI_THINK_MS : null,
    updatedAt: now,
    message: text.shadowTurnStart(state.turn, state.totalTurns)
  };
}

function revealIfNeeded(state: SchattenjagdState): SchattenjagdState {
  if (!isRevealTurn(state.turn)) {
    return state;
  }

  // Bei einem Doppelzug zaehlt nur die Endposition des Zuges als Sichtung.
  const lastIndexOfTurn = state.travelLog.reduce(
    (accumulator, entry, index) => (entry.turn === state.turn ? index : accumulator),
    -1
  );

  const travelLog = state.travelLog.map((entry, index) =>
    index === lastIndexOfTurn ? { ...entry, revealedStationId: state.shadowStationId } : entry
  );

  return {
    ...state,
    lastRevealedStationId: state.shadowStationId,
    lastRevealTurn: state.turn,
    travelLog
  };
}

export function beginDetectiveTurn(
  state: SchattenjagdState,
  now: number,
  language: SchattenjagdLanguage
): SchattenjagdState {
  const text = getSchattenjagdText(language);
  const revealed = revealIfNeeded(state);
  const surfaced = revealed.lastRevealTurn === revealed.turn;

  return {
    ...revealed,
    stage: "detective_move",
    activeDetectivePlayerId: revealed.detectives.find((entry) => entry.movedInTurn < revealed.turn)?.playerId ?? null,
    turnEndsAt: resolveTurnEndsAt(revealed, now, false),
    aiDecideAt: null,
    updatedAt: now,
    message: surfaced && revealed.lastRevealedStationId !== null
      ? text.shadowSurfaced(revealed.lastRevealedStationId)
      : text.detectivesTurn
  };
}

export function applyShadowMove(
  state: SchattenjagdState,
  stationId: number,
  mode: TransportMode,
  now: number,
  language: SchattenjagdLanguage,
  isAiShadow: boolean
): SchattenjagdState {
  const text = getSchattenjagdText(language);
  const option = listShadowOptions(state).find(
    (entry) => entry.stationId === stationId && entry.mode === mode && !entry.blocked
  );

  if (!option) {
    return state;
  }

  const shrouded = state.shroudArmed && state.shadowShroudsLeft > 0;
  const startsDoubleMove =
    state.doubleMoveStep === 1 && state.doubleArmed && state.shadowDoublesLeft > 0;

  const movedState: SchattenjagdState = {
    ...state,
    shadowStationId: stationId,
    shroudArmed: false,
    doubleArmed: false,
    shadowShroudsLeft: shrouded ? state.shadowShroudsLeft - 1 : state.shadowShroudsLeft,
    shadowDoublesLeft: startsDoubleMove ? state.shadowDoublesLeft - 1 : state.shadowDoublesLeft,
    travelLog: [
      ...state.travelLog,
      {
        turn: state.turn,
        mode: shrouded ? null : mode,
        shrouded,
        doubleMove: startsDoubleMove || state.doubleMoveStep === 2,
        revealedStationId: null
      }
    ],
    updatedAt: now,
    message: shrouded ? text.shadowMovedShrouded : text.shadowMovedPublic(text.modeLabels[mode])
  };

  if (startsDoubleMove) {
    const secondStep: SchattenjagdState = {
      ...movedState,
      stage: "shadow_move",
      doubleMoveStep: 2,
      turnEndsAt: resolveTurnEndsAt(movedState, now, isAiShadow),
      aiDecideAt: isAiShadow ? now + AI_THINK_MS : null,
      message: text.doubleSecondStep
    };

    return hasLegalMove(listShadowOptions(secondStep))
      ? secondStep
      : beginDetectiveTurn({ ...movedState, doubleMoveStep: 1 }, now, language);
  }

  return beginDetectiveTurn({ ...movedState, doubleMoveStep: 1 }, now, language);
}

export function applyDetectiveMove(
  state: SchattenjagdState,
  playerId: string,
  stationId: number,
  mode: TransportMode,
  now: number,
  language: SchattenjagdLanguage,
  resolveName: (playerId: string) => string
): SchattenjagdState {
  const text = getSchattenjagdText(language);
  const detective = state.detectives.find((entry) => entry.playerId === playerId);

  if (state.stage !== "detective_move" || state.activeDetectivePlayerId !== playerId || !detective || detective.movedInTurn >= state.turn) {
    return state;
  }

  const option = listDetectiveOptions(state, detective).find(
    (entry) => entry.stationId === stationId && entry.mode === mode && !entry.blocked
  );

  if (!option) {
    return state;
  }

  const detectives = state.detectives.map((entry) =>
    entry.playerId === playerId
      ? {
          ...entry,
          stationId,
          lastMode: mode,
          movedInTurn: state.turn,
          tickets: { ...entry.tickets, [mode]: entry.tickets[mode] - 1 }
        }
      : entry
  );

  const nextState: SchattenjagdState = {
    ...state,
    detectives,
    updatedAt: now
  };

  if (stationId === state.shadowStationId) {
    return {
      ...nextState,
      stage: "caught",
      outcome: "caught",
      capturedByPlayerId: playerId,
      captureStationId: stationId,
      turnEndsAt: null,
      aiDecideAt: null,
      message: text.caughtMessage(resolveName(playerId), stationId)
    };
  }

  return nextState;
}

export function finishDetectivePhase(
  state: SchattenjagdState,
  now: number,
  language: SchattenjagdLanguage,
  isAiShadow: boolean,
  resolveName: (playerId: string) => string
): SchattenjagdState {
  const text = getSchattenjagdText(language);
  const capturingDetective = state.detectives.find(
    (detective) => detective.stationId === state.shadowStationId
  );

  if (capturingDetective) {
    return {
      ...state,
      stage: "caught",
      outcome: "caught",
      capturedByPlayerId: capturingDetective.playerId,
      captureStationId: state.shadowStationId,
      turnEndsAt: null,
      aiDecideAt: null,
      updatedAt: now,
      message: text.caughtMessage(resolveName(capturingDetective.playerId), state.shadowStationId)
    };
  }

  if (state.turn >= state.totalTurns) {
    return {
      ...state,
      stage: "escaped",
      outcome: "escaped",
      turnEndsAt: null,
      aiDecideAt: null,
      updatedAt: now,
      message: text.escapedMessage
    };
  }

  return beginShadowTurn({ ...state, turn: state.turn + 1 }, now, language, isAiShadow);
}
