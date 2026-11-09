/** Game-owned presentation copy; technical action/card IDs stay unchanged. */
const englishText: Record<string, string> = {
  "Sprint": "Sprint",
  "Bewegung": "Move",
  "Bewege eine Figur ueber einen geplanten Pfad bis zu 4 Hexfelder.": "Move a figure along a planned path of up to 4 hexes.",
  "Stosslauf": "Charge",
  "Bewegung/Angriff": "Move / Attack",
  "Laufe geradeaus bis zu 3 Felder. Ein getroffener Gegner erhaelt 1 Schaden und wird geschoben.": "Carga hacia adelante hasta 3 hexágonos. Un enemigo al que golpeas recibe 1 daño y es empujado.",
  "Gegner-Tausch": "Enemy Swap",
  "Trick": "Trick",
  "Tausche die aktive Figur mit einer gegnerischen Figur in Reichweite 3.": "Intercambia tu figura activa con una figura enemiga dentro del alcance 3.",
  "Blitzschlag": "Lightning Strike",
  "Angriff": "Attack",
  "Alle Figuren auf der Zielkoordinate, auch im Wasser, erhalten 2 Schaden.": "All figures at the target coordinate, including those in water, take 2 damage.",
  "Feuerball": "Fireball",
  "Angriff/Terrain": "Attack / Terrain",
  "Alle Figuren auf der Zielkoordinate erhalten 1 Schaden. Das Feld erhaelt 2 Feldschaden.": "All figures at the target coordinate take 1 damage. The tile takes 2 terrain damage.",
  "Hammerstoss": "Hammer Blow",
  "Nahkampfangriff": "Melee attack",
  "Ein Gegner im eigenen oder angrenzenden Feld erhaelt 2 Schaden und wird geschoben.": "Un enemigo en tu casilla o en una casilla adyacente sufre 2 daños y es empujado.",
  "Bombe": "Bomb",
  "Angriff/Terrain/verzoegert": "Attack / Terrain / Delayed",
  "Lege eine Bombe. Sie explodiert am Anfang des naechsten globalen Aktionsslots.": "Place a bomb. It explodes at the start of the next global action slot.",
  "Meteorschlag": "Meteor Strike",
  "Zentrum: 2 Figurenschaden und 3 Feldschaden. Nachbarn: 1 Feldschaden.": "Centre: 2 figure damage and 3 terrain damage. Adjacent tiles: 1 terrain damage.",
  "Riss": "Rift",
  "Terrain": "Terrain",
  "Ein existierendes Feld erhaelt 3 Feldschaden.": "Inflige 3 daños al terreno a una loseta existente.",
  "Erdbeben": "Earthquake",
  "Alle angrenzenden existierenden Felder erhalten 1 Feldschaden.": "All adjacent existing tiles take 1 terrain damage.",
  "Plattformbruch": "Platform Break",
  "Ein beschaedigtes Feld mit 2 oder weniger HP wird sofort zerstoert.": "Immediately destroy a damaged tile with 2 HP or less.",
  "Tarnung": "Stealth",
  "Die aktive Figur ist bis zum Ende der naechsten Runde verborgen, bis sie Schaden verursacht.": "Hide your active figure until the end of the next round, or until it deals damage.",
  "Schubwelle": "Shockwave",
  "Angriff/Trick": "Attack / Trick",
  "Alle angrenzenden gegnerischen Figuren erhalten 1 Schaden und werden weggeschoben.": "All adjacent enemy figures take 1 damage and are pushed away.",
  "Kettenblitz": "Chain Lightning",
  "Trifft eine Figur fuer 2 Schaden und springt bis zu zweimal fuer je 1 Schaden weiter.": "Hit one figure for 2 damage, then arc to up to two other figures for 1 damage each.",
  "Fallgrube": "Pitfall",
  "Falle/Terrain": "Trap / Terrain",
  "Platziert eine verdeckte Falle. Beim Betreten: 1 Figurenschaden und 2 Feldschaden.": "Place a hidden trap. Entering it deals 1 figure damage and 2 terrain damage.",
  "Teleport": "Teleport",
  "Teleportiert die aktive Figur auf ein freies, intaktes Feld in Reichweite 4.": "Teleport your active figure to a free, intact tile within range 4.",
  "Krake": "Kraken",
  "Wasser-Angriff": "Water attack",
  "Eine Figur im Wasser erhaelt 2 Schaden und wird ein Feld zur Kartenmitte gezogen.": "A figure in water takes 2 damage and is pulled one hex toward the board centre.",
  "Zeitriss": "Time Rift",
  "Verzoegerter Terrain-Angriff": "Delayed terrain attack",
  "Markiert ein Feld. Zu Beginn der naechsten Runde: 1 Figurenschaden und 3 Feldschaden.": "Mark a tile. At the start of the next round, deal 1 figure damage and 3 terrain damage.",
  "Magische Mine": "Magic Mine",
  "Falle/Explosion": "Trap / Explosion",
  "Platziert eine sichtbare Mine. Beim Ausloesen: Zentrum 1 Schaden und 2 Feldschaden.": "Place a visible mine. When triggered, the centre takes 1 figure damage and 2 terrain damage.",
  "Gewitterwolke": "Storm Cloud",
  "Verzoegerter Flaechenangriff": "Delayed area attack",
  "Markiert ein Feld. Ab naechster Runde trifft sie 2 Runden lang Figuren, die dort stehen oder hindurchlaufen, maximal 1x pro Figur und Runde.": "Mark a tile. Starting next round, the cloud strikes figures standing on or crossing it for 2 rounds, at most once per figure per round.",
  "Der Ablagestapel wird neu gemischt.": "The discard pile is reshuffled.",
  "Magic Arena: Zieht Zug fuer Zug nacheinander und ueberlebt die Hex-Insel.": "Magic Arena: Take turns and survive the hex island.",
  "Magic Arena: Plane vier Aktionen und ueberlebe die Hex-Insel.": "Magic Arena: Plan four actions and survive the hex island.",
  "Magic Arena beginnt. Zieht Zug fuer Zug nacheinander.": "Magic Arena begins. Take one turn at a time.",
  "Magic Arena beginnt. Plant verdeckt eure Aktionen.": "Magic Arena begins. Plan your actions in secret.",
  "Nicht in der Planungsphase.": "It is not the planning phase.",
  "Spieler nicht gefunden.": "Player not found.",
  "Deine Planung ist bereits gesperrt.": "Your plan is already locked.",
  "Du bist gerade nicht am Zug.": "It is not your turn.",
  "Dieser Slot ist nicht verfuegbar.": "This slot is unavailable.",
  "Diese Figur kann nicht handeln.": "This figure cannot act.",
  "Diese Figur ist bereits verplant.": "This figure already has a planned action.",
  "Der Bewegungspfad ist ungueltig.": "The movement path is invalid.",
  "Boxen braucht ein Ziel im eigenen oder angrenzenden Feld.": "Punch necesita un objetivo en tu ficha o en una ficha adyacente.",
  "Unbekannte Basisaktion.": "Unknown basic action.",
  "Diese Karte liegt nicht auf deiner Hand.": "This card is not in your hand.",
  "Diese Karte ist bereits reserviert.": "This card is already reserved.",
  "Diese Karte ist in Magic Arena nicht mehr verfuegbar.": "This card is no longer available in Magic Arena.",
  "Der Sprintpfad ist ungueltig.": "The sprint path is invalid.",
  "Stosslauf braucht Richtung und Distanz 1-3.": "Charge needs a direction and a distance from 1 to 3.",
  "Das Ziel liegt ausser Reichweite.": "The target is out of range.",
  "Teleport braucht ein freies, intaktes Feld.": "Teleport needs a free, intact tile.",
  "Diese Falle braucht ein freies Feld ohne Objekt.": "This trap needs a free tile with no object.",
  "Gewitterwolke braucht ein freies Ereignisfeld.": "Storm Cloud needs a tile with no active event.",
  "Krake braucht eine Figur im Wasser.": "Kraken needs a figure in the water.",
  "Bombe braucht ein Ziel auf der Karte.": "Bomb needs a target on the board.",
  "Hammerstoss im eigenen Feld braucht eine Schubrichtung.": "Hammer Blow in your own tile needs a push direction.",
  "Feldzerstoerung": "Tile destruction",
  "Sprengfeld": "Explosive tile",
  "Rissfeld": "Cracked tile",
  "Duennen Boden": "Thin floor",
  "Ein Kristall flackert auf, findet aber kein freies Feld.": "A crystal flickers into view, but there is no free tile.",
  "Boxen": "Punch",
  "Graben": "Dig",
  "vergrabene Bombe": "buried bomb",
  "Stosslauf trifft nur eigene Figuren und stoppt.": "Charge hits only friendly figures and stops.",
  "Gegner-Tausch scheitert: Ziel ausser Reichweite.": "Enemy Swap failed: target out of range.",
  "Gegner-Tausch findet keinen Gegner.": "Enemy Swap found no enemy.",
  "Hammerstoss scheitert: Ziel ausser Reichweite.": "Hammer Blow failed: target out of range.",
  "Bombe kann nicht gelegt werden.": "The bomb cannot be placed.",
  "Meteorschlag scheitert: Ziel ausser Reichweite.": "Meteor Strike failed: target out of range.",
  "Magnetzug scheitert: Ziel ausser Reichweite.": "Magnetic Pull failed: target out of range.",
  "Magnetzug findet kein gueltiges Ziel.": "Magnetic Pull found no valid target.",
  "Magnetzug": "Magnetic Pull",
  "Magnetzug wird von einem vollen Feld blockiert.": "Magnetic Pull is blocked by a full tile.",
  "Kettenblitz scheitert: Ziel ausser Reichweite.": "Chain Lightning failed: target out of range.",
  "Kettenblitz findet kein erstes Ziel.": "Chain Lightning found no initial target.",
  "Fallgrube kann hier nicht platziert werden.": "A pitfall cannot be placed here.",
  "Teleport scheitert: Ziel muss frei, intakt und in Reichweite sein.": "Teleport failed: the target must be free, intact and within range.",
  "Krake scheitert: Ziel ausser Reichweite.": "Kraken failed: target out of range.",
  "Krake findet keine Figur im Wasser.": "Kraken found no figure in the water.",
  "Zeitriss kann hier nicht markiert werden.": "Time Rift cannot mark this tile.",
  "Magische Mine kann hier nicht platziert werden.": "A Magic Mine cannot be placed here.",
  "Gewitterwolke kann hier nicht markiert werden.": "Storm Cloud cannot mark this tile.",
  "Platztausch findet keine zweite eigene Figur.": "Swap found no second friendly figure.",
  "Platztausch": "Swap",
  "Blitzschlag scheitert: Ziel ausser Reichweite.": "Lightning Strike failed: target out of range.",
  "Feuerball scheitert: Ziel ausser Reichweite.": "Fireball failed: target out of range.",
  "Riss scheitert: Ziel muss ein existierendes Feld in Reichweite sein.": "Falló la falla: el objetivo debe ser una casilla existente dentro del alcance.",
  "Plattformbruch scheitert: Ziel ausser Reichweite.": "Platform Break failed: target out of range.",
  "Plattformbruch scheitert: Feld ist noch zu stabil.": "Platform Break failed: the tile is still too strong.",
  "Unbekannte Karte verpufft.": "The unknown card fizzles.",
  "Basisaktion": "Basic action",
  "Karte": "Card",
  "Die geplante Aktion verfaellt: ausfuehrende Figur kann nicht handeln.": "The planned action is cancelled: its figure cannot act.",
  "Slotende": "End of slot",
  "Flutwelle": "Tidal Wave",
  "Eine Seite der Arena wird markiert. Am Rundenende bekommen diese Randfelder 2 Feldschaden.": "One side of the arena is marked. At round end, those edge tiles take 2 terrain damage.",
  "Instabile Arena": "Unstable Arena",
  "Am Rundenende bekommen alle Felder mit 2 oder weniger HP 1 Feldschaden.": "At round end, all tiles with 2 HP or less take 1 terrain damage.",
  "Erdbebenwarnung": "Earthquake Warning",
  "Sechs markierte Felder bekommen am Rundenende jeweils 2 Feldschaden.": "Six marked tiles each take 2 terrain damage at round end.",
  "Arkane Ueberladung": "Arcane Overload",
  "Direkter Feldschaden von Karten verursacht auf dem Hauptziel +1 Feldschaden.": "Direct terrain damage from cards deals +1 terrain damage to the main target.",
  "Jeder Feldschaden hat zusaetzlich 10% Chance auf 1 weiteren Feldschaden.": "Each instance of terrain damage has a 10% chance to deal 1 additional terrain damage.",
  "Schatzduft": "Treasure Scent",
  "Graben ist staerker: Nichts wird zu 1 Karte, kleine Truhen ziehen 2 Karten.": "Digging is stronger: finding nothing grants 1 card; small chests grant 2 cards.",
  "Flutwelle trifft die markierten Randfelder.": "Tidal Wave hits the marked edge tiles.",
  "Instabile Arena laesst beschaedigte Felder nachgeben.": "Unstable Arena makes damaged tiles collapse.",
  "Erdbebenwarnung trifft die markierten Felder.": "Earthquake Warning hits the marked tiles.",
  "Die Arena zieht sich stark zusammen": "The arena shrinks rapidly",
  "Die Arena zieht sich zusammen": "The arena shrinks",
  "Arenarand": "Arena edge",
  "Rettungsfrist": "Rescue deadline",
  "Magic Arena endet ohne Sieger.": "Magic Arena ends with no winner.",
  "Der Zug ist unvollstaendig.": "The turn is incomplete.",
  "Magic Arena Sieg": "Magic Arena victory"
};

