import type { ArenaSurvivorState } from "../protocol.js";

/**
 * Smooth motion between server states.
 *
 * The server sends the host a state every other tick (~31 Hz) while the screen
 * draws at 60 Hz or more. Drawing only what arrived made everything move in
 * visible steps, and a step is also up to one state interval of extra delay
 * before a stick movement shows up.
 *
 * Every frame this moves players, enemies and projectiles forward along the
 * velocity the server reported (dead reckoning), capped so a late or dropped
 * packet cannot send anything flying off. When the next state disagrees with
 * where something was drawn (a player let go of the stick, an enemy turned),
 * the difference is faded out over a few frames instead of snapping.
 *
 * Nothing here feeds back into the game: the server stays authoritative and
 * the base state is never mutated. The renderer draws a private copy.
 */

/** Longest look-ahead. Two missed states at 24 ms, plus slack. */
const MAX_EXTRAPOLATION_MS = 70;
/** Time constant for fading out a correction, in ms. */
const CORRECTION_FADE_MS = 60;
/** A jump larger than this is a respawn or teleport and snaps. */
const MAX_CORRECTION_DISTANCE = 90;

interface Offset {
  x: number;
  y: number;
}

interface PickupSample {
  x: number;
  y: number;
  elapsedMs: number;
  vx: number;
  vy: number;
}

export interface ArenaSurvivorMotion {
  /** The base state the render copy was made from. */
  readonly base: ArenaSurvivorState | null;
  /** Copy the renderer draws; positions are rewritten every frame. */
  readonly view: ArenaSurvivorState | null;
  /** Whether the wave is running, i.e. things move between states. */
  readonly live: boolean;
  /** Adopts a new server state. `live` enables extrapolation (the wave is running). */
  accept(state: ArenaSurvivorState, live: boolean, nowMs: number): void;
  /** Rewrites the view for the current frame. Returns false when there is nothing to draw. */
  advance(nowMs: number): boolean;
  clear(): void;
}

function cloneForView(state: ArenaSurvivorState): ArenaSurvivorState {
  return {
    ...state,
    players: state.players.map((player) => ({ ...player })),
    enemies: state.enemies.map((enemy) => ({ ...enemy })),
    projectiles: state.projectiles.map((projectile) => ({ ...projectile })),
    pickups: state.pickups.map((pickup) => ({ ...pickup }))
  };
}

function clamp(value: number, min: number, max: number): number {
  return value < min ? min : value > max ? max : value;
}

