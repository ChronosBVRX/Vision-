import type { LocalizedGameTextMap } from "@open-party-lab/game-core";
import type { TransportMode } from "./config.js";

export type SchattenjagdLanguage = "de" | "en";

export interface SchattenjagdText {
  title: string;
  shadowRole: string;
  detectiveRole: string;
  spectatorRole: string;
  aiShadowName: string;
  shadowUnknown: string;
  modeLabels: Record<TransportMode, string>;
  ticketLabel: string;
  stationLabel: string;
  turnLabel: string;
  stageShadow: string;
  stageDetective: string;
  stageCaught: string;
  stageEscaped: string;
  preparing: string;
  shadowTurnHint: string;
  shadowWaitHint: string;
  detectiveTurnHint: string;
  detectiveWaitHint: string;
  activeDetective: (name: string) => string;
  shadowMovedPublic: (mode: string) => string;
  shadowMovedShrouded: string;
  shadowSurfaced: (station: number) => string;
  detectiveProgress: (moved: number, total: number) => string;
  detectivesTurn: string;
  shadowTurnStart: (turn: number, total: number) => string;
  caughtMessage: (detective: string, station: number) => string;
  escapedMessage: string;
  shadowStuck: string;
  noMoveLeft: string;
  timeoutShadow: string;
  timeoutDetectives: string;
  shroudArmed: string;
  shroudDisarmed: string;
  doubleArmed: string;
  doubleDisarmed: string;
  shroudLabel: string;
  doubleLabel: string;
  shroudDescription: string;
  doubleDescription: string;
  shroudActiveNote: string;
  doubleActiveNote: string;
  doubleSecondStep: string;
  nextReveal: (turn: number) => string;
  noReveal: string;
  lastSeen: (station: number, turn: number) => string;
  neverSeen: string;
  travelLogTitle: string;
  ticketsTitle: string;
  scoreShadowEscape: string;
  scoreDetectiveWin: string;
  scoreCapture: string;
  waitingForState: string;
  moveDescription: (mode: string, ticketsLeft: number) => string;
  moveDescriptionUnlimited: (mode: string) => string;
  blockedOccupied: string;
  blockedNoTicket: string;
  hunt: string;
  escapeIn: (turns: number) => string;
  roleAssignedShadow: string;
  roleAssignedDetective: string;
  aiShadowActive: string;
  yourStation: string;
  yourColor: string;
  shadowLabel: string;
  specialsLabel: string;
  sightingLabel: string;
  positionLine: (station: number) => string;
}

const es: SchattenjagdText = {
  title: "Sombra fugitiva",
  shadowRole: "tú eres la Sombra",
  detectiveRole: "eres detective",
  spectatorRole: "estás mirando",
  aiShadowName: "La Sombra (IA)",
  shadowUnknown: "la Sombra",
  modeLabels: { taxi: "Taxi", bus: "Bus", metro: "Metro" },
  ticketLabel: "Boletos",
  stationLabel: "Estación",
  turnLabel: "Turno",
  stageShadow: "la Sombra se mueve",
  stageDetective: "Los detectives se mueven",
  stageCaught: "Sombra acorralada",
  stageEscaped: "La Sombra escapó",
  preparing: "Preparando el mapa de la ciudad...",
  shadowTurnHint: "Elige tu próxima estación. Solo se revela el tipo de transporte.",
  shadowWaitHint: "La Sombra está planeando su movimiento.",
  detectiveTurnHint: "Elige tu próxima estación.",
  detectiveWaitHint: "Espera a los demás detectives.",
  activeDetective: (name) => `${name}, es tu turno`,
  shadowMovedPublic: (mode) => `La Sombra viajó en ${mode}.`,
  shadowMovedShrouded: "La Sombra cubrió sus huellas.",
  shadowSurfaced: (station) => `La Sombra fue vista en la estación ${station}.`,
  detectiveProgress: (moved, total) => `Movimientos de detectives: ${moved}/${total}.`,
  detectivesTurn: "Les toca a los detectives.",
  shadowTurnStart: (turn, total) => `Turno ${turn} de ${total}. Le toca a la Sombra.`,
  caughtMessage: (detective, station) => `${detective} acorrala a la Sombra en la estación ${station}.`,
  escapedMessage: "La Sombra sobrevivió a todos los movimientos y escapó.",
  shadowStuck: "La Sombra está acorralada y ya no puede moverse.",
  noMoveLeft: "No hay conexión disponible.",
  timeoutShadow: "Se acabó el tiempo. La Sombra se mueve automáticamente.",
  timeoutDetectives: "Se acabó el tiempo. Quien no se movió se queda donde está.",
  shroudArmed: "Velo preparado.",
  shroudDisarmed: "Velo guardado.",
  doubleArmed: "Doble movimiento preparado.",
  doubleDisarmed: "Doble movimiento guardado.",
  shroudLabel: "Velo",
  doubleLabel: "Doble movimiento",
  shroudDescription: "Oculta el tipo de transporte de tu siguiente movimiento.",
  doubleDescription: "Te permite moverte dos veces antes de que reaccionen los detectives.",
  shroudActiveNote: "El Velo está activo.",
  doubleActiveNote: "El doble movimiento está activo.",
  doubleSecondStep: "Segunda parte del doble movimiento.",
  nextReveal: (turn) => `Próximo avistamiento en el turno ${turn}.`,
  noReveal: "No hay más avistamientos programados.",
  lastSeen: (station, turn) => `Visto por última vez: estación ${station} (turno ${turn}).`,
  neverSeen: "Todavía no hay avistamientos.",
  travelLogTitle: "Registro de viaje",
  ticketsTitle: "Detectives",
  scoreShadowEscape: "Escape exitoso",
  scoreDetectiveWin: "Sombra acorralada",
  scoreCapture: "Captura",
  waitingForState: "Esperando el estado de la partida.",
  moveDescription: (mode, ticketsLeft) => `${mode} · te quedan ${ticketsLeft} boletos`,
  moveDescriptionUnlimited: (mode) => `${mode} · sin límite`,
  blockedOccupied: "La estación está ocupada.",
  blockedNoTicket: "No te quedan boletos.",
  hunt: "La cacería comenzó.",
  escapeIn: (turns) => `Faltan ${turns} turnos para la fuga.`,
  roleAssignedShadow: "Viajas oculto. No te delates.",
  roleAssignedDetective: "Encuentra a la Sombra antes de que se acaben los turnos.",
  aiShadowActive: "La Sombra está controlada por el servidor.",
  yourStation: "tu estación",
  yourColor: "Tu color de jugador",
  shadowLabel: "Sombra",
  specialsLabel: "Movimientos especiales",
  sightingLabel: "Avistamiento",
  positionLine: (station) => `estás en la estación ${station}`
};

