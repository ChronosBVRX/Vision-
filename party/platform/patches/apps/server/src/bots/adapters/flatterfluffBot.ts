import type { BotAdapter, BotThinkContext } from "../types.js";
import { clamp, createBotRandom, normalize } from "../random.js";

interface FlatterfluffTarget {
  id: string;
  kind: string;
  x: number;
  y: number;
  radius: number;
  scale: number;
  points: number;
  hp: number;
}

interface FlatterfluffPlayer {
  playerId: string;
  aimX: number;
  aimY: number;
  ammo: number;
  maxAmmo: number;
  reloadEndsAt: number | null;
}

interface FlatterfluffState {
  phase: string;
  serverNow: number;
  targets: FlatterfluffTarget[];
  players: FlatterfluffPlayer[];
}

function targetValue(target: FlatterfluffTarget): number {
  const kindBonus: Record<string, number> = {
    golden: 3,
    jumbo: 2,
    captain: 1.6,
    flier: 1.2,
    scout: 1,
    crate: 0.4
  };
  return (kindBonus[target.kind] ?? 1) * Math.max(1, target.points);
}

export const flatterfluffBot: BotAdapter = {
  gameId: "flatterfluff",
  thinkEveryMs: 55,
  think(context: BotThinkContext) {
    const state = context.state as FlatterfluffState | null;
    if (!state || state.phase !== "playing") {
      return;
    }

    const bot = state.players?.find((player) => player.playerId === context.bot.id);
    if (!bot) {
      return;
    }

    if (bot.ammo <= 0) {
      if (bot.reloadEndsAt === null) {
        context.input({ type: "reload" });
      }
      return;
    }

    const targets = (state.targets ?? []).filter((target) => target.hp > 0);
    if (!targets.length) {
      return;
    }

    const random = createBotRandom(`${context.room.code}:${context.bot.id}:flatterfluff:${context.memory.get("round") ?? ""}`);
    const ranked = [...targets].sort((left, right) => {
      const leftDistance = Math.hypot(left.x - bot.aimX, left.y - bot.aimY);
      const rightDistance = Math.hypot(right.x - bot.aimX, right.y - bot.aimY);
      return targetValue(right) / (0.25 + rightDistance) - targetValue(left) / (0.25 + leftDistance);
    });

    const target = ranked[0];
    const dx = target.x - bot.aimX;
    const dy = target.y - bot.aimY;
    const distance = Math.hypot(dx, dy);
    const hitRadius = Math.max(0.012, target.radius * target.scale);
    const desired = normalize(dx, dy);
    const noise = context.difficulty === "expert" ? 0 : context.difficulty === "hard" ? 0.03 : 0.09;
    const aimX = clamp(desired.x + (random() - 0.5) * noise, -1, 1);
    const aimY = clamp(desired.y + (random() - 0.5) * noise, -1, 1);

    if (distance > hitRadius * 0.55) {
      context.input({ type: "aim", aimX, aimY });
      return;
    }

    // Close enough: keep the aim steady and take the shot. The game enforces
    // its own fire cooldown, so a repeated shoot request is harmless.
    context.input({ type: "aim", aimX: 0, aimY: 0 });
    context.input({ type: "shoot" });
  }
};
