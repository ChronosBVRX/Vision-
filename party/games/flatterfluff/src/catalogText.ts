import type { LocalizedGameTextMap } from "@open-party-lab/game-core";

export const flatterfluffText = {
  "de": {
    "displayName": "Dulce puntería",
    "description": "Malvaviscos alados, miras de colores y dedos calientes. Apunta, dispara y recarga antes de que se escapen.",
    "lobbySetup": {
      "title": "Prepara la puntería",
      "description": "Elige la búsqueda del tiempo o una ronda abierta sin fin.",
      "fields": {
        "mode": {
          "label": "Modo de cacería",
          "options": {
            "timed": {
              "label": "Cacería cronometrada",
              "description": "Recoge tantos puntos como puedas antes de que se acabe el tiempo."
            },
            "endless": {
              "label": "Sin fin",
              "description": "Juega sin límite de tiempo hasta que el anfitrión termine la ronda."
            }
          }
        },
        "roundSeconds": {
          "label": "Duración de la cacería",
          "description": "Cuánto dura la cacería, en segundos."
        }
      }
    },
    "visual": {
      "eyebrow": "Puntería dulce"
    }
  }
,
  "en": {
    "displayName": "Dulce puntería",
    "description": "Malvaviscos alados, miras de colores y dedos calientes. Apunta, dispara y recarga antes de que se escapen.",
    "lobbySetup": {
      "title": "Prepara la puntería",
      "description": "Elige la búsqueda del tiempo o una ronda abierta sin fin.",
      "fields": {
        "mode": {
          "label": "Modo de cacería",
          "options": {
            "timed": {
              "label": "Cacería cronometrada",
              "description": "Recoge tantos puntos como puedas antes de que se acabe el tiempo."
            },
            "endless": {
              "label": "Sin fin",
              "description": "Juega sin límite de tiempo hasta que el anfitrión termine la ronda."
            }
          }
        },
        "roundSeconds": {
          "label": "Duración de la cacería",
          "description": "Cuánto dura la cacería, en segundos."
        }
      }
    },
    "visual": {
      "eyebrow": "Puntería dulce"
    }
  }} as const satisfies Partial<LocalizedGameTextMap>;
