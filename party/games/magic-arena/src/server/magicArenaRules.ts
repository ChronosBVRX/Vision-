import { arenaText } from "../text.js";
import {
  createBaseRoundState,
  roundPhaseDurations,
  transitionRoundState,
  type BaseRoundState,
  type ScoreEntry,
  type ServerGameContext
} from "@open-party-lab/game-core";
import type {
  MagicArenaActionLogEntry,
  MagicArenaActiveActionState,
  MagicArenaBaseActionId,
  MagicArenaBoardState,
  MagicArenaBoardObjectState,
  MagicArenaCardId,
  MagicArenaCardInstanceState,
  MagicArenaControllerState,
  MagicArenaCoord,
  MagicArenaDelayedEffectState,
  MagicArenaFieldState,
  MagicArenaFigureState,
  MagicArenaInput,
  MagicArenaPendingBombState,
  MagicArenaPlannedAction,
  MagicArenaPlanningMode,
  MagicArenaPlayerState,
  MagicArenaPublicState,
  MagicArenaRoundEventId,
  MagicArenaRoundEventState
} from "../protocol.js";
import {
  ARENA_DECAY_FIRST_DAMAGE,
  ARENA_DECAY_FIRST_FIGURE_THRESHOLD,
  ARENA_DECAY_FIRST_ROUND,
  ARENA_DECAY_SECOND_DAMAGE,
  ARENA_DECAY_SECOND_FIGURE_THRESHOLD,
  ARENA_DECAY_SECOND_ROUND,
  BOARD_RADIUS,
  CRACKED_BREAK_CHANCE,
  CRYSTAL_SPAWN_CHANCE,
  CRYSTAL_SPAWN_START_ROUND,
  DIG_BASE_FIELD_DAMAGE,
  DIG_BURIED_BOMB_DAMAGE,
  DIG_BURIED_BOMB_CHANCE,
  DIG_BIG_CHEST_CHANCE,
  DIG_NOTHING_CHANCE,
  DIG_SMALL_CHEST_CHANCE,
  DIG_TREASURE_CHANCE,
  DIG_UNSTABLE_EXTRA_FIELD_DAMAGE,
  DIG_UNSTABLE_GROUND_CHANCE,
  EXECUTION_STEP_MS,
  FIGURE_MAX_HP,
  FIGURES_PER_PLAYER,
  MAGIC_ARENA_DEFAULT_PLANNING_MODE,
  MAGIC_ARENA_GAME_ID,
  MAGIC_ARENA_PLANNING_MODES,
  MAX_ACTION_LOG_ENTRIES,
  MAX_HAND_CARDS,
  magicArenaRoomSettingKeys,
  MAX_OCCUPANTS_PER_FIELD,
  PLANNED_ACTIONS_PER_ROUND,
  PUSH_CHAIN_LIMIT,
  ROUND_EVENT_CHANCE,
  ROUND_EVENT_START_ROUND,
  SEQUENTIAL_ACTIONS_PER_ROUND,
  STARTING_HAND_SIZE,
  STORM_CLOUD_ACTIVE_ROUNDS,
  THIN_FLOOR_EXTRA_DAMAGE_CHANCE,
  WATER_RESCUE_ROUNDS,
  magicArenaColors
} from "./magicArenaConfig.js";
import {
  createMagicArenaDeck,
  magicArenaCardDefinitions,
  magicArenaCardDefinitionsById
} from "./magicArenaCards.js";
import {
  assignMagicArenaSpecialFields,
  createMagicArenaBoard,
  getSpawnCandidates,
  getSpawnSideIndexes,
  makeSpawnFieldSafe,
  seededShuffle
} from "./magicArenaBoard.js";
import {
  addCoords,
  axialKey,
  getDirectionByIndex,
  getDirectionIndexBetween,
  getNeighbors,
  getStepToward,
  hexDistance,
  isInsideBoard,
  sameCoord
} from "./magicArenaHex.js";

export interface MagicArenaRuntimePlayer extends Omit<MagicArenaPlayerState, "handCount"> {
  hand: MagicArenaCardInstanceState[];
}

interface MagicArenaExecutionStep {
  kind: "bombs" | "action" | "storm_clouds" | "round_event_end" | "arena_decay";
  roundIndex: number;
  slotIndex: number;
  action?: MagicArenaPlannedAction;
}

export type MagicArenaSequentialStage = "bombs" | "turns" | "storm_clouds" | "round_end";

export interface MagicArenaRuntimeState extends BaseRoundState {
  gameId: "magic-arena";
  magicPhase: "setup" | "planning" | "execution" | "game_over";
  planningMode: MagicArenaPlanningMode;
  planningSlotIndex: number;
  turnOrderIndex: number;
  sequentialStage: MagicArenaSequentialStage;
  activePlayerId: string | null;
  roundIndex: number;
  currentSlot: number | null;
  initiativeStartIndex: number;
  players: MagicArenaRuntimePlayer[];
  board: MagicArenaBoardState;
  deck: MagicArenaCardInstanceState[];
  discardPile: MagicArenaCardInstanceState[];
  pendingBombs: MagicArenaPendingBombState[];
  boardObjects: MagicArenaBoardObjectState[];
  delayedEffects: MagicArenaDelayedEffectState[];
  plannedActionsByPlayer: Record<string, MagicArenaPlannedAction[]>;
  actionLog: MagicArenaActionLogEntry[];
  activeAction?: MagicArenaActiveActionState;
  currentRoundEvent?: MagicArenaRoundEventState;
  winnerPlayerId?: string;
  rngSeed: string;
  shuffleCounter: number;
  nextLogId: number;
  nextBombId: number;
  nextObjectId: number;
  nextDelayedEffectId: number;
  digCounter: number;
  rngCounter: number;
  executionQueue: MagicArenaExecutionStep[];
  nextExecutionAt: number | null;
}

const figureNames = [
  "Auri",
  "Brinx",
  "Cira",
  "Dax",
  "Elio",
  "Fenn",
  "Gala",
  "Hux",
  "Ivo",
  "Jora",
  "Kito",
  "Luma",
  "Mika",
  "Nox",
  "Orin",
  "Pavo",
  "Quin",
  "Runa",
  "Sora",
  "Tavi",
  "Uma",
  "Vex",
  "Wira",
  "Zeno"
];

function cloneState(state: MagicArenaRuntimeState): MagicArenaRuntimeState {
  return JSON.parse(JSON.stringify(state)) as MagicArenaRuntimeState;
}

function createSeed(context: ServerGameContext): string {
  return `${context.roomCode}:${context.roundNumber}:magic-arena`;
}

function createFigureId(playerIndex: number, figureIndex: number): string {
  return `f${playerIndex + 1}-${figureIndex + 1}`;
}

function createFigureName(playerIndex: number, figureIndex: number): string {
  return figureNames[(playerIndex * FIGURES_PER_PLAYER + figureIndex) % figureNames.length] ?? `Hero ${figureIndex + 1}`;
}

function appendLog(
  state: MagicArenaRuntimeState,
  message: string,
  slotIndex: number | null = state.currentSlot
): void {
  const entry: MagicArenaActionLogEntry = {
    id: state.nextLogId,
    roundIndex: state.roundIndex,
    slotIndex,
    message
  };
  state.nextLogId += 1;
  state.actionLog = [...state.actionLog, entry].slice(-MAX_ACTION_LOG_ENTRIES);
  state.message = message;
}

function appendActiveResult(state: MagicArenaRuntimeState, message: string): void {
  if (!state.activeAction) {
    return;
  }

  state.activeAction.resultLabel = state.activeAction.resultLabel
    ? `${state.activeAction.resultLabel}\n${message}`
    : message;
}

function rollUnit(state: MagicArenaRuntimeState, label: string): number {
  state.rngCounter += 1;
  const roll = seededShuffle(
    Array.from({ length: 10_000 }, (_, index) => index),
    `${state.rngSeed}:roll:${label}:${state.roundIndex}:${state.currentSlot ?? "planning"}:${state.rngCounter}`
  )[0] ?? 0;

  return roll / 10_000;
}

function getPlayer(state: MagicArenaRuntimeState, playerId: string): MagicArenaRuntimePlayer | undefined {
  return state.players.find((player) => player.playerId === playerId);
}

function getFigure(state: MagicArenaRuntimeState, figureId: string): MagicArenaFigureState | undefined {
  for (const player of state.players) {
    const figure = player.figures.find((entry) => entry.figureId === figureId);

    if (figure) {
      return figure;
    }
  }

  return undefined;
}

function getPlayerName(state: MagicArenaRuntimeState, playerId: string): string {
  return getPlayer(state, playerId)?.name ?? playerId;
}

function getFigureLabel(figure: MagicArenaFigureState): string {
  return figure.name;
}

function isHiddenFromDirectEnemyTarget(figure: MagicArenaFigureState, attackerPlayerId: string): boolean {
  return figure.playerId !== attackerPlayerId && figure.stealthUntilRound !== undefined;
}

export function getInitiativeOrder(state: Pick<MagicArenaRuntimeState, "players" | "initiativeStartIndex">): string[] {
  const playerIds = state.players.map((player) => player.playerId);

  if (playerIds.length === 0) {
    return [];
  }

  const start = ((state.initiativeStartIndex % playerIds.length) + playerIds.length) % playerIds.length;
  return [...playerIds.slice(start), ...playerIds.slice(0, start)];
}

export function getField(
  state: Pick<MagicArenaRuntimeState, "board">,
  coord: MagicArenaCoord | undefined
): MagicArenaFieldState | undefined {
  return coord ? state.board.fieldsByCoord[axialKey(coord)] : undefined;
}

export function isFieldEnterable(state: MagicArenaRuntimeState, coord: MagicArenaCoord): boolean {
  const field = getField(state, coord);
  return Boolean(field?.exists && field.occupants.length < MAX_OCCUPANTS_PER_FIELD);
}

function getLiveFiguresAtCoord(state: MagicArenaRuntimeState, coord: MagicArenaCoord): MagicArenaFigureState[] {
  const field = getField(state, coord);

  if (!field) {
    return [];
  }

  return field.occupants
    .map((figureId) => getFigure(state, figureId))
    .filter((figure): figure is MagicArenaFigureState => Boolean(figure && figure.status === "alive"));
}

function getFiguresAtCoordIncludingWater(state: MagicArenaRuntimeState, coord: MagicArenaCoord): MagicArenaFigureState[] {
  const figures = getLiveFiguresAtCoord(state, coord);

  for (const player of state.players) {
    for (const figure of player.figures) {
      if (figure.status === "in_water" && sameCoord(figure.waterCoord, coord)) {
        figures.push(figure);
      }
    }
  }

  return figures;
}

function removeFigureFromField(state: MagicArenaRuntimeState, figure: MagicArenaFigureState): void {
  const field = getField(state, figure.coord);

  if (!field) {
    return;
  }

  field.occupants = field.occupants.filter((figureId) => figureId !== figure.figureId);
}

function placeFigureOnField(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, coord: MagicArenaCoord): void {
  const field = getField(state, coord);

  if (!field) {
    return;
  }

  if (!field.occupants.includes(figure.figureId)) {
    field.occupants.push(figure.figureId);
  }

  figure.status = "alive";
  figure.coord = { ...coord };
  delete figure.waterCoord;
  delete figure.rescueDeadlineRound;
}

function moveFigureToCoord(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, coord: MagicArenaCoord): void {
  removeFigureFromField(state, figure);
  placeFigureOnField(state, figure, coord);
}

function isActionCapableFigure(state: MagicArenaRuntimeState, figure: MagicArenaFigureState): boolean {
  if (figure.status !== "alive" || !figure.coord) {
    return false;
  }

  const field = getField(state, figure.coord);
  return Boolean(field?.exists);
}

function getBoardObjectsAt(
  state: MagicArenaRuntimeState,
  coord: MagicArenaCoord,
  type?: MagicArenaBoardObjectState["type"]
): MagicArenaBoardObjectState[] {
  return state.boardObjects.filter((object) => sameCoord(object.coord, coord) && (!type || object.type === type));
}

function hasBlockingBoardObjectAt(state: MagicArenaRuntimeState, coord: MagicArenaCoord): boolean {
  return state.boardObjects.some(
    (object) =>
      sameCoord(object.coord, coord) &&
      ["bomb", "pitfall", "magic_mine", "storm_cloud_marker"].includes(object.type)
  );
}

function removeBoardObject(state: MagicArenaRuntimeState, objectId: string): void {
  state.boardObjects = state.boardObjects.filter((object) => object.id !== objectId);
}

function createBoardObject(
  state: MagicArenaRuntimeState,
  object: Omit<MagicArenaBoardObjectState, "id" | "createdRound" | "createdSlot">
): MagicArenaBoardObjectState {
  const boardObject: MagicArenaBoardObjectState = {
    ...object,
    id: `object-${state.nextObjectId}`,
    createdRound: state.roundIndex,
    createdSlot: state.currentSlot
  };
  state.nextObjectId += 1;
  state.boardObjects.push(boardObject);
  return boardObject;
}

function isFreeIntactField(state: MagicArenaRuntimeState, coord: MagicArenaCoord | undefined): boolean {
  const field = getField(state, coord);
  return Boolean(field?.exists && field.occupants.length < MAX_OCCUPANTS_PER_FIELD);
}

/**
 * Planned mode plans four actions per round, sequential mode gives every player a single turn.
 */
export function getSlotsPerRound(state: Pick<MagicArenaRuntimeState, "planningMode">): number {
  return state.planningMode === "sequential" ? SEQUENTIAL_ACTIONS_PER_ROUND : PLANNED_ACTIONS_PER_ROUND;
}

export function playerHasPlannableFigure(state: MagicArenaRuntimeState, playerId: string): boolean {
  const player = getPlayer(state, playerId);

  if (!player) {
    return false;
  }

  const reservedFigureIds = new Set(
    (state.plannedActionsByPlayer[playerId] ?? [])
      .filter((action) => action.slotIndex !== state.planningSlotIndex)
      .map((action) => action.figureId)
  );

  return player.figures.some(
    (figure) => isActionCapableFigure(state, figure) && !reservedFigureIds.has(figure.figureId)
  );
}

export function getRequiredPlanningSlots(state: MagicArenaRuntimeState, playerId: string): number {
  const player = getPlayer(state, playerId);

  if (!player) {
    return 0;
  }

  if (state.planningMode === "sequential") {
    return state.activePlayerId === playerId && playerHasPlannableFigure(state, playerId) ? 1 : 0;
  }

  return Math.min(
    PLANNED_ACTIONS_PER_ROUND,
    player.figures.filter((figure) => isActionCapableFigure(state, figure)).length
  );
}

