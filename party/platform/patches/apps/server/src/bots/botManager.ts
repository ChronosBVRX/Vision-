import { resolveGameMaxPlayers, type GameManifest } from "@open-party-lab/game-core";
import type { BotDifficulty } from "@open-party-lab/protocol";
import type { GameRegistry } from "../game-engine/gameRegistry.js";
import type { GameRuntime } from "../game-engine/gameRuntime.js";
import { now as defaultNow } from "../core/time/now.js";
import type { PlayerManager } from "../players/playerManager.js";
import { assignPlayerSetup } from "../rooms/playerSetupAssignments.js";
import type { PlayerRecord, RoomRecord } from "../rooms/roomStore.js";
import { BOT_GAME_IDS, getBotAdapter } from "./botRegistry.js";
import { createBotRandom } from "./random.js";

const BOT_NAMES = [
  "Bot Nova",
  "Bot Rocco",
  "Bot Lola",
  "Bot Tito",
  "Bot Maya",
  "Bot Chuy",
  "Bot Vera",
  "Bot Beto"
];

export interface AddBotsResult {
  added: PlayerRecord[];
  error?: string;
}

/**
 * Owns the server-driven seats.
 *
 * The manager seats bots as real players (roster, scoring, min/max players) and
 * feeds their inputs through the same game `handleInput` a phone uses. It never
 * gives them host control or a socket.
 */
export class BotManager {
  private readonly memory = new Map<string, Map<string, Map<string, unknown>>>();

  constructor(
    private readonly playerManager: PlayerManager,
    private readonly gameRegistry: GameRegistry,
    private readonly gameRuntime: GameRuntime,
    private readonly getNow: () => number = defaultNow
  ) {}

  supportsGame(gameId: string | null | undefined): boolean {
    return Boolean(gameId && BOT_GAME_IDS.has(gameId));
  }

  addBots(
    room: RoomRecord,
    count: number,
    difficulty: BotDifficulty = "hard"
  ): AddBotsResult {
    const gameId = room.selectedGameId;
    if (!gameId) {
      return { added: [], error: "Elige un juego antes de agregar bots." };
    }
    if (!this.supportsGame(gameId)) {
      return { added: [], error: "Este juego todavía no tiene bots." };
    }
    if (room.currentRound && room.currentRound.phase !== "finished") {
      return { added: [], error: "Solo puedes agregar bots en el lobby." };
    }

    const game = this.gameRegistry.getAvailableGame(
      gameId,
      room.language,
      room.gameSettingsByGameId[gameId]
    );
    if (!game) {
      return { added: [], error: "Juego desconocido." };
    }

    const maximum = resolveGameMaxPlayers(game, room.gameSettingsByGameId[gameId]);
    const freeSlots = Math.max(0, maximum - room.players.size);
    const requested = Math.max(1, Math.min(8, Math.floor(count)));
    const amount = Math.min(requested, freeSlots);

    if (amount <= 0) {
      return { added: [], error: "La sala ya está llena." };
    }

    const existingNames = new Set([...room.players.values()].map((player) => player.name));
    const added: PlayerRecord[] = [];

    for (let index = 0; index < amount; index += 1) {
      const name = BOT_NAMES.find((candidate) => !existingNames.has(candidate)) ?? `Bot ${room.players.size + 1}`;
      existingNames.add(name);
      added.push(this.playerManager.addBot(room, { name, difficulty }));
    }

    // Characters are balanced across the table; multi-select seats get a
    // deterministic legal choice so the round can actually start.
    assignPlayerSetup(room, game);
    for (const bot of added) {
      this.ensureBotSetup(room, game, bot);
    }

    return { added };
  }

  removeBot(room: RoomRecord, playerId: string): boolean {
    const player = room.players.get(playerId);
    if (!player?.isBot) {
      return false;
    }
    room.players.delete(playerId);
    this.memory.get(room.code)?.delete(playerId);
    return true;
  }

