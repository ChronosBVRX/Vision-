import type { LocalizedGameTextMap } from "@open-party-lab/game-core";

export const zeichnenUndErratenText = {
  "de": {
    "displayName": "Garabato mental",
    "description": "Dibujos feos, mentes retorcidas y un celular de por medio. Traza, adivina y ríete del desastre.",
    "visual": {
      "eyebrow": "Garabato"
    }
  }
,
  "en": {
    "displayName": "Garabato mental",
    "description": "Dibujos feos, mentes retorcidas y un celular de por medio. Traza, adivina y ríete del desastre.",
    "visual": {
      "eyebrow": "Garabato"
    }
  }} as const satisfies Partial<LocalizedGameTextMap>;
