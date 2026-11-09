import type { BaseRoundState, PlayerInput } from "@open-party-lab/game-core";

export type DungeonActionId = "fight" | "loot" | "aid" | "warrior_guard" | "mage_burst" | "rogue_lift" | "cleric_heal" | "bard_inspire" | "tinkerer_improvise";
export type DungeonCardEffect = "intrigue" | "false_bill" | "rally" | "ward" | "jam" | "reinforce" | "heal" | "weaken" | "fortune" | "barrier";
export type DungeonCampAction = "rest" | "stock" | "upgrade" | "scout";

export interface DungeonRoomRule {
  id: "frontline" | "echo" | "arcane" | "riches" | "danger";
  name: string;
  description: string;
  symbol: string;
}

export interface DungeonCampResult {
  playerId: string;
  playerName: string;
  action: DungeonCampAction;
  healthDelta: number;
  goldDelta: number;
  powerDelta: number;
  cards: DungeonCard[];
}

export type DungeonPartyInput = (PlayerInput & {
  type: "dungeon_action";
  action: DungeonActionId;
  cardId?: string;
  targetPlayerId?: string;
}) | (PlayerInput & {
  type: "dungeon_route_vote";
  routeId: string;
}) | (PlayerInput & {
  type: "dungeon_card_response";
  cardId?: string;
  targetPlayerId?: string;
}) | (PlayerInput & {
  type: "dungeon_camp_action";
  action: DungeonCampAction;
}) | (PlayerInput & {
  type: "dungeon_continue";
});

export interface DungeonRouteOption {
  id: string;
  name: string;
  description: string;
  artKey: string;
  kind: DungeonEncounter["kind"];
  difficulty: number;
  health: number;
  maxHealth: number;
  cleared: false;
}

export interface DungeonHero {
  playerId: string;
  name: string;
  classId: string;
  health: number;
  fame: number;
  gold: number;
  items: DungeonItem[];
  handCount?: number;
  lastAction?: DungeonActionId;
  lastOutcome?: string;
  lastFameDelta?: number;
}

export interface DungeonResolutionPlayer {
  playerId: string;
  name: string;
  classId: string;
  action: DungeonActionId;
  roll: number;
  originalRoll?: number;
  contribution: number;
  healthDelta: number;
  fameDelta: number;
  goldDelta: number;
  outcome?: string;
}

export interface DungeonResolutionCard {
  playerId: string;
  playerName: string;
  cardId: string;
  name: string;
  description: string;
  kind?: "equipment" | "effect";
  source?: "loot" | "boss" | "room";
  effect?: DungeonCardEffect;
  targetPlayerId?: string;
  targetName?: string;
}

export interface DungeonResolution {
  id: string;
  encounterId: string;
  encounterName: string;
  success: boolean;
  partyPower: number;
  sharedPower: number;
  targetDifficulty: number;
  difficultyModifier: number;
  heroes: DungeonResolutionPlayer[];
  cards: DungeonResolutionCard[];
  rewards: Array<DungeonResolutionCard & { kind: "equipment" | "effect"; source: "loot" | "boss" | "room" }>;
}

export interface DungeonItem {
  id: string;
  name: string;
  description: string;
  attack: number;
}

export interface DungeonCard extends DungeonItem {
  kind: "equipment" | "effect";
  effect?: DungeonCardEffect;
  sabotagePenalty?: number;
  sabotageReward?: number;
}

export interface DungeonEncounter {
  id: string;
  name: string;
  kind: "battle" | "challenge" | "event" | "boss";
  flavor: string;
  artKey?: string;
  rule?: DungeonRoomRule;
  difficulty: number;
  health: number;
  maxHealth: number;
  cleared: boolean;
  phaseIndex?: number;
  phaseName?: string;
  phaseCount?: number;
  attempt?: number;
  attemptLimit?: number;
  partyPower?: number;
  resolution?: string;
}

export interface DungeonPartyState extends BaseRoundState {
  stage: "voting" | "planning" | "response" | "reveal" | "camp" | "camp_reveal" | "complete";
  language: "de" | "en";
  encounterIndex: number;
  encounters: DungeonEncounter[];
  heroes: DungeonHero[];
  actionsByPlayer: Record<string, { action: DungeonActionId; cardId?: string; targetPlayerId?: string }>;
  responsesByPlayerId: Record<string, { cardId?: string; targetPlayerId?: string }>;
  routeVotesByPlayer: Record<string, string>;
  continueByPlayerId: Record<string, true>;
  routeHistory: Array<{ encounterIndex: number; routeId: string; name: string }>;
  routeOptions?: DungeonRouteOption[];
  handsByPlayerId: Record<string, DungeonCard[]>;
  partyMorale: number;
  bossPhaseIndex: number;
  bossPhaseAttempts: number;
  seed: number;
  campChoicesByPlayer: Record<string, DungeonCampAction>;
  campResults: DungeonCampResult[];
  completedCamps: number[];
  preparedPower: number;
  winnerPlayerId?: string;
  winnerPlayerIds?: string[];
  campaignWon?: boolean;
  lastResolution?: DungeonResolution;
  lastRolls?: Array<{ playerId: string; roll: number; originalRoll?: number; contribution: number }>;
}

export type DungeonPartyPublicState = Omit<DungeonPartyState, "actionsByPlayer" | "responsesByPlayerId" | "handsByPlayerId" | "routeVotesByPlayer" | "campChoicesByPlayer" | "seed"> & {
  submittedCount: number;
  responseCount: number;
  routeVoteCount: number;
  campSubmittedCount: number;
  revealedActionsByPlayer: Record<string, DungeonActionId>;
};

export interface DungeonPartyControllerState extends DungeonPartyPublicState {
  ownActionSubmitted: boolean;
  ownResponseSubmitted: boolean;
  ownVoteSubmitted: boolean;
  ownContinueSubmitted: boolean;
  ownHasPlayableResponse: boolean;
  ownCampSubmitted: boolean;
  ownResponseCard?: DungeonCard;
  ownResponseTargetName?: string;
  availableTargets: Array<{ playerId: string; name: string }>;
  ownHand: DungeonCard[];
}
