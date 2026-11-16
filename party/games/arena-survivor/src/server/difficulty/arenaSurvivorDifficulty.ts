import {
  arenaSurvivorSetupConfig,
  resolveArenaSurvivorDifficultyTier
} from "../../protocol.js";
import { arenaSurvivorConfig } from "../arenaSurvivorConfig.js";
import { arenaSurvivorBalance } from "../arenaSurvivorBalance.js";

export interface ArenaSurvivorDifficultyState {
  level: number;
  difficultyTier: number;
  playerCountMultiplier: number;
  enemySpawnIntervalMs: number;
  maxEnemiesOnScreen: number;
  pickupValue: number;
  enemyHpMultiplier: number;
  enemyDamageMultiplier: number;
  enemySpeedMultiplier: number;
  enemyUnlockWaveBonus: number;
  spawnBurstBonus: number;
  bossSpawnCount: number;
  bossHpMultiplier: number;
}

export function resolveArenaSurvivorPlayerCountMultiplier(playerCount: number): number {
  return 1 + Math.max(0, playerCount - 1) * 0.45;
}

export function resolveArenaSurvivorDifficulty(
  roundNumber: number,
  playerCount = 1,
  difficultyTier: number = arenaSurvivorSetupConfig.difficulty.defaultValue
): ArenaSurvivorDifficultyState {
  const level = Math.max(1, roundNumber);
  const spawnIntervalReduction = (level - 1) * arenaSurvivorConfig.difficultySpawnIntervalReductionMs;
  const maxEnemiesBonus = (level - 1) * arenaSurvivorConfig.difficultyMaxEnemiesBonusPerRound;
  const playerCountMultiplier = resolveArenaSurvivorPlayerCountMultiplier(playerCount);
  const resolvedTier = resolveArenaSurvivorDifficultyTier(difficultyTier);
  const baseSpawnInterval = Math.max(
    200,
    arenaSurvivorConfig.enemySpawnIntervalMs - spawnIntervalReduction
  );

  return {
    level,
    difficultyTier: resolvedTier.level,
    playerCountMultiplier,
    enemySpawnIntervalMs: Math.max(
      200,
      Math.round(
        (baseSpawnInterval * resolvedTier.spawnIntervalMultiplier) / playerCountMultiplier
      )
    ),
    maxEnemiesOnScreen: Math.min(
      arenaSurvivorConfig.maxActiveEnemies,
      Math.max(
        8,
        arenaSurvivorConfig.baseMaxEnemiesOnScreen +
          Math.min(48, maxEnemiesBonus) +
          resolvedTier.maxEnemyCountBonus
      )
    ),
    pickupValue: arenaSurvivorConfig.pickupValueBase,
    enemyHpMultiplier: resolvedTier.enemyHpMultiplier *
      (1 + (level - 1) * arenaSurvivorBalance.enemyHpPerWave) * (1 + Math.max(0, playerCount - 1) * 0.3),
    enemyDamageMultiplier: resolvedTier.enemyDamageMultiplier * (1 + (level - 1) * arenaSurvivorBalance.enemyDamagePerWave),
    enemySpeedMultiplier: resolvedTier.enemySpeedMultiplier *
      (1 + Math.min(arenaSurvivorBalance.maxEnemySpeedGrowth, (level - 1) * arenaSurvivorBalance.enemySpeedPerWave)),
    enemyUnlockWaveBonus: resolvedTier.enemyUnlockWaveBonus,
    spawnBurstBonus: resolvedTier.spawnBurstBonus,
    bossSpawnCount: resolvedTier.bossSpawnCount,
    bossHpMultiplier: resolvedTier.bossHpMultiplier
  };
}

export function resolveArenaSurvivorSurvivalDifficulty(elapsedMs: number, playerCount: number, tier: number) {
  const minutes = Math.max(0, elapsedMs) / 60_000;
  const difficulty = resolveArenaSurvivorDifficulty(1 + Math.floor(minutes), playerCount, tier);
  const tierConfig = resolveArenaSurvivorDifficultyTier(tier);
  difficulty.maxEnemiesOnScreen = Math.min(arenaSurvivorBalance.survivalEnemyLimit,
    Math.max(12, Math.round((28 + minutes * 5) * (1 + Math.max(0, playerCount - 1) * 0.15) + tierConfig.maxEnemyCountBonus)));
  difficulty.enemySpawnIntervalMs = Math.max(200,
    (850 - Math.min(500, minutes * 30)) * tierConfig.spawnIntervalMultiplier / difficulty.playerCountMultiplier);
  return difficulty;
}
