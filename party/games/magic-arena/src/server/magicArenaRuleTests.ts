import assert from "node:assert/strict";
import { pathToFileURL } from "node:url";
import type { ServerGameContext } from "@open-party-lab/game-core";
import type { MagicArenaCoord, MagicArenaPlannedAction } from "../protocol.js";
import { createMagicArenaDeck, getMagicArenaDeckSize, magicArenaCardDefinitions } from "./magicArenaCards.js";
import {
  ARENA_DECAY_FIRST_DAMAGE,
  ARENA_DECAY_FIRST_FIGURE_THRESHOLD,
  ARENA_DECAY_FIRST_ROUND,
  ARENA_DECAY_SECOND_DAMAGE,
  ARENA_DECAY_SECOND_FIGURE_THRESHOLD,
  ARENA_DECAY_SECOND_ROUND,
  DIG_BIG_CHEST_CHANCE,
  DIG_BURIED_BOMB_CHANCE,
  DIG_NOTHING_CHANCE,
  DIG_SMALL_CHEST_CHANCE,
  DIG_TREASURE_CHANCE,
  DIG_UNSTABLE_GROUND_CHANCE,
  magicArenaRoomSettingKeys
} from "./magicArenaConfig.js";
import { getFieldMaxHp } from "./magicArenaBoard.js";
import {
  addCoords,
  axialKey,
  getNeighbors,
  hexDistance
} from "./magicArenaHex.js";
import {
  applyFieldDamage,
  applyFigureDamage,
  buildExecutionQueue,
  createMagicArenaInitialState,
  destroyField,
  expireWaterAndRoundEffects,
  getField,
  getInitiativeOrder,
  getSlotsPerRound,
  handleMagicArenaInput,
  makeFigureFallIntoWater,
  maybeSpawnCrystalFieldForRound,
  resolveArenaDecayEndEffect,
  resolveBombsForSlot,
  resolvePlannedAction,
  resolvePushChain,
  resolveRoundEventEndEffects,
  resolveStormCloudsForSlot,
  resolveTimeRiftsForRound,
  startMagicArenaRound,
  tickMagicArena,
  toMagicArenaPublicState,
  tryAutoRescueAround,
  type MagicArenaRuntimeState
} from "./magicArenaRules.js";

function createContext(playerCount = 2, now = 1_000): ServerGameContext {
  return {
    roomCode: "TEST",
    roundNumber: 1,
    players: Array.from({ length: playerCount }, (_, index) => ({
      id: `p${index + 1}`,
      name: `P${index + 1}`,
      color: ["#38bdf8", "#fb7185", "#34d399", "#facc15"][index] ?? "#38bdf8",
      score: 0,
      isReady: false,
      connected: true
    })),
    now,
    deltaMs: 0,
    language: "de",
    theme: "light",
    selectedGame: {
      id: "magic-arena",
      displayName: "Hexagonia",
      description: "",
      minPlayers: 2,
      maxPlayers: 4,
      hostView: "MagicArenaHostScene",
      controllerView: "magic-arena",
      controllerLayout: "magic_arena",
      supportsTeams: false,
      estimatedRoundDurationMs: 1
    },
    previousRound: null,
    roomSettings: {}
  };
}

function fieldCoord(state: MagicArenaRuntimeState, coord: MagicArenaCoord): MagicArenaCoord {
  const field = getField(state, coord);
  assert.ok(field?.exists, `Expected field ${axialKey(coord)} to exist`);
  return field.coord;
}

function findEmptyNeighbor(state: MagicArenaRuntimeState, coord: MagicArenaCoord): MagicArenaCoord {
  const neighbor = getNeighbors(coord).find((candidate) => {
    const field = getField(state, candidate);
    return Boolean(field?.exists && field.occupants.length === 0);
  });
  assert.ok(neighbor, "Se esperaba un vecino vacío.");
  return neighbor;
}

function putFigureOn(state: MagicArenaRuntimeState, figureId: string, coord: MagicArenaCoord): void {
  const figure = state.players.flatMap((player) => player.figures).find((entry) => entry.figureId === figureId);
  assert.ok(figure, "Figure missing");
  const oldField = getField(state, figure.coord);

  if (oldField) {
    oldField.occupants = oldField.occupants.filter((entry) => entry !== figureId);
  }

  const field = getField(state, coord);
  assert.ok(field?.exists, "Target field missing");
  field.occupants.push(figureId);
  figure.status = "alive";
  figure.coord = { ...coord };
  delete figure.waterCoord;
  delete figure.rescueDeadlineRound;
}

