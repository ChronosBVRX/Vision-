import type { BotAdapter } from "./types.js";
import { lightTrailsBot } from "./adapters/lightTrailsBot.js";
import { flatterfluffBot } from "./adapters/flatterfluffBot.js";
import { arenaSurvivorBot } from "./adapters/arenaSurvivorBot.js";
import { chaosKommandoBot } from "./adapters/chaosKommandoBot.js";

const adapters = new Map<string, BotAdapter>(
  [lightTrailsBot, flatterfluffBot, arenaSurvivorBot, chaosKommandoBot].map((adapter) => [
    adapter.gameId,
    adapter
  ])
);

/** Games where a server-driven seat can genuinely compete today. */
export const BOT_GAME_IDS: ReadonlySet<string> = new Set(adapters.keys());

export function getBotAdapter(gameId: string): BotAdapter | undefined {
  return adapters.get(gameId);
}
