import Phaser from "phaser";
import { evolvedOrbit } from "../evolvedOrbit.js";
import {
  ARENA_SURVIVOR_MELEE_ARC_HALF_ANGLE_RAD,
  ARENA_SURVIVOR_MELEE_IMPACT_RATIO,
  ARENA_SURVIVOR_MELEE_SWING_DURATION_MS,
  ARENA_SURVIVOR_MAX_WEAPON_SLOTS,
  resolveArenaSurvivorWeaponOrbitDistance,
  resolveArenaSurvivorWeaponSlotTransform,
  type ArenaSurvivorPlayerState,
  type ArenaSurvivorState,
  type ArenaSurvivorWeaponCategory
} from "../protocol.js";
import {
  arenaSurvivorMarshmallowRigKeys,
  resolveArenaSurvivorEnemySpriteKey,
  resolveArenaSurvivorMarshmallowHeadbandKey,
  resolveArenaSurvivorPickupSpriteKey,
  resolveArenaSurvivorPlayerSpriteKey,
  resolveArenaSurvivorWeaponCarrySpriteKey
} from "./arenaSurvivorAssets.js";
import { arenaSurvivorVisualConfig } from "./arenaSurvivorVisualConfig.js";

/** Grey for a downed player's fallback token on the dark arena floor. */
const DOWNED_PLAYER_COLOR = "#94a3b8";

function toColor(color: string): number {
  return Phaser.Display.Color.HexStringToColor(color).color;
}

function resolveWeaponColor(category: ArenaSurvivorWeaponCategory): number {
  switch (category) {
    case "magic":
      return 0xa855f7;
    case "melee":
      return 0xf97316;
    case "ranged":
    default:
      return 0x38bdf8;
  }
}

function resolveProjectileAngle(vx: number, vy: number): number {
  return Math.atan2(vy, vx);
}

function resolvePlayerDisplayRadius(radius: number): number {
  return radius * arenaSurvivorVisualConfig.player.diameterScale;
}

const MARSHMALLOW_PLAYER_SCALE = 0.86;
const MARSHMALLOW_WEAPON_SCALE = 1;

const enemyVisualScaleByDefinition: Readonly<Record<string, number>> = {
  "slime-blob": 0.9,
  "needle-runner": 0.9,
  "loot-runner": 0.88,
  "fang-crawler": 0.96,
  "ember-wisp": 0.96,
  "ash-spitter": 0.98,
  "toxic-shroom": 1,
  "stone-brute": 1.06,
  "shell-bulwark": 1.08,
  "plague-lobber": 1.08,
  "elite-spitter": 1.08,
  "charger-hulk": 1.1,
  "iron-mauler": 1.14,
  "scrap-goliath": 1.18,
  "crimson-overlord": 1.22
};

function resolveEnemyDisplayRadius(radius: number, definitionId: string): number {
  return (
    radius *
    arenaSurvivorVisualConfig.enemy.diameterScale *
    (enemyVisualScaleByDefinition[definitionId] ?? 1)
  );
}

function hashSeed(input: string): number {
  let hash = 0;

  for (let index = 0; index < input.length; index += 1) {
    hash = (hash * 31 + input.charCodeAt(index)) >>> 0;
  }

  return hash;
}

function resolvePhaseOffset(seedKey: string): number {
  return (hashSeed(seedKey) % 360) * (Math.PI / 180);
}

function resolvePlayerBobOffset(playerId: string, elapsedMs: number): number {
  const phase = resolvePhaseOffset(playerId);
  return (
    Math.sin(elapsedMs / arenaSurvivorVisualConfig.player.bobSpeedMs + phase) *
    arenaSurvivorVisualConfig.player.bobAmplitude
  );
}

function resolvePlayerPulseScale(playerId: string, elapsedMs: number): number {
  const phase = (hashSeed(playerId) % 360) * (Math.PI / 180);
  return 1 + Math.sin(elapsedMs / arenaSurvivorVisualConfig.player.pulseSpeedMs + phase) *
    arenaSurvivorVisualConfig.player.pulseAmplitude;
}

/** Per-enemy pulse phase and speed; hashing the id twice per enemy per frame added up. */
const enemyPulseParams = new Map<string, { phase: number; speedMs: number }>();

function resolveEnemyPulseScale(enemyId: string, elapsedMs: number): number {
  if (arenaSurvivorVisualConfig.enemy.pulseAmplitude === 0) return 1;
  let params = enemyPulseParams.get(enemyId);

  if (!params) {
    if (enemyPulseParams.size > 4096) {
      enemyPulseParams.clear();
    }

    const speedOffset =
      ((hashSeed(`${enemyId}:speed`) % 1000) / 1000 - 0.5) * 2 * arenaSurvivorVisualConfig.enemy.pulseSpeedVarianceMs;
    params = {
      phase: resolvePhaseOffset(enemyId),
      speedMs: Math.max(180, arenaSurvivorVisualConfig.enemy.pulseSpeedMs + speedOffset)
    };
    enemyPulseParams.set(enemyId, params);
  }

  return 1 + Math.sin(elapsedMs / params.speedMs + params.phase) * arenaSurvivorVisualConfig.enemy.pulseAmplitude;
}

function resolvePlayerVisualPosition(
  player: ArenaSurvivorState["players"][number],
  elapsedMs: number
): { x: number; y: number; bobOffset: number; pulseScale: number } {
  const walking = Math.min(1, Math.hypot(player.vx, player.vy) / 60);
  const bobOffset = resolvePlayerBobOffset(player.playerId, elapsedMs) * walking;
  const pulseScale = resolvePlayerPulseScale(player.playerId, elapsedMs);

  return {
    x: player.x,
    y: player.y - bobOffset,
    bobOffset,
    pulseScale
  };
}

interface NearestEnemy {
  x: number;
  y: number;
}

/**
 * The closest living enemy per player, found once per frame.
 *
 * Every weapon of every player and the marshmallow faces each used to scan the
 * whole enemy list on their own, up to ~30 scans per frame.
 */
function nearCamera(scene: Phaser.Scene, entity: { x: number; y: number; radius: number }): boolean {
  const camera = scene.cameras.main;
  const padding = entity.radius * 3 + 100;
  return entity.x >= camera.scrollX - padding && entity.y >= camera.scrollY - padding &&
    entity.x <= camera.scrollX + scene.scale.width / camera.zoom + padding &&
    entity.y <= camera.scrollY + scene.scale.height / camera.zoom + padding;
}

function resolveNearestEnemyByPlayer(state: ArenaSurvivorState): Map<string, NearestEnemy | null> {
  const nearestByPlayer = new Map<string, NearestEnemy | null>();

  for (const player of state.players) {
    let nearest: NearestEnemy | null = null;
    let nearestDistance = Number.POSITIVE_INFINITY;

    for (const enemy of state.enemies) {
      if (!enemy.alive) {
        continue;
      }

      const deltaX = enemy.x - player.x;
      const deltaY = enemy.y - player.y;
      const distance = deltaX * deltaX + deltaY * deltaY;

      if (distance < nearestDistance) {
        nearestDistance = distance;
        nearest = enemy;
      }
    }

    nearestByPlayer.set(player.playerId, nearest);
  }

  return nearestByPlayer;
}

function resolveWeaponAimAngle(
  player: ArenaSurvivorState["players"][number],
  nearestEnemy: NearestEnemy | null,
  originX: number,
  originY: number,
  fallbackAngle: number
): number {
  if (nearestEnemy) {
    return Math.atan2(nearestEnemy.y - originY, nearestEnemy.x - originX);
  }

  if (Math.hypot(player.vx, player.vy) > 8) {
    return Math.atan2(player.vy, player.vx);
  }

  return fallbackAngle;
}

