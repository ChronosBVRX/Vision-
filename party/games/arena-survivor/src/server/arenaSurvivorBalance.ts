/** Shared combat tuning for Wave and Survival. HP uses this game's 100-HP scale. */
export const arenaSurvivorBalance = {
  armorDivisor: 15,
  maxDodgePct: 60,
  lifeStealIntervalMs: 100,
  minRangedCooldownMs: 1000 / 12,
  minMeleeCooldownMs: 180,
  enemyHpPerWave: 0.12,
  enemyDamagePerWave: 0.045,
  enemySpeedPerWave: 0.008,
  maxEnemySpeedGrowth: 0.18,
  survivalEnemyLimit: 160
} as const;

export function resolveArmorDamage(rawDamage: number, armor: number): number {
  const positive = Math.max(0, armor);
  const negative = Math.max(0, -armor);
  return Math.max(1, rawDamage / (1 + positive / arenaSurvivorBalance.armorDivisor) *
    (1 + negative / arenaSurvivorBalance.armorDivisor));
}

/** Negative attack-speed penalties approach zero instead of becoming negative. */
export function resolveAttackRate(bonus: number): number {
  return bonus >= 0 ? 1 + bonus : 1 / (1 - bonus);
}

export function resolveWeaponCooldown(baseMs: number, rate: number, melee: boolean): number {
  return Math.max(melee ? arenaSurvivorBalance.minMeleeCooldownMs : arenaSurvivorBalance.minRangedCooldownMs,
    baseMs / Math.max(0.05, rate));
}

/** Weapon crit values are final multipliers; character/item crit adds to them. */
export function resolveCriticalMultiplier(weaponMultiplier: number | undefined, playerMultiplier: number): number {
  return Math.max(1, (weaponMultiplier ?? 1.5) + playerMultiplier - 1.5);
}