export const schattenjagdText: Record<SchattenjagdLanguage, SchattenjagdText> = { de: es, en: es };

export function getSchattenjagdText(language?: string): SchattenjagdText {
  return language === "en" ? schattenjagdText.en : schattenjagdText.de;
}


export const schattenjagdCatalogText = {
  "de": {
    "displayName": "Sombra fugitiva",
    "description": "Una sombra se oculta y se mueve por la ciudad. Los detectives deben cerrar el cerco antes de que escape.",
    "lobbySetup": {
      "title": "Prepara la fuga",
      "description": "Decide quién interpreta la sombra. Todos los jugadores restantes investigan juntos.",
      "fields": {
        "shadowMode": {
          "label": "sombra",
          "description": "Aleatorio, por asiento o completamente controlado por el servidor.",
          "options": {
            "random": {
              "label": "Jugador aleatorio",
              "description": "Un jugador será sorteado en secreto."
            },
            "seat": {
              "label": "Asiento fijo",
              "description": "Jugador con el número de asiento elegido."
            },
            "ai": {
              "label": "IA del servidor",
              "description": "Todos los jugadores investigan contra la IA."
            }
          }
        },
        "turnTime": {
          "label": "Duración del turno",
          "description": "Tiempo disponible para cada turno de la Sombra o de los detectives.",
          "options": {
            "20": {
              "label": "20 segundos",
              "description": "Ritmo muy rápido."
            },
            "30": {
              "label": "30 segundos",
              "description": "Ritmo ágil."
            },
            "45": {
              "label": "45 segundos",
              "description": "Equilibrado."
            },
            "60": {
              "label": "60 segundos",
              "description": "Relajado."
            },
            "90": {
              "label": "90 segundos",
              "description": "Mucho tiempo para pensar."
            },
            "off": {
              "label": "Sin límite de tiempo",
              "description": "Sin cuenta atrás: se espera a que cada jugador cierre su turno."
            }
          }
        },
        "shadowSeat": {
          "label": "Asiento de la Sombra",
          "description": "Solo se usa con asiento fijo. El asiento 1 es quien se unió primero."
        }
      }
    },
    "visual": {
      "eyebrow": "Fuga nocturna"
    }
  },
  "en": {
    "displayName": "Sombra fugitiva",
    "description": "A shadow hides and moves through the city. The detectives must close the net before it escapes.",
    "lobbySetup": {
      "title": "Sombra fugitiva setup",
      "description": "Choose who plays the shadow. Everyone else investigates together.",
      "fields": {
        "shadowMode": {
          "label": "Shadow",
          "description": "Choose randomly, by seat number, or let the server take control.",
          "options": {
            "random": {
              "label": "Random player",
              "description": "One player is chosen in secret."
            },
            "seat": {
              "label": "Fixed seat",
              "description": "The player with the selected seat number."
            },
            "ai": {
              "label": "Server AI",
              "description": "All players investigate against the AI."
            }
          }
        },
        "turnTime": {
          "label": "Turn duration",
          "description": "Time available for each shadow or investigator turn.",
          "options": {
            "20": {
              "label": "20 seconds",
              "description": "Very fast-paced."
            },
            "30": {
              "label": "30 seconds",
              "description": "Brisk."
            },
            "45": {
              "label": "45 seconds",
              "description": "Balanced."
            },
            "60": {
              "label": "60 seconds",
              "description": "Relaxed."
            },
            "90": {
              "label": "90 seconds",
              "description": "Plenty of thinking time."
            },
            "off": {
              "label": "No time limit",
              "description": "No countdown; every turn waits for its player."
            }
          }
        },
        "shadowSeat": {
          "label": "Shadow's seat",
          "description": "Only used with fixed-seat selection. Seat 1 is the first player who joined."
        }
      }
    },
    "visual": {
      "eyebrow": "Night escape"
    }
  }
} as const satisfies Partial<LocalizedGameTextMap>;