function resolveMeleeSwingPose(
  lastFiredAt: number | null | undefined,
  elapsedMs: number
): { reachProgress: number; angleOffset: number } {
  if (lastFiredAt === null || lastFiredAt === undefined) {
    return { reachProgress: 0, angleOffset: 0 };
  }

  const elapsedSinceAttack = elapsedMs - lastFiredAt;

  if (elapsedSinceAttack < 0 || elapsedSinceAttack > ARENA_SURVIVOR_MELEE_SWING_DURATION_MS) {
    return { reachProgress: 0, angleOffset: 0 };
  }

  const impactAtMs = ARENA_SURVIVOR_MELEE_SWING_DURATION_MS * ARENA_SURVIVOR_MELEE_IMPACT_RATIO;

  if (elapsedSinceAttack <= impactAtMs) {
    const windupProgress = elapsedSinceAttack / Math.max(1, impactAtMs);
    return {
      reachProgress: windupProgress,
      angleOffset: -ARENA_SURVIVOR_MELEE_ARC_HALF_ANGLE_RAD * (1 - windupProgress)
    };
  }

  const followThroughProgress =
    (elapsedSinceAttack - impactAtMs) /
    Math.max(1, ARENA_SURVIVOR_MELEE_SWING_DURATION_MS - impactAtMs);

  return {
    reachProgress: Math.max(0, 1 - followThroughProgress),
    angleOffset: ARENA_SURVIVOR_MELEE_ARC_HALF_ANGLE_RAD * followThroughProgress
  };
}

function resolveWeaponDisplaySize(
  player: ArenaSurvivorState["players"][number],
  slotIndex: number
): number {
  const equippedWeapon = player.loadout.weapons[slotIndex];
  const weaponState = player.weaponRuntimeStates[slotIndex];
  const baseDisplaySize = Math.max(
    arenaSurvivorVisualConfig.weaponSlots.minSpriteDisplaySize,
    player.radius * arenaSurvivorVisualConfig.weaponSlots.spriteDisplaySizeMultiplier
  );

  if (equippedWeapon?.category !== "melee") {
    return equippedWeapon?.category === "ranged"
      ? baseDisplaySize * arenaSurvivorVisualConfig.weaponSlots.rangedSpriteScale
      : baseDisplaySize;
  }

  const effectiveRange = weaponState?.effectiveRange ?? weaponState?.lastAttackReachDistance ?? 0;
  return Phaser.Math.Clamp(
    Math.max(
      arenaSurvivorVisualConfig.weaponSlots.meleeMinSpriteDisplaySize,
      effectiveRange * arenaSurvivorVisualConfig.weaponSlots.meleeSpriteRangeRatio
    ),
    arenaSurvivorVisualConfig.weaponSlots.meleeMinSpriteDisplaySize,
    arenaSurvivorVisualConfig.weaponSlots.meleeMaxSpriteDisplaySize
  );
}

function resolveWeaponPose(
  player: ArenaSurvivorState["players"][number],
  state: ArenaSurvivorState,
  slotIndex: number,
  orbitDistance: number,
  playerPosition: { x: number; y: number },
  weaponDisplaySize: number,
  nearestEnemy: NearestEnemy | null
): { x: number; y: number; aimAngle: number } {
  const equippedWeapon = player.loadout.weapons[slotIndex];
  const weaponState = player.weaponRuntimeStates[slotIndex];
  const slotTransform = resolveArenaSurvivorWeaponSlotTransform(slotIndex, orbitDistance);
  const baseX = playerPosition.x + slotTransform.offsetX;
  const baseY = playerPosition.y + slotTransform.offsetY;
  const meleeSwingPose =
    equippedWeapon?.category === "melee"
      ? resolveMeleeSwingPose(weaponState?.lastFiredAt, state.elapsedMs)
      : { reachProgress: 0, angleOffset: 0 };
  const isActiveMeleeSwing =
    equippedWeapon?.category === "melee" &&
    meleeSwingPose.reachProgress > 0.01 &&
    weaponState?.lastAimAngleRad !== null &&
    weaponState?.lastAimAngleRad !== undefined;
  const meleeAimAngle = weaponState?.lastAimAngleRad ?? slotTransform.angleRad;
  const aimAngle =
    equippedWeapon?.category === "melee"
      ? isActiveMeleeSwing
        ? meleeAimAngle + meleeSwingPose.angleOffset
        : slotTransform.angleRad
      : resolveWeaponAimAngle(player, nearestEnemy, baseX, baseY, slotTransform.angleRad);
  const meleeLungeDistance =
    equippedWeapon?.category === "melee"
      ? Math.max(
        0,
        (weaponState?.lastAttackReachDistance ?? 0) -
          weaponDisplaySize * arenaSurvivorVisualConfig.weaponSlots.meleeSpriteTipRatio
      ) * meleeSwingPose.reachProgress
      : 0;

  return {
    x: baseX + Math.cos(aimAngle) * meleeLungeDistance,
    y: baseY + Math.sin(aimAngle) * meleeLungeDistance,
    aimAngle
  };
}

function drawProjectileDiamond(
  graphics: Phaser.GameObjects.Graphics,
  x: number,
  y: number,
  radius: number,
  fillColor: number
): void {
  graphics.fillStyle(fillColor, 0.96);
  graphics.fillPoints(
    [
      new Phaser.Geom.Point(x, y - radius),
      new Phaser.Geom.Point(x + radius, y),
      new Phaser.Geom.Point(x, y + radius),
      new Phaser.Geom.Point(x - radius, y)
    ],
    true
  );
}

function drawProjectileTriangle(
  graphics: Phaser.GameObjects.Graphics,
  x: number,
  y: number,
  radius: number,
  angleRad: number,
  fillColor: number
): void {
  const tipLength = radius * 1.65;
  const wingLength = radius * 0.9;
  const tip = new Phaser.Geom.Point(
    x + Math.cos(angleRad) * tipLength,
    y + Math.sin(angleRad) * tipLength
  );
  const left = new Phaser.Geom.Point(
    x + Math.cos(angleRad + Math.PI * 0.72) * wingLength,
    y + Math.sin(angleRad + Math.PI * 0.72) * wingLength
  );
  const right = new Phaser.Geom.Point(
    x + Math.cos(angleRad - Math.PI * 0.72) * wingLength,
    y + Math.sin(angleRad - Math.PI * 0.72) * wingLength
  );

  graphics.fillStyle(fillColor, 0.96);
  graphics.fillPoints([tip, left, right], true);
}

function drawProjectileBolt(
  graphics: Phaser.GameObjects.Graphics,
  x: number,
  y: number,
  radius: number,
  angleRad: number,
  fillColor: number
): void {
  const length = radius * 3.6;
  const tailX = x - Math.cos(angleRad) * length * 0.55;
  const tailY = y - Math.sin(angleRad) * length * 0.55;
  const headX = x + Math.cos(angleRad) * length * 0.55;
  const headY = y + Math.sin(angleRad) * length * 0.55;

  graphics.lineStyle(Math.max(2, radius * 0.95), fillColor, 0.98);
  graphics.lineBetween(tailX, tailY, headX, headY);
  graphics.fillStyle(0xffffff, 0.75);
  graphics.fillCircle(headX, headY, Math.max(1.4, radius * 0.45));
}

