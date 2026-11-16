import { arenaSurvivorConfig } from "../arenaSurvivorConfig.js";
import type {
  ArenaSurvivorRuntimeEnemyState,
  ArenaSurvivorRuntimePlayerState
} from "../arenaSurvivorState.js";
import { arenaSurvivorEnemyDefinitionsById } from "../definitions/enemyDefinitions.js";

export function resolveArenaSurvivorExperienceToNextLevel(level: number): number {
  const safeLevel = Math.max(1, Math.floor(level));
  return Math.max(
    1,
    Math.round(
      arenaSurvivorConfig.experienceToNextLevelBase +
        arenaSurvivorConfig.experienceToNextLevelGrowth * (safeLevel - 1)
    )
  );
}

export function resolveArenaSurvivorEnemyExperienceReward(definitionId: string): number {
  const definition = arenaSurvivorEnemyDefinitionsById[definitionId];

  if (!definition) {
    return arenaSurvivorConfig.experienceBaseReward;
  }

  const baseReward =
    arenaSurvivorConfig.experienceBaseReward +
    Math.floor(Math.max(0, definition.minWave - 1) / 4) *
      arenaSurvivorConfig.experienceRewardPerUnlockWave;
  const bossMultiplier = (definition.tags as readonly string[]).includes("boss")
    ? arenaSurvivorConfig.bossExperienceMultiplier
    : 1;

  return Math.max(1, Math.round(baseReward * bossMultiplier));
}

export function awardArenaSurvivorEnemyExperience(
  player: ArenaSurvivorRuntimePlayerState,
  enemy: Pick<ArenaSurvivorRuntimeEnemyState, "definitionId">
): ArenaSurvivorRuntimePlayerState {
  let level = Math.max(1, Math.floor(player.level));
  let experience = Math.max(0, player.experience) +
    resolveArenaSurvivorEnemyExperienceReward(enemy.definitionId);
  let experienceToNextLevel = resolveArenaSurvivorExperienceToNextLevel(level);
  let levelsGained = 0;

  while (experience >= experienceToNextLevel) {
    experience -= experienceToNextLevel;
    level += 1;
    levelsGained += 1;
    experienceToNextLevel = resolveArenaSurvivorExperienceToNextLevel(level);
  }

  return {
    ...player,
    level,
    experience,
    experienceToNextLevel,
    pendingLevelUpChoices: player.pendingLevelUpChoices + levelsGained
  };
}
