import {
  createBaseRoundState,
  resolveRoundPhaseTimings,
  transitionRoundState,
  type BaseRoundState,
  type GamePlayerSummary,
  type ScoreEntry,
  type ServerGame,
  type ServerGameContext
} from "@open-party-lab/game-core";
import {
  flatterfluffManifest,
  flatterfluffRoomSettingKeys
} from "../manifest.js";
import type {
  FlatterfluffEventKind,
  FlatterfluffGameEvent,
  FlatterfluffInput,
  FlatterfluffMode,
  FlatterfluffPlayerState,
  FlatterfluffState,
  FlatterfluffTargetKind,
  FlatterfluffTargetState
} from "../protocol.js";

const phaseTimings = resolveRoundPhaseTimings(flatterfluffManifest.phaseDurations);
const maxAmmo = 6;
const reloadDurationMs = 1_900;
const shotCooldownMs = 145;
const aimSpeedPerSecond = 0.46;
const eventHistoryLimit = 36;
const growingTargetStartScale = 0.12;
const growingTargetDurationMs = 5_500;
const typicalHostHeightToWidth = 9 / 16;
const targetTopBound = 0.17;
const targetBottomBound = 0.79;
const targetDefinitions = {
  scout: { radius: 0.034, points: 180, hp: 1, speed: 0.205 },
  flier: { radius: 0.052, points: 110, hp: 1, speed: 0.135 },
  captain: { radius: 0.074, points: 320, hp: 2, speed: 0.085 },
  jumbo: { radius: 0.095, points: 450, hp: 3, speed: 0.065 },
  golden: { radius: 0.045, points: 500, hp: 1, speed: 0.175 },
  crate: { radius: 0.058, points: 0, hp: 1, speed: 0.07 }
} as const;

interface RuntimeTarget extends FlatterfluffTargetState {
  vx: number;
  baseY: number;
  wobblePhase: number;
  wobbleSpeed: number;
  spawnedAtMs: number;
  growthDurationMs: number;
  expiresAtMs: number;
}

interface RuntimePlayer extends FlatterfluffPlayerState {
  aimInputX: number;
  aimInputY: number;
  nextShotAt: number;
  lastHitAt: number;
}

interface RuntimeState extends BaseRoundState {
  mode: FlatterfluffMode;
  roundDurationMs: number | null;
  elapsedMs: number;
  remainingMs: number | null;
  serverNow: number;
  targets: RuntimeTarget[];
  players: RuntimePlayer[];
  events: FlatterfluffGameEvent[];
  totalHits: number;
  nextTargetId: number;
  nextEventId: number;
  nextCrateSpawnMs: number;
  rngSeed: number;
}

