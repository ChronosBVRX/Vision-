import type { GameManifest } from "@open-party-lab/game-core";
import { merkissionerText } from "./catalogText.js";

const rules = {
  es: [
    {
      title: "Roles y objetivo",
      lines: [
        "La mayoría son Merkizens; una minoría son Merkites; una persona es El Comisionado Merk, el jefe oculto.",
        "Los Merkizens ganan aprobando 5 decretos Merkizen o expulsando al jefe.",
        "Los Merkites ganan aprobando 6 decretos Merkite o logrando que el jefe sea elegido comisionado después de 3 decretos Merkite.",
      ],
    },
    {
      title: "Gobierno y votación",
      lines: [
        "La silla rota entre personas vivas. La persona en la silla nomina a un comisionado.",
        "Todos votan SÍ o NO. Si pasa, la silla recibe 3 decretos, descarta uno y el comisionado aprueba uno.",
        "Si el gobierno falla tres veces seguidas, hay anarquía y se aprueba el decreto de arriba del mazo.",
      ],
    },
    {
      title: "Poderes",
      lines: [
        "Cada decreto Merkite desbloquea un poder según el tablero: auditoría, elección anticipada, vistazo al mazo o expulsión.",
        "El veto se desbloquea con el quinto decreto Merkite.",
        "Nadie puede hablar de cartas, roles ni del mazo fuera de lo que su rol le permita saber.",
      ],
    },
  ],
} as const;

export const merkissionerManifest = {
  id: "merkissioner",
  translations: merkissionerText,
  displayName: "El Comisionado Merk",
  description:
    "Aprueben decretos, huelan a los Merkites y jamás le den la silla de comisionado al Comisionado Merk.",
  minPlayers: 5,
  maxPlayers: 12,
  hostView: "MerkissionerHost",
  controllerView: "merkissioner",
  controllerLayout: "social_party",
  supportsTeams: false,
  scoreScope: "game",
  estimatedRoundDurationMs: 900_000,
  roundCompletionMode: "wait_for_ready",
  phaseDurations: {
    roundIntroMs: 1_800,
    countdownMs: 2_200,
    resultMs: 8_000,
    scoreboardMs: 8_000,
  },
  lobbySetup: {
    title: "Configural Comisionado Merk",
    description: "Ritmo, temporizadores y transparencia.",
    fields: [
      {
        kind: "select",
        id: "pace",
        settingKey: "merkissionerPace",
        label: "Ritmo",
        defaultValue: "standard",
        options: [
          { id: "relaxed", label: "Relajado — 90 s", description: "Para grupos que discuten mucho.", rules },
          { id: "standard", label: "Estándar — 60 s", description: "El ritmo recomendado.", rules },
          { id: "speedy", label: "Rápido — 30 s", description: "Sin piedad.", rules },
        ],
      },
      {
        kind: "toggle",
        id: "timers",
        settingKey: "merkissionerTimers",
        label: "Temporizadores",
        description: "Resuelve fases estancadas con cuenta regresiva.",
        onValue: "on",
        offValue: "off",
        defaultValue: "on",
      },
      {
        kind: "toggle",
        id: "revealVotes",
        settingKey: "merkissionerRevealVotes",
        label: "Revelar votos",
        description: "Muestra el voto de cada persona al cerrar la votación.",
        onValue: "on",
        offValue: "off",
        defaultValue: "on",
      },
    ],
  },
  ownsScreens: ["round_intro", "result"],
  hostChrome: { hud: false, roomCode: false },
  controllerChrome: { bare: true, hideScore: true },
  visual: { accent: "#2D7DF6", icon: "mask", eyebrow: "Deducción" },
  audio: { track: { profile: "mystery", bpm: 88, rootMidi: 44, masterGain: 0.1 } },
} as const satisfies GameManifest;

export const manifest = merkissionerManifest;
