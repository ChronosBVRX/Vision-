import type { TransportMode } from "../config.js";
import type { CityMapVariant } from "../protocol.js";

/**
 * Board-only colours. They deliberately do not follow the live shell theme:
 * the board variant is part of the round state and must stay stable after a
 * room skin change or a browser reload.
 */
export interface CityBoardStyle {
  textureKey: string;
  mapAlpha: number;
  shade: number;
  casing: number;
  stationOuter: number;
  stationOutline: number;
  labelStroke: string;
  mutedModeColors: Record<TransportMode, number>;
  modeColors: Record<TransportMode, number>;
  modeGlow: Record<TransportMode, number>;
}

export const cityBoardStyles: Record<CityMapVariant, CityBoardStyle> = {
  night: {
    textureKey: "schattenjagd-city-night",
    mapAlpha: 0.9,
    shade: 0.14,
    casing: 0x020617,
    stationOuter: 0x020617,
    stationOutline: 0xf8fafc,
    labelStroke: "#020617",
    mutedModeColors: { taxi: 0xd9aa3e, bus: 0x37be73, metro: 0x936cd8 },
    modeColors: {
      taxi: 0xf4c95d,
      bus: 0x4ade80,
      metro: 0xa78bfa
    },
    modeGlow: {
      taxi: 0x7a5f1d,
      bus: 0x1d5c38,
      metro: 0x4c3a86
    }
  },
  day: {
    textureKey: "schattenjagd-city-day",
    mapAlpha: 0.96,
    shade: 0.04,
    casing: 0xfffbf2,
    stationOuter: 0xfffbf2,
    stationOutline: 0x26354a,
    labelStroke: "#26354a",
    mutedModeColors: { taxi: 0xb48612, bus: 0x158c57, metro: 0x8257cf },
    modeColors: {
      taxi: 0xb66c00,
      bus: 0x087443,
      metro: 0x6742a5
    },
    modeGlow: {
      taxi: 0xf1c66b,
      bus: 0x9ad5b6,
      metro: 0xc5b4e8
    }
  }
};