function drawArenaSurvivorProjectile(
  graphics: Phaser.GameObjects.Graphics,
  projectile: ArenaSurvivorState["projectiles"][number],
  x: number,
  y: number,
  radius: number
): void {
  const angleRad = resolveProjectileAngle(projectile.vx, projectile.vy);

  switch (projectile.definitionId) {
    case "coil-spike":
      // A long electromagnetic needle with two parallel rails.
      graphics.lineStyle(radius * 0.45, 0xa78bfa, 0.25);
      graphics.lineBetween(x - radius * 2.3, y, x + radius * 1.8, y);
      for (const side of [-1, 1]) {
        graphics.lineStyle(radius * 0.18, 0xc4b5fd, 0.95);
        graphics.lineBetween(x - radius * 1.8, y + side * radius * 0.45,
          x + radius * 0.6, y + side * radius * 0.45);
      }
      graphics.fillStyle(0xf5f3ff, 1);
      graphics.fillTriangle(x + radius * 2.1, y, x - radius * 0.7, y - radius * 0.3,
        x - radius * 0.7, y + radius * 0.3);
      return;
    case "gear-shot":
      // Brass teeth and a hollow hub remain readable even without color.
      graphics.fillStyle(0xd99a43, 1);
      for (let tooth = 0; tooth < 8; tooth += 1) {
        const angle = tooth * Math.PI / 4;
        graphics.fillCircle(x + Math.cos(angle) * radius * 1.15,
          y + Math.sin(angle) * radius * 1.15, radius * 0.38);
      }
      graphics.fillCircle(x, y, radius * 1.08);
      graphics.fillStyle(0x453027, 1);
      graphics.fillCircle(x, y, radius * 0.48);
      graphics.lineStyle(radius * 0.17, 0xffe0a3, 1);
      graphics.strokeCircle(x, y, radius * 0.75);
      return;
    case "venom-dart":
      graphics.fillStyle(0x84cc16, 0.2);
      graphics.fillEllipse(x - radius * 0.9, y, radius * 3, radius * 1.4);
      graphics.lineStyle(radius * 0.3, 0xd9f99d, 1);
      graphics.lineBetween(x - radius * 1.65, y, x + radius * 0.8, y);
      graphics.fillStyle(0xa3e635, 1);
      graphics.fillTriangle(x + radius * 1.9, y, x, y - radius * 0.7, x, y + radius * 0.7);
      graphics.fillStyle(0x4d7c0f, 1);
      graphics.fillTriangle(x - radius * 0.8, y, x - radius * 1.8, y - radius * 0.65,
        x - radius * 1.8, y + radius * 0.65);
      return;
    case "prism-shard":
      graphics.fillStyle(0xe879f9, 0.18);
      graphics.fillCircle(x, y, radius * 1.7);
      graphics.fillStyle(0xf0abfc, 1);
      graphics.fillTriangle(x + radius * 1.5, y, x, y - radius, x - radius * 1.3, y);
      graphics.fillStyle(0x67e8f9, 1);
      graphics.fillTriangle(x + radius * 1.5, y, x, y + radius, x - radius * 1.3, y);
      graphics.lineStyle(radius * 0.18, 0xffffff, 0.95);
      graphics.lineBetween(x - radius * 1.3, y, x + radius * 1.5, y);
      return;
    case "hunter-arrow":
      drawProjectileTriangle(graphics, x, y, radius, angleRad, 0xf59e0b);
      graphics.lineStyle(Math.max(1, radius * 0.4), 0xfef3c7, 0.88);
      graphics.lineBetween(
        x - Math.cos(angleRad) * radius * 1.25,
        y - Math.sin(angleRad) * radius * 1.25,
        x + Math.cos(angleRad) * radius * 0.25,
        y + Math.sin(angleRad) * radius * 0.25
      );
      return;
    case "smg-pellet":
      drawProjectileBolt(graphics, x, y, Math.max(2.2, radius * 0.7), angleRad, 0xe2e8f0);
      return;
    case "ember-orb":
      graphics.fillStyle(0xef4444, 0.45);
      graphics.fillTriangle(x - radius * 2.4, y, x + radius * 0.25, y - radius,
        x + radius * 0.25, y + radius);
      graphics.fillStyle(0xfbbf24, 0.75);
      graphics.fillTriangle(x - radius * 1.8, y, x + radius * 0.3, y - radius * 0.5,
        x + radius * 0.3, y + radius * 0.5);
      graphics.fillStyle(0xf97316, 0.97);
      graphics.fillCircle(x, y, radius);
      graphics.fillStyle(0xfff7c2, 1);
      graphics.fillCircle(x + radius * 0.25, y, radius * 0.5);
      return;
    case "ember-bolt":
      graphics.fillStyle(0xfb923c, 0.2);
      graphics.fillCircle(x, y, radius * 2.2);
      graphics.fillStyle(0xf97316, 0.97);
      graphics.fillCircle(x, y, radius * 1.15);
      graphics.fillStyle(0xfef3c7, 0.78);
      graphics.fillCircle(x, y, Math.max(1.4, radius * 0.45));
      return;
    case "frost-shard":
      graphics.lineStyle(Math.max(1, radius * 0.28), 0xe0f2fe, 0.85);
      drawProjectileDiamond(graphics, x, y, radius * 1.05, 0x67e8f9);
      graphics.strokePoints(
        [
          new Phaser.Geom.Point(x, y - radius * 1.05),
          new Phaser.Geom.Point(x + radius * 1.05, y),
          new Phaser.Geom.Point(x, y + radius * 1.05),
          new Phaser.Geom.Point(x - radius * 1.05, y)
        ],
        true
      );
      return;
    case "spark-bolt":
      graphics.lineStyle(radius * 0.8, 0x38bdf8, 0.22);
      graphics.lineBetween(x - radius * 2.2, y, x + radius * 2.1, y);
      graphics.lineStyle(radius * 0.32, 0xe0f2fe, 1);
      graphics.strokePoints([
        new Phaser.Geom.Point(x - radius * 2.1, y + radius * 0.4),
        new Phaser.Geom.Point(x - radius * 0.6, y - radius * 0.5),
        new Phaser.Geom.Point(x - radius * 0.2, y + radius * 0.5),
        new Phaser.Geom.Point(x + radius * 0.8, y - radius * 0.45),
        new Phaser.Geom.Point(x + radius * 2, y)
      ], false);
      return;
    case "toxic-spore":
      graphics.fillStyle(0x22c55e, 0.18);
      graphics.fillCircle(x, y, radius * 2.25);
      graphics.fillStyle(0x4ade80, 0.94);
      graphics.fillCircle(x, y, radius * 1.2);
      graphics.lineStyle(Math.max(1, radius * 0.35), 0xbbf7d0, 0.78);
      graphics.strokeCircle(x, y, radius * 1.45);
      return;
    case "crimson-orb":
      graphics.fillStyle(0x7f1d1d, 0.18);
      graphics.fillCircle(x, y, radius * 2.5);
      graphics.fillStyle(0xef4444, 0.96);
      graphics.fillCircle(x, y, radius * 1.2);
      graphics.lineStyle(Math.max(1, radius * 0.3), 0xfda4af, 0.82);
      graphics.strokeCircle(x, y, radius * 1.55);
      graphics.fillStyle(0xffedd5, 0.82);
      graphics.fillCircle(x, y, Math.max(1.6, radius * 0.38));
      return;
    case "pistol-round":
      graphics.fillStyle(0xf59e0b, 0.25);
      graphics.fillTriangle(x - radius * 2.2, y, x, y - radius * 0.55, x, y + radius * 0.55);
      graphics.fillStyle(0xfbbf24, 0.96);
      graphics.fillRoundedRect(x - radius * 0.8, y - radius * 0.6, radius * 2, radius * 1.2, radius * 0.55);
      graphics.fillStyle(0xfffbeb, 0.75);
      graphics.fillCircle(x, y, Math.max(1.2, radius * 0.42));
      return;
    default:
      graphics.fillStyle(projectile.ownerKind === "enemy" ? 0xfb7185 : 0xfbbf24, 0.95);
      graphics.fillCircle(x, y, Math.max(2.5, radius));
  }
}

function drawSpawnIndicator(
  graphics: Phaser.GameObjects.Graphics,
  indicator: ArenaSurvivorState["spawnIndicators"][number],
  elapsedMs: number
): void {
  const warningDurationMs = Math.max(1, indicator.spawnAtMs - indicator.createdAtMs);
  const remainingMs = Math.max(0, indicator.spawnAtMs - elapsedMs);
  const progress = Phaser.Math.Clamp(1 - remainingMs / warningDurationMs, 0, 1);
  const pulse = 0.82 + Math.sin((elapsedMs - indicator.createdAtMs) / 70) * 0.18;
  const crossRadius = (16 + progress * 10) * 0.75;
  const ringRadius = crossRadius * (1.05 + pulse * 0.2);
  const alpha = 0.34 + progress * 0.36;

  graphics.fillStyle(0x7f1d1d, 0.12 + progress * 0.1);
  graphics.fillCircle(indicator.x, indicator.y, ringRadius * 1.1);
  graphics.lineStyle(2.5, 0xfca5a5, alpha * 0.9);
  graphics.strokeCircle(indicator.x, indicator.y, ringRadius);
  graphics.lineStyle(4, 0xef4444, 0.74 + progress * 0.22);
  graphics.lineBetween(
    indicator.x - crossRadius,
    indicator.y - crossRadius,
    indicator.x + crossRadius,
    indicator.y + crossRadius
  );
  graphics.lineBetween(
    indicator.x + crossRadius,
    indicator.y - crossRadius,
    indicator.x - crossRadius,
    indicator.y + crossRadius
  );
}

