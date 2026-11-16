import { localizeArenaSurvivorPlayer, localizeArenaSurvivorPresentation } from "../contentText.js";
import {
  createBaseRoundState,
  resolveRoundPhaseTimings,
  transitionRoundState,
  type GamePlayerSummary,
  type ScoreEntry,
  type ServerGame,
  type ServerGameContext
} from "@open-party-lab/game-core";
import type {
  ArenaSurvivorHostAction,
  ArenaSurvivorEnemyRenderState,
  ArenaSurvivorInput,
  ArenaSurvivorPickupState,
  ArenaSurvivorPlayerState,
  ArenaSurvivorProjectileRenderState
} from "../protocol.js";
import { arenaSurvivorSetupConfig } from "../protocol.js";
import { arenaSurvivorManifest } from "../manifest.js";
import {
  arenaSurvivorConfig,
  arenaSurvivorRoomSettingKeys
} from "./arenaSurvivorConfig.js";
import { resolveArenaSurvivorDifficulty } from "./difficulty/arenaSurvivorDifficulty.js";
import { createArenaSurvivorPlayer, type ArenaSurvivorSpawnPoint } from "./factories/createPlayer.js";
import {
  createEmptyShopState,
  countAlivePlayers,
  createEmptyRunStats,
  type ArenaSurvivorPublicState,
  type ArenaSurvivorRuntimeEnemyState,
  type ArenaSurvivorRuntimePickupState,
  type ArenaSurvivorRuntimePlayerState,
  type ArenaSurvivorRuntimeProjectileState,
  type ArenaSurvivorRuntimeSpawnIndicatorState,
  type ArenaSurvivorRuntimeState
} from "./arenaSurvivorState.js";
import {
  applyArenaSurvivorShopCombine,
  applyArenaSurvivorShopPurchase,
  applyArenaSurvivorShopReroll,
  applyArenaSurvivorShopSell,
  createArenaSurvivorStarterLoadout,
  resolveArenaSurvivorRunCarry
} from "./loadout/arenaSurvivorLoadout.js";
import { applyAutoFireSystem } from "./systems/autoFireSystem.js";
import { resolveCollisionSystem } from "./systems/collisionSystem.js";
import { applyDamageSystem } from "./systems/damageSystem.js";
import { applyEnemyAiSystem } from "./systems/enemyAiSystem.js";
import { applyMovementSystem } from "./systems/movementSystem.js";
import { applyPickupSystem } from "./systems/pickupSystem.js";
import { applyProjectileSystem } from "./systems/projectileSystem.js";
import { resolveRoundEndSystem } from "./systems/roundEndSystem.js";
import { applySpawnSystem } from "./systems/spawnSystem.js";
import {
  arenaSurvivorDefaultVisualTheme,
  isArenaSurvivorVisualTheme
} from "../visualThemes.js";

const phaseTimings = resolveRoundPhaseTimings(arenaSurvivorManifest.phaseDurations);
import { initializeSurvival, handleSurvivalInput, updateSurvivalPause, advanceSurvivalWorld, resolveSurvivalCombat, survivalDurationMs } from "./survival.js";

function getPlayers(context: { players: GamePlayerSummary[] }): GamePlayerSummary[] {
  if (context.players.length > 0) {
    return context.players;
  }

  return [
    {
      id: "arena-survivor-player",
      name: "Player",
      color: "#22c55e",
      score: 0,
      isReady: true,
      connected: true
    }
  ];
}

