import type {
  MagicArenaBoardState,
  MagicArenaCoord,
  MagicArenaFieldState,
  MagicArenaFieldType,
  MagicArenaSpecialFieldType
} from "../protocol.js";
import {
  BOARD_RADIUS,
  NORMAL_FIELD_HP_MAX,
  NORMAL_FIELD_HP_MIN,
  SPECIAL_CRACKED_COUNT_RADIUS_5,
  SPECIAL_EXPLOSIVE_COUNT_RADIUS_5,
  STRONG_FIELD_HP_MAX,
  STRONG_FIELD_HP_MIN,
  WEAK_FIELD_HP_MAX,
  WEAK_FIELD_HP_MIN
} from "./magicArenaConfig.js";
import { axialKey, createBoardCoords, hexDistance, scaleCoord, magicArenaDirections } from "./magicArenaHex.js";

function hashSeed(seed: string): number {
  let hash = 2166136261;

  for (let index = 0; index < seed.length; index += 1) {
    hash ^= seed.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }

  return hash >>> 0;
}

function createRng(seed: string): () => number {
  let state = hashSeed(seed);

  return () => {
    state += 0x6d2b79f5;
    let value = state;
    value = Math.imul(value ^ (value >>> 15), value | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };
}

export function seededShuffle<T>(items: readonly T[], seed: string): T[] {
  const rng = createRng(seed);
  const copy = [...items];

  for (let index = copy.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(rng() * (index + 1));
    const current = copy[index] as T;
    copy[index] = copy[swapIndex] as T;
    copy[swapIndex] = current;
  }

  return copy;
}

export function getFieldMaxHp(type: MagicArenaFieldType): number {
  switch (type) {
    case "weak":
      return 2;
    case "strong":
      return 6;
    case "normal":
    default:
      return 4;
  }
}

function getFieldHpRange(type: MagicArenaFieldType): { min: number; max: number } {
  switch (type) {
    case "weak":
      return { min: WEAK_FIELD_HP_MIN, max: WEAK_FIELD_HP_MAX };
    case "strong":
      return { min: STRONG_FIELD_HP_MIN, max: STRONG_FIELD_HP_MAX };
    case "normal":
    default:
      return { min: NORMAL_FIELD_HP_MIN, max: NORMAL_FIELD_HP_MAX };
  }
}

function createFieldMaxHp(type: MagicArenaFieldType, rng: () => number): number {
  const range = getFieldHpRange(type);
  return range.min + Math.floor(rng() * (range.max - range.min + 1));
}

export function createMagicArenaBoard(seed: string, radius = BOARD_RADIUS): MagicArenaBoardState {
  const coords = createBoardCoords(radius);
  const weakCount = Math.round(coords.length * 0.15);
  const strongCount = Math.round(coords.length * 0.2);
  const normalCount = Math.max(0, coords.length - weakCount - strongCount);
  const fieldTypes = seededShuffle<MagicArenaFieldType>(
    [
      ...Array.from({ length: weakCount }, () => "weak" as const),
      ...Array.from({ length: normalCount }, () => "normal" as const),
      ...Array.from({ length: strongCount }, () => "strong" as const)
    ],
    `${seed}:board-types`
  );
  const fieldsByCoord: Record<string, MagicArenaFieldState> = {};
  const hpRng = createRng(`${seed}:field-hp`);

  for (const [index, coord] of coords.entries()) {
    const type = fieldTypes[index] ?? "normal";
    const maxHp = createFieldMaxHp(type, hpRng);
    fieldsByCoord[axialKey(coord)] = {
      coord,
      exists: true,
      hp: maxHp,
      maxHp,
      type,
      specialType: null,
      occupants: []
    };
  }

  return {
    radius,
    fieldsByCoord
  };
}

export function getSpawnSideIndexes(playerCount: number): number[] {
  if (playerCount <= 2) {
    return [0, 3];
  }

  if (playerCount === 3) {
    return [0, 2, 4];
  }

  return [0, 1, 3, 4];
}

export function getSpawnCandidates(sideIndex: number, radius = BOARD_RADIUS): MagicArenaCoord[] {
  const center = scaleCoord(magicArenaDirections[sideIndex] ?? magicArenaDirections[0], radius);

  return createBoardCoords(radius)
    .map((coord) => ({
      coord,
      edgeDistance: Math.max(Math.abs(coord.q), Math.abs(coord.r), Math.abs(-coord.q - coord.r)),
      distanceToCenter: hexDistance(coord, center)
    }))
    .filter((entry) => entry.edgeDistance >= radius - 1)
    .sort(
      (left, right) =>
        left.distanceToCenter - right.distanceToCenter ||
        right.edgeDistance - left.edgeDistance ||
        axialKey(left.coord).localeCompare(axialKey(right.coord))
    )
    .map((entry) => entry.coord);
}

export function makeSpawnFieldSafe(board: MagicArenaBoardState, coord: MagicArenaCoord): void {
  const field = board.fieldsByCoord[axialKey(coord)];

  if (!field) {
    return;
  }

  if (field.type === "weak") {
    field.type = "normal";
    field.maxHp = getFieldMaxHp("normal");
    field.hp = Math.max(field.hp, field.maxHp);
  }

  field.specialType = null;
}

export function assignMagicArenaSpecialFields(board: MagicArenaBoardState, seed: string): void {
  const candidates = seededShuffle(
    Object.values(board.fieldsByCoord)
      .filter((field) => field.exists && field.occupants.length === 0)
      .map((field) => field.coord),
    `${seed}:special-fields`
  );
  const placements: Array<{ type: MagicArenaSpecialFieldType; count: number }> = [
    { type: "explosive", count: SPECIAL_EXPLOSIVE_COUNT_RADIUS_5 },
    { type: "cracked", count: SPECIAL_CRACKED_COUNT_RADIUS_5 }
  ];
  let cursor = 0;

  for (const placement of placements) {
    for (let index = 0; index < placement.count; index += 1) {
      const coord = candidates[cursor];
      cursor += 1;

      if (!coord) {
        return;
      }

      const field = board.fieldsByCoord[axialKey(coord)];

      if (field?.exists) {
        field.specialType = placement.type;
      }
    }
  }
}
