import type { BotAdapter, BotThinkContext } from "../types.js";
import { clamp, createBotRandom } from "../random.js";

interface Mercenary {
  id: string;
  x: number;
  y: number;
  radius: number;
  hp: number;
  alive: boolean;
  aimAngleRad: number;
  ammo: Record<string, number>;
}

interface ChaosPlayer {
  playerId: string;
  mercenaries: Mercenary[];
}

interface ChaosWeapon {
  id: string;
  projectileSpeed: number;
  gravityScale: number;
  windScale: number;
  fireMode: "charged" | "instant";
  blastRadius: number;
}

interface ChaosTerrain {
  width: number;
  height: number;
  waterlineY: number;
  samples: number[];
  sampleSpacing: number;
}

interface ChaosState {
  phase: string;
  terrain: ChaosTerrain;
  players: ChaosPlayer[];
  turn: {
    turnNumber: number;
    currentPlayerId: string;
    activeMercenaryId: string;
    currentWeaponId: string;
    turnEndsAt: number;
    prepEndsAt: number;
    hasFired: boolean;
    resolvingShot: boolean;
    chargeStartedAt: number | null;
  };
  weapons: ChaosWeapon[];
  wind: { strength: number; direction: -1 | 1 };
}

interface Shot {
  angle: number;
  charge: number;
  distance: number;
}

const GRAVITY = 880;
const CHARGE_WINDOW_MS = 1750;
const LAUNCH_HEIGHT = 14;

function groundAt(terrain: ChaosTerrain, x: number): number {
  if (!terrain.samples.length) {
    return terrain.height;
  }
  const normalized = clamp(x / Math.max(1, terrain.width), 0, 1);
  const index = Math.min(
    terrain.samples.length - 1,
    Math.max(0, Math.round(normalized * (terrain.samples.length - 1)))
  );
  return terrain.samples[index];
}

function simulate(
  state: ChaosState,
  mercenary: Mercenary,
  target: Mercenary,
  weapon: ChaosWeapon,
  angle: number,
  charge: number
): number {
  const speed = weapon.projectileSpeed * charge;
  let x = mercenary.x;
  let y = mercenary.y - LAUNCH_HEIGHT;
  let vx = Math.cos(angle) * speed;
  let vy = Math.sin(angle) * speed;
  const wind = state.wind.direction * state.wind.strength * 78 * weapon.windScale;
  let closest = Infinity;

  for (let step = 0; step < 320; step += 1) {
    const dt = 0.016;
    vx += wind * dt;
    vy += GRAVITY * weapon.gravityScale * dt;
    x += vx * dt;
    y += vy * dt;

    const distance = Math.hypot(x - target.x, y - target.y);
    if (distance < closest) {
      closest = distance;
    }
    if (distance <= Math.max(18, weapon.blastRadius * 0.55)) {
      return distance - 500; // A hit is always better than a near miss.
    }
    if (x < 0 || x > state.terrain.width || y > state.terrain.height) {
      break;
    }
    if (y >= groundAt(state.terrain, x) || y >= state.terrain.waterlineY) {
      break;
    }
  }

  return closest;
}

function solveShot(
  state: ChaosState,
  mercenary: Mercenary,
  target: Mercenary,
  weapon: ChaosWeapon,
  difficulty: string,
  random: () => number
): Shot | null {
  if (weapon.projectileSpeed <= 0) {
    return null;
  }

  const expert = difficulty === "expert";
  const angleStep = expert ? 0.02 : difficulty === "hard" ? 0.035 : 0.05;
  const chargeStep = expert ? 0.05 : 0.1;
  let best: Shot | null = null;

  for (let angle = -2.85; angle <= -0.2; angle += angleStep) {
    for (let charge = 0.3; charge <= 1.0001; charge += chargeStep) {
      const distance = simulate(state, mercenary, target, weapon, angle, charge);
      if (!best || distance < best.distance) {
        best = { angle, charge: Math.min(1, charge), distance };
      }
    }
  }

  if (!best) {
    return null;
  }

  // Human-like error at lower difficulties, never on expert.
  if (difficulty !== "expert") {
    const error = difficulty === "hard" ? 0.012 : 0.035;
    best.angle += (random() - 0.5) * error * 2;
  }

  return best;
}

