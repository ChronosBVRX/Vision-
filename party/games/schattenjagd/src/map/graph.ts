import { transportModes, type TransportMode } from "../config.js";
import type { TransitMap } from "../protocol.js";

export interface Connection {
  stationId: number;
  mode: TransportMode;
}

export function listConnections(map: TransitMap, stationId: number): Connection[] {
  const entry = map.neighbours[stationId];

  if (!entry) {
    return [];
  }

  const connections: Connection[] = [];

  for (const mode of transportModes) {
    for (const target of entry[mode] ?? []) {
      connections.push({ stationId: target, mode });
    }
  }

  return connections;
}

export function listNeighbourStations(map: TransitMap, stationId: number): number[] {
  return [...new Set(listConnections(map, stationId).map((connection) => connection.stationId))];
}

/** Hop-Distanz ueber alle Verkehrsmittel, ausgehend von mehreren Startstationen. */
export function bfsDistances(map: TransitMap, sources: number[]): Map<number, number> {
  const distances = new Map<number, number>();
  const queue: number[] = [];

  for (const source of sources) {
    if (!distances.has(source)) {
      distances.set(source, 0);
      queue.push(source);
    }
  }

  let head = 0;

  while (head < queue.length) {
    const current = queue[head];
    head += 1;
    const currentDistance = distances.get(current) ?? 0;

    for (const neighbour of listNeighbourStations(map, current)) {
      if (distances.has(neighbour)) {
        continue;
      }

      distances.set(neighbour, currentDistance + 1);
      queue.push(neighbour);
    }
  }

  return distances;
}

export function distanceBetween(map: TransitMap, from: number, to: number): number {
  return bfsDistances(map, [from]).get(to) ?? Number.POSITIVE_INFINITY;
}

export function isConnected(map: TransitMap): boolean {
  if (map.stations.length === 0) {
    return false;
  }

  return bfsDistances(map, [map.stations[0].id]).size === map.stations.length;
}
