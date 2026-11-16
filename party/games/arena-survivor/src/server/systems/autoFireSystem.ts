import { collectDamageEvents, recordDamageEvent } from "./damageEvents.js";
import { resolveCriticalMultiplier, resolveWeaponCooldown } from "../arenaSurvivorBalance.js";
import { applyLifeSteal } from "./lifeSteal.js";
import { evolvedOrbit } from "../../evolvedOrbit.js";
import {
  ARENA_SURVIVOR_MELEE_ARC_HALF_ANGLE_RAD,
  ARENA_SURVIVOR_MELEE_IMPACT_RATIO,
  ARENA_SURVIVOR_MELEE_SWING_DURATION_MS,
  resolveArenaSurvivorWeaponOrbitDistance,
  resolveArenaSurvivorWeaponSlotTransform
} from "../../protocol.js";
import type {
  ArenaSurvivorRuntimeEnemyState,
  ArenaSurvivorRuntimePlayerState,
  ArenaSurvivorRuntimeState
} from "../arenaSurvivorState.js";
import { createSeededRandom, distanceSquared } from "../arenaSurvivorState.js";
import { arenaSurvivorProjectileDefinitionsById } from "../definitions/projectileDefinitions.js";
import { createArenaSurvivorEnemyDrops } from "../factories/createEnemyDrops.js";
import { createArenaSurvivorProjectile } from "../factories/createProjectile.js";
import { resolveArenaSurvivorDifficulty } from "../difficulty/arenaSurvivorDifficulty.js";
import { resolveArenaSurvivorWeaponLevel } from "../loadout/arenaSurvivorLoadout.js";
import type { ArenaSurvivorWeaponDamageScaling } from "../content/types.js";
import { awardArenaSurvivorEnemyExperience } from "../progression/arenaSurvivorProgression.js";

function resolveStatScalingMultiplier(
  scaling: ArenaSurvivorWeaponDamageScaling | undefined,
  stats: ArenaSurvivorRuntimePlayerState["stats"],
  fallbackMultiplier: number
): number {
  if (!scaling) {
    return fallbackMultiplier;
  }

  const multiplierDelta =
    (stats.meleePowerMultiplier - 1) * (scaling.meleePower ?? 0) +
    (stats.rangedPowerMultiplier - 1) * (scaling.rangedPower ?? 0) +
    (stats.magicPowerMultiplier - 1) * (scaling.magicPower ?? 0) +
    (stats.elementalPowerMultiplier - 1) * (scaling.elementalPower ?? 0) +
    (stats.attackSpeedMultiplier - 1) * (scaling.attackSpeed ?? 0) +
    (stats.maxHp / 100 - 1) * (scaling.maxHp ?? 0) +
    (stats.armor / 10) * (scaling.armor ?? 0) +
    (stats.lifeStealPct / 100) * (scaling.lifeSteal ?? 0);

  return Math.max(0.1, 1 + multiplierDelta);
}

function pickTargetEnemy(
  originX: number,
  originY: number,
  enemies: ArenaSurvivorRuntimeEnemyState[],
  range: number,
  extraHitRadius = 0
) {
  const aliveEnemies = enemies.filter((enemy) => enemy.alive);

  if (aliveEnemies.length === 0) {
    return undefined;
  }

  const inRange = aliveEnemies.filter((enemy) => {
    const hitDistance = range + enemy.radius + extraHitRadius;
    return distanceSquared(originX, originY, enemy.x, enemy.y) <= hitDistance * hitDistance;
  });

  if (inRange.length === 0) {
    return undefined;
  }

  return inRange.reduce((closest, enemy) => {
    const closestDistance = distanceSquared(originX, originY, closest.x, closest.y);
    const enemyDistance = distanceSquared(originX, originY, enemy.x, enemy.y);
    return enemyDistance < closestDistance ? enemy : closest;
  });
}

function resolveAngleDifference(left: number, right: number): number {
  return Math.atan2(Math.sin(left - right), Math.cos(left - right));
}

