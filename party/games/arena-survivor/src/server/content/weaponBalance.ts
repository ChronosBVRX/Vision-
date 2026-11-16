import type { ArenaSurvivorWeaponDefinition } from "./types.js";

// Explicit tier budgets: burst, reach, multi-shot and pierce are paid for in
// single-target DPS. Shops and combat both consume these same definitions.
const profiles: Record<string, { damage: readonly number[]; cooldown: readonly number[] }> = {
  "rust-blade": { damage: [18, 25, 34, 46], cooldown: [650, 620, 585, 545] },
  "twin-daggers": { damage: [7, 10, 14, 20], cooldown: [404, 380, 355, 330] },
  "war-hammer": { damage: [34, 48, 64, 84], cooldown: [1100, 1050, 1000, 950] },
  spear: { damage: [15, 23, 33, 46], cooldown: [680, 650, 620, 590] },
  lance: { damage: [22, 30, 41, 56], cooldown: [800, 760, 720, 680] },
  stone: { damage: [20, 29, 40, 56], cooldown: [800, 760, 720, 680] },
  stick: { damage: [9, 14, 21, 30], cooldown: [520, 500, 480, 460] },
  pitchfork: { damage: [16, 22, 30, 41], cooldown: [700, 670, 640, 610] },
  halberd: { damage: [25, 35, 48, 65], cooldown: [900, 860, 820, 780] },
  mace: { damage: [30, 42, 57, 77], cooldown: [1000, 960, 920, 880] },
  cleaver: { damage: [8, 12, 17, 24], cooldown: [460, 440, 420, 400] },
  "survivor-pistol": { damage: [12, 17, 24, 34], cooldown: [550, 520, 490, 460] },
  "hunter-bow": { damage: [28, 39, 53, 72], cooldown: [1050, 1000, 950, 900] },
  "scrap-smg": { damage: [3, 4, 6, 8], cooldown: [160, 155, 150, 145] },
  "ember-wand": { damage: [7, 10, 14, 20], cooldown: [400, 380, 360, 340] },
  "frost-orb": { damage: [18, 25, 35, 48], cooldown: [850, 820, 790, 760] },
  "spark-rod": { damage: [7, 10, 14, 13], cooldown: [300, 285, 270, 255] },
  "coil-rifle": { damage: [21, 29, 40, 54], cooldown: [750, 720, 690, 660] },
  "gear-launcher": { damage: [7, 10, 10, 14], cooldown: [650, 620, 590, 560] },
  "venom-siphon": { damage: [8, 11, 15, 21], cooldown: [420, 400, 380, 360] },
  "prism-scepter": { damage: [24, 33, 45, 61], cooldown: [900, 860, 820, 780] }
};

export function applyWeaponBalance(weapon: ArenaSurvivorWeaponDefinition): ArenaSurvivorWeaponDefinition {
  const profile = profiles[weapon.id];
  if (!profile) throw new Error(`Missing Arena Survivor weapon balance: ${weapon.id}`);
  return { ...weapon, levels: weapon.levels.map((level, index) => ({
    ...level, damage: profile.damage[index], cooldownMs: profile.cooldown[index],
    critScale: Math.max(1.5, level.critScale ?? 1.5),
    // Basic pistol: reliable single-target damage. Penetration belongs to
    // spears, heavy rifles and later upgrades rather than every starter gun.
    pierce: weapon.id === "survivor-pistol" ? (index === 3 ? 1 : 0) : level.pierce
  })) };
}
