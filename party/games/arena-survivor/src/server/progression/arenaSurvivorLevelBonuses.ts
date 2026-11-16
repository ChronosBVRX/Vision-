import type {
  ArenaSurvivorShopOfferState,
  ArenaSurvivorShopState,
  ArenaSurvivorStatModifiers
} from "../../protocol.js";
import { createSeededRandom, type ArenaSurvivorRuntimePlayerState } from "../arenaSurvivorState.js";

interface ArenaSurvivorLevelBonusDefinition {
  id: string;
  title: { de: string; en: string };
  description: { de: string; en: string };
  detail: { de: string; en: string };
  modifiers: ArenaSurvivorStatModifiers;
}

const levelBonusDefinitions: ArenaSurvivorLevelBonusDefinition[] = [
  { id: "dodge", title: { de: "Ausweichen", en: "Dodge" }, description: { de: "+1% Ausweichen", en: "+1% dodge" }, detail: { de: "Ausweichen", en: "Dodge" }, modifiers: { dodgePct: 1 } },
  { id: "luck", title: { de: "Glueck", en: "Luck" }, description: { de: "+2 Glueck", en: "+2 luck" }, detail: { de: "Glueck", en: "Luck" }, modifiers: { luck: 2 } },
  { id: "harvesting", title: { de: "Ernte", en: "Harvesting" }, description: { de: "+2 Ernte", en: "+2 harvesting" }, detail: { de: "Ernte", en: "Harvesting" }, modifiers: { harvesting: 2 } },
  { id: "pickup", title: { de: "Sammelradius", en: "Pickup radius" }, description: { de: "+5% Sammelradius", en: "+5% pickup radius" }, detail: { de: "Radius", en: "Radius" }, modifiers: { pickupRadiusPct: 5 } },
  { id: "regen", title: { de: "Regeneration", en: "Regeneration" }, description: { de: "+0,1 Leben/s", en: "+0.1 HP/s" }, detail: { de: "Heilung", en: "Healing" }, modifiers: { hpRegen: 0.1 } },
  { id: "crit-damage", title: { de: "Kritischer Schaden", en: "Critical damage" }, description: { de: "+3% kritischer Schaden", en: "+3% critical damage" }, detail: { de: "Krit", en: "Crit" }, modifiers: { critDamagePct: 3 } },
  { id: "melee-power", title: { de: "Nahkampfkraft", en: "Melee power" }, description: { de: "+3% Nahkampfkraft", en: "+3% melee power" }, detail: { de: "Kraft", en: "Power" }, modifiers: { meleePowerPct: 3 } },
  { id: "ranged-power", title: { de: "Fernkampfkraft", en: "Ranged power" }, description: { de: "+3% Fernkampfkraft", en: "+3% ranged power" }, detail: { de: "Kraft", en: "Power" }, modifiers: { rangedPowerPct: 3 } },
  { id: "magic-power", title: { de: "Magiekraft", en: "Magic power" }, description: { de: "+3% Magiekraft", en: "+3% magic power" }, detail: { de: "Kraft", en: "Power" }, modifiers: { magicPowerPct: 3 } },
  { id: "damage", title: { de: "Mehr Schaden", en: "More Damage" }, description: { de: "+3% Schaden", en: "+3% damage" }, detail: { de: "Schaden", en: "Damage" }, modifiers: { damagePct: 3 } },
  { id: "range", title: { de: "Mehr Reichweite", en: "More Range" }, description: { de: "+8% Waffenreichweite", en: "+8% weapon range" }, detail: { de: "Reichweite", en: "Range" }, modifiers: { weaponRangePct: 8 } },
  { id: "crit", title: { de: "Kritische Chance", en: "Critical Chance" }, description: { de: "+1% kritische Trefferchance", en: "+1% critical hit chance" }, detail: { de: "Krit", en: "Crit" }, modifiers: { critChancePct: 1 } },
  { id: "attack-speed", title: { de: "Schnellere Angriffe", en: "Faster Attacks" }, description: { de: "+3% Angriffsgeschwindigkeit", en: "+3% attack speed" }, detail: { de: "Angriffstempo", en: "Attack speed" }, modifiers: { attackSpeedPct: 3 } },
  { id: "max-health", title: { de: "Mehr Leben", en: "More Health" }, description: { de: "+5 maximales Leben", en: "+5 maximum health" }, detail: { de: "Max. Leben", en: "Max health" }, modifiers: { maxHp: 5 } },
  { id: "armor", title: { de: "Mehr Ruestung", en: "More Armor" }, description: { de: "+1 Ruestung", en: "+1 armor" }, detail: { de: "Ruestung", en: "Armor" }, modifiers: { armor: 1 } },
  { id: "move-speed", title: { de: "Mehr Tempo", en: "More Speed" }, description: { de: "+3% Bewegungstempo", en: "+3% movement speed" }, detail: { de: "Tempo", en: "Speed" }, modifiers: { moveSpeedPct: 3 } },
  { id: "life-steal", title: { de: "Lebensraub", en: "Life Steal" }, description: { de: "+1% Lebensraub", en: "+1% life steal" }, detail: { de: "Lebensraub", en: "Life steal" }, modifiers: { lifeStealPct: 1 } }
];

