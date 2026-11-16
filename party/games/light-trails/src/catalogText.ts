import type { LocalizedGameTextMap } from "@open-party-lab/game-core";

export const lightTrailsText = {
  "es": {
    "displayName": "Estelas de Luz",
    "description": "Gira tu estela por la arena y sé el último en pie.",
    "visual": {
      "eyebrow": "Arcade"
    }
  },
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