function hasLivingPresence(player: MagicArenaRuntimePlayer): boolean {
  return player.figures.some((figure) => figure.status !== "eliminated");
}

function drawCards(state: MagicArenaRuntimeState, player: MagicArenaRuntimePlayer, count: number): number {
  let drawn = 0;

  while (drawn < count && player.hand.length < MAX_HAND_CARDS) {
    if (state.deck.length === 0 && state.discardPile.length > 0) {
      state.shuffleCounter += 1;
      state.deck = seededShuffle(state.discardPile, `${state.rngSeed}:reshuffle:${state.shuffleCounter}`);
      state.discardPile = [];
      appendLog(state, "Se reorganiza la pila de descartes.", null);
    }

    const card = state.deck.shift();

    if (!card) {
      return drawn;
    }

    player.hand.push(card);
    drawn += 1;
  }

  return drawn;
}

function randomizePlayerHand(state: MagicArenaRuntimeState, player: MagicArenaRuntimePlayer): void {
  player.hand = seededShuffle(
    player.hand,
    `${state.rngSeed}:hand:${state.roundIndex}:${player.playerId}:${player.hand.map((card) => card.instanceId).join("|")}`
  );
}

function buildRoundEventActiveAction(
  state: MagicArenaRuntimeState,
  now: number
): MagicArenaActiveActionState | undefined {
  return state.currentRoundEvent
    ? {
        kind: "round_event",
        roundIndex: state.roundIndex,
        slotIndex: null,
        roundEventId: state.currentRoundEvent.eventId,
        label: `Rundenereignis: ${state.currentRoundEvent.name}\n${state.currentRoundEvent.description}`,
        affectedCoords: state.currentRoundEvent.markedCoords.map((coord) => ({ ...coord })),
        at: now
      }
    : undefined;
}

function beginPlanningRound(state: MagicArenaRuntimeState, now: number): MagicArenaRuntimeState {
  state.magicPhase = "planning";
  state.currentSlot = null;
  state.planningSlotIndex = 0;
  state.turnOrderIndex = 0;
  state.sequentialStage = "bombs";
  state.activePlayerId = null;
  state.activeAction = buildRoundEventActiveAction(state, now);
  state.nextExecutionAt = null;
  state.executionQueue = [];
  state.plannedActionsByPlayer = {};
  state.players = state.players.map((player) => ({
    ...player,
    ready: getRequiredPlanningSlots(state, player.playerId) === 0
  }));

  for (const player of state.players) {
    randomizePlayerHand(state, player);
  }

  appendLog(state, `Runde ${state.roundIndex}: Planungsphase beginnt.`, null);
  state.updatedAt = now;
  return state;
}

function beginSequentialRound(state: MagicArenaRuntimeState, now: number): MagicArenaRuntimeState {
  state.magicPhase = "execution";
  state.currentSlot = null;
  state.planningSlotIndex = 0;
  state.turnOrderIndex = 0;
  state.sequentialStage = "bombs";
  state.activePlayerId = null;
  state.activeAction = buildRoundEventActiveAction(state, now);
  state.plannedActionsByPlayer = {};
  state.players = state.players.map((player) => ({
    ...player,
    ready: false
  }));

  for (const player of state.players) {
    randomizePlayerHand(state, player);
  }

  state.executionQueue = [
    {
      kind: "bombs",
      roundIndex: state.roundIndex,
      slotIndex: 0
    }
  ];
  state.nextExecutionAt = now;
  appendLog(state, `Runde ${state.roundIndex}: Zug-fuer-Zug-Phase beginnt.`, null);
  state.updatedAt = now;
  return state;
}

function beginRound(state: MagicArenaRuntimeState, now: number): MagicArenaRuntimeState {
  return state.planningMode === "sequential"
    ? beginSequentialRound(state, now)
    : beginPlanningRound(state, now);
}

function startNextSequentialTurn(state: MagicArenaRuntimeState, now: number): MagicArenaRuntimeState {
  const initiativeOrder = getInitiativeOrder(state);

  while (state.turnOrderIndex < initiativeOrder.length) {
    const playerId = initiativeOrder[state.turnOrderIndex];

    if (playerId && playerHasPlannableFigure(state, playerId)) {
      state.magicPhase = "planning";
      state.activePlayerId = playerId;
      state.currentSlot = state.planningSlotIndex;
      state.nextExecutionAt = null;
      state.executionQueue = [];
      state.players = state.players.map((player) => ({
        ...player,
        ready: false
      }));
      appendLog(state, `${getPlayerName(state, playerId)} ist am Zug.`, state.planningSlotIndex);
      state.updatedAt = now;
      return state;
    }

    state.turnOrderIndex += 1;
  }

  state.activePlayerId = null;
  state.sequentialStage = "storm_clouds";
  state.magicPhase = "execution";
  state.executionQueue = [
    {
      kind: "storm_clouds",
      roundIndex: state.roundIndex,
      slotIndex: state.planningSlotIndex
    }
  ];
  state.nextExecutionAt = now;
  state.updatedAt = now;
  return state;
}

function advanceSequentialFlow(state: MagicArenaRuntimeState, now: number): MagicArenaRuntimeState {
  if (state.sequentialStage === "bombs") {
    state.sequentialStage = "turns";
    state.turnOrderIndex = 0;
    return startNextSequentialTurn(state, now);
  }

  if (state.sequentialStage === "turns") {
    state.turnOrderIndex += 1;
    return startNextSequentialTurn(state, now);
  }

  if (state.sequentialStage === "storm_clouds") {
    const lastSlotIndex = getSlotsPerRound(state) - 1;

    if (state.planningSlotIndex < lastSlotIndex) {
      state.planningSlotIndex += 1;
      state.sequentialStage = "bombs";
      state.magicPhase = "execution";
      state.executionQueue = [
        {
          kind: "bombs",
          roundIndex: state.roundIndex,
          slotIndex: state.planningSlotIndex
        }
      ];
      state.nextExecutionAt = now;
      state.updatedAt = now;
      return state;
    }

    state.sequentialStage = "round_end";
    state.magicPhase = "execution";
    state.executionQueue = [];

    if (hasRoundEventEndEffect(state.currentRoundEvent)) {
      state.executionQueue.push({
        kind: "round_event_end",
        roundIndex: state.roundIndex,
        slotIndex: lastSlotIndex
      });
    }

    state.executionQueue.push({
      kind: "arena_decay",
      roundIndex: state.roundIndex,
      slotIndex: lastSlotIndex
    });
    state.nextExecutionAt = now;
    state.updatedAt = now;
    return state;
  }

  return finishExecutionRound(state, now);
}

function createPlayers(
  context: ServerGameContext,
  board: MagicArenaBoardState
): MagicArenaRuntimePlayer[] {
  const players = context.players.slice(0, 4);
  const sideIndexes = getSpawnSideIndexes(players.length);
  const usedSpawnKeys = new Set<string>();

  return players.map((player, playerIndex) => {
    const sideIndex = sideIndexes[playerIndex] ?? playerIndex;
    const candidates = getSpawnCandidates(sideIndex, BOARD_RADIUS);
    const figures: MagicArenaFigureState[] = [];

    for (let figureIndex = 0; figureIndex < FIGURES_PER_PLAYER; figureIndex += 1) {
      const spawnCoord =
        candidates.find((coord) => !usedSpawnKeys.has(axialKey(coord))) ??
        candidates[figureIndex % Math.max(1, candidates.length)] ??
        { q: 0, r: 0 };
      const spawnKey = axialKey(spawnCoord);
      const figure: MagicArenaFigureState = {
        figureId: createFigureId(playerIndex, figureIndex),
        playerId: player.id,
        name: createFigureName(playerIndex, figureIndex),
        hp: FIGURE_MAX_HP,
        maxHp: FIGURE_MAX_HP,
        status: "alive",
        coord: { ...spawnCoord }
      };

      usedSpawnKeys.add(spawnKey);
      makeSpawnFieldSafe(board, spawnCoord);
      board.fieldsByCoord[spawnKey]?.occupants.push(figure.figureId);
      figures.push(figure);
    }

    return {
      playerId: player.id,
      name: player.name || `P${playerIndex + 1}`,
      color: player.color || magicArenaColors[playerIndex] || "#38bdf8",
      hand: [],
      figures,
      ready: false,
      connected: player.connected
    };
  });
}

export function resolveMagicArenaPlanningMode(
  roomSettings: Readonly<Record<string, unknown>> | undefined
): MagicArenaPlanningMode {
  const configured = roomSettings?.[magicArenaRoomSettingKeys.planningMode];

  return typeof configured === "string" &&
    (MAGIC_ARENA_PLANNING_MODES as readonly string[]).includes(configured)
    ? (configured as MagicArenaPlanningMode)
    : MAGIC_ARENA_DEFAULT_PLANNING_MODE;
}

export function createMagicArenaInitialState(context: ServerGameContext): MagicArenaRuntimeState {
  const rngSeed = createSeed(context);
  const board = createMagicArenaBoard(rngSeed);
  const players = createPlayers(context, board);
  assignMagicArenaSpecialFields(board, rngSeed);
  const deck = seededShuffle(createMagicArenaDeck(), `${rngSeed}:deck`);
  const planningMode = resolveMagicArenaPlanningMode(context.roomSettings);
  const state: MagicArenaRuntimeState = {
    ...createBaseRoundState("round_intro", context.now, {
      durationMs: roundPhaseDurations.roundIntroMs,
      message:
        planningMode === "sequential"
          ? "Magic Arena: muévete paso a paso y sobrevive en la isla hexagonal."
          : "Magic Arena: Planifica cuatro acciones y sobrevive a la Isla Hex."
    }),
    gameId: MAGIC_ARENA_GAME_ID,
    magicPhase: "setup",
    planningMode,
    planningSlotIndex: 0,
    turnOrderIndex: 0,
    sequentialStage: "bombs",
    activePlayerId: null,
    roundIndex: 1,
    currentSlot: null,
    initiativeStartIndex: 0,
    players,
    board,
    deck,
    discardPile: [],
    pendingBombs: [],
    boardObjects: [],
    delayedEffects: [],
    plannedActionsByPlayer: {},
    actionLog: [],
    currentRoundEvent: undefined,
    winnerPlayerId: undefined,
    rngSeed,
    shuffleCounter: 0,
    nextLogId: 1,
    nextBombId: 1,
    nextObjectId: 1,
    nextDelayedEffectId: 1,
    digCounter: 0,
    rngCounter: 0,
    executionQueue: [],
    nextExecutionAt: null
  };

  for (const player of state.players) {
    drawCards(state, player, STARTING_HAND_SIZE);
    randomizePlayerHand(state, player);
  }

  return state;
}

export function startMagicArenaRound(context: ServerGameContext): MagicArenaRuntimeState {
  const state = createMagicArenaInitialState(context);
  return beginRound(
    transitionRoundState(state, "playing", context.now, {
      startedAt: context.now,
      message:
        state.planningMode === "sequential"
          ? "Comienza Hexagonia. Muévete, movimiento a movimiento, uno tras otro."
          : "Comienza Hexagonia. Planifica tus acciones de forma encubierta."
    }),
    context.now
  );
}

