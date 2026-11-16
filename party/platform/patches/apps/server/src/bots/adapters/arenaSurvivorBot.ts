import type { BotAdapter, BotThinkContext } from "../types.js";
import { clamp, createBotRandom, normalize } from "../random.js";

interface ShopOffer {
  id: string;
  kind: "item" | "weapon" | "upgrade";
  cost: number;
  affordable: boolean;
  purchased: boolean;
  targetLevel: number;
  targetWeaponInstanceId?: string;
}

interface ArenaPlayer {
  playerId: string;
  x: number;
  y: number;
  hp: number;
  maxHp: number;
  alive: boolean;
  materials: number;
  pendingLevelUpChoices: number;
  shop: { offers: ShopOffer[] };
  levelBonusModifiers?: unknown[];
}

interface ArenaEnemy {
  id: string;
  x: number;
  y: number;
  radius: number;
  hp: number;
  contactDamage: number;
  role: string;
}

interface ArenaProjectile {
  id: string;
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
}

interface ArenaPickup {
  id: string;
  kind: "material" | "health";
  x: number;
  y: number;
  value: number;
}

interface ArenaSurvival {
  pause: "level_up" | "shop" | "forge" | "victory" | "chest" | null;
  chestReward?: { id: string; playerId: string; core: boolean; salvageGold: number; offer?: { targetWeaponInstanceId: string; targetLevel: number } };
  readyPlayerIds: string[];
  participantIds: string[];
}

interface ArenaState {
  phase: string;
  arenaWidth: number;
  arenaHeight: number;
  players: ArenaPlayer[];
  enemies: ArenaEnemy[];
  projectiles: ArenaProjectile[];
  pickups: ArenaPickup[];
  survival?: ArenaSurvival;
}

function buyOffer(offers: ShopOffer[]): ShopOffer | undefined {
  const priority: Record<ShopOffer["kind"], number> = { upgrade: 3, weapon: 2, item: 1 };
  return [...offers]
    .filter((offer) => !offer.purchased && offer.affordable)
    .sort((left, right) => priority[right.kind] - priority[left.kind] || left.cost - right.cost)[0];
}

function movementFor(
  state: ArenaState,
  bot: ArenaPlayer,
  difficulty: string,
  random: () => number
): { x: number; y: number } {
  let pushX = 0;
  let pushY = 0;
  const edge = 180;

  for (const enemy of state.enemies ?? []) {
    if (enemy.hp <= 0) {
      continue;
    }
    const dx = bot.x - enemy.x;
    const dy = bot.y - enemy.y;
    const distance = Math.max(1, Math.hypot(dx, dy));
    const danger = enemy.role === "boss" ? 2.2 : 1;
    if (distance < 340) {
      const weight = (danger * (340 - distance) * (340 - distance)) / (distance * 340);
      pushX += (dx / distance) * weight;
      pushY += (dy / distance) * weight;
    }
  }

  for (const projectile of state.projectiles ?? []) {
    const dx = bot.x - projectile.x;
    const dy = bot.y - projectile.y;
    const distance = Math.max(1, Math.hypot(dx, dy));
    if (distance < 210) {
      // Step sideways from the projectile's line of travel.
      const speed = Math.max(1, Math.hypot(projectile.vx, projectile.vy));
      const sideX = -projectile.vy / speed;
      const sideY = projectile.vx / speed;
      const side = sideX * dx + sideY * dy >= 0 ? 1 : -1;
      const weight = (210 - distance) / 210;
      pushX += sideX * side * weight * 2.6;
      pushY += sideY * side * weight * 2.6;
    }
  }

  const healthRatio = bot.maxHp > 0 ? bot.hp / bot.maxHp : 1;
  for (const pickup of state.pickups ?? []) {
    const dx = pickup.x - bot.x;
    const dy = pickup.y - bot.y;
    const distance = Math.max(1, Math.hypot(dx, dy));
    const wanted = pickup.kind === "health" ? healthRatio < 0.65 : true;
    if (wanted && distance < 520) {
      const weight = (pickup.kind === "health" ? 1.4 : 0.8) * (1 - distance / 520);
      pushX += (dx / distance) * weight;
      pushY += (dy / distance) * weight;
    }
  }

  // Do not hug a wall: steer back to the middle when close to an edge.
  if (bot.x < edge) pushX += (edge - bot.x) / edge;
  if (bot.x > state.arenaWidth - edge) pushX -= (bot.x - (state.arenaWidth - edge)) / edge;
  if (bot.y < edge) pushY += (edge - bot.y) / edge;
  if (bot.y > state.arenaHeight - edge) pushY -= (bot.y - (state.arenaHeight - edge)) / edge;

  const direction = normalize(pushX, pushY);
  const noise = difficulty === "expert" ? 0 : difficulty === "hard" ? 0.08 : 0.2;
  return {
    x: clamp(direction.x + (random() - 0.5) * noise, -1, 1),
    y: clamp(direction.y + (random() - 0.5) * noise, -1, 1)
  };
}

export const arenaSurvivorBot: BotAdapter = {
  gameId: "arena-survivor",
  thinkEveryMs: 85,
  think(context: BotThinkContext) {
    const state = context.state as ArenaState | null;
    if (!state || (state.phase !== "playing" && state.phase !== "locked" && state.phase !== "result")) {
      return;
    }

    const bot = state.players?.find((player) => player.playerId === context.bot.id);
    if (!bot) {
      return;
    }

    const random = createBotRandom(`${context.room.code}:${context.bot.id}:arena:${context.memory.get("round") ?? ""}`);
    const survival = state.survival;

    if (survival?.pause) {
      if (survival.pause === "level_up") {
        const offer = buyOffer(bot.shop?.offers ?? []);
        if (offer) {
          context.input({ type: "shop:buy", offerId: offer.id });
        }
        return;
      }

      if (survival.pause === "shop") {
        const offer = buyOffer(bot.shop?.offers ?? []);
        if (offer && Number(context.memory.get("shopBuys") ?? 0) < 2) {
          context.memory.set("shopBuys", Number(context.memory.get("shopBuys") ?? 0) + 1);
          context.input({ type: "shop:buy", offerId: offer.id });
          return;
        }
        context.memory.set("shopBuys", 0);
        context.input({ type: "survival:ready" });
        return;
      }

      if (survival.pause === "forge") {
        const weapon = (bot as unknown as { loadout?: { weapons?: Array<{ weaponInstanceId: string; level: number; evolved: boolean }> } })
          .loadout?.weapons?.find((entry) => entry.level === 4 && !entry.evolved);
        if (weapon) {
          context.input({ type: "survival:evolve", weaponInstanceId: weapon.weaponInstanceId });
        }
        context.input({ type: "survival:ready" });
        return;
      }

      if (survival.pause === "chest") {
        const reward = survival.chestReward;
        if (reward && reward.playerId === bot.playerId) {
          context.input({
            type: "shop:buy",
            offerId: `${reward.id}:${reward.offer ? "take" : "salvage"}`
          });
        }
        return;
      }

      if (survival.pause === "victory") {
        context.input({ type: "survival:endless", continueRun: true });
        return;
      }
    }

    if (state.phase === "locked" || state.phase === "result") {
      const offer = buyOffer(bot.shop?.offers ?? []);
      if (offer) {
        context.input({ type: "shop:buy", offerId: offer.id });
      }
      return;
    }

    if (!bot.alive) {
      return;
    }

    const direction = movementFor(state, bot, context.difficulty, random);
    context.input({ type: "move", moveX: direction.x, moveY: direction.y });
  }
};
