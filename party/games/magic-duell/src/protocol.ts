import type { PlayerInput } from "@open-party-lab/game-core";

export type MageDuelSpellId =
  | "fire_bolt"
  | "flame_drop"
  | "ember_orb"
  | "meteor_square"
  | "burn_spiral"
  | "scarlet_lightning"
  | "rift_cleave"
  | "shield"
  | "frost_ward"
  | "mirror_square"
  | "mana_triplet"
  | "null_cross"
  | "tide_barrier"
  | "heal"
  | "regeneration"
  | "thorn_lance"
  | "vine_wall"
  | "clover_bloom"
  | "poison_spiral"
  | "root_snare"
  | "verdant_chevron"
  | "fire_wall"
  | "arcane_bolt"
  | "mana_surge";
export type MageDuelGestureColor = "red" | "blue" | "green";
export type MageDuelGestureShape =
  | "vertical_up"
  | "vertical_down"
  | "triangle"
  | "circle"
  | "square"
  | "zigzag"
  | "spiral"
  | "triple_circle"
  | "triangle_slash"
  | "lightning"
  | "chevron"
  | "cross"
  | "wave";

export interface MageDuelGesturePoint {
  x: number;
  y: number;
  t?: number;
}

export interface MageDuelGestureInput extends PlayerInput {
  type: "spell:gesture";
  spellId: MageDuelSpellId;
  color: MageDuelGestureColor;
  points: MageDuelGesturePoint[];
}

export type MageDuelInput = MageDuelGestureInput;

export interface MageDuelSpellDefinition {
  id: MageDuelSpellId;
  displayName: string;
  description: string;
  color: MageDuelGestureColor;
  shape: MageDuelGestureShape;
  manaCost: number;
  cooldownMs: number;
  castMs: number;
  damage?: number;
  heal?: number;
  healOverTime?: number;
  healDurationMs?: number;
  manaRestore?: number;
  manaBurn?: number;
  lifeSteal?: number;
  damageOverTime?: number;
  damageDurationMs?: number;
  shieldHp?: number;
  shieldDurationMs?: number;
  shieldPiercePct?: number;
  fireWallDurationMs?: number;
  fireBonusDamage?: number;
  cooldownReductionMs?: number;
  cleanse?: boolean;
  manaRegenBoostPerSecond?: number;
  manaRegenBoostDurationMs?: number;
  projectileTravelMs?: number;
}

export interface MageDuelGestureRecognition {
  spellId: MageDuelSpellId | null;
  shape: MageDuelGestureShape | null;
  confidence: number;
  reason: string;
  simplifiedPoints: MageDuelGesturePoint[];
}

export interface MageDuelActiveCastState {
  id: string;
  spellId: MageDuelSpellId;
  startedAt: number;
  resolvesAt: number;
  targetPlayerId: string | null;
  quality: number;
}

export interface MageDuelLastGestureState {
  color: MageDuelGestureColor;
  spellId: MageDuelSpellId | null;
  confidence: number;
  reason: string;
  at: number;
}

export interface MageDuelRegenerationState {
  id: string;
  startedAt: number;
  endsAt: number;
  totalHeal: number;
  appliedHeal: number;
}

export interface MageDuelDamageOverTimeState {
  id: string;
  spellId: MageDuelSpellId;
  startedAt: number;
  endsAt: number;
  totalDamage: number;
  appliedDamage: number;
}

export interface MageDuelDuelistState {
  playerId: string;
  name: string;
  color: string;
  slot: 0 | 1;
  loadoutSpellIds: MageDuelSpellId[];
  hp: number;
  maxHp: number;
  mana: number;
  maxMana: number;
  manaRegenPerSecond: number;
  shieldHp: number;
  shieldEndsAt: number | null;
  fireWallEndsAt: number | null;
  fireWallBonusDamage: number;
  regeneration: MageDuelRegenerationState | null;
  damageOverTime: MageDuelDamageOverTimeState[];
  manaRegenBoostEndsAt: number | null;
  manaRegenBonusPerSecond: number;
  cooldownReadyAt: Record<MageDuelSpellId, number>;
  activeCast: MageDuelActiveCastState | null;
  lastGesture: MageDuelLastGestureState | null;
}

export type MageDuelEffectKind =
  | "cast"
  | "impact"
  | "shield"
  | "heal"
  | "fire_wall"
  | "fizzle"
  | "mana"
  | "dot"
  | "cleanse"
  | "boost";

export interface MageDuelEffectState {
  id: string;
  kind: MageDuelEffectKind;
  spellId: MageDuelSpellId | null;
  ownerPlayerId: string;
  targetPlayerId: string | null;
  createdAt: number;
  endsAt: number;
}

export interface MageDuelProjectileState {
  id: string;
  spellId: MageDuelSpellId;
  ownerPlayerId: string;
  targetPlayerId: string;
  createdAt: number;
  impactAt: number;
  damage: number;
  quality: number;
  manaBurn?: number;
  lifeSteal?: number;
  damageOverTime?: number;
  damageDurationMs?: number;
  shieldPiercePct?: number;
}

export interface MageDuelPublicState {
  roundEndsAt: number | null;
  duelists: MageDuelDuelistState[];
  spellBook: MageDuelSpellDefinition[];
  projectiles: MageDuelProjectileState[];
  effects: MageDuelEffectState[];
  actionLog: string[];
  winnerPlayerId?: string;
  winnerName?: string;
  isDraw: boolean;
}