export function createMagicArenaDebugState(
  playerCount = 2,
  now = Date.now(),
  planningMode: MagicArenaPlanningMode = MAGIC_ARENA_DEFAULT_PLANNING_MODE
): MagicArenaRuntimeState {
  const safePlayerCount = Math.max(2, Math.min(4, Math.floor(playerCount)));
  const context: ServerGameContext = {
    roomCode: "DEBUG",
    roundNumber: 1,
    players: Array.from({ length: safePlayerCount }, (_, index) => ({
      id: `debug-p${index + 1}`,
      name: `Debug P${index + 1}`,
      color: magicArenaColors[index] ?? "#38bdf8",
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
      description: "Depurar",
      minPlayers: 2,
      maxPlayers: 4,
      hostView: "MagicArenaHostScene",
      controllerView: "magic-arena",
      controllerLayout: "magic_arena",
      supportsTeams: false,
      estimatedRoundDurationMs: 240_000
    },
    previousRound: null,
    roomSettings: {
      [magicArenaRoomSettingKeys.planningMode]: planningMode
    }
  };

  return startMagicArenaRound(context);
}

function isValidCoord(coord: MagicArenaCoord | undefined): coord is MagicArenaCoord {
  return Boolean(
    coord &&
      Number.isInteger(coord.q) &&
      Number.isInteger(coord.r) &&
      Math.abs(coord.q) < 100 &&
      Math.abs(coord.r) < 100
  );
}

function isValidPathFrom(start: MagicArenaCoord, path: MagicArenaCoord[] | undefined, maxLength: number): boolean {
  if (!path || path.length < 1 || path.length > maxLength) {
    return false;
  }

  let cursor = start;

  for (const step of path) {
    if (!isValidCoord(step) || hexDistance(cursor, step) !== 1) {
      return false;
    }

    cursor = step;
  }

  return true;
}

function isCardReservedInOtherSlot(
  plannedActions: MagicArenaPlannedAction[],
  slotIndex: number,
  cardInstanceId: string
): boolean {
  return plannedActions.some((action) => action.slotIndex !== slotIndex && action.cardInstanceId === cardInstanceId);
}

function isFigureReservedInOtherSlot(
  plannedActions: MagicArenaPlannedAction[],
  slotIndex: number,
  figureId: string
): boolean {
  return plannedActions.some((action) => action.slotIndex !== slotIndex && action.figureId === figureId);
}

function getCardFromHand(
  player: MagicArenaRuntimePlayer,
  cardInstanceId: string | undefined
): MagicArenaCardInstanceState | undefined {
  return cardInstanceId ? player.hand.find((card) => card.instanceId === cardInstanceId) : undefined;
}

function validateRangeFromFigure(
  figure: MagicArenaFigureState,
  targetCoord: MagicArenaCoord | undefined,
  range: number
): boolean {
  return Boolean(figure.coord && isValidCoord(targetCoord) && hexDistance(figure.coord, targetCoord) <= range);
}

export function validatePlannedAction(
  state: MagicArenaRuntimeState,
  playerId: string,
  slotIndex: number,
  action: Omit<MagicArenaPlannedAction, "playerId" | "slotIndex"> | null
): { ok: true; action: MagicArenaPlannedAction | null } | { ok: false; error: string } {
  if (state.phase !== "playing" || state.magicPhase !== "planning") {
    return { ok: false, error: "No en la fase de planificación." };
  }

  const player = getPlayer(state, playerId);

  if (!player) {
    return { ok: false, error: "Jugador no encontrado." };
  }

  if (player.ready) {
    return { ok: false, error: "Tu plan ya está bloqueado." };
  }

  if (state.planningMode === "sequential") {
    if (state.activePlayerId !== playerId) {
      return { ok: false, error: "No es tu turno ahora." };
    }

    if (!Number.isInteger(slotIndex) || slotIndex !== state.planningSlotIndex) {
      return { ok: false, error: "Este espacio no está disponible." };
    }
  } else if (!Number.isInteger(slotIndex) || slotIndex < 0 || slotIndex >= getRequiredPlanningSlots(state, playerId)) {
    return { ok: false, error: "Este espacio no está disponible." };
  }

  if (!action) {
    return { ok: true, action: null };
  }

  const plannedActions = state.plannedActionsByPlayer[playerId] ?? [];
  const figure = player.figures.find((entry) => entry.figureId === action.figureId);

  if (!figure || !isActionCapableFigure(state, figure)) {
    return { ok: false, error: "Este personaje no puede actuar." };
  }

  if (isFigureReservedInOtherSlot(plannedActions, slotIndex, figure.figureId)) {
    return { ok: false, error: "Esta cifra ya está prevista." };
  }

  if (action.actionKind === "base") {
    if (action.baseActionId === "move") {
      if (!figure.coord || !isValidPathFrom(figure.coord, action.path, 2)) {
        return { ok: false, error: "La ruta de movimiento no es válida." };
      }
    } else if (action.baseActionId === "punch") {
      if (!validateRangeFromFigure(figure, action.targetCoord, 1)) {
        return { ok: false, error: "El boxeo requiere un objetivo en tu propio campo o en el adyacente." };
      }
    } else if (action.baseActionId === "dig") {
      // Graben zielt immer auf das eigene Feld und braucht keine weiteren Angaben.
    } else {
      return { ok: false, error: "Unbekannte Basisaktion." };
    }
  } else {
    const card = getCardFromHand(player, action.cardInstanceId);

    if (!card || !action.cardId || card.cardId !== action.cardId) {
      return { ok: false, error: "Esta carta no está en tu mano." };
    }

    if (isCardReservedInOtherSlot(plannedActions, slotIndex, card.instanceId)) {
      return { ok: false, error: "Esta tarjeta ya está reservada." };
    }

    const definition = magicArenaCardDefinitionsById[action.cardId];

    if (!definition) {
      return { ok: false, error: "Esta carta ya no está disponible en Magic Arena." };
    }

    const range = definition.range;

    switch (action.cardId) {
      case "sprint":
        if (!figure.coord || !isValidPathFrom(figure.coord, action.path, 4)) {
          return { ok: false, error: "La ruta del sprint no es válida." };
        }
        break;
      case "charge_run":
        if (getDirectionByIndex(action.direction ?? -1) === null || !Number.isInteger(action.distance) || (action.distance ?? 0) < 1 || (action.distance ?? 0) > 3) {
          return { ok: false, error: "Correr con empuje requiere dirección y distancia 1-3." };
        }
        break;
      case "earthquake":
      case "stealth":
      case "shockwave":
        break;
      case "teleport":
        if (range !== null && !validateRangeFromFigure(figure, action.targetCoord, range)) {
          return { ok: false, error: "El objetivo está fuera de su alcance." };
        }
        if (!isFreeIntactField(state, action.targetCoord)) {
          return { ok: false, error: "El teletransporte necesita un campo despejado e intacto." };
        }
        break;
      case "pitfall":
      case "magic_mine":
        if (range !== null && !validateRangeFromFigure(figure, action.targetCoord, range)) {
          return { ok: false, error: "El objetivo está fuera de su alcance." };
        }
        if (!isFreeIntactField(state, action.targetCoord) || (action.targetCoord && hasBlockingBoardObjectAt(state, action.targetCoord))) {
          return { ok: false, error: "Esta trampa necesita un campo libre sin objeto." };
        }
        break;
      case "storm_cloud":
        if (range !== null && !validateRangeFromFigure(figure, action.targetCoord, range)) {
          return { ok: false, error: "El objetivo está fuera de su alcance." };
        }
        if (!action.targetCoord || hasBlockingBoardObjectAt(state, action.targetCoord)) {
          return { ok: false, error: "Thundercloud necesita un campo de eventos gratuito." };
        }
        break;
      case "kraken":
        if (range !== null && !validateRangeFromFigure(figure, action.targetCoord, range)) {
          return { ok: false, error: "El objetivo está fuera de su alcance." };
        }
        if (!action.targetCoord || !getFiguresAtCoordIncludingWater(state, action.targetCoord).some((entry) => entry.status === "in_water")) {
          return { ok: false, error: "El pulpo necesita una figura en el agua." };
        }
        break;
      case "bomb":
        if (range !== null && !validateRangeFromFigure(figure, action.targetCoord, range)) {
          return { ok: false, error: "El objetivo está fuera de su alcance." };
        }
        if (action.targetCoord && !isInsideBoard(action.targetCoord, state.board.radius)) {
          return { ok: false, error: "La bomba necesita un objetivo en el mapa." };
        }
        break;
      default:
        if (range !== null && !validateRangeFromFigure(figure, action.targetCoord, range)) {
          return { ok: false, error: "El objetivo está fuera de su alcance." };
        }
        if (action.cardId === "hammer_blow" && sameCoord(figure.coord, action.targetCoord) && getDirectionByIndex(action.direction ?? -1) === null) {
          return { ok: false, error: "La patada de martillo en tu propio campo necesita una dirección de empuje." };
        }
        break;
    }
  }

  return {
    ok: true,
    action: {
      ...action,
      playerId,
      slotIndex
    }
  };
}

function consumePlannedCard(state: MagicArenaRuntimeState, action: MagicArenaPlannedAction): boolean {
  if (action.actionKind !== "card" || !action.cardInstanceId) {
    return true;
  }

  const player = getPlayer(state, action.playerId);
  const handIndex = player?.hand.findIndex((card) => card.instanceId === action.cardInstanceId) ?? -1;

  if (!player || handIndex < 0) {
    return false;
  }

  const [card] = player.hand.splice(handIndex, 1);

  if (card) {
    state.discardPile.push(card);
  }

  return true;
}

function selectEnemyFigureOnCoord(
  state: MagicArenaRuntimeState,
  coord: MagicArenaCoord,
  attackerPlayerId: string
): MagicArenaFigureState | undefined {
  return getLiveFiguresAtCoord(state, coord)
    .filter((figure) => figure.playerId !== attackerPlayerId)
    .filter((figure) => !isHiddenFromDirectEnemyTarget(figure, attackerPlayerId))
    .sort((left, right) => left.hp - right.hp || left.figureId.localeCompare(right.figureId))[0];
}

function selectMagnetTarget(
  state: MagicArenaRuntimeState,
  coord: MagicArenaCoord,
  actorPlayerId: string
): MagicArenaFigureState | undefined {
  return getLiveFiguresAtCoord(state, coord)
    .filter((figure) => !isHiddenFromDirectEnemyTarget(figure, actorPlayerId))
    .sort((left, right) => {
      const leftEnemy = left.playerId !== actorPlayerId ? 0 : 1;
      const rightEnemy = right.playerId !== actorPlayerId ? 0 : 1;
      return leftEnemy - rightEnemy || left.hp - right.hp || left.figureId.localeCompare(right.figureId);
    })[0];
}

export function eliminateFigure(
  state: MagicArenaRuntimeState,
  figureId: string,
  source: string
): void {
  const figure = getFigure(state, figureId);

  if (!figure || figure.status === "eliminated") {
    return;
  }

  removeFigureFromField(state, figure);
  figure.status = "eliminated";
  figure.hp = Math.max(0, figure.hp);
  delete figure.coord;
  delete figure.waterCoord;
  delete figure.rescueDeadlineRound;
  delete figure.stealthUntilRound;
  appendLog(state, `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} wurde eliminiert (${source}).`);
}

export function makeFigureFallIntoWater(
  state: MagicArenaRuntimeState,
  figureId: string,
  waterCoord: MagicArenaCoord,
  source: string
): void {
  const figure = getFigure(state, figureId);

  if (!figure || figure.status !== "alive") {
    return;
  }

  removeFigureFromField(state, figure);
  figure.status = "in_water";
  figure.waterCoord = { ...waterCoord };
  figure.rescueDeadlineRound = state.roundIndex + WATER_RESCUE_ROUNDS;
  delete figure.coord;
  appendLog(state, `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} faellt ins Wasser (${source}).`);

  const rescuer = getPlayer(state, figure.playerId)?.figures
    .filter((entry) => entry.status === "alive" && entry.coord && hexDistance(entry.coord, waterCoord) === 1)
    .sort((left, right) => (left.coord ? axialKey(left.coord) : "").localeCompare(right.coord ? axialKey(right.coord) : ""))[0];

  if (rescuer?.coord) {
    tryAutoRescueAround(state, rescuer.coord, figure.playerId);
  }
}

export function applyFigureDamage(
  state: MagicArenaRuntimeState,
  figureId: string,
  amount: number,
  source: string
): void {
  const figure = getFigure(state, figureId);

  if (!figure || figure.status === "eliminated" || amount <= 0) {
    return;
  }

  figure.hp -= amount;
  appendLog(state, `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} erhaelt ${amount} Schaden (${source}).`);

  if (figure.hp <= 0) {
    eliminateFigure(state, figure.figureId, source);
  }
}

export function destroyField(
  state: MagicArenaRuntimeState,
  coord: MagicArenaCoord,
  source: string
): void {
  const field = getField(state, coord);

  if (!field?.exists) {
    return;
  }

  const occupants = [...field.occupants];
  const specialType = field.specialType;
  field.exists = false;
  field.hp = 0;
  field.specialType = null;
  field.occupants = [];
  appendLog(state, `Feld ${coord.q},${coord.r} wurde zerstoert (${source}).`);

  for (const figureId of occupants) {
    makeFigureFallIntoWater(state, figureId, coord, "Feldzerstoerung");
  }

  if (specialType === "explosive") {
    appendLog(state, `Sprengfeld ${coord.q},${coord.r} explodiert.`);

    for (const neighbor of getNeighbors(coord)) {
      const neighborField = getField(state, neighbor);

      if (neighborField?.exists) {
        damageFiguresAtCoord(state, neighbor, 1, "Sprengfeld");
        applyFieldDamage(state, neighbor, 1, "Sprengfeld");
      } else {
        for (const figure of getFiguresAtCoordIncludingWater(state, neighbor)) {
          if (figure.status === "in_water") {
            applyFigureDamage(state, figure.figureId, 1, "Sprengfeld");
          }
        }
      }
    }
  }
}

export function applyFieldDamage(
  state: MagicArenaRuntimeState,
  coord: MagicArenaCoord,
  amount: number,
  source: string,
  options: { arcaneOverloadTarget?: boolean; thinFloorEligible?: boolean } = {}
): void {
  const field = getField(state, coord);

  if (!field?.exists || amount <= 0) {
    return;
  }

  const overloadBonus = state.currentRoundEvent?.eventId === "arcane_overload" && options.arcaneOverloadTarget ? 1 : 0;
  const totalDamage = amount + overloadBonus;

  field.hp = Math.max(0, field.hp - totalDamage);
  appendLog(
    state,
    `Feld ${coord.q},${coord.r} erhaelt ${totalDamage} Schaden (${source}${overloadBonus > 0 ? ", Arkane Ueberladung" : ""}).`
  );

  if (field.hp <= 0) {
    destroyField(state, coord, source);
    return;
  }

  if (field.specialType === "cracked" && rollUnit(state, `cracked:${axialKey(coord)}:${source}`) < CRACKED_BREAK_CHANCE) {
    appendLog(state, `Rissfeld ${coord.q},${coord.r} bricht spontan.`);
    destroyField(state, coord, "Rissfeld");
    return;
  }

  if (
    state.currentRoundEvent?.eventId === "thin_floor" &&
    options.thinFloorEligible !== false &&
    rollUnit(state, `thin-floor:${axialKey(coord)}:${source}`) < THIN_FLOOR_EXTRA_DAMAGE_CHANCE
  ) {
    appendLog(state, `Duennen Boden auf ${coord.q},${coord.r} gibt weiter nach.`);
    applyFieldDamage(state, coord, 1, "Duennen Boden", { thinFloorEligible: false });
  }
}

function findRescueLandingCoord(
  state: MagicArenaRuntimeState,
  waterCoord: MagicArenaCoord,
  rescuerCoord: MagicArenaCoord
): MagicArenaCoord | null {
  const candidates = getNeighbors(waterCoord)
    .filter((candidate) => hexDistance(candidate, rescuerCoord) === 1)
    .filter((candidate) => {
      const field = getField(state, candidate);
      return Boolean(field?.exists && field.occupants.length < MAX_OCCUPANTS_PER_FIELD);
    })
    .sort(
      (left, right) =>
        axialKey(left).localeCompare(axialKey(right))
    );

  return candidates[0] ? { ...candidates[0] } : null;
}

export function tryAutoRescueAround(
  state: MagicArenaRuntimeState,
  coord: MagicArenaCoord,
  playerId: string
): boolean {
  const field = getField(state, coord);

  if (!field?.exists) {
    return false;
  }

  const rescuer = field.occupants
    .map((figureId) => getFigure(state, figureId))
    .filter((figure): figure is MagicArenaFigureState => Boolean(figure?.playerId === playerId && figure.status === "alive"))
    .sort((left, right) => left.figureId.localeCompare(right.figureId))[0];

  if (!rescuer) {
    return false;
  }

  const candidates = getPlayer(state, playerId)?.figures
    .filter(
      (figure) =>
        figure.status === "in_water" &&
        figure.waterCoord &&
        hexDistance(coord, figure.waterCoord) === 1
    )
    .sort(
      (left, right) =>
        (left.rescueDeadlineRound ?? Number.MAX_SAFE_INTEGER) -
          (right.rescueDeadlineRound ?? Number.MAX_SAFE_INTEGER) ||
        left.figureId.localeCompare(right.figureId)
    ) ?? [];
  const rescued = candidates[0];

  if (!rescued) {
    return false;
  }

  const landingCoord = rescued.waterCoord ? findRescueLandingCoord(state, rescued.waterCoord, coord) : null;

  if (!landingCoord) {
    const message = `${getFigureLabel(rescuer)} versucht ${getFigureLabel(rescued)} zu retten. Aber kein Rettungsfeld vorhanden.`;
    appendLog(state, message);
    appendActiveResult(state, message);
    return false;
  }

  placeFigureOnField(state, rescued, landingCoord);
  const message = `${getFigureLabel(rescuer)} rettet ${getFigureLabel(rescued)} nach ${landingCoord.q},${landingCoord.r}.`;
  appendLog(
    state,
    message
  );
  appendActiveResult(state, message);
  return true;
}

function triggerPitfallAt(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, coord: MagicArenaCoord): void {
  const pitfall = getBoardObjectsAt(state, coord, "pitfall")[0];

  if (!pitfall || figure.status !== "alive") {
    return;
  }

  removeBoardObject(state, pitfall.id);
  appendLog(state, `Fallgrube auf ${coord.q},${coord.r} wird ausgeloest.`);
  applyFigureDamage(state, figure.figureId, 1, "Fallgrube");
  applyFieldDamage(state, coord, 2, "Fallgrube");
}

function triggerMagicMine(state: MagicArenaRuntimeState, mine: MagicArenaBoardObjectState, source: string): void {
  removeBoardObject(state, mine.id);
  appendLog(state, `Magische Mine auf ${mine.coord.q},${mine.coord.r} wird ausgeloest (${source}).`);
  const area = uniqueCoords([mine.coord, ...getNeighbors(mine.coord)]);

  damageFiguresAtCoord(state, mine.coord, 1, "Magische Mine");
  applyFieldDamage(state, mine.coord, 2, "Magische Mine", { arcaneOverloadTarget: true });

  for (const coord of getNeighbors(mine.coord)) {
    applyFieldDamage(state, coord, 1, "Magische Mine");
  }

  for (const coord of area) {
    if (sameCoord(coord, mine.coord)) {
      continue;
    }

    for (const figure of getFiguresAtCoordIncludingWater(state, coord)) {
      if (figure.status === "in_water") {
        applyFigureDamage(state, figure.figureId, 1, "Magische Mine");
      }
    }
  }
}

function triggerMagicMinesForPosition(
  state: MagicArenaRuntimeState,
  coord: MagicArenaCoord,
  includeAdjacent: boolean,
  source: string
): void {
  const candidates = uniqueCoords(includeAdjacent ? [coord, ...getNeighbors(coord)] : [coord]);
  const mines = candidates
    .flatMap((candidate) => getBoardObjectsAt(state, candidate, "magic_mine"))
    .sort((left, right) => left.id.localeCompare(right.id));

  for (const mine of mines) {
    if (state.boardObjects.some((object) => object.id === mine.id)) {
      triggerMagicMine(state, mine, source);
    }
  }
}

function triggerCrystalField(state: MagicArenaRuntimeState, figure: MagicArenaFigureState): void {
  if (figure.status !== "alive" || !figure.coord) {
    return;
  }

  const field = getField(state, figure.coord);
  const player = getPlayer(state, figure.playerId);

  if (field?.specialType !== "crystal" || !player) {
    return;
  }

  field.specialType = null;
  const drawn = drawCards(state, player, 1);
  appendLog(
    state,
    drawn > 0
      ? `Kristallfeld ${field.coord.q},${field.coord.r}: ${getFigureLabel(figure)} zieht 1 Karte.`
      : `Kristallfeld ${field.coord.q},${field.coord.r} leuchtet auf, aber die Hand ist voll.`
  );
}

export function maybeSpawnCrystalFieldForRound(state: MagicArenaRuntimeState): boolean {
  if (state.roundIndex < CRYSTAL_SPAWN_START_ROUND) {
    return false;
  }

  if (rollUnit(state, `crystal-spawn:${state.roundIndex}`) >= CRYSTAL_SPAWN_CHANCE) {
    return false;
  }

  const candidates = seededShuffle(
    Object.values(state.board.fieldsByCoord)
      .filter((field) => field.exists && field.occupants.length === 0 && field.specialType !== "crystal")
      .map((field) => field.coord),
    `${state.rngSeed}:crystal-spawn:${state.roundIndex}:${state.rngCounter}`
  );
  const coord = candidates[0];

  if (!coord) {
    appendLog(state, "Un cristal parpadea pero no puede encontrar un campo claro.", null);
    return false;
  }

  const field = getField(state, coord);

  if (!field?.exists) {
    return false;
  }

  field.specialType = "crystal";
  appendLog(state, `Ein Kristallfeld erscheint auf ${coord.q},${coord.r}.`, null);
  return true;
}

function isStormCloudActive(effect: MagicArenaDelayedEffectState, roundIndex: number): boolean {
  return (
    effect.type === "storm_cloud" &&
    roundIndex >= effect.triggerRound &&
    roundIndex <= (effect.expiresRound ?? effect.triggerRound)
  );
}

function getStormCloudHitKey(state: MagicArenaRuntimeState, figureId: string): string {
  return `${state.roundIndex}:${figureId}`;
}

function triggerStormCloudOnFigure(
  state: MagicArenaRuntimeState,
  cloud: MagicArenaDelayedEffectState,
  figure: MagicArenaFigureState,
  source: string,
  slotIndex?: number
): boolean {
  if (!isStormCloudActive(cloud, state.roundIndex) || figure.status !== "alive") {
    return false;
  }

  const hitKey = getStormCloudHitKey(state, figure.figureId);

  if ((cloud.triggeredFigureKeys ?? []).includes(hitKey)) {
    return false;
  }

  cloud.triggeredFigureKeys = [...(cloud.triggeredFigureKeys ?? []), hitKey];
  appendLog(state, `Gewitterwolke auf ${cloud.coord.q},${cloud.coord.r} trifft ${getFigureLabel(figure)} (${source}).`, slotIndex ?? state.currentSlot);
  applyFigureDamage(state, figure.figureId, 1, "Gewitterwolke");
  return true;
}

function triggerStormCloudsAtPosition(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, source: string): void {
  if (figure.status !== "alive" || !figure.coord) {
    return;
  }

  for (const cloud of state.delayedEffects.filter((effect) => isStormCloudActive(effect, state.roundIndex))) {
    if (sameCoord(cloud.coord, figure.coord)) {
      triggerStormCloudOnFigure(state, cloud, figure, source);
    }
  }
}

function resolvePositionEffects(
  state: MagicArenaRuntimeState,
  figure: MagicArenaFigureState,
  source: string,
  includeAdjacentMines = true
): void {
  if (figure.status !== "alive" || !figure.coord) {
    return;
  }

  const coord = { ...figure.coord };
  triggerPitfallAt(state, figure, coord);

  if (figure.status === "alive" && figure.coord) {
    triggerMagicMinesForPosition(state, figure.coord, includeAdjacentMines, source);
  }

  if (figure.status === "alive" && figure.coord) {
    triggerStormCloudsAtPosition(state, figure, source);
  }

  if (figure.status === "alive" && figure.coord) {
    triggerCrystalField(state, figure);
  }

  if (figure.status === "alive" && figure.coord) {
    tryAutoRescueAround(state, figure.coord, figure.playerId);
  }
}

export function resolvePushChain(
  state: MagicArenaRuntimeState,
  startFigureId: string,
  directionIndex: number,
  source: string,
  depth = 0
): MagicArenaCoord | null {
  const direction = getDirectionByIndex(directionIndex);
  const figure = getFigure(state, startFigureId);

  if (!direction || !figure || figure.status !== "alive" || !figure.coord) {
    return null;
  }

  if (depth >= PUSH_CHAIN_LIMIT) {
    appendLog(state, `Kettenschub-Limit erreicht (${source}).`);
    return figure.coord;
  }

  const destination = addCoords(figure.coord, direction);
  const destinationField = getField(state, destination);

  if (!isInsideBoard(destination, state.board.radius) || !destinationField?.exists) {
    makeFigureFallIntoWater(state, figure.figureId, destination, source);
    return null;
  }

  const blockingFigureId = destinationField.occupants[0];

  if (blockingFigureId) {
    appendLog(state, `Kettenschub: ${blockingFigureId} wird weitergeschoben.`);
    resolvePushChain(state, blockingFigureId, directionIndex, source, depth + 1);
  }

  const updatedDestinationField = getField(state, destination);

  if (!updatedDestinationField?.exists || updatedDestinationField.occupants.length >= MAX_OCCUPANTS_PER_FIELD) {
    appendLog(state, `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} kann nicht geschoben werden: Kette blockiert.`);
    return figure.coord;
  }

  moveFigureToCoord(state, figure, destination);
  appendLog(state, `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} wird nach ${destination.q},${destination.r} geschoben.`);
  resolvePositionEffects(state, figure, source);

  return destination;
}

function resolvePathMovement(
  state: MagicArenaRuntimeState,
  figure: MagicArenaFigureState,
  path: MagicArenaCoord[] | undefined,
  maxLength: number,
  label: string
): void {
  if (!figure.coord || !isValidPathFrom(figure.coord, path, maxLength)) {
    appendLog(state, `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} kann ${label} nicht ausfuehren.`);
    return;
  }

  for (const step of path ?? []) {
    const field = getField(state, step);

    if (!isInsideBoard(step, state.board.radius) || !field?.exists) {
      makeFigureFallIntoWater(state, figure.figureId, step, label);
      return;
    }

    if (field.occupants.length >= MAX_OCCUPANTS_PER_FIELD) {
      appendLog(state, `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} stoppt vor einem vollen Feld.`);
      break;
    }

    moveFigureToCoord(state, figure, step);
    resolvePositionEffects(state, figure, label, false);

    if (figure.status !== "alive") {
      return;
    }
  }

  if (figure.status === "alive" && figure.coord) {
    appendLog(state, `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} bewegt sich nach ${figure.coord.q},${figure.coord.r}.`);
    resolvePositionEffects(state, figure, label);
  }
}

function swapFigures(state: MagicArenaRuntimeState, left: MagicArenaFigureState, right: MagicArenaFigureState, label: string): void {
  if (left.status !== "alive" || right.status !== "alive" || !left.coord || !right.coord) {
    return;
  }

  const leftCoord = { ...left.coord };
  const rightCoord = { ...right.coord };
  removeFigureFromField(state, left);
  removeFigureFromField(state, right);
  placeFigureOnField(state, left, rightCoord);
  placeFigureOnField(state, right, leftCoord);
  appendLog(state, `${getPlayerName(state, left.playerId)} ${getFigureLabel(left)} tauscht Platz mit ${getPlayerName(state, right.playerId)} ${getFigureLabel(right)} (${label}).`);
  resolvePositionEffects(state, left, label);
  resolvePositionEffects(state, right, label);
}

function damageFiguresAtCoord(
  state: MagicArenaRuntimeState,
  coord: MagicArenaCoord,
  amount: number,
  source: string
): void {
  const targets = getFiguresAtCoordIncludingWater(state, coord).map((figure) => figure.figureId);

  for (const figureId of targets) {
    applyFigureDamage(state, figureId, amount, source);
  }
}

function resolveMoveAction(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, action: MagicArenaPlannedAction): void {
  resolvePathMovement(state, figure, action.path, 2, "Bewegung");
}

function resolvePunchAction(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, action: MagicArenaPlannedAction): void {
  if (!figure.coord || !validateRangeFromFigure(figure, action.targetCoord, 1) || !action.targetCoord) {
    appendLog(state, `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} boxt ins Leere.`);
    return;
  }

  const target = selectEnemyFigureOnCoord(state, action.targetCoord, figure.playerId);

  if (target) {
    applyFigureDamage(state, target.figureId, 1, "Boxen");
    return;
  }

  applyFieldDamage(state, action.targetCoord, 1, "Boxen");
}

type MagicArenaDigOutcome = "small_chest" | "big_chest" | "treasure" | "nothing" | "buried_bomb" | "unstable_ground";

function getDigOutcome(state: MagicArenaRuntimeState, figure: MagicArenaFigureState): MagicArenaDigOutcome {
  state.digCounter += 1;
  const roll = rollUnit(state, `dig:${state.digCounter}:${figure.figureId}`);
  let threshold = DIG_SMALL_CHEST_CHANCE;

  if (roll < threshold) {
    return "small_chest";
  }

  threshold += DIG_BIG_CHEST_CHANCE;
  if (roll < threshold) {
    return "big_chest";
  }

  threshold += DIG_TREASURE_CHANCE;
  if (roll < threshold) {
    return "treasure";
  }

  threshold += DIG_NOTHING_CHANCE;
  if (roll < threshold) {
    return "nothing";
  }

  threshold += DIG_BURIED_BOMB_CHANCE;
  if (roll < threshold) {
    return "buried_bomb";
  }

  return "unstable_ground";
}

function resolveDigAction(state: MagicArenaRuntimeState, figure: MagicArenaFigureState): void {
  if (!figure.coord) {
    appendLog(state, `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} kann hier nicht graben.`);
    return;
  }

  const coord = { ...figure.coord };
  const player = getPlayer(state, figure.playerId);
  const field = getField(state, coord);

  if (!player || !field?.exists) {
    appendLog(state, `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} kann hier nicht graben.`);
    return;
  }

  const outcome = getDigOutcome(state, figure);
  let resultLabel = `${getFigureLabel(figure)} hat nichts gefunden.`;
  let fieldDamage = DIG_BASE_FIELD_DAMAGE;
  const treasureScent = state.currentRoundEvent?.eventId === "treasure_scent";

  if (outcome === "small_chest" || outcome === "big_chest" || outcome === "treasure") {
    const requested = outcome === "small_chest" ? (treasureScent ? 2 : 1) : outcome === "big_chest" ? 2 : 3;
    const drawn = drawCards(state, player, requested);
    resultLabel = drawn > 0
      ? `${getFigureLabel(figure)} hat ${drawn} Karte${drawn === 1 ? "" : "n"} gefunden.`
      : `${getFigureLabel(figure)} hat eine Truhe gefunden, aber die Hand ist voll.`;
    appendLog(state, `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} findet ${drawn} Karte${drawn === 1 ? "" : "n"}.`);
  } else if (outcome === "buried_bomb") {
    resultLabel = `${getFigureLabel(figure)} hat eine vergrabene Bombe gefunden.`;
    appendLog(state, `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} findet eine vergrabene Bombe.`);
    applyFigureDamage(state, figure.figureId, DIG_BURIED_BOMB_DAMAGE, "vergrabene Bombe");
  } else if (outcome === "unstable_ground") {
    resultLabel = `${getFigureLabel(figure)} hat instabilen Boden gefunden.`;
    fieldDamage += DIG_UNSTABLE_EXTRA_FIELD_DAMAGE;
    appendLog(state, `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} legt instabilen Boden frei.`);
  } else {
    const drawn = treasureScent ? drawCards(state, player, 1) : 0;
    resultLabel = treasureScent && drawn > 0
      ? `${getFigureLabel(figure)} hat durch Schatzduft 1 Karte gefunden.`
      : `${getFigureLabel(figure)} hat nichts gefunden.`;
    appendLog(state, treasureScent && drawn > 0
      ? `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} findet durch Schatzduft 1 Karte.`
      : `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} findet nichts.`
    );
  }

  if (state.activeAction?.actionId === "dig") {
    appendActiveResult(state, resultLabel);
  }

  applyFieldDamage(state, coord, fieldDamage, "Graben");
}

function resolveChargeRun(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, action: MagicArenaPlannedAction): void {
  const direction = getDirectionByIndex(action.direction ?? -1);
  const distance = action.distance ?? 0;

  if (!figure.coord || !direction || distance < 1 || distance > 3) {
    appendLog(state, `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} kann Stosslauf nicht ausfuehren.`);
    return;
  }

  for (let stepIndex = 0; stepIndex < distance; stepIndex += 1) {
    if (!figure.coord) {
      return;
    }

    const nextCoord = addCoords(figure.coord, direction);
    const field = getField(state, nextCoord);

    if (!isInsideBoard(nextCoord, state.board.radius) || !field?.exists) {
      makeFigureFallIntoWater(state, figure.figureId, nextCoord, "Stosslauf");
      return;
    }

    if (field.occupants.length > 0) {
      const target = selectEnemyFigureOnCoord(state, nextCoord, figure.playerId);

      if (target) {
        applyFigureDamage(state, target.figureId, 1, "Stosslauf");

        if (target.status === "alive") {
          resolvePushChain(state, target.figureId, action.direction ?? 0, "Stosslauf");
        }
      } else {
        appendLog(state, "Push run solo golpea tus propias piezas y se detiene.");
      }

      if (figure.coord) {
        resolvePositionEffects(state, figure, "Stosslauf");
      }
      return;
    }

    moveFigureToCoord(state, figure, nextCoord);
    resolvePositionEffects(state, figure, "Stosslauf", false);

    if (figure.status !== "alive") {
      return;
    }
  }

  if (figure.coord) {
    appendLog(state, `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} beendet Stosslauf auf ${figure.coord.q},${figure.coord.r}.`);
    resolvePositionEffects(state, figure, "Stosslauf");
  }
}

function resolveEnemySwap(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, action: MagicArenaPlannedAction): void {
  if (!figure.coord || !action.targetCoord || hexDistance(figure.coord, action.targetCoord) > 3) {
    appendLog(state, "Gegner-Tausch scheitert: Ziel ausser Reichweite.");
    return;
  }

  const target = selectEnemyFigureOnCoord(state, action.targetCoord, figure.playerId);

  if (!target) {
    appendLog(state, "Gegner-Tausch findet keinen Gegner.");
    return;
  }

  swapFigures(state, figure, target, "Gegner-Tausch");
}

function resolveHammerBlow(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, action: MagicArenaPlannedAction): void {
  if (!figure.coord || !action.targetCoord || hexDistance(figure.coord, action.targetCoord) > 1) {
    appendLog(state, "Hammerstoss scheitert: Ziel ausser Reichweite.");
    return;
  }

  const target = selectEnemyFigureOnCoord(state, action.targetCoord, figure.playerId);

  if (target) {
    let directionIndex = getDirectionIndexBetween(figure.coord, action.targetCoord);

    if (directionIndex === null && sameCoord(figure.coord, action.targetCoord)) {
      directionIndex = action.direction ?? -1;
    }

    applyFigureDamage(state, target.figureId, 2, "Hammerstoss");

    if (target.status === "alive" && directionIndex !== null && directionIndex >= 0) {
      resolvePushChain(state, target.figureId, directionIndex, "Hammerstoss");
    }
  }

  applyFieldDamage(state, action.targetCoord, 1, "Hammerstoss");
}

function resolveBombPlacement(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, action: MagicArenaPlannedAction): void {
  if (!figure.coord || !action.targetCoord || hexDistance(figure.coord, action.targetCoord) > 2 || !isInsideBoard(action.targetCoord, state.board.radius) || hasBlockingBoardObjectAt(state, action.targetCoord)) {
    appendLog(state, "No se puede colocar una bomba.");
    return;
  }

  const slotIndex = action.slotIndex;
  const nextSlot = slotIndex + 1;
  const slotsPerRound = getSlotsPerRound(state);
  const bomb: MagicArenaPendingBombState = {
    bombId: `bomb-${state.nextBombId}`,
    ownerPlayerId: figure.playerId,
    coord: { ...action.targetCoord },
    explodeRound: nextSlot >= slotsPerRound ? state.roundIndex + 1 : state.roundIndex,
    explodeSlot: nextSlot >= slotsPerRound ? 0 : nextSlot
  };
  state.nextBombId += 1;
  state.pendingBombs.push(bomb);
  createBoardObject(state, {
    type: "bomb",
    ownerPlayerId: figure.playerId,
    coord: { ...action.targetCoord },
    visibleTo: "all",
    triggerRound: bomb.explodeRound,
    triggerSlot: bomb.explodeSlot
  });
  appendLog(state, `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} legt eine Bombe auf ${bomb.coord.q},${bomb.coord.r}.`);
}

function resolveMeteorStrike(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, action: MagicArenaPlannedAction): void {
  if (!figure.coord || !action.targetCoord || hexDistance(figure.coord, action.targetCoord) > 4 || !isInsideBoard(action.targetCoord, state.board.radius)) {
    appendLog(state, "Meteorschlag scheitert: Ziel ausser Reichweite.");
    return;
  }

  damageFiguresAtCoord(state, action.targetCoord, 2, "Meteorschlag");
  applyFieldDamage(state, action.targetCoord, 3, "Meteorschlag", { arcaneOverloadTarget: true });

  for (const neighbor of getNeighbors(action.targetCoord)) {
    applyFieldDamage(state, neighbor, 1, "Meteorschlag");
  }
}

function resolveMagnetPull(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, action: MagicArenaPlannedAction): void {
  if (!figure.coord || !action.targetCoord || hexDistance(figure.coord, action.targetCoord) > 6) {
    appendLog(state, "Magnetzug scheitert: Ziel ausser Reichweite.");
    return;
  }

  const target = selectMagnetTarget(state, action.targetCoord, figure.playerId);
  const destination = getStepToward(action.targetCoord, figure.coord);

  if (!target || !destination) {
    appendLog(state, "Magnetzug findet kein gueltiges Ziel.");
    return;
  }

  const destinationField = getField(state, destination);

  if (!isInsideBoard(destination, state.board.radius) || !destinationField?.exists) {
    makeFigureFallIntoWater(state, target.figureId, destination, "Magnetzug");
    return;
  }

  if (destinationField.occupants.length >= MAX_OCCUPANTS_PER_FIELD) {
    appendLog(state, "La atracción magnética está bloqueada por un campo completo.");
    return;
  }

  moveFigureToCoord(state, target, destination);
  appendLog(state, `${getPlayerName(state, target.playerId)} ${getFigureLabel(target)} wird nach ${destination.q},${destination.r} gezogen.`);
  resolvePositionEffects(state, target, "Magnetzug");
}

function resolveShockwave(state: MagicArenaRuntimeState, figure: MagicArenaFigureState): void {
  if (!figure.coord) {
    return;
  }

  const affected = getNeighbors(figure.coord)
    .flatMap((coord) =>
      getLiveFiguresAtCoord(state, coord).map((target) => ({
        coord,
        target,
        directionIndex: getDirectionIndexBetween(figure.coord as MagicArenaCoord, coord)
      }))
    )
    .filter((entry) => entry.target.playerId !== figure.playerId && entry.directionIndex !== null)
    .sort((left, right) => left.target.figureId.localeCompare(right.target.figureId));
  const rescueChecks: Array<{ coord: MagicArenaCoord; playerId: string }> = [];

  for (const entry of affected) {
    applyFigureDamage(state, entry.target.figureId, 1, "Schubwelle");

    if (entry.target.status === "alive" && entry.directionIndex !== null) {
      const pushedTo = resolvePushChain(state, entry.target.figureId, entry.directionIndex, "Schubwelle");

      if (pushedTo) {
        rescueChecks.push({ coord: pushedTo, playerId: entry.target.playerId });
      }
    }
  }

  for (const check of rescueChecks) {
    tryAutoRescueAround(state, check.coord, check.playerId);
  }
}

function removeStealthForAttack(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, cardId: MagicArenaCardId | undefined): void {
  if (!figure.stealthUntilRound) {
    return;
  }

  const attackCards: MagicArenaCardId[] = [
    "lightning_strike",
    "fireball",
    "hammer_blow",
    "bomb",
    "meteor_strike",
    "shockwave",
    "chain_lightning",
    "kraken",
    "magic_mine",
    "storm_cloud"
  ];

  if (cardId && attackCards.includes(cardId)) {
    delete figure.stealthUntilRound;
    appendLog(state, `${getFigureLabel(figure)} verliert die Tarnung.`);
  }
}

function getFigureCoordForRange(figure: MagicArenaFigureState): MagicArenaCoord | undefined {
  return figure.status === "alive" ? figure.coord : figure.status === "in_water" ? figure.waterCoord : undefined;
}

function selectFigureAtCoordIncludingWater(
  state: MagicArenaRuntimeState,
  coord: MagicArenaCoord,
  actorPlayerId: string,
  excludeIds = new Set<string>()
): MagicArenaFigureState | undefined {
  return getFiguresAtCoordIncludingWater(state, coord)
    .filter((figure) => figure.status !== "eliminated" && !excludeIds.has(figure.figureId))
    .sort((left, right) => {
      const leftEnemy = left.playerId !== actorPlayerId ? 0 : 1;
      const rightEnemy = right.playerId !== actorPlayerId ? 0 : 1;
      return leftEnemy - rightEnemy || left.hp - right.hp || left.figureId.localeCompare(right.figureId);
    })[0];
}

function resolveChainLightning(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, action: MagicArenaPlannedAction): void {
  if (!figure.coord || !action.targetCoord || hexDistance(figure.coord, action.targetCoord) > 4) {
    appendLog(state, "Kettenblitz scheitert: Ziel ausser Reichweite.");
    return;
  }

  const hitIds = new Set<string>();
  const firstTarget = selectFigureAtCoordIncludingWater(state, action.targetCoord, figure.playerId, hitIds);

  if (!firstTarget) {
    appendLog(state, "Kettenblitz findet kein erstes Ziel.");
    return;
  }

  let lastTarget: MagicArenaFigureState | undefined = firstTarget;
  applyFigureDamage(state, firstTarget.figureId, 2, "Kettenblitz");
  hitIds.add(firstTarget.figureId);

  for (let jump = 0; jump < 2 && lastTarget; jump += 1) {
    const lastCoord = getFigureCoordForRange(lastTarget);

    if (!lastCoord) {
      break;
    }

    const nextTarget = state.players
      .flatMap((player) => player.figures)
      .filter((candidate) => candidate.status !== "eliminated" && !hitIds.has(candidate.figureId))
      .map((candidate) => ({
        candidate,
        coord: getFigureCoordForRange(candidate)
      }))
      .filter((entry): entry is { candidate: MagicArenaFigureState; coord: MagicArenaCoord } => Boolean(entry.coord))
      .map((entry) => ({
        ...entry,
        distance: hexDistance(lastCoord, entry.coord)
      }))
      .filter((entry) => entry.distance <= 2)
      .sort((left, right) => {
        const leftEnemy = left.candidate.playerId !== figure.playerId ? 0 : 1;
        const rightEnemy = right.candidate.playerId !== figure.playerId ? 0 : 1;
        return (
          leftEnemy - rightEnemy ||
          left.distance - right.distance ||
          left.candidate.hp - right.candidate.hp ||
          left.candidate.figureId.localeCompare(right.candidate.figureId)
        );
      })[0]?.candidate;

    if (!nextTarget) {
      break;
    }

    appendLog(state, `Kettenblitz springt auf ${getFigureLabel(nextTarget)}.`);
    applyFigureDamage(state, nextTarget.figureId, 1, "Kettenblitz");
    hitIds.add(nextTarget.figureId);
    lastTarget = nextTarget;
  }
}

function resolvePitfallPlacement(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, action: MagicArenaPlannedAction): void {
  if (!figure.coord || !action.targetCoord || hexDistance(figure.coord, action.targetCoord) > 2 || !isFreeIntactField(state, action.targetCoord) || hasBlockingBoardObjectAt(state, action.targetCoord)) {
    appendLog(state, "No se puede colocar trampa aquí.");
    return;
  }

  createBoardObject(state, {
    type: "pitfall",
    ownerPlayerId: figure.playerId,
    coord: { ...action.targetCoord },
    visibleTo: "owner"
  });
  appendLog(state, `${getFigureLabel(figure)} legt eine Fallgrube auf ${action.targetCoord.q},${action.targetCoord.r}.`);
}

function resolveTeleport(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, action: MagicArenaPlannedAction): void {
  if (!figure.coord || !action.targetCoord || hexDistance(figure.coord, action.targetCoord) > 4 || !isFreeIntactField(state, action.targetCoord)) {
    appendLog(state, "El teletransporte falla: el objetivo debe estar libre, intacto y dentro del alcance.");
    return;
  }

  moveFigureToCoord(state, figure, action.targetCoord);
  appendLog(state, `${getFigureLabel(figure)} teleportiert nach ${action.targetCoord.q},${action.targetCoord.r}.`);
  resolvePositionEffects(state, figure, "Teleport");
}

function resolveKraken(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, action: MagicArenaPlannedAction): void {
  if (!figure.coord || !action.targetCoord || hexDistance(figure.coord, action.targetCoord) > 5) {
    appendLog(state, "Krake scheitert: Ziel ausser Reichweite.");
    return;
  }

  const target = getFiguresAtCoordIncludingWater(state, action.targetCoord)
    .filter((entry) => entry.status === "in_water")
    .sort((left, right) => {
      const leftEnemy = left.playerId !== figure.playerId ? 0 : 1;
      const rightEnemy = right.playerId !== figure.playerId ? 0 : 1;
      return leftEnemy - rightEnemy || left.hp - right.hp || left.figureId.localeCompare(right.figureId);
    })[0];

  if (!target?.waterCoord) {
    appendLog(state, "El pulpo no encuentra una figura en el agua.");
    return;
  }

  const oldCoord = { ...target.waterCoord };
  applyFigureDamage(state, target.figureId, 2, "Krake");

  if (target.status !== "in_water" || !target.waterCoord) {
    return;
  }

  const pulled = getStepToward(target.waterCoord, { q: 0, r: 0 });

  if (pulled && isInsideBoard(pulled, state.board.radius)) {
    target.waterCoord = pulled;
  }

  appendLog(state, `Krake zieht ${getFigureLabel(target)} von ${oldCoord.q},${oldCoord.r} nach ${target.waterCoord.q},${target.waterCoord.r}.`);
}

function resolveTimeRiftPlacement(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, action: MagicArenaPlannedAction): void {
  if (!figure.coord || !action.targetCoord || hexDistance(figure.coord, action.targetCoord) > 4 || !isInsideBoard(action.targetCoord, state.board.radius)) {
    appendLog(state, "La brecha temporal no se puede marcar aquí.");
    return;
  }

  const id = `delay-${state.nextDelayedEffectId}`;
  state.nextDelayedEffectId += 1;
  state.delayedEffects.push({
    id,
    type: "time_rift",
    ownerPlayerId: figure.playerId,
    coord: { ...action.targetCoord },
    createdRound: state.roundIndex,
    triggerRound: state.roundIndex + 1
  });
  createBoardObject(state, {
    type: "time_rift_marker",
    ownerPlayerId: figure.playerId,
    coord: { ...action.targetCoord },
    visibleTo: "all",
    triggerRound: state.roundIndex + 1
  });
  appendLog(state, `Zeitriss auf ${action.targetCoord.q},${action.targetCoord.r} markiert.`);
}

function resolveMagicMinePlacement(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, action: MagicArenaPlannedAction): void {
  if (!figure.coord || !action.targetCoord || hexDistance(figure.coord, action.targetCoord) > 2 || !isFreeIntactField(state, action.targetCoord) || hasBlockingBoardObjectAt(state, action.targetCoord)) {
    appendLog(state, "La mina mágica no se puede colocar aquí.");
    return;
  }

  createBoardObject(state, {
    type: "magic_mine",
    ownerPlayerId: figure.playerId,
    coord: { ...action.targetCoord },
    visibleTo: "all"
  });
  appendLog(state, `Magische Mine auf ${action.targetCoord.q},${action.targetCoord.r} platziert.`);
}

function resolveStormCloudPlacement(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, action: MagicArenaPlannedAction): void {
  if (!figure.coord || !action.targetCoord || hexDistance(figure.coord, action.targetCoord) > 4 || !isInsideBoard(action.targetCoord, state.board.radius) || hasBlockingBoardObjectAt(state, action.targetCoord)) {
    appendLog(state, "La nube de tormenta no se puede marcar aquí.");
    return;
  }

  const id = `delay-${state.nextDelayedEffectId}`;
  state.nextDelayedEffectId += 1;
  state.delayedEffects.push({
    id,
    type: "storm_cloud",
    ownerPlayerId: figure.playerId,
    coord: { ...action.targetCoord },
    createdRound: state.roundIndex,
    triggerRound: state.roundIndex + 1,
    expiresRound: state.roundIndex + STORM_CLOUD_ACTIVE_ROUNDS,
    triggeredFigureKeys: []
  });
  createBoardObject(state, {
    type: "storm_cloud_marker",
    ownerPlayerId: figure.playerId,
    coord: { ...action.targetCoord },
    visibleTo: "all",
    triggerRound: state.roundIndex + 1
  });
  appendLog(state, `Gewitterwolke auf ${action.targetCoord.q},${action.targetCoord.r} markiert.`);
}

function resolveCardAction(state: MagicArenaRuntimeState, figure: MagicArenaFigureState, action: MagicArenaPlannedAction): void {
  removeStealthForAttack(state, figure, action.cardId);

  switch (action.cardId as string | undefined) {
    case "sprint":
      resolvePathMovement(state, figure, action.path, 4, "Sprint");
      return;
    case "charge_run":
      resolveChargeRun(state, figure, action);
      return;
    case "ally_swap": {
      const targetFigureId = typeof action.extra?.targetFigureId === "string" ? action.extra.targetFigureId : null;
      const target = targetFigureId ? getFigure(state, targetFigureId) : undefined;

      if (!target || target.playerId !== figure.playerId || !isActionCapableFigure(state, target)) {
        appendLog(state, "Platztausch findet keine zweite eigene Figur.");
        return;
      }

      swapFigures(state, figure, target, "Platztausch");
      return;
    }
    case "enemy_swap":
      resolveEnemySwap(state, figure, action);
      return;
    case "lightning_strike":
      if (figure.coord && action.targetCoord && hexDistance(figure.coord, action.targetCoord) <= 5) {
        damageFiguresAtCoord(state, action.targetCoord, 2, "Blitzschlag");
      } else {
        appendLog(state, "Blitzschlag scheitert: Ziel ausser Reichweite.");
      }
      return;
    case "fireball":
      if (figure.coord && action.targetCoord && hexDistance(figure.coord, action.targetCoord) <= 3) {
        damageFiguresAtCoord(state, action.targetCoord, 1, "Feuerball");
        applyFieldDamage(state, action.targetCoord, 2, "Feuerball", { arcaneOverloadTarget: true });
      } else {
        appendLog(state, "Feuerball scheitert: Ziel ausser Reichweite.");
      }
      return;
    case "hammer_blow":
      resolveHammerBlow(state, figure, action);
      return;
    case "bomb":
      resolveBombPlacement(state, figure, action);
      return;
    case "meteor_strike":
      resolveMeteorStrike(state, figure, action);
      return;
    case "rift":
      if (figure.coord && action.targetCoord && hexDistance(figure.coord, action.targetCoord) <= 4 && getField(state, action.targetCoord)?.exists) {
        applyFieldDamage(state, action.targetCoord, 3, "Riss", { arcaneOverloadTarget: true });
      } else {
        appendLog(state, "El crack falla: el objetivo debe ser un campo existente dentro del rango.");
      }
      return;
    case "earthquake":
      if (!figure.coord) {
        return;
      }
      for (const neighbor of getNeighbors(figure.coord)) {
        applyFieldDamage(state, neighbor, 1, "Erdbeben", { arcaneOverloadTarget: true });
      }
      return;
    case "platform_break":
      if (!figure.coord || !action.targetCoord || hexDistance(figure.coord, action.targetCoord) > 4) {
        appendLog(state, "Plattformbruch scheitert: Ziel ausser Reichweite.");
        return;
      }
      if ((getField(state, action.targetCoord)?.hp ?? Number.MAX_SAFE_INTEGER) <= 2) {
        destroyField(state, action.targetCoord, "Plattformbruch");
      } else {
        appendLog(state, "Falla la ruptura de plataforma: el campo aún es demasiado estable.");
      }
      return;
    case "stealth":
      figure.stealthUntilRound = state.roundIndex + 1;
      appendLog(state, `${getPlayerName(state, figure.playerId)} ${getFigureLabel(figure)} ist getarnt.`);
      return;
    case "magnet_pull":
      resolveMagnetPull(state, figure, action);
      return;
    case "shockwave":
      resolveShockwave(state, figure);
      return;
    case "chain_lightning":
      resolveChainLightning(state, figure, action);
      return;
    case "pitfall":
      resolvePitfallPlacement(state, figure, action);
      return;
    case "teleport":
      resolveTeleport(state, figure, action);
      return;
    case "kraken":
      resolveKraken(state, figure, action);
      return;
    case "time_rift":
      resolveTimeRiftPlacement(state, figure, action);
      return;
    case "magic_mine":
      resolveMagicMinePlacement(state, figure, action);
      return;
    case "storm_cloud":
      resolveStormCloudPlacement(state, figure, action);
      return;
    default:
      appendLog(state, "La tarjeta desconocida desaparece.");
  }
}

function uniqueCoords(coords: (MagicArenaCoord | undefined)[]): MagicArenaCoord[] {
  const seen = new Set<string>();
  const unique: MagicArenaCoord[] = [];

  for (const coord of coords) {
    if (!coord) {
      continue;
    }

    const key = axialKey(coord);

    if (!seen.has(key)) {
      seen.add(key);
      unique.push({ ...coord });
    }
  }

  return unique;
}

function getLinePreview(origin: MagicArenaCoord, directionIndex: number | undefined, distance: number | undefined): MagicArenaCoord[] {
  const direction = getDirectionByIndex(directionIndex ?? -1);

  if (!direction) {
    return [];
  }

  return Array.from({ length: Math.max(0, distance ?? 0) }, (_, index) => ({
    q: origin.q + direction.q * (index + 1),
    r: origin.r + direction.r * (index + 1)
  }));
}

function getBaseActionName(actionId: MagicArenaBaseActionId | undefined): string {
  switch (actionId) {
    case "move":
      return "Move";
    case "punch":
      return "Punch";
    case "dig":
      return "Graben";
    default:
      return "Basisaktion";
  }
}

function getActionPreviewTarget(
  state: MagicArenaRuntimeState,
  figure: MagicArenaFigureState | undefined,
  action: MagicArenaPlannedAction
): MagicArenaCoord | undefined {
  if (figure?.coord && action.actionKind === "base" && action.baseActionId === "dig") {
    return { ...figure.coord };
  }

  if (action.targetCoord) {
    return { ...action.targetCoord };
  }

  if (action.path && action.path.length > 0) {
    return { ...action.path[action.path.length - 1] };
  }

  if (figure?.coord && action.cardId === "charge_run") {
    return getLinePreview(figure.coord, action.direction, action.distance).at(-1);
  }

  if (figure?.coord && (action.cardId as string | undefined) === "ally_swap") {
    const targetFigureId = typeof action.extra?.targetFigureId === "string" ? action.extra.targetFigureId : null;
    const targetFigure = targetFigureId ? getFigure(state, targetFigureId) : undefined;
    return targetFigure?.coord ? { ...targetFigure.coord } : undefined;
  }

  return undefined;
}

function getActionAffectedCoords(
  state: MagicArenaRuntimeState,
  figure: MagicArenaFigureState | undefined,
  action: MagicArenaPlannedAction
): MagicArenaCoord[] {
  const origin = figure?.coord;
  const target = getActionPreviewTarget(state, figure, action);

  if (action.path?.length) {
    return uniqueCoords(action.path);
  }

  if (origin && action.cardId === "charge_run") {
    return uniqueCoords(getLinePreview(origin, action.direction, action.distance));
  }

  if (origin && action.actionKind === "base" && action.baseActionId === "dig") {
    return uniqueCoords([origin]);
  }

  if (origin && (action.cardId === "earthquake" || action.cardId === "shockwave")) {
    return uniqueCoords(getNeighbors(origin));
  }

  if (target && (action.cardId === "bomb" || action.cardId === "meteor_strike" || action.cardId === "magic_mine")) {
    return uniqueCoords([target, ...getNeighbors(target)]);
  }

  if (target && action.cardId === "storm_cloud") {
    return uniqueCoords([target]);
  }

  return uniqueCoords([target]);
}

export function resolvePlannedAction(
  state: MagicArenaRuntimeState,
  action: MagicArenaPlannedAction
): void {
  const actionName =
    action.actionKind === "base"
      ? getBaseActionName(action.baseActionId)
      : action.cardId
        ? magicArenaCardDefinitionsById[action.cardId]?.name ?? "Mapa"
        : "Mapa";
  const consumed = consumePlannedCard(state, action);
  const figure = getFigure(state, action.figureId);
  const previewTarget = getActionPreviewTarget(state, figure, action);
  const actorLabel = figure?.name ?? action.figureId;
  const activeLabel = action.actionKind === "card"
    ? `${actorLabel} wirkt ${actionName}`
    : `${actorLabel} wendet ${actionName} an`;

  state.activeAction = {
    kind: "action",
    roundIndex: state.roundIndex,
    slotIndex: action.slotIndex,
    playerId: action.playerId,
    figureId: action.figureId,
    actionId: action.actionKind === "base" ? action.baseActionId : action.cardId,
    label: activeLabel,
    originCoord: figure?.coord ? { ...figure.coord } : undefined,
    targetCoord: previewTarget,
    path: action.path?.map((coord) => ({ ...coord })),
    affectedCoords: getActionAffectedCoords(state, figure, action),
    at: state.updatedAt
  };

  appendLog(
    state,
    `${figure ? getFigureLabel(figure) : action.figureId} nutzt ${actionName}${action.targetCoord ? ` auf ${action.targetCoord.q},${action.targetCoord.r}` : ""}.`,
    action.slotIndex
  );

  if (action.actionKind === "card" && !consumed) {
    appendLog(state, `${actionName} ist nicht mehr auf der Hand und verpufft.`, action.slotIndex);
    return;
  }

  if (!figure || !isActionCapableFigure(state, figure)) {
    appendLog(state, "La acción prevista caduca: la figura ejecutora no puede actuar.", action.slotIndex);
    return;
  }

  if (action.actionKind === "base") {
    if (action.baseActionId === "move") {
      resolveMoveAction(state, figure, action);
    } else if (action.baseActionId === "punch") {
      resolvePunchAction(state, figure, action);
    } else if (action.baseActionId === "dig") {
      resolveDigAction(state, figure);
    }
    return;
  }

  resolveCardAction(state, figure, action);
}

export function resolveBombsForSlot(
  state: MagicArenaRuntimeState,
  roundIndex: number,
  slotIndex: number
): void {
  const bombs = state.pendingBombs.filter((bomb) => bomb.explodeRound === roundIndex && bomb.explodeSlot === slotIndex);

  if (bombs.length === 0) {
    return;
  }

  state.pendingBombs = state.pendingBombs.filter((bomb) => !(bomb.explodeRound === roundIndex && bomb.explodeSlot === slotIndex));
  const affectedCoords = uniqueCoords(bombs.flatMap((bomb) => [bomb.coord, ...getNeighbors(bomb.coord)]));
  state.activeAction = {
    kind: "bombs",
    roundIndex,
    slotIndex,
    actionId: "bomb",
    label: `${bombs.length} Bombe${bombs.length === 1 ? "" : "n"} explodieren`,
    targetCoord: bombs[0]?.coord ? { ...bombs[0].coord } : undefined,
    affectedCoords,
    at: state.updatedAt
  };

  for (const bomb of bombs) {
    state.boardObjects = state.boardObjects.filter(
      (object) =>
        !(
          object.type === "bomb" &&
          object.triggerRound === bomb.explodeRound &&
          object.triggerSlot === bomb.explodeSlot &&
          sameCoord(object.coord, bomb.coord)
        )
    );
    appendLog(state, `Bombe auf ${bomb.coord.q},${bomb.coord.r} explodiert.`, slotIndex);
    const area = [bomb.coord, ...getNeighbors(bomb.coord)];

    for (const coord of area) {
      damageFiguresAtCoord(state, coord, sameCoord(coord, bomb.coord) ? 2 : 1, "Bombe");
    }

    for (const coord of area) {
      applyFieldDamage(state, coord, sameCoord(coord, bomb.coord) ? 2 : 1, "Bombe", {
        arcaneOverloadTarget: sameCoord(coord, bomb.coord)
      });
    }
  }
}

export function resolveTimeRiftsForRound(state: MagicArenaRuntimeState, roundIndex: number): void {
  const rifts = state.delayedEffects.filter((effect) => effect.type === "time_rift" && effect.triggerRound === roundIndex);

  if (rifts.length === 0) {
    return;
  }

  state.delayedEffects = state.delayedEffects.filter((effect) => !(effect.type === "time_rift" && effect.triggerRound === roundIndex));

  for (const rift of rifts) {
    removeBoardObject(
      state,
      state.boardObjects.find(
        (object) =>
          object.type === "time_rift_marker" &&
          object.triggerRound === rift.triggerRound &&
          sameCoord(object.coord, rift.coord)
      )?.id ?? ""
    );
    state.activeAction = {
      kind: "action",
      roundIndex,
      slotIndex: null,
      actionId: "time_rift",
      label: `Zeitriss auf ${rift.coord.q},${rift.coord.r} bricht auf`,
      targetCoord: { ...rift.coord },
      affectedCoords: [{ ...rift.coord }],
      at: state.updatedAt
    };
    appendLog(state, `Zeitriss auf ${rift.coord.q},${rift.coord.r} wird ausgeloest.`, null);
    damageFiguresAtCoord(state, rift.coord, 1, "Zeitriss");
    applyFieldDamage(state, rift.coord, 3, "Zeitriss", { arcaneOverloadTarget: true });
  }
}

export function resolveStormCloudsForSlot(
  state: MagicArenaRuntimeState,
  roundIndex: number,
  slotIndex: number
): void {
  const clouds = state.delayedEffects.filter(
    (effect) => isStormCloudActive(effect, roundIndex)
  );
  const isLastSlotOfRound = slotIndex >= getSlotsPerRound(state) - 1;

  if (clouds.length === 0) {
    if (isLastSlotOfRound) {
      state.delayedEffects = state.delayedEffects.filter((effect) => {
        if (effect.type !== "storm_cloud" || (effect.expiresRound ?? effect.triggerRound) >= roundIndex) {
          return true;
        }

        removeBoardObject(
          state,
          state.boardObjects.find(
            (object) =>
              object.type === "storm_cloud_marker" &&
              object.triggerRound === effect.triggerRound &&
              sameCoord(object.coord, effect.coord)
          )?.id ?? ""
        );
        return false;
      });
    }
    return;
  }

  const hitCoords: MagicArenaCoord[] = [];

  for (const cloud of clouds) {
    for (const figure of getLiveFiguresAtCoord(state, cloud.coord)) {
      if (triggerStormCloudOnFigure(state, cloud, figure, "Slotende", slotIndex)) {
        hitCoords.push({ ...cloud.coord });
      }
    }
  }

  if (hitCoords.length > 0) {
    const affectedCoords = uniqueCoords(hitCoords);
    state.activeAction = {
      kind: "action",
      roundIndex,
      slotIndex,
      actionId: "storm_cloud",
      label: `${affectedCoords.length} Gewitterwolke${affectedCoords.length === 1 ? "" : "n"} treffen Figuren`,
      targetCoord: affectedCoords[0] ? { ...affectedCoords[0] } : undefined,
      affectedCoords,
      at: state.updatedAt
    };
  }

  if (isLastSlotOfRound) {
    state.delayedEffects = state.delayedEffects.filter((effect) => {
      if (effect.type !== "storm_cloud" || (effect.expiresRound ?? effect.triggerRound) > roundIndex) {
        return true;
      }

      removeBoardObject(
        state,
        state.boardObjects.find(
          (object) =>
            object.type === "storm_cloud_marker" &&
            object.triggerRound === effect.triggerRound &&
            sameCoord(object.coord, effect.coord)
        )?.id ?? ""
      );
      return false;
    });
  }
}

const roundEventDefinitions: Record<MagicArenaRoundEventId, { name: string; description: string }> = {
  flood_wave: {
    name: "Maremoto",
    description: "Un lado de la arena está marcado. Al final de la ronda, estos espacios de borde reciben 2 daños de campo."
  },
  unstable_arena: {
    name: "Arena inestable",
    description: "Al final de la ronda, todos los campos con 2 o menos HP reciben 1 daño de campo."
  },
  earthquake_warning: {
    name: "Advertencia de terremoto",
    description: "Seis campos marcados reciben cada uno 2 daños de campo al final de la ronda."
  },
  arcane_overload: {
    name: "Sobrecarga arcana",
    description: "El daño de campo directo de las cartas inflige +1 de daño de campo al objetivo principal."
  },
  thin_floor: {
    name: "Tierra delgada",
    description: "Cada daño de campo tiene un 10% adicional de probabilidad de 1 daño de campo adicional."
  },
  treasure_scent: {
    name: "Aroma del tesoro",
    description: "Cavar es más fuerte: nada se convierte en 1 carta, los cofres pequeños se convierten en 2 cartas."
  }
};

function getFloodWaveCoords(state: MagicArenaRuntimeState, direction: number): MagicArenaCoord[] {
  const radius = state.board.radius;

  return Object.values(state.board.fieldsByCoord)
    .filter((field) => {
      const { q, r } = field.coord;
      const s = -q - r;

      switch (direction) {
        case 0:
          return q === radius;
        case 1:
          return r === -radius;
        case 2:
          return s === radius;
        case 3:
          return q === -radius;
        case 4:
          return r === radius;
        case 5:
          return s === -radius;
        default:
          return false;
      }
    })
    .map((field) => ({ ...field.coord }))
    .sort((left, right) => axialKey(left).localeCompare(axialKey(right)));
}

function getEarthquakeWarningCoords(state: MagicArenaRuntimeState): MagicArenaCoord[] {
  const fields = Object.values(state.board.fieldsByCoord).filter((field) => field.exists);
  const innerFields = fields.filter((field) => {
    const { q, r } = field.coord;
    return Math.max(Math.abs(q), Math.abs(r), Math.abs(-q - r)) <= state.board.radius - 2;
  });
  const candidates = innerFields.length >= 6 ? innerFields : fields;

  return seededShuffle(candidates, `${state.rngSeed}:round-event-earthquake:${state.roundIndex}:${state.rngCounter}`)
    .slice(0, 6)
    .map((field) => ({ ...field.coord }))
    .sort((left, right) => axialKey(left).localeCompare(axialKey(right)));
}

function getUnstableArenaPreviewCoords(state: MagicArenaRuntimeState): MagicArenaCoord[] {
  return Object.values(state.board.fieldsByCoord)
    .filter((field) => field.exists && field.hp <= 2)
    .map((field) => ({ ...field.coord }))
    .sort((left, right) => axialKey(left).localeCompare(axialKey(right)));
}

function announceRoundEvent(state: MagicArenaRuntimeState, event: MagicArenaRoundEventState): void {
  state.currentRoundEvent = event;
  state.activeAction = {
    kind: "round_event",
    roundIndex: state.roundIndex,
    slotIndex: null,
    roundEventId: event.eventId,
    label: `Rundenereignis: ${event.name}\n${event.description}`,
    affectedCoords: event.markedCoords.map((coord) => ({ ...coord })),
    at: state.updatedAt
  };
  appendLog(state, `Rundenereignis: ${event.name}.`, null);
}

function maybeResolveRoundEvent(state: MagicArenaRuntimeState): void {
  state.currentRoundEvent = undefined;

  if (state.roundIndex < ROUND_EVENT_START_ROUND || rollUnit(state, "round-event") >= ROUND_EVENT_CHANCE) {
    return;
  }

  const eventId = seededShuffle<MagicArenaRoundEventId>(
    ["flood_wave", "unstable_arena", "earthquake_warning", "arcane_overload", "thin_floor", "treasure_scent"],
    `${state.rngSeed}:round-event-type:${state.roundIndex}:${state.rngCounter}`
  )[0] ?? "unstable_arena";
  const definition = roundEventDefinitions[eventId];
  const direction = eventId === "flood_wave"
    ? Math.floor(rollUnit(state, "flood-wave-direction") * 6)
    : undefined;
  const markedCoords =
    eventId === "flood_wave" && direction !== undefined
      ? getFloodWaveCoords(state, direction)
      : eventId === "earthquake_warning"
        ? getEarthquakeWarningCoords(state)
        : eventId === "unstable_arena"
          ? getUnstableArenaPreviewCoords(state)
          : [];

  announceRoundEvent(state, {
    id: `round-event-${state.roundIndex}`,
    eventId,
    name: definition.name,
    description: definition.description,
    roundIndex: state.roundIndex,
    markedCoords,
    direction
  });
}

export function resolveRoundEventEndEffects(state: MagicArenaRuntimeState): void {
  const event = state.currentRoundEvent;

  if (!event) {
    return;
  }

  state.activeAction = {
    kind: "round_event",
    roundIndex: state.roundIndex,
    slotIndex: null,
    roundEventId: event.eventId,
    label: `${event.name} wird ausgeloest`,
    affectedCoords: event.markedCoords.map((coord) => ({ ...coord })),
    at: state.updatedAt
  };

  if (event.eventId === "flood_wave") {
    appendLog(state, "El maremoto golpea los espacios de borde marcados.", null);
    for (const coord of event.markedCoords) {
      applyFieldDamage(state, coord, 2, "Flutwelle");
    }
  } else if (event.eventId === "unstable_arena") {
    const targets = Object.values(state.board.fieldsByCoord)
      .filter((field) => field.exists && field.hp <= 2)
      .map((field) => ({ ...field.coord }));
    state.activeAction.affectedCoords = targets;
    appendLog(state, "Instabile Arena laesst beschaedigte Felder nachgeben.", null);
    for (const coord of targets) {
      applyFieldDamage(state, coord, 1, "Instabile Arena");
    }
  } else if (event.eventId === "earthquake_warning") {
    appendLog(state, "El aviso de terremoto llega a los campos marcados.", null);
    for (const coord of event.markedCoords) {
      applyFieldDamage(state, coord, 2, "Erdbebenwarnung");
    }
  } else {
    appendLog(state, `${event.name} endet.`, null);
  }

  state.currentRoundEvent = undefined;
}

function hasRoundEventEndEffect(event: MagicArenaRoundEventState | undefined): boolean {
  return Boolean(
    event &&
      (event.eventId === "flood_wave" ||
        event.eventId === "unstable_arena" ||
        event.eventId === "earthquake_warning")
  );
}

function countFiguresOnIntactFields(state: MagicArenaRuntimeState): number {
  return state.players.reduce((count, player) => {
    return (
      count +
      player.figures.filter((figure) => {
        if (figure.status !== "alive" || !figure.coord) {
          return false;
        }

        const field = getField(state, figure.coord);
        return Boolean(field?.exists);
      }).length
    );
  }, 0);
}

function getArenaDecayDamage(state: MagicArenaRuntimeState): number {
  const figuresOnFields = countFiguresOnIntactFields(state);

  if (
    figuresOnFields <= ARENA_DECAY_SECOND_FIGURE_THRESHOLD ||
    state.roundIndex >= ARENA_DECAY_SECOND_ROUND
  ) {
    return ARENA_DECAY_SECOND_DAMAGE;
  }

  if (
    figuresOnFields <= ARENA_DECAY_FIRST_FIGURE_THRESHOLD ||
    state.roundIndex >= ARENA_DECAY_FIRST_ROUND
  ) {
    return ARENA_DECAY_FIRST_DAMAGE;
  }

  return 0;
}

function getOutermostExistingFieldCoords(state: MagicArenaRuntimeState): MagicArenaCoord[] {
  let outerDistance = -1;
  const existingFields = Object.values(state.board.fieldsByCoord).filter((field) => field.exists);

  for (const field of existingFields) {
    outerDistance = Math.max(outerDistance, hexDistance(field.coord, { q: 0, r: 0 }));
  }

  if (outerDistance < 0) {
    return [];
  }

  return existingFields
    .filter((field) => hexDistance(field.coord, { q: 0, r: 0 }) === outerDistance)
    .map((field) => ({ ...field.coord }));
}

export function resolveArenaDecayEndEffect(state: MagicArenaRuntimeState): boolean {
  const damage = getArenaDecayDamage(state);

  if (damage <= 0) {
    return false;
  }

  const affectedCoords = getOutermostExistingFieldCoords(state);

  if (affectedCoords.length === 0) {
    return false;
  }

  state.activeAction = {
    kind: "round_event",
    roundIndex: state.roundIndex,
    slotIndex: null,
    label: damage >= ARENA_DECAY_SECOND_DAMAGE
      ? "La arena se contrae significativamente"
      : "Los contratos de arena",
    affectedCoords,
    at: state.updatedAt
  };
  appendLog(
    state,
    `Der Arenarand bekommt ${damage} Feldschaden auf ${affectedCoords.length} Feld(ern).`,
    null
  );

  for (const coord of affectedCoords) {
    applyFieldDamage(state, coord, damage, "Arenarand");
  }

  return true;
}

export function buildExecutionQueue(state: MagicArenaRuntimeState): MagicArenaExecutionStep[] {
  const queue: MagicArenaExecutionStep[] = [];
  const initiativeOrder = getInitiativeOrder(state);

  for (let slotIndex = 0; slotIndex < PLANNED_ACTIONS_PER_ROUND; slotIndex += 1) {
    queue.push({
      kind: "bombs",
      roundIndex: state.roundIndex,
      slotIndex
    });

    for (const playerId of initiativeOrder) {
      const action = state.plannedActionsByPlayer[playerId]?.find((entry) => entry.slotIndex === slotIndex);

      if (action) {
        queue.push({
          kind: "action",
          roundIndex: state.roundIndex,
          slotIndex,
          action
        });
      }
    }

    queue.push({
      kind: "storm_clouds",
      roundIndex: state.roundIndex,
      slotIndex
    });
  }

  if (hasRoundEventEndEffect(state.currentRoundEvent)) {
    queue.push({
      kind: "round_event_end",
      roundIndex: state.roundIndex,
      slotIndex: PLANNED_ACTIONS_PER_ROUND - 1
    });
  }

  queue.push({
    kind: "arena_decay",
    roundIndex: state.roundIndex,
    slotIndex: PLANNED_ACTIONS_PER_ROUND - 1
  });

  return queue;
}

function allPlanningDone(state: MagicArenaRuntimeState): boolean {
  return state.players.every((player) => player.ready || getRequiredPlanningSlots(state, player.playerId) === 0);
}

function startExecution(state: MagicArenaRuntimeState, now: number): MagicArenaRuntimeState {
  state.magicPhase = "execution";
  state.currentSlot = 0;
  state.executionQueue = buildExecutionQueue(state);
  state.nextExecutionAt = now + Math.floor(EXECUTION_STEP_MS / 2);
  state.players = state.players.map((player) => ({
    ...player,
    ready: false
  }));
  appendLog(state, `Runde ${state.roundIndex}: Ausfuehrung startet.`, null);
  state.updatedAt = now;
  return state;
}

export function expireWaterAndRoundEffects(state: MagicArenaRuntimeState): void {
  for (const player of state.players) {
    for (const figure of player.figures) {
      if (figure.status === "in_water" && (figure.rescueDeadlineRound ?? Number.MAX_SAFE_INTEGER) <= state.roundIndex) {
        eliminateFigure(state, figure.figureId, "Rettungsfrist");
      }

      if (figure.stealthUntilRound !== undefined && figure.stealthUntilRound <= state.roundIndex) {
        delete figure.stealthUntilRound;
      }
    }
  }
}

function finishIfNeeded(state: MagicArenaRuntimeState, now: number): MagicArenaRuntimeState {
  const contenders = state.players.filter(hasLivingPresence);

  if (contenders.length > 1 || state.phase !== "playing") {
    return state;
  }

  const winner = contenders[0];
  const message = winner ? `${winner.name} gewinnt Magic Arena!` : "Magic Arena endet ohne Sieger.";

  state.magicPhase = "game_over";
  state.winnerPlayerId = winner?.playerId;
  state.executionQueue = [];
  state.nextExecutionAt = null;
  state.activeAction = {
    kind: "round_end",
    roundIndex: state.roundIndex,
    slotIndex: null,
    label: message,
    at: now
  };
  appendLog(state, message, null);
  return transitionRoundState(state, "locked", now, {
    durationMs: roundPhaseDurations.lockedMs,
    message
  });
}

function finishExecutionRound(state: MagicArenaRuntimeState, now: number): MagicArenaRuntimeState {
  state.activeAction = {
    kind: "round_end",
    roundIndex: state.roundIndex,
    slotIndex: null,
    label: `Runde ${state.roundIndex} endet`,
    at: now
  };
  expireWaterAndRoundEffects(state);

  const afterWinCheck = finishIfNeeded(state, now);

  if (afterWinCheck.magicPhase === "game_over") {
    return afterWinCheck;
  }

  state.initiativeStartIndex = state.players.length > 0 ? (state.initiativeStartIndex + 1) % state.players.length : 0;
  state.roundIndex += 1;
  state.currentSlot = null;
  state.executionQueue = [];
  state.nextExecutionAt = null;
  state.updatedAt = now;
  resolveTimeRiftsForRound(state, state.roundIndex);
  maybeSpawnCrystalFieldForRound(state);
  maybeResolveRoundEvent(state);
  return beginRound(state, now);
}

export function handleMagicArenaInput(
  state: MagicArenaRuntimeState,
  input: MagicArenaInput,
  context: ServerGameContext
): MagicArenaRuntimeState {
  if (input.type === "magic-arena:debug-start") {
    return state;
  }

  if (state.phase !== "playing" || state.magicPhase !== "planning") {
    return state;
  }

  const player = getPlayer(state, input.playerId);

  if (!player) {
    return state;
  }

  const nextState = cloneState(state);
  const nextPlayer = getPlayer(nextState, input.playerId);

  if (!nextPlayer) {
    return state;
  }

  if (input.type === "magic-arena:plan-slot") {
    const validation = validatePlannedAction(nextState, input.playerId, input.slotIndex, input.action);

    if (!validation.ok) {
      appendLog(nextState, validation.error, null);
      nextState.updatedAt = context.now;
      return nextState;
    }

    const existing = nextState.plannedActionsByPlayer[input.playerId] ?? [];
    nextState.plannedActionsByPlayer[input.playerId] = validation.action
      ? [...existing.filter((action) => action.slotIndex !== input.slotIndex), validation.action].sort((left, right) => left.slotIndex - right.slotIndex)
      : existing.filter((action) => action.slotIndex !== input.slotIndex);
    nextPlayer.ready = false;
    nextState.updatedAt = context.now;
    appendLog(nextState, `${nextPlayer.name} plant Slot ${input.slotIndex + 1}.`, null);
    return nextState;
  }

  if (input.type === "magic-arena:play-turn") {
    if (nextState.planningMode !== "sequential") {
      return state;
    }

    if (nextState.activePlayerId !== input.playerId) {
      appendLog(nextState, `${nextPlayer.name} ist gerade nicht am Zug.`, null);
      nextState.updatedAt = context.now;
      return nextState;
    }

    const validation = validatePlannedAction(
      nextState,
      input.playerId,
      nextState.planningSlotIndex,
      input.action
    );

    if (!validation.ok || !validation.action) {
      appendLog(nextState, validation.ok ? "El tren está incompleto." : validation.error, null);
      nextState.updatedAt = context.now;
      return nextState;
    }

    const plannedAction = validation.action;
    nextState.plannedActionsByPlayer[input.playerId] = [
      ...(nextState.plannedActionsByPlayer[input.playerId] ?? []).filter(
        (entry) => entry.slotIndex !== nextState.planningSlotIndex
      ),
      plannedAction
    ].sort((left, right) => left.slotIndex - right.slotIndex);
    nextPlayer.ready = true;
    nextState.magicPhase = "execution";
    nextState.currentSlot = nextState.planningSlotIndex;
    nextState.executionQueue = [
      {
        kind: "action",
        roundIndex: nextState.roundIndex,
        slotIndex: nextState.planningSlotIndex,
        action: plannedAction
      }
    ];
    nextState.nextExecutionAt = context.now;
    nextState.updatedAt = context.now;
    return nextState;
  }

  if (input.type === "magic-arena:ready") {
    if (nextState.planningMode === "sequential") {
      // Sequential turns are submitted atomically through play-turn.
      return state;
    }

    const requiredSlots = getRequiredPlanningSlots(nextState, input.playerId);
    const plannedSlots = new Set((nextState.plannedActionsByPlayer[input.playerId] ?? []).map((action) => action.slotIndex));

    if (plannedSlots.size < requiredSlots) {
      appendLog(nextState, `${nextPlayer.name} braucht noch ${requiredSlots - plannedSlots.size} Planungsslot(s).`, null);
      nextState.updatedAt = context.now;
      return nextState;
    }

    nextPlayer.ready = true;
    appendLog(nextState, `${nextPlayer.name} ist bereit.`, null);

    if (allPlanningDone(nextState)) {
      return startExecution(nextState, context.now);
    }

    nextState.updatedAt = context.now;
    return nextState;
  }

  return state;
}

function getExecutionStepDelayMs(step: MagicArenaExecutionStep): number {
  if (step.action?.cardId === "teleport") {
    return 1450;
  }

  return EXECUTION_STEP_MS;
}

export function tickMagicArena(
  state: MagicArenaRuntimeState,
  _deltaMs: number,
  context: ServerGameContext
): MagicArenaRuntimeState {
  if (state.phase !== "playing" || state.magicPhase !== "execution") {
    return state;
  }

  if (state.nextExecutionAt !== null && context.now < state.nextExecutionAt) {
    return state;
  }

  const nextState = cloneState(state);
  const step = nextState.executionQueue.shift();

  if (!step) {
    return nextState.planningMode === "sequential"
      ? advanceSequentialFlow(nextState, context.now)
      : finishExecutionRound(nextState, context.now);
  }

  nextState.currentSlot = step.slotIndex;
  nextState.updatedAt = context.now;
  let shouldPauseAfterStep = true;
  const activeActionBefore = nextState.activeAction;

  if (step.kind === "bombs") {
    resolveBombsForSlot(nextState, step.roundIndex, step.slotIndex);
  } else if (step.kind === "storm_clouds") {
    resolveStormCloudsForSlot(nextState, step.roundIndex, step.slotIndex);
  } else if (step.kind === "round_event_end") {
    resolveRoundEventEndEffects(nextState);
  } else if (step.kind === "arena_decay") {
    shouldPauseAfterStep = resolveArenaDecayEndEffect(nextState);
  } else if (step.action) {
    resolvePlannedAction(nextState, step.action);
  }

  if (
    nextState.planningMode === "sequential" &&
    (step.kind === "bombs" || step.kind === "storm_clouds") &&
    nextState.activeAction === activeActionBefore
  ) {
    shouldPauseAfterStep = false;
  }

  const afterWinCheck = finishIfNeeded(nextState, context.now);

  if (afterWinCheck.magicPhase === "game_over") {
    return afterWinCheck;
  }

  nextState.nextExecutionAt = shouldPauseAfterStep ? context.now + getExecutionStepDelayMs(step) : context.now;
  return nextState;
}

function redactPlans(
  state: MagicArenaRuntimeState,
  viewerPlayerId?: string
): Record<string, MagicArenaPlannedAction[]> {
  if (state.magicPhase !== "planning") {
    return state.plannedActionsByPlayer;
  }

  if (viewerPlayerId) {
    return {
      [viewerPlayerId]: state.plannedActionsByPlayer[viewerPlayerId] ?? []
    };
  }

  return {};
}

function isFigureVisibleToViewer(figure: MagicArenaFigureState, viewerPlayerId?: string): boolean {
  return figure.stealthUntilRound === undefined || figure.playerId === viewerPlayerId;
}

function toPublicPlayer(
  player: MagicArenaRuntimePlayer,
  context: ServerGameContext,
  viewerPlayerId?: string
): MagicArenaPlayerState {
  const livePlayer = context.players.find((entry) => entry.id === player.playerId);

  return {
    playerId: player.playerId,
    name: livePlayer?.name ?? player.name,
    color: livePlayer?.color ?? player.color,
    handCount: player.hand.length,
    figures: player.figures
      .filter((figure) => isFigureVisibleToViewer(figure, viewerPlayerId))
      .map((figure) => ({ ...figure })),
    ready: player.ready,
    connected: livePlayer?.connected ?? player.connected
  };
}

function toPublicBoard(state: MagicArenaRuntimeState, viewerPlayerId?: string): MagicArenaBoardState {
  const visibleFigureIds = new Set(
    state.players.flatMap((player) =>
      player.figures
        .filter((figure) => isFigureVisibleToViewer(figure, viewerPlayerId))
        .map((figure) => figure.figureId)
    )
  );
  const fieldsByCoord: MagicArenaBoardState["fieldsByCoord"] = {};

  for (const [key, field] of Object.entries(state.board.fieldsByCoord)) {
    fieldsByCoord[key] = {
      ...field,
      coord: { ...field.coord },
      occupants: field.occupants.filter((figureId) => visibleFigureIds.has(figureId))
    };
  }

  return {
    radius: state.board.radius,
    fieldsByCoord
  };
}

function toPublicBoardObjects(
  state: MagicArenaRuntimeState,
  viewerPlayerId?: string
): MagicArenaBoardObjectState[] {
  return state.boardObjects
    .filter((object) => !(viewerPlayerId && object.type === "storm_cloud_marker"))
    .filter((object) => object.visibleTo === "all" || (object.visibleTo === "owner" && object.ownerPlayerId === viewerPlayerId))
    .map((object) => ({
      ...object,
      coord: { ...object.coord }
    }));
}

export function toMagicArenaPublicState(
  state: MagicArenaRuntimeState,
  context: ServerGameContext,
  viewerPlayerId?: string
): MagicArenaPublicState {
  return {
    gameId: "magic-arena",
    magicPhase: state.magicPhase,
    planningMode: state.planningMode,
    planningSlotIndex: state.planningSlotIndex,
    totalPlanningSlots: getSlotsPerRound(state),
    activePlayerId: state.activePlayerId,
    roundIndex: state.roundIndex,
    currentSlot: state.currentSlot,
    initiativeStartIndex: state.initiativeStartIndex,
    initiativeOrder: getInitiativeOrder(state),
    players: state.players.map((player) => toPublicPlayer(player, context, viewerPlayerId)),
    board: toPublicBoard(state, viewerPlayerId),
    deckCount: state.deck.length,
    discardCount: state.discardPile.length,
    pendingBombs: state.pendingBombs,
    boardObjects: toPublicBoardObjects(state, viewerPlayerId),
    delayedEffects: state.delayedEffects.map(({ triggeredFigureKeys: _triggeredFigureKeys, ...effect }) => ({
      ...effect,
      coord: { ...effect.coord }
    })),
    plannedActionsByPlayer: redactPlans(state, viewerPlayerId),
    actionLog: state.actionLog.map(entry => ({ ...entry, message: arenaText(entry.message, context.language) })),
    activeAction: state.activeAction ? { ...state.activeAction, label: arenaText(state.activeAction.label, context.language), resultLabel: state.activeAction.resultLabel ? arenaText(state.activeAction.resultLabel, context.language) : undefined } : undefined,
    currentRoundEvent: state.currentRoundEvent
      ? {
          ...state.currentRoundEvent,
          name: arenaText(state.currentRoundEvent.name, context.language),
          description: arenaText(state.currentRoundEvent.description, context.language),
          markedCoords: state.currentRoundEvent.markedCoords.map((coord) => ({ ...coord }))
        }
      : undefined,
    winnerPlayerId: state.winnerPlayerId,
    rngSeed: state.rngSeed,
    cardDefinitions: magicArenaCardDefinitions.map(card => ({ ...card, name: arenaText(card.name, context.language), type: arenaText(card.type, context.language), description: arenaText(card.description, context.language) }))
  };
}

export function toMagicArenaControllerState(
  state: MagicArenaRuntimeState,
  context: ServerGameContext,
  playerId: string
): MagicArenaControllerState {
  const publicState = toMagicArenaPublicState(state, context, playerId);
  const player = getPlayer(state, playerId);

  return {
    ...publicState,
    ownPlayerId: playerId,
    ownPlayer: player
      ? {
          ...toPublicPlayer(player, context),
          figures: player.figures.map((figure) => ({ ...figure })),
          hand: player.hand,
          requiredSlots: getRequiredPlanningSlots(state, playerId)
        }
      : null
  };
}

export function isMagicArenaFinished(state: MagicArenaRuntimeState): boolean {
  return state.phase === "locked" || state.magicPhase === "game_over";
}

export function buildMagicArenaScore(state: MagicArenaRuntimeState): ScoreEntry[] {
  return state.winnerPlayerId
    ? [
        {
          playerId: state.winnerPlayerId,
          delta: 1,
          reason: "Victoria en Hexagonia"
        }
      ]
    : [];
}
