import type {
  MagicArenaCardDefinition,
  MagicArenaCardId,
  MagicArenaCardInstanceState
} from "../protocol.js";

export const magicArenaCardDefinitions = [
  {
    cardId: "sprint",
    name: "Correr como si te persiguiera la renta",
    type: "Movimiento",
    frequency: 8,
    range: 4,
    description: "Mueve una figura por un camino planeado hasta 4 hexágonos. Tú sí sabes a dónde vas."
  },
  {
    cardId: "charge_run",
    name: "Embestida con prisa",
    type: "Movimiento / Ataque",
    frequency: 6,
    range: 3,
    description: "Corre derecho hasta 3 hexágonos. Al enemigo que empujes le das 1 de daño y lo mandas a volar."
  },
  {
    cardId: "enemy_swap",
    name: "Cambiazo de lugares",
    type: "Truco",
    frequency: 2,
    range: 3,
    description: "Intercambia tu figura con la de un rival a 3 hexágonos. Sorpresa, ahora tú estás en su lugar."
  },
  {
    cardId: "lightning_strike",
    name: "Rayo de barrio",
    type: "Ataque",
    frequency: 8,
    range: 5,
    description: "Todo lo que esté en el hexágono objetivo, aunque esté en el agua, se lleva 2 de daño."
  },
  {
    cardId: "fireball",
    name: "Bola de fuego",
    type: "Ataque / Terreno",
    frequency: 6,
    range: 3,
    description: "Las figuras en el hexágono objetivo reciben 1 de daño; el terreno se lleva 2. Que arda todo."
  },
  {
    cardId: "hammer_blow",
    name: "Martillazo",
    type: "Ataque cuerpo a cuerpo",
    frequency: 6,
    range: 1,
    description: "Un rival en tu hexágono o al lado recibe 2 de daño y sale volando."
  },
  {
    cardId: "bomb",
    name: "Bomba de tiempo",
    type: "Ataque / Terreno / Diferido",
    frequency: 5,
    range: 2,
    description: "Coloca una bomba. Explota al inicio de la siguiente acción global. Corran."
  },
  {
    cardId: "meteor_strike",
    name: "Meteorito",
    type: "Ataque / Terreno",
    frequency: 4,
    range: 4,
    description: "Centro: 2 de daño a figuras y 3 al terreno. Vecinos: 1 de daño al terreno."
  },
  {
    cardId: "rift",
    name: "Grieta",
    type: "Terreno",
    frequency: 6,
    range: 4,
    description: "Un hexágono existente recibe 3 de daño. El piso no es tan confiable."
  },
  {
    cardId: "earthquake",
    name: "Terremoto",
    type: "Terreno",
    frequency: 4,
    range: null,
    description: "Todos los hexágonos vecinos reciben 1 de daño de terreno. Tiembla la arena."
  },
  {
    cardId: "platform_break",
    name: "Se cayó el piso",
    type: "Terreno",
    frequency: 3,
    range: 4,
    description: "Destruye de inmediato un hexágono dañado con 2 o menos de vida. Adiós, piso."
  },
  {
    cardId: "stealth",
    name: "Modo invisible",
    type: "Truco",
    frequency: 4,
    range: null,
    description: "Tu figura queda oculta hasta el final de la siguiente ronda o hasta que ataque. Nadie te vio."
  },
  {
    cardId: "shockwave",
    name: "Onda de empujón",
    type: "Ataque / Truco",
    frequency: 3,
    range: 1,
    description: "Todas las figuras enemigas vecinas reciben 1 de daño y salen empujadas."
  },
  {
    cardId: "chain_lightning",
    name: "Rayo en cadena",
    type: "Ataque",
    frequency: 6,
    range: 4,
    description: "Golpea una figura con 2 de daño y salta hasta dos veces con 1 de daño cada brinco."
  },
  {
    cardId: "pitfall",
    name: "Trampa",
    type: "Trampa / Terreno",
    frequency: 5,
    range: 2,
    description: "Coloca una trampa oculta. Quien entre se lleva 1 de daño y el terreno 2."
  },
  {
    cardId: "teleport",
    name: "Teletransporte",
    type: "Movimiento",
    frequency: 5,
    range: 4,
    description: "Mueve tu figura a un hexágono libre e intacto a 4 de distancia. Sin pasar por la fila."
  },
  {
    cardId: "kraken",
    name: "Pulpo gigante",
    type: "Ataque acuático",
    frequency: 4,
    range: 5,
    description: "Una figura en el agua recibe 2 de daño y es arrastrada un hexágono hacia el centro."
  },
  {
    cardId: "time_rift",
    name: "Rasgadura temporal",
    type: "Ataque de terreno diferido",
    frequency: 5,
    range: 4,
    description: "Marca un hexágono. Al inicio de la siguiente ronda: 1 de daño a figuras y 3 al terreno."
  },
  {
    cardId: "magic_mine",
    name: "Mina mágica",
    type: "Trampa / Explosión",
    frequency: 5,
    range: 2,
    description: "Coloca una mina visible. Al activarse: 1 de daño al centro y 2 al terreno."
  },
  {
    cardId: "storm_cloud",
    name: "Nube de tormenta",
    type: "Ataque de área diferido",
    frequency: 5,
    range: 4,
    description: "Marca un hexágono. Durante 2 rondas, castiga a quien esté ahí o pase por ahí, máximo una vez por figura por ronda."
  }
] as const satisfies MagicArenaCardDefinition[];

export const magicArenaCardDefinitionsById = Object.fromEntries(
  magicArenaCardDefinitions.map((definition) => [definition.cardId, definition])
) as Partial<Record<MagicArenaCardId, MagicArenaCardDefinition>>;

export function createMagicArenaDeck(): MagicArenaCardInstanceState[] {
  const deck: MagicArenaCardInstanceState[] = [];

  for (const definition of magicArenaCardDefinitions) {
    for (let index = 1; index <= definition.frequency; index += 1) {
      deck.push({
        instanceId: `${definition.cardId}-${index}`,
        cardId: definition.cardId
      });
    }
  }

  return deck;
}

export function getMagicArenaDeckSize(): number {
  return magicArenaCardDefinitions.reduce((sum, definition) => sum + definition.frequency, 0);
}
