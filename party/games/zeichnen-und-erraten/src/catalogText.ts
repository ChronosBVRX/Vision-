import type { LocalizedGameTextMap } from "@open-party-lab/game-core";

export const zeichnenUndErratenText = {
  "de": {
    "displayName": "Garabato mental",
    "description": "Dibujos feos, mentes retorcidas y un celular de por medio. Traza, adivina y ríete del desastre.",
    "visual": {
      "eyebrow": "Garabato"
    }
  },
  "en": {
    "displayName": "Garabato mental",
    "description": "Ugly drawings, twisted minds and a phone in the middle. Sketch, guess and laugh at the disaster.",
    "lobbySetup": {
      "title": "Prepare your doodle",
      "description": "Choose which words may appear in this round.",
      "fields": {
        "category": {
          "label": "Category",
          "options": {
            "standard": {
              "label": "Family",
              "description": "Family-friendly drawing words."
            },
            "adult": {
              "label": "18+",
              "description": "Adult party words."
            },
            "all": {
              "label": "All",
              "description": "Family and adult words mixed together."
            }
          }
        },
        "roundSeconds": {
          "label": "Round time in seconds (0 = unlimited)",
          "description": "Choose how long players have to guess."
        }
      }
    },
    "visual": {
      "eyebrow": "Doodle"
    }
  }
} as const satisfies Partial<LocalizedGameTextMap>;
