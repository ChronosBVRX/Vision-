import { schattenjagdConfig, transportModes, type TransportMode } from "../config.js";
import type { StationLink, StationNode, TransitMap } from "../protocol.js";
import { fixedCityStations } from "./cityStations.js";
import { alignTransportStops } from "./cityNetwork.js";
import { createSeededRandom, type SeededRandom } from "./rng.js";

interface Point {
  x: number;
  y: number;
}

interface DraftStation extends Point {
  id: number;
}

const MARGIN_X = 52;
const MARGIN_Y = 46;
/** Das illustrierte Spielbrett und sein Verkehrsnetz bleiben deckungsgleich. */
const FIXED_CITY_LAYOUT_SEED = "schattenjagd-city-night-v1";

function distance(left: Point, right: Point): number {
  return Math.hypot(left.x - right.x, left.y - right.y);
}

class UnionFind {
  private readonly parent: number[];

  constructor(size: number) {
    this.parent = Array.from({ length: size }, (_, index) => index);
  }

  find(value: number): number {
    let root = value;

    while (this.parent[root] !== root) {
      this.parent[root] = this.parent[this.parent[root]];
      root = this.parent[root];
    }

    return root;
  }

  union(left: number, right: number): boolean {
    const leftRoot = this.find(left);
    const rightRoot = this.find(right);

    if (leftRoot === rightRoot) {
      return false;
    }

    this.parent[rightRoot] = leftRoot;
    return true;
  }
}

function placeStations(random: SeededRandom, stationCount: number, width: number, height: number): DraftStation[] {
  const usableWidth = width - MARGIN_X * 2;
  const usableHeight = height - MARGIN_Y * 2;
  const columns = Math.max(4, Math.round(Math.sqrt((stationCount * usableWidth) / usableHeight)));
  const rows = Math.max(3, Math.ceil(stationCount / columns));
  const cellWidth = usableWidth / columns;
  const cellHeight = usableHeight / rows;

  const cells: Point[] = [];

  for (let row = 0; row < rows; row += 1) {
    for (let column = 0; column < columns; column += 1) {
      cells.push({
        x: MARGIN_X + (column + 0.5) * cellWidth,
        y: MARGIN_Y + (row + 0.5) * cellHeight
      });
    }
  }

  const keptCells = random.shuffle(cells).slice(0, Math.min(stationCount, cells.length));

  // Moderater Versatz: genug Unregelmaessigkeit fuer eine organische Karte,
  // aber garantiert genug Abstand fuer lesbare Stationsnummern.
  const jittered = keptCells.map((cell) => ({
    x: cell.x + (random.next() - 0.5) * cellWidth * 0.44,
    y: cell.y + (random.next() - 0.5) * cellHeight * 0.44
  }));

  // Leserichtung sortieren, damit die Stationsnummern auf dem Host-Screen ruhig wirken.
  const rowHeight = usableHeight / rows;
  const sorted = [...jittered].sort((left, right) => {
    const leftBand = Math.floor((left.y - MARGIN_Y) / rowHeight);
    const rightBand = Math.floor((right.y - MARGIN_Y) / rowHeight);

    if (leftBand !== rightBand) {
      return leftBand - rightBand;
    }

    return left.x - right.x;
  });

  return sorted.map((point, index) => ({
    id: index + 1,
    x: Math.round(point.x),
    y: Math.round(point.y)
  }));
}

function farthestPointSampling(
  random: SeededRandom,
  candidates: DraftStation[],
  wanted: number
): DraftStation[] {
  if (candidates.length <= wanted) {
    return [...candidates];
  }

  const picked: DraftStation[] = [random.pick(candidates)];

  while (picked.length < wanted) {
    let bestCandidate: DraftStation | null = null;
    let bestDistance = -1;

    for (const candidate of candidates) {
      if (picked.some((entry) => entry.id === candidate.id)) {
        continue;
      }

      const nearest = picked.reduce(
        (accumulator, entry) => Math.min(accumulator, distance(entry, candidate)),
        Number.POSITIVE_INFINITY
      );

      if (nearest > bestDistance) {
        bestDistance = nearest;
        bestCandidate = candidate;
      }
    }

    if (!bestCandidate) {
      break;
    }

    picked.push(bestCandidate);
  }

  return picked;
}

