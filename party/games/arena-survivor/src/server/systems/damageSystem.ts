import { collectDamageEvents, recordDamageEvent } from "./damageEvents.js";
import { arenaSurvivorConfig } from "../arenaSurvivorConfig.js";
import { createArenaSurvivorEnemyDrops } from "../factories/createEnemyDrops.js";
import { resolveArenaSurvivorDifficulty } from "../difficulty/arenaSurvivorDifficulty.js";
import { createSeededRandom } from "../arenaSurvivorState.js";
import type {
  ArenaSurvivorRuntimeEnemyState,
  ArenaSurvivorRuntimeProjectileState,
  ArenaSurvivorRuntimeState
} from "../arenaSurvivorState.js";
import type { ArenaSurvivorCollisionReport } from "./collisionSystem.js";
import { awardArenaSurvivorEnemyExperience } from "../progression/arenaSurvivorProgression.js";
import { arenaSurvivorBalance, resolveArmorDamage } from "../arenaSurvivorBalance.js";
import { applyLifeSteal } from "./lifeSteal.js";

function resolveContactDamage(rawDamage: number, armor: number): number {
  return resolveArmorDamage(rawDamage, armor);
}

function rollDodge(dodgePct: number, seed: number): { dodged: boolean; seed: number } {
  const chance = Math.min(arenaSurvivorBalance.maxDodgePct, Math.max(0, dodgePct)) / 100;

  if (chance <= 0) {
    return { dodged: false, seed };
  }

  const roll = createSeededRandom(seed);

  return {
    dodged: roll.value <= chance,
    seed: roll.seed
  };
}

