import type { LocalizedGameTextMap } from "@open-party-lab/game-core";

export const imposterText = {
  "de": {
    "displayName": "El Infiltrado",
    "description": "Una palabra secreta, un topo y demasiadas caras de sospecha. Encuentra al Infiltrado antes de que te sabotea.",
    "visual": {
      "eyebrow": "Infiltración"
    }
  },
  "en": {
    "displayName": "El Infiltrado",
    "description": "One secret word, one mole and too many suspicious faces. Find the Infiltrator before they sabotage you.",
    "lobbySetup": {
      "title": "Set up El Infiltrado",
      "fields": {
        "category": {
          "label": "Words",
          "options": {
            "standard": {
              "label": "Family",
              "description": "Family-friendly words."
            },
            "adult": {
              "label": "18+",
              "description": "Sex, erotic themes, strong language, relationships and nightlife."
            },
            "all": {
              "label": "All",
              "description": "Family and adult words mixed together."
            }
          }
        },
        "hint": {
          "label": "Give a hint",
          "description": "The Infiltrator receives a related clue, for example condom → sex."
        },
        "confirmClues": {
          "label": "Confirm each clue",
          "description": "Off: discuss freely and vote directly. On: two clue rounds, with confirmation after each clue."
        }
      }
    },
    "visual": {
      "eyebrow": "Infiltration"
    }
  }
} as const satisfies Partial<LocalizedGameTextMap>;
