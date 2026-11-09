import type { TransportMode } from "../config.js";
import type { StationLink, StationNode, TransitMap } from "../protocol.js";
import { cityRoadPaths } from "./cityRoadPaths.js";

export interface RoutePoint {
  x: number;
  y: number;
}

export interface CityRoadSection {
  key: string;
  a: number;
  b: number;
  from: RoutePoint;
  to: RoutePoint;
  points: readonly RoutePoint[];
  modes: Set<TransportMode>;
}

export interface CityNetwork {
  sections: CityRoadSection[];
  /** Express connections use a sequence of the same road sections as taxis. */
  routes: Map<string, number[]>;
}

export function roadKey(a: number, b: number): string {
  return `${Math.min(a, b)}:${Math.max(a, b)}`;
}

export function routeKey(link: StationLink): string {
  return `${roadKey(link.a, link.b)}:${link.mode}`;
}

/** A canonical orientation keeps Metro above/left and Taxi below/right,
 * regardless of which direction a vehicle travels along the road. */
export const CITY_LANE_WIDTH = 2.2;

export function sectionLane(section: CityRoadSection, mode: TransportMode): RoutePoint[] {
  const dx = section.to.x - section.from.x;
  const dy = section.to.y - section.from.y;
  const horizontal = Math.abs(dx) >= Math.abs(dy);
  const direction = horizontal ? Math.sign(dx) || 1 : Math.sign(dy) || 1;
  const metroSide = horizontal ? -direction : direction;
  const offset = mode === "bus" ? 0 : metroSide * (mode === "metro" ? CITY_LANE_WIDTH : -CITY_LANE_WIDTH);
  const normals = section.points.slice(1).map((point, index) => {
    const from = section.points[index];
    const dx = point.x - from.x;
    const dy = point.y - from.y;
    const length = Math.hypot(dx, dy) || 1;
    return { x: -dy / length, y: dx / length };
  });
  return section.points.map((point, index) => {
    const before = normals[Math.max(0, index - 1)];
    const after = normals[Math.min(normals.length - 1, index)];
    // Miter intersection: all lanes bend at the same road vertex, without
    // taking independent shortcuts across the corner.
    const nx = before.x + after.x;
    const ny = before.y + after.y;
    const normalLength = Math.hypot(nx, ny) || 1;
    const normal = { x: nx / normalLength, y: ny / normalLength };
    const projection = normal.x * after.x + normal.y * after.y;
    const shift = offset / Math.max(0.3, projection);
    return { x: point.x + normal.x * shift, y: point.y + normal.y * shift };
  });
}

function roadPoints(from: StationNode, to: StationNode): RoutePoint[] {
  const interior = cityRoadPaths[roadKey(from.id, to.id)] ?? [];
  const points: RoutePoint[] = [from, ...interior.map(([x, y]) => ({ x, y })), to];
  // Drop duplicate and near-collinear micro-bends from the original artwork
  // alignment. The retained road corners are shared by every transport.
  const unique = points.filter((point, index) => index === 0 || point.x !== points[index - 1].x || point.y !== points[index - 1].y);
  for (let index = 1; index < unique.length - 1;) {
    const before = unique[index - 1], point = unique[index], after = unique[index + 1];
    const dx = after.x - before.x, dy = after.y - before.y;
    const length = Math.hypot(dx, dy);
    const along = ((point.x - before.x) * dx + (point.y - before.y) * dy) / (length * length || 1);
    const gap = Math.abs(dx * (point.y - before.y) - dy * (point.x - before.x)) / (length || 1);
    if (along >= 0 && along <= 1 && gap < 5) unique.splice(index, 1);
    else index += 1;
  }
  return unique;
}

function stopsAlongRoad(map: TransitMap, section: CityRoadSection): number[] {
  const stops = [{ id: section.a, distance: 0 }];
  let travelled = 0;
  for (let index = 1; index < section.points.length; index += 1) {
    const from = section.points[index - 1], to = section.points[index];
    const dx = to.x - from.x, dy = to.y - from.y;
    const length = Math.hypot(dx, dy);
    for (const station of map.stations) {
      if (station.id === section.a || station.id === section.b) continue;
      const along = ((station.x - from.x) * dx + (station.y - from.y) * dy) / (length * length || 1);
      if (along < 0 || along > 1) continue;
      const gap = Math.hypot(station.x - from.x - along * dx, station.y - from.y - along * dy);
      if (gap <= 9 && !stops.some(stop => stop.id === station.id)) {
        stops.push({ id: station.id, distance: travelled + along * length });
      }
    }
    travelled += length;
  }
  stops.push({ id: section.b, distance: travelled });
  return stops.sort((a, b) => a.distance - b.distance).map(stop => stop.id);
}