class LinkSet {
  private readonly keys = new Set<string>();
  private readonly degree = new Map<string, number>();

  readonly links: StationLink[] = [];

  private key(a: number, b: number, mode: TransportMode): string {
    return `${Math.min(a, b)}:${Math.max(a, b)}:${mode}`;
  }

  private degreeKey(station: number, mode: TransportMode): string {
    return `${station}:${mode}`;
  }

  has(a: number, b: number, mode: TransportMode): boolean {
    return this.keys.has(this.key(a, b, mode));
  }

  degreeOf(station: number, mode: TransportMode): number {
    return this.degree.get(this.degreeKey(station, mode)) ?? 0;
  }

  add(a: number, b: number, mode: TransportMode): boolean {
    if (a === b || this.has(a, b, mode)) {
      return false;
    }

    this.keys.add(this.key(a, b, mode));
    this.degree.set(this.degreeKey(a, mode), this.degreeOf(a, mode) + 1);
    this.degree.set(this.degreeKey(b, mode), this.degreeOf(b, mode) + 1);
    this.links.push({ a: Math.min(a, b), b: Math.max(a, b), mode });
    return true;
  }
}

function connectComponents(
  stations: DraftStation[],
  linkSet: LinkSet,
  mode: TransportMode
): void {
  const indexById = new Map(stations.map((station, index) => [station.id, index]));
  const unionFind = new UnionFind(stations.length);

  for (const link of linkSet.links) {
    if (link.mode !== mode) {
      continue;
    }

    const leftIndex = indexById.get(link.a);
    const rightIndex = indexById.get(link.b);

    if (leftIndex === undefined || rightIndex === undefined) {
      continue;
    }

    unionFind.union(leftIndex, rightIndex);
  }

  for (let guard = 0; guard < stations.length; guard += 1) {
    const roots = new Set(stations.map((_, index) => unionFind.find(index)));

    if (roots.size <= 1) {
      return;
    }

    let bestPair: [number, number] | null = null;
    let bestDistance = Number.POSITIVE_INFINITY;

    for (let leftIndex = 0; leftIndex < stations.length; leftIndex += 1) {
      for (let rightIndex = leftIndex + 1; rightIndex < stations.length; rightIndex += 1) {
        if (unionFind.find(leftIndex) === unionFind.find(rightIndex)) {
          continue;
        }

        const gap = distance(stations[leftIndex], stations[rightIndex]);

        if (gap < bestDistance) {
          bestDistance = gap;
          bestPair = [leftIndex, rightIndex];
        }
      }
    }

    if (!bestPair) {
      return;
    }

    linkSet.add(stations[bestPair[0]].id, stations[bestPair[1]].id, mode);
    unionFind.union(bestPair[0], bestPair[1]);
  }
}

function buildTaxiLinks(
  random: SeededRandom,
  stations: DraftStation[],
  linkSet: LinkSet,
  cellSize: number
): void {
  const maxTaxiDistance = cellSize * 1.55;

  for (const station of stations) {
    const neighbours = stations
      .filter((candidate) => candidate.id !== station.id)
      .map((candidate) => ({ candidate, gap: distance(station, candidate) }))
      .sort((left, right) => left.gap - right.gap);

    const wanted = random.int(2, 4);
    let added = 0;

    for (const entry of neighbours) {
      if (added >= wanted) {
        break;
      }

      if (entry.gap > maxTaxiDistance) {
        break;
      }

      if (linkSet.degreeOf(entry.candidate.id, "taxi") >= 4) {
        continue;
      }

      if (linkSet.add(station.id, entry.candidate.id, "taxi")) {
        added += 1;
      }
    }

    if (added === 0 && neighbours.length > 0) {
      linkSet.add(station.id, neighbours[0].candidate.id, "taxi");
    }
  }

  connectComponents(stations, linkSet, "taxi");

  // A connected graph can still contain cul-de-sacs. Each station needs at
  // least two different road neighbours, not merely two transport modes.
  for (const station of stations) {
    const neighbours = stations
      .filter(candidate => candidate.id !== station.id)
      .sort((left, right) => distance(station, left) - distance(station, right));
    for (const neighbour of neighbours) {
      if (linkSet.degreeOf(station.id, "taxi") >= Math.min(2, stations.length - 1)) break;
      linkSet.add(station.id, neighbour.id, "taxi");
    }
  }
}

