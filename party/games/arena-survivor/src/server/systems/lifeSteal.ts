import { arenaSurvivorBalance } from "../arenaSurvivorBalance.js";
import { createSeededRandom, type ArenaSurvivorRuntimePlayerState } from "../arenaSurvivorState.js";

/** One HP per successful on-hit roll, shared across all six weapons. */
export function applyLifeSteal(player: ArenaSurvivorRuntimePlayerState, elapsedMs: number, seed: number) {
  if (player.hp >= player.maxHp || player.stats.lifeStealPct <= 0 ||
      elapsedMs < (player.nextLifeStealAtMs ?? 0)) return { player, seed };
  const roll = createSeededRandom(seed);
  if (roll.value >= Math.min(100, player.stats.lifeStealPct) / 100) return { player, seed: roll.seed };
  return {
    player: { ...player, hp: Math.min(player.maxHp, player.hp + 1),
      nextLifeStealAtMs: elapsedMs + arenaSurvivorBalance.lifeStealIntervalMs },
    seed: roll.seed
  };
}
