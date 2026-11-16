import type { ArenaSurvivorDamageEvent } from "../../protocol.js";
import type { ArenaSurvivorRuntimeState } from "../arenaSurvivorState.js";

export function collectDamageEvents(state: ArenaSurvivorRuntimeState): ArenaSurvivorDamageEvent[] {
  return (state.damageEvents ?? []).filter(event => event.atMs <= state.elapsedMs && state.elapsedMs - event.atMs < 800);
}

export function recordDamageEvent(
  events: ArenaSurvivorDamageEvent[], atMs: number,
  enemy: { id: string; x: number; y: number; radius: number }, damage: number
): void {
  if (damage <= 0) return;
  const serial = Number(events.at(-1)?.id.split(":").at(-1) ?? -1) + 1;
  events.push({ id: `${atMs}:${enemy.id}:${serial}`, atMs, x: enemy.x, y: enemy.y - enemy.radius, damage });
  // Bound snapshot cost during large area attacks; preserve the newest hits.
  if (events.length > 256) events.splice(0, events.length - 256);
}
