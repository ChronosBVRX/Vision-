import type { LocalizedGameTextMap } from "@open-party-lab/game-core";
export const catalogText = {
  "de": {
    "displayName": "Magic Duell",
    "description": "Statisches 3D-Magierduell mit gezeichneten Handy-Zaubern.",
    "playerSetup": {
      "title": "Zauber wählen",
      "description": "Wähle die Zauber, die auf deinem Handy verfügbar sein sollen.",
      "readyBlockedLabel": "Wähle zuerst deine Zauber"
    },
    "visual": {
      "eyebrow": "Duell"
    }
  },
  "en": {
    "displayName": "Magic Duel",
    "description": "A 3D mage duel powered by spells drawn on your phone.",
    "playerSetup": {
      "title": "Choose spells",
      "description": "Choose the spells you want to use on your phone.",
      "readyBlockedLabel": "Choose your spells first",
      "options": {
        "fire_bolt": {
          "name": "Fire Lance",
          "description": "Red upward stroke. Launch a direct fire attack.",
          "title": "20 mana | 4.5s cooldown"
        },
        "flame_drop": {
          "name": "Flamefall",
          "description": "Red downward stroke. Rain fire on your opponent.",
          "title": "24 mana | 7.0s cooldown"
        },
        "fire_wall": {
          "name": "Fire Wall",
          "description": "Red triangle. Empower your next attack spells for 10 seconds.",
          "title": "55 mana | 60.0s cooldown"
        },
        "ember_orb": {
          "name": "Ember Orb",
          "description": "Red circle. Launch a slower orb of embers.",
          "title": "28 mana | 9.0s cooldown"
        },
        "meteor_square": {
          "name": "Magma Square",
          "description": "Red square. A heavy impact that also burns mana.",
          "title": "38 mana | 16.0s cooldown"
        },
        "burn_spiral": {
          "name": "Burning Spiral",
          "description": "Red spiral. Set your opponent ablaze for damage over time.",
          "title": "42 mana | 21.0s cooldown"
        },
        "scarlet_lightning": {
          "name": "Scarlet Lightning",
          "description": "Red lightning bolt. A very fast, powerful attack.",
          "title": "34 mana | 14.0s cooldown"
        },
        "rift_cleave": {
          "name": "Rift Blade",
          "description": "Red triangle with a stroke. Pierce part of the target’s shield.",
          "title": "36 mana | 17.0s cooldown"
        },
        "arcane_bolt": {
          "name": "Arcane Lance",
          "description": "Blue upward stroke. A fast arcane attack with a low mana cost.",
          "title": "14 mana | 5.0s cooldown"
        },
        "frost_ward": {
          "name": "Frost Ward",
          "description": "Blue downward stroke. Gain brief protection.",
          "title": "22 mana | 9.5s cooldown"
        },
        "shield": {
          "name": "Triangle Shield",
          "description": "Blue triangle. Surround your mage with a protective bubble.",
          "title": "35 mana | 11.0s cooldown"
        },
        "mana_surge": {
          "name": "Mana Circle",
          "description": "Blue circle. Restore mana.",
          "title": "5 mana | 26.0s cooldown"
        },
        "mirror_square": {
          "name": "Mirror Square",
          "description": "Blue square. Gain a strong, lasting shield.",
          "title": "42 mana | 19.0s cooldown"
        },
        "mana_triplet": {
          "name": "Mana Trio",
          "description": "Three blue circles. Restore mana and reduce current cooldowns.",
          "title": "8 mana | 34.0s cooldown"
        },
        "null_cross": {
          "name": "Null Cross",
          "description": "Blue cross. Deal little damage but drain your opponent’s mana.",
          "title": "26 mana | 18.0s cooldown"
        },
        "tide_barrier": {
          "name": "Tide Barrier",
          "description": "Blue wave. Remove damage-over-time effects and gain brief protection.",
          "title": "32 mana | 24.0s cooldown"
        },
        "thorn_lance": {
          "name": "Thorn Lance",
          "description": "Green upward stroke. A light nature attack.",
          "title": "16 mana | 6.5s cooldown"
        },
        "heal": {
          "name": "Verdant Return",
          "description": "Green downward stroke. Restore health immediately.",
          "title": "30 mana | 13.0s cooldown"
        },
        "vine_wall": {
          "name": "Vine Wall",
          "description": "Green triangle. Gain a weaker, longer-lasting shield.",
          "title": "27 mana | 14.0s cooldown"
        },
        "clover_bloom": {
          "name": "Clover Bloom",
          "description": "Three green circles. Heal immediately and then over time.",
          "title": "46 mana | 32.0s cooldown"
        },
        "poison_spiral": {
          "name": "Poison Spiral",
          "description": "Green spiral. Poison your opponent for damage over time.",
          "title": "38 mana | 23.0s cooldown"
        },
        "root_snare": {
          "name": "Root Snare",
          "description": "Green zigzag. Deal light damage and disrupt the opponent’s mana.",
          "title": "25 mana | 13.0s cooldown"
        },
        "verdant_chevron": {
          "name": "Nature Focus",
          "description": "Green chevron. Heal slightly and accelerate your mana regeneration.",
          "title": "34 mana | 28.0s cooldown"
        },
        "regeneration": {
          "name": "Circle of Life",
          "description": "Green circle. Restore health over 20 seconds.",
          "title": "45 mana | 30.0s cooldown"
        }
      }
    },
    "visual": {
      "eyebrow": "Duel"
    }
  }
} satisfies LocalizedGameTextMap;
