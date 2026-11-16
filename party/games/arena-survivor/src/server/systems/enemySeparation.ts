import type { ArenaSurvivorRuntimeEnemyState } from "../arenaSurvivorState.js";

/** A small local steering force keeps hordes readable without hard pushing. */
export function createEnemySeparation(enemies: ArenaSurvivorRuntimeEnemyState[]) {
  const cellSize = Math.max(32, ...enemies.map(enemy => enemy.radius * 2));
  const cells = new Map<string, ArenaSurvivorRuntimeEnemyState[]>();
  for (const enemy of enemies) {
    if (!enemy.alive) continue;
    const key = `${Math.floor(enemy.x / cellSize)}:${Math.floor(enemy.y / cellSize)}`;
    const bucket = cells.get(key) ?? [];
    bucket.push(enemy); cells.set(key, bucket);
  }
  return (enemy: ArenaSurvivorRuntimeEnemyState) => {
    let x = 0, y = 0;
    const cx = Math.floor(enemy.x / cellSize), cy = Math.floor(enemy.y / cellSize);
    for (let ix = cx - 1; ix <= cx + 1; ix++) for (let iy = cy - 1; iy <= cy + 1; iy++) {
      for (const peer of cells.get(`${ix}:${iy}`) ?? []) {
        if (peer.id === enemy.id) continue;
        const dx = enemy.x - peer.x, dy = enemy.y - peer.y;
        const distance = Math.hypot(dx, dy);
        const spacing = (enemy.radius + peer.radius) * 0.85;
        if (distance >= spacing) continue;
        const strength = (1 - distance / spacing) * 0.65;
        if (distance < 0.001) x += enemy.id < peer.id ? -strength : strength;
        else { x += dx / distance * strength; y += dy / distance * strength; }
      }
    }
    const magnitude = Math.max(1, Math.hypot(x, y) / 0.65);
    return { x: x / magnitude, y: y / magnitude };
  };
}
