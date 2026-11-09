import { pantomimeText } from "./catalogText.js";
import type { GameManifest } from "@open-party-lab/game-core";
const rules = {
  de: [
    { title: "Turno y mímica", lines: ["El actor revela el primer término y arranca el reloj (60 segundos por defecto).", "Solo gestos: nada de palabras, sonidos ni letras. El resto adivina en voz alta.", "Cada acierto suma; los saltos son limitados y no se recuperan."] },
    { title: "Puntos y equipos", lines: ["En todos contra todos, el actor y quien acierta ganan 1 punto por término confirmado.", "En equipos, solo el equipo activo adivina y cada acierto vale 1 punto para el equipo.", "Los equipos se alternan por orden de entrada; necesitas al menos 4 jugadores."] },
    { title: "Revisión y relevo", lines: ["Al terminar el tiempo, el actor confirma aciertos y puede corregir reclamaciones.", "Los términos saltados o pendientes también se pueden asignar durante la revisión.", "Solo el actor cierra el turno y pasa al siguiente. Los puntos son de esta partida."] }
  ],
  en: [
    { title: "Turn & charades", lines: ["The actor reveals the first word and starts the clock (60 seconds by default).", "Gestures only: no words, sounds or spelling. Everyone else guesses out loud.", "Every correct guess scores; skips are limited and do not refill."] },
    { title: "Points & teams", lines: ["In free-for-all, the actor and the correct guesser each get 1 point per confirmed word.", "In team mode, only the active team guesses and every correct word earns 1 team point.", "Teams alternate by join order; you need at least 4 players."] },
    { title: "Review & handover", lines: ["When time runs out, the actor confirms hits and can correct claims.", "Skipped or unfinished words can also be assigned during review.", "Only the actor closes the turn and hands over. Scores belong only to this game."] }
  ]
} as const;
export const pantomimeManifest = {
  id: "pantomime",
  translations: pantomimeText, displayName: "Mímica en llamas", description: "Puro teatro, cero palabras y vergüenza al rojo vivo. Actúa, adivina y sobrevive al incendio.",
  minPlayers: 2, maxPlayers: 20,
  minPlayersBySetting: { settingKey: "pantomimeMode", values: { team: 4 } },
  hostView: "PantomimeHostScene", controllerView: "pantomime", controllerLayout: "social_party",
  scoreScope: "game", controllerChrome: { bare: true, hideScore: true },
  hostChrome: { hud: false },
  lobbySetup: {
    title: "Prende la mímica",
    fields: [
      { kind: "select", id: "mode", settingKey: "pantomimeMode", label: "Modo de fuego", defaultValue: "solo",
        options: [
          { id: "solo", label: "Todos contra todos", description: "Todos, excepto el artista, adivinan y reclaman sus aciertos.", rules },
          { id: "team", label: "Equipos", description: "Dos equipos por orden de entrada. Sólo el equipo activo adivina. A partir de 4 personas.", rules }
        ] },
      { kind: "select", id: "category", settingKey: "pantomimeWordCategory", label: "Términos en llamas", defaultValue: "standard",
        options: [
          { id: "standard", label: "Familiar", description: "124 términos familiares.", rules },
          { id: "adult", label: "18+", description: "76 términos para adultos: sexo, relaciones y vida nocturna.", rules },
          { id: "all", label: "Todo mezclado", description: "Mezcla de términos familiares y picantes. Para grupos de adultos.", rules }
        ] },
      { kind: "number", id: "duration", settingKey: "pantomimeDurationSeconds", label: "Segundos por actor", min: 15, max: 300, step: 15, defaultValue: 60 },
      { kind: "number", id: "skips", settingKey: "pantomimeSkips", label: "Saltos por actor", min: 0, max: 10, step: 1, defaultValue: 3 }
    ]
  },
  supportsTeams: true, estimatedRoundDurationMs: 90_000,
  ownsScreens: ["round_intro", "result"],
  visual: { accent: "#9a6f8c", icon: "drama", eyebrow: "Teatro en llamas" },
  audio: { track: { profile: "gentle", bpm: 112, rootMidi: 59, masterGain: 0.12 } }
} as const satisfies GameManifest;
export const manifest = pantomimeManifest;
