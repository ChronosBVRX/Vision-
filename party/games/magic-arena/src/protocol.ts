import type { PlayerInput } from "@open-party-lab/game-core";

export interface MagicArenaCoord {
  q: number;
  r: number;
}

export type MagicArenaFigureStatus = "alive" | "in_water" | "eliminated";
export type MagicArenaFieldType = "weak" | "normal" | "strong";
export type MagicArenaSpecialFieldType = "crystal" | "explosive" | "cracked";
export type MagicArenaPhase = "setup" | "planning" | "execution" | "game_over";
export type MagicArenaPlanningMode = "planned" | "sequential";
export type MagicArenaRoundEventId =
  | "flood_wave"
  | "unstable_arena"
  | "earthquake_warning"
  | "arcane_overload"
  | "thin_floor"
  | "treasure_scent";

export type MagicArenaBaseActionId = "move" | "punch" | "dig";

export type MagicArenaCardId =
  | "sprint"
  | "charge_run"
  | "enemy_swap"
  | "lightning_strike"
  | "fireball"
  | "hammer_blow"
  | "bomb"
  | "meteor_strike"
  | "rift"
  | "earthquake"
  | "platform_break"
  | "stealth"
  | "shockwave"
  | "chain_lightning"
  | "pitfall"
  | "teleport"
  | "kraken"
  | "time_rift"
  | "magic_mine"
  | "storm_cloud";

export interface MagicArenaCardDefinition {
  cardId: MagicArenaCardId;
  name: string;
  type: string;
  frequency: number;
  range: number | null;
  description: string;
}

export interface MagicArenaCardInstanceState {
  instanceId: string;
  cardId: MagicArenaCardId;
}

export interface MagicArenaFieldState {
  coord: MagicArenaCoord;
  exists: boolean;
  hp: number;
  maxHp: number;
  type: MagicArenaFieldType;
  specialType: MagicArenaSpecialFieldType | null;
  occupants: string[];
}

export interface MagicArenaBoardState {
  radius: number;
  fieldsByCoord: Record<string, MagicArenaFieldState>;
}

export interface MagicArenaFigureState {
  figureId: string;
  playerId: string;
  name: string;
  hp: number;
  maxHp: number;
  status: MagicArenaFigureStatus;
  coord?: MagicArenaCoord;
  waterCoord?: MagicArenaCoord;
  rescueDeadlineRound?: number;
  stealthUntilRound?: number;
}

export interface MagicArenaPlayerState {
  playerId: string;
  name: string;
  color: string;
  handCount: number;
  figures: MagicArenaFigureState[];
  ready: boolean;
  connected: boolean;
}

export interface MagicArenaPlannedAction {
  playerId: string;
  slotIndex: number;
  figureId: string;
  actionKind: "base" | "card";
  baseActionId?: MagicArenaBaseActionId;
  cardInstanceId?: string;
  cardId?: MagicArenaCardId;
  targetCoord?: MagicArenaCoord;
  path?: MagicArenaCoord[];
  direction?: number;
  distance?: number;
  extra?: Record<string, unknown>;
}

export interface MagicArenaPendingBombState {
  bombId: string;
  ownerPlayerId: string;
  coord: MagicArenaCoord;
  explodeRound: number;
  explodeSlot: number;
}

export interface MagicArenaDelayedEffectState {
  id: string;
  type: "time_rift" | "storm_cloud";
  ownerPlayerId: string;
  coord: MagicArenaCoord;
  createdRound: number;
  triggerRound: number;
  remainingTriggers?: number;
  expiresRound?: number;
  triggeredFigureKeys?: string[];
}

export interface MagicArenaBoardObjectState {
  id: string;
  type: "bomb" | "pitfall" | "magic_mine" | "storm_cloud_marker" | "time_rift_marker";
  ownerPlayerId: string;
  coord: MagicArenaCoord;
  visibleTo: "all" | "owner" | "debug";
  createdRound: number;
  createdSlot: number | null;
  triggerRound?: number;
  triggerSlot?: number;
}

export interface MagicArenaRoundEventState {
  id: string;
  eventId: MagicArenaRoundEventId;
  name: string;
  description: string;
  roundIndex: number;
  markedCoords: MagicArenaCoord[];
  direction?: number;
}

export interface MagicArenaActionLogEntry {
  id: number;
  roundIndex: number;
  slotIndex: number | null;
  message: string;
}

export interface MagicArenaActiveActionState {
  kind: "bombs" | "action" | "round_end" | "round_event";
  roundIndex: number;
  slotIndex: number | null;
  playerId?: string;
  figureId?: string;
  actionId?: MagicArenaBaseActionId | MagicArenaCardId;
  roundEventId?: MagicArenaRoundEventId;
  label: string;
  originCoord?: MagicArenaCoord;
  targetCoord?: MagicArenaCoord;
  path?: MagicArenaCoord[];
  affectedCoords?: MagicArenaCoord[];
  resultLabel?: string;
  at: number;
}

export interface MagicArenaPublicState {
  gameId: "magic-arena";
  magicPhase: MagicArenaPhase;
  planningMode: MagicArenaPlanningMode;
  planningSlotIndex: number;
  totalPlanningSlots: number;
  activePlayerId: string | null;
  roundIndex: number;
  currentSlot: number | null;
  initiativeStartIndex: number;
  initiativeOrder: string[];
  players: MagicArenaPlayerState[];
  board: MagicArenaBoardState;
  deckCount: number;
  discardCount: number;
  pendingBombs: MagicArenaPendingBombState[];
  boardObjects: MagicArenaBoardObjectState[];
  delayedEffects: MagicArenaDelayedEffectState[];
  plannedActionsByPlayer: Record<string, MagicArenaPlannedAction[]>;
  actionLog: MagicArenaActionLogEntry[];
  activeAction?: MagicArenaActiveActionState;
  currentRoundEvent?: MagicArenaRoundEventState;
  winnerPlayerId?: string;
  rngSeed: string;
  cardDefinitions: MagicArenaCardDefinition[];
}

export interface MagicArenaControllerPlayerState extends MagicArenaPlayerState {
  hand: MagicArenaCardInstanceState[];
  requiredSlots: number;
}

export interface MagicArenaControllerState extends MagicArenaPublicState {
  ownPlayerId: string;
  ownPlayer: MagicArenaControllerPlayerState | null;
}

export interface MagicArenaPlanSlotInput extends PlayerInput {
  type: "magic-arena:plan-slot";
  slotIndex: number;
  action: Omit<MagicArenaPlannedAction, "playerId" | "slotIndex"> | null;
}

export interface MagicArenaReadyInput extends PlayerInput {
  type: "magic-arena:ready";
}

/**
 * Sequential mode: plan and immediately execute the acting player's single turn.
 */
export interface MagicArenaPlayTurnInput extends PlayerInput {
  type: "magic-arena:play-turn";
  action: Omit<MagicArenaPlannedAction, "playerId" | "slotIndex">;
}

export interface MagicArenaDebugStartInput extends PlayerInput {
  type: "magic-arena:debug-start";
  playerCount: number;
}

export type MagicArenaInput =
  | MagicArenaPlanSlotInput
  | MagicArenaReadyInput
  | MagicArenaPlayTurnInput
  | MagicArenaDebugStartInput;