function keepOnlyFiguresOnFields(state: MagicArenaRuntimeState, keptFigureIds: string[]): void {
  const kept = new Set(keptFigureIds);

  for (const player of state.players) {
    for (const figure of player.figures) {
      if (kept.has(figure.figureId)) {
        continue;
      }

      if (figure.coord) {
        const field = getField(state, figure.coord);
        if (field) {
          field.occupants = field.occupants.filter((entry) => entry !== figure.figureId);
        }
      }

      figure.status = "eliminated";
      delete figure.coord;
      delete figure.waterCoord;
      delete figure.rescueDeadlineRound;
    }
  }
}

function setRingFieldsHp(state: MagicArenaRuntimeState, distance: number, hp: number): MagicArenaCoord[] {
  return Object.values(state.board.fieldsByCoord)
    .filter((field) => field.exists && hexDistance(field.coord, { q: 0, r: 0 }) === distance)
    .map((field) => {
      field.maxHp = Math.max(field.maxHp, hp);
      field.hp = hp;
      field.specialType = null;
      return { ...field.coord };
    });
}

function planBaseMove(playerId: string, figureId: string, path: MagicArenaCoord[]): MagicArenaPlannedAction {
  return {
    playerId,
    slotIndex: 0,
    figureId,
    actionKind: "base",
    baseActionId: "move",
    path
  };
}

function planBaseDig(playerId: string, figureId: string): MagicArenaPlannedAction {
  return {
    playerId,
    slotIndex: 0,
    figureId,
    actionKind: "base",
    baseActionId: "dig"
  };
}

function planCard(
  playerId: string,
  figureId: string,
  cardId: MagicArenaPlannedAction["cardId"],
  targetCoord?: MagicArenaCoord
): MagicArenaPlannedAction {
  assert.ok(cardId, "Card id required");
  return {
    playerId,
    slotIndex: 0,
    figureId,
    actionKind: "card",
    cardInstanceId: `${cardId}-test`,
    cardId,
    targetCoord
  };
}

function giveCard(state: MagicArenaRuntimeState, playerIndex: number, cardId: MagicArenaPlannedAction["cardId"]): void {
  assert.ok(cardId, "Card id required");
  state.players[playerIndex].hand.push({ instanceId: `${cardId}-test`, cardId });
}

