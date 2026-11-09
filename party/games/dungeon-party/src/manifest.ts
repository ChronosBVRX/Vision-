import { catalogText } from "./catalogText.js";
import type { GameManifest } from "@open-party-lab/game-core";
import type { DungeonActionId } from "./protocol.js";

export const classActions: Record<string, { id: DungeonActionId; name: { de: string; en: string }; description: { de: string; en: string } }> = {
  warrior: { id: "warrior_guard", name: { de: "Intercepción", en: "Guard the line" }, description: { de: "Poder de combate total. Si fallas, recibes 1 daño menos.", en: "Full attack power. Reduce your damage by 1 if the party fails." } },
  mage: { id: "mage_burst", name: { de: "Rayo arcano", en: "Arcane burst" }, description: { de: "+4 Fuerza vs Lucha. Un 1 obtenido te cuesta 2 vidas.", en: "+4 power compared to Fight. Rolling 1 costs you 2 health." } },
  rogue: { id: "rogue_lift", name: { de: "juego de bolsillo", en: "Sleight of hand" }, description: { de: "Poco poder de combate; Si tienes éxito, robas hasta 3 de oro de la persona más rica y ganas 1 de fama por robar.", en: "Low power; on success, steal up to 3 gold from the richest hero and gain 1 fame when you steal." } },
  cleric: { id: "cleric_heal", name: { de: "Oración curativa", en: "Healing prayer" }, description: { de: "Publicación sólida. Antes del daño de la mazmorra, cura 2 a la persona más herida.", en: "Steady contribution. Heal the most wounded hero by 2 before dungeon damage." } },
  bard: { id: "bard_inspire", name: { de: "cancion de pelea", en: "Battle song" }, description: { de: "Tu canción le da al grupo un poder de combate adicional de +2.", en: "Your song gives the party +2 extra power." } },
  tinkerer: { id: "tinkerer_improvise", name: { de: "Conversión de campo", en: "Field contraption" }, description: { de: "Con un 4-6, tu dispositivo improvisado se vuelve particularmente poderoso (+3).", en: "Roll 4–6 to make your contraption extra powerful (+3)." } }
};

export const dungeonPartyManifest = {
  id: "dungeon-party",
  translations: catalogText,
  displayName: "Héroes del sótano",
  description: "Cooperen para sobrevivir la mazmorra, pero compitan por la fama y el botín. El sótano no perdona.",
  minPlayers: 3,
  maxPlayers: 8,
  hostView: "DungeonPartyHost",
  controllerView: "dungeon-party",
  controllerLayout: "dungeon_party",
  supportsTeams: false,
  estimatedRoundDurationMs: 1_800_000,
  scoreScope: "game",
  ownsScreens: ["round_intro", "result"],
  visual: { accent: "#b7773e", icon: "tower", eyebrow: "Sótano" },
  audio: {
    track: { profile: "strategy", bpm: 92, rootMidi: 43, masterGain: 0.12 },
    trackByStage: {
      camp: { profile: "strategy", bpm: 76, rootMidi: 48, masterGain: 0.08, crossfadeSeconds: 2 },
      camp_reveal: { profile: "strategy", bpm: 76, rootMidi: 48, masterGain: 0.08, crossfadeSeconds: 2 },
      response: { profile: "mystery", bpm: 96, rootMidi: 43, masterGain: 0.09, crossfadeSeconds: 1.8 },
      reveal: { profile: "battle", bpm: 112, rootMidi: 43, masterGain: 0.14, crossfadeSeconds: 0.7 },
      complete: { profile: "battle", bpm: 92, rootMidi: 43, masterGain: 0.1, crossfadeSeconds: 2.5 }
    }
  },
  playerSetup: {
    kind: "choice",
    required: true,
    selectionKey: "dungeonClass",
    options: [
      { id: "warrior", name: "guerrero", title: "Intercepción", description: "Poder de combate total y 1 daño menos en caso de fallo.", visual: { primaryColor: "#c45a46", secondaryColor: "#422b2b", accentColor: "#f2a87d" } },
      { id: "mage", name: "mago", title: "Rayo arcano", description: "+4 fuerza contra lucha; Un 1 obtenido te cuesta 2 vidas.", visual: { primaryColor: "#7b72d8", secondaryColor: "#302c60", accentColor: "#c3baff" } },
      { id: "rogue", name: "pícaro", title: "juego de bolsillo", description: "Si tienes éxito, robas hasta 3 de oro de la persona más rica y obtienes 1 de gloria por robar.", visual: { primaryColor: "#4d9a72", secondaryColor: "#234539", accentColor: "#a8e2b9" } },
      { id: "cleric", name: "clérigo", title: "Oración curativa", description: "Antes del daño de la mazmorra, cura 2 puntos de salud a la persona más herida.", visual: { primaryColor: "#d3ad54", secondaryColor: "#574522", accentColor: "#f8df91" } },
      { id: "bard", name: "bardo", title: "cancion de pelea", description: "Le da al grupo un poder de combate adicional de +2.", visual: { primaryColor: "#d16b9d", secondaryColor: "#572c48", accentColor: "#ffc0dc" } },
      { id: "tinkerer", name: "manitas", title: "Conversión de campo", description: "Con un 4-6, tu dispositivo otorga un poder de combate adicional de +3.", visual: { primaryColor: "#618eae", secondaryColor: "#2c4051", accentColor: "#b0d7ee" } }
    ]
  }
} as const satisfies GameManifest;

export const manifest = dungeonPartyManifest;