const patterns: Array<[string, string, number[]?]> = [
  [
    "Rundenereignis: $1",
    "Round event: $1",
    [
      1
    ]
  ],
  [
    "Ronda $1: Comienza la fase de planificación.",
    "Round $1: planning begins."
  ],
  [
    "Ronda $1: Comienza la fase paso a paso.",
    "Round $1: turn-by-turn play begins."
  ],
  [
    "Es el turno de $1.",
    "It is $1’s turn."
  ],
  [
    "$1 wurde eliminiert ($2).",
    "Se eliminó $1 ($2).",
    [
      2
    ]
  ],
  [
    "$1 faellt ins Wasser ($2).",
    "$1 falls into the water ($2).",
    [
      2
    ]
  ],
  [
    "$1 erhaelt $2 Schaden ($3).",
    "$1 takes $2 damage ($3).",
    [
      3
    ]
  ],
  [
    "Feld $1 wurde zerstoert ($2).",
    "La baldosa $1 fue destruida ($2).",
    [
      2
    ]
  ],
  [
    "Sprengfeld $1 explodiert.",
    "Explosive tile $1 explodes."
  ],
  [
    "Feld $1 erhaelt $2 Schaden ($3).",
    "Tile $1 takes $2 damage ($3).",
    [
      3
    ]
  ],
  [
    "Rissfeld $1 bricht spontan.",
    "Cracked tile $1 suddenly collapses."
  ],
  [
    "El terreno delgado a 1 dólar continúa cediendo.",
    "The thin floor at $1 gives way further."
  ],
  [
    "$1 intenta ahorrar $2. Pero no hay campo de rescate.",
    "$1 tries to rescue $2, but there is no safe landing tile."
  ],
  [
    "$1 ahorra $2 después de $3.",
    "$1 rescues $2 to $3."
  ],
  [
    "Se activa la trampa de 1 dólar.",
    "Pitfall at $1 is triggered."
  ],
  [
    "Magic Mine a $1 desencadena ($2).",
    "Magic Mine at $1 is triggered ($2).",
    [
      2
    ]
  ],
  [
    "Crystal Field $1: $2 roba 1 carta.",
    "Crystal tile $1: $2 draws 1 card."
  ],
  [
    "El campo de cristal $1 se ilumina, pero la mano está llena.",
    "Crystal tile $1 lights up, but the hand is full."
  ],
  [
    "Aparece un campo de cristal en $1.",
    "A crystal tile appears at $1."
  ],
  [
    "La nube de tormenta en $1 se encuentra con $2 ($3).",
    "Storm Cloud at $1 hits $2 ($3).",
    [
      3
    ]
  ],
  [
    "Kettenschub-Limit erreicht ($1).",
    "Chain-push limit reached ($1).",
    [
      1
    ]
  ],
  [
    "Empuje de cadena: 1 dólar se empuja más.",
    "Chain push: $1 is pushed further."
  ],
  [
    "No se puede empujar $1: cadena bloqueada.",
    "$1 cannot be pushed: the chain is blocked."
  ],
  [
    "1 dólar se convierte en 2 dólares.",
    "$1 is pushed to $2."
  ],
  [
    "$1 no puede ejecutar $2.",
    "$1 cannot perform $2.",
    [
      2
    ]
  ],
  [
    "$1 se detiene antes de un campo completo.",
    "$1 stops before a full tile."
  ],
  [
    "1$ se mueve a 2$.",
    "$1 moves to $2."
  ],
  [
    "1 dólar intercambia lugares con 2 dólares ($3).",
    "$1 swaps places with $2 ($3).",
    [
      3
    ]
  ],
  [
    "$1 boxt ins Leere.",
    "$1 punches thin air."
  ],
  [
    "$1 no puede cavar aquí.",
    "$1 cannot dig here."
  ],
  [
    "$1 no encontró nada.",
    "$1 found nothing."
  ],
  [
    "$1 encontrado $2 tarjeta$3.",
    "$1 found $2 cards."
  ],
  [
    "$1 encontró un cofre, pero tiene la mano llena.",
    "$1 found a chest, but the hand is full."
  ],
  [
    "$1 encuentra $2 tarjeta$3.",
    "$1 finds $2 cards."
  ],
  [
    "$1 encontró una bomba enterrada.",
    "$1 found a buried bomb."
  ],
  [
    "1 dólar encuentra una bomba enterrada.",
    "$1 finds a buried bomb."
  ],
  [
    "$1 ha encontrado un terreno inestable.",
    "$1 found unstable ground."
  ],
  [
    "$1 legt instabilen Boden frei.",
    "$1 uncovers unstable ground."
  ],
  [
    "$1 encontró 1 tarjeta a través del aroma del tesoro.",
    "$1 found 1 card thanks to Treasure Scent."
  ],
  [
    "$1 encuentra 1 tarjeta a través del aroma del tesoro.",
    "$1 finds 1 card thanks to Treasure Scent."
  ],
  [
    "$1 findet nichts.",
    "$1 finds nothing."
  ],
  [
    "$1 finaliza la ejecución push en $2.",
    "$1 finishes Charge at $2."
  ],
  [
    "$1 coloca una bomba sobre $2.",
    "$1 places a bomb at $2."
  ],
  [
    "$1 se usa después de $2.",
    "$1 is pulled to $2."
  ],
  [
    "$1 entregado por Tarnung.",
    "$1 loses stealth."
  ],
  [
    "Chain Lightning salta a $1.",
    "Chain Lightning arcs to $1."
  ],
  [
    "1 dólar pone en peligro a 2 dólares.",
    "$1 places a pitfall at $2."
  ],
  [
    "Teletransporte de 1 dólar a 2 dólares.",
    "$1 teleports to $2."
  ],
  [
    "Octopus mueve $1 de $2 a $3.",
    "Kraken pulls $1 from $2 to $3."
  ],
  [
    "Time Rift marcado en $1.",
    "Time Rift marked at $1."
  ],
  [
    "Mina mágica colocada a 1 dólar.",
    "Magic Mine placed at $1."
  ],
  [
    "Nube de tormenta marcada a $1.",
    "Storm Cloud marked at $1."
  ],
  [
    "Se recibirá 1 dólar.",
    "$1 is hidden."
  ],
  [
    "$1 wirkt $2",
    "$1 casts $2",
    [
      2
    ]
  ],
  [
    "$1 wendet $2 por",
    "$1 uses $2",
    [
      2
    ]
  ],
  [
    "$ 1 y $ 2 o $ 3.",
    "$1 uses $2 at $3.",
    [
      2
    ]
  ],
  [
    "$1 nutzt $2.",
    "$1 uses $2.",
    [
      2
    ]
  ],
  [
    "1 dólar ya no está disponible y se desperdicia.",
    "$1 is no longer in the hand and fizzles.",
    [
      1
    ]
  ],
  [
    "$1 Bombe$2 explodieren",
    "$1 bombs explode"
  ],
  [
    "Bomba por valor de 1 dólar explotada.",
    "Bomb at $1 explodes."
  ],
  [
    "Se abre Time Rift a $1",
    "Time Rift opens at $1"
  ],
  [
    "Se activa Time Rift a $1.",
    "Time Rift at $1 is triggered."
  ],
  [
    "$1 Gewitterwolke$2 treffen Figuren",
    "$1 storm clouds strike figures"
  ],
  [
    "$1 sin pagar",
    "$1 is triggered",
    [
      1
    ]
  ],
  [
    "Rundenereignis: $1.",
    "Round event: $1.",
    [
      1
    ]
  ],
  [
    "$1 endet.",
    "$1 ends.",
    [
      1
    ]
  ],
  [
    "El borde de la arena sufre un daño de campo de $1 en campos de $2.",
    "The arena edge takes $1 terrain damage on $2 tiles."
  ],
  [
    "Ronda $1: Comienza la ejecución.",
    "Round $1: execution begins."
  ],
  [
    "$1 gewinnt Magic Arena!",
    "$1 wins Magic Arena!"
  ],
  [
    "Ronda final de $1",
    "Round $1 ends"
  ],
  [
    "$1 plant Slot $2.",
    "$1 plans slot $2."
  ],
  [
    "Ahora no es el turno de $1.",
    "It is not $1’s turn."
  ],
  [
    "$1 todavía necesita espacios de planificación de $2.",
    "$1 still needs $2 planning slots."
  ],
  [
    "1 dólar está listo.",
    "$1 is ready."
  ]
];