function pickTarget(state: ChaosState, playerId: string): Mercenary | null {
  const enemies: Mercenary[] = [];
  for (const player of state.players) {
    if (player.playerId === playerId) {
      continue;
    }
    for (const mercenary of player.mercenaries) {
      if (mercenary.alive) {
        enemies.push(mercenary);
      }
    }
  }

  const active = state.players.find((player) => player.playerId === playerId)
    ?.mercenaries.find((mercenary) => mercenary.id === state.turn.activeMercenaryId);

  if (!active) {
    return enemies[0] ?? null;
  }

  return enemies.sort((left, right) => {
    const leftScore = Math.hypot(left.x - active.x, left.y - active.y) + (left.hp / 100) * 60;
    const rightScore = Math.hypot(right.x - active.x, right.y - active.y) + (right.hp / 100) * 60;
    return leftScore - rightScore;
  })[0] ?? null;
}

export const chaosKommandoBot: BotAdapter = {
  gameId: "chaos-kommando",
  thinkEveryMs: 40,
  think(context: BotThinkContext) {
    const state = context.state as ChaosState | null;
    if (!state || state.phase !== "playing" || state.turn.currentPlayerId !== context.bot.id) {
      return;
    }
    if (state.turn.prepEndsAt > context.now || state.turn.resolvingShot) {
      return;
    }

    const player = state.players.find((entry) => entry.playerId === context.bot.id);
    const mercenary = player?.mercenaries.find((entry) => entry.id === state.turn.activeMercenaryId);
    if (!mercenary || !mercenary.alive) {
      return;
    }

    const weapon = state.weapons.find((entry) => entry.id === state.turn.currentWeaponId);
    if (!weapon) {
      return;
    }

    if (state.turn.hasFired) {
      const endAt = Number(context.memory.get("endTurnAt") ?? 0);
      if (endAt && context.now >= endAt) {
        context.memory.delete("endTurnAt");
        context.input({ type: "end-turn" });
      }
      return;
    }

    const random = createBotRandom(
      `${context.room.code}:${context.bot.id}:chaos:${state.turn.turnNumber}`
    );
    const turnKey = `${state.turn.turnNumber}:${state.turn.activeMercenaryId}`;

    if (context.memory.get("turnKey") !== turnKey) {
      context.memory.set("turnKey", turnKey);
      context.memory.delete("shot");
      context.memory.delete("fireAt");
      context.memory.delete("endTurnAt");
    }

    let shot = context.memory.get("shot") as Shot | null | undefined;
    if (!shot) {
      const target = pickTarget(state, context.bot.id);
      shot = target && weapon.fireMode === "charged"
        ? solveShot(state, mercenary, target, weapon, context.difficulty, random)
        : null;
      context.memory.set("shot", shot);
      if (!shot && weapon.fireMode === "charged") {
        context.input({ type: "end-turn" });
        return;
      }
    }

    if (weapon.fireMode === "instant") {
      const target = pickTarget(state, context.bot.id);
      if (target) {
        const angle = Math.atan2(target.y - (mercenary.y - LAUNCH_HEIGHT), target.x - mercenary.x);
        context.input({ type: "aim", aimX: Math.cos(angle), aimY: Math.sin(angle) });
        context.input({ type: "fire:start" });
        context.memory.set("endTurnAt", context.now + 1200);
      }
      return;
    }

    if (!shot) {
      return;
    }

    const fireAt = Number(context.memory.get("fireAt") ?? 0);
    if (!fireAt) {
      context.input({ type: "aim", aimX: Math.cos(shot.angle), aimY: Math.sin(shot.angle) });
      context.input({ type: "fire:start" });
      context.memory.set("fireAt", context.now + shot.charge * CHARGE_WINDOW_MS - 40);
      return;
    }

    if (context.now >= fireAt) {
      context.input({ type: "fire:release" });
      context.memory.delete("fireAt");
      context.memory.set("endTurnAt", context.now + 2600);
    }
  }
};