function collectEnemiesInMeleeArc(
  originX: number,
  originY: number,
  enemies: ArenaSurvivorRuntimeEnemyState[],
  range: number,
  aimAngleRad: number
): ArenaSurvivorRuntimeEnemyState[] {
  return enemies
    .filter((enemy) => {
      if (!enemy.alive) {
        return false;
      }

      const hitDistance = range + enemy.radius;
      const centerDistance = Math.hypot(enemy.x - originX, enemy.y - originY);
      const withinRange = centerDistance <= hitDistance;
      const enemyAngle = Math.atan2(enemy.y - originY, enemy.x - originX);
      const angularPadding =
        centerDistance > 0.0001
          ? Math.asin(Math.min(1, enemy.radius / centerDistance))
          : Math.PI;
      const withinArc =
        Math.abs(resolveAngleDifference(enemyAngle, aimAngleRad)) <=
        ARENA_SURVIVOR_MELEE_ARC_HALF_ANGLE_RAD + angularPadding;

      return withinRange && withinArc;
    })
    .sort(
      (left, right) =>
        distanceSquared(originX, originY, left.x, left.y) -
        distanceSquared(originX, originY, right.x, right.y)
    );
}

export function applyAutoFireSystem(
  state: ArenaSurvivorRuntimeState,
  deltaMs: number,
  now: number
): ArenaSurvivorRuntimeState {
  if (state.phase !== "playing") {
    return state;
  }

  const nextPlayers = [...state.players];
  const nextEnemies = state.enemies.map((enemy) => ({ ...enemy }));
  const nextProjectiles = [...state.projectiles];
  const nextPickups = [...state.pickups];
  const damageEvents = collectDamageEvents(state);
  const difficulty = resolveArenaSurvivorDifficulty(
    state.waveNumber,
    state.players.length,
    state.difficultyTier
  );
  let nextSeed = state.seed;
  let nextKills = state.kills;

  for (let playerIndex = 0; playerIndex < nextPlayers.length; playerIndex += 1) {
    const player = nextPlayers[playerIndex];

    if (!player.alive) {
      continue;
    }

    const nextWeaponStates = player.weaponRuntimeStates.map((weaponState) => ({
      ...weaponState,
      cooldownRemainingMs: Math.max(0, weaponState.cooldownRemainingMs - deltaMs)
    }));
    let shotsFiredThisTick = 0;
    let hitsLandedThisTick = 0;
    let damageDealtThisTick = 0;
    let killsThisTick = 0;
    let nextPlayer = {
      ...player
    };

    for (let weaponIndex = 0; weaponIndex < nextWeaponStates.length; weaponIndex += 1) {
      let weaponState = nextWeaponStates[weaponIndex];
      const { definition, levelDefinition } = resolveArenaSurvivorWeaponLevel(
        weaponState.weaponId,
        weaponState.level,
        nextPlayer.loadout.weapons.find((weapon) => weapon.weaponInstanceId === weaponState.weaponInstanceId)?.evolved
      );
      const slotTransform = resolveArenaSurvivorWeaponSlotTransform(
        weaponIndex,
        resolveArenaSurvivorWeaponOrbitDistance(nextPlayer.radius)
      );
      const orbiting = definition.category === "melee" && nextPlayer.loadout.weapons.some(w => w.weaponInstanceId === weaponState.weaponInstanceId && w.evolved);
      const orbit = evolvedOrbit(nextPlayer.x, nextPlayer.y, state.elapsedMs, weaponIndex);
      const originX = orbiting ? orbit.x : nextPlayer.x + slotTransform.offsetX;
      const originY = orbiting ? orbit.y : nextPlayer.y + slotTransform.offsetY;
      const weaponRange = levelDefinition.range * nextPlayer.stats.weaponRangeMultiplier * (state.survival && definition.category === "ranged" ? 0.8 : 1);

      weaponState = { ...weaponState, effectiveRange: weaponRange };
      nextWeaponStates[weaponIndex] = weaponState;

      const categoryMultiplier =
        definition.category === "magic"
          ? nextPlayer.stats.magicPowerMultiplier
          : definition.category === "melee"
            ? nextPlayer.stats.meleePowerMultiplier
            : nextPlayer.stats.rangedPowerMultiplier;
      const elementalMultiplier = definition.tags.includes("elemental")
        ? nextPlayer.stats.elementalPowerMultiplier
        : 1;
      const weaponScalingMultiplier = resolveStatScalingMultiplier(
        levelDefinition.damageScaling,
        nextPlayer.stats,
        categoryMultiplier * elementalMultiplier
      );
      const baseDamage = Math.max(1,
        levelDefinition.damage * nextPlayer.stats.damageMultiplier * weaponScalingMultiplier);
      const critChance = Math.min(1, Math.max(
        0,
        (nextPlayer.stats.critChancePct + (levelDefinition.critChancePct ?? 0)) / 100
      ));
      const critScale = resolveCriticalMultiplier(levelDefinition.critScale, nextPlayer.stats.critDamageMultiplier);

      if (
        orbiting || (definition.attackPattern === "melee_arc" &&
        weaponState.meleeAttackResolvesAtMs !== null &&
        weaponState.meleeAttackResolvesAtMs !== undefined)
      ) {
        if (orbiting || state.elapsedMs >= weaponState.meleeAttackResolvesAtMs!) {
          const aimAngle = weaponState.lastAimAngleRad ?? slotTransform.angleRad;
          const rapidHitCount = Math.max(
            1,
            (levelDefinition.projectileCount ?? 1) + nextPlayer.stats.projectileCountBonus
          );
          const cleaveTargetCount = Math.max(
            0,
            (levelDefinition.pierce ?? 0) + nextPlayer.stats.pierceBonus
          );
          const firstTarget = collectEnemiesInMeleeArc(
            originX,
            originY,
            nextEnemies,
            weaponRange,
            aimAngle
          )[0];
          const rapidTargetIds = new Set<string>();
          const applyMeleeHit = (enemyId: string): void => {
            const targetIndex = nextEnemies.findIndex((enemy) => enemy.id === enemyId);
            const enemy = targetIndex >= 0 ? nextEnemies[targetIndex] : null;

            if (!enemy || !enemy.alive) {
              return;
            }

            const critRoll = createSeededRandom(nextSeed);
            nextSeed = critRoll.seed;
            const crit = critRoll.value <= critChance;
            const damage = crit
              ? baseDamage * critScale
              : baseDamage;
            const appliedDamage = Math.min(enemy.hp, damage);
            recordDamageEvent(damageEvents, state.elapsedMs, enemy, appliedDamage);
            const nextEnemyHp = enemy.hp - appliedDamage;

            nextEnemies[targetIndex] = { ...enemy, hp: nextEnemyHp, alive: nextEnemyHp > 0 };
            const healing = applyLifeSteal(nextPlayer, state.elapsedMs, nextSeed);
            nextPlayer = healing.player;
            nextSeed = healing.seed;
            hitsLandedThisTick += 1;
            damageDealtThisTick += appliedDamage;

            if (nextEnemyHp <= 0) {
              nextKills += 1;
              killsThisTick += 1;
              nextPlayer = awardArenaSurvivorEnemyExperience(nextPlayer, enemy);
              const drops = createArenaSurvivorEnemyDrops({
                enemy,
                materialValue: difficulty.pickupValue,
                now,
                seed: nextSeed,
                luck: nextPlayer.stats.luck
              });
              nextSeed = drops.seed;
              nextPickups.push(...drops.pickups);
            }
          };

          if (orbiting) {
            const hitTimes: Record<string, number> = {};
            for (const enemy of nextEnemies) {
              if (!enemy.alive) continue;
              const last = weaponState.orbitHitAtMs?.[enemy.id] ?? -Infinity;
              if (state.elapsedMs - last < 500) { hitTimes[enemy.id] = last; continue; }
              // Sample the swept arc to retain contacts on slower server frames.
              const steps = Math.max(1, Math.ceil(deltaMs / 16));
              for (let step = 0; step <= steps; step++) {
                const point = evolvedOrbit(nextPlayer.x, nextPlayer.y, state.elapsedMs - deltaMs + deltaMs * step / steps, weaponIndex);
                if (Math.hypot(enemy.x - point.x, enemy.y - point.y) <= enemy.radius + 22) {
                  applyMeleeHit(enemy.id); hitTimes[enemy.id] = state.elapsedMs; break;
                }
              }
            }
            nextWeaponStates[weaponIndex] = { ...weaponState, orbitHitAtMs: hitTimes, meleeAttackResolvesAtMs: null };
            continue;
          }
          for (let hitIndex = 0; hitIndex < rapidHitCount; hitIndex += 1) {
            const activePrimaryTarget =
              nextEnemies.find((enemy) => enemy.id === firstTarget?.id && enemy.alive) ??
              collectEnemiesInMeleeArc(
                originX,
                originY,
                nextEnemies,
                weaponRange,
                aimAngle
              ).find((enemy) => !rapidTargetIds.has(enemy.id));

            if (!activePrimaryTarget) {
              break;
            }

            rapidTargetIds.add(activePrimaryTarget.id);
            applyMeleeHit(activePrimaryTarget.id);
          }

          const cleaveTargets = collectEnemiesInMeleeArc(
            originX,
            originY,
            nextEnemies,
            weaponRange,
            aimAngle
          )
            .filter((enemy) => !rapidTargetIds.has(enemy.id))
            .slice(0, cleaveTargetCount);

          for (const cleaveTarget of cleaveTargets) {
            applyMeleeHit(cleaveTarget.id);
          }

          shotsFiredThisTick += cleaveTargets.length;
          nextWeaponStates[weaponIndex] = {
            ...weaponState,
            meleeAttackResolvesAtMs: null
          };
        }

        continue;
      }

      if (weaponState.cooldownRemainingMs > 0) {
        continue;
      }

      const projectileRadius =
        definition.attackPattern === "single_projectile"
          ? (arenaSurvivorProjectileDefinitionsById[
            definition.projectileDefinitionId ?? definition.id
          ]?.radius ?? 0)
          : 0;
      const targetEnemy = pickTargetEnemy(
        originX,
        originY,
        nextEnemies,
        weaponRange,
        projectileRadius
      );

      if (!targetEnemy) {
        continue;
      }

      const baseAngle = Math.atan2(targetEnemy.y - originY, targetEnemy.x - originX);
      const cooldownMs = resolveWeaponCooldown(levelDefinition.cooldownMs,
        nextPlayer.stats.autoFireRateMultiplier, definition.category === "melee");

      if (definition.attackPattern === "melee_arc") {
        const rapidHitCount = Math.max(
          1,
          (levelDefinition.projectileCount ?? 1) + nextPlayer.stats.projectileCountBonus
        );
        const targetDistance = Math.hypot(targetEnemy.x - originX, targetEnemy.y - originY);
        const attackReachDistance = Math.max(
          0,
          Math.min(weaponRange, targetDistance - targetEnemy.radius)
        );

        shotsFiredThisTick += rapidHitCount;
        nextWeaponStates[weaponIndex] = {
          ...weaponState,
          cooldownRemainingMs: cooldownMs,
          lastFiredAt: state.elapsedMs,
          lastAimAngleRad: baseAngle,
          lastAttackReachDistance: attackReachDistance,
          meleeAttackResolvesAtMs:
            state.elapsedMs +
            ARENA_SURVIVOR_MELEE_SWING_DURATION_MS * ARENA_SURVIVOR_MELEE_IMPACT_RATIO
        };
        continue;
      }

      const projectileCount = Math.max(
        1,
        (levelDefinition.projectileCount ?? 1) + nextPlayer.stats.projectileCountBonus
      );
      const spreadRad = projectileCount > 1 ? 0.18 : 0;
      const projectileSpeed =
        levelDefinition.projectileSpeed * nextPlayer.stats.projectileSpeedMultiplier;

      for (let index = 0; index < projectileCount; index += 1) {
        const offsetIndex = index - (projectileCount - 1) / 2;
        const angle = baseAngle + offsetIndex * spreadRad;
        const critRoll = createSeededRandom(nextSeed);
        nextSeed = critRoll.seed;
        const crit = critRoll.value <= critChance;
        const damage = crit
          ? baseDamage * critScale
          : baseDamage;

        nextProjectiles.push(
          createArenaSurvivorProjectile({
            ownerId: nextPlayer.playerId,
            ownerKind: "player",
            definitionId: definition.projectileDefinitionId ?? definition.id,
            originX,
            originY,
            angleRad: angle,
            now,
            speed: projectileSpeed,
            damage,
            maxRange: weaponRange,
            pierce: Math.max(1, 1 + (levelDefinition.pierce ?? 0) + nextPlayer.stats.pierceBonus),
            crit
          })
        );
      }

      shotsFiredThisTick += projectileCount;
      nextWeaponStates[weaponIndex] = {
        ...weaponState,
        cooldownRemainingMs: cooldownMs,
        lastFiredAt: state.elapsedMs,
        lastAimAngleRad: baseAngle,
        lastAttackReachDistance: null,
        meleeAttackResolvesAtMs: null
      };
    }

    nextPlayers[playerIndex] = {
      ...nextPlayer,
      weaponRuntimeStates: nextWeaponStates,
      runStats: {
        ...player.runStats,
        shotsFired: player.runStats.shotsFired + shotsFiredThisTick,
        hitsLanded: player.runStats.hitsLanded + hitsLandedThisTick,
        damageDealt: player.runStats.damageDealt + damageDealtThisTick,
        kills: player.runStats.kills + killsThisTick
      }
    };
  }

  return {
    ...state,
    damageEvents,
    seed: nextSeed,
    kills: nextKills,
    players: nextPlayers,
    enemies: nextEnemies.filter((enemy) => enemy.alive),
    projectiles: nextProjectiles,
    pickups: nextPickups
  };
}
