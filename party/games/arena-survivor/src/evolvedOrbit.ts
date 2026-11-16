/** Shared world-space orbit for rendering and authoritative contact damage. */
export function evolvedOrbit(x: number, y: number, elapsedMs: number, slot: number, speed = 1) {
  const angle = elapsedMs / 1000 * 2.6 * speed + slot * Math.PI / 3;
  return { x: x + Math.cos(angle) * 100, y: y + Math.sin(angle) * 100, aimAngle: angle };
}