  forgetPlayer(room: RoomRecord, playerId: string): void {
    this.memory.get(room.code)?.delete(playerId);
  }

  clearBots(room: RoomRecord): void {
    for (const player of [...room.players.values()]) {
      if (player.isBot) {
        room.players.delete(player.id);
      }
    }
    this.memory.delete(room.code);
  }

  onRoundStart(room: RoomRecord): void {
    for (const memory of this.memory.get(room.code)?.values() ?? []) {
      memory.clear();
    }
  }

  onRoomClosed(roomCode: string): void {
    this.memory.delete(roomCode);
  }

  /** Called once per room tick, before the game's own `tick`. */
  stepRoom(room: RoomRecord, _deltaMs: number): boolean {
    if (!room.currentRound || room.clock.pausedAt !== null) {
      return false;
    }

    const gameId = room.currentRound.gameId;
    const adapter = getBotAdapter(gameId);
    if (!adapter) {
      return false;
    }

    const manifest = this.gameRegistry.require(gameId).manifest;
    const currentNow = this.getNow();
    const roundKey = `${room.currentRound.roundNumber}:${room.currentRound.startedAt ?? 0}`;
    let changed = false;

    for (const bot of room.players.values()) {
      if (!bot.isBot || !bot.connected) {
        continue;
      }

      const memory = this.memoryFor(room, bot.id);
      if (memory.get("round") !== roundKey) {
        memory.clear();
        memory.set("round", roundKey);
      }
      const lastThinkAt = Number(memory.get("lastThinkAt") ?? 0);
      if (currentNow - lastThinkAt < adapter.thinkEveryMs) {
        continue;
      }
      memory.set("lastThinkAt", currentNow);

      adapter.think({
        room,
        bot,
        state: room.currentRound.state,
        manifest,
        now: currentNow,
        difficulty: bot.botDifficulty ?? "hard",
        memory,
        input: (input) => {
          if (this.gameRuntime.applyPlayerInput(room, bot.id, input)) {
            changed = true;
          }
        }
      });
    }

    return changed;
  }

  private memoryFor(room: RoomRecord, playerId: string): Map<string, unknown> {
    let roomMemory = this.memory.get(room.code);
    if (!roomMemory) {
      roomMemory = new Map();
      this.memory.set(room.code, roomMemory);
    }
    let botMemory = roomMemory.get(playerId);
    if (!botMemory) {
      botMemory = new Map();
      roomMemory.set(playerId, botMemory);
    }
    return botMemory;
  }

  private ensureBotSetup(
    room: RoomRecord,
    game: { id: string; playerSetup?: GameManifest["playerSetup"] },
    bot: PlayerRecord
  ): void {
    const setup = game.playerSetup;
    if (!setup || !setup.options.length) {
      return;
    }

    const key = setup.selectionKey ?? "character";
    const current = bot.setupSelectionsByGameId[game.id]?.[key];
    const ids = setup.options.map((option) => option.id);
    const random = createBotRandom(`${room.code}:${bot.id}:setup`);

    if (setup.kind === "choice") {
      const value = typeof current === "string" && ids.includes(current)
        ? current
        : ids[Math.floor(random() * ids.length)];
      bot.setupSelectionsByGameId[game.id] = { ...bot.setupSelectionsByGameId[game.id], [key]: value };
      bot.selectedCharacterId = value;
      return;
    }

    const picked = Array.isArray(current)
      ? current.filter((entry) => ids.includes(entry))
      : [];
    const minimum = Math.min(setup.minSelections, ids.length);
    const maximum = Math.min(setup.maxSelections, ids.length);

    while (picked.length < minimum) {
      const candidate = ids[Math.floor(random() * ids.length)];
      if (!picked.includes(candidate)) {
        picked.push(candidate);
      }
    }

    bot.setupSelectionsByGameId[game.id] = {
      ...bot.setupSelectionsByGameId[game.id],
      [key]: picked.slice(0, maximum)
    };
  }
}
