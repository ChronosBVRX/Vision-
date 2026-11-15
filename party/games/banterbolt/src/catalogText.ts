import type { LocalizedGameTextMap } from "@open-party-lab/game-core";

const rulesEs = [
  {
    title: "Cómo se juega",
    lines: [
      "Cada persona recibe dos prompts secretos y escribe una respuesta para cada uno. No hay respuesta incorrecta: la gracia manda.",
      "Cuando todos terminan, las respuestas se emparejan y se votan a ciegas. No puedes votar por la tuya.",
      "Cada voto vale 100 puntos. Si todos los votantes eligen la misma respuesta, hay ¡ZAP! y +50 extra.",
      "Después de las rondas regulares viene la Ronda Relámpago: todos responden al mismo prompt y se vota a la mejor.",
    ],
  },
  {
    title: "Puntos y siguiente ronda",
    lines: [
      "Los puntos son de esta partida, no del acumulado de la sala.",
      "Al terminar, todos se marcan listos en el celular para jugar otra vez.",
      "El anfitrión puede cambiar el paquete de prompts y los tiempos en la configuración de la sala.",
    ],
  },
];

const spanishBanterboltText = {
  displayName: "Banterbolt",
  description:
    "Escribe respuestas irreverentes, vota a ciegas y sobrevive a la Ronda Relámpago. El juego de ocurrencias de Vision+.",
  visual: { eyebrow: "Ocurrencias" },
  lobbySetup: {
    title: "Configura Banterbolt",
    description: "Elige el paquete de prompts, los tiempos y la ronda final.",
    fields: {
      pack: {
        label: "Paquete de prompts",
        description: "El tono de las respuestas cambia con el paquete.",
        options: {
          mexico: { label: "México irreverente", description: "Familia, tacos, tráfico y telenovelas." },
          cine: { label: "Cine y series", description: "Secuelas, finales y decisiones de la industria." },
          afterdark: { label: "After dark (18+)", description: "Picante, no explícito. Para grupos que ya se conocen." },
          mixto: { label: "Mixto", description: "Un poco de todo." },
        },
      },
      rounds: { label: "Rondas regulares", description: "Cuántas rondas antes de la Ronda Relámpago." },
      writeSeconds: { label: "Segundos para escribir", description: "Tiempo para cada tanda de respuestas." },
      voteSeconds: { label: "Segundos para votar", description: "Tiempo para cada enfrentamiento." },
      lightning: { label: "Ronda Relámpago", description: "Cierre todos contra todos con un solo prompt." },
    },
  },
} as const;

export const banterboltText = {
  de: spanishBanterboltText,
  en: spanishBanterboltText,
  es: spanishBanterboltText,
} as const satisfies Partial<LocalizedGameTextMap>;