function createSpawnPoints(playerCount: number): ArenaSurvivorSpawnPoint[] {
  const width = arenaSurvivorConfig.arenaWidth;
  const height = arenaSurvivorConfig.arenaHeight;

  switch (playerCount) {
    case 1:
      return [{ x: width / 2, y: height / 2, facingAngleRad: -Math.PI / 2 }];
    case 2:
      return [
        { x: width * 0.35, y: height / 2, facingAngleRad: 0 },
        { x: width * 0.65, y: height / 2, facingAngleRad: Math.PI }
      ];
    case 3:
      return [
        { x: width * 0.28, y: height * 0.3, facingAngleRad: Math.PI / 4 },
        { x: width * 0.72, y: height * 0.3, facingAngleRad: (Math.PI * 3) / 4 },
        { x: width / 2, y: height * 0.72, facingAngleRad: -Math.PI / 2 }
      ];
    default:
      return [
        { x: width * 0.28, y: height * 0.28, facingAngleRad: Math.PI / 4 },
        { x: width * 0.72, y: height * 0.28, facingAngleRad: (Math.PI * 3) / 4 },
        { x: width * 0.28, y: height * 0.72, facingAngleRad: -Math.PI / 4 },
        { x: width * 0.72, y: height * 0.72, facingAngleRad: (-Math.PI * 3) / 4 }
      ];
  }
}

function toPublicPickup(pickup: ArenaSurvivorRuntimePickupState): ArenaSurvivorPickupState {
  const {
    spawnedAtMs: _spawnedAtMs,
    targetPlayerId: _targetPlayerId,
    ...publicPickup
  } = pickup;
  return publicPickup;
}

function toPublicSpawnIndicator(
  indicator: ArenaSurvivorRuntimeSpawnIndicatorState
) {
  const {
    definitionId: _definitionId,
    moveSpeed: _moveSpeed,
    maxHp: _maxHp,
    contactDamage: _contactDamage,
    projectileDamageMultiplier: _projectileDamageMultiplier,
    ...publicIndicator
  } = indicator;
  return publicIndicator;
}

function toPublicPlayer(player: ArenaSurvivorRuntimePlayerState, language?: "de" | "en"): ArenaSurvivorPlayerState {
  const { moveInputX: _moveInputX, moveInputY: _moveInputY,
    nextLifeStealAtMs: _nextLifeStealAtMs, ...publicPlayer } = player;
  return localizeArenaSurvivorPlayer(publicPlayer, language);
}

function toPublicEnemy(enemy: ArenaSurvivorRuntimeEnemyState): ArenaSurvivorEnemyRenderState {
  return {
    id: enemy.id,
    definitionId: enemy.definitionId,
    role: enemy.role,
    x: enemy.x,
    y: enemy.y,
    vx: enemy.vx,
    vy: enemy.vy,
    radius: enemy.radius,
    hp: enemy.hp,
    maxHp: enemy.maxHp,
    alive: enemy.alive
  };
}

function toPublicProjectile(
  projectile: ArenaSurvivorRuntimeProjectileState
): ArenaSurvivorProjectileRenderState {
  return {
    id: projectile.id,
    ownerKind: projectile.ownerKind,
    definitionId: projectile.definitionId,
    x: projectile.x,
    y: projectile.y,
    vx: projectile.vx,
    vy: projectile.vy,
    radius: projectile.radius,
    alive: projectile.alive
  };
}

/**
 * What a phone gets while a wave runs.
 *
 * The controller only shows its own player (HUD values, shop, loadout) and never
 * draws the arena. Sending every phone the full enemy, projectile and pickup
 * lists cost Wi-Fi airtime and a large JSON parse on the phone per server tick,
 * which is the same main thread that has to deliver the stick's pointer events.
 * The host still receives the full state through `toPublicState`.
 */
function toSlimControllerState(
  state: ArenaSurvivorRuntimeState,
  players: ArenaSurvivorPlayerState[]
): ArenaSurvivorPublicState {
  return {
    survival: state.survival?.chestReward ? { ...state.survival, chestReward: localizeArenaSurvivorPresentation(state.survival.chestReward, state.language) } : state.survival,
    visualTheme: state.visualTheme,
    arenaWidth: state.arenaWidth,
    arenaHeight: state.arenaHeight,
    waveNumber: state.waveNumber,
    difficultyLevel: state.difficultyLevel,
    elapsedMs: state.elapsedMs,
    remainingMs: state.remainingMs,
    kills: state.kills,
    players,
    difficultyTier: state.difficultyTier,
    enemies: [],
    projectiles: [],
    pickups: [],
    spawnIndicators: [],
    result: {
      ...state.result
    },
    seed: state.seed
  };
}