export interface MageDuelControllerState extends MageDuelPublicState {
  ownPlayerId: string;
}

export interface MageDuelLobbyState {
  spellCatalog: MageDuelSpellDefinition[];
  loadoutsByPlayerId: Record<string, MageDuelSpellId[]>;
  maxLoadoutSize: number;
  minLoadoutSize: number;
}

export const mageDuelLoadoutSize = 12;
export const mageDuelMinLoadoutSize = 1;

export const mageDuelShapeIconPaths = {
  vertical_up: "/magic-duell/vertical-up.svg",
  vertical_down: "/magic-duell/vertical-down.svg",
  triangle: "/magic-duell/triangle.svg",
  circle: "/magic-duell/circle.svg",
  square: "/magic-duell/square.svg",
  zigzag: "/magic-duell/zigzag.svg",
  spiral: "/magic-duell/spiral.svg",
  triple_circle: "/magic-duell/triple-circle.svg",
  triangle_slash: "/magic-duell/triangle-slash.svg",
  lightning: "/magic-duell/lightning.svg",
  chevron: "/magic-duell/chevron.svg",
  cross: "/magic-duell/cross.svg",
  wave: "/magic-duell/wave.svg"
} as const satisfies Record<MageDuelGestureShape, string>;

export function getMageDuelShapeIconPath(shape: MageDuelGestureShape): string {
  return mageDuelShapeIconPaths[shape];
}