interface ConfigureLobbyAction {
  type: "configure-lobby";
  mode?: unknown;
  roundSeconds?: unknown;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function nextRandom(seed: number): { seed: number; value: number } {
  const nextSeed = (Math.imul(seed, 1_664_525) + 1_013_904_223) >>> 0;
  return { seed: nextSeed, value: nextSeed / 0x1_0000_0000 };
}

function resolveMode(settings: Readonly<Record<string, unknown>>): FlatterfluffMode {
  return settings[flatterfluffRoomSettingKeys.mode] === "endless" ? "endless" : "timed";
}

function resolveRoundSeconds(settings: Readonly<Record<string, unknown>>): number {
  const value = settings[flatterfluffRoomSettingKeys.roundSeconds];
  return typeof value === "number" && Number.isFinite(value)
    ? clamp(Math.round(value / 15) * 15, 45, 180)
    : 90;
}

function getPlayers(context: ServerGameContext): GamePlayerSummary[] {
  if (context.players.length > 0) {
    return context.players.slice(0, flatterfluffManifest.maxPlayers);
  }

  return [
    {
      id: "flatterfluff-player",
      name: "Player",
      color: "#22d3ee",
      score: 0,
      isReady: true,
      connected: true
    }
  ];
}

function createPlayers(context: ServerGameContext): RuntimePlayer[] {
  const players = getPlayers(context);

  return players.map((player, index) => ({
    playerId: player.id,
    name: player.name,
    color: player.color,
    aimX: 0.5 + ((index % 3) - 1) * 0.08,
    aimY: 0.48 + (Math.floor(index / 3) - 0.5) * 0.1,
    aimInputX: 0,
    aimInputY: 0,
    ammo: maxAmmo,
    maxAmmo,
    reloadEndsAt: null,
    nextShotAt: 0,
    score: 0,
    hits: 0,
    shots: 0,
    streak: 0,
    lastHitAt: 0
  }));
}

function chooseTargetKind(randomValue: number): Exclude<FlatterfluffTargetKind, "crate"> {
  if (randomValue < 0.2) {
    return "scout";
  }
  if (randomValue < 0.7) {
    return "flier";
  }
  if (randomValue < 0.88) {
    return "captain";
  }
  if (randomValue < 0.95) {
    return "jumbo";
  }
  return "golden";
}

function spawnTarget(
  state: RuntimeState,
  forcedKind?: FlatterfluffTargetKind
): RuntimeState {
  let random = nextRandom(state.rngSeed);
  const kind = forcedKind ?? chooseTargetKind(random.value);
  random = nextRandom(random.seed);
  const fromLeft = random.value >= 0.5;
  random = nextRandom(random.seed);
  const lane = Math.floor(random.value * 3);
  random = nextRandom(random.seed);
  const laneY = [0.31, 0.48, 0.65][lane] ?? 0.48;
  const baseY = clamp(laneY + (random.value - 0.5) * 0.12, 0.2, 0.76);
  random = nextRandom(random.seed);
  const speedScale = 0.86 + random.value * 0.34;
  random = nextRandom(random.seed);
  const wobblePhase = random.value * Math.PI * 2;
  random = nextRandom(random.seed);
  const growthDurationMs = growingTargetDurationMs;
  const definition = targetDefinitions[kind];
  const facing: -1 | 1 = fromLeft ? 1 : -1;
  const x = fromLeft ? -definition.radius * 1.4 : 1 + definition.radius * 1.4;
  const target: RuntimeTarget = {
    id: `fluff-${state.nextTargetId}`,
    kind,
    x,
    y: baseY,
    baseY,
    radius: definition.radius,
    scale: growingTargetStartScale,
    lane,
    points: definition.points + (2 - lane) * 35,
    hp: definition.hp,
    maxHp: definition.hp,
    facing,
    vx: definition.speed * speedScale * facing,
    wobblePhase,
    wobbleSpeed: 2.1 + random.value * 1.8,
    spawnedAtMs: state.elapsedMs,
    growthDurationMs,
    expiresAtMs: state.elapsedMs + (kind === "crate" ? 18_000 : 13_000)
  };

  return {
    ...state,
    targets: [...state.targets, target],
    nextTargetId: state.nextTargetId + 1,
    rngSeed: random.seed
  };
}

function targetVisualDiameter(target: RuntimeTarget): number {
  const factor =
    target.kind === "scout"
      ? 1.95
      : target.kind === "flier"
        ? 1.65
        : target.kind === "captain"
          ? 1.3
          : target.kind === "jumbo"
            ? 1.15
            : target.kind === "golden"
              ? 1.5
              : 1.6;
  return target.radius * factor * target.scale;
}

function limitTargetOverlap(targets: RuntimeTarget[]): RuntimeTarget[] {
  const adjusted = targets.map((target) => ({ ...target }));

  for (let pass = 0; pass < 8; pass += 1) {
    for (let firstIndex = 0; firstIndex < adjusted.length; firstIndex += 1) {
      const first = adjusted[firstIndex];
      if (!first || first.x < 0 || first.x > 1) {
        continue;
      }

      for (let secondIndex = firstIndex + 1; secondIndex < adjusted.length; secondIndex += 1) {
        const second = adjusted[secondIndex];
        if (!second || second.x < 0 || second.x > 1) {
          continue;
        }

        const horizontalDistance = Math.abs(first.x - second.x);
        const minimumCenterDistance =
          Math.max(targetVisualDiameter(first), targetVisualDiameter(second)) * 0.5;

        if (horizontalDistance >= minimumCenterDistance) {
          continue;
        }

        const requiredVerticalDistance =
          Math.sqrt(minimumCenterDistance ** 2 - horizontalDistance ** 2) /
          typicalHostHeightToWidth;
        const currentVerticalDistance = Math.abs(first.y - second.y);
        const missingDistance = requiredVerticalDistance - currentVerticalDistance;

        if (missingDistance <= 0) {
          continue;
        }

        const firstIsAbove =
          first.y < second.y || (first.y === second.y && first.id < second.id);
        const upper = firstIsAbove ? first : second;
        const lower = firstIsAbove ? second : first;
        const upperCapacity = upper.y - targetTopBound;
        const lowerCapacity = targetBottomBound - lower.y;
        let upperShift = Math.min(missingDistance / 2, Math.max(0, upperCapacity));
        let lowerShift = Math.min(
          missingDistance - upperShift,
          Math.max(0, lowerCapacity)
        );
        upperShift += Math.min(
          missingDistance - upperShift - lowerShift,
          Math.max(0, upperCapacity - upperShift)
        );

        upper.y -= upperShift;
        upper.baseY = clamp(upper.baseY - upperShift, targetTopBound, targetBottomBound);
        lower.y += lowerShift;
        lower.baseY = clamp(lower.baseY + lowerShift, targetTopBound, targetBottomBound);
      }
    }
  }

  return adjusted;
}

function appendEvent(
  state: RuntimeState,
  kind: FlatterfluffEventKind,
  event: Omit<FlatterfluffGameEvent, "id" | "kind" | "at">
): RuntimeState {
  const nextEvent: FlatterfluffGameEvent = {
    id: state.nextEventId,
    kind,
    at: state.serverNow,
    ...event
  };

  return {
    ...state,
    nextEventId: state.nextEventId + 1,
    events: [...state.events, nextEvent].slice(-eventHistoryLimit)
  };
}

function createRuntimeState(context: ServerGameContext): RuntimeState {
  const mode = resolveMode(context.roomSettings);
  const roundDurationMs = mode === "timed" ? resolveRoundSeconds(context.roomSettings) * 1_000 : null;
  let state: RuntimeState = {
    ...createBaseRoundState("round_intro", context.now, {
      durationMs: phaseTimings.roundIntroMs,
      message:
        context.language === "en"
          ? "Winged sweets are gathering over the sugar prairie."
          : "Gefluegelte Suessigkeiten sammeln sich ueber der Zuckerpraerie."
    }),
    mode,
    roundDurationMs,
    elapsedMs: 0,
    remainingMs: roundDurationMs,
    serverNow: context.now,
    targets: [],
    players: createPlayers(context),
    events: [],
    totalHits: 0,
    nextTargetId: 1,
    nextEventId: 1,
    nextCrateSpawnMs: 11_000,
    rngSeed: ((context.now ^ context.roundNumber * 2_654_435_761) >>> 0) || 1
  };

  const initialTargetCount = Math.min(9, 4 + state.players.length);
  for (let index = 0; index < initialTargetCount; index += 1) {
    state = spawnTarget(state);
    const target = state.targets[state.targets.length - 1];
    if (target) {
      target.x = 0.08 + ((index + 1) / (initialTargetCount + 1)) * 0.84;
    }
  }

  return state;
}

function startReload(state: RuntimeState, playerIndex: number): RuntimeState {
  const player = state.players[playerIndex];
  if (!player || player.reloadEndsAt !== null || player.ammo >= player.maxAmmo) {
    return state;
  }

  const players = [...state.players];
  players[playerIndex] = {
    ...player,
    reloadEndsAt: state.serverNow + reloadDurationMs,
    streak: 0
  };

  return appendEvent(
    { ...state, players },
    "reload",
    { playerId: player.playerId, x: player.aimX, y: player.aimY }
  );
}

function resolveShot(state: RuntimeState, playerIndex: number): RuntimeState {
  const player = state.players[playerIndex];
  if (!player || state.serverNow < player.nextShotAt) {
    return state;
  }

  if (player.reloadEndsAt !== null) {
    return state;
  }

  if (player.ammo <= 0) {
    const emptyState = appendEvent(state, "empty", {
      playerId: player.playerId,
      x: player.aimX,
      y: player.aimY
    });
    return startReload(emptyState, playerIndex);
  }

  const players = [...state.players];
  const firingPlayer: RuntimePlayer = {
    ...player,
    ammo: player.ammo - 1,
    shots: player.shots + 1,
    nextShotAt: state.serverNow + shotCooldownMs
  };
  players[playerIndex] = firingPlayer;

  let nextState = appendEvent(
    { ...state, players },
    "shot",
    { playerId: player.playerId, x: player.aimX, y: player.aimY }
  );
  const candidates = nextState.targets
    .map((target, index) => ({
      target,
      index,
      distance: Math.hypot(target.x - player.aimX, target.y - player.aimY),
      hitRadius: target.radius * target.scale
    }))
    .filter((entry) => entry.distance <= entry.hitRadius + 0.012)
    .sort((left, right) => {
      const normalizedLeft = left.distance / left.hitRadius;
      const normalizedRight = right.distance / right.hitRadius;
      return normalizedLeft - normalizedRight || right.target.lane - left.target.lane;
    });
  const hit = candidates[0];

  if (!hit) {
    nextState = appendEvent(nextState, "miss", {
      playerId: player.playerId,
      x: player.aimX,
      y: player.aimY
    });
    if (firingPlayer.ammo === 0) {
      nextState = startReload(nextState, playerIndex);
    }
    return nextState;
  }

  if (hit.target.kind === "crate") {
    const updatedPlayers = [...nextState.players];
    updatedPlayers[playerIndex] = {
      ...updatedPlayers[playerIndex],
      ammo: maxAmmo,
      reloadEndsAt: null,
      streak: updatedPlayers[playerIndex].streak + 1
    };
    nextState = {
      ...nextState,
      players: updatedPlayers,
      targets: nextState.targets.filter((target) => target.id !== hit.target.id)
    };
    return appendEvent(nextState, "ammo", {
      playerId: player.playerId,
      targetId: hit.target.id,
      x: hit.target.x,
      y: hit.target.y
    });
  }

  const hitWithinStreakWindow = state.serverNow - player.lastHitAt <= 2_600;
  const streak = hitWithinStreakWindow ? player.streak + 1 : 1;
  const armorHit = hit.target.hp > 1;
  const sizeBonus = 1 + (1 - hit.target.scale) * 1.6;
  const earnedPoints = armorHit
    ? Math.round(45 * sizeBonus)
    : Math.round(
        hit.target.points *
          (1 + Math.min(5, streak - 1) * 0.12) *
          sizeBonus
      );
  const updatedPlayers = [...nextState.players];
  updatedPlayers[playerIndex] = {
    ...updatedPlayers[playerIndex],
    score: updatedPlayers[playerIndex].score + earnedPoints,
    hits: updatedPlayers[playerIndex].hits + (armorHit ? 0 : 1),
    streak,
    lastHitAt: state.serverNow
  };
  nextState = {
    ...nextState,
    players: updatedPlayers,
    totalHits: nextState.totalHits + (armorHit ? 0 : 1),
    targets: armorHit
      ? nextState.targets.map((target) =>
          target.id === hit.target.id ? { ...target, hp: target.hp - 1 } : target
        )
      : nextState.targets.filter((target) => target.id !== hit.target.id)
  };
  nextState = appendEvent(nextState, armorHit ? "armor" : "hit", {
    playerId: player.playerId,
    targetId: hit.target.id,
    x: hit.target.x,
    y: hit.target.y,
    points: earnedPoints
  });

  if (updatedPlayers[playerIndex].ammo === 0) {
    nextState = startReload(nextState, playerIndex);
  }

  return nextState;
}

function tickPlayers(state: RuntimeState, deltaMs: number): RuntimeState {
  const seconds = deltaMs / 1_000;
  let nextState = {
    ...state,
    players: state.players.map((player) => ({
      ...player,
      aimX: clamp(player.aimX + player.aimInputX * aimSpeedPerSecond * seconds, 0.035, 0.965),
      aimY: clamp(player.aimY + player.aimInputY * aimSpeedPerSecond * seconds, 0.15, 0.86)
    }))
  };

  for (let index = 0; index < nextState.players.length; index += 1) {
    const player = nextState.players[index];
    if (player.reloadEndsAt === null || nextState.serverNow < player.reloadEndsAt) {
      continue;
    }

    const players = [...nextState.players];
    players[index] = {
      ...player,
      ammo: player.maxAmmo,
      reloadEndsAt: null
    };
    nextState = appendEvent(
      { ...nextState, players },
      "reload-complete",
      { playerId: player.playerId, x: player.aimX, y: player.aimY }
    );
  }

  return nextState;
}

function tickTargets(state: RuntimeState, deltaMs: number): RuntimeState {
  const seconds = deltaMs / 1_000;
  const elapsedMs = state.elapsedMs + deltaMs;
  const movedTargets = state.targets
    .map((target) => ({
      ...target,
      x: target.x + target.vx * seconds,
      scale:
        target.growthDurationMs > 0
          ? clamp(
              growingTargetStartScale +
                ((elapsedMs - target.spawnedAtMs) / target.growthDurationMs) *
                  (1 - growingTargetStartScale),
              growingTargetStartScale,
              1
            )
          : 1,
      y:
        target.baseY +
        Math.sin(target.wobblePhase + elapsedMs * 0.001 * target.wobbleSpeed) *
          (target.kind === "crate" ? 0.018 : 0.028)
    }))
    .filter(
      (target) =>
        target.x > -0.16 &&
        target.x < 1.16 &&
        elapsedMs < target.expiresAtMs
    );
  let nextState: RuntimeState = {
    ...state,
    elapsedMs,
    remainingMs:
      state.roundDurationMs === null ? null : Math.max(0, state.roundDurationMs - elapsedMs),
    targets: limitTargetOverlap(movedTargets)
  };

  if (elapsedMs >= nextState.nextCrateSpawnMs && !nextState.targets.some((target) => target.kind === "crate")) {
    nextState = spawnTarget(nextState, "crate");
    const random = nextRandom(nextState.rngSeed);
    nextState = {
      ...nextState,
      rngSeed: random.seed,
      nextCrateSpawnMs: elapsedMs + 12_000 + Math.round(random.value * 7_000)
    };
  }

  const desiredTargets = Math.min(11, 4 + nextState.players.length);
  let safety = 0;
  while (
    nextState.targets.filter((target) => target.kind !== "crate").length < desiredTargets &&
    safety < desiredTargets
  ) {
    nextState = spawnTarget(nextState);
    safety += 1;
  }

  return nextState;
}

function toPublicState(state: RuntimeState): FlatterfluffState {
  return {
    mode: state.mode,
    roundDurationMs: state.roundDurationMs,
    elapsedMs: state.elapsedMs,
    remainingMs: state.remainingMs,
    serverNow: state.serverNow,
    targets: state.targets.map(
      ({ vx: _vx, baseY: _baseY, wobblePhase: _wobblePhase, wobbleSpeed: _wobbleSpeed, spawnedAtMs: _spawnedAtMs, growthDurationMs: _growthDurationMs, expiresAtMs: _expiresAtMs, ...target }) =>
        target
    ),
    players: state.players.map(
      ({ aimInputX: _aimInputX, aimInputY: _aimInputY, nextShotAt: _nextShotAt, lastHitAt: _lastHitAt, ...player }) =>
        player
    ),
    events: state.events,
    totalHits: state.totalHits
  };
}

function buildScore(state: RuntimeState): ScoreEntry[] {
  return state.players.map((player) => ({
    playerId: player.playerId,
    delta: Math.max(0, Math.round(player.score / 500)),
    reason: "Puntos de Dulce puntería"
  }));
}

export const serverGame: ServerGame<RuntimeState, FlatterfluffInput, FlatterfluffState> = {
  manifest: flatterfluffManifest,
  handleHostAction(state, action) {
    const hostAction = action as Partial<ConfigureLobbyAction> | null;
    if (state || hostAction?.type !== "configure-lobby") {
      return {};
    }

    const roomSettings: Record<string, unknown> = {};
    if (hostAction.mode === "timed" || hostAction.mode === "endless") {
      roomSettings[flatterfluffRoomSettingKeys.mode] = hostAction.mode;
    }
    if (typeof hostAction.roundSeconds === "number" && Number.isFinite(hostAction.roundSeconds)) {
      roomSettings[flatterfluffRoomSettingKeys.roundSeconds] = clamp(
        Math.round(hostAction.roundSeconds / 15) * 15,
        45,
        180
      );
    }
    return { roomSettings };
  },
  createInitialState(context) {
    return createRuntimeState(context);
  },
  startRound(_state, context) {
    const freshState = createRuntimeState(context);
    return transitionRoundState(freshState, "playing", context.now, {
      startedAt: context.now,
      message:
        context.language === "en"
          ? "Aim, fire, and watch your magazine!"
          : "Zielen, feuern und das Magazin im Blick behalten!"
    });
  },
  handleInput(state, input, context) {
    if (state.phase !== "playing") {
      return state;
    }

    const playerIndex = state.players.findIndex((player) => player.playerId === input.playerId);
    if (playerIndex < 0) {
      return state;
    }

    let nextState: RuntimeState = {
      ...state,
      serverNow: context.now,
      updatedAt: input.sentAt ?? context.now
    };

    if (input.type === "aim") {
      const players = [...nextState.players];
      const player = players[playerIndex];
      players[playerIndex] = {
        ...player,
        aimInputX: Number.isFinite(input.aimX) ? clamp(input.aimX, -1, 1) : 0,
        aimInputY: Number.isFinite(input.aimY) ? clamp(input.aimY, -1, 1) : 0
      };
      return { ...nextState, players };
    }

    if (input.type === "reload") {
      return startReload(nextState, playerIndex);
    }

    return resolveShot(nextState, playerIndex);
  },
  tick(state, deltaMs, context) {
    if (state.phase !== "playing") {
      return state;
    }

    let nextState: RuntimeState = {
      ...state,
      serverNow: context.now,
      updatedAt: context.now
    };
    nextState = tickPlayers(nextState, deltaMs);
    nextState = tickTargets(nextState, deltaMs);

    if (nextState.mode === "timed" && nextState.remainingMs === 0) {
      const leader = [...nextState.players].sort((left, right) => right.score - left.score)[0];
      return transitionRoundState(nextState, "locked", context.now, {
        durationMs: phaseTimings.lockedMs,
        message:
          context.language === "en"
            ? `${leader?.name ?? "The top shot"} leads the sugar prairie.`
            : `${leader?.name ?? "Der beste Schuetze"} fuehrt in der Zuckerpraerie.`
      });
    }

    return nextState;
  },
  isRoundFinished(state) {
    return state.phase === "locked";
  },
  buildScore(state, context) {
    return buildScore(state).map(entry => ({ ...entry, reason: context.language === "en" ? "Dulce puntería score" : "Puntos de Dulce puntería" }));
  },
  toPublicState(state) {
    return toPublicState(state);
  },
  toControllerState(state) {
    return toPublicState(state);
  }
};
