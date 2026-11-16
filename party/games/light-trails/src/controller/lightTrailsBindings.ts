import type { LightTrailsTurnDirection } from "../protocol.js";

export function createLightTrailsInput(
  playerId: string,
  direction: LightTrailsTurnDirection,
  active: boolean
) {
  return {
    type: "turn" as const,
    playerId,
    direction,
    active,
    sentAt: Date.now()
  };
}
