import type { DeckDefinition } from "./cardTypes.js";
const englishLabels: Record<string, string> = {
  "Französisches Blatt (52)": "French deck (52)",
  "Französisches Blatt + 2 Joker (54)": "French deck + 2 jokers (54)",
  "Doppeldeck + 4 Joker (108)": "Double deck + 4 jokers (108)",
  "Romméblatt (108)": "Rummy deck (108)",
  "Deutsches Blatt (32)": "German deck (32)",
  "Party-Deck (40)": "Party deck (40)",
  "Stichwette-Blatt (60)": "Trick Bets deck (60)",
  "Zahlenblatt 1-20 (80)": "Number deck 1–20 (80)",
  "Peter-Blatt (49)": "Old Maid deck (49)",
  "Doppelkopf-Blatt (48)": "Doppelkopf deck (48)",
  "Doppelkopf ohne Neunen (40)": "Doppelkopf without nines (40)",
  "Pik": "Spades",
  "Herz": "Hearts",
  "Karo": "Diamonds",
  "Kreuz": "Clubs",
  "Eichel": "Acorns",
  "Grün": "Leaves",
  "Schellen": "Bells",
  "Sonne": "Sun",
  "Welle": "Wave",
  "Blatt": "Leaf",
  "Funke": "Spark",
  "Rot": "Red",
  "Gelb": "Yellow",
  "Blau": "Blue",
  "Flamme": "Flame",
  "Stern": "Star",
  "Hain": "Grove",
  "Mond": "Moon",
  "Symbole": "Symbols"
};
const spanishLabels: Record<string, string> = {
  "Franz\u00f6sisches Blatt (52)": "Baraja francesa (52)",
  "Franz\u00f6sisches Blatt + 2 Joker (54)": "Baraja francesa + 2 comodines (54)",
  "Doppeldeck + 4 Joker (108)": "Doble baraja + 4 comodines (108)",
  "Romm\u00e9blatt (108)": "Baraja de rummy (108)",
  "Deutsches Blatt (32)": "Baraja alemana (32)",
  "Party-Deck (40)": "Baraja de fiesta (40)",
  "Stichwette-Blatt (60)": "Baraja de apuestas (60)",
  "Zahlenblatt 1-20 (80)": "Baraja num\u00e9rica 1-20 (80)",
  "Peter-Blatt (49)": "Baraja del tonto (49)",
  "Doppelkopf-Blatt (48)": "Baraja de Doppelkopf (48)",
  "Doppelkopf ohne Neunen (40)": "Doppelkopf sin nueves (40)",
  "Pik": "Picas",
  "Herz": "Corazones",
  "Karo": "Diamantes",
  "Kreuz": "Tr\u00e9boles",
  "Eichel": "Bellotas",
  "Gr\u00fcn": "Hojas",
  "Schellen": "Campanas",
  "Sonne": "Sol",
  "Welle": "Ola",
  "Blatt": "Hoja",
  "Funke": "Chispa",
  "Rot": "Rojo",
  "Gelb": "Amarillo",
  "Blau": "Azul",
  "Flamme": "Llama",
  "Stern": "Estrella",
  "Hain": "Arboleda",
  "Mond": "Luna",
  "Symbole": "S\u00edmbolos",
};

export function localizeDeck(deck: DeckDefinition, language: string): DeckDefinition {
 if (language !== "en" && language !== "es") return deck;
 const labels = language === "es" ? spanishLabels : englishLabels;
 const label = (value: string) => labels[value] ?? value.replace(/^Symboljagd-Bildkarten /, language === "es" ? "Cartas de Symboljagd" : "Symbol Hunt picture cards ");
 const green = language === "es" ? "Verde" : "Green";
 const crown = language === "es" ? "C" : "CROWN";
 const feather = language === "es" ? "PLUMA" : "FEATHER";
 const oldmaid = language === "es" ? "TONTA" : "OLD MAID";
 return { ...deck, label: label(deck.label), suits: deck.suits.map(suit => ({ ...suit, label: suit.id === "gruen" && deck.id !== "skat-32" ? green : label(suit.label) })), ranks: deck.ranks.map(rank => ({ ...rank, label: rank.id === "jack" ? "J" : rank.id === "queen" ? "Q" : rank.id === "crown" ? "C" : rank.label, ...(rank.centerLabel ? { centerLabel: rank.centerLabel === "KRONE" ? crown : rank.centerLabel === "FEDER" ? feather : rank.centerLabel === "PETER" ? oldmaid : rank.centerLabel } : {}) })) };
}