export function createArenaSurvivorMotion(): ArenaSurvivorMotion {
  let base: ArenaSurvivorState | null = null;
  let view: ArenaSurvivorState | null = null;
  let live = false;
  let acceptedAtMs = 0;
  const playerOffsets = new Map<string, Offset>();
  const enemyOffsets = new Map<string, Offset>();
  const pickupSamples = new Map<string, PickupSample>();

  /** Offset between where an entity was last drawn and where the new state puts it. */
  function collectOffsets<T extends { x: number; y: number }>(
    previous: readonly T[] | undefined,
    next: readonly T[],
    idOf: (entry: T) => string,
    target: Map<string, Offset>
  ): void {
    const drawn = new Map<string, T>();

    for (const entry of previous ?? []) {
      drawn.set(idOf(entry), entry);
    }

    target.clear();

    for (const entry of next) {
      const before = drawn.get(idOf(entry));

      if (!before) {
        continue;
      }

      const offsetX = before.x - entry.x;
      const offsetY = before.y - entry.y;

      if (Math.abs(offsetX) + Math.abs(offsetY) < 0.01) {
        continue;
      }

      if (Math.hypot(offsetX, offsetY) > MAX_CORRECTION_DISTANCE) {
        continue;
      }

      target.set(idOf(entry), { x: offsetX, y: offsetY });
    }
  }

  /**
   * Pickups carry no velocity, but the magnet pulls them towards players at
   * up to 900 units/s. Their speed is estimated from the last two states.
   */
  function samplePickups(state: ArenaSurvivorState): void {
    const seen = new Set<string>();

    for (const pickup of state.pickups) {
      seen.add(pickup.id);
      const previous = pickupSamples.get(pickup.id);
      const elapsedDeltaMs = previous ? state.elapsedMs - previous.elapsedMs : 0;
      const vx = previous && elapsedDeltaMs > 0 ? ((pickup.x - previous.x) / elapsedDeltaMs) * 1000 : 0;
      const vy = previous && elapsedDeltaMs > 0 ? ((pickup.y - previous.y) / elapsedDeltaMs) * 1000 : 0;
      pickupSamples.set(pickup.id, {
        x: pickup.x,
        y: pickup.y,
        elapsedMs: state.elapsedMs,
        vx: elapsedDeltaMs > 0 ? vx : previous?.vx ?? 0,
        vy: elapsedDeltaMs > 0 ? vy : previous?.vy ?? 0
      });
    }

    for (const id of pickupSamples.keys()) {
      if (!seen.has(id)) {
        pickupSamples.delete(id);
      }
    }
  }

  /** Writes the positions for `nowMs` into the view; see `advance`. */
  function advanceView(nowMs: number): boolean {
    if (!base || !view) {
      return false;
    }

    const sinceAcceptMs = Math.max(0, nowMs - acceptedAtMs);
    const aheadMs = live ? Math.min(MAX_EXTRAPOLATION_MS, sinceAcceptMs) : 0;
    const aheadSeconds = aheadMs / 1000;
    const correction = Math.exp(-sinceAcceptMs / CORRECTION_FADE_MS);
    const width = base.arenaWidth;
    const height = base.arenaHeight;

    view.elapsedMs = base.elapsedMs + aheadMs;
    view.remainingMs = Math.max(0, base.remainingMs - aheadMs);

    for (let index = 0; index < base.players.length; index += 1) {
      const source = base.players[index];
      const target = view.players[index];
      const offset = playerOffsets.get(source.playerId);
      const moving = source.alive ? 1 : 0;
      target.x = clamp(
        source.x + source.vx * aheadSeconds * moving + (offset ? offset.x * correction : 0),
        source.radius,
        width - source.radius
      );
      target.y = clamp(
        source.y + source.vy * aheadSeconds * moving + (offset ? offset.y * correction : 0),
        source.radius,
        height - source.radius
      );
    }

    for (let index = 0; index < base.enemies.length; index += 1) {
      const source = base.enemies[index];
      const target = view.enemies[index];
      const offset = enemyOffsets.get(source.id);
      target.x = source.x + source.vx * aheadSeconds + (offset ? offset.x * correction : 0);
      target.y = source.y + source.vy * aheadSeconds + (offset ? offset.y * correction : 0);
    }

    for (let index = 0; index < base.projectiles.length; index += 1) {
      const source = base.projectiles[index];
      const target = view.projectiles[index];
      target.x = source.x + source.vx * aheadSeconds;
      target.y = source.y + source.vy * aheadSeconds;
    }

    for (let index = 0; index < base.pickups.length; index += 1) {
      const source = base.pickups[index];
      const target = view.pickups[index];
      const sample = pickupSamples.get(source.id);
      target.x = source.x + (sample?.vx ?? 0) * aheadSeconds;
      target.y = source.y + (sample?.vy ?? 0) * aheadSeconds;
      target.ageMs = source.ageMs + aheadMs;
    }

    return true;
  }

  return {
    get base() {
      return base;
    },
    get view() {
      return view;
    },
    get live() {
      return live;
    },
    accept(state, isLive, nowMs) {
      if (state === base) {
        live = isLive;
        return;
      }

      const sameRun = Boolean(base && view && live && isLive);

      if (sameRun) {
        // Where the old state puts things *now* - not where the last frame
        // drew them - is what the new state has to blend in from.
        advanceView(nowMs);
        collectOffsets(view?.players, state.players, (player) => player.playerId, playerOffsets);
        collectOffsets(view?.enemies, state.enemies, (enemy) => enemy.id, enemyOffsets);
      } else {
        playerOffsets.clear();
        enemyOffsets.clear();
        pickupSamples.clear();
      }

      samplePickups(state);
      base = state;
      view = cloneForView(state);
      live = isLive;
      acceptedAtMs = nowMs;
    },
    advance(nowMs) {
      return advanceView(nowMs);
    },
    clear() {
      base = null;
      view = null;
      live = false;
      playerOffsets.clear();
      enemyOffsets.clear();
      pickupSamples.clear();
    }
  };
}
