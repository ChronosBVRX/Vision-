import type { MageDuelGestureColor, MageDuelGesturePoint, MageDuelSpellId } from "../protocol.js";

export function createMageDuelGestureInput(
  playerId: string,
  spellId: MageDuelSpellId,
  color: MageDuelGestureColor,
  points: MageDuelGesturePoint[]
) {
  return {
    type: "spell:gesture" as const,
    playerId,
    spellId,
    color,
    points: points.map((point) => ({
      x: point.x,
      y: point.y,
      t: point.t
    })),
    sentAt: Date.now()
  };
}
