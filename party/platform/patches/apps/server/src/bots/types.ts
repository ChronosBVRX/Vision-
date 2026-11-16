import type { BotDifficulty } from "@open-party-lab/protocol";
import type { GameManifest } from "@open-party-lab/game-core";
import type { PlayerRecord, RoomRecord } from "../rooms/roomStore.js";

/**
 * One decision step for a server-driven seat.
 *
 * `state` is the game's authoritative runtime state. Bots are part of the
 * server, so they read the same state the game engine uses; adapters must only
 * read what a phone in that seat could reasonably know (no hidden hands from
 * other players).
 */
export interface BotThinkContext {
  room: RoomRecord;
  bot: PlayerRecord;
  state: unknown;
  manifest: GameManifest;
  now: number;
  difficulty: BotDifficulty;
  memory: Map<string, unknown>;
  /** Feeds one input through the same `ServerGame.handleInput` as a phone. */
  input(input: Record<string, unknown> & { type: string }): void;
}

export interface BotAdapter {
  gameId: string;
  /** How often the bot may think. Between steps it keeps its previous input. */
  thinkEveryMs: number;
  think(context: BotThinkContext): void;
}
