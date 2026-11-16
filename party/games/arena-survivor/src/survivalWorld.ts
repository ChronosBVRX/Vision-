/** Authored world coordinates shared by rendering, navigation and server collision. */
export const frostfireWorld = {
  width: 4800,
  height: 3600,
  terrainKey: "survival-ground",
  assetRoot: "/arena-survivor/themes/frostfire-saga/survival",
  // Safe fallback points only. Normal objectives spawn freely across the field.
  sites: [
    { x: 950, y: 460 }, { x: 2350, y: 760 }, { x: 3690, y: 410 },
    { x: 700, y: 1680 }, { x: 2420, y: 1740 }, { x: 4240, y: 1680 },
    { x: 1110, y: 2830 }, { x: 2430, y: 2920 }, { x: 3880, y: 2860 },
    { x: 610, y: 2240 }, { x: 2520, y: 2340 }, { x: 4060, y: 2280 },
    { x: 1420, y: 1760 }, { x: 3100, y: 1760 }, { x: 3190, y: 600 }
  ]
} as const;

export const frostfireObstacles = [
  { x: 480, y: 650, radius: 66 }, { x: 1490, y: 430, radius: 82 },
  { x: 1780, y: 1170, radius: 94 }, { x: 1110, y: 1100, radius: 65 },
  { x: 450, y: 1230, radius: 84 }, { x: 1630, y: 2180, radius: 82 },
  { x: 470, y: 2920, radius: 90 }, { x: 1670, y: 3090, radius: 70 },
  { x: 1950, y: 2600, radius: 65 }, { x: 3120, y: 1110, radius: 100 },
  { x: 3710, y: 1180, radius: 85 }, { x: 2900, y: 2310, radius: 70 },
  { x: 3390, y: 2470, radius: 100 }, { x: 4400, y: 2630, radius: 80 },
  { x: 3090, y: 3210, radius: 90 }, { x: 4180, y: 520, radius: 78 },
  { x: 2250, y: 350, radius: 70 }, { x: 740, y: 3360, radius: 88 }
] as const;

export function isFrostfireWalkable(x: number, y: number, radius = 20): boolean {
  return x >= radius && y >= radius && x <= frostfireWorld.width - radius && y <= frostfireWorld.height - radius &&
    frostfireObstacles.every(o => Math.hypot(x - o.x, y - o.y) >= radius + o.radius);
}

/** Slide along obstacle surfaces; substeps prevent tunnelling on slower ticks. */
export function moveThroughFrostfire(x: number, y: number, nextX: number, nextY: number, radius: number) {
  const steps = Math.max(1, Math.ceil(Math.hypot(nextX - x, nextY - y) / 12));
  const dx = (nextX - x) / steps, dy = (nextY - y) / steps;
  for (let step = 0; step < steps; step++) {
    if (isFrostfireWalkable(x + dx, y + dy, radius)) { x += dx; y += dy; }
    else {
      if (isFrostfireWalkable(x + dx, y, radius)) x += dx;
      if (isFrostfireWalkable(x, y + dy, radius)) y += dy;
    }
  }
  return { x, y };
}

/** Local avoidance for separated rock islands while preserving the combat AI. */
export function steerAroundFrostfire(x: number, y: number, dx: number, dy: number, radius: number) {
  for (const o of frostfireObstacles) {
    const ox = o.x - x, oy = o.y - y, distance = Math.hypot(ox, oy);
    if (ox * dx + oy * dy <= 0 || distance > o.radius + radius + 100) continue;
    const cross = dx * oy - dy * ox;
    if (Math.abs(cross) > o.radius + radius + 15) continue;
    const side = cross >= 0 ? -1 : 1;
    const nx = ox / Math.max(1, distance), ny = oy / Math.max(1, distance);
    return { x: -ny * side - nx * 0.2, y: nx * side - ny * 0.2 };
  }
  return { x: dx, y: dy };
}