function mapWithLinks(map: TransitMap, links: Iterable<StationLink>): TransitMap {
  const sorted = [...links].sort((a, b) => a.a - b.a || a.b - b.b || a.mode.localeCompare(b.mode));
  const neighbours: TransitMap["neighbours"] = {};
  for (const station of map.stations) neighbours[station.id] = { taxi: [], bus: [], metro: [] };
  for (const link of sorted) {
    neighbours[link.a][link.mode].push(link.b);
    neighbours[link.b][link.mode].push(link.a);
  }
  for (const station of map.stations) {
    for (const mode of ["taxi", "bus", "metro"] as const) neighbours[station.id][mode].sort((a, b) => a - b);
  }
  return { ...map, links: sorted, neighbours };
}

function alignTaxiStops(map: TransitMap): TransitMap {
  // Old road-following taxi arcs sometimes crossed a numbered stop. Split
  // those arcs too, so a visible junction never hides a one-ticket shortcut.
  let aligned = map;
  for (let pass = 0; pass < map.stations.length; pass += 1) {
    const links = new Map(aligned.links.filter(link => link.mode !== "taxi").map(link => [routeKey(link), link]));
    let changed = false;
    for (const link of aligned.links) {
      if (link.mode !== "taxi") continue;
      const from = aligned.stations.find(station => station.id === link.a)!;
      const to = aligned.stations.find(station => station.id === link.b)!;
      const route = stopsAlongRoad(aligned, { key: roadKey(link.a, link.b), a: link.a, b: link.b, from, to, points: roadPoints(from, to), modes: new Set(["taxi"]) });
      changed ||= route.length > 2;
      for (let index = 1; index < route.length; index += 1) {
        const next: StationLink = { a: Math.min(route[index - 1], route[index]), b: Math.max(route[index - 1], route[index]), mode: "taxi" };
        links.set(routeKey(next), next);
      }
    }
    aligned = mapWithLinks(aligned, links.values());
    if (!changed) break;
  }
  // Splitting a redundant arc may expose a leaf. Give it a real second road,
  // chosen by distance so it does not pass through another station first.
  const links = new Map(aligned.links.map(link => [routeKey(link), link]));
  for (const station of aligned.stations) {
    const neighbours = new Set([...links.values()].filter(link => link.mode === "taxi" && (link.a === station.id || link.b === station.id)).map(link => link.a === station.id ? link.b : link.a));
    const candidates = aligned.stations.filter(candidate => candidate.id !== station.id && !neighbours.has(candidate.id))
      .sort((a, b) => Math.hypot(a.x - station.x, a.y - station.y) - Math.hypot(b.x - station.x, b.y - station.y));
    for (const candidate of candidates) {
      if (neighbours.size >= Math.min(2, aligned.stations.length - 1)) break;
      const link: StationLink = { a: Math.min(station.id, candidate.id), b: Math.max(station.id, candidate.id), mode: "taxi" };
      const from = aligned.stations.find(entry => entry.id === link.a)!;
      const to = aligned.stations.find(entry => entry.id === link.b)!;
      if (stopsAlongRoad(aligned, { ...link, key: roadKey(link.a, link.b), from, to, points: roadPoints(from, to), modes: new Set(["taxi"]) }).length > 2) continue;
      links.set(routeKey(link), link);
      neighbours.add(candidate.id);
    }
  }
  return mapWithLinks(aligned, links.values());
}

