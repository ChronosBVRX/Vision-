import type { LocalizedGameTextMap } from "@open-party-lab/game-core";

export const youGotItText = {
  es: {
    displayName: "¿Le atinaste?",
    description:
      "Una pista, un dial y el cerebro del Oráculo. Adivina dónde está el objetivo en el espectro y roba puntos con el Undercut.",
    visual: { eyebrow: "Espectro" },
    lobbySetup: {
      title: "Configura ¿Le atinaste?",
      description: "Puntos para ganar, robo y tiempo de adivinanza.",
      fields: {
        targetScore: {
          label: "Puntos para ganar",
          options: {
            "7": { label: "7 — Ronda rápida", description: "Una partida corta para calentar." },
            "10": { label: "10 — Estándar", description: "El ritmo recomendado." },
            "15": { label: "15 — Maratón", description: "Para grupos que no se quieren ir." },
          },
        },
        steal: {
          label: "Undercut",
          description: "El equipo rival puede robar un punto si adivina hacia dónde falló el dial.",
        },
        guessSeconds: {
          label: "Tiempo para adivinar",
          description: "Segundos que tiene el equipo del Oráculo para mover el dial.",
        },
        pack: {
          label: "Paquete de espectros",
          description: "El tono de las pistas cambia con el paquete.",
          options: {
            core: { label: "Espectros base", description: "Cine, comida, familia y vida diaria." },
            afterdark: { label: "After dark (18+)", description: "Pícaro, no explícito. Para grupos que ya se conocen." },
          },
        },
      },
    },
  },
} as const satisfies Partial<LocalizedGameTextMap>;
