import type { ArenaSurvivorRuntimeState } from "../arenaSurvivorState.js";

export function applyProjectileSystem(
  state: ArenaSurvivorRuntimeState,
  deltaMs: number
): ArenaSurvivorRuntimeState {
  if (state.phase !== "playing") {
    return state;
  }

  const deltaSeconds = Math.max(0, deltaMs / 1000);

  return {
    ...state,
    projectiles: state.projectiles
      .map((projectile) => {
        if (!projectile.alive) {
          return projectile;
        }

        const speed = Math.hypot(projectile.vx, projectile.vy);
        const travelSeconds = speed > 0 ? Math.min(deltaSeconds,
          Math.max(0, projectile.maxRange - projectile.travelledDistance) / speed,
          Math.max(0, projectile.lifetimeMs - projectile.ageMs) / 1000) : deltaSeconds;
        const nextX = projectile.x + projectile.vx * travelSeconds;
        const nextY = projectile.y + projectile.vy * travelSeconds;
        const nextTravelledDistance =
          projectile.travelledDistance + speed * travelSeconds;
        const nextAgeMs = projectile.ageMs + deltaMs;
        const stillAlive =
          nextAgeMs < projectile.lifetimeMs &&
          nextTravelledDistance < projectile.maxRange &&
          nextX >= -projectile.radius &&
          nextY >= -projectile.radius &&
          nextX <= state.arenaWidth + projectile.radius &&
          nextY <= state.arenaHeight + projectile.radius;

        return {
          ...projectile,
          previousX: projectile.x,
          previousY: projectile.y,
          x: nextX,
          y: nextY,
          ageMs: nextAgeMs,
          travelledDistance: nextTravelledDistance,
          // Keep the last travelled segment for collision detection this tick.
          // The damage system retires it immediately after resolving contacts.
          expiresAfterStep: !stillAlive,
          alive: true
        };
      })
      .filter((projectile) => projectile.alive)
  };
}
