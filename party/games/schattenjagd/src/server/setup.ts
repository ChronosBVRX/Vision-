import { schattenjagdConfig } from "../config.js";
import { bfsDistances } from "../map/graph.js";
import type { SeededRandom } from "../map/rng.js";
import type { TransitMap } from "../protocol.js";

export interface StartPositions {
  detectiveStations: number[];
  shadowStation: number;
}

function minimumDistanceTo(map: TransitMap, sources: number[], target: number): number {
  if (sources.length === 0) {
    return Number.POSITIVE_INFINITY;
  }

  return bfsDistances(map, sources).get(target) ?? Number.POSITIVE_INFINITY;
}

export function chooseStartPositions(
  map: TransitMap,
  random: SeededRandom,
  detectiveCount: number
): StartPositions {
  const stationIds = map.stations.map((station) => station.id);

  if (stationIds.length === 0) {
    return { detectiveStations: [], shadowStation: 0 };
  }

  const detectiveStations: number[] = [];

  if (detectiveCount > 0) {
    detectiveStations.push(random.pick(stationIds));
  }

  while (detectiveStations.length < detectiveCount && detectiveStations.length < stationIds.length) {
    const distances = bfsDistances(map, detectiveStations);
    let bestStation = stationIds[0];
    let bestDistance = -1;

    for (const stationId of random.shuffle(stationIds)) {
      if (detectiveStations.includes(stationId)) {
        continue;
      }

      const distance = distances.get(stationId) ?? Number.POSITIVE_INFINITY;
      const comparable = Number.isFinite(distance) ? distance : stationIds.length;

      if (comparable > bestDistance) {
        bestDistance = comparable;
        bestStation = stationId;
      }
    }

    detectiveStations.push(bestStation);
  }

  const candidates = random
    .shuffle(stationIds)
    .filter((stationId) => !detectiveStations.includes(stationId));

  if (candidates.length === 0) {
    return { detectiveStations, shadowStation: stationIds[0] };
  }

  const distances = bfsDistances(map, detectiveStations);
  const scored = candidates.map((stationId) => ({
    stationId,
    distance: distances.get(stationId) ?? 0
  }));

  const bestDistance = scored.reduce((accumulator, entry) => Math.max(accumulator, entry.distance), 0);
  // Wunschabstand, aber nie mehr als die Karte hergibt.
  const threshold = Math.min(schattenjagdConfig.minimumStartDistance, bestDistance);
  const tier = scored.filter((entry) => entry.distance >= threshold);

  return {
    detectiveStations,
    shadowStation: random.pick(tier.length > 0 ? tier : scored).stationId
  };
}
