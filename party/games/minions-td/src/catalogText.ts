import type { LocalizedGameTextMap } from "@open-party-lab/game-core";

export const minionsTdText = {
  "es": {
    "displayName": "MinionsTD",
    "description": "Construye torres, envía esbirros y mantén tu carril vivo más que tus rivales.",
    "lobbySetup": {
      "title": "Configuración de MinionsTD",
      "description": "Elige el mapa, las vidas iniciales y el oro inicial antes de la ronda.",
      "fields": {
        "mapId": {
          "label": "Mapa",
          "options": {
            "switchback": { "label": "Switchback" },
            "causeway": { "label": "Causeway" },
            "ziggurat": { "label": "Ziggurat" },
            "crossroads": { "label": "Crossroads" },
            "lockstep": { "label": "Lockstep" },
            "harbor": { "label": "Harbor" },
            "serpentine": { "label": "Serpentine" },
            "redoubt": { "label": "Redoubt" },
            "overpass": { "label": "Overpass" },
            "bastion": { "label": "Bastion" }
          }
        },
        "startingLives": {
          "label": "Vidas iniciales"
        },
        "startingGold": {
          "label": "Oro inicial"
        }
      }
    },
    "visual": {
      "eyebrow": "Defensa de Torres"
    }
  },
  "de": {
    "displayName": "MinionsTD",
    "description": "Baue Tower, schicke Minions weiter und halte deine Lane laenger als die anderen.",
    "lobbySetup": {
      "title": "MinionsTD Setup",
      "description": "Waehle Map, Startleben und Startgold vor dem Rundenstart.",
      "fields": {
        "mapId": {
          "label": "Karte",
          "options": {
            "switchback": {
              "label": "Switchback"
            },
            "causeway": {
              "label": "Causeway"
            },
            "ziggurat": {
              "label": "Ziggurat"
            },
            "crossroads": {
              "label": "Crossroads"
            },
            "lockstep": {
              "label": "Lockstep"
            },
            "harbor": {
              "label": "Harbor"
            },
            "serpentine": {
              "label": "Serpentine"
            },
            "redoubt": {
              "label": "Redoubt"
            },
            "overpass": {
              "label": "Overpass"
            },
            "bastion": {
              "label": "Bastion"
            }
          }
        },
        "startingLives": {
          "label": "Startleben"
        },
        "startingGold": {
          "label": "Startgold"
        }
      }
    },
    "visual": {
      "eyebrow": "Turmverteidigung"
    }
  },
  "en": {
    "displayName": "MinionsTD",
    "description": "Build towers, send minions onward and keep your lane alive longer than your rivals.",
    "lobbySetup": {
      "title": "MinionsTD setup",
      "description": "Choose the map, starting lives and starting gold before the round.",
      "fields": {
        "mapId": {
          options: Object.fromEntries(["switchback", "causeway", "ziggurat", "crossroads", "lockstep", "harbor", "serpentine", "redoubt", "overpass", "bastion"].map(id => [id, { label: ({ switchback: "Switchback", causeway: "Causeway", ziggurat: "Ziggurat", crossroads: "Crossroads", lockstep: "Lockstep", harbor: "Harbor", serpentine: "Serpentine", redoubt: "Redoubt", overpass: "Overpass", bastion: "Bastion" } as Record<string, string>)[id] }])),
          "label": "Map"
        },
        "startingLives": {
          "label": "Starting lives"
        },
        "startingGold": {
          "label": "Starting gold"
        }
      }
    },
    "visual": {
      "eyebrow": "Tower Defense"
    }
  }
} as const satisfies LocalizedGameTextMap;
