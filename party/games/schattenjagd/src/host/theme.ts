import type { TransportMode } from "../config.js";
import { tokenColor, tokens } from "./platformTheme.js";

/**
 * Schattenjagd's host palette.
 *
 * Structure — background, panels, text — follows the platform theme so the
 * board matches the shell and the phones. The transport-line colours below are
 * game content: taxi, bus and metro have to stay distinguishable from each
 * other, so they keep their own hues in both skins.
 */
export const hostTheme = {
  get titleFont() {
    return tokens().font.display;
  },
  get bodyFont() {
    return tokens().font.body;
  },
  get background() {
    return tokenColor((theme) => theme.color.background);
  },
  get backgroundGlow() {
    return tokenColor((theme) => theme.color.backgroundDeep);
  },
  get panel() {
    return tokenColor((theme) => theme.color.surface);
  },
  get panelStroke() {
    return tokenColor((theme) => theme.color.line);
  },
  get text() {
    return tokens().color.text;
  },
  get muted() {
    return tokens().color.muted;
  },
  get accent() {
    return tokens().color.warning;
  },
  get shadow() {
    return tokens().color.danger;
  },
  get positive() {
    return tokens().color.success;
  },
  get danger() {
    return tokens().color.danger;
  }
};

/** Transport lines stay recognisable across themes — this is game content. */
export const modeColors: Record<TransportMode, number> = {
  taxi: 0xf4c95d,
  bus: 0x4ade80,
  metro: 0xa78bfa
};

export const modeGlow: Record<TransportMode, number> = {
  taxi: 0x7a5f1d,
  bus: 0x1d5c38,
  metro: 0x4c3a86
};

export const modeShortLabel: Record<TransportMode, string> = {
  taxi: "T",
  bus: "B",
  metro: "M"
};