export function applyDamageSystem(
  state: ArenaSurvivorRuntimeState,
  report: ArenaSurvivorCollisionReport,
  now: number
): ArenaSurvivorRuntimeState {
  if (state.phase !== "playing") {
    return state;
  }

  const enemiesById = new Map<string, ArenaSurvivorRuntimeEnemyState>(
    state.enemies.map((enemy) => [enemy.id, enemy])
  );
  const projectilesById = new Map<string, ArenaSurvivorRuntimeProjectileState>(
    state.projectiles.map((projectile) => [projectile.id, projectile])
  );
  const playersById = new Map(state.players.map((player) => [player.playerId, player]));
  let nextSeed = state.seed;
  let kills = state.kills;
  let pickups = state.pickups;
  const damageEvents = collectDamageEvents(state);
  const difficulty = resolveArenaSurvivorDifficulty(
    state.waveNumber,
    state.players.length,
    state.difficultyTier
  );

  for (const hit of report.projectileHits) {
    const projectile = projectilesById.get(hit.projectileId);
    const enemy = enemiesById.get(hit.enemyId);

    if (!projectile || !projectile.alive || !enemy || !enemy.alive || projectile.hitEnemyIds?.includes(enemy.id)) {
      continue;
    }

    const appliedDamage = Math.min(enemy.hp, projectile.damage);
    recordDamageEvent(damageEvents, state.elapsedMs, enemy, appliedDamage);
    const nextEnemyHp = enemy.hp - appliedDamage;
    const owner = playersById.get(projectile.ownerId);

    if (owner) {
      const healing = applyLifeSteal(owner, state.elapsedMs, nextSeed);
      nextSeed = healing.seed;

      playersById.set(owner.playerId, {
        ...healing.player,
        runStats: {
          ...owner.runStats,
          hitsLanded: owner.runStats.hitsLanded + 1,
          damageDealt: owner.runStats.damageDealt + appliedDamage
        }
      });
    }

    enemiesById.set(enemy.id, {
      ...enemy,
      hp: nextEnemyHp,
      alive: nextEnemyHp > 0
    });

    if (projectile.remainingPierce <= 1) {
      projectilesById.set(projectile.id, {
        ...projectile,
        remainingPierce: 0,
        hitEnemyIds: [...(projectile.hitEnemyIds ?? []), enemy.id],
        alive: false
      });
    } else {
      projectilesById.set(projectile.id, {
        ...projectile,
        remainingPierce: projectile.remainingPierce - 1,
        hitEnemyIds: [...(projectile.hitEnemyIds ?? []), enemy.id]
      });
    }

    if (nextEnemyHp <= 0) {
      kills += 1;
      const drops = createArenaSurvivorEnemyDrops({
        enemy,
        materialValue: difficulty.pickupValue,
        now,
        seed: nextSeed,
        luck: owner?.stats.luck
      });
      nextSeed = drops.seed;
      pickups = [...pickups, ...drops.pickups];

      if (owner) {
        const updatedOwner = playersById.get(owner.playerId);

        if (updatedOwner) {
          const rewardedOwner = awardArenaSurvivorEnemyExperience(updatedOwner, enemy);
          playersById.set(owner.playerId, {
            ...rewardedOwner,
            runStats: {
              ...rewardedOwner.runStats,
              kills: rewardedOwner.runStats.kills + 1
            }
          });
        }
      }
    }
  }

  for (const hit of report.playerProjectileHits) {
    const projectile = projectilesById.get(hit.projectileId);
    const player = playersById.get(hit.playerId);

    if (!projectile || !projectile.alive || !player || !player.alive) {
      continue;
    }

    projectilesById.set(projectile.id, {
      ...projectile,
      alive: false
    });

    if (now < player.invulnerableUntilMs) {
      continue;
    }

    const dodgeRoll = rollDodge(player.stats.dodgePct, nextSeed);
    nextSeed = dodgeRoll.seed;

    if (dodgeRoll.dodged) {
      continue;
    }

    const mitigatedDamage =
      resolveContactDamage(projectile.damage, player.stats.armor) *
      player.stats.contactDamageTakenMultiplier;
    const nextHp = player.hp - mitigatedDamage;

    playersById.set(player.playerId, {
      ...player,
      hp: nextHp,
      alive: nextHp > 0,
      invulnerableUntilMs: now + arenaSurvivorConfig.hitInvulnerabilityMs,
      runStats: {
        ...player.runStats,
        damageTaken: player.runStats.damageTaken + mitigatedDamage
      }
    });
  }

  for (const hit of report.enemyHits) {
    const enemy = enemiesById.get(hit.enemyId);
    const player = playersById.get(hit.playerId);

    if (!enemy || !enemy.alive || !player || !player.alive) {
      continue;
    }

    if (now < player.invulnerableUntilMs) {
      continue;
    }

    if (enemy.lastContactDamageAt !== null && now < enemy.lastContactDamageAt + enemy.contactDamageCooldownMs) {
      continue;
    }

    const dodgeRoll = rollDodge(player.stats.dodgePct, nextSeed);
    nextSeed = dodgeRoll.seed;

    if (dodgeRoll.dodged) {
      enemiesById.set(enemy.id, {
        ...enemy,
        lastContactDamageAt: now
      });
      continue;
    }

    const mitigatedDamage =
      resolveContactDamage(enemy.contactDamage, player.stats.armor) *
      player.stats.contactDamageTakenMultiplier;
    const nextHp = player.hp - mitigatedDamage;

    playersById.set(player.playerId, {
      ...player,
      hp: nextHp,
      alive: nextHp > 0,
      invulnerableUntilMs: now + arenaSurvivorConfig.hitInvulnerabilityMs,
      runStats: {
        ...player.runStats,
        damageTaken: player.runStats.damageTaken + mitigatedDamage
      }
    });

    enemiesById.set(enemy.id, {
      ...enemy,
      lastContactDamageAt: now
    });
  }

  return {
    ...state,
    damageEvents,
    seed: nextSeed,
    kills,
    players: state.players
      .map((player) => playersById.get(player.playerId) ?? player),
    enemies: [...enemiesById.values()].filter((enemy) => enemy.alive),
    projectiles: [...projectilesById.values()].filter((projectile) => projectile.alive && !projectile.expiresAfterStep),
    pickups
  };
}
