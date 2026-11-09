import type { LocalizedGameTextMap } from "@open-party-lab/game-core";

export const catalogText = {
  "de": {
    "displayName": "Héroes del sótano",
    "description": "Cooperen para sobrevivir la mazmorra, pero compitan por la fama y el botín. El sótano no perdona.",
    "visual": {
      "eyebrow": "Sótano"
    }
  },
  "en": {
    "displayName": "Héroes del sótano",
    "description": "Cooperate to survive the dungeon, but compete for fame and loot. The basement shows no mercy.",
    "visual": {
      "eyebrow": "Basement"
    },
    "playerSetup": {
      "title": "Choose your class",
      "description": "Choose a hero and their special ability.",
      "options": {
        "warrior": {
          "name": "Warrior",
          "title": "Guard the line",
          "description": "Full combat power and 1 less damage if the party fails."
        },
        "mage": {
          "name": "Mage",
          "title": "Arcane burst",
          "description": "+4 power compared to Fight; rolling a 1 costs you 2 health."
        },
        "rogue": {
          "name": "Rogue",
          "title": "Sleight of hand",
          "description": "On success, steal up to 3 gold from the richest hero and gain 1 fame when you steal."
        },
        "cleric": {
          "name": "Cleric",
          "title": "Healing prayer",
          "description": "Heal the most wounded hero by 2 health before dungeon damage."
        },
        "bard": {
          "name": "Bard",
          "title": "Battle song",
          "description": "Give the party +2 additional combat power."
        },
        "tinkerer": {
          "name": "Tinkerer",
          "title": "Field contraption",
          "description": "Rolling 4–6 gives your contraption +3 additional combat power."
        }
      }
    }
  }
} satisfies Partial<LocalizedGameTextMap>;
