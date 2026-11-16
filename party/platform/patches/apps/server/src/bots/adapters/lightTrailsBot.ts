import type { BotAdapter, BotThinkContext } from "../types.js";
import { clamp, createBotRandom } from "../random.js";

interface TrailSegment {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

interface TrailPlayer {
  playerId: string;
  x: number;
  y: number;
  angleRad: number;
  alive: boolean;
  turnInput: -1 | 0 | 1;
  trailSegments: TrailSegment[];
}

interface LightTrailsState {
  phase: string;
  arenaWidth: number;
  arenaHeight: number;
  trailThickness: number;
  players: Record<string, TrailPlayer>;
}

interface SegmentBox {
  segment: TrailSegment;
  playerId: string;
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
}

const HEAD_RADIUS = 9;

function pointSegmentDistance(px: number, py: number, segment: TrailSegment): number {
  const dx = segment.x2 - segment.x1;
  const dy = segment.y2 - segment.y1;
  const lengthSquared = dx * dx + dy * dy;

  if (lengthSquared <= 0.0001) {
    return Math.hypot(px - segment.x1, py - segment.y1);
  }

  const t = clamp(((px - segment.x1) * dx + (py - segment.y1) * dy) / lengthSquared, 0, 1);
  return Math.hypot(px - (segment.x1 + dx * t), py - (segment.y1 + dy * t));
}

function nearbySegments(
  state: LightTrailsState,
  x: number,
  y: number,
  radius: number
): SegmentBox[] {
  const boxes: SegmentBox[] = [];
  for (const player of Object.values(state.players)) {
    for (const segment of player.trailSegments ?? []) {
      const minX = Math.min(segment.x1, segment.x2);
      const maxX = Math.max(segment.x1, segment.x2);
      const minY = Math.min(segment.y1, segment.y2);
      const maxY = Math.max(segment.y1, segment.y2);
      if (maxX < x - radius || minX > x + radius || maxY < y - radius || minY > y + radius) {
        continue;
      }
      boxes.push({ segment, playerId: player.playerId, minX, minY, maxX, maxY });
    }
  }
  return boxes;
}

function hitsSomething(
  state: LightTrailsState,
  boxes: SegmentBox[],
  x: number,
  y: number,
  clearance: number
): boolean {
  const margin = HEAD_RADIUS;
  if (x < margin || y < margin || x > state.arenaWidth - margin || y > state.arenaHeight - margin) {
    return true;
  }

  for (const box of boxes) {
    if (x < box.minX - clearance || x > box.maxX + clearance ||
        y < box.minY - clearance || y > box.maxY + clearance) {
      continue;
    }
    if (pointSegmentDistance(x, y, box.segment) < clearance) {
      return true;
    }
  }
  return false;
}

/** How much open room a heading has, in pixels, before the first crash. */
function rayDistance(
  state: LightTrailsState,
  boxes: SegmentBox[],
  x: number,
  y: number,
  angle: number,
  step: number,
  steps: number
): number {
  const clearance = state.trailThickness / 2 + 5;
  for (let index = 1; index <= steps; index += 1) {
    const distance = step * index;
    if (hitsSomething(state, boxes, x + Math.cos(angle) * distance, y + Math.sin(angle) * distance, clearance)) {
      return distance - step;
    }
  }
  return step * steps;
}

function threatPenalty(
  state: LightTrailsState,
  bot: TrailPlayer,
  angle: number,
  difficulty: string
): number {
  if (difficulty === "normal") {
    return 0;
  }

  const forward = { x: Math.cos(angle), y: Math.sin(angle) };
  let penalty = 0;

  for (const other of Object.values(state.players)) {
    if (!other.alive || other.playerId === bot.playerId) {
      continue;
    }
    const dx = other.x - bot.x;
    const dy = other.y - bot.y;
    const distance = Math.hypot(dx, dy);
    if (distance > 190 || distance < 1) {
      continue;
    }
    const towards = (dx * forward.x + dy * forward.y) / distance;
    if (towards < 0.55) {
      continue;
    }

    const otherForward = { x: Math.cos(other.angleRad), y: Math.sin(other.angleRad) };
    const opposite = forward.x * otherForward.x + forward.y * otherForward.y < -0.2;
    penalty += (opposite ? 150 : 70) * (1 - distance / 190) * towards;
  }

  return penalty;
}

function chooseTurn(
  state: LightTrailsState,
  bot: TrailPlayer,
  difficulty: string,
  random: () => number
): -1 | 0 | 1 {
  const expert = difficulty === "expert";
  const step = expert ? 16 : 20;
  const steps = expert ? 18 : 13;
  const offsets = expert
    ? [-1.05, -0.8, -0.55, -0.35, -0.18, 0, 0.18, 0.35, 0.55, 0.8, 1.05]
    : [-0.85, -0.5, -0.25, 0, 0.25, 0.5, 0.85];
  const boxes = nearbySegments(state, bot.x, bot.y, step * steps + 60);
  const scoreFor = (offset: number) => {
    const angle = bot.angleRad + offset;
    const free = rayDistance(state, boxes, bot.x, bot.y, angle, step, steps);
    // Prefer the path with the most room and, on near ties, the gentler turn.
    return free - Math.abs(offset) * 14 - threatPenalty(state, bot, angle, difficulty);
  };

  let bestOffset = 0;
  let bestScore = -Infinity;
  const scored = offsets.map((offset) => ({ offset, score: scoreFor(offset) }));
  scored.sort((left, right) => right.score - left.score);
  bestOffset = scored[0]?.offset ?? 0;
  bestScore = scored[0]?.score ?? 0;

  // Normal bots sometimes take the second-best line; expert never does.
  if (difficulty === "normal" && scored.length > 1 && random() < 0.22) {
    bestOffset = scored[1].offset;
    bestScore = scored[1].score;
  }

  // When every line is blocked, turning hard is better than driving straight.
  if (bestScore < 20) {
    const left = scoreFor(-1.05);
    const right = scoreFor(1.05);
    bestOffset = left >= right ? -1.05 : 1.05;
  }

  if (Math.abs(bestOffset) < 0.16) {
    return 0;
  }
  return bestOffset < 0 ? -1 : 1;
}

export const lightTrailsBot: BotAdapter = {
  gameId: "light-trails",
  thinkEveryMs: 70,
  think(context: BotThinkContext) {
    const state = context.state as LightTrailsState | null;
    if (!state || state.phase !== "playing") {
      return;
    }

    const bot = state.players?.[context.bot.id];
    if (!bot || !bot.alive) {
      return;
    }

    const random = createBotRandom(`${context.room.code}:${context.bot.id}:${context.memory.get("round") ?? ""}`);
    const turn = chooseTurn(state, bot, context.difficulty, random);

    // The game keeps a pressed state per direction; release the other one so
    // the next decision is not fighting a stale hold.
    if (turn === -1) {
      if (bot.turnInput === 1) {
        context.input({ type: "turn", direction: "right", active: false });
      }
      context.input({ type: "turn", direction: "left", active: true });
    } else if (turn === 1) {
      if (bot.turnInput === -1) {
        context.input({ type: "turn", direction: "left", active: false });
      }
      context.input({ type: "turn", direction: "right", active: true });
    } else if (bot.turnInput !== 0) {
      context.input({ type: "turn", direction: "left", active: false });
      context.input({ type: "turn", direction: "right", active: false });
    }
  }
};