function drawMaterialPickup(
  graphics: Phaser.GameObjects.Graphics,
  pickup: ArenaSurvivorState["pickups"][number]
): void {
  const radius = Math.max(5.5, pickup.radius * 0.9);
  const pulse = 0.86 + Math.sin(pickup.ageMs / 160) * 0.12;
  const coreRadius = radius * pulse;

  graphics.fillStyle(0xf59e0b, 0.14);
  graphics.fillCircle(pickup.x, pickup.y, radius * 1.65);
  graphics.lineStyle(2, 0xfcd34d, 0.9);
  graphics.strokeCircle(pickup.x, pickup.y, radius * 1.1);
  graphics.fillStyle(0xfbbf24, 0.96);
  graphics.fillPoints(
    [
      new Phaser.Geom.Point(pickup.x, pickup.y - coreRadius),
      new Phaser.Geom.Point(pickup.x + coreRadius * 0.72, pickup.y),
      new Phaser.Geom.Point(pickup.x, pickup.y + coreRadius),
      new Phaser.Geom.Point(pickup.x - coreRadius * 0.72, pickup.y)
    ],
    true
  );
  graphics.fillStyle(0xfffbeb, 0.7);
  graphics.fillCircle(pickup.x, pickup.y, Math.max(1.3, coreRadius * 0.22));
}

function drawHealthPickup(
  graphics: Phaser.GameObjects.Graphics,
  pickup: ArenaSurvivorState["pickups"][number]
): void {
  const radius = Math.max(6, pickup.radius);
  const pulse = 0.9 + Math.sin(pickup.ageMs / 145) * 0.1;
  const arm = radius * pulse;
  const thickness = Math.max(3, radius * 0.46);

  graphics.fillStyle(0x14532d, 0.16);
  graphics.fillCircle(pickup.x, pickup.y, radius * 1.75);
  graphics.lineStyle(2, 0x86efac, 0.92);
  graphics.strokeCircle(pickup.x, pickup.y, radius * 1.12);
  graphics.lineStyle(thickness, 0x22c55e, 0.98);
  graphics.lineBetween(pickup.x - arm, pickup.y, pickup.x + arm, pickup.y);
  graphics.lineBetween(pickup.x, pickup.y - arm, pickup.x, pickup.y + arm);
  graphics.fillStyle(0xdcfce7, 0.78);
  graphics.fillCircle(pickup.x, pickup.y, Math.max(1.5, radius * 0.22));
}

export interface ArenaSurvivorRenderMeta {
  centerX: number;
  centerY: number;
  zoom: number;
  worldViewWidth: number;
  worldViewHeight: number;
  arenaWidth: number;
  arenaHeight: number;
  left: number;
  top: number;
}

export interface ArenaSurvivorSpriteLayer {
  playerSprites: Map<string, Phaser.GameObjects.Image>;
  marshmallowPlayerRigs: Map<string, ArenaSurvivorMarshmallowPlayerRig>;
  enemySprites: Map<string, Phaser.GameObjects.Image>;
  pickupSprites: Map<string, Phaser.GameObjects.Image>;
  projectileSprites: Map<string, Phaser.GameObjects.Image>;
  weaponSprites: Map<string, Phaser.GameObjects.Image>;
  /**
   * Hidden images waiting for reuse. Enemies, pickups and projectiles come and
   * go by the dozen per second; recycling their images instead of destroying
   * and recreating them keeps garbage collection out of the frame budget.
   */
  imagePool: Phaser.GameObjects.Image[];
}

interface ArenaSurvivorMarshmallowPlayerRig {
  container: Phaser.GameObjects.Container;
  torso: Phaser.GameObjects.Image;
  leftHand: Phaser.GameObjects.Image;
  rightHand: Phaser.GameObjects.Image;
  leftFoot: Phaser.GameObjects.Image;
  rightFoot: Phaser.GameObjects.Image;
  face: Phaser.GameObjects.Graphics;
  headgear: Phaser.GameObjects.Image;
}

export function resolveArenaSurvivorRenderMeta(
  scene: Phaser.Scene,
  state: ArenaSurvivorState
): ArenaSurvivorRenderMeta {
  const alivePlayers = state.players.filter((player) => player.alive);
  const cameraPadding = state.survival ? 160 : Math.max(
    arenaSurvivorVisualConfig.cameraPadding.min,
    Math.round(
      Math.min(state.arenaWidth, state.arenaHeight) * arenaSurvivorVisualConfig.cameraPadding.ratio
    )
  );
  const arenaFitZoom = Math.min(
    scene.scale.width / state.arenaWidth,
    scene.scale.height / state.arenaHeight
  );
  // Wave shows the entire arena at a constant scale. Survival keeps at least
  // 900 world units vertically visible, even for a solo player or grouped team.
  const maxZoom = state.survival ? scene.scale.height / 900 : arenaFitZoom;
  const minZoom = state.survival ? Math.min(maxZoom,
    Math.min(scene.scale.width, scene.scale.height) / (state.survival.maxGroupDistance + 320)) : arenaFitZoom;
  let centerX = state.arenaWidth / 2;
  let centerY = state.arenaHeight / 2;
  let zoom = 1;

  if (alivePlayers.length === 1) {
    const player = alivePlayers[0];
    centerX = player.x;
    centerY = player.y;
    zoom = maxZoom;
  } else if (alivePlayers.length > 1) {
    const minX = Math.min(...alivePlayers.map((player) => player.x));
    const maxX = Math.max(...alivePlayers.map((player) => player.x));
    const minY = Math.min(...alivePlayers.map((player) => player.y));
    const maxY = Math.max(...alivePlayers.map((player) => player.y));
    const width = Math.max(1, maxX - minX);
    const height = Math.max(1, maxY - minY);
    const paddedWidth = width + cameraPadding * 2;
    const paddedHeight = height + cameraPadding * 2;
    centerX = (minX + maxX) / 2;
    centerY = (minY + maxY) / 2;
    zoom = Math.min(scene.scale.width / paddedWidth, scene.scale.height / paddedHeight, maxZoom);
  }

  zoom = Phaser.Math.Clamp(zoom, minZoom, maxZoom);
  if (!state.survival) {
    centerX = state.arenaWidth / 2;
    centerY = state.arenaHeight / 2;
  }

  const worldViewWidth = scene.scale.width / zoom;
  const worldViewHeight = scene.scale.height / zoom;
  const halfWidth = worldViewWidth / 2;
  const halfHeight = worldViewHeight / 2;

  centerX = Phaser.Math.Clamp(centerX, halfWidth, Math.max(halfWidth, state.arenaWidth - halfWidth));
  centerY = Phaser.Math.Clamp(centerY, halfHeight, Math.max(halfHeight, state.arenaHeight - halfHeight));

  return {
    centerX,
    centerY,
    zoom,
    worldViewWidth,
    worldViewHeight,
    arenaWidth: state.arenaWidth,
    arenaHeight: state.arenaHeight,
    left: centerX - halfWidth,
    top: centerY - halfHeight
  };
}

export function applyArenaSurvivorCamera(
  scene: Phaser.Scene,
  meta: ArenaSurvivorRenderMeta
): void {
  const camera = scene.cameras.main;

  camera.setBounds(0, 0, meta.arenaWidth, meta.arenaHeight);
  camera.setZoom(meta.zoom);
  camera.centerOn(meta.centerX, meta.centerY);
}

export function createArenaSurvivorSpriteLayer(): ArenaSurvivorSpriteLayer {
  return {
    playerSprites: new Map(),
    marshmallowPlayerRigs: new Map(),
    enemySprites: new Map(),
    pickupSprites: new Map(),
    projectileSprites: new Map(),
    weaponSprites: new Map(),
    imagePool: []
  };
}

