import type { LocalizedGameTextMap } from "@open-party-lab/game-core";

export const chaosKommandoText = {
  "es": {
    "displayName": "Comando Caos",
    "description": "Artillería por turnos de malvaviscos: seis equipos, arenas de islas, minas, cajas y terreno destructible con túneles.",
    "visual": {
      "eyebrow": "Artillería"
    }
  },
  "de": {
    "displayName": "Chaos-Kommando",
    "description": "Marshmallow-Runden-Artillerie: geroestete Kommandos, sechs Teams, Inselarenen, Minen, Kisten und komplett zerstoerbares Terrain mit Tunneln.",
    "visual": {
      "eyebrow": "Artillerie"
    }
  },
  "en": {
    "displayName": "Chaos Squad",
    "description": "Turn-based marshmallow artillery: six teams, island arenas, mines, crates and fully destructible terrain with tunnels.",
    "visual": {
      "eyebrow": "Artillery"
    }
  }
} as const satisfies LocalizedGameTextMap;
