import type { LocalizedGameTextMap } from "@open-party-lab/game-core";

export const buzzwortText = {
  "de": {
    "displayName": "Palabra maldita",
    "description": "Explica el término sin soltar las palabras que queman. Un guardián vigila cada error.",
    "visual": {
      "eyebrow": "Labia maldita"
    }
  }
,
  "en": {
    "displayName": "Palabra maldita",
    "description": "Explica el término sin soltar las palabras que queman. Un guardián vigila cada error.",
    "visual": {
      "eyebrow": "Labia maldita"
    }
  }} as const satisfies Partial<LocalizedGameTextMap>;