function acquirePooledImage(
  scene: Phaser.Scene,
  layer: ArenaSurvivorSpriteLayer,
  textureKey: string,
  depth: number
): Phaser.GameObjects.Image {
  const image = layer.imagePool.pop() ?? scene.add.image(0, 0, textureKey);

  if (image.texture.key !== textureKey) {
    image.setTexture(textureKey);
  }

  image.setOrigin(0.5);
  image.setDepth(depth);
  image.setRotation(0);
  image.setFlip(false, false);
  image.setAlpha(1);
  image.setVisible(true);
  return image;
}

function releasePooledImages(
  layer: ArenaSurvivorSpriteLayer,
  sprites: Map<string, Phaser.GameObjects.Image>,
  activeIds: ReadonlySet<string>
): void {
  for (const [id, sprite] of sprites) {
    if (!activeIds.has(id)) {
      sprite.setVisible(false);
      layer.imagePool.push(sprite);
      sprites.delete(id);
    }
  }
}

/** Hides everything the layer drew, e.g. while no round state is available. */
export function hideArenaSurvivorSpriteLayer(layer: ArenaSurvivorSpriteLayer): void {
  for (const sprite of layer.playerSprites.values()) {
    sprite.setVisible(false);
  }
  for (const rig of layer.marshmallowPlayerRigs.values()) {
    rig.container.setVisible(false);
  }
  for (const sprite of layer.weaponSprites.values()) {
    sprite.setVisible(false);
  }
  releasePooledImages(layer, layer.enemySprites, new Set());
  releasePooledImages(layer, layer.pickupSprites, new Set());
  releasePooledImages(layer, layer.projectileSprites, new Set());
}

export function destroyArenaSurvivorSpriteLayer(layer: ArenaSurvivorSpriteLayer): void {
  for (const sprites of [
    layer.playerSprites,
    layer.enemySprites,
    layer.pickupSprites,
    layer.projectileSprites,
    layer.weaponSprites
  ]) {
    for (const sprite of sprites.values()) {
      sprite.destroy();
    }
    sprites.clear();
  }

  for (const rig of layer.marshmallowPlayerRigs.values()) {
    rig.container.destroy(true);
  }
  layer.marshmallowPlayerRigs.clear();

  for (const sprite of layer.imagePool) {
    sprite.destroy();
  }
  layer.imagePool.length = 0;
}

/** Reference radius projectile textures are drawn at; sprites scale from it. */
const PROJECTILE_TEXTURE_RADIUS = 16;
/** Glows reach 2.5x the radius; the texture leaves room for them and the line caps. */
const PROJECTILE_TEXTURE_SIZE = Math.ceil(PROJECTILE_TEXTURE_RADIUS * 2.6 * 2) + 4;
/** Shapes that point along the flight direction and rotate with it. */
const DIRECTIONAL_PROJECTILES = new Set([
  "hunter-arrow", "smg-pellet", "spark-bolt", "pistol-round", "coil-spike",
  "venom-dart", "prism-shard", "frost-shard", "ember-orb"
]);

/**
 * Texture for one projectile look, drawn once with the same vector routine the
 * graphics layer used every frame before.
 *
 * Projectiles are the most numerous objects on screen. As vector paths they
 * were rebuilt into the entity graphics on every state and replayed by the
 * Canvas renderer on every frame, glows and all; as images they are one
 * `drawImage` each.
 */
function ensureProjectileTexture(
  scene: Phaser.Scene,
  projectile: ArenaSurvivorState["projectiles"][number]
): string {
  const key = `arena-survivor-projectile-${projectile.definitionId}-${projectile.ownerKind}`;

  if (scene.textures.exists(key)) {
    return key;
  }

  const graphics = scene.make.graphics({}, false);
  const center = PROJECTILE_TEXTURE_SIZE / 2;
  drawArenaSurvivorProjectile(
    graphics,
    { ...projectile, vx: 1, vy: 0 },
    center,
    center,
    PROJECTILE_TEXTURE_RADIUS
  );
  graphics.generateTexture(key, PROJECTILE_TEXTURE_SIZE, PROJECTILE_TEXTURE_SIZE);
  graphics.destroy();
  return key;
}

function syncProjectileSprites(
  scene: Phaser.Scene,
  layer: ArenaSurvivorSpriteLayer,
  state: ArenaSurvivorState
): void {
  const activeIds = new Set<string>();

  for (const projectile of state.projectiles) {
    if (!projectile.alive) {
      continue;
    }

    const textureKey = ensureProjectileTexture(scene, projectile);
    let sprite = layer.projectileSprites.get(projectile.id);

    if (!sprite) {
      sprite = acquirePooledImage(scene, layer, textureKey, 1);
      layer.projectileSprites.set(projectile.id, sprite);
    } else if (sprite.texture.key !== textureKey) {
      sprite.setTexture(textureKey);
    }

    const scale = Math.max(2.5, projectile.radius) / PROJECTILE_TEXTURE_RADIUS;
    sprite.setPosition(projectile.x, projectile.y);
    sprite.setScale(scale);
    sprite.setRotation(
      DIRECTIONAL_PROJECTILES.has(projectile.definitionId)
        ? resolveProjectileAngle(projectile.vx, projectile.vy)
        : projectile.definitionId === "gear-shot" ? state.elapsedMs / 110 : 0
    );
    activeIds.add(projectile.id);
  }

  releasePooledImages(layer, layer.projectileSprites, activeIds);
}

function createMarshmallowPlayerRig(
  scene: Phaser.Scene,
  torsoKey: string,
  headgearKey: string
): ArenaSurvivorMarshmallowPlayerRig {
  for (const textureKey of [
    torsoKey,
    headgearKey,
    arenaSurvivorMarshmallowRigKeys.hand,
    arenaSurvivorMarshmallowRigKeys.foot
  ]) {
    scene.textures.get(textureKey).setFilter(Phaser.Textures.FilterMode.LINEAR);
  }
  const leftFoot = scene.add.image(0, 0, arenaSurvivorMarshmallowRigKeys.foot).setOrigin(0.5);
  const rightFoot = scene.add.image(0, 0, arenaSurvivorMarshmallowRigKeys.foot).setOrigin(0.5);
  const torso = scene.add.image(0, 0, torsoKey).setOrigin(0.5);
  const face = scene.add.graphics();
  const headgear = scene.add.image(0, 0, headgearKey).setOrigin(0.5);
  const leftHand = scene.add.image(0, 0, arenaSurvivorMarshmallowRigKeys.hand).setOrigin(0.5);
  const rightHand = scene.add.image(0, 0, arenaSurvivorMarshmallowRigKeys.hand).setOrigin(0.5);
  const container = scene.add.container(0, 0, [
    leftFoot,
    rightFoot,
    torso,
    face,
    headgear,
    leftHand,
    rightHand
  ]);
  container.setDepth(10);
  return { container, torso, leftHand, rightHand, leftFoot, rightFoot, face, headgear };
}

function drawMarshmallowFace(
  graphics: Phaser.GameObjects.Graphics,
  bodyX: number,
  bodyBottom: number,
  scaleX: number,
  scaleY: number,
  shearX: number,
  gazeX: number,
  gazeY: number,
  elapsedMs: number
): void {
  const eyeCenterY = -306 * 0.62;
  const blinkPhase = elapsedMs % 4_600;
  const blink = blinkPhase > 4_390
    ? Math.max(0.1, Math.abs(blinkPhase - 4_495) / 105)
    : 1;
  graphics.clear();
  graphics.setPosition(bodyX, bodyBottom);
  graphics.setScale(scaleX, scaleY);
  graphics.setRotation(shearX * 0.35);

  for (const direction of [-1, 1]) {
    const eyeX = direction * 49;
    graphics.fillStyle(0xfffae1, 0.96);
    graphics.lineStyle(3, 0x532d1f, 0.92);
    graphics.fillEllipse(eyeX, eyeCenterY, 44, Math.max(4, 54 * blink));
    graphics.strokeEllipse(eyeX, eyeCenterY, 44, Math.max(4, 54 * blink));
    if (blink > 0.26) {
      graphics.fillStyle(0x382119, 1);
      graphics.fillEllipse(eyeX + gazeX, eyeCenterY + gazeY, 20, 30);
      graphics.fillStyle(0xffffff, 0.96);
      graphics.fillCircle(eyeX + gazeX - 3, eyeCenterY + gazeY - 5, 3.2);
    }
  }
  graphics.lineStyle(3.2, 0x633528, 1);
  graphics.beginPath();
  graphics.arc(0, eyeCenterY + 30, 24, 0.2, Math.PI - 0.2);
  graphics.strokePath();
}