const compiledPatterns = [...patterns].sort((left, right) => right[0].replace(/\$\d+/g, "").length - left[0].replace(/\$\d+/g, "").length).map(([source, translation, translatedSlots = []]) => {
  const slots: number[] = [];
  const escaped = source.split(/(\$\d+)/).map(part => {
    if (/^\$\d+$/.test(part)) { slots.push(Number(part.slice(1))); return "([\\s\\S]*?)"; }
    return part.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  }).join("");
  return { expression: new RegExp("^" + escaped + "$"), slots, translation, translatedSlots };
});

/** Translate generated display copy only; never technical IDs or player names. */
export function arenaText(value: string, language: string | undefined): string {
  if (language !== "en") return value;
  if (value.endsWith(", Arkane Ueberladung")) return arenaText(value.slice(0, -", Arkane Ueberladung".length), language) + ", Arcane Overload";
  const exact = englishText[value];
  if (exact) return exact;
  if (value.includes("\n")) return value.split("\n").map(line => arenaText(line, language)).join("\n");
  for (const pattern of compiledPatterns) {
    const match = pattern.expression.exec(value);
    if (!match) continue;
    return pattern.translation.replace(/\$(\d+)/g, (_token, number: string) => {
      const slot = Number(number);
      const capture = match[pattern.slots.indexOf(slot) + 1] ?? "";
      return pattern.translatedSlots.includes(slot) ? arenaText(capture, language) : capture;
    });
  }
  return value;
}