export const mageDuelSpellCatalog = [
  {
    id: "fire_bolt",
    displayName: "Feuerstich",
    description: "Roter Strich von unten nach oben. Schleudert einen direkten Feuerangriff.",
    color: "red",
    shape: "vertical_up",
    manaCost: 20,
    cooldownMs: 4_500,
    castMs: 1_400,
    damage: 16,
    projectileTravelMs: 1_550
  },
  {
    id: "flame_drop",
    displayName: "Flammenfall",
    description: "Roter Strich von oben nach unten. Laesst Feuer auf den Gegner fallen.",
    color: "red",
    shape: "vertical_down",
    manaCost: 24,
    cooldownMs: 7_000,
    castMs: 1_700,
    damage: 19,
    projectileTravelMs: 1_800
  },
  {
    id: "fire_wall",
    displayName: "Feuerwand",
    description: "Rotes Dreieck. Verstaerkt nachfolgende Angriffszauber 10 Sekunden lang.",
    color: "red",
    shape: "triangle",
    manaCost: 55,
    cooldownMs: 60_000,
    castMs: 2_200,
    fireWallDurationMs: 12_000,
    fireBonusDamage: 8
  },
  {
    id: "ember_orb",
    displayName: "Glutkreis",
    description: "Roter Kreis. Schleudert eine langsamere Glutkugel.",
    color: "red",
    shape: "circle",
    manaCost: 28,
    cooldownMs: 9_000,
    castMs: 1_800,
    damage: 22,
    projectileTravelMs: 2_050
  },
  {
    id: "meteor_square",
    displayName: "Magmaquadrat",
    description: "Rotes Viereck. Ein schwerer Einschlag, der auch Mana verbrennt.",
    color: "red",
    shape: "square",
    manaCost: 38,
    cooldownMs: 16_000,
    castMs: 2_100,
    damage: 25,
    manaBurn: 10,
    projectileTravelMs: 2_250
  },
  {
    id: "burn_spiral",
    displayName: "Brandspirale",
    description: "Rote Spirale. Entzuendet den Gegner ueber Zeit.",
    color: "red",
    shape: "spiral",
    manaCost: 42,
    cooldownMs: 21_000,
    castMs: 2_000,
    damage: 6,
    damageOverTime: 24,
    damageDurationMs: 8_000,
    projectileTravelMs: 1_850
  },
  {
    id: "scarlet_lightning",
    displayName: "Scharlachblitz",
    description: "Roter Blitz. Sehr schneller, harter Angriff.",
    color: "red",
    shape: "lightning",
    manaCost: 34,
    cooldownMs: 14_000,
    castMs: 1_300,
    damage: 30,
    projectileTravelMs: 950
  },
  {
    id: "rift_cleave",
    displayName: "Rissklinge",
    description: "Rotes Dreieck mit Strich. Durchschneidet einen Teil des Schilds.",
    color: "red",
    shape: "triangle_slash",
    manaCost: 36,
    cooldownMs: 17_000,
    castMs: 1_700,
    damage: 20,
    shieldPiercePct: 0.65,
    projectileTravelMs: 1_450
  },
  {
    id: "arcane_bolt",
    displayName: "Arkanstich",
    description: "Blauer Strich von unten nach oben. Ein schneller, guenstiger Arkanangriff.",
    color: "blue",
    shape: "vertical_up",
    manaCost: 14,
    cooldownMs: 5_000,
    castMs: 1_000,
    damage: 10,
    projectileTravelMs: 1_250
  },
  {
    id: "frost_ward",
    displayName: "Frostsenke",
    description: "Blauer Strich von oben nach unten. Erzeugt einen kurzen Schutz.",
    color: "blue",
    shape: "vertical_down",
    manaCost: 22,
    cooldownMs: 9_500,
    castMs: 1_100,
    shieldHp: 15,
    shieldDurationMs: 3_200
  },
  {
    id: "shield",
    displayName: "Dreiecksschild",
    description: "Blaues Dreieck. Erzeugt eine Schutzblase um deinen Charakter.",
    color: "blue",
    shape: "triangle",
    manaCost: 35,
    cooldownMs: 11_000,
    castMs: 900,
    shieldHp: 24,
    shieldDurationMs: 4_500
  },
  {
    id: "mana_surge",
    displayName: "Manakreis",
    description: "Blauer Kreis. Laedt Mana wieder auf.",
    color: "blue",
    shape: "circle",
    manaCost: 5,
    cooldownMs: 26_000,
    castMs: 1_800,
    manaRestore: 34
  },
  {
    id: "mirror_square",
    displayName: "Spiegelquadrat",
    description: "Blaues Viereck. Erzeugt einen stabilen, laengeren Schild.",
    color: "blue",
    shape: "square",
    manaCost: 42,
    cooldownMs: 19_000,
    castMs: 1_800,
    shieldHp: 34,
    shieldDurationMs: 5_800
  },
  {
    id: "mana_triplet",
    displayName: "Manatrio",
    description: "Drei blaue Kreise. Stellt Mana wieder her und drueckt laufende Abklingzeiten.",
    color: "blue",
    shape: "triple_circle",
    manaCost: 8,
    cooldownMs: 34_000,
    castMs: 2_200,
    manaRestore: 45,
    cooldownReductionMs: 2_500
  },
  {
    id: "null_cross",
    displayName: "Nullkreuz",
    description: "Blaues Kreuz. Trifft kaum, raubt dem Gegner aber Mana.",
    color: "blue",
    shape: "cross",
    manaCost: 26,
    cooldownMs: 18_000,
    castMs: 1_600,
    damage: 4,
    manaBurn: 32,
    projectileTravelMs: 1_300
  },
  {
    id: "tide_barrier",
    displayName: "Wellenbarriere",
    description: "Blaue Welle. Reinigt Schaden ueber Zeit und schuetzt kurz.",
    color: "blue",
    shape: "wave",
    manaCost: 32,
    cooldownMs: 24_000,
    castMs: 1_700,
    shieldHp: 16,
    shieldDurationMs: 4_200,
    cleanse: true
  },
  {
    id: "thorn_lance",
    displayName: "Dornenstich",
    description: "Gruener Strich von unten nach oben. Ein leichter Naturangriff.",
    color: "green",
    shape: "vertical_up",
    manaCost: 16,
    cooldownMs: 6_500,
    castMs: 1_200,
    damage: 11,
    projectileTravelMs: 1_450
  },
  {
    id: "heal",
    displayName: "Gruener Rueckfluss",
    description: "Gruener Strich von oben nach unten. Stellt sofort Leben wieder her.",
    color: "green",
    shape: "vertical_down",
    manaCost: 30,
    cooldownMs: 13_000,
    castMs: 1_500,
    heal: 20
  },
  {
    id: "vine_wall",
    displayName: "Rankenwall",
    description: "Gruenes Dreieck. Ein schwacher, laengerer Schutz.",
    color: "green",
    shape: "triangle",
    manaCost: 27,
    cooldownMs: 14_000,
    castMs: 1_400,
    shieldHp: 18,
    shieldDurationMs: 6_500
  },
  {
    id: "clover_bloom",
    displayName: "Kleebluete",
    description: "Drei gruene Kreise. Heilt sofort und dann weiter ueber Zeit.",
    color: "green",
    shape: "triple_circle",
    manaCost: 46,
    cooldownMs: 32_000,
    castMs: 2_100,
    heal: 10,
    healOverTime: 24,
    healDurationMs: 14_000
  },
  {
    id: "poison_spiral",
    displayName: "Giftspirale",
    description: "Gruene Spirale. Laesst den Gegner ueber Zeit Leben verlieren.",
    color: "green",
    shape: "spiral",
    manaCost: 38,
    cooldownMs: 23_000,
    castMs: 1_900,
    damage: 3,
    damageOverTime: 26,
    damageDurationMs: 10_000,
    projectileTravelMs: 1_750
  },
  {
    id: "root_snare",
    displayName: "Wurzelzacke",
    description: "Gruener Zickzack. Trifft leicht und stoert gegnerisches Mana.",
    color: "green",
    shape: "zigzag",
    manaCost: 25,
    cooldownMs: 13_000,
    castMs: 1_400,
    damage: 14,
    manaBurn: 12,
    projectileTravelMs: 1_500
  },
  {
    id: "verdant_chevron",
    displayName: "Naturfokus",
    description: "Gruenes Chevron. Heilt leicht und beschleunigt deine Mana-Regeneration.",
    color: "green",
    shape: "chevron",
    manaCost: 34,
    cooldownMs: 28_000,
    castMs: 1_800,
    heal: 8,
    manaRegenBoostPerSecond: 5,
    manaRegenBoostDurationMs: 12_000
  },
  {
    id: "regeneration",
    displayName: "Lebenskreis",
    description: "Gruener Kreis. Stellt Leben ueber 20 Sekunden wieder her.",
    color: "green",
    shape: "circle",
    manaCost: 45,
    cooldownMs: 30_000,
    castMs: 1_800,
    healOverTime: 20,
    healDurationMs: 20_000
  }
] as const satisfies readonly MageDuelSpellDefinition[];

export const defaultMageDuelLoadoutSpellIds = mageDuelSpellCatalog
  .slice(0, mageDuelLoadoutSize)
  .map((spell) => spell.id);