function updateMarshmallowPlayerRig(
  rig: ArenaSurvivorMarshmallowPlayerRig,
  player: ArenaSurvivorPlayerState,
  state: ArenaSurvivorState,
  torsoKey: string,
  playerPosition: { x: number; y: number; pulseScale: number },
  nearestEnemy: NearestEnemy | null
): void {
  const displaySize =
    resolvePlayerDisplayRadius(player.radius) *
    2 *
    playerPosition.pulseScale *
    MARSHMALLOW_PLAYER_SCALE;
  const rigScale = displaySize / 380;
  const speed = Math.hypot(player.vx, player.vy);
  const speedRatio = Phaser.Math.Clamp(speed / Math.max(1, player.moveSpeed), 0, 1);
  const moving = speedRatio > 0.035;
  const legMotion = 0.3 + speedRatio * 0.7;
  const cyclePerSecond = moving ? 0.58 + speedRatio * 0.52 : 0.42;
  const phase = (state.elapsedMs / 1000) * Math.PI * 2 * cyclePerSecond;
  const walkDirection = player.vx < -1 ? -1 : 1;
  const step = moving ? Math.sin(phase) * walkDirection : 0;
  const compression = moving ? Math.cos(phase * 2) : Math.sin(phase);
  const warpAmount = (moving ? 0.032 : 0.018) * 0.55;
  const stride = (moving ? step * 25 : Math.sin(phase) * 1.5) * legMotion;
  const walkBounce = moving ? (1 - Math.cos(phase * 2)) * 2.2 : 0;
  const idleBob = moving ? 0 : Math.sin(phase) * 0.8;
  const bodyX = moving ? step * 2.2 : 0;
  const bodyBottom = 150 - 0.1 * 145 - walkBounce - idleBob;
  const scaleX = 1 + compression * warpAmount;
  const scaleY = 1 - compression * warpAmount * 0.92;
  const shearX = (moving ? step * 0.012 : Math.sin(phase) * 0.004) * 0.55;
  const limbDistance = 0.36 * 162;
  const leftLift = (moving ? Math.max(0, step) * 20 : 0) * legMotion;
  const rightLift = (moving ? Math.max(0, -step) * 20 : 0) * legMotion;
  const footCenterY = 150 - 31 * 1.08 * 1.05;
  const footRotation = step * 0.13 * legMotion;
  const shoulderY = 150 - (155 + 0.16 * 92);
  const armGap = (0.45 / 0.5) * 184;
  const armSwing = moving ? Math.sin(phase) * walkDirection : Math.sin(state.elapsedMs * 0.0018) * 0.12;

  rig.container.setVisible(player.alive);
  rig.container.setPosition(playerPosition.x, playerPosition.y);
  rig.container.setScale(rigScale);
  rig.container.setAlpha(player.alive ? 1 : 0.42);
  if (rig.torso.texture.key !== torsoKey) {
    rig.torso.setTexture(torsoKey);
  }
  rig.torso.setPosition(bodyX, bodyBottom - 153);
  rig.torso.setDisplaySize(330 * scaleX, 306 * scaleY);
  rig.torso.setRotation(shearX * 0.35);

  rig.leftFoot.setPosition(bodyX - limbDistance + stride, footCenterY - leftLift);
  rig.rightFoot.setPosition(bodyX + limbDistance - stride, footCenterY - rightLift);
  rig.leftFoot.setDisplaySize(86, 62).setRotation(footRotation);
  rig.rightFoot.setDisplaySize(86, 62).setRotation(-footRotation);

  rig.leftHand.setPosition(bodyX - armGap, shoulderY + armSwing * 15);
  rig.rightHand.setPosition(bodyX + armGap, shoulderY - armSwing * 15);
  rig.leftHand.setDisplaySize(58, 46).setRotation(-0.2 + armSwing * 0.22);
  rig.rightHand.setDisplaySize(58, 46).setRotation(0.2 + armSwing * 0.22);

  const targetX = nearestEnemy?.x ?? player.x + Math.cos(player.facingAngleRad) * 100;
  const targetY = nearestEnemy?.y ?? player.y + Math.sin(player.facingAngleRad) * 100;
  const targetAngle = Math.atan2(targetY - player.y, targetX - player.x);
  drawMarshmallowFace(
    rig.face,
    bodyX,
    bodyBottom,
    scaleX,
    scaleY,
    shearX,
    Math.cos(targetAngle) * 11,
    Math.sin(targetAngle) * 8,
    state.elapsedMs
  );

  const wearsHelmet = player.character.id === "sir-pampel-panzer";
  const headgearKey = wearsHelmet
    ? arenaSurvivorMarshmallowRigKeys.helmet
    : resolveArenaSurvivorMarshmallowHeadbandKey(player.color);
  if (rig.headgear.texture.key !== headgearKey) {
    rig.headgear.setTexture(headgearKey);
  }
  if (wearsHelmet) {
    rig.headgear.setPosition(bodyX, bodyBottom - 306 + 113.5 - 101);
    rig.headgear.setDisplaySize(360 * scaleX, 202 * scaleY);
  } else {
    rig.headgear.setPosition(bodyX, bodyBottom - 306 + 47.5 + 37);
    rig.headgear.setDisplaySize(410 * scaleX, 74 * scaleY);
  }
  rig.headgear.setRotation(shearX * 0.35);
}

export function drawArenaSurvivorBackground(
  scene: Phaser.Scene,
  graphics: Phaser.GameObjects.Graphics,
  state: ArenaSurvivorState,
  meta: ArenaSurvivorRenderMeta = resolveArenaSurvivorRenderMeta(scene, state)
): void {
  void scene;
  void state;
  graphics.clear();
  graphics.lineStyle(3, 0xe3c59d, 0.12);
  graphics.strokeRoundedRect(0, 0, meta.arenaWidth, meta.arenaHeight, 18);
}

export function drawArenaSurvivorPlayerHealthBars(
  graphics: Phaser.GameObjects.Graphics,
  state: ArenaSurvivorState
): void {
  graphics.clear();

  for (const player of state.players) {
    if (!player.alive) {
      continue;
    }

    const playerPosition = resolvePlayerVisualPosition(player, state.elapsedMs);
    const displayRadius =
      resolvePlayerDisplayRadius(Math.max(10, player.radius)) *
      playerPosition.pulseScale *
      (state.visualTheme === "marshmallow-mayhem" ? MARSHMALLOW_PLAYER_SCALE : 1);
    const hpRatio = player.maxHp > 0
      ? Phaser.Math.Clamp(player.hp / player.maxHp, 0, 1)
      : 0;

    if (hpRatio >= 0.999) {
      continue;
    }

    const width = Phaser.Math.Clamp(displayRadius * 1.55, 28, 50);
    const height = 4;
    const left = playerPosition.x - width / 2;
    const top = playerPosition.y - displayRadius - 5;
    const fillColor = hpRatio > 0.5 ? 0x22c55e : hpRatio > 0.25 ? 0xf59e0b : 0xef4444;

    graphics.fillStyle(0x020617, 0.88);
    graphics.fillRoundedRect(left - 1, top - 1, width + 2, height + 2, 3);
    graphics.fillStyle(0x1e293b, 0.96);
    graphics.fillRoundedRect(left, top, width, height, 2);

    if (hpRatio > 0) {
      graphics.fillStyle(fillColor, 1);
      graphics.fillRoundedRect(left, top, width * hpRatio, height, 2);
    }

    graphics.lineStyle(1, 0xe2e8f0, 0.55);
    graphics.strokeRoundedRect(left, top, width, height, 2);
  }
}

