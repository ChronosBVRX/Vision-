import { imposterText } from "./catalogText.js";
import type { GameManifest } from "@open-party-lab/game-core";

const rules = {
  de: [
    { title: "Roles y pistas", lines: ["Una persona al azar es el Infiltrado. Solo el resto del equipo ve la palabra secreta en su celular.", "Por turnos digan una pista relacionada, sin decir la palabra. Con confirmación activada, cada pista se valida antes de seguir.", "El Infiltrado puede recibir una pista relacionada, pero nunca la palabra exacta."] },
    { title: "Voto y victoria", lines: ["Todos votan en secreto desde el celular por otra persona. Un voto por jugador y nadie puede votar por sí mismo.", "El resultado aparece cuando todos votaron. Un empate o una sospecha equivocada dejan ganar al Infiltrado.", "Si lo descubren, el Infiltrado tiene 30 segundos y una palabra libre para adivinar. Si acierta, roba la victoria."] },
    { title: "Puntos y siguiente ronda", lines: ["Victoria del Infiltrado: +2 puntos. Victoria del equipo: +1 punto por cada miembro que lo cazó.", "Los puntos son solo de esta partida. Todos se marcan listos para la siguiente palabra.", "La foto es opcional. Quien entre tarde juega desde la siguiente palabra."] }
  ],
  en: [
    { title: "Roles & clues", lines: ["One random player is the Infiltrator. Only the rest of the team sees the secret word on their phone.", "Take turns giving one related clue without saying the word. With confirmation on, each clue is checked before the next.", "The Infiltrator may receive a related hint, but never the exact word."] },
    { title: "Voting & victory", lines: ["Everyone secretly votes for another player on their phone. One vote each; no self-votes.", "Results appear after all votes. A tie or a wrong suspect lets the Infiltrator win.", "If exposed, the Infiltrator gets 30 seconds and one free-text guess. A correct guess steals the win."] },
    { title: "Points & next round", lines: ["Infiltrator victory: +2 points. Team victory: +1 point for each member who caught them.", "Points belong only to this game. Everyone readies up for the next word.", "Player photos are optional. Late joiners play from the next word."] }
  ]
} as const;

export const imposterManifest = {
  id: "imposter",
  translations: imposterText,
  displayName: "El Infiltrado",
  description: "Una palabra secreta, un topo y demasiadas caras de sospecha. Encuentra al Infiltrado antes de que te sabotea.",
  minPlayers: 3,
  maxPlayers: 20,
  hostView: "ImposterHostScene",
  controllerView: "imposter",
  controllerLayout: "social_party",
  supportsTeams: false,
  scoreScope: "game",
  lobbySetup: {
    title: "Prepara la infiltración",
    fields: [
      { kind: "select", id: "category", settingKey: "imposterWordCategory", label: "Palabras", defaultValue: "standard",
        options: [
          { id: "standard", label: "Familiar", description: "Palabras para toda la mesa.", rules },
          { id: "adult", label: "18+", description: "Sexo, erotismo, groserías, relaciones y vida nocturna.", rules },
          { id: "all", label: "Todo mezclado", description: "Mezcla familiar y picante.", rules }
        ] },
      { kind: "toggle", id: "hint", settingKey: "imposterGiveHint", label: "Dar una pista",
        description: "El Infiltrado recibe una pista relacionada, por ejemplo condón → sexo.", onValue: "on", offValue: "off", defaultValue: "off" },
      { kind: "toggle", id: "confirmClues", settingKey: "imposterConfirmClues", label: "Confirmar cada pista",
        description: "Apagado: discutan libremente y voten directo. Encendido: dos rondas de pistas con confirmación después de cada una.", onValue: "on", offValue: "off", defaultValue: "on" }
    ]
  },
  estimatedRoundDurationMs: 120_000,
  roundCompletionMode: "wait_for_ready",
  phaseDurations: {
    roundIntroMs: 1_600,
    countdownMs: 2_200,
    resultMs: 6_000,
    scoreboardMs: 5_000
  },

  ownsScreens: ["round_intro", "result"],
  hostChrome: { hud: false, roomCode: false },
  controllerChrome: { bare: true, hideScore: true },
  visual: { accent: "#8a7aa8", icon: "mask", eyebrow: "Infiltración" },
  audio: { track: { profile: "mystery", bpm: 98, rootMidi: 46, masterGain: 0.11 } },
} as const satisfies GameManifest;

export const manifest = imposterManifest;

