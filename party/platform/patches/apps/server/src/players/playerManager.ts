import {
  canManagePlayerRoster,
  type BotDifficulty,
  type JoinRoomRequest,
  type PlayerSetupValue,
  type PlayerSnapshot
} from "@open-party-lab/protocol";
import { createId } from "../core/ids/createId.js";
import { now } from "../core/time/now.js";
import type { PlayerRecord, RoomRecord } from "../rooms/roomStore.js";
import { PlayerPresenceTracker } from "./playerPresenceTracker.js";
import { PlayerStore } from "./playerStore.js";
import { ReconnectService } from "./reconnectService.js";

const playerColors = [
  "#ef4444",
  "#f59e0b",
  "#d946ef",
  "#0ea5e9",
  "#8b5cf6",
  "#ec4899",
  "#f97316",
  "#14b8a6"
];

export interface JoinPlayerResult {
  player: PlayerRecord;
  resumed: boolean;
}

export interface RemovePlayerResult {
  player: PlayerRecord;
  socketId: string | null;
}

export interface RemovePlayerError {
  error: string;
}

export type RemovePlayerOutcome = RemovePlayerResult | RemovePlayerError;

export class PlayerManager {
  constructor(
    private readonly playerStore: PlayerStore,
    private readonly presenceTracker: PlayerPresenceTracker,
    private readonly reconnectService: ReconnectService,
    private readonly getNow: () => number = now
  ) {}

  joinPlayer(room: RoomRecord, request: JoinRoomRequest, socketId: string): JoinPlayerResult {
    const existingPlayer = this.playerStore.findByDeviceId(room, request.deviceId);

    if (existingPlayer) {
      existingPlayer.setupSelectionsByGameId ??= {};

      if (request.playerName.trim()) {
        existingPlayer.name = request.playerName.trim();
      }

      this.presenceTracker.markConnected(existingPlayer, socketId);
      this.reconnectService.bindPlayer(existingPlayer, room.code, socketId);

      return {
        player: existingPlayer,
        resumed: true
      };
    }

    const player = this.playerStore.add(room, {
      id: createId("player"),
      deviceId: request.deviceId,
      name: request.playerName.trim(),
      color: request.preferredColor ?? this.pickColor(room),
      selectedCharacterId: null,
      setupSelectionsByGameId: {},
      score: 0,
      isReady: false,
      connected: true,
      presence: "online",
      socketId,
      reconnectToken: this.reconnectService.createReconnectToken(),
      joinedAt: this.getNow(),
      lastSeenAt: this.getNow(),
      reconnectGraceEndsAt: null
    });

    this.reconnectService.bindPlayer(player, room.code, socketId);

    return {
      player,
      resumed: false
    };
  }

  /**
   * Seats a server-driven player.
   *
   * Bots have no socket and never reconnect: they are born connected so every
   * game's `connected` filters keep them in play. The caller (BotManager) is
   * responsible for the game setup choices and readiness.
   */
  addBot(
    room: RoomRecord,
    options: { name: string; difficulty: BotDifficulty; color?: string }
  ): PlayerRecord {
    const player = this.playerStore.add(room, {
      id: createId("bot"),
      deviceId: `bot:${room.code}:${createId("seat")}`,
      name: options.name.trim(),
      color: options.color ?? this.pickColor(room),
      selectedCharacterId: null,
      setupSelectionsByGameId: {},
      score: 0,
      isReady: true,
      connected: true,
      presence: "online",
      socketId: null,
      reconnectToken: `bot:${createId("token")}`,
      joinedAt: this.getNow(),
      lastSeenAt: this.getNow(),
      reconnectGraceEndsAt: null,
      isBot: true,
      botDifficulty: options.difficulty
    });

    return player;
  }

  resumePlayer(
    room: RoomRecord,
    reconnectToken: string,
    deviceId: string,
    socketId: string
  ): PlayerRecord | null {
    const player = this.playerStore.findByReconnectToken(room, reconnectToken);

    if (!player || player.reconnectToken !== reconnectToken || player.deviceId !== deviceId) {
      return null;
    }

    this.presenceTracker.markConnected(player, socketId);
    this.reconnectService.attachSocket(reconnectToken, socketId);
    return player;
  }

  setReady(room: RoomRecord, playerId: string, isReady: boolean): PlayerRecord | null {
    const player = this.playerStore.get(room, playerId);

    if (!player) {
      return null;
    }

    player.isReady = isReady;
    return player;
  }

  setSelectedCharacter(room: RoomRecord, playerId: string, characterId: string): PlayerRecord | null {
    const player = this.playerStore.get(room, playerId);

    if (!player) {
      return null;
    }

    player.selectedCharacterId = characterId;
    return player;
  }

  setPlayerSetup(
    room: RoomRecord,
    playerId: string,
    gameId: string,
    selectionKey: string,
    value: PlayerSetupValue
  ): PlayerRecord | null {
    const player = this.playerStore.get(room, playerId);

    if (!player) {
      return null;
    }

    const currentGameSelections = player.setupSelectionsByGameId[gameId] ?? {};
    player.setupSelectionsByGameId = {
      ...player.setupSelectionsByGameId,
      [gameId]: {
        ...currentGameSelections,
        [selectionKey]: Array.isArray(value) ? [...value] : value
      }
    };
    player.isReady = false;

    return player;
  }

  setAllReady(room: RoomRecord, isReady: boolean): boolean {
    let changed = false;

    for (const player of room.players.values()) {
      if (player.isReady === isReady) {
        continue;
      }

      player.isReady = isReady;
      changed = true;
    }

    return changed;
  }

  disconnectPlayer(room: RoomRecord, socketId: string): PlayerRecord | null {
    const player = this.playerStore.findBySocket(room, socketId);

    if (!player) {
      return null;
    }

    this.presenceTracker.markDisconnected(player);
    this.reconnectService.detachSocket(socketId);
    return player;
  }

  removePlayer(room: RoomRecord, playerId: string): RemovePlayerOutcome {
    if (!canManagePlayerRoster(room)) {
      return {
        error: room.language === "en"
          ? "Players can only be removed while no active round is running."
          : "Los jugadores solo se pueden expulsar cuando no hay una ronda activa."
      };
    }

    const player = this.playerStore.get(room, playerId);

    if (!player) {
      return {
        error: room.language === "en" ? "Player not found." : "Jugador no encontrado."
      };
    }

    const socketId = player.socketId;
    room.players.delete(player.id);
    this.reconnectService.removePlayerSession(room.code, player.id);

    if (socketId) {
      this.reconnectService.detachSocket(socketId);
    }

    return {
      player,
      socketId
    };
  }

  expireDisconnectedPlayers(room: RoomRecord): boolean {
    return this.presenceTracker.expireGraceWindows(room);
  }

  toSnapshot(player: PlayerRecord): PlayerSnapshot {
    const setupSelections = Object.assign({}, ...Object.values(player.setupSelectionsByGameId ?? {}));

    return {
      id: player.id,
      name: player.name,
      avatar: player.avatar ?? null,
      color: player.color,
      selectedCharacterId: player.selectedCharacterId,
      selectedCharacterName: null,
      setupSelections,
      isReady: player.isReady,
      connected: player.connected,
      presence: player.presence,
      score: player.score,
      joinedAt: player.joinedAt,
      lastSeenAt: player.lastSeenAt,
      reconnectGraceEndsAt: player.reconnectGraceEndsAt,
      isBot: player.isBot === true,
      botDifficulty: player.botDifficulty
    };
  }

  private pickColor(room: RoomRecord): string {
    return playerColors[room.players.size % playerColors.length] ?? playerColors[0];
  }
}
