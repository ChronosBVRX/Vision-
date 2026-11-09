import type { GameManifest } from "@open-party-lab/game-core";
import { youGotItText } from "./catalogText.js";

const rules = {
  es: [
    {
      title: "Cómo se juega",
      lines: [
        "Dos equipos: Bajos y Agudos. En cada turno, un jugador es el Oráculo y ve un objetivo secreto en el espectro.",
        "El Oráculo da una pista corta. Su equipo mueve el dial para acercarse al objetivo.",
        "Cerca del centro: 4 puntos; luego 3, 2 o 0 según la distancia. El equipo rival puede hacer Undercut para robar 1 punto.",
        "Si aciertan el centro y van abajo, el mismo equipo juega otra vez. Gana el primero en llegar a los puntos objetivo.",
      ],
    },
  ],
} as const;

export const youGotItManifest = {
  id: "you-got-it",
  translations: youGotItText,
  displayName: "¿Le atinaste?",
  description:
    "Una pista, un dial y el cerebro del Oráculo. Adivina el espectro y roba puntos con el Undercut.",
  minPlayers: 4,
  maxPlayers: 12,
  hostView: "YouGotItHost",
  controllerView: "you-got-it",
  controllerLayout: "wavelength",
  supportsTeams: true,
  scoreScope: "game",
  estimatedRoundDurationMs: 180_000,
  roundCompletionMode: "wait_for_ready",
  phaseDurations: {
    roundIntroMs: 1_800,
    countdownMs: 2_200,
    resultMs: 7_000,
    scoreboardMs: 7_000,
  },
  lobbySetup: {
    title: "Configura ¿Le atinaste?",
    description: "Puntos, robo y tiempo de adivinanza.",
    fields: [
      {
        kind: "select",
        id: "targetScore",
        settingKey: "youGotItTargetScore",
        label: "Puntos para ganar",
        defaultValue: "10",
        options: [
          { id: "7", label: "7 — Ronda rápida", description: "Una partida corta para calentar.", rules },
          { id: "10", label: "10 — Estándar", description: "El ritmo recomendado.", rules },
          { id: "15", label: "15 — Maratón", description: "Para grupos que no se quieren ir.", rules },
        ],
      },
      {
        kind: "toggle",
        id: "steal",
        settingKey: "youGotItSteal",
        label: "Undercut",
        description: "El equipo rival puede robar un punto si adivina hacia dónde falló el dial.",
        onValue: "on",
        offValue: "off",
        defaultValue: "on",
      },
      {
        kind: "number",
        id: "guessSeconds",
        settingKey: "youGotItGuessSeconds",
        label: "Tiempo para adivinar",
        min: 30,
        max: 150,
        step: 15,
        defaultValue: 90,
      },
      {
        kind: "select",
        id: "pack",
        settingKey: "youGotItPack",
        label: "Paquete de espectros",
        defaultValue: "core",
        options: [
          { id: "core", label: "Espectros base", description: "Cine, comida, familia y vida diaria.", rules },
          { id: "afterdark", label: "After dark (18+)", description: "Pícaro, no explícito.", rules },
        ],
      },
    ],
  },
  ownsScreens: ["round_intro", "result"],
  hostChrome: { hud: false, roomCode: false },
  controllerChrome: { bare: true, hideScore: true },
  visual: { accent: "#2D7DF6", icon: "sparkles", eyebrow: "Espectro" },
  audio: { track: { profile: "mystery", bpm: 96, rootMidi: 48, masterGain: 0.1 } },
} as const satisfies GameManifest;

export const manifest = youGotItManifest;