export function drawArenaSurvivorEntities(
  scene: Phaser.Scene,
  graphics: Phaser.GameObjects.Graphics,
  state: ArenaSurvivorState,
  meta: ArenaSurvivorRenderMeta = resolveArenaSurvivorRenderMeta(scene, state)
): void {
  void meta;
  graphics.clear();

  if (state.visualTheme === "obsidian-relay") {
    const relayPulse = 0.45 + Math.sin(state.elapsedMs / 520) * 0.18;
    const scanAngle = state.elapsedMs / 2400;
    const relayNodes = [
      { x: 150, y: 190 },
      { x: 1130, y: 190 },
      { x: 150, y: 530 },
      { x: 1130, y: 530 }
    ];

    graphics.lineStyle(2, 0x67e8f9, relayPulse);
    for (const node of relayNodes) {
      graphics.strokeCircle(node.x, node.y, 12 + relayPulse * 8);
    }

    graphics.lineStyle(2, 0xc87545, 0.22);
    graphics.strokeCircle(state.arenaWidth / 2, state.arenaHeight / 2, 116 + Math.sin(state.elapsedMs / 760) * 7);
    graphics.lineStyle(2, 0x67e8f9, 0.16);
    graphics.lineBetween(
      state.arenaWidth / 2,
      state.arenaHeight / 2,
      state.arenaWidth / 2 + Math.cos(scanAngle) * 104,
      state.arenaHeight / 2 + Math.sin(scanAngle) * 104
    );
  }

  // Projectiles are pooled images now, see `syncProjectileSprites`.
  const nearestEnemyByPlayer = resolveNearestEnemyByPlayer(state);

  for (const indicator of state.spawnIndicators) {
    drawSpawnIndicator(graphics, indicator, state.elapsedMs);
  }

  for (const pickup of state.pickups) {
    if (resolveArenaSurvivorPickupSpriteKey(pickup.kind, state.visualTheme)) {
      continue;
    }

    if (pickup.kind === "health") {
      drawHealthPickup(graphics, pickup);
      continue;
    }

    drawMaterialPickup(graphics, pickup);
  }

  for (const player of state.players) {
    const playerPosition = resolvePlayerVisualPosition(player, state.elapsedMs);
    const playerX = playerPosition.x;
    const playerY = playerPosition.y;
    const playerRadius = Math.max(10, player.radius);
    const playerDisplayRadius = resolvePlayerDisplayRadius(playerRadius) * playerPosition.pulseScale;
    const slotDistance = resolveArenaSurvivorWeaponOrbitDistance(player.radius);
    const occupiedSlots = player.loadout.weapons;

    for (let slotIndex = 0; slotIndex < ARENA_SURVIVOR_MAX_WEAPON_SLOTS; slotIndex += 1) {
      const equippedWeapon = occupiedSlots[slotIndex];

      if (
        equippedWeapon &&
        !resolveArenaSurvivorWeaponCarrySpriteKey(equippedWeapon.weaponId, state.visualTheme)
      ) {
        const weaponDisplaySize = resolveWeaponDisplaySize(player, slotIndex);
        const weaponPose = resolveWeaponPose(player, state, slotIndex, slotDistance, {
          x: playerX,
          y: playerY
        }, weaponDisplaySize, nearestEnemyByPlayer.get(player.playerId) ?? null);
        const fallbackTipLength = Math.max(8, weaponDisplaySize * 0.4);
        const fallbackHalfWidth = Math.max(5, weaponDisplaySize * 0.25);
        graphics.fillStyle(resolveWeaponColor(equippedWeapon.category), 0.95);
        graphics.fillTriangle(
          weaponPose.x + Math.cos(weaponPose.aimAngle) * fallbackTipLength,
          weaponPose.y + Math.sin(weaponPose.aimAngle) * fallbackTipLength,
          weaponPose.x + Math.cos(weaponPose.aimAngle + 2.4) * fallbackHalfWidth,
          weaponPose.y + Math.sin(weaponPose.aimAngle + 2.4) * fallbackHalfWidth,
          weaponPose.x + Math.cos(weaponPose.aimAngle - 2.4) * fallbackHalfWidth,
          weaponPose.y + Math.sin(weaponPose.aimAngle - 2.4) * fallbackHalfWidth
        );
      }
    }

    if (!scene.textures.exists(resolveArenaSurvivorPlayerSpriteKey(player.character.id, state.visualTheme))) {
      // Fallback token on the arena floor — artwork, so a fixed grey rather
      // than the room's muted ink, which is dark on a dark stage in light mode.
      const playerColor = player.alive ? toColor(player.color) : toColor(DOWNED_PLAYER_COLOR);

      graphics.fillStyle(0x020617, 0.35);
      graphics.fillEllipse(playerX, playerY + playerDisplayRadius * 0.92, playerDisplayRadius * 1.45, playerDisplayRadius * 0.46);
      graphics.fillStyle(playerColor, player.alive ? 1 : 0.45);
      graphics.fillCircle(playerX, playerY, playerDisplayRadius);
      graphics.lineStyle(3, 0xe2e8f0, player.alive ? 0.9 : 0.4);
      graphics.strokeCircle(playerX, playerY, playerDisplayRadius);
    }
  }

  for (const enemy of state.enemies) {
    if (!enemy.alive) {
      continue;
    }

    const spriteKey = resolveArenaSurvivorEnemySpriteKey(enemy.definitionId, state.visualTheme);
    if (spriteKey && scene.textures.exists(spriteKey)) {
      continue;
    }

    const enemyColor = enemy.role === "shooter" ? 0xfb7185 : enemy.role === "brute" ? 0xf97316 : 0xf59e0b;
    const px = enemy.x;
    const py = enemy.y;
    const radius = Math.max(8, enemy.radius);
    const displayRadius =
      resolveEnemyDisplayRadius(radius, enemy.definitionId) *
      resolveEnemyPulseScale(enemy.id, state.elapsedMs);
    const hpRatio = enemy.maxHp > 0 ? Math.max(0, Math.min(1, enemy.hp / enemy.maxHp)) : 0;

    graphics.fillStyle(enemyColor, 0.8);
    graphics.fillCircle(px, py, displayRadius);
    graphics.lineStyle(2, 0x1f2937, 0.8);
    graphics.strokeCircle(px, py, displayRadius);
    graphics.lineStyle(Math.max(2, displayRadius * 0.12), 0xfef3c7, 0.9 * hpRatio);
    graphics.strokeCircle(px, py, displayRadius + 4);
  }
}

