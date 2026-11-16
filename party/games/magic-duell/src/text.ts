import type { MageDuelSpellDefinition, MageDuelSpellId } from "./protocol.js";

const englishSpells: Record<MageDuelSpellId, { displayName: string; description: string }> = {
  "fire_bolt": {
    "displayName": "Fire Lance",
    "description": "Red upward stroke. Launch a direct fire attack."
  },
  "flame_drop": {
    "displayName": "Flamefall",
    "description": "Red downward stroke. Rain fire on your opponent."
  },
  "fire_wall": {
    "displayName": "Fire Wall",
    "description": "Red triangle. Empower your next attack spells for 10 seconds."
  },
  "ember_orb": {
    "displayName": "Ember Orb",
    "description": "Red circle. Launch a slower orb of embers."
  },
  "meteor_square": {
    "displayName": "Magma Square",
    "description": "Red square. A heavy impact that also burns mana."
  },
  "burn_spiral": {
    "displayName": "Burning Spiral",
    "description": "Red spiral. Set your opponent ablaze for damage over time."
  },
  "scarlet_lightning": {
    "displayName": "Scarlet Lightning",
    "description": "Red lightning bolt. A very fast, powerful attack."
  },
  "rift_cleave": {
    "displayName": "Rift Blade",
    "description": "Red triangle with a stroke. Pierce part of the target’s shield."
  },
  "arcane_bolt": {
    "displayName": "Arcane Lance",
    "description": "Blue upward stroke. A fast arcane attack with a low mana cost."
  },
  "frost_ward": {
    "displayName": "Frost Ward",
    "description": "Blue downward stroke. Gain brief protection."
  },
  "shield": {
    "displayName": "Triangle Shield",
    "description": "Blue triangle. Surround your mage with a protective bubble."
  },
  "mana_surge": {
    "displayName": "Mana Circle",
    "description": "Blue circle. Restore mana."
  },
  "mirror_square": {
    "displayName": "Mirror Square",
    "description": "Blue square. Gain a strong, lasting shield."
  },
  "mana_triplet": {
    "displayName": "Mana Trio",
    "description": "Three blue circles. Restore mana and reduce current cooldowns."
  },
  "null_cross": {
    "displayName": "Null Cross",
    "description": "Blue cross. Deal little damage but drain your opponent’s mana."
  },
  "tide_barrier": {
    "displayName": "Tide Barrier",
    "description": "Blue wave. Remove damage-over-time effects and gain brief protection."
  },
  "thorn_lance": {
    "displayName": "Thorn Lance",
    "description": "Green upward stroke. A light nature attack."
  },
  "heal": {
    "displayName": "Verdant Return",
    "description": "Green downward stroke. Restore health immediately."
  },
  "vine_wall": {
    "displayName": "Vine Wall",
    "description": "Green triangle. Gain a weaker, longer-lasting shield."
  },
  "clover_bloom": {
    "displayName": "Clover Bloom",
    "description": "Three green circles. Heal immediately and then over time."
  },
  "poison_spiral": {
    "displayName": "Poison Spiral",
    "description": "Green spiral. Poison your opponent for damage over time."
  },
  "root_snare": {
    "displayName": "Root Snare",
    "description": "Green zigzag. Deal light damage and disrupt the opponent’s mana."
  },
  "verdant_chevron": {
    "displayName": "Nature Focus",
    "description": "Green chevron. Heal slightly and accelerate your mana regeneration."
  },
  "regeneration": {
    "displayName": "Circle of Life",
    "description": "Green circle. Restore health over 20 seconds."
  }
};

export function localizeSpell(spell: MageDuelSpellDefinition, language: string | undefined): MageDuelSpellDefinition {
 return language === "en" ? { ...spell, ...englishSpells[spell.id] } : spell;
}

const recognitionText: Record<string, string> = { "Dreieck erkannt": "Triangle recognised", "Viereck erkannt": "Square recognised", "Dreieck mit Strich erkannt": "Slashed triangle recognised", "Spirale erkannt": "Spiral recognised", "Drei Kreise erkannt": "Three circles recognised", "Zickzack erkannt": "Zigzag recognised", "Blitz erkannt": "Lightning recognised", "Chevron erkannt": "Chevron recognised", "Kreuz erkannt": "Cross recognised", "Welle erkannt": "Wave recognised", "Linienrichtung erkannt": "Stroke direction recognised", "Kreis erkannt": "Circle recognised", "Zu wenige Punkte": "Too few points", "Kein bekannter Zauber": "No matching spell", "Zauber misslungen": "Spell failed" };
export function localizeGestureReason(reason: string, language: string | undefined): string { return language === "en" ? recognitionText[reason] ?? reason : reason; }
