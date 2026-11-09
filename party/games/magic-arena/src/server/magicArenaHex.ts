import type { MagicArenaCoord } from "../protocol.js";

export const magicArenaDirections: MagicArenaCoord[] = [
  { q: 1, r: 0 },
  { q: 1, r: -1 },
  { q: 0, r: -1 },
  { q: -1, r: 0 },
  { q: -1, r: 1 },
  { q: 0, r: 1 }
];

export function axialKey(coord: MagicArenaCoord): string {
  return `${coord.q},${coord.r}`;
}

export function parseAxialKey(key: string): MagicArenaCoord {
  const [q, r] = key.split(",").map((part) => Number.parseInt(part, 10));
  return {
    q: Number.isFinite(q) ? q : 0,
    r: Number.isFinite(r) ? r : 0
  };
}

export function sameCoord(left: MagicArenaCoord | undefined, right: MagicArenaCoord | undefined): boolean {
  return Boolean(left && right && left.q === right.q && left.r === right.r);
}

export function addCoords(left: MagicArenaCoord, right: MagicArenaCoord): MagicArenaCoord {
  return {
    q: left.q + right.q,
    r: left.r + right.r
  };
}

export function scaleCoord(coord: MagicArenaCoord, amount: number): MagicArenaCoord {
  return {
    q: coord.q * amount,
    r: coord.r * amount
  };
}

export function hexDistance(left: MagicArenaCoord, right: MagicArenaCoord): number {
  return (
    Math.abs(left.q - right.q) +
    Math.abs(left.r - right.r) +
    Math.abs(left.q + left.r - right.q - right.r)
  ) / 2;
}

export function getNeighbors(coord: MagicArenaCoord): MagicArenaCoord[] {
  return magicArenaDirections.map((direction) => addCoords(coord, direction));
}

export function isInsideBoard(coord: MagicArenaCoord, radius: number): boolean {
  return Math.max(Math.abs(coord.q), Math.abs(coord.r), Math.abs(-coord.q - coord.r)) <= radius;
}

export function getDirectionByIndex(direction: number): MagicArenaCoord | null {
  if (!Number.isInteger(direction) || direction < 0 || direction >= magicArenaDirections.length) {
    return null;
  }

  return magicArenaDirections[direction] ?? null;
}

export function getDirectionIndexBetween(
  from: MagicArenaCoord,
  to: MagicArenaCoord
): number | null {
  const delta = {
    q: to.q - from.q,
    r: to.r - from.r
  };

  const directionIndex = magicArenaDirections.findIndex((direction) => direction.q === delta.q && direction.r === delta.r);
  return directionIndex >= 0 ? directionIndex : null;
}

export function getStepToward(from: MagicArenaCoord, to: MagicArenaCoord): MagicArenaCoord | null {
  const currentDistance = hexDistance(from, to);

  if (currentDistance <= 0) {
    return null;
  }

  return getNeighbors(from)
    .map((coord) => ({
      coord,
      distance: hexDistance(coord, to)
    }))
    .sort((left, right) => left.distance - right.distance || axialKey(left.coord).localeCompare(axialKey(right.coord)))
    .find((entry) => entry.distance < currentDistance)?.coord ?? null;
}

export function createBoardCoords(radius: number): MagicArenaCoord[] {
  const coords: MagicArenaCoord[] = [];

  for (let q = -radius; q <= radius; q += 1) {
    for (let r = -radius; r <= radius; r += 1) {
      const coord = { q, r };

      if (isInsideBoard(coord, radius)) {
        coords.push(coord);
      }
    }
  }

  return coords.sort((left, right) => left.q - right.q || left.r - right.r);
}