function buildScore(state: ArenaSurvivorRuntimeState): ScoreEntry[] {
  return state.players.map((player) => ({
    playerId: player.playerId,
    delta:
      state.result.outcome === "survived" || state.survival?.won
        ? Math.max(3, state.waveNumber + player.runStats.kills)
        : Math.max(0, Math.floor(player.runStats.kills / 2)),
    reason: "Arena Survivor"
  }));
}

function createDefaultResult() {
  return {
    outcome: "running" as const,
    title: "Arena Survivor"
  };
}

function resolvePreviousArenaState(
  previousRound: ArenaSurvivorRuntimeState | null
): ArenaSurvivorRuntimeState | null {
  return previousRound;
}

function clampArenaSurvivorDifficultyTier(value: unknown): number {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return arenaSurvivorSetupConfig.difficulty.defaultValue;
  }

  return Math.max(
    arenaSurvivorSetupConfig.difficulty.min,
    Math.min(arenaSurvivorSetupConfig.difficulty.max, Math.round(value))
  );
}

function resolveConfiguredArenaSurvivorDifficultyTier(context: {
  roomSettings: Readonly<Record<string, unknown>>;
}): number {
  return clampArenaSurvivorDifficultyTier(
    context.roomSettings[arenaSurvivorRoomSettingKeys.difficultyTier]
  );
}

function resolveConfiguredArenaSurvivorVisualTheme(context: {
  roomSettings: Readonly<Record<string, unknown>>;
}) {
  const value = context.roomSettings[arenaSurvivorRoomSettingKeys.visualTheme];
  return isArenaSurvivorVisualTheme(value) ? value : arenaSurvivorDefaultVisualTheme;
}

function createPlayersFromCarry(
  gamePlayers: GamePlayerSummary[],
  now: number,
  previousState: ArenaSurvivorRuntimeState | null
): { waveNumber: number; continuedRun: boolean; players: ArenaSurvivorRuntimePlayerState[] } {
  const carry = resolveArenaSurvivorRunCarry(previousState, gamePlayers.map((player) => player.id));
  const spawnPoints = createSpawnPoints(gamePlayers.length);

  return {
    waveNumber: carry.waveNumber,
    continuedRun: carry.continuedRun,
    players: gamePlayers.map((gamePlayer, index) => {
      const carriedPlayer = carry.players.find((entry) => entry.playerId === gamePlayer.id);
      const selectedCharacterId = gamePlayer.selectedCharacterId ?? undefined;
      const reuseCarry = carriedPlayer?.continuedRun === true;
      const characterId = reuseCarry
        ? carriedPlayer?.characterId ?? selectedCharacterId
        : selectedCharacterId;
      const loadout = reuseCarry
        ? carriedPlayer?.loadout ?? createArenaSurvivorStarterLoadout(characterId)
        : createArenaSurvivorStarterLoadout(characterId);

      return createArenaSurvivorPlayer(
        gamePlayer,
        spawnPoints[index] ?? spawnPoints[0],
        now,
        reuseCarry ? carriedPlayer : undefined,
        loadout,
        characterId,
        index
      );
    })
  };
}

function rebuildPlayersForNextWave(
  contextPlayers: GamePlayerSummary[],
  statePlayers: ArenaSurvivorRuntimePlayerState[],
  now: number
): ArenaSurvivorRuntimePlayerState[] {
  const spawnPoints = createSpawnPoints(contextPlayers.length);

  return contextPlayers.map((gamePlayer, index) => {
    const previousPlayer = statePlayers.find((entry) => entry.playerId === gamePlayer.id);

    return createArenaSurvivorPlayer(
      gamePlayer,
      spawnPoints[index] ?? spawnPoints[0],
      now,
      previousPlayer
        ? {
          continuedRun: true,
          characterId: previousPlayer.character.id,
          level: previousPlayer.level,
          experience: previousPlayer.experience,
          pendingLevelUpChoices: previousPlayer.pendingLevelUpChoices,
          levelBonusModifiers: previousPlayer.levelBonusModifiers.map((modifiers) => ({ ...modifiers })),
          materials: previousPlayer.materials,
          loadout: previousPlayer.loadout,
          runSummary: previousPlayer.runSummary
        }
        : undefined,
      previousPlayer?.loadout ?? createArenaSurvivorStarterLoadout(previousPlayer?.character.id),
      previousPlayer?.character.id,
      index
    );
  });
}

