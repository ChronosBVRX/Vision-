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

const rulesEn = [
  {
    title: "How to play",
    lines: [
      "Everyone gets two secret prompts and writes one answer for each. There are no wrong answers: funny wins.",
      "When everyone is done, answers are paired and voted on blind. You cannot vote for your own.",
      "Each vote is worth 100 points. If every voter picks the same answer, that is a ZAP! and +50 extra.",
      "After the regular rounds comes the Lightning Round: everyone answers the same prompt and votes for the best.",
    ],
  },
  {
    title: "Points and next round",
    lines: [
      "Points belong to this game, not the room total.",
      "When the game ends, everyone readies up on their phone to play again.",
      "The host can change the prompt pack and timers in the room setup.",
    ],
  },
];

const rulesDe = [
  {
    title: "So wird gespielt",
    lines: [
      "Alle bekommen zwei geheime Prompts und schreiben je eine Antwort. Es gibt keine falsche Antwort: lustig gewinnt.",
      "Danach werden die Antworten gepaart und blind abgestimmt. Für die eigene Antwort darf nicht gestimmt werden.",
      "Jede Stimme zählt 100 Punkte. Stimmen alle für dieselbe Antwort, gibt es einen ZAP! und +50.",
      "Nach den regulären Runden folgt die Blitzrunde: alle beantworten denselben Prompt und stimmen für die beste Antwort.",
    ],
  },
  {
    title: "Punkte und nächste Runde",
    lines: [
      "Die Punkte gelten nur für dieses Spiel, nicht für die Raumwertung.",
      "Am Ende melden sich alle auf dem Handy bereit für eine neue Runde.",
      "Der Host kann Prompt-Paket und Zeiten in den Raumeinstellungen ändern.",
    ],
  },
];

export const banterboltText = {
  es: {
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
            afterdark: { label: "After dark (18+)", description: "Pícante, no explícito. Para grupos que ya se conocen." },
            mixto: { label: "Mixto", description: "Un poco de todo." },
          },
        },
        rounds: { label: "Rondas regulares", description: "Cuántas rondas antes de la Ronda Relámpago." },
        writeSeconds: { label: "Segundos para escribir", description: "Tiempo para cada tanda de respuestas." },
        voteSeconds: { label: "Segundos para votar", description: "Tiempo para cada enfrentamiento." },
        lightning: { label: "Ronda Relámpago", description: "Cierre todos contra todos con un solo prompt." },
      },
    },
  },
} as const;