function buildLayerLinks(
  random: SeededRandom,
  layerStations: DraftStation[],
  linkSet: LinkSet,
  mode: TransportMode,
  connectionsPerStation: number,
  maxDistance: number
): void {
  for (const station of layerStations) {
    const neighbours = layerStations
      .filter((candidate) => candidate.id !== station.id)
      .map((candidate) => ({ candidate, gap: distance(station, candidate) }))
      .sort((left, right) => left.gap - right.gap);

    const wanted = random.int(connectionsPerStation, connectionsPerStation + 2);
    let added = 0;

    for (const entry of neighbours) {
      if (added >= wanted) {
        break;
      }

      if (entry.gap > maxDistance) {
        break;
      }

      if (linkSet.degreeOf(entry.candidate.id, mode) >= 5) {
        continue;
      }

      if (linkSet.add(station.id, entry.candidate.id, mode)) {
        added += 1;
      }
    }
  }

  connectComponents(layerStations, linkSet, mode);
}

function buildMetroLinks(
  random: SeededRandom,
  hubs: DraftStation[],
  linkSet: LinkSet,
  width: number,
  height: number
): void {
  const centre = { x: width / 2, y: height / 2 };
  const ring = [...hubs].sort(
    (left, right) =>
      Math.atan2(left.y - centre.y, left.x - centre.x) - Math.atan2(right.y - centre.y, right.x - centre.x)
  );

  for (let index = 0; index < ring.length; index += 1) {
    const current = ring[index];
    const next = ring[(index + 1) % ring.length];
    linkSet.add(current.id, next.id, "metro");
  }

  const chordCount = Math.max(1, Math.round(ring.length / 5));

  for (let index = 0; index < chordCount; index += 1) {
    const start = random.int(0, ring.length);
    const offset = random.int(2, Math.max(3, Math.floor(ring.length / 2) + 1));
    const target = (start + offset) % ring.length;
    linkSet.add(ring[start].id, ring[target].id, "metro");
  }

  connectComponents(hubs, linkSet, "metro");
}

export function generateTransitMap(
  _seed: string,
  stationCount: number = schattenjagdConfig.stationCount,
  width: number = schattenjagdConfig.mapWidth,
  height: number = schattenjagdConfig.mapHeight
): TransitMap {
  const random = createSeededRandom(`${FIXED_CITY_LAYOUT_SEED}:${stationCount}:${width}:${height}`);
  const stations = placeStations(random, stationCount, width, height);
  const cellSize = Math.sqrt(((width - MARGIN_X * 2) * (height - MARGIN_Y * 2)) / Math.max(1, stations.length));
  const linkSet = new LinkSet();

  buildTaxiLinks(random, stations, linkSet, cellSize);

  const busStops = farthestPointSampling(random, stations, Math.max(8, Math.round(stations.length * 0.36)));
  buildLayerLinks(random, busStops, linkSet, "bus", 2, cellSize * 3.1);

  const metroHubs = farthestPointSampling(random, busStops, Math.max(6, Math.round(stations.length * 0.13)));
  buildMetroLinks(random, metroHubs, linkSet, width, height);

  const neighbours: TransitMap["neighbours"] = {};

  for (const station of stations) {
    neighbours[station.id] = { taxi: [], bus: [], metro: [] };
  }

  for (const link of linkSet.links) {
    neighbours[link.a][link.mode].push(link.b);
    neighbours[link.b][link.mode].push(link.a);
  }

  const fixedPositionById = new Map(fixedCityStations.map((station) => [station.id, station]));
  const nodes: StationNode[] = stations.map((station) => {
    const fixedPosition = fixedPositionById.get(station.id);

    return {
      id: station.id,
      x: fixedPosition?.x ?? station.x,
      y: fixedPosition?.y ?? station.y,
      modes: transportModes.filter((mode) => neighbours[station.id][mode].length > 0)
    };
  });

  for (const station of nodes) {
    for (const mode of transportModes) {
      neighbours[station.id][mode].sort((left, right) => left - right);
    }
  }

  return alignTransportStops({
    width,
    height,
    stations: nodes,
    links: linkSet.links.sort((left, right) => left.a - right.a || left.b - right.b),
    neighbours
  });
}
