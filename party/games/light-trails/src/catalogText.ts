import type { LocalizedGameTextMap } from "@open-party-lab/game-core";

export const lightTrailsText = {
  "de": {
    "displayName": "Light Trails",
    "description": "Lenke deine Spur durch die Arena und ueberlebe am laengsten.",
    "visual": {
      "eyebrow": "Arcade"
    }
  },
  "en": {
    "displayName": "Light Trails",
    "description": "Steer your trail through the arena and be the last player standing.",
    "visual": {
      "eyebrow": "Arcade"
    }
  }
} as const satisfies LocalizedGameTextMap;