export function syncArenaSurvivorSpriteLayer(
  scene: Phaser.Scene,
  layer: ArenaSurvivorSpriteLayer,
  state: ArenaSurvivorState,
  meta: ArenaSurvivorRenderMeta = resolveArenaSurvivorRenderMeta(scene, state)
): void {
  void meta;
  const activePlayerIds = new Set(state.players.map((player) => player.playerId));
  const nearestEnemyByPlayer = resolveNearestEnemyByPlayer(state);

  syncProjectileSprites(scene, layer, state);

  for (const player of state.players) {
    const spriteKey = resolveArenaSurvivorPlayerSpriteKey(player.character.id, state.visualTheme);
    let playerSprite = layer.playerSprites.get(player.playerId);
    const playerPosition = resolvePlayerVisualPosition(player, state.elapsedMs);

    if (state.visualTheme === "marshmallow-mayhem") {
      if (playerSprite) {
        playerSprite.setVisible(false);
      }
      let rig = layer.marshmallowPlayerRigs.get(player.playerId);
      const wearsHelmet = player.character.id === "sir-pampel-panzer";
      const headgearKey = wearsHelmet
        ? arenaSurvivorMarshmallowRigKeys.helmet
        : resolveArenaSurvivorMarshmallowHeadbandKey(player.color);
      if (!rig) {
        // The rig's hands, feet and headgear are created once; building it
        // before the theme's textures are in would leave them blank for good.
        if (
          !scene.textures.exists(spriteKey) ||
          !scene.textures.exists(headgearKey) ||
          !scene.textures.exists(arenaSurvivorMarshmallowRigKeys.hand) ||
          !scene.textures.exists(arenaSurvivorMarshmallowRigKeys.foot)
        ) {
          continue;
        }

        rig = createMarshmallowPlayerRig(scene, spriteKey, headgearKey);
        layer.marshmallowPlayerRigs.set(player.playerId, rig);
      }
      updateMarshmallowPlayerRig(
        rig,
        player,
        state,
        spriteKey,
        playerPosition,
        nearestEnemyByPlayer.get(player.playerId) ?? null
      );
      continue;
    }

    const marshmallowRig = layer.marshmallowPlayerRigs.get(player.playerId);
    if (marshmallowRig) {
      marshmallowRig.container.destroy(true);
      layer.marshmallowPlayerRigs.delete(player.playerId);
    }

    if (!scene.textures.exists(spriteKey)) {
      if (playerSprite) {
        playerSprite.setVisible(false);
      }
      continue;
    }

    if (!playerSprite) {
      playerSprite = scene.add.image(0, 0, spriteKey);
      playerSprite.setDepth(10);
      playerSprite.setOrigin(0.5);
      layer.playerSprites.set(player.playerId, playerSprite);
    } else if (playerSprite.texture.key !== spriteKey) {
      playerSprite.setTexture(spriteKey);
    }

    playerSprite.setVisible(player.alive);
    playerSprite.setPosition(playerPosition.x, playerPosition.y);
    const displaySize = resolvePlayerDisplayRadius(player.radius) * 2 * playerPosition.pulseScale;
    playerSprite.setDisplaySize(displaySize, displaySize);
    playerSprite.setRotation(0);
    playerSprite.setAlpha(player.alive ? 1 : 0.42);
  }

  for (const [playerId, playerSprite] of layer.playerSprites) {
    if (!activePlayerIds.has(playerId)) {
      playerSprite.destroy();
      layer.playerSprites.delete(playerId);
    }
  }

  for (const [playerId, rig] of layer.marshmallowPlayerRigs) {
    if (!activePlayerIds.has(playerId)) {
      rig.container.destroy(true);
      layer.marshmallowPlayerRigs.delete(playerId);
    }
  }

  const visibleEnemies = state.enemies.filter(enemy => enemy.alive && (!state.survival || nearCamera(scene, enemy)));
  const activeEnemyIds = new Set(visibleEnemies.map(enemy => enemy.id));
  const activePickupIds = new Set<string>();
  const activeWeaponKeys = new Set<string>();

  for (const pickup of state.pickups) {
    const spriteKey = resolveArenaSurvivorPickupSpriteKey(pickup.kind, state.visualTheme);

    if (!spriteKey || !scene.textures.exists(spriteKey)) {
      continue;
    }

    let pickupSprite = layer.pickupSprites.get(pickup.id);

    if (!pickupSprite) {
      pickupSprite = acquirePooledImage(scene, layer, spriteKey, 8);
      layer.pickupSprites.set(pickup.id, pickupSprite);
    } else if (pickupSprite.texture.key !== spriteKey) {
      pickupSprite.setTexture(spriteKey);
    }

    const pulse = 0.94 + Math.sin(pickup.ageMs / 150) * 0.08;
    const displaySize = Math.max(pickup.kind === "health" ? 30 : 26, pickup.radius * 2.8) * pulse;
    pickupSprite.setVisible(true);
    pickupSprite.setPosition(pickup.x, pickup.y);
    pickupSprite.setDisplaySize(displaySize, displaySize);
    pickupSprite.setAlpha(0.98);
    activePickupIds.add(pickup.id);
  }

  releasePooledImages(layer, layer.pickupSprites, activePickupIds);

  for (const enemy of visibleEnemies) {
    if (!enemy.alive) {
      continue;
    }

    const spriteKey = resolveArenaSurvivorEnemySpriteKey(enemy.definitionId, state.visualTheme);

    if (!spriteKey || !scene.textures.exists(spriteKey)) {
      continue;
    }

    let enemySprite = layer.enemySprites.get(enemy.id);

    if (!enemySprite) {
      enemySprite = acquirePooledImage(scene, layer, spriteKey, 9);
      layer.enemySprites.set(enemy.id, enemySprite);
    } else if (enemySprite.texture.key !== spriteKey) {
      enemySprite.setTexture(spriteKey);
    }

    const displaySize =
      resolveEnemyDisplayRadius(enemy.radius, enemy.definitionId) *
      2 *
      resolveEnemyPulseScale(enemy.id, state.elapsedMs);

    enemySprite.setVisible(true);
    enemySprite.setPosition(enemy.x, enemy.y);
    enemySprite.setDisplaySize(displaySize, displaySize);
    enemySprite.setFlipX(enemy.vx < -1);
    enemySprite.setAlpha(0.92);
  }

  for (const player of state.players) {
    const playerPosition = resolvePlayerVisualPosition(player, state.elapsedMs);
    const orbitDistance = resolveArenaSurvivorWeaponOrbitDistance(player.radius);

    for (let slotIndex = 0; slotIndex < ARENA_SURVIVOR_MAX_WEAPON_SLOTS; slotIndex += 1) {
      const equippedWeapon = player.loadout.weapons[slotIndex];
      const spriteId = `${player.playerId}:${slotIndex}`;
      const weaponSprite = layer.weaponSprites.get(spriteId);

      if (!equippedWeapon) {
        if (weaponSprite) {
          weaponSprite.destroy();
          layer.weaponSprites.delete(spriteId);
        }
        continue;
      }

      const spriteKey = resolveArenaSurvivorWeaponCarrySpriteKey(
        equippedWeapon.weaponId,
        state.visualTheme
      );

      if (!spriteKey || !scene.textures.exists(spriteKey)) {
        if (weaponSprite) {
          weaponSprite.destroy();
          layer.weaponSprites.delete(spriteId);
        }
        continue;
      }

      const displaySize =
        resolveWeaponDisplaySize(player, slotIndex) *
        (state.visualTheme === "marshmallow-mayhem" ? MARSHMALLOW_WEAPON_SCALE : 1);
      const weaponPose = equippedWeapon.evolved && equippedWeapon.category === "melee" ? evolvedOrbit(player.x, player.y, state.elapsedMs, slotIndex) : resolveWeaponPose(
        player,
        state,
        slotIndex,
        orbitDistance,
        playerPosition,
        displaySize,
        nearestEnemyByPlayer.get(player.playerId) ?? null
      );
      let nextWeaponSprite = weaponSprite;

      if (!nextWeaponSprite) {
        nextWeaponSprite = scene.add.image(0, 0, spriteKey);
        nextWeaponSprite.setDepth(11);
        nextWeaponSprite.setOrigin(0.5);
        layer.weaponSprites.set(spriteId, nextWeaponSprite);
      } else if (nextWeaponSprite.texture.key !== spriteKey) {
        nextWeaponSprite.setTexture(spriteKey);
      }

      nextWeaponSprite.setVisible(player.alive);
      nextWeaponSprite.setPosition(weaponPose.x, weaponPose.y);
      nextWeaponSprite.setDisplaySize(displaySize, displaySize);
      if (equippedWeapon.evolved) {
        nextWeaponSprite.setDisplaySize(displaySize * 1.25, displaySize * 1.25);
        nextWeaponSprite.setTint(0xc4b5fd);
      } else {
        nextWeaponSprite.clearTint();
      }
      const mirrorRangedWeapon =
        equippedWeapon.category === "ranged" &&
        Math.cos(weaponPose.aimAngle) < 0;
      nextWeaponSprite.setFlipY(mirrorRangedWeapon);
      // The bow carry SVGs already contain their own -90° artwork rotation.
      // Applying the generic weapon offset here rotated the bow a second time,
      // making its limbs and arrow point across the actual firing direction.
      const weaponAlreadyHasDirectionalBasis = equippedWeapon.weaponId === "hunter-bow";
      nextWeaponSprite.setRotation(
        weaponAlreadyHasDirectionalBasis
          ? weaponPose.aimAngle
          : mirrorRangedWeapon
          ? weaponPose.aimAngle - Math.PI / 2
          : weaponPose.aimAngle + Math.PI / 2
      );
      nextWeaponSprite.setAlpha(player.alive ? 0.98 : 0.38);
      activeWeaponKeys.add(spriteId);
    }
  }

  releasePooledImages(layer, layer.enemySprites, activeEnemyIds);

  for (const [weaponId, weaponSprite] of layer.weaponSprites) {
    if (!activeWeaponKeys.has(weaponId)) {
      weaponSprite.destroy();
      layer.weaponSprites.delete(weaponId);
    }
  }
}
