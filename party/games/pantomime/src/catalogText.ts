import type { LocalizedGameTextMap } from "@open-party-lab/game-core";

export const pantomimeText = {
  "de": {
    "displayName": "Mímica en llamas",
    "description": "Puro teatro, cero palabras y vergüenza al rojo vivo. Actúa, adivina y sobrevive al incendio.",
    "lobbySetup": {
      "title": "Prende la mímica",
      "fields": {
        "mode": {
          "label": "Modo de fuego"
        },
        "category": {
          "label": "Términos en llamas"
        },
        "duration": {
          "label": "Segundos por actor"
        },
        "skips": {
          "label": "Saltos por actor"
        }
      }
    },
    "visual": {
      "eyebrow": "Teatro en llamas"
    }
  }
,
  "en": {
    "displayName": "Mímica en llamas",
    "description": "Puro teatro, cero palabras y vergüenza al rojo vivo. Actúa, adivina y sobrevive al incendio.",
    "lobbySetup": {
      "title": "Prende la mímica",
      "fields": {
        "mode": {
          "label": "Modo de fuego"
        },
        "category": {
          "label": "Términos en llamas"
        },
        "duration": {
          "label": "Segundos por actor"
        },
        "skips": {
          "label": "Saltos por actor"
        }
      }
    },
    "visual": {
      "eyebrow": "Teatro en llamas"
    }
  }} as const satisfies Partial<LocalizedGameTextMap>;
