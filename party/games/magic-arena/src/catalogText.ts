import type { LocalizedGameTextMap } from "@open-party-lab/game-core";

export const catalogText = {
  "de": {
    "displayName": "Hexagonia",
    "description": "Hexágonos, hechizos y una arena que no perdona errores. Planea en secreto, lanza magia y sobrevive al cierre.",
    "visual": {
      "eyebrow": "Hexagonia"
    }
  },
  "en": {
    "displayName": "Hexagonia",
    "description": "Hexes, spells and an arena that forgives no mistakes. Plan in secret, cast magic and survive the collapse.",
    "visual": {
      "eyebrow": "Hexagonia"
    },
    "lobbySetup": {
      "title": "Hexagonia setup",
      "description": "Choose how turns are played.",
      "fields": {
        "planningMode": {
          "label": "Turn planning",
          "options": {
            "planned": {
              "label": "Plan ahead",
              "description": "Plan all four turns in secret, then resolve them together."
            },
            "sequential": {
              "label": "One turn at a time",
              "description": "Players take turns, with each move resolving immediately."
            }
          }
        }
      }
    }
  }
} satisfies Partial<LocalizedGameTextMap>;
