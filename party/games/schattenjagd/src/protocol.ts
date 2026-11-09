import type { BaseRoundState, PlayerInput } from "@open-party-lab/game-core";
import type { ShadowAssignmentMode, TransportMode } from "./config.js";

export type SchattenjagdStage =
  | "shadow_move"
  | "detective_move"
  | "caught"
  | "escaped";

/** Die Brettvariante wird beim Erzeugen der Runde festgelegt. */
export type CityMapVariant = "day" | "night";

export interface StationNode {
  id: number;
  x: number;
  y: number;
  modes: TransportMode[];
}

export interface StationLink {
  a: number;
  b: number;
  mode: TransportMode;
}

export interface TransitMap {
  width: number;
  height: number;
  stations: StationNode[];
  links: StationLink[];
  /** Nachbarn je Station und Verkehrsmittel. */
  neighbours: Record<number, Record<TransportMode, number[]>>;
}

export interface TicketWallet {
  taxi: number;
  bus: number;
  metro: number;
}

export interface DetectiveState {
  playerId: string;
  stationId: number;
  tickets: TicketWallet;
  movedInTurn: number;
  lastMode: TransportMode | null;
}

export interface TravelLogEntry {
  turn: number;
  mode: TransportMode | null;
  shrouded: boolean;
  doubleMove: boolean;
  revealedStationId: number | null;
}

export interface MoveInput extends PlayerInput {
  type: "move";
  stationId: number;
  mode: TransportMode;
}

export interface ToggleShroudInput extends PlayerInput {
  type: "toggle_shroud";
}

export interface ToggleDoubleMoveInput extends PlayerInput {
  type: "toggle_double_move";
}

export type SchattenjagdInput = MoveInput | ToggleShroudInput | ToggleDoubleMoveInput;

export interface SchattenjagdConfigureLobbyHostAction {
  type: "configure-lobby";
  shadowMode?: string;
  shadowSeat?: number;
  turnTime?: string | number;
}

export interface SchattenjagdState extends BaseRoundState {
  activeDetectivePlayerId: string | null;
  stage: SchattenjagdStage;
  turn: number;
  totalTurns: number;
  mapVariant: CityMapVariant;
  map: TransitMap;
  shadowMode: ShadowAssignmentMode;
  shadowPlayerId: string | null;
  shadowStationId: number;
  shadowShroudsLeft: number;
  shadowDoublesLeft: number;
  shroudArmed: boolean;
  doubleArmed: boolean;
  /** Zweiter Teilzug eines Doppelzugs laeuft. */
  doubleMoveStep: number;
  lastRevealedStationId: number | null;
  lastRevealTurn: number | null;
  detectives: DetectiveState[];
  travelLog: TravelLogEntry[];
  /** Zeitfenster pro Zug in Millisekunden, null bedeutet ohne Zeitlimit. */
  turnDurationMs: number | null;
  turnEndsAt: number | null;
  aiDecideAt: number | null;
  capturedByPlayerId: string | null;
  captureStationId: number | null;
  outcome: "caught" | "escaped" | null;
}

export interface PublicDetective {
  playerId: string;
  name: string;
  color: string;
  stationId: number;
  tickets: TicketWallet;
  lastMode: TransportMode | null;
  hasMoved: boolean;
  connected: boolean;
  isCapturer: boolean;
}

export interface SchattenjagdPublicState {
  activeDetectivePlayerId: string | null;
  reachableOptions: SchattenjagdMoveOption[];
  stage: SchattenjagdStage;
  turn: number;
  totalTurns: number;
  mapVariant: CityMapVariant;
  revealTurns: readonly number[];
  nextRevealTurn: number | null;
  map: TransitMap;
  detectives: PublicDetective[];
  shadow: {
    name: string;
    isAi: boolean;
    lastKnownStationId: number | null;
    lastRevealTurn: number | null;
    currentStationId: number | null;
    shroudsLeft: number;
    doublesLeft: number;
    shroudArmed: boolean;
    doubleArmed: boolean;
  };
  travelLog: TravelLogEntry[];
  turnEndsAt: number | null;
  turnDurationMs: number | null;
  outcome: "caught" | "escaped" | null;
  capturedByName: string | null;
  captureStationId: number | null;
  message?: string;
}

export interface SchattenjagdMoveOption {
  stationId: number;
  mode: TransportMode;
  ticketsLeft: number;
  blocked: boolean;
  blockedReason?: "occupied" | "no_ticket";
}

export interface SchattenjagdControllerState {
  activeDetectiveName: string | null;
  role: "shadow" | "detective" | "spectator";
  roleLabel: string;
  stage: SchattenjagdStage;
  turn: number;
  totalTurns: number;
  nextRevealTurn: number | null;
  isMyTurn: boolean;
  hasMoved: boolean;
  currentStationId: number | null;
  options: SchattenjagdMoveOption[];
  tickets: TicketWallet | null;
  shroudsLeft: number;
  doublesLeft: number;
  shroudArmed: boolean;
  doubleArmed: boolean;
  doubleMoveStep: number;
  lastKnownShadowStationId: number | null;
  lastRevealTurn: number | null;
  detectiveStations: Array<{ playerId: string; name: string; stationId: number; hasMoved: boolean }>;
  travelLog: TravelLogEntry[];
  turnEndsAt: number | null;
  turnDurationMs: number | null;
  outcome: "caught" | "escaped" | null;
  shadowName: string | null;
  message?: string;
}