function createRestartedRunState(
  state: ArenaSurvivorRuntimeState,
  context: Pick<ServerGameContext, "players" | "now">
): ArenaSurvivorRuntimeState {
  const gamePlayers = getPlayers(context).slice(0, 4);
  const spawnPoints = createSpawnPoints(gamePlayers.length);
  const difficulty = resolveArenaSurvivorDifficulty(1, gamePlayers.length, state.difficultyTier);
  const players = gamePlayers.map((gamePlayer, index) => {
    const previousPlayer = state.players.find((player) => player.playerId === gamePlayer.id);
    const characterId = previousPlayer?.character.id ?? gamePlayer.selectedCharacterId ?? undefined;

    return createArenaSurvivorPlayer(
      gamePlayer,
      spawnPoints[index] ?? spawnPoints[0],
      context.now,
      undefined,
      createArenaSurvivorStarterLoadout(characterId),
      characterId,
      index
    );
  });

  return {
    ...createBaseRoundState("round_intro", context.now, {
      durationMs: phaseTimings.roundIntroMs,
      message: state.language === "en" ? "A new run begins." : "Ein neuer Run beginnt."
    }),
    language: state.language,
    visualTheme: state.visualTheme,
    seed: ((context.now ^ 0x9e3779b9) >>> 0),
    arenaWidth: arenaSurvivorConfig.arenaWidth,
    arenaHeight: arenaSurvivorConfig.arenaHeight,
    waveNumber: 1,
    difficultyLevel: difficulty.level,
    difficultyTier: state.difficultyTier,
    spawnedBossDefinitionIds: [],
    elapsedMs: 0,
    remainingMs: arenaSurvivorConfig.roundDurationMs,
    nextEnemySpawnAtMs: Math.max(
      difficulty.enemySpawnIntervalMs,
      arenaSurvivorConfig.enemySpawnWarningLeadMs
    ),
    kills: 0,
    players,
    enemies: [],
    projectiles: [],
    pickups: [],
    spawnIndicators: [],
    result: createDefaultResult(),
    debugInfo: {
      enemySpawnCooldownMs: Math.max(
        difficulty.enemySpawnIntervalMs,
        arenaSurvivorConfig.enemySpawnWarningLeadMs
      ),
      enemyCount: 0,
      pickupCount: 0,
      projectileCount: 0,
      alivePlayerCount: countAlivePlayers(players)
    }
  };
}

export const arenaSurvivorServerGame: ServerGame<
  ArenaSurvivorRuntimeState,
  ArenaSurvivorInput,
  ArenaSurvivorPublicState
