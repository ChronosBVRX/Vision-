import { arenaSurvivorCharacterDefinitions } from "./server/content/characterDefinitions.js";
import { localizeArenaSurvivorCharacter } from "./contentText.js";
import type { LocalizedGameTextMap } from "@open-party-lab/game-core";


const archetypes = {
  de: { ranged: "Fernkampf", melee: "Nahkampf", magic: "Magie", lifesteal: "Lebensraub", regen: "Regeneration", tank: "Panzer", speed: "Tempo", hybrid: "Allrounder", luck: "Glück", economy: "Wirtschaft" },
  en: { ranged: "Ranged", melee: "Melee", magic: "Magic", lifesteal: "Life steal", regen: "Regeneration", tank: "Tank", speed: "Speed", hybrid: "All-rounder", luck: "Luck", economy: "Economy" }
};
function characterSetupText(language: "de" | "en") {
  return Object.fromEntries(arenaSurvivorCharacterDefinitions.map(character => {
    const text = localizeArenaSurvivorCharacter(character, language);
    return [character.id, { name: text.name, title: text.title, description: text.description, archetype: archetypes[language][character.archetype] }];
  }));
}

export const arenaSurvivorText = {
  "de": {
    "displayName": "Arena Survivor",
    "description": "Kaempft gemeinsam im Wave-Modus oder erkundet Frostfire im Survival-Modus mit Level-Ups, Loot und Waffen-Evolutionen.",
    "lobbySetup": {
      "title": "Run Setup",
      "description": "Lege die Schwierigkeit fest und gib den Run frei.",
      "fields": {
        "mode": {
          "label": "Modus / Mode",
          "description": "Wave: Wellen und Shops. Survival: 20 Minuten in der grossen Frostfire-Welt.",
          "options": {
            "wave": {
              "label": "Wave"
            },
            "survival": {
              "label": "Survival · Frostfire Saga"
            }
          }
        },
        "visualTheme": {
          "label": "Art Design",
          "description": "Waehle das komplette visuelle Set fuer Map, Figuren, Gegner und Ausruestung.",
          "options": {
            "marshmallow-mayhem": {
              "label": "Marshmallow Mayhem",
              "description": "Geröstete Aquarellwelt mit lebendigen Wide-Rigs, Blickanimation und süßen Monstern."
            },
            "frostfire-saga": {
              "label": "Frostfire Saga",
              "description": "Handgemalte nordische Storybook-Welt mit Eis, Glut, Runen und geschnitztem Holz."
            },
            "obsidian-relay": {
              "label": "Obsidian Relay",
              "description": "Detaillierte Sci-Fi-Ruinen, Plasma, Kupfer und schwarzes Metall."
            },
            "classic": {
              "label": "Classic Arena",
              "description": "Das bisherige warme, organische Arena-Design."
            }
          }
        },
        "difficulty": {
          "label": "Schwierigkeit",
          "description": "Gefahr 1 bis 5."
        }
      },
      "confirmation": {
        "label": "Run freigeben",
        "description": "Startet automatisch, sobald alle Spieler bereit sind."
      }
    },
    "visual": {
      "eyebrow": "Arena"
    },
    "playerSetup": {
      "title": "Charakterwahl",
      options: characterSetupText("de"),
      "description": "Wähle deinen Charakter für den Run."
    }
  },
  "en": {
    "displayName": "Arena Survivor",
    "description": "Fight together in Wave mode or explore Frostfire in Survival mode with shared level-ups, loot and weapon evolutions.",
    "lobbySetup": {
      "title": "Run setup",
      "description": "Choose the difficulty and unlock the run.",
      "fields": {
        "mode": {
          "label": "Mode",
          "description": "Wave: waves and shops. Survival: 20 minutes in the expansive Frostfire world.",
          "options": {
            "wave": {
              "label": "Wave"
            },
            "survival": {
              "label": "Survival · Frostfire Saga"
            }
          }
        },
        "visualTheme": {
          "label": "Art style",
          "description": "Choose the complete visual set for the map, characters, enemies and equipment.",
          "options": {
            "marshmallow-mayhem": {
              "label": "Marshmallow Mayhem",
              "description": "A toasted watercolour world with expressive figures, animated eyes and sweet monsters."
            },
            "frostfire-saga": {
              "label": "Frostfire Saga",
              "description": "A hand-painted Nordic storybook world of ice, embers, runes and carved wood."
            },
            "obsidian-relay": {
              "label": "Obsidian Relay",
              "description": "Detailed sci-fi ruins, plasma, copper and black metal."
            },
            "classic": {
              "label": "Classic",
              "description": "The original warm, organic arena style."
            }
          }
        },
        "difficulty": {
          "label": "Difficulty",
          "description": "Danger level from 1 to 5."
        }
      },
      "confirmation": {
        "label": "Unlock run",
        "description": "Starts automatically once every player is ready."
      }
    },
    "visual": {
      "eyebrow": "Arena"
    },
    "playerSetup": {
      "title": "Choose your character",
      options: characterSetupText("en"),
      "description": "Choose a character for this run."
    }
  }
} as const satisfies LocalizedGameTextMap;
