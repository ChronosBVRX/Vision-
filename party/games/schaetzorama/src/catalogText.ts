import type { LocalizedGameTextMap } from "@open-party-lab/game-core";

export const schaetzoramaText = {
  "de": {
    "displayName": "Cifras salvajes",
    "description": "Números al aire, estimaciones dudosas y cero miedo al ridículo. Estima, ordena y apuesta.",
    "visual": {
      "eyebrow": "Cifras salvajes"
    }
  },
  "en": {
    "displayName": "Cifras salvajes",
    "description": "Numbers in the air, dubious estimates and no fear of looking silly. Estimate, sort and bet.",
    "visual": {
      "eyebrow": "Wild numbers"
    }
  }
} as const satisfies Partial<LocalizedGameTextMap>;