export function runMagicArenaRuleTests(): void {
  assert.equal(hexDistance({ q: 0, r: 0 }, { q: 2, r: -1 }), 2);

  {
    const state = startMagicArenaRound(createContext());
    const figure = state.players[0].figures[0];
    putFigureOn(state, figure.figureId, fieldCoord(state, { q: 0, r: 0 }));
    const target = findEmptyNeighbor(state, figure.coord as MagicArenaCoord);
    resolvePlannedAction(state, planBaseMove(figure.playerId, figure.figureId, [target]));
    assert.deepEqual(figure.coord, target);
  }

  {
    const state = startMagicArenaRound(createContext());
    const figure = state.players[0].figures[0];
    putFigureOn(state, figure.figureId, fieldCoord(state, { q: 0, r: 0 }));
    const target = findEmptyNeighbor(state, figure.coord as MagicArenaCoord);
    destroyField(state, target, "test");
    resolvePlannedAction(state, planBaseMove(figure.playerId, figure.figureId, [target]));
    assert.equal(figure.status, "in_water");
    assert.deepEqual(figure.waterCoord, target);
  }

  {
    const state = startMagicArenaRound(createContext());
    const figure = state.players[0].figures[0];
    putFigureOn(state, figure.figureId, fieldCoord(state, { q: 0, r: 0 }));
    applyFieldDamage(state, figure.coord as MagicArenaCoord, 99, "test");
    assert.equal(figure.status, "in_water");
  }

  {
    const state = startMagicArenaRound(createContext());
    const figure = state.players[0].figures[0];
    makeFigureFallIntoWater(state, figure.figureId, figure.coord as MagicArenaCoord, "test");
    state.roundIndex = figure.rescueDeadlineRound ?? state.roundIndex;
    expireWaterAndRoundEffects(state);
    assert.equal(figure.status, "eliminated");
  }

  {
    const state = startMagicArenaRound(createContext());
    const waterFigure = state.players[0].figures[0];
    const rescuer = state.players[0].figures[1];
    putFigureOn(state, waterFigure.figureId, fieldCoord(state, { q: 0, r: 0 }));
    const waterCoord = waterFigure.coord as MagicArenaCoord;
    const rescueCoord = findEmptyNeighbor(state, waterCoord);
    waterFigure.hp = 2;
    makeFigureFallIntoWater(state, waterFigure.figureId, waterCoord, "test");
    putFigureOn(state, rescuer.figureId, rescueCoord);
    assert.equal(tryAutoRescueAround(state, rescueCoord, waterFigure.playerId), true);
    assert.equal(waterFigure.status, "alive");
    assert.equal(waterFigure.hp, 2);
    assert.ok(waterFigure.coord);
    assert.notDeepEqual(waterFigure.coord, rescueCoord);
    assert.equal(hexDistance(waterFigure.coord, waterCoord), 1);
    assert.equal(hexDistance(waterFigure.coord, rescueCoord), 1);
  }

  {
    const state = startMagicArenaRound(createContext());
    const waterFigure = state.players[0].figures[0];
    const rescuer = state.players[0].figures[1];
    const [blockerA, blockerB] = state.players[1].figures.slice(0, 2);
    assert.ok(blockerA && blockerB);
    const waterCoord = fieldCoord(state, { q: 0, r: 0 });
    const rescueCoord = fieldCoord(state, { q: 1, r: 0 });
    const commonA = fieldCoord(state, { q: 1, r: -1 });
    const commonB = fieldCoord(state, { q: 0, r: 1 });
    putFigureOn(state, waterFigure.figureId, waterCoord);
    putFigureOn(state, rescuer.figureId, rescueCoord);
    putFigureOn(state, blockerA.figureId, commonA);
    putFigureOn(state, blockerB.figureId, commonB);
    makeFigureFallIntoWater(state, waterFigure.figureId, waterCoord, "test");
    assert.equal(waterFigure.status, "in_water");
    assert.ok(state.actionLog.some((entry) => entry.message.includes("kein Rettungsfeld vorhanden")));
  }

  {
    const state = startMagicArenaRound(createContext());
    const figure = state.players[0].figures[0];
    makeFigureFallIntoWater(state, figure.figureId, figure.coord as MagicArenaCoord, "test");
    applyFigureDamage(state, figure.figureId, 1, "test");
    assert.equal(figure.hp, 3);
    applyFigureDamage(state, figure.figureId, 3, "test");
    assert.equal(figure.status, "eliminated");
  }

  {
    const state = startMagicArenaRound(createContext());
    const [actor, blocker] = state.players[0].figures;
    const target = fieldCoord(state, { q: 0, r: 0 });
    const actorCoord = findEmptyNeighbor(state, target);
    putFigureOn(state, blocker.figureId, target);
    putFigureOn(state, actor.figureId, actorCoord);
    resolvePlannedAction(state, planBaseMove(actor.playerId, actor.figureId, [target]));
    assert.deepEqual(actor.coord, actorCoord);
  }

  {
    const state = startMagicArenaRound(createContext(4));
    assert.deepEqual(getInitiativeOrder(state), ["p1", "p2", "p3", "p4"]);
    state.initiativeStartIndex = 1;
    assert.deepEqual(getInitiativeOrder(state), ["p2", "p3", "p4", "p1"]);
  }

  {
    const state = startMagicArenaRound(createContext(2));
    const p1 = state.players[0];
    const p2 = state.players[1];
    state.plannedActionsByPlayer = {
      p1: [
        planBaseMove(p1.playerId, p1.figures[0].figureId, [{ q: 0, r: 0 }]),
        { ...planBaseMove(p1.playerId, p1.figures[1].figureId, [{ q: 0, r: 1 }]), slotIndex: 1 }
      ],
      p2: [
        planBaseMove(p2.playerId, p2.figures[0].figureId, [{ q: -1, r: 0 }])
      ]
    };
    const queue = buildExecutionQueue(state);
    const actionSteps = queue.filter((step) => step.kind === "action");
    assert.equal(queue.filter((step) => step.kind === "bombs").length, 4);
    assert.equal(actionSteps[0].slotIndex, 0);
    assert.equal(actionSteps[1].slotIndex, 0);
    assert.equal(actionSteps[2].slotIndex, 1);
  }

  {
    const state = startMagicArenaRound(createContext(2));
    state.currentRoundEvent = {
      id: "event-test",
      eventId: "flood_wave",
      name: "Maremoto",
      description: "prueba",
      roundIndex: state.roundIndex,
      markedCoords: [{ q: 0, r: 0 }],
      direction: 0
    };
    const queue = buildExecutionQueue(state);
    assert.equal(queue.some((step) => step.kind === "round_event_end"), true);
    assert.equal(queue.at(-1)?.kind, "arena_decay");
  }

  {
    const state = startMagicArenaRound(createContext(2));
    state.currentRoundEvent = {
      id: "event-test",
      eventId: "arcane_overload",
      name: "Sobrecarga arcana",
      description: "prueba",
      roundIndex: state.roundIndex,
      markedCoords: []
    };
    const queue = buildExecutionQueue(state);
    assert.equal(queue.some((step) => step.kind === "round_event_end"), false);
    assert.equal(queue.at(-1)?.kind, "arena_decay");
  }

  assert.equal(getMagicArenaDeckSize(), 100);
  assert.equal(createMagicArenaDeck().length, 100);
  assert.equal(
    createMagicArenaDeck().some((card) => {
      const cardId = card.cardId as string;
      return cardId === "ally_swap" || cardId === "magnet_pull";
    }),
    false
  );
  assert.equal(
    magicArenaCardDefinitions.some((definition) => {
      const cardId = definition.cardId as string;
      return cardId === "ally_swap" || cardId === "magnet_pull";
    }),
    false
  );
  assert.ok(
    Math.abs(
      DIG_SMALL_CHEST_CHANCE +
        DIG_BIG_CHEST_CHANCE +
        DIG_TREASURE_CHANCE +
        DIG_NOTHING_CHANCE +
        DIG_BURIED_BOMB_CHANCE +
        DIG_UNSTABLE_GROUND_CHANCE -
        1
    ) < 0.000001
  );

  {
    const state = startMagicArenaRound(createContext());
    assert.equal(state.players[0].hand.length, 6);
    assert.equal(state.players[1].hand.length, 6);
    assert.equal(state.deck.length, 88);
  }

  {
    const state = startMagicArenaRound(createContext());
    const player = state.players[0];
    const figure = player.figures[0];
    putFigureOn(state, figure.figureId, fieldCoord(state, { q: 0, r: 0 }));
    const field = getField(state, figure.coord);
    assert.ok(field);
    field.type = "normal";
    field.maxHp = getFieldMaxHp("normal");
    field.hp = field.maxHp;
    const handBefore = player.hand.length;
    const hpBefore = figure.hp;
    resolvePlannedAction(state, planBaseDig(player.playerId, figure.figureId));
    assert.ok(field.hp <= field.maxHp - 1 || !field.exists);
    assert.ok(player.hand.length >= handBefore || figure.hp <= hpBefore);
  }

  {
    const state = startMagicArenaRound(createContext());
    const figure = state.players[0].figures[0];
    const player = state.players[0];
    putFigureOn(state, figure.figureId, fieldCoord(state, { q: 0, r: 0 }));
    const target = findEmptyNeighbor(state, figure.coord as MagicArenaCoord);
    player.hand.push({ instanceId: "bomb-test", cardId: "bomb" });
    resolvePlannedAction(state, {
      playerId: player.playerId,
      slotIndex: 0,
      figureId: figure.figureId,
      actionKind: "card",
      cardInstanceId: "bomb-test",
      cardId: "bomb",
      targetCoord: target
    });
    assert.equal(state.pendingBombs[0].explodeSlot, 1);
    resolveBombsForSlot(state, state.roundIndex, 1);
    assert.equal(state.pendingBombs.length, 0);
  }

  {
    const state = startMagicArenaRound(createContext());
    const figure = state.players[0].figures[0];
    putFigureOn(state, figure.figureId, fieldCoord(state, { q: 0, r: 0 }));
    const target = findEmptyNeighbor(state, figure.coord as MagicArenaCoord);
    const enemy = state.players[1].figures[0];
    putFigureOn(state, enemy.figureId, target);
    const targetField = getField(state, target);
    assert.ok(targetField);
    targetField.type = "normal";
    targetField.maxHp = getFieldMaxHp("normal");
    targetField.hp = targetField.maxHp;
    state.players[0].hand.push({ instanceId: "meteor-test", cardId: "meteor_strike" });
    resolvePlannedAction(state, {
      playerId: figure.playerId,
      slotIndex: 0,
      figureId: figure.figureId,
      actionKind: "card",
      cardInstanceId: "meteor-test",
      cardId: "meteor_strike",
      targetCoord: target
    });
    assert.equal(enemy.hp, 2);
    assert.equal(targetField.hp, 1);
  }

  {
    const state = startMagicArenaRound(createContext());
    const player = state.players[0];
    const figure = player.figures[0];
    putFigureOn(state, figure.figureId, fieldCoord(state, { q: 0, r: 0 }));
    const target = findEmptyNeighbor(state, figure.coord as MagicArenaCoord);
    const field = getField(state, target);
    assert.ok(field);
    field.specialType = "crystal";
    player.hand = [];
    state.deck = [{ instanceId: "crystal-card", cardId: "sprint" }];
    resolvePlannedAction(state, planBaseMove(player.playerId, figure.figureId, [target]));
    assert.equal(player.hand.length, 1);
    assert.equal(field.specialType, null);
  }

  {
    const state = startMagicArenaRound(createContext());
    const center = fieldCoord(state, { q: 0, r: 0 });
    const neighbor = findEmptyNeighbor(state, center);
    const centerField = getField(state, center);
    const neighborField = getField(state, neighbor);
    const enemy = state.players[1].figures[0];
    assert.ok(centerField && neighborField);
    centerField.specialType = "explosive";
    neighborField.type = "normal";
    neighborField.maxHp = getFieldMaxHp("normal");
    neighborField.hp = neighborField.maxHp;
    putFigureOn(state, enemy.figureId, neighbor);
    destroyField(state, center, "test");
    assert.equal(neighborField.hp, neighborField.maxHp - 1);
    assert.equal(enemy.hp, 3);
  }

  {
    let broke = false;

    for (let index = 0; index < 80 && !broke; index += 1) {
      const state = startMagicArenaRound(createContext(2, 10_000 + index));
      state.rngSeed = `cracked-test-${index}`;
      const coord = fieldCoord(state, { q: 0, r: 0 });
      const field = getField(state, coord);
      assert.ok(field);
      field.type = "strong";
      field.maxHp = getFieldMaxHp("strong");
      field.hp = field.maxHp;
      field.specialType = "cracked";
      applyFieldDamage(state, coord, 1, "test");
      broke = !field.exists;
    }

    assert.equal(broke, true);
  }

  {
    const state = startMagicArenaRound(createContext());
    const [pusher] = state.players[0].figures;
    const front = state.players[1].figures[0];
    const rear = state.players[1].figures[1];
    putFigureOn(state, pusher.figureId, fieldCoord(state, { q: -1, r: 0 }));
    putFigureOn(state, front.figureId, fieldCoord(state, { q: 0, r: 0 }));
    putFigureOn(state, rear.figureId, fieldCoord(state, { q: 1, r: 0 }));
    resolvePushChain(state, pusher.figureId, 0, "test");
    assert.deepEqual(pusher.coord, { q: 0, r: 0 });
    assert.deepEqual(front.coord, { q: 1, r: 0 });
    assert.deepEqual(rear.coord, { q: 2, r: 0 });
  }

  {
    const state = startMagicArenaRound(createContext());
    const player = state.players[0];
    const figure = player.figures[0];
    putFigureOn(state, figure.figureId, fieldCoord(state, { q: 0, r: 0 }));
    const target = findEmptyNeighbor(state, figure.coord as MagicArenaCoord);
    giveCard(state, 0, "pitfall");
    resolvePlannedAction(state, planCard(player.playerId, figure.figureId, "pitfall", target));
    assert.equal(toMagicArenaPublicState(state, createContext(), player.playerId).boardObjects.length, 1);
    assert.equal(toMagicArenaPublicState(state, createContext(), state.players[1].playerId).boardObjects.length, 0);
    const enemy = state.players[1].figures[0];
    putFigureOn(state, enemy.figureId, fieldCoord(state, { q: 1, r: -1 }));
    resolvePlannedAction(state, planBaseMove(enemy.playerId, enemy.figureId, [target]));
    assert.equal(enemy.hp, 3);
    assert.equal(state.boardObjects.some((object) => object.type === "pitfall"), false);
  }

  {
    const state = startMagicArenaRound(createContext());
    const player = state.players[0];
    const figure = player.figures[0];
    putFigureOn(state, figure.figureId, fieldCoord(state, { q: 0, r: 0 }));
    const target = findEmptyNeighbor(state, figure.coord as MagicArenaCoord);
    giveCard(state, 0, "teleport");
    resolvePlannedAction(state, planCard(player.playerId, figure.figureId, "teleport", target));
    assert.deepEqual(figure.coord, target);
  }

  {
    const state = startMagicArenaRound(createContext());
    const figure = state.players[0].figures[0];
    const waterFigure = state.players[1].figures[0];
    putFigureOn(state, figure.figureId, fieldCoord(state, { q: 0, r: 0 }));
    const waterCoord = findEmptyNeighbor(state, figure.coord as MagicArenaCoord);
    makeFigureFallIntoWater(state, waterFigure.figureId, waterCoord, "test");
    giveCard(state, 0, "kraken");
    resolvePlannedAction(state, planCard(figure.playerId, figure.figureId, "kraken", waterCoord));
    assert.equal(waterFigure.hp, 2);
  }

  {
    const state = startMagicArenaRound(createContext());
    const player = state.players[0];
    const figure = player.figures[0];
    putFigureOn(state, figure.figureId, fieldCoord(state, { q: 0, r: 0 }));
    const target = findEmptyNeighbor(state, figure.coord as MagicArenaCoord);
    const field = getField(state, target);
    assert.ok(field);
    giveCard(state, 0, "time_rift");
    resolvePlannedAction(state, planCard(player.playerId, figure.figureId, "time_rift", target));
    assert.equal(state.delayedEffects[0]?.triggerRound, state.roundIndex + 1);
    assert.equal(field.hp, field.maxHp);
    state.roundIndex += 1;
    resolveTimeRiftsForRound(state, state.roundIndex);
    assert.equal(state.delayedEffects.length, 0);
    assert.equal(field.hp, Math.max(0, field.maxHp - 3));
  }

  {
    const state = startMagicArenaRound(createContext());
    const player = state.players[0];
    const figure = player.figures[0];
    putFigureOn(state, figure.figureId, fieldCoord(state, { q: 0, r: 0 }));
    const target = findEmptyNeighbor(state, figure.coord as MagicArenaCoord);
    const field = getField(state, target);
    const enemy = state.players[1].figures[0];
    assert.ok(field);
    giveCard(state, 0, "storm_cloud");
    resolvePlannedAction(state, planCard(player.playerId, figure.figureId, "storm_cloud", target));
    assert.equal(state.delayedEffects[0]?.triggerRound, state.roundIndex + 1);
    assert.equal(state.delayedEffects[0]?.expiresRound, state.roundIndex + 2);
    state.roundIndex += 1;
    putFigureOn(state, enemy.figureId, target);
    resolveStormCloudsForSlot(state, state.roundIndex, 0);
    assert.equal(enemy.hp, 3);
    assert.equal(field.hp, field.maxHp);
    resolveStormCloudsForSlot(state, state.roundIndex, 0);
    assert.equal(enemy.hp, 3);
    resolveStormCloudsForSlot(state, state.roundIndex, 1);
    assert.equal(enemy.hp, 3);
    state.roundIndex += 1;
    resolveStormCloudsForSlot(state, state.roundIndex, 0);
    assert.equal(enemy.hp, 2);
    resolveStormCloudsForSlot(state, state.roundIndex, 1);
    assert.equal(enemy.hp, 2);
  }

  {
    const state = startMagicArenaRound(createContext());
    const player = state.players[0];
    const figure = player.figures[0];
    const mover = state.players[1].figures[0];
    putFigureOn(state, figure.figureId, fieldCoord(state, { q: 0, r: 0 }));
    const target = findEmptyNeighbor(state, figure.coord as MagicArenaCoord);
    const moverStart = getNeighbors(target).find((coord) => {
      const field = getField(state, coord);
      return Boolean(field?.exists && field.occupants.length === 0);
    });
    assert.ok(moverStart);
    putFigureOn(state, mover.figureId, moverStart);
    giveCard(state, 0, "storm_cloud");
    resolvePlannedAction(state, planCard(player.playerId, figure.figureId, "storm_cloud", target));
    state.roundIndex += 1;
    resolvePlannedAction(state, planBaseMove(mover.playerId, mover.figureId, [target]));
    assert.equal(mover.hp, 3);
    resolveStormCloudsForSlot(state, state.roundIndex, 0);
    assert.equal(mover.hp, 3);
    state.roundIndex += 1;
    resolveStormCloudsForSlot(state, state.roundIndex, 0);
    assert.equal(mover.hp, 2);
  }

  {
    const state = startMagicArenaRound(createContext());
    const player = state.players[0];
    const figure = player.figures[0];
    putFigureOn(state, figure.figureId, fieldCoord(state, { q: 0, r: 0 }));
    const target = findEmptyNeighbor(state, figure.coord as MagicArenaCoord);
    giveCard(state, 0, "storm_cloud");
    resolvePlannedAction(state, planCard(player.playerId, figure.figureId, "storm_cloud", target));
    state.roundIndex += 1;
    resolveStormCloudsForSlot(state, state.roundIndex, 3);
    assert.equal(state.delayedEffects.length, 1);
    state.roundIndex += 1;
    resolveStormCloudsForSlot(state, state.roundIndex, 3);
    assert.equal(state.delayedEffects.length, 0);
    assert.equal(state.boardObjects.some((object) => object.type === "storm_cloud_marker"), false);
  }

  {
    const state = startMagicArenaRound(createContext());
    const coord = fieldCoord(state, { q: 0, r: 0 });
    const field = getField(state, coord);
    assert.ok(field);
    field.specialType = null;
    state.currentRoundEvent = {
      id: "event-test",
      eventId: "flood_wave",
      name: "Maremoto",
      description: "prueba",
      roundIndex: state.roundIndex,
      markedCoords: [coord],
      direction: 0
    };
    resolveRoundEventEndEffects(state);
    assert.equal(field.hp, field.maxHp - 2);
    assert.equal(state.currentRoundEvent, undefined);
  }

  {
    const state = startMagicArenaRound(createContext());
    const player = state.players[0];
    const figure = player.figures[0];
    putFigureOn(state, figure.figureId, fieldCoord(state, { q: 0, r: 0 }));
    const target = findEmptyNeighbor(state, figure.coord as MagicArenaCoord);
    const field = getField(state, target);
    assert.ok(field);
    field.specialType = null;
    state.currentRoundEvent = {
      id: "event-test",
      eventId: "arcane_overload",
      name: "Sobrecarga arcana",
      description: "prueba",
      roundIndex: state.roundIndex,
      markedCoords: []
    };
    giveCard(state, 0, "fireball");
    resolvePlannedAction(state, planCard(player.playerId, figure.figureId, "fireball", target));
    assert.equal(field.hp, Math.max(0, field.maxHp - 3));
  }

  {
    const state = startMagicArenaRound(createContext());
    const coord = fieldCoord(state, { q: 0, r: 0 });
    const field = getField(state, coord);
    assert.ok(field);
    field.specialType = null;
    field.hp = 2;
    state.currentRoundEvent = {
      id: "event-test",
      eventId: "unstable_arena",
      name: "Arena inestable",
      description: "prueba",
      roundIndex: state.roundIndex,
      markedCoords: [coord]
    };
    resolveRoundEventEndEffects(state);
    assert.equal(field.hp, 1);
  }

  {
    const state = startMagicArenaRound(createContext());
    state.roundIndex = ARENA_DECAY_FIRST_ROUND - 1;
    const outerRing = setRingFieldsHp(state, 5, 7);
    assert.equal(resolveArenaDecayEndEffect(state), false);
    assert.equal(getField(state, outerRing[0])?.hp, 7);
  }

  {
    const state = startMagicArenaRound(createContext());
    state.roundIndex = ARENA_DECAY_FIRST_ROUND;
    const outerRing = setRingFieldsHp(state, 5, 7);
    const center = fieldCoord(state, { q: 0, r: 0 });
    const centerField = getField(state, center);
    const figure = state.players[0].figures[0];
    assert.ok(centerField);
    centerField.hp = centerField.maxHp;
    putFigureOn(state, figure.figureId, outerRing[0]);
    const figureHp = figure.hp;
    assert.equal(resolveArenaDecayEndEffect(state), true);
    assert.equal(getField(state, outerRing[0])?.hp, 7 - ARENA_DECAY_FIRST_DAMAGE);
    assert.equal(centerField.hp, centerField.maxHp);
    assert.equal(figure.hp, figureHp);
  }

  {
    const state = startMagicArenaRound(createContext());
    state.roundIndex = 1;
    const keptFigures = state.players
      .flatMap((player) => player.figures)
      .slice(0, ARENA_DECAY_FIRST_FIGURE_THRESHOLD)
      .map((figure) => figure.figureId);
    const outerRing = setRingFieldsHp(state, 5, 7);
    keepOnlyFiguresOnFields(state, keptFigures);
    assert.equal(resolveArenaDecayEndEffect(state), true);
    assert.equal(getField(state, outerRing[0])?.hp, 7 - ARENA_DECAY_FIRST_DAMAGE);
  }

  {
    const state = startMagicArenaRound(createContext());
    state.roundIndex = 1;
    const keptFigures = state.players
      .flatMap((player) => player.figures)
      .slice(0, ARENA_DECAY_SECOND_FIGURE_THRESHOLD)
      .map((figure) => figure.figureId);
    const outerRing = setRingFieldsHp(state, 5, 7);
    keepOnlyFiguresOnFields(state, keptFigures);
    assert.equal(resolveArenaDecayEndEffect(state), true);
    assert.equal(getField(state, outerRing[0])?.hp, 7 - ARENA_DECAY_SECOND_DAMAGE);
  }

  {
    const state = startMagicArenaRound(createContext());
    state.roundIndex = ARENA_DECAY_SECOND_ROUND;
    const outerRing = setRingFieldsHp(state, 5, 7);
    assert.equal(resolveArenaDecayEndEffect(state), true);
    assert.equal(getField(state, outerRing[0])?.hp, 7 - ARENA_DECAY_SECOND_DAMAGE);
  }

  {
    const state = startMagicArenaRound(createContext());
    state.roundIndex = ARENA_DECAY_FIRST_ROUND;
    const innerCoords = Object.values(state.board.fieldsByCoord)
      .filter((field) => field.exists && hexDistance(field.coord, { q: 0, r: 0 }) <= 2)
      .map((field) => ({ ...field.coord }));
    state.players
      .flatMap((player) => player.figures)
      .slice(0, ARENA_DECAY_FIRST_FIGURE_THRESHOLD + 1)
      .forEach((figure, index) => {
        putFigureOn(state, figure.figureId, innerCoords[index]);
      });
    const oldOuterRing = setRingFieldsHp(state, 5, 7);

    for (const coord of oldOuterRing) {
      const field = getField(state, coord);
      assert.ok(field);
      field.exists = false;
      field.hp = 0;
      field.occupants = [];
      field.specialType = null;
    }

    const newOuterRing = setRingFieldsHp(state, 4, 7);
    const innerField = getField(state, fieldCoord(state, { q: 0, r: 0 }));
    assert.ok(innerField);
    innerField.hp = innerField.maxHp;
    assert.equal(resolveArenaDecayEndEffect(state), true);
    assert.equal(getField(state, newOuterRing[0])?.hp, 7 - ARENA_DECAY_FIRST_DAMAGE);
    assert.equal(innerField.hp, innerField.maxHp);
  }

  {
    const state = createMagicArenaInitialState(createContext());
    assert.equal(Object.values(state.board.fieldsByCoord).length, 91);
    assert.equal(Object.values(state.board.fieldsByCoord).some((field) => field.specialType === "crystal"), false);

    for (const field of Object.values(state.board.fieldsByCoord)) {
      if (field.type === "weak") {
        assert.ok(field.maxHp >= 1 && field.maxHp <= 3);
      } else if (field.type === "normal") {
        assert.ok(field.maxHp >= 4 && field.maxHp <= 5);
      } else if (field.type === "strong") {
        assert.ok(field.maxHp >= 6 && field.maxHp <= 7);
      }
    }
  }

  {
    let spawned = false;

    for (let index = 0; index < 80 && !spawned; index += 1) {
      const state = createMagicArenaInitialState(createContext(2, 50_000 + index));
      state.rngSeed = `crystal-test-${index}`;
      state.roundIndex = 2;
      spawned = maybeSpawnCrystalFieldForRound(state);

      if (spawned) {
        const crystalField = Object.values(state.board.fieldsByCoord).find((field) => field.specialType === "crystal");
        assert.ok(crystalField);
        assert.equal(crystalField.occupants.length, 0);
      }
    }

    assert.equal(spawned, true);
  }

  {
    // Sequential planning mode: players take turns one move at a time.
    const context = createContext();
    context.roomSettings = { [magicArenaRoomSettingKeys.planningMode]: "sequential" };
    let state = startMagicArenaRound(context);
    let now = context.now;
    assert.equal(state.planningMode, "sequential");
    assert.equal(state.magicPhase, "execution");
    assert.equal(state.sequentialStage, "bombs");

    const tick = (advanceMs = 5_000) => {
      now += advanceMs;
      state = tickMagicArena(state, advanceMs, { ...context, now });
    };

    // Bomb step for slot 0 resolves (no bombs), then the first player's turn starts.
    for (let guard = 0; guard < 10 && (state.magicPhase as string) !== "planning"; guard += 1) {
      tick();
    }
    assert.equal(state.magicPhase, "planning");
    const initiativeOrder = getInitiativeOrder(state);
    assert.equal(state.activePlayerId, initiativeOrder[0]);
    assert.equal(state.planningSlotIndex, 0);

    // Sequential mode gives every player exactly one turn per round.
    assert.equal(getSlotsPerRound(state), 1);

    // A player who is not on turn cannot execute a move.
    const otherPlayerId = initiativeOrder[1];
    assert.ok(otherPlayerId);
    const otherFigure = state.players.find((player) => player.playerId === otherPlayerId)?.figures[0];
    assert.ok(otherFigure?.coord);
    const blocked = handleMagicArenaInput(
      state,
      {
        type: "magic-arena:play-turn",
        playerId: otherPlayerId,
        action: {
          figureId: otherFigure.figureId,
          actionKind: "base",
          baseActionId: "dig"
        },
        sentAt: now
      },
      { ...context, now }
    );
    assert.equal((blocked.plannedActionsByPlayer[otherPlayerId] ?? []).length, 0);
    assert.equal(blocked.magicPhase, "planning");
    state = blocked;

    // Play one full round: each player acts exactly once, strictly alternating.
    for (let turn = 0; turn < initiativeOrder.length; turn += 1) {
      assert.equal(state.magicPhase, "planning", `expected planning at turn ${turn}`);
      assert.equal(state.planningSlotIndex, 0);
      const activePlayerId = state.activePlayerId;
      assert.equal(activePlayerId, initiativeOrder[turn]);
      assert.ok(activePlayerId);
      const activePlayer = state.players.find((player) => player.playerId === activePlayerId);
      assert.ok(activePlayer);
      const figure = activePlayer.figures.find((entry) => entry.status === "alive" && entry.coord);
      assert.ok(figure, "expected a plannable figure");

      state = handleMagicArenaInput(
        state,
        {
          type: "magic-arena:play-turn",
          playerId: activePlayerId,
          action: {
            figureId: figure.figureId,
            actionKind: "base",
            baseActionId: "dig"
          },
          sentAt: now
        },
        { ...context, now }
      );
      assert.equal(state.magicPhase, "execution", "expected the turn to execute immediately");
      assert.equal(state.executionQueue.length, 1);

      // Run ticks until the next player's turn or the next round starts.
      for (let guard = 0; guard < 24 && state.magicPhase === "execution" && state.roundIndex === 1; guard += 1) {
        tick();
      }
    }

    assert.equal(state.roundIndex, 2, "expected round 2 after every player acted once");
  }
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) {
  runMagicArenaRuleTests();
  console.log("Magic Arena rule tests passed.");
}
