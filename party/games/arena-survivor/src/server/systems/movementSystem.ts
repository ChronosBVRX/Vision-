import type { ArenaSurvivorRuntimeState } from "../arenaSurvivorState.js";
import { clamp, clampVectorMagnitude } from "../arenaSurvivorState.js";
import { moveThroughFrostfire } from "../../survivalWorld.js";

export function applyMovementSystem(
  state: ArenaSurvivorRuntimeState,
  deltaMs: number
): ArenaSurvivorRuntimeState {
  if (state.phase !== "playing") {
    return state;
  }

  if (deltaMs <= 0) return state;
  const deltaSeconds = deltaMs / 1000;

  const positions = state.players.map(p => ({ ...p }));
  return {
    ...state,
    players: state.players.map((player) => {
      if (!player.alive) {
        return player;
      }

      const moveVector = clampVectorMagnitude(player.moveInputX, player.moveInputY);
      const nextVx = moveVector.x * player.moveSpeed;
      const nextVy = moveVector.y * player.moveSpeed;
      let nextX = clamp(
        player.x + nextVx * deltaSeconds,
        player.radius,
        state.arenaWidth - player.radius
      );
      let nextY = clamp(
        player.y + nextVy * deltaSeconds,
        player.radius,
        state.arenaHeight - player.radius
      );

      if (state.survival) {
        // Project the intended position onto each teammate's allowed disk.
        // A tiny safety margin keeps tangent motion possible without teleporting.
        for (let pass = 0; pass < 6; pass++) for (const peer of positions) {
          if (!peer.alive || peer.playerId === player.playerId) continue;
          const oldDistance = Math.hypot(player.x - peer.x, player.y - peer.y);
          const limit = Math.max(state.survival.maxGroupDistance, oldDistance);
          const dx = nextX - peer.x, dy = nextY - peer.y, distance = Math.hypot(dx, dy);
          if (distance > limit) { nextX = peer.x + dx / distance * limit; nextY = peer.y + dy / distance * limit; }
        }
        const point = moveThroughFrostfire(player.x, player.y, nextX, nextY, player.radius);
        nextX = point.x; nextY = point.y;
        // Sliding must also respect the group boundary; never push a player through terrain.
        if (positions.some(peer => peer.alive && peer.playerId !== player.playerId && Math.hypot(nextX - peer.x, nextY - peer.y) > Math.max(state.survival!.maxGroupDistance, Math.hypot(player.x - peer.x, player.y - peer.y)) + 0.001)) {
          nextX = player.x; nextY = player.y;
        }
        // Every player uses the same start-of-tick positions. Updating this
        // array in player order made the tether asymmetric and caused jitter.
      }
      return {
        ...player,
        x: nextX,
        y: nextY,
        vx: (nextX - player.x) / deltaSeconds,
        vy: (nextY - player.y) / deltaSeconds,
        facingAngleRad: moveVector.magnitude > 0.001
          ? Math.atan2(moveVector.y, moveVector.x) : player.facingAngleRad
      };
    })
  };
}
