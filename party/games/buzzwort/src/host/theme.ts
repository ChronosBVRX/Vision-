import type { BuzzwortTeamId } from "../protocol.js";
import { tokenColor, tokens } from "./platformTheme.js";

export { bindPlatformTheme, tokenColor, tokens } from "./platformTheme.js";

/**
 * Semantic view of the platform theme for this game's host screen.
 *
 * Everything is a getter, because the platform mutates the token object in
 * place on a skin switch. Never destructure these into locals that outlive a
 * frame.
 *
 * `paint` yields Phaser colour numbers for draw calls, `ink` yields CSS strings
 * for text styles, `font` yields font stacks.
 */
export const paint = {
  get background(): number {
    return tokenColor((theme) => theme.color.background);
  },
  get backgroundDeep(): number {
    return tokenColor((theme) => theme.color.backgroundDeep);
  },
  get surface(): number {
    return tokenColor((theme) => theme.color.surface);
  },
  get surfaceMuted(): number {
    return tokenColor((theme) => theme.color.surfaceMuted);
  },
  get surfaceRaised(): number {
    return tokenColor((theme) => theme.color.surfaceRaised);
  },
  get line(): number {
    return tokenColor((theme) => theme.color.line);
  },
  get lineStrong(): number {
    return tokenColor((theme) => theme.color.lineStrong);
  },
  get accent(): number {
    return tokenColor((theme) => theme.color.accent);
  },
  get positive(): number {
    return tokenColor((theme) => theme.color.success);
  },
  get warning(): number {
    return tokenColor((theme) => theme.color.warning);
  },
  get danger(): number {
    return tokenColor((theme) => theme.color.danger);
  }
};

export const ink = {
  get text(): string {
    return tokens().color.text;
  },
  get soft(): string {
    return tokens().color.textSoft;
  },
  get muted(): string {
    return tokens().color.muted;
  },
  get accent(): string {
    return tokens().color.accent;
  },
  get positive(): string {
    return tokens().color.success;
  },
  get warning(): string {
    return tokens().color.warning;
  },
  get danger(): string {
    return tokens().color.danger;
  }
};

export const font = {
  get title(): string {
    return tokens().font.display;
  },
  get body(): string {
    return tokens().font.body;
  },
  get mono(): string {
    return tokens().font.mono;
  }
};

/**
 * Team identity colours.
 *
 * Accent and success are the two tokens that stay clearly distinguishable in
 * both skins (terracotta vs sage on paper, sky vs emerald on the dark stage),
 * so the teams borrow those rather than shipping their own hard-coded hues.
 */
export function teamPaint(teamId: BuzzwortTeamId): number {
  return teamId === "team1" ? paint.accent : paint.positive;
}

export function teamInk(teamId: BuzzwortTeamId): string {
  return teamId === "team1" ? ink.accent : ink.positive;
}
