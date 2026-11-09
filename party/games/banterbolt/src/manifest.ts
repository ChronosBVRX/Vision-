import type { GameManifest } from "@open-party-lab/game-core";
import { banterboltText } from "./catalogText.js";

const rules = {
  es: [
    {
      title: "Cómo se juega",
      lines: [
        "Cada persona recibe dos prompts secretos y escribe una respuesta para cada uno.",
        "Las respuestas se emparejan y se votan a ciegas; no puedes votar por la tuya.",
        "Cada voto vale 100 puntos y una barrida unánime da ¡ZAP! +50.",
        "Cierra con la Ronda Relámpago: todos responden lo mismo y se vota a la mejor.",
      ],
    },
  ],
} as const;

export const banterboltManifest = {
  id: "banterbolt",
  translations: banterboltText,
  displayName: "Banterbolt",
  description:
    "Escribe respuestas irreverentes, vota a ciegas y sobrevive a la Ronda Relámpago.",
  minPlayers: 3,
  maxPlayers: 12,
  hostView: "BanterboltHost",
  controllerView: "banterbolt",
  controllerLayout: "social_party",
  supportsTeams: false,
  scoreScope: "game",
  estimatedRoundDurationMs: 180_000,
  roundCompletionMode: "wait_for_ready",
  phaseDurations: {
    roundIntroMs: 1_800,
    countdownMs: 2_200,
    resultMs: 8_000,
    scoreboardMs: 7_000,
  },
  lobbySetup: {
    title: "Configura Banterbolt",
    description: "Paquete de prompts, tiempos y ronda final.",
    fields: [
      {
        kind: "select",
        id: "pack",
        settingKey: "banterboltPack",
        label: "Paquete de prompts",
        defaultValue: "mexico",
        options: [
          { id: "mexico", label: "México irreverente", description: "Familia, tacos, tráfico y telenovelas.", rules },
          { id: "cine", label: "Cine y series", description: "Secuelas, finales y decisiones de la industria.", rules },
          { id: "afterdark", label: "After dark (18+)", description: "Pícante, no explícito. Para grupos que ya se conocen.", rules },
          { id: "mixto", label: "Mixto", description: "Un poco de todo.", rules },
        ],
      },
      {
        kind: "number",
        id: "rounds",
        settingKey: "banterboltRounds",
        label: "Rondas regulares",
        min: 1,
        max: 3,
        step: 1,
        defaultValue: 2,
      },
      {
        kind: "number",
        id: "writeSeconds",
        settingKey: "banterboltWriteSeconds",
        label: "Segundos para escribir",
        min: 30,
        max: 180,
        step: 15,
        defaultValue: 90,
      },
      {
        kind: "number",
        id: "voteSeconds",
        settingKey: "banterboltVoteSeconds",
        label: "Segundos para votar",
        min: 10,
        max: 60,
        step: 5,
        defaultValue: 25,
      },
      {
        kind: "toggle",
        id: "lightning",
        settingKey: "banterboltLightning",
        label: "Ronda Relámpago",
        description: "Cierre todos contra todos con un solo prompt.",
        onValue: "on",
        offValue: "off",
        defaultValue: "on",
      },
    ],
  },
  ownsScreens: ["round_intro", "result"],
  hostChrome: { hud: false, roomCode: false },
  controllerChrome: { bare: true, hideScore: true },
  visual: { accent: "#2D7DF6", icon: "bolt", eyebrow: "Ocurrencias" },
  audio: { track: { profile: "battle", bpm: 112, rootMidi: 52, masterGain: 0.1 } },
} as const satisfies GameManifest;

export const manifest = banterboltManifest;
