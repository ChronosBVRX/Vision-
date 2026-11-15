import type { LocalizedGameTextMap } from "@open-party-lab/game-core";

export const imposterText = {
  "de": {
    "displayName": "El Infiltrado",
    "description": "Una palabra secreta, un topo y demasiadas caras de sospecha. Encuentra al Infiltrado antes de que te sabotea.",
    "visual": {
      "eyebrow": "Infiltración"
    }
  }
,
  "en": {
    "displayName": "El Infiltrado",
    "description": "Una palabra secreta, un topo y demasiadas caras de sospecha. Encuentra al Infiltrado antes de que te sabotea.",
    "visual": {
      "eyebrow": "Infiltración"
    }
  }} as const satisfies Partial<LocalizedGameTextMap>;