export function isMageDuelSpellId(value: unknown): value is MageDuelSpellId {
  return typeof value === "string" && mageDuelSpellCatalog.some((spell) => spell.id === value);
}

export function getMageDuelSpellDefinition(spellId: MageDuelSpellId): MageDuelSpellDefinition | undefined {
  return mageDuelSpellCatalog.find((spell) => spell.id === spellId);
}

const spellByGesture: Record<MageDuelGestureColor, Partial<Record<MageDuelGestureShape, MageDuelSpellId>>> = {
  red: {
    vertical_up: "fire_bolt",
    vertical_down: "flame_drop",
    triangle: "fire_wall",
    circle: "ember_orb",
    square: "meteor_square",
    spiral: "burn_spiral",
    lightning: "scarlet_lightning",
    triangle_slash: "rift_cleave"
  },
  blue: {
    vertical_up: "arcane_bolt",
    vertical_down: "frost_ward",
    triangle: "shield",
    circle: "mana_surge",
    square: "mirror_square",
    triple_circle: "mana_triplet",
    cross: "null_cross",
    wave: "tide_barrier"
  },
  green: {
    vertical_up: "thorn_lance",
    vertical_down: "heal",
    triangle: "vine_wall",
    circle: "regeneration",
    triple_circle: "clover_bloom",
    spiral: "poison_spiral",
    zigzag: "root_snare",
    chevron: "verdant_chevron"
  }
};

const gestureEpsilon = 0.045;

function clamp01(value: number): number {
  return Math.max(0, Math.min(1, value));
}

function isFinitePoint(point: MageDuelGesturePoint): boolean {
  return Number.isFinite(point.x) && Number.isFinite(point.y);
}