function shortestRoadRoute(map: TransitMap, stations: Map<number, StationNode>, from: number, to: number): number[] {
  const distances = new Map<number, number>([[from, 0]]);
  const previous = new Map<number, number>();
  const remaining = new Set(stations.keys());

  while (remaining.size > 0) {
    let current: number | undefined;
    let distance = Number.POSITIVE_INFINITY;
    for (const candidate of remaining) {
      const candidateDistance = distances.get(candidate) ?? Number.POSITIVE_INFINITY;
      if (candidateDistance < distance) {
        current = candidate;
        distance = candidateDistance;
      }
    }
    if (current === undefined) break;
    if (current === to) {
      const route = [to];
      while (route[0] !== from) route.unshift(previous.get(route[0])!);
      return route;
    }
    remaining.delete(current);
    const origin = stations.get(current)!;
    for (const neighbour of map.neighbours[current].taxi) {
      if (!remaining.has(neighbour)) continue;
      const destination = stations.get(neighbour)!;
      const points = roadPoints(origin.id < destination.id ? origin : destination, origin.id < destination.id ? destination : origin);
      const length = points.slice(1).reduce((total, point, index) => total + Math.hypot(point.x - points[index].x, point.y - points[index].y), 0);
      const nextDistance = distance + length;
      if (nextDistance < (distances.get(neighbour) ?? Number.POSITIVE_INFINITY)) {
        distances.set(neighbour, nextDistance);
        previous.set(neighbour, current);
      }
    }
  }
  return [];
}

/** One connected street mesh, with no independently authored transport bends.
 * Intermediate nodes on an express route do not add playable stops. */
export function buildCityNetwork(map: TransitMap): CityNetwork {
  const stations = new Map(map.stations.map(station => [station.id, station]));
  const sections = new Map<string, CityRoadSection>();
  const routes = new Map<string, number[]>();
  for (const link of map.links) {
    if (link.mode !== "taxi") continue;
    sections.set(roadKey(link.a, link.b), {
      key: roadKey(link.a, link.b), a: link.a, b: link.b,
      from: stations.get(link.a)!, to: stations.get(link.b)!,
      points: roadPoints(stations.get(link.a)!, stations.get(link.b)!), modes: new Set(["taxi"])
    });
  }
  for (const link of map.links) {
    const route = link.mode === "taxi" ? [link.a, link.b] : shortestRoadRoute(map, stations, link.a, link.b);
    routes.set(routeKey(link), route);
    for (let index = 1; index < route.length; index += 1) {
      sections.get(roadKey(route[index - 1], route[index]))!.modes.add(link.mode);
    }
  }
  return { sections: [...sections.values()], routes };
}

/** A visible transit line stops at the next station supporting that mode.
 * Otherwise overlapping express links would imply shortcuts that cannot be
 * read from the shared mesh. Taxi-only stations remain pass-throughs. */
export function alignTransportStops(map: TransitMap): TransitMap {
  map = alignTaxiStops(map);
  const network = buildCityNetwork(map);
  const stations = new Map(map.stations.map(station => [station.id, station]));
  const links = new Map<string, StationLink>();
  for (const link of map.links) {
    const route = network.routes.get(routeKey(link))!;
    const stops = route.filter(id => stations.get(id)!.modes.includes(link.mode));
    for (let index = 1; index < stops.length; index += 1) {
      const next = { a: Math.min(stops[index - 1], stops[index]), b: Math.max(stops[index - 1], stops[index]), mode: link.mode };
      links.set(routeKey(next), next);
    }
  }
  return mapWithLinks(map, links.values());
}

/** Travel and highlighting consume exactly the same lanes as the board. */
export function networkRoutePoints(network: CityNetwork, link: StationLink): RoutePoint[] {
  const route = network.routes.get(routeKey(link)) ?? [];
  const sectionByKey = new Map(network.sections.map(section => [section.key, section]));
  const points: RoutePoint[] = [];
  for (let index = 1; index < route.length; index += 1) {
    const section = sectionByKey.get(roadKey(route[index - 1], route[index]))!;
    const lane = sectionLane(section, link.mode);
    if (section.a !== route[index - 1]) lane.reverse();
    if (points.length === 0) points.push(section.a === route[index - 1] ? section.from : section.to);
    points.push(...lane);
    if (index === route.length - 1) points.push(section.b === route[index] ? section.to : section.from);
  }
  return points.filter((point, index) => index === 0 || point.x !== points[index - 1].x || point.y !== points[index - 1].y);
}
