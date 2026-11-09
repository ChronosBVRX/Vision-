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
  },
  "en": {
    "displayName": "Mímica en llamas",
    "description": "Pure theater, zero words and red-hot embarrassment. Act, guess and survive the blaze.",
    "lobbySetup": {
      "title": "Set up Mímica en llamas",
      "fields": {
        "mode": {
          "label": "Game mode",
          "options": {
            "solo": {
              "label": "Free-for-all",
              "description": "Everyone except the actor guesses and claims their own correct answers."
            },
            "team": {
              "label": "Teams",
              "description": "Two teams alternate by join order. Only the active team guesses. Requires at least 4 players."
            }
          }
        },
        "category": {
          "label": "Words",
          "options": {
            "standard": {
              "label": "Family",
              "description": "124 family-friendly prompts."
            },
            "adult": {
              "label": "18+",
              "description": "76 adult prompts about sex, relationships and nightlife."
            },
            "all": {
              "label": "All",
              "description": "Family and adult prompts mixed together. For adult groups."
            }
          }
        },
        "duration": {
          "label": "Seconds per actor"
        },
        "skips": {
          "label": "Skips per actor turn"
        }
      }
    },
    "visual": {
      "eyebrow": "Theater on fire"
    }
  }
} as const satisfies Partial<LocalizedGameTextMap>;
