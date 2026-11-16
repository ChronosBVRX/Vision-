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

const spanishSpells: Record<MageDuelSpellId, { displayName: string; description: string }> = {
  "fire_bolt": { "displayName": "Lanza de Fuego", "description": "Trazo rojo de abajo hacia arriba. Lanza un ataque directo de fuego." },
  "flame_drop": { "displayName": "Lluvia de Llamas", "description": "Trazo rojo de arriba hacia abajo. Lluvia de fuego sobre el rival." },
  "fire_wall": { "displayName": "Muro de Fuego", "description": "Triángulo rojo. Potencia tus siguientes hechizos de ataque por 10 segundos." },
  "ember_orb": { "displayName": "Orbe de Brasas", "description": "Círculo rojo. Lanza un orbe de brasas más lento." },
  "meteor_square": { "displayName": "Cuadrado de Magma", "description": "Cuadrado rojo. Impacto pesado que también quema maná." },
  "burn_spiral": { "displayName": "Espiral Ardiente", "description": "Espiral roja. Prende fuego al rival con daño en el tiempo." },
  "scarlet_lightning": { "displayName": "Relámpago Escarlata", "description": "Rayo rojo. Ataque muy rápido y potente." },
  "rift_cleave": { "displayName": "Hoja de Grieta", "description": "Triángulo rojo con trazo. Atraviesa parte del escudo del objetivo." },
  "arcane_bolt": { "displayName": "Lanza Arcana", "description": "Trazo azul de abajo hacia arriba. Ataque arcano rápido y económico." },
  "frost_ward": { "displayName": "Guardia de Hielo", "description": "Trazo azul de arriba hacia abajo. Protección breve." },
  "shield": { "displayName": "Escudo Triangular", "description": "Triángulo azul. Burbuja protectora alrededor del mago." },
  "mana_surge": { "displayName": "Círculo de Maná", "description": "Círculo azul. Restaura maná." },
  "mirror_square": { "displayName": "Cuadrado Espejo", "description": "Cuadrado azul. Escudo fuerte y duradero." },
  "mana_triplet": { "displayName": "Trío de Maná", "description": "Tres círculos azules. Restaura maná y reduce enfriamientos actuales." },
  "null_cross": { "displayName": "Cruz Nula", "description": "Cruz azul. Poco daño pero drena el maná del rival." },
  "tide_barrier": { "displayName": "Barrera de Marea", "description": "Onda azul. Elimina efectos de daño en el tiempo y da protección breve." },
  "thorn_lance": { "displayName": "Lanza de Espinas", "description": "Trazo verde de abajo hacia arriba. Ataque ligero de naturaleza." },
  "heal": { "displayName": "Retorno Verde", "description": "Trazo verde de arriba hacia abajo. Restaura salud al instante." },
  "vine_wall": { "displayName": "Muro de Enredaderas", "description": "Triángulo verde. Escudo más débil pero duradero." },
  "clover_bloom": { "displayName": "Flor de Trébol", "description": "Tres círculos verdes. Cura al instante y con el tiempo." },
  "poison_spiral": { "displayName": "Espiral Venenosa", "description": "Espiral verde. Envenena al rival con daño en el tiempo." },
  "root_snare": { "displayName": "Trampa de Raíces", "description": "Zigzag verde. Daño ligero y altera el maná del rival." },
  "verdant_chevron": { "displayName": "Foco Natural", "description": "Chevron verde. Cura un poco y acelera la regeneración de maná." },
  "regeneration": { "displayName": "Círculo de Vida", "description": "Círculo verde. Restaura salud durante 20 segundos." }
};

export function localizeSpell(spell: MageDuelSpellDefinition, language: string | undefined): MageDuelSpellDefinition {
  if (language === "en") return { ...spell, ...englishSpells[spell.id] };
  if (language === "es") return { ...spell, ...spanishSpells[spell.id] };
  return spell;
}

const recognitionText: Record<string, string> = { "Dreieck erkannt": "Triangle recognised", "Viereck erkannt": "Square recognised", "Dreieck mit Strich erkannt": "Slashed triangle recognised", "Spirale erkannt": "Spiral recognised", "Drei Kreise erkannt": "Three circles recognised", "Zickzack erkannt": "Zigzag recognised", "Blitz erkannt": "Lightning recognised", "Chevron erkannt": "Chevron recognised", "Kreuz erkannt": "Cross recognised", "Welle erkannt": "Wave recognised", "Linienrichtung erkannt": "Stroke direction recognised", "Kreis erkannt": "Circle recognised", "Zu wenige Punkte": "Too few points", "Kein bekannter Zauber": "No matching spell", "Zauber misslungen": "Spell failed" };
const recognitionTextEs: Record<string, string> = { "Dreieck erkannt": "Triángulo reconocido", "Viereck erkannt": "Cuadrado reconocido", "Dreieck mit Strich erkannt": "Triángulo con trazo reconocido", "Spirale erkannt": "Espiral reconocida", "Drei Kreise erkannt": "Tres círculos reconocidos", "Zickzack erkannt": "Zigzag reconocido", "Blitz erkannt": "Relámpago reconocido", "Chevron erkannt": "Chevron reconocido", "Kreuz erkannt": "Cruz reconocida", "Welle erkannt": "Onda reconocida", "Linienrichtung erkannt": "Dirección del trazo reconocida", "Kreis erkannt": "Círculo reconocido", "Zu wenige Punkte": "Muy pocos puntos", "Kein bekannter Zauber": "Hechizo no reconocido", "Zauber misslungen": "Hechizo fallido" };
export function localizeGestureReason(reason: string, language: string | undefined): string {
  if (language === "en") return recognitionText[reason] ?? reason;
  if (language === "es") return recognitionTextEs[reason] ?? reason;
  return reason;
}
