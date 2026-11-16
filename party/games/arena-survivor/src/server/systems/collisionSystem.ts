import { distanceSquared } from "../arenaSurvivorState.js";
import type { ArenaSurvivorRuntimeState } from "../arenaSurvivorState.js";

export interface ArenaSurvivorCollisionReport {
  projectileHits: Array<{ projectileId: string; enemyId: string }>;
  playerProjectileHits: Array<{ projectileId: string; playerId: string }>;
  enemyHits: Array<{ enemyId: string; playerId: string }>;
}

/** First segment/circle intersection, so fast shots cannot skip small enemies. */
function contactTime(projectile: ArenaSurvivorRuntimeState["projectiles"][number],
  target: { x: number; y: number; radius: number }): number | null {
  const startX = projectile.previousX ?? projectile.x;
  const startY = projectile.previousY ?? projectile.y;
  const dx = projectile.x - startX, dy = projectile.y - startY;
  const fx = startX - target.x, fy = startY - target.y;
  const radius = projectile.radius + target.radius;
  const c = fx * fx + fy * fy - radius * radius;
  if (c <= 0) return 0;
  const a = dx * dx + dy * dy;
  if (a <= 0) return null;
  const b = 2 * (fx * dx + fy * dy);
  const discriminant = b * b - 4 * a * c;
  if (discriminant < 0) return null;
  const t = (-b - Math.sqrt(discriminant)) / (2 * a);
  return t >= 0 && t <= 1 ? t : null;
}

export function resolveCollisionSystem(
  state: ArenaSurvivorRuntimeState
): ArenaSurvivorCollisionReport {
  const projectileHits: Array<{ projectileId: string; enemyId: string }> = [];
  const playerProjectileHits: Array<{ projectileId: string; playerId: string }> = [];
  const enemyHits: Array<{ enemyId: string; playerId: string }> = [];

  for (const projectile of state.projectiles) {
    if (!projectile.alive) {
      continue;
    }

    if (projectile.ownerKind === "enemy") {
      const first = state.players.filter(player => player.alive)
        .map(player => ({ player, time: contactTime(projectile, player) }))
        .filter((hit): hit is { player: typeof hit.player; time: number } => hit.time !== null)
        .sort((left, right) => left.time - right.time)[0];
      if (first) playerProjectileHits.push({ projectileId: projectile.id, playerId: first.player.playerId });

      continue;
    }

    const contacts = state.enemies.filter(enemy => enemy.alive && !projectile.hitEnemyIds?.includes(enemy.id))
      .map(enemy => ({ enemy, time: contactTime(projectile, enemy) }))
      .filter((hit): hit is { enemy: typeof hit.enemy; time: number } => hit.time !== null)
      .sort((left, right) => left.time - right.time);
    // Damage resolution consumes pierce only for enemies still alive then.
    for (const { enemy } of contacts) projectileHits.push({ projectileId: projectile.id, enemyId: enemy.id });
  }

  for (const enemy of state.enemies) {
    if (!enemy.alive) {
      continue;
    }

    for (const player of state.players) {
      if (!player.alive) {
        continue;
      }

      const hitDistance = player.radius + enemy.radius;

      if (distanceSquared(player.x, player.y, enemy.x, enemy.y) <= hitDistance * hitDistance) {
        enemyHits.push({ enemyId: enemy.id, playerId: player.playerId });
        break;
      }
    }
  }

  return {
    projectileHits,
    playerProjectileHits,
    enemyHits
  };
}
