import type { LocalizedGameTextMap } from "@open-party-lab/game-core";

export const merkissionerText = {
  es: {
    displayName: "El Comisionado Merk",
    description:
      "Aprueben decretos, huelan a los Merkites y jamás —JAMÁS— le den la silla de comisionado a El Comisionado Merk.",
    visual: { eyebrow: "Deducción" },
    lobbySetup: {
      title: "Configura El Comisionado Merk",
      description: "Ritmo, temporizadores y transparencia del voto.",
      fields: {
        pace: {
          label: "Ritmo",
          options: {
            relaxed: { label: "Relajado — 90 s", description: "Para grupos que discuten mucho." },
            standard: { label: "Estándar — 60 s", description: "El ritmo recomendado." },
            speedy: { label: "Rápido — 30 s", description: "Sin piedad." },
          },
        },
        timers: { label: "Temporizadores", description: "Resuelve fases estancadas con cuenta regresiva." },
        revealVotes: { label: "Revelar votos", description: "Muestra el voto de cada persona al cerrar la votación." },
      },
    },
  },
} as const satisfies Partial<LocalizedGameTextMap>;
