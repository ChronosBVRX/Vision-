export interface ArenaSurvivorVisualConfig {
  cameraPadding: {
    min: number;
    ratio: number;
    minZoom: number;
    maxZoom: number;
  };
  player: {
    // 1 means: visible sprite diameter matches the gameplay hitbox diameter exactly.
    // Values above 1 make the character visually larger than the real hitbox.
    diameterScale: number;
    bobAmplitude: number;
    bobSpeedMs: number;
    pulseAmplitude: number;
    pulseSpeedMs: number;
  };
  enemy: {
    // 1 means: visible sprite diameter matches the gameplay hitbox diameter exactly.
    // Values above 1 make the enemy visually larger than the real hitbox.
    diameterScale: number;
    pulseAmplitude: number;
    pulseSpeedMs: number;
    pulseSpeedVarianceMs: number;
  };
  weaponSlots: {
    spriteDisplaySizeMultiplier: number;
    minSpriteDisplaySize: number;
    rangedSpriteScale: number;
    fallbackDotRadiusMultiplier: number;
    minFallbackDotRadius: number;
    meleeSpriteRangeRatio: number;
    meleeMinSpriteDisplaySize: number;
    meleeMaxSpriteDisplaySize: number;
    meleeSpriteTipRatio: number;
  };
}

// Central host-only tuning for how Arena Survivor looks on screen.
// Player/enemy hitboxes are still defined on the server. Keep `diameterScale`
// at 1 when sprite size should visually match the real collision size.
export const arenaSurvivorVisualConfig: ArenaSurvivorVisualConfig = {
  cameraPadding: {
    min: 150,
    ratio: 0.18,
    minZoom: 0.6,
    maxZoom: 1.55
  },
  player: {
    diameterScale: 1.25,
    bobAmplitude: 1,
    bobSpeedMs: 200,
    pulseAmplitude: 0,
    pulseSpeedMs: 200
  },
  enemy: {
    diameterScale: 1.15,
    pulseAmplitude: 0,
    pulseSpeedMs: 200,
    pulseSpeedVarianceMs: 60
  },
  weaponSlots: {
    spriteDisplaySizeMultiplier: 1.75 * 0.75,
    minSpriteDisplaySize: 20 * 0.75,
    rangedSpriteScale: 1.25,
    fallbackDotRadiusMultiplier: 0.16,
    minFallbackDotRadius: 2.8,
    meleeSpriteRangeRatio: 0.42 * 0.75,
    meleeMinSpriteDisplaySize: 32 * 0.75,
    meleeMaxSpriteDisplaySize: 84 * 0.75,
    meleeSpriteTipRatio: 0.42
  }
};