> = {
  manifest: arenaSurvivorManifest,
  handleHostAction(state, action, context) {
    const hostAction = action as Partial<ArenaSurvivorHostAction> | null;

    if (!hostAction?.type) {
      return {};
    }

    if (state) {
      if (hostAction.type === "restart-run" && (state.result.outcome === "defeated" || (state.survival && state.result.outcome === "survived"))) {
        const restarted = createRestartedRunState(state, context);
        return { state: state.survival ? initializeSurvival(restarted) : restarted };
      }

      return {};
    }

    if (hostAction.type === "confirm-lobby") {
      return {
        roomSettings: {
          [arenaSurvivorRoomSettingKeys.setupConfirmed]: true
        }
      };
    }

    if (hostAction.type !== "configure-lobby") {
      return {};
    }

    const roomSettings: Record<string, unknown> = {
      [arenaSurvivorRoomSettingKeys.setupConfirmed]: false
    };

    if (hostAction.difficulty !== undefined) {
      roomSettings[arenaSurvivorRoomSettingKeys.difficultyTier] =
        clampArenaSurvivorDifficultyTier(hostAction.difficulty);
    }

    if (hostAction.mode === "wave" || hostAction.mode === "survival") {
      roomSettings.arenaSurvivorMode = hostAction.mode;
      if (hostAction.mode === "survival") roomSettings[arenaSurvivorRoomSettingKeys.visualTheme] = "frostfire-saga";
    }

    if (hostAction.visualTheme !== undefined) {
      roomSettings[arenaSurvivorRoomSettingKeys.visualTheme] =
        isArenaSurvivorVisualTheme(hostAction.visualTheme)
          ? hostAction.visualTheme
          : arenaSurvivorDefaultVisualTheme;
    }

    if (hostAction.mode === "survival" || (hostAction.mode !== "wave" && context.roomSettings.arenaSurvivorMode === "survival")) {
      roomSettings[arenaSurvivorRoomSettingKeys.visualTheme] = "frostfire-saga";
    }
    return {
      roomSettings
    };
  },
  createInitialState(context) {
    const en = context.language === "en";
    const gamePlayers = getPlayers(context).slice(0, 4);
    const previousState =
      context.previousRound?.gameId === arenaSurvivorManifest.id
        ? resolvePreviousArenaState(context.previousRound.state as ArenaSurvivorRuntimeState)
        : null;
    const survivalMode = context.roomSettings.arenaSurvivorMode === "survival";
    const playerSetup = createPlayersFromCarry(gamePlayers, context.now, survivalMode || previousState?.survival ? null : previousState);
    const visualTheme = resolveConfiguredArenaSurvivorVisualTheme(context);
    const configuredDifficultyTier = resolveConfiguredArenaSurvivorDifficultyTier(context);
    const difficulty = resolveArenaSurvivorDifficulty(
      playerSetup.waveNumber,
      playerSetup.players.length,
      configuredDifficultyTier
    );

    const initial: ArenaSurvivorRuntimeState = {
      ...createBaseRoundState("round_intro", context.now, {
        durationMs: phaseTimings.roundIntroMs,
        message: playerSetup.continuedRun
          ? en ? `Wave ${playerSetup.waveNumber} is being prepared.` : `Welle ${playerSetup.waveNumber} wird vorbereitet.`
          : en ? "A new run begins." : "Ein neuer Run beginnt."
      }),
      language: context.language,
      visualTheme,
      seed:
        previousState?.seed ??
        ((context.now ^ 0x9e3779b9 ^ playerSetup.waveNumber * 97) >>> 0),
      arenaWidth: arenaSurvivorConfig.arenaWidth,
      arenaHeight: arenaSurvivorConfig.arenaHeight,
      waveNumber: playerSetup.waveNumber,
      difficultyLevel: difficulty.level,
      difficultyTier: difficulty.difficultyTier,
      spawnedBossDefinitionIds: [],
      elapsedMs: 0,
      remainingMs: arenaSurvivorConfig.roundDurationMs,
      nextEnemySpawnAtMs: Math.max(
        difficulty.enemySpawnIntervalMs,
        arenaSurvivorConfig.enemySpawnWarningLeadMs
      ),
      kills: 0,
      players: playerSetup.players,
      enemies: [],
      projectiles: [],
      pickups: [],
      spawnIndicators: [],
      result: createDefaultResult(),
      debugInfo: {
        enemySpawnCooldownMs: Math.max(
          difficulty.enemySpawnIntervalMs,
          arenaSurvivorConfig.enemySpawnWarningLeadMs
        ),
        enemyCount: 0,
        pickupCount: 0,
        projectileCount: 0,
        alivePlayerCount: countAlivePlayers(playerSetup.players)
      }
    };
    return survivalMode ? initializeSurvival(initial) : initial;
  },
  startRound(state, context) {
    if (state.survival) {
      return transitionRoundState(initializeSurvival(state), "playing", context.now, { startedAt: context.now, message: "Survival" });
    }
    const freshPlayers = rebuildPlayersForNextWave(getPlayers(context).slice(0, 4), state.players, context.now);
    const configuredDifficultyTier = resolveConfiguredArenaSurvivorDifficultyTier(context);
    const difficulty = resolveArenaSurvivorDifficulty(
      state.waveNumber,
      freshPlayers.length,
      configuredDifficultyTier
    );

    return transitionRoundState(
      {
        ...state,
        elapsedMs: 0,
        remainingMs: arenaSurvivorConfig.roundDurationMs,
        difficultyLevel: difficulty.level,
        difficultyTier: difficulty.difficultyTier,
        spawnedBossDefinitionIds: [],
        nextEnemySpawnAtMs: Math.max(
          difficulty.enemySpawnIntervalMs,
          arenaSurvivorConfig.enemySpawnWarningLeadMs
        ),
        kills: 0,
        players: freshPlayers.map((player) => ({
          ...player,
          shop: createEmptyShopState(),
          runStats: createEmptyRunStats()
        })),
        enemies: [],
        projectiles: [],
        pickups: [],
        spawnIndicators: [],
        result: createDefaultResult(),
        debugInfo: {
          enemySpawnCooldownMs: Math.max(
            difficulty.enemySpawnIntervalMs,
            arenaSurvivorConfig.enemySpawnWarningLeadMs
          ),
          enemyCount: 0,
          pickupCount: 0,
          projectileCount: 0,
          alivePlayerCount: countAlivePlayers(freshPlayers)
        }
      },
      "playing",
      context.now,
      {
        startedAt: context.now,
        message: state.language === "en" ? `Wave ${state.waveNumber} is running.` : `Welle ${state.waveNumber} laeuft.`
      }
    );
  },
  handleInput(state, input, context) {
    const playerIndex = state.players.findIndex((player) => player.playerId === input.playerId);

    if (playerIndex === -1) {
      return state;
    }

    if (state.survival && state.phase === "playing") {
      if (state.survival.pause) return handleSurvivalInput(state, input, context.now);
      if (input.type !== "move") return state;
    }

    if (
      input.type === "shop:buy" ||
      input.type === "shop:reroll" ||
      input.type === "shop:sell" ||
      input.type === "shop:combine"
    ) {
      if (
        state.phase === "locked" ||
        state.phase === "result" ||
        state.phase === "scoreboard" ||
        state.phase === "finished"
      ) {
        const nextState =
          input.type === "shop:buy"
            ? applyArenaSurvivorShopPurchase(state, input.playerId, input.offerId)
            : input.type === "shop:sell"
              ? applyArenaSurvivorShopSell(state, input.playerId, input.weaponInstanceId)
              : input.type === "shop:combine"
                ? applyArenaSurvivorShopCombine(state, input.playerId, input.weaponInstanceId)
              : applyArenaSurvivorShopReroll(state, input.playerId);

        return {
          ...nextState,
          // Server clock: `sentAt` is the phone's clock and can be skewed.
          updatedAt: context.now
        };
      }

      return state;
    }

    if (state.phase !== "countdown" && state.phase !== "playing") {
      return state;
    }

    if (input.type !== "move") {
      return state;
    }

    const nextPlayers = [...state.players];
    const player = nextPlayers[playerIndex];
    nextPlayers[playerIndex] = {
      ...player,
      moveInputX: Math.max(-1, Math.min(1, input.moveX)),
      moveInputY: Math.max(-1, Math.min(1, input.moveY))
    };

    return {
      ...state,
      players: nextPlayers,
      updatedAt: context.now
    };
  },
  tick(state, deltaMs, context) {
    if (state.phase !== "playing") {
      return state;
    }

    if (state.survival) {
      const wasPaused = Boolean(state.survival.pause);
      state = updateSurvivalPause(state, context.now, context.players.filter(p => p.connected).map(p => p.id));
      if (wasPaused || state.survival?.pause || !state.survival?.participantIds.length) return state;
    }

    let nextState: ArenaSurvivorRuntimeState = {
      ...state,
      elapsedMs: state.elapsedMs + deltaMs,
      remainingMs: Math.max(0, state.remainingMs - deltaMs),
      players: state.players.map((player) => ({
        ...player,
        hp: player.alive
          ? Math.min(player.maxHp, player.hp + player.stats.hpRegen * (deltaMs / 1000))
          : player.hp,
        runStats: {
          ...player.runStats,
          survivedMs: player.runStats.survivedMs + deltaMs
        }
      }))
    };

    if (nextState.survival) {
      nextState.waveNumber = 1 + Math.floor(nextState.elapsedMs / 60_000);
      nextState.remainingMs = Math.max(0, survivalDurationMs - nextState.elapsedMs);
      nextState = advanceSurvivalWorld(nextState);
    }
    nextState = applySpawnSystem(nextState);
    const beforeCombat = nextState;
    nextState = applyMovementSystem(nextState, deltaMs);
    nextState = applyEnemyAiSystem(nextState, deltaMs);
    const combatNow = nextState.survival ? nextState.elapsedMs : context.now;
    nextState = applyAutoFireSystem(nextState, deltaMs, combatNow);
    nextState = applyProjectileSystem(nextState, deltaMs);

    const collisionReport = resolveCollisionSystem(nextState);
    nextState = applyDamageSystem(nextState, collisionReport, combatNow);
    nextState = applyPickupSystem(nextState, deltaMs);
    nextState = nextState.survival ? resolveSurvivalCombat(nextState, beforeCombat, context.now) : resolveRoundEndSystem(nextState, context.now);

    if (nextState.phase !== "playing") {
      return nextState;
    }

    return {
      ...nextState,
      debugInfo: {
        enemySpawnCooldownMs: Math.max(0, nextState.nextEnemySpawnAtMs - nextState.elapsedMs),
        enemyCount: nextState.enemies.length,
        pickupCount: nextState.pickups.length,
        projectileCount: nextState.projectiles.length,
        alivePlayerCount: countAlivePlayers(nextState.players)
      }
    };
  },
  isRoundFinished(state) {
    return state.phase === "locked";
  },
  buildScore(state) {
    return buildScore(state);
  },
  toPublicState(state) {
    return {
      survival: state.survival?.chestReward ? { ...state.survival, chestReward: localizeArenaSurvivorPresentation(state.survival.chestReward, state.language) } : state.survival,
      visualTheme: state.visualTheme,
      arenaWidth: state.arenaWidth,
      arenaHeight: state.arenaHeight,
      waveNumber: state.waveNumber,
      difficultyLevel: state.difficultyLevel,
      elapsedMs: state.elapsedMs,
      remainingMs: state.remainingMs,
      kills: state.kills,
      players: state.players.map(player => toPublicPlayer(player, state.language)),
      difficultyTier: state.difficultyTier,
      damageEvents: state.damageEvents ?? [],
      enemies: state.enemies.map(toPublicEnemy),
      projectiles: state.projectiles.map(toPublicProjectile),
      pickups: state.pickups.map(toPublicPickup),
      spawnIndicators: state.spawnIndicators.map(toPublicSpawnIndicator),
      result: {
        ...state.result
      },
      debugInfo: state.debugInfo,
      seed: state.seed
    };
  },
  toControllerState(state) {
    // Fallback for controller sockets without a player id; the broadcaster
    // builds it on every emit, so it stays as small as the per-player variant.
    return toSlimControllerState(state, state.players.map(player => toPublicPlayer(player, state.language)));
  },
  toControllerStateForPlayer(state, _context, playerId) {
    const ownPlayer = state.players.find((player) => player.playerId === playerId);
    return toSlimControllerState(state, ownPlayer ? [toPublicPlayer(ownPlayer, state.language)] : []);
  }
};
