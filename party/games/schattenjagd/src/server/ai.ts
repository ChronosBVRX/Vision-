import { isRevealTurn, type TransportMode } from "../config.js";
import { bfsDistances, listNeighbourStations } from "../map/graph.js";
import { createSeededRandom } from "../map/rng.js";
import type { SchattenjagdState } from "../protocol.js";
import { listShadowOptions } from "./rules.js";

export interface ShadowDecision {
  stationId: number;
  mode: TransportMode;
  armShroud: boolean;
  armDouble: boolean;
}

const modeMobilityBonus: Record<TransportMode, number> = {
  taxi: 0,
  bus: 1.2,
  metro: 2.4
};

/**
 * Bewertet alle legalen Zuege des Schattens und waehlt den sichersten aus.
 * Die Bewertung mischt Abstand zu den Ermittlern, Anzahl der Anschlussverbindungen
 * und Verkehrsmittel-Reichweite, damit die KI nicht in Sackgassen laeuft.
 */
export function decideShadowMove(state: SchattenjagdState, seedSalt = ""): ShadowDecision | null {
  const options = listShadowOptions(state).filter((option) => !option.blocked);

  if (options.length === 0) {
    return null;
  }

  const random = createSeededRandom(
    `${state.turn}:${state.shadowStationId}:${state.detectives.map((entry) => entry.stationId).join("-")}:${seedSalt}`
  );
  const detectiveStations = state.detectives.map((detective) => detective.stationId);
  const distances = bfsDistances(state.map, detectiveStations);
  const hunted = detectiveStations.length > 0;
  const currentDistance = hunted ? distances.get(state.shadowStationId) ?? 99 : 99;
  const wasJustRevealed = state.lastRevealTurn !== null && state.lastRevealTurn >= state.turn - 1;
  const revealComing = isRevealTurn(state.turn);

  let bestDecision: ShadowDecision | null = null;
  let bestScore = Number.NEGATIVE_INFINITY;

  for (const option of options) {
    const safety = hunted ? distances.get(option.stationId) ?? 0 : 3;
    const escapeRoutes = listNeighbourStations(state.map, option.stationId).length;
    const relocationWeight = wasJustRevealed || revealComing ? 1.8 : 1;

    const score =
      safety * 9 +
      escapeRoutes * 0.7 +
      modeMobilityBonus[option.mode] * relocationWeight +
      random.next() * 1.6;

    if (score > bestScore) {
      bestScore = score;
      bestDecision = {
        stationId: option.stationId,
        mode: option.mode,
        armShroud: false,
        armDouble: false
      };
    }
  }

  if (!bestDecision) {
    return null;
  }

  const pressured = hunted && currentDistance <= 2;

  return {
    ...bestDecision,
    armShroud: state.shadowShroudsLeft > 0 && (pressured || revealComing),
    armDouble: state.shadowDoublesLeft > 0 && state.doubleMoveStep === 1 && (pressured || wasJustRevealed)
  };
}
