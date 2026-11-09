import { cardTableText } from "./catalogText.js";
import type { GameManifest } from "@open-party-lab/game-core";
import { resolveCardRuleset } from "./rules/index.js";
import { resolveCardDeck } from "./cards/deckPresets.js";

function selectionRules(id: string) {
  const ruleset = resolveCardRuleset(id);
  const rules = (language: "de" | "en") => ruleset.rules({
    language, deck: resolveCardDeck(ruleset.defaultDeckId), now: 0,
    playerNames: {}, playerColors: {}, scores: {}, settings: {}, previousExtra: {}
  });
  return { de: rules("de"), en: rules("en") };
}

export const cardTableRoomSettingKeys = {
  ruleset: "cardTableRuleset",
  deck: "cardTableDeck",
  handSize: "cardTableHandSize",
  cardStyle: "cardTableCardStyle",
  botCount: "cardTableBotCount",
  doppelkopfScoring: "cardTableDoppelkopfScoring",
  handSort: "cardTableHandSort",
  dimUnplayable: "cardTableDimUnplayable",
  mauStackSevens: "cardTableMauStackSevens",
  mauSkipEights: "cardTableMauSkipEights",
  mauReverseNines: "cardTableMauReverseNines",
  mauJackOnJack: "cardTableMauJackOnJack",
  mauPlayDrawn: "cardTableMauPlayDrawn",
  mauPlayPenaltyDrawn: "cardTableMauPlayPenaltyDrawn",
  dokoNines: "cardTableDokoNines",
  dokoSecondDulle: "cardTableDokoSecondDulle",
  dokoDoppelkopf: "cardTableDokoDoppelkopf",
  dokoFox: "cardTableDokoFox",
  dokoCharlie: "cardTableDokoCharlie",
  dokoFoxEnd: "cardTableDokoFoxEnd",
  dokoPigs: "cardTableDokoPigs",
  dokoAgainstOld: "cardTableDokoAgainstOld",
  dokoBock: "cardTableDokoBock",
  dokoForced: "cardTableDokoForced",
  symboljagdSymbolsPerCard: "cardTableSymboljagdSymbolsPerCard",
  symboljagdDealMode: "cardTableSymboljagdDealMode",
  symboljagdCardTarget: "cardTableSymboljagdCardTarget"
} as const;

