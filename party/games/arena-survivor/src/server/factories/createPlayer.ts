import type { GamePlayerSummary } from "@open-party-lab/game-core";
import type { ArenaSurvivorLoadoutState } from "../../protocol.js";
import type {
  ArenaSurvivorPlayerCarryState,
  ArenaSurvivorRuntimePlayerState
} from "../arenaSurvivorState.js";
import { arenaSurvivorPlayerDefinition } from "../definitions/playerDefinitions.js";
import {
  createArenaSurvivorStarterLoadout,
  createArenaSurvivorWeaponRuntimeStates,
  resolveArenaSurvivorCharacter,
  resolveArenaSurvivorPlayerStats
} from "../loadout/arenaSurvivorLoadout.js";
import {
  cloneLoadout,
  createEmptyRunSummary,
  cloneRunSummary,
  createEmptyRunStats,
  createEmptyShopState
} from "../arenaSurvivorState.js";
import { resolveArenaSurvivorExperienceToNextLevel } from "../progression/arenaSurvivorProgression.js";

export interface ArenaSurvivorSpawnPoint {
  x: number;
  y: number;
  facingAngleRad?: number;
}

export function createArenaSurvivorPlayer(
  player: GamePlayerSummary,
  spawnPoint: ArenaSurvivorSpawnPoint,
  now: number,
  carry?: ArenaSurvivorPlayerCarryState,
  loadout: ArenaSurvivorLoadoutState = carry?.loadout ?? createArenaSurvivorStarterLoadout(carry?.characterId),
  characterId?: string,
  playerIndex = 0
): ArenaSurvivorRuntimePlayerState {
  const character = resolveArenaSurvivorCharacter(characterId ?? carry?.characterId, playerIndex);
  const levelBonusModifiers = carry?.levelBonusModifiers?.map((modifiers) => ({ ...modifiers })) ?? [];
  const playerStats = resolveArenaSurvivorPlayerStats(loadout, character.id, levelBonusModifiers);
  const level = Math.max(1, Math.floor(carry?.level ?? 1));

  return {
    playerId: player.id,
    name: player.name,
    color: player.color,
    character,
    x: spawnPoint.x,
    y: spawnPoint.y,
    vx: 0,
    vy: 0,
    radius: arenaSurvivorPlayerDefinition.radius,
    moveSpeed: playerStats.moveSpeed,
    hp: playerStats.maxHp,
    maxHp: playerStats.maxHp,
    alive: true,
    facingAngleRad: spawnPoint.facingAngleRad ?? -Math.PI / 2,
    invulnerableUntilMs: now + 600,
    weaponRuntimeStates: createArenaSurvivorWeaponRuntimeStates(loadout),
    stats: playerStats,
    level,
    experience: Math.max(0, carry?.experience ?? 0),
    experienceToNextLevel: resolveArenaSurvivorExperienceToNextLevel(level),
    pendingLevelUpChoices: Math.max(0, Math.floor(carry?.pendingLevelUpChoices ?? 0)),
    levelBonusModifiers,
    materials: carry?.materials ?? 0,
    loadout: cloneLoadout(loadout),
    shop: createEmptyShopState(),
    runStats: createEmptyRunStats(),
    runSummary: cloneRunSummary(carry?.runSummary ?? createEmptyRunSummary()),
    moveInputX: 0,
    moveInputY: 0
  };
}