function distance(a: MageDuelGesturePoint, b: MageDuelGesturePoint): number {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

function normalizePoints(points: MageDuelGesturePoint[]): MageDuelGesturePoint[] {
  const normalized: MageDuelGesturePoint[] = [];

  for (const point of points) {
    if (!isFinitePoint(point)) {
      continue;
    }

    const nextPoint = {
      x: clamp01(point.x),
      y: clamp01(point.y),
      t: Number.isFinite(point.t) ? point.t : undefined
    };
    const previous = normalized[normalized.length - 1];

    if (previous && distance(previous, nextPoint) < 0.008) {
      continue;
    }

    normalized.push(nextPoint);
  }

  return normalized;
}

function pathLength(points: MageDuelGesturePoint[]): number {
  let length = 0;

  for (let index = 1; index < points.length; index += 1) {
    length += distance(points[index - 1], points[index]);
  }

  return length;
}

function perpendicularDistance(
  point: MageDuelGesturePoint,
  start: MageDuelGesturePoint,
  end: MageDuelGesturePoint
): number {
  const dx = end.x - start.x;
  const dy = end.y - start.y;
  const denominator = Math.hypot(dx, dy);

  if (denominator < 0.0001) {
    return distance(point, start);
  }

  return Math.abs(dy * point.x - dx * point.y + end.x * start.y - end.y * start.x) / denominator;
}

function simplifyRdp(points: MageDuelGesturePoint[], epsilon: number): MageDuelGesturePoint[] {
  if (points.length <= 2) {
    return points;
  }

  let maxDistance = 0;
  let splitIndex = 0;
  const start = points[0];
  const end = points[points.length - 1];

  for (let index = 1; index < points.length - 1; index += 1) {
    const pointDistance = perpendicularDistance(points[index], start, end);

    if (pointDistance > maxDistance) {
      maxDistance = pointDistance;
      splitIndex = index;
    }
  }

  if (maxDistance <= epsilon) {
    return [start, end];
  }

  const left = simplifyRdp(points.slice(0, splitIndex + 1), epsilon);
  const right = simplifyRdp(points.slice(splitIndex), epsilon);

  return [...left.slice(0, -1), ...right];
}

function getBounds(points: MageDuelGesturePoint[]): {
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
  width: number;
  height: number;
  diagonal: number;
} {
  let minX = 1;
  let maxX = 0;
  let minY = 1;
  let maxY = 0;

  for (const point of points) {
    minX = Math.min(minX, point.x);
    maxX = Math.max(maxX, point.x);
    minY = Math.min(minY, point.y);
    maxY = Math.max(maxY, point.y);
  }

  const width = maxX - minX;
  const height = maxY - minY;

  return {
    minX,
    maxX,
    minY,
    maxY,
    width,
    height,
    diagonal: Math.hypot(width, height)
  };
}

function polygonArea(points: MageDuelGesturePoint[]): number {
  let area = 0;

  for (let index = 0; index < points.length; index += 1) {
    const current = points[index];
    const next = points[(index + 1) % points.length];
    area += current.x * next.y - next.x * current.y;
  }

  return Math.abs(area) / 2;
}

function removeClosingDuplicate(points: MageDuelGesturePoint[]): MageDuelGesturePoint[] {
  if (points.length < 2) {
    return points;
  }

  const first = points[0];
  const last = points[points.length - 1];

  if (distance(first, last) < 0.075) {
    return points.slice(0, -1);
  }

  return points;
}

function countMeaningfulCorners(points: MageDuelGesturePoint[]): number {
  if (points.length < 3) {
    return 0;
  }

  let corners = 0;

  for (let index = 0; index < points.length; index += 1) {
    const previous = points[(index - 1 + points.length) % points.length];
    const current = points[index];
    const next = points[(index + 1) % points.length];
    const ax = previous.x - current.x;
    const ay = previous.y - current.y;
    const bx = next.x - current.x;
    const by = next.y - current.y;
    const aLength = Math.hypot(ax, ay);
    const bLength = Math.hypot(bx, by);

    if (aLength < 0.04 || bLength < 0.04) {
      continue;
    }

    const dot = (ax * bx + ay * by) / (aLength * bLength);
    const angle = Math.acos(Math.max(-1, Math.min(1, dot)));

    if (angle < 2.45) {
      corners += 1;
    }
  }

  return corners;
}

function normalizedAngleDelta(from: number, to: number): number {
  let delta = to - from;

  while (delta > Math.PI) {
    delta -= Math.PI * 2;
  }

  while (delta < -Math.PI) {
    delta += Math.PI * 2;
  }

  return delta;
}

function countDirectionChanges(values: number[]): number {
  let previousSign = 0;
  let changes = 0;

  for (const value of values) {
    const sign = Math.abs(value) < 0.035 ? 0 : Math.sign(value);

    if (sign === 0) {
      continue;
    }

    if (previousSign !== 0 && sign !== previousSign) {
      changes += 1;
    }

    previousSign = sign;
  }

  return changes;
}

function createShapeRecognition(
  color: MageDuelGestureColor,
  shape: MageDuelGestureShape,
  confidence: number,
  reason: string,
  simplifiedPoints: MageDuelGesturePoint[]
): MageDuelGestureRecognition | null {
  const spellId = spellByGesture[color]?.[shape] ?? null;

  if (!spellId) {
    return null;
  }

  return {
    spellId,
    shape,
    confidence: Math.max(0, Math.min(1, confidence)),
    reason,
    simplifiedPoints
  };
}

function recognizeVerticalLine(
  points: MageDuelGesturePoint[],
  color: MageDuelGestureColor
): MageDuelGestureRecognition | null {
  const first = points[0];
  const last = points[points.length - 1];
  const bounds = getBounds(points);
  const displacementX = last.x - first.x;
  const displacementY = last.y - first.y;
  const displacement = Math.hypot(displacementX, displacementY);
  const length = pathLength(points);
  const straightness = displacement / Math.max(length, 0.0001);
  const verticality = Math.abs(displacementY) / Math.max(Math.abs(displacementX) + Math.abs(displacementY), 0.0001);

  if (bounds.height < 0.34) {
    return null;
  }

  if (straightness < 0.78 || verticality < 0.72) {
    return null;
  }

  const shape: MageDuelGestureShape = displacementY < 0 ? "vertical_up" : "vertical_down";
  const spellId = spellByGesture[color]?.[shape] ?? null;

  if (!spellId) {
    return null;
  }

  const confidence = Math.min(1, Math.max(0.45, straightness * 0.62 + verticality * 0.38));

  return {
    spellId,
    shape,
    confidence,
    reason: "Linienrichtung erkannt",
    simplifiedPoints: [first, last]
  };
}

function recognizeTriangle(
  points: MageDuelGesturePoint[],
  color: MageDuelGestureColor
): MageDuelGestureRecognition | null {
  if (points.length < 4) {
    return null;
  }

  const first = points[0];
  const last = points[points.length - 1];
  const bounds = getBounds(points);
  const length = pathLength(points);
  const closingDistance = distance(first, last);
  const closeEnough = closingDistance <= Math.max(0.13, bounds.diagonal * 0.28);
  const hasSize = bounds.width >= 0.18 && bounds.height >= 0.18;

  if (!closeEnough || !hasSize) {
    return null;
  }

  const closedPoints = closeEnough ? [...points, first] : points;
  const simplifiedClosed = simplifyRdp(closedPoints, gestureEpsilon);
  const simplified = removeClosingDuplicate(simplifiedClosed);
  const corners = countMeaningfulCorners(simplified);
  const area = polygonArea(simplified);
  const bboxArea = Math.max(bounds.width * bounds.height, 0.0001);
  const areaRatio = area / bboxArea;
  const loopiness = length / Math.max(bounds.diagonal, 0.0001);

  if (corners !== 3 || areaRatio < 0.16 || loopiness < 1.9) {
    return null;
  }

  const closureScore = 1 - Math.min(1, closingDistance / Math.max(bounds.diagonal, 0.0001));
  const areaScore = Math.min(1, areaRatio / 0.34);
  const confidence = Math.min(1, Math.max(0.55, 0.5 + closureScore * 0.25 + areaScore * 0.25));

  return createShapeRecognition(color, "triangle", confidence, "Dreieck erkannt", simplified);
}

function recognizeSquare(
  points: MageDuelGesturePoint[],
  color: MageDuelGestureColor
): MageDuelGestureRecognition | null {
  if (points.length < 5) {
    return null;
  }

  const first = points[0];
  const last = points[points.length - 1];
  const bounds = getBounds(points);
  const closingDistance = distance(first, last);
  const closeEnough = closingDistance <= Math.max(0.12, bounds.diagonal * 0.22);
  const aspect = bounds.width / Math.max(bounds.height, 0.0001);

  if (!closeEnough || bounds.width < 0.18 || bounds.height < 0.18 || aspect < 0.62 || aspect > 1.62) {
    return null;
  }

  const closedPoints = [...points, first];
  const simplified = removeClosingDuplicate(simplifyRdp(closedPoints, gestureEpsilon));
  const corners = countMeaningfulCorners(simplified);
  const area = polygonArea(simplified);
  const bboxArea = Math.max(bounds.width * bounds.height, 0.0001);
  const areaRatio = area / bboxArea;

  if (corners < 4 || corners > 5 || areaRatio < 0.46) {
    return null;
  }

  const closureScore = 1 - Math.min(1, closingDistance / Math.max(bounds.diagonal, 0.0001));
  const aspectScore = 1 - Math.min(1, Math.abs(1 - aspect));
  const areaScore = Math.min(1, areaRatio / 0.78);

  return createShapeRecognition(
    color,
    "square",
    Math.max(0.55, closureScore * 0.3 + aspectScore * 0.25 + areaScore * 0.45),
    "Viereck erkannt",
    simplified
  );
}

function recognizeTriangleSlash(
  points: MageDuelGesturePoint[],
  color: MageDuelGestureColor
): MageDuelGestureRecognition | null {
  if (points.length < 6) {
    return null;
  }

  const bounds = getBounds(points);

  if (bounds.width < 0.22 || bounds.height < 0.22) {
    return null;
  }

  const simplified = simplifyRdp(points, gestureEpsilon);
  const triangleEndIndex = simplified.findIndex(
    (point, index) => index >= 3 && distance(point, simplified[0]) <= Math.max(0.13, bounds.diagonal * 0.24)
  );

  if (triangleEndIndex < 3) {
    return null;
  }

  const trianglePoints = removeClosingDuplicate(simplified.slice(0, triangleEndIndex + 1));
  const triangleCorners = countMeaningfulCorners(trianglePoints);
  const area = polygonArea(trianglePoints);
  const bboxArea = Math.max(bounds.width * bounds.height, 0.0001);
  const areaRatio = area / bboxArea;
  const slashPoints = simplified.slice(triangleEndIndex);
  const longDiagonalSegments = slashPoints.slice(1).filter((point, index) => {
    const previous = slashPoints[index];
    return Math.abs(point.x - previous.x) > 0.22 && Math.abs(point.y - previous.y) > 0.12;
  }).length;
  const hasSlash =
    longDiagonalSegments > 0 ||
    slashPoints.slice(1).some((point, index) => {
      const previous = slashPoints[index];
      return Math.abs(point.x - previous.x) > 0.3 && Math.abs(point.y - previous.y) < 0.12;
    });

  if (triangleCorners < 3 || triangleCorners > 4 || areaRatio < 0.08 || areaRatio > 0.62 || !hasSlash) {
    return null;
  }

  return createShapeRecognition(
    color,
    "triangle_slash",
    Math.max(0.55, Math.min(1, 0.5 + triangleCorners * 0.07 + longDiagonalSegments * 0.08)),
    "Dreieck mit Strich erkannt",
    simplified
  );
}

function recognizeCircle(
  points: MageDuelGesturePoint[],
  color: MageDuelGestureColor
): MageDuelGestureRecognition | null {
  const spellId = spellByGesture[color]?.circle ?? null;

  if (!spellId || points.length < 7) {
    return null;
  }

  const first = points[0];
  const last = points[points.length - 1];
  const bounds = getBounds(points);
  const closingDistance = distance(first, last);
  const closeEnough = closingDistance <= Math.max(0.12, bounds.diagonal * 0.25);
  const hasSize = bounds.width >= 0.18 && bounds.height >= 0.18;
  const aspect = bounds.width / Math.max(bounds.height, 0.0001);

  if (!closeEnough || !hasSize || aspect < 0.55 || aspect > 1.8) {
    return null;
  }

  const center = points.reduce(
    (sum, point) => ({ x: sum.x + point.x / points.length, y: sum.y + point.y / points.length }),
    { x: 0, y: 0 }
  );
  const radii = points.map((point) => Math.hypot(point.x - center.x, point.y - center.y));
  const averageRadius = radii.reduce((sum, radius) => sum + radius, 0) / Math.max(1, radii.length);

  if (averageRadius < 0.08) {
    return null;
  }

  const variance =
    radii.reduce((sum, radius) => sum + Math.abs(radius - averageRadius), 0) /
    Math.max(1, radii.length) /
    averageRadius;
  const area = polygonArea(points);
  const bboxArea = Math.max(bounds.width * bounds.height, 0.0001);
  const areaRatio = area / bboxArea;

  if (variance > 0.42 || areaRatio < 0.32) {
    return null;
  }

  const closureScore = 1 - Math.min(1, closingDistance / Math.max(bounds.diagonal, 0.0001));
  const roundnessScore = 1 - Math.min(1, variance / 0.42);
  const areaScore = Math.min(1, areaRatio / 0.66);
  const confidence = Math.min(1, Math.max(0.55, closureScore * 0.3 + roundnessScore * 0.45 + areaScore * 0.25));

  return {
    spellId,
    shape: "circle",
    confidence,
    reason: "Kreis erkannt",
    simplifiedPoints: simplifyRdp(points, gestureEpsilon)
  };
}

function getAngularTravel(points: MageDuelGesturePoint[], center: MageDuelGesturePoint): {
  signed: number;
  absolute: number;
  startRadius: number;
  endRadius: number;
} {
  if (points.length < 2) {
    return { signed: 0, absolute: 0, startRadius: 0, endRadius: 0 };
  }

  let signed = 0;
  let absolute = 0;
  let previousAngle = Math.atan2(points[0].y - center.y, points[0].x - center.x);

  for (let index = 1; index < points.length; index += 1) {
    const angle = Math.atan2(points[index].y - center.y, points[index].x - center.x);
    const delta = normalizedAngleDelta(previousAngle, angle);
    signed += delta;
    absolute += Math.abs(delta);
    previousAngle = angle;
  }

  return {
    signed,
    absolute,
    startRadius: distance(points[0], center),
    endRadius: distance(points[points.length - 1], center)
  };
}

function recognizeSpiral(
  points: MageDuelGesturePoint[],
  color: MageDuelGestureColor
): MageDuelGestureRecognition | null {
  if (points.length < 10) {
    return null;
  }

  const bounds = getBounds(points);

  if (bounds.width < 0.18 || bounds.height < 0.18) {
    return null;
  }

  const center = {
    x: (bounds.minX + bounds.maxX) / 2,
    y: (bounds.minY + bounds.maxY) / 2
  };
  const angular = getAngularTravel(points, center);
  const radiusShift = Math.abs(angular.startRadius - angular.endRadius) / Math.max(bounds.diagonal, 0.0001);
  const loopiness = pathLength(points) / Math.max(bounds.diagonal, 0.0001);

  if (angular.absolute < Math.PI * 2.15 || radiusShift < 0.08 || loopiness < 2.4) {
    return null;
  }

  const turnScore = Math.min(1, angular.absolute / (Math.PI * 4.2));
  const radiusScore = Math.min(1, radiusShift / 0.25);
  const loopScore = Math.min(1, loopiness / 4.4);

  return createShapeRecognition(
    color,
    "spiral",
    Math.max(0.55, turnScore * 0.45 + radiusScore * 0.25 + loopScore * 0.3),
    "Spirale erkannt",
    simplifyRdp(points, gestureEpsilon)
  );
}

function recognizeTripleCircle(
  points: MageDuelGesturePoint[],
  color: MageDuelGestureColor
): MageDuelGestureRecognition | null {
  if (points.length < 16) {
    return null;
  }

  const bounds = getBounds(points);
  const loopiness = pathLength(points) / Math.max(bounds.diagonal, 0.0001);
  const simplified = simplifyRdp(points, 0.03);
  const corners = countMeaningfulCorners(simplified);
  const center = {
    x: (bounds.minX + bounds.maxX) / 2,
    y: (bounds.minY + bounds.maxY) / 2
  };
  const angular = getAngularTravel(points, center);
  const radiusShift = Math.abs(angular.startRadius - angular.endRadius) / Math.max(bounds.diagonal, 0.0001);

  if (
    bounds.width < 0.24 ||
    bounds.height < 0.2 ||
    loopiness < 3.7 ||
    corners < 5 ||
    radiusShift > 0.22
  ) {
    return null;
  }

  const loopScore = Math.min(1, loopiness / 6.4);
  const turnScore = Math.min(1, angular.absolute / (Math.PI * 5.2));
  const cornerScore = Math.min(1, corners / 12);
  const radiusScore = 1 - Math.min(1, radiusShift / 0.22);

  return createShapeRecognition(
    color,
    "triple_circle",
    Math.max(0.55, loopScore * 0.34 + turnScore * 0.26 + cornerScore * 0.2 + radiusScore * 0.2),
    "Drei Kreise erkannt",
    simplified
  );
}

function recognizeZigzag(
  points: MageDuelGesturePoint[],
  color: MageDuelGestureColor
): MageDuelGestureRecognition | null {
  if (points.length < 4) {
    return null;
  }

  const first = points[0];
  const last = points[points.length - 1];
  const bounds = getBounds(points);
  const simplified = simplifyRdp(points, gestureEpsilon);
  const xChanges = countDirectionChanges(simplified.slice(1).map((point, index) => point.x - simplified[index].x));
  const yTravel = Math.abs(last.y - first.y);

  if (
    distance(first, last) < 0.2 ||
    bounds.width < 0.22 ||
    bounds.height < 0.18 ||
    simplified.length < 4 ||
    simplified.length > 8 ||
    xChanges < 2 ||
    yTravel < 0.16
  ) {
    return null;
  }

  return createShapeRecognition(
    color,
    "zigzag",
    Math.max(0.55, Math.min(1, 0.38 + xChanges * 0.16 + Math.min(0.22, bounds.height))),
    "Zickzack erkannt",
    simplified
  );
}

function recognizeLightning(
  points: MageDuelGesturePoint[],
  color: MageDuelGestureColor
): MageDuelGestureRecognition | null {
  if (points.length < 3) {
    return null;
  }

  const bounds = getBounds(points);
  const simplified = simplifyRdp(points, gestureEpsilon);
  const xChanges = countDirectionChanges(simplified.slice(1).map((point, index) => point.x - simplified[index].x));
  const heightDominance = bounds.height / Math.max(bounds.width + bounds.height, 0.0001);

  if (
    bounds.height < 0.32 ||
    bounds.width < 0.1 ||
    simplified.length < 3 ||
    simplified.length > 6 ||
    xChanges < 1 ||
    heightDominance < 0.48
  ) {
    return null;
  }

  return createShapeRecognition(
    color,
    "lightning",
    Math.max(0.55, Math.min(1, 0.45 + xChanges * 0.16 + heightDominance * 0.25)),
    "Blitz erkannt",
    simplified
  );
}

function recognizeChevron(
  points: MageDuelGesturePoint[],
  color: MageDuelGestureColor
): MageDuelGestureRecognition | null {
  if (points.length < 3) {
    return null;
  }

  const simplified = simplifyRdp(points, gestureEpsilon);

  if (simplified.length < 3 || simplified.length > 4) {
    return null;
  }

  const first = simplified[0];
  const middle = simplified[Math.floor(simplified.length / 2)];
  const last = simplified[simplified.length - 1];
  const bounds = getBounds(points);
  const outerLevel = Math.abs(first.y - last.y);
  const depth = Math.min(Math.abs(middle.y - first.y), Math.abs(middle.y - last.y));

  if (bounds.width < 0.24 || bounds.height < 0.18 || outerLevel > 0.18 || depth < 0.16) {
    return null;
  }

  const symmetry = 1 - Math.min(1, Math.abs((middle.x - first.x) - (last.x - middle.x)));

  return createShapeRecognition(
    color,
    "chevron",
    Math.max(0.55, 0.52 + symmetry * 0.24 + Math.min(0.24, depth)),
    "Chevron erkannt",
    simplified
  );
}

function recognizeCross(
  points: MageDuelGesturePoint[],
  color: MageDuelGestureColor
): MageDuelGestureRecognition | null {
  if (points.length < 4) {
    return null;
  }

  const bounds = getBounds(points);
  const simplified = simplifyRdp(points, gestureEpsilon);

  if (bounds.width < 0.24 || bounds.height < 0.24 || simplified.length < 4 || simplified.length > 7) {
    return null;
  }

  let positiveDiagonals = 0;
  let negativeDiagonals = 0;

  for (let index = 1; index < simplified.length; index += 1) {
    const previous = simplified[index - 1];
    const current = simplified[index];
    const dx = current.x - previous.x;
    const dy = current.y - previous.y;

    if (Math.abs(dx) < 0.18 || Math.abs(dy) < 0.18) {
      continue;
    }

    if (dx * dy > 0) {
      positiveDiagonals += 1;
    } else {
      negativeDiagonals += 1;
    }
  }

  if (positiveDiagonals === 0 || negativeDiagonals === 0) {
    return null;
  }

  return createShapeRecognition(
    color,
    "cross",
    Math.max(0.55, Math.min(1, 0.55 + (positiveDiagonals + negativeDiagonals) * 0.08)),
    "Kreuz erkannt",
    simplified
  );
}

function recognizeWave(
  points: MageDuelGesturePoint[],
  color: MageDuelGestureColor
): MageDuelGestureRecognition | null {
  if (points.length < 5) {
    return null;
  }

  const first = points[0];
  const last = points[points.length - 1];
  const bounds = getBounds(points);
  const simplified = simplifyRdp(points, 0.025);
  const yChanges = countDirectionChanges(simplified.slice(1).map((point, index) => point.y - simplified[index].y));
  const horizontalTravel = Math.abs(last.x - first.x);
  const horizontality = horizontalTravel / Math.max(horizontalTravel + Math.abs(last.y - first.y), 0.0001);

  if (
    bounds.width < 0.42 ||
    bounds.height < 0.1 ||
    yChanges < 2 ||
    horizontality < 0.62 ||
    pathLength(points) / Math.max(bounds.width, 0.0001) < 1.15
  ) {
    return null;
  }

  return createShapeRecognition(
    color,
    "wave",
    Math.max(0.55, Math.min(1, 0.42 + yChanges * 0.12 + horizontality * 0.24)),
    "Welle erkannt",
    simplified
  );
}

export function recognizeMageDuelGesture(
  color: MageDuelGestureColor,
  rawPoints: MageDuelGesturePoint[]
): MageDuelGestureRecognition {
  const points = normalizePoints(rawPoints);

  if (points.length < 2) {
    return {
      spellId: null,
      shape: null,
      confidence: 0,
      reason: "Zu wenige Punkte",
      simplifiedPoints: points
    };
  }

  const tripleCircle = recognizeTripleCircle(points, color);

  if (tripleCircle) {
    return tripleCircle;
  }

  const spiral = recognizeSpiral(points, color);

  if (spiral) {
    return spiral;
  }

  const square = recognizeSquare(points, color);

  if (square) {
    return square;
  }

  const triangleSlash = recognizeTriangleSlash(points, color);

  if (triangleSlash) {
    return triangleSlash;
  }

  const lightning = recognizeLightning(points, color);

  if (lightning) {
    return lightning;
  }

  const cross = recognizeCross(points, color);

  if (cross) {
    return cross;
  }

  const chevron = recognizeChevron(points, color);

  if (chevron) {
    return chevron;
  }

  const zigzag = recognizeZigzag(points, color);

  if (zigzag) {
    return zigzag;
  }

  const wave = recognizeWave(points, color);

  if (wave) {
    return wave;
  }

  const triangle = recognizeTriangle(points, color);

  if (triangle) {
    return triangle;
  }

  const circle = recognizeCircle(points, color);

  if (circle) {
    return circle;
  }

  const line = recognizeVerticalLine(points, color);

  if (line) {
    return line;
  }

  return {
    spellId: null,
    shape: null,
    confidence: 0,
    reason: "Kein bekannter Zauber",
    simplifiedPoints: simplifyRdp(points, gestureEpsilon)
  };
}