export function createArenaSurvivorLevelUpShopState(
  player: ArenaSurvivorRuntimePlayerState,
  seed: number,
  language?: "de" | "en",
  survival = false
): { shop: ArenaSurvivorShopState; seed: number } {
  const languageKey = language === "en" ? "en" : "de";
  const remainingDefinitions = levelBonusDefinitions.filter(definition => !survival || definition.id !== "harvesting");
  const offers: ArenaSurvivorShopOfferState[] = [];
  let nextSeed = seed;

  while (offers.length < (survival ? 3 : 4) && remainingDefinitions.length > 0) {
    const random = createSeededRandom(nextSeed);
    nextSeed = random.seed;
    const definitionIndex = Math.min(remainingDefinitions.length - 1, Math.floor(random.value * remainingDefinitions.length));
    const [definition] = remainingDefinitions.splice(definitionIndex, 1);
    // Later level-ups offer meaningful progression instead of the same tiny
    // bonus forever. One tier every five levels, up to four, in both modes.
    const awardedLevel = Math.max(1, player.level - player.pendingLevelUpChoices + 1);
    const tier = Math.min(4, 1 + Math.floor((awardedLevel - 1) / 5));
    const modifiers = Object.fromEntries(Object.entries(definition.modifiers)
      .map(([key, value]) => [key, value * tier])) as ArenaSurvivorStatModifiers;
    const description = definition.description[languageKey].replace(/\d+(?:[.,]\d+)?/, value =>
      new Intl.NumberFormat(languageKey, { maximumFractionDigits: 2 })
        .format(Number(value.replace(",", ".")) * tier));

    offers.push({
      id: `level-bonus:${player.level}:${player.pendingLevelUpChoices}:${definition.id}`,
      kind: "upgrade",
      title: definition.title[languageKey],
      description,
      cost: 0,
      affordable: true,
      purchased: false,
      targetLevel: player.level - player.pendingLevelUpChoices + 1,
      levelBonusId: definition.id,
      levelBonusModifiers: modifiers,
      tags: [languageKey === "en" ? "Level bonus" : "Level-Bonus", `${languageKey === "en" ? "Tier" : "Stufe"} ${tier}`],
      summary: description,
      detailLines: [{ label: definition.detail[languageKey], value: description }]
    });
  }

  return {
    seed: nextSeed,
    shop: {
      mode: "level_up",
      available: offers.length > 0,
      offers,
      message: languageKey === "en" ? "Choose one bonus for this level." : "Waehle einen Bonus fuer dieses Level.",
      rerollCount: 0,
      rerollCost: 0,
      canReroll: false
    }
  };
}