export const cardTableManifest = {
  id: "card-table",
  translations: cardTableText,
  displayName: "Baraja brava",
    description:
      "Doce juegos de cartas, una mesa compartida y tu mano privada en el celular. Juega fino, roba o miente.",
  // Eine Person reicht: Fehlende Plätze füllt der Tisch mit KI-Sitzen auf.
  minPlayers: 1,
  // Die Lobby kann bis zu 16 Personen aufnehmen; das gewählte Regelwerk setzt
  // die tatsächliche Obergrenze (z. B. vier bei Doppelkopf).
  maxPlayers: 16,
  maxPlayersBySetting: {
    settingKey: cardTableRoomSettingKeys.ruleset,
    values: {
      "mau-mau": 6,
      schwimmen: 6,
      stichwette: 6,
      zahlenreihe: 6,
      luegen: 6,
      "schwarzer-peter": 6,
      fischen: 6,
      herzeln: 6,
      doppelkopf: 4,
      romme: 6,
      symboljagd: 16,
      "free-play": 6
    }
  },
  hostView: "CardTableHostScene",
  controllerView: "card-table",
  controllerLayout: "card_hand",
  supportsTeams: false,
  estimatedRoundDurationMs: 600_000,
  roundCompletionMode: "wait_for_ready",
  scoreScope: "game",
  lobbySetup: {
    title: "Prepara la baraja",
    description: "Elige reglas, mazo y cartas de mano. Dependiendo de las reglas, pueden jugar hasta 6 personas, hasta 16 en Symbol Hunt y hasta 4 en Doppelkopf.",
    fields: [
      {
        kind: "select",
        id: "ruleset",
        settingKey: cardTableRoomSettingKeys.ruleset,
        actionKey: "ruleset",
        label: "Reglas y regulaciones",
        defaultValue: "mau-mau",
        options: [
          {
            id: "mau-mau",
            label: "mau mau",
            description: "Siete dibuja dos, ocho salta, nueve gira, jack quiere un color."
          },
          {
            id: "schwimmen",
            label: "Natación (31)",
            description: "Tres cartas, tres cartas de mesa boca arriba, intercambia y golpea. Tres vidas por persona."
          },
          {
            id: "stichwette",
            label: "apuesta con truco",
            description: "Anuncia trucos y golpéalos exactamente. Mano propia con coronas y plumas, aumentando el número de cartas por ronda."
          },
          {
            id: "zahlenreihe",
            label: "Serie numérica",
            description: "Cuatro filas de colores del 1 al 20, cada una de las cuales se abre con el once. Hoja de números propia."
          },
          {
            id: "luegen",
            label: "mentiras",
            description: "Colóquelo boca abajo y anuncie el valor requerido. El que duda y se equivoca se lleva el montón."
          },
          {
            id: "schwarzer-peter",
            label: "Pedro negro",
            description: "Descarte los pares y muévete en la casa del vecino. Pierde quien se queda con la tarjeta sin pareja."
          },
          {
            id: "fischen",
            label: "pesca",
            description: "Pregunta a otros jugadores un valor y colecciona cuartetos. Apto para familias."
          },
          {
            id: "herzeln",
            label: "Herzeln",
            description: "Evite trucos: Cada corazón cuenta como un punto de penalización, la dama de espadas cuenta como trece."
          },
          {
            id: "doppelkopf",
            label: "doble kopf",
            description: "Para cuatro personas: Diamantes, Reinas, Jotas y Diez de Corazones son triunfos, las Reinas de Tréboles forman Re."
          },
          {
            id: "romme",
            label: "rummy",
            description: "Reúne frases y secuencias, dispóngalas a partir de 40 puntos, póntelas y vacía tu mano con comodines."
          },
          {
            id: "symboljagd",
            label: "Caza de símbolos",
            description: "Busque el símbolo de imagen común entre su tarjeta y el centro. Solo y sin IA, estás jugando contra el tiempo. Un consejo incorrecto hará que te suspendan por un corto tiempo.",
          },
          {
            id: "free-play",
            label: "juego libre",
            description: "Mesa abierta sin reglas: se puede descartar cualquier carta."
          }
        ].map((option) => ({ ...option, rules: selectionRules(option.id) }))
      },
      {
        kind: "select",
        id: "symboljagdSymbolsPerCard",
        settingKey: cardTableRoomSettingKeys.symboljagdSymbolsPerCard,
        actionKey: "symboljagdSymbolsPerCard",
        label: "Símbolos por tarjeta",
        description: "Más motivaciones son que la coincidencia marítima es más difícil. Las cartas del juego son únicas por su significado matemático.",
        visibleWhen: { field: cardTableRoomSettingKeys.ruleset, anyOf: ["symboljagd"] },
        defaultValue: "8",
        options: [
          { id: "8", label: "8 símbolos · Estándar", description: "57 motivos y 57 cartas." },
          { id: "9", label: "9 símbolos · Sofisticado", description: "73 motivos y 73 cartas." },
          { id: "10", label: "10 símbolos · Profesional", description: "91 motivos y 91 cartas." }
        ]
      },
      {
        kind: "select",
        id: "symboljagdDealMode",
        settingKey: cardTableRoomSettingKeys.symboljagdDealMode,
        actionKey: "symboljagdDealMode",
        label: "Uso de tarjeta",
        visibleWhen: { field: cardTableRoomSettingKeys.ruleset, anyOf: ["symboljagd"] },
        defaultValue: "all",
        options: [
          { id: "all", label: "Todas las tarjetas", description: "Distribuya todo el juego de cartas como antes." },
          { id: "fixed", label: "Número fijo por persona", description: "Todos juegan con el mismo número objetivo. Las cartas viejas se vuelven a mezclar." }
        ]
      },
      {
        kind: "number",
        id: "symboljagdCardTarget",
        settingKey: cardTableRoomSettingKeys.symboljagdCardTarget,
        actionKey: "symboljagdCardTarget",
        label: "Entradas por persona",
        description: "Número objetivo con carga de tarjeta fija; Para Todas las Tarjetas, se aplica el conjunto completo de tarjetas.",
        visibleWhen: { field: cardTableRoomSettingKeys.ruleset, anyOf: ["symboljagd"] },
        min: 1, max: 300, step: 1, defaultValue: 30
      },
      {
        kind: "select",
        id: "deck",
        settingKey: cardTableRoomSettingKeys.deck,
        actionKey: "deck",
        label: "baraja de cartas",
        description: "Hoja para este conjunto de reglas.",
        // Regelwerke mit festem Blatt bekommen die Auswahl gar nicht erst zu
        // sehen - sie hätte dort keine Wirkung.
        visibleWhen: {
          field: cardTableRoomSettingKeys.ruleset,
          anyOf: ["mau-mau", "schwimmen", "luegen", "fischen", "free-play"]
        },
        defaultValue: "french-52",
        options: [
          { id: "french-52", label: "francés (52)", description: "Espadas, corazones, diamantes, tréboles del 2 al as." },
          { id: "french-54", label: "Francés + Joker (54)", description: "Como arriba, más dos comodines." },
          { id: "skat-32", label: "periódico alemán (32)", description: "Bellota, verde, corazón, campanas del 7 al as." },
          { id: "party-40", label: "Baraja de fiesta (40)", description: "Mazo de ejemplo definido libremente con tus propios símbolos." },
          { id: "french-104", label: "Doble piso (108)", description: "Dos manos francesas y cuatro comodines." }
        ]
      },
      {
        kind: "number",
        id: "handSize",
        settingKey: cardTableRoomSettingKeys.handSize,
        actionKey: "handSize",
        label: "Tarjetas de mano",
        description: "Cartas por jugador cuando se reparten.",
        // Nur dort sichtbar, wo die Zahl wirklich frei ist: Schwimmen spielt
        // immer mit drei, die Stichwette steigert selbst, und die übrigen
        // Regelwerke teilen das ganze Blatt aus.
        visibleWhen: {
          field: cardTableRoomSettingKeys.ruleset,
          anyOf: ["mau-mau", "luegen", "fischen", "free-play"]
        },
        min: 3,
        max: 12,
        step: 1,
        defaultValue: 5
      },
      {
        kind: "toggle",
        id: "dimUnplayable",
        settingKey: cardTableRoomSettingKeys.dimUnplayable,
        actionKey: "dimUnplayable",
        label: "Atenuar las cartas injugables",
        description: "Destaca las tarjetas coincidentes en el móvil. Cuando está apagado, todas las tarjetas permanecen visibles con normalidad; las reglas del juego siguen vigentes.",
        onValue: "on",
        offValue: "off",
        defaultValue: "on"
      },
      ...([
        ["mauStackSevens", "Siebenen stapeln", "Un siete pasa las tarjetas de penalización acumuladas al siguiente jugador."],
        ["mauSkipEights", "Ocho se sienta", "Después de un ocho, se salta al siguiente jugador."],
        ["mauReverseNines", "Dirección de nueve vueltas", "Después de un nueve cambia el sentido del juego. En parejas, el otro jugador se sienta."],
        ["mauJackOnJack", "Jack sobre Jack", "Las jotas y comodines también se pueden colocar encima de otras jotas o comodines."],
        ["mauPlayDrawn", "Coloca la carta robada inmediatamente.", "Durante el sorteo normal, la carta extraída se puede colocar inmediatamente. La opción separada se aplica a las multas."],
        ["mauPlayPenaltyDrawn", "Acuéstese inmediatamente después del castigo.", "Después de +2, +4, etc., se puede colocar una carta adecuada de las cartas de penalización recién extraídas. Las cartas antiguas permanecen bloqueadas."]
      ] as const).map(([id, label, description]) => ({
        kind: "toggle" as const,
        id,
        settingKey: cardTableRoomSettingKeys[id],
        actionKey: id,
        label,
        description,
        group: "Mau-Mau-Hausregeln",
        visibleWhen: { field: cardTableRoomSettingKeys.ruleset, anyOf: ["mau-mau"] },
        onValue: "on",
        offValue: "off",
        defaultValue: id === "mauPlayPenaltyDrawn" ? "off" : "on"
      })),
      {
        kind: "toggle",
        id: "handSort",
        settingKey: cardTableRoomSettingKeys.handSort,
        actionKey: "handSort",
        label: "Ordenar cartas de mano",
        description: "Organice la mano en su teléfono por triunfo y palo en lugar de en el orden en que se repartió.",
        visibleWhen: {
          field: cardTableRoomSettingKeys.ruleset,
          anyOf: ["doppelkopf", "herzeln", "stichwette", "romme"]
        },
        onValue: "auto",
        offValue: "dealt",
        defaultValue: "auto"
      },
      {
        kind: "number",
        id: "botCount",
        settingKey: cardTableRoomSettingKeys.botCount,
        actionKey: "botCount",
        label: "jugador de IA",
        description:
          "Jugadores virtuales que la mesa controla sola. Ocupan hasta seis plazas y juegan según las mismas reglas. Si un conjunto de reglas carece de ocupación mínima, la tabla se llena sola.",
        min: 0,
        max: 5,
        step: 1,
        defaultValue: 0
      },
      {
        kind: "toggle",
        id: "dokoNines",
        settingKey: cardTableRoomSettingKeys.dokoNines,
        actionKey: "dokoNines",
        label: "Sin nueves (40 cartas)",
        description: "El rey de diamantes se convierte en el triunfo más pequeño y las rondas se acortan.",
        group: "Doppelkopf-Hausregeln",
        visibleWhen: { field: cardTableRoomSettingKeys.ruleset, anyOf: ["doppelkopf"] },
        onValue: "without",
        offValue: "with",
        defaultValue: "with"
      },
      {
        kind: "toggle",
        id: "dokoSecondDulle",
        settingKey: cardTableRoomSettingKeys.dokoSecondDulle,
        actionKey: "dokoSecondDulle",
        label: "Segundas picaduras sordas",
        description: "Si ambas decenas de corazones caen en una baza, gana la segunda.",
        group: "Doppelkopf-Hausregeln",
        visibleWhen: { field: cardTableRoomSettingKeys.ruleset, anyOf: ["doppelkopf"] },
        onValue: "on",
        offValue: "off",
        defaultValue: "off"
      },
      {
        kind: "toggle",
        id: "dokoDoppelkopf",
        settingKey: cardTableRoomSettingKeys.dokoDoppelkopf,
        actionKey: "dokoDoppelkopf",
        label: "Doppelkopf (más de 40 ojos)",
        description: "Punto especial para un truco con 40 puntos o más.",
        group: "Doppelkopf-Hausregeln",
        visibleWhen: { field: cardTableRoomSettingKeys.ruleset, anyOf: ["doppelkopf"] },
        onValue: "on",
        offValue: "off",
        defaultValue: "on"
      },
      {
        kind: "toggle",
        id: "dokoFox",
        settingKey: cardTableRoomSettingKeys.dokoFox,
        actionKey: "dokoFox",
        label: "zorro atrapado",
        description: "Punto especial por un as de diamantes del lado contrario.",
        group: "Doppelkopf-Hausregeln",
        visibleWhen: { field: cardTableRoomSettingKeys.ruleset, anyOf: ["doppelkopf"] },
        onValue: "on",
        offValue: "off",
        defaultValue: "on"
      },
      {
        kind: "toggle",
        id: "dokoCharlie",
        settingKey: cardTableRoomSettingKeys.dokoCharlie,
        actionKey: "dokoCharlie",
        label: "Karlchen en el último",
        description: "Punto especial para la jota de tréboles en la última baza.",
        group: "Doppelkopf-Hausregeln",
        visibleWhen: { field: cardTableRoomSettingKeys.ruleset, anyOf: ["doppelkopf"] },
        onValue: "on",
        offValue: "off",
        defaultValue: "on"
      },
      {
        kind: "toggle",
        id: "dokoFoxEnd",
        settingKey: cardTableRoomSettingKeys.dokoFoxEnd,
        actionKey: "dokoFoxEnd",
        label: "zorro al final",
        description: "Punto especial por un as de diamantes en la última baza.",
        group: "Doppelkopf-Hausregeln",
        visibleWhen: { field: cardTableRoomSettingKeys.ruleset, anyOf: ["doppelkopf"] },
        onValue: "on",
        offValue: "off",
        defaultValue: "off"
      },
      {
        kind: "toggle",
        id: "dokoPigs",
        settingKey: cardTableRoomSettingKeys.dokoPigs,
        actionKey: "dokoPigs",
        label: "cerdito",
        description: "Ambos ases de diamantes una mano se convierten en los triunfos más altos.",
        group: "Doppelkopf-Hausregeln",
        visibleWhen: { field: cardTableRoomSettingKeys.ruleset, anyOf: ["doppelkopf"] },
        onValue: "on",
        offValue: "off",
        defaultValue: "off"
      },
      {
        kind: "toggle",
        id: "dokoAgainstOld",
        settingKey: cardTableRoomSettingKeys.dokoAgainstOld,
        actionKey: "dokoAgainstOld",
        label: "contra lo viejo",
        description: "Punto especial si gana Kontra.",
        group: "Doppelkopf-Hausregeln",
        visibleWhen: { field: cardTableRoomSettingKeys.ruleset, anyOf: ["doppelkopf"] },
        onValue: "on",
        offValue: "off",
        defaultValue: "on"
      },
      {
        kind: "toggle",
        id: "dokoBock",
        settingKey: cardTableRoomSettingKeys.dokoBock,
        actionKey: "dokoBock",
        label: "ronda de bock",
        description: "Si Re se pierde o el grupo es negro, la siguiente ronda cuenta doble.",
        group: "Doppelkopf-Hausregeln",
        visibleWhen: { field: cardTableRoomSettingKeys.ruleset, anyOf: ["doppelkopf"] },
        onValue: "on",
        offValue: "off",
        defaultValue: "off"
      },
      {
        kind: "toggle",
        id: "dokoForced",
        settingKey: cardTableRoomSettingKeys.dokoForced,
        actionKey: "dokoForced",
        label: "Anuncio obligatorio de 35 ojos.",
        description: "El ganador del primer truco revela su lado.",
        group: "Doppelkopf-Hausregeln",
        visibleWhen: { field: cardTableRoomSettingKeys.ruleset, anyOf: ["doppelkopf"] },
        onValue: "on",
        offValue: "off",
        defaultValue: "off"
      },
      {
        kind: "toggle",
        id: "doppelkopfScoring",
        settingKey: cardTableRoomSettingKeys.doppelkopfScoring,
        actionKey: "doppelkopfScoring",
        label: "Cuenta los ojos solo al final.",
        description: "No hay números durante la ronda; la liquidación se realiza al final.",
        group: "Doppelkopf-Hausregeln",
        visibleWhen: { field: cardTableRoomSettingKeys.ruleset, anyOf: ["doppelkopf"] },
        onValue: "end",
        offValue: "live",
        defaultValue: "live"
      },
      {
        kind: "select",
        id: "cardStyle",
        settingKey: cardTableRoomSettingKeys.cardStyle,
        actionKey: "cardStyle",
        label: "Imagen del mapa",
        defaultValue: "classic",
        options: [
          { id: "classic", label: "Clásico", description: "Hoja blanca con serifas, esquinas y pepitas." },
          { id: "modern", label: "Moderno", description: "Campo coloreado, número grande, fácil de leer." },
          { id: "clear", label: "seguro", description: "Muy reducido: número enorme, símbolo pequeño." }
        ]
      }
    ]
  },
  phaseDurations: {
    roundIntroMs: 1_500,
    countdownMs: 1_000,
    lockedMs: 2_000,
    resultMs: 5_000,
    scoreboardMs: 5_000
  },
  ownsScreens: ["round_intro", "result"],
  visual: { accent: "#8d5f4a", icon: "cards", eyebrow: "Baraja" },
  audio: { track: { profile: "gentle", bpm: 96, rootMidi: 53, masterGain: 0.11 } },
  controllerChrome: { bare: true }
} as const satisfies GameManifest;

export const manifest = cardTableManifest;
