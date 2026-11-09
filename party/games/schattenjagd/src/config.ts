/**
 * Zentrale Regel- und Balancing-Konstanten fuer Schattenjagd.
 *
 * Schattenjagd ist ein eigenstaendiges Verfolgungsspiel mit verdeckter Bewegung.
 * Karte, Namen, Rollen, Regelwerte und Grafik stammen vollstaendig aus diesem Repo.
 */

export const schattenjagdRoomSettingKeys = {
  shadowMode: "schattenjagd.shadowMode",
  shadowSeat: "schattenjagd.shadowSeat",
  turnTime: "schattenjagd.turnTime"
} as const;

/** Auswaehlbare Zugzeiten. "off" spielt komplett ohne Zeitdruck. */
export const turnTimeOptions = ["20", "30", "45", "60", "90", "off"] as const;

export type TurnTimeOption = (typeof turnTimeOptions)[number];

export function resolveTurnDurationMs(value: unknown): number | null {
  const raw = typeof value === "number" ? String(value) : String(value ?? "");

  if (raw === "off") {
    return null;
  }

  const seconds = Number.parseInt(raw, 10);

  if (!Number.isFinite(seconds) || seconds <= 0) {
    return schattenjagdConfig.defaultTurnSeconds * 1_000;
  }

  return Math.min(600, Math.max(10, seconds)) * 1_000;
}

export type ShadowAssignmentMode = "random" | "seat" | "ai";

export const shadowAssignmentModes: readonly ShadowAssignmentMode[] = ["random", "seat", "ai"];

export const schattenjagdSetupConfig = {
  shadowMode: {
    defaultValue: "random" as ShadowAssignmentMode
  },
  shadowSeat: {
    min: 1,
    max: 12,
    step: 1,
    defaultValue: 1
  },
  turnTime: {
    defaultValue: "45" as TurnTimeOption
  }
} as const;

export const transportModes = ["taxi", "bus", "metro"] as const;

export type TransportMode = (typeof transportModes)[number];

export const schattenjagdConfig = {
  /** Anzahl Spielzuege bis zur gelungenen Flucht. */
  totalTurns: 22,
  /** Zuege, in denen die Position des Schattens oeffentlich aufgedeckt wird. */
  revealTurns: [3, 8, 13, 18, 22] as readonly number[],
  /** Standard-Zugzeit in Sekunden, wenn die Lobby nichts anderes vorgibt. */
  defaultTurnSeconds: 45,
  /** Bedenkzeit der KI, damit der Host-Screen lesbar bleibt. */
  aiThinkMs: 1_600,
  /** Startkontingent der Ermittler. */
  detectiveTickets: {
    taxi: 12,
    bus: 9,
    metro: 5
  } as Record<TransportMode, number>,
  /** Sonderzuege des Schattens. */
  shadowShrouds: 2,
  shadowDoubleMoves: 2,
  /** Kartengroesse. */
  stationCount: 64,
  /** Breites Seitenverhaeltnis, damit die Karte den Host-Screen fuellt. */
  mapWidth: 1000,
  mapHeight: 500,
  /** Wunschabstand (Hops) zwischen Schatten und Ermittlern zu Spielbeginn. */
  minimumStartDistance: 3,
  /** Ruhige, gut verfolgbare Host-Animationen entlang der echten Strecken. */
  travelAnimation: {
    pixelsPerSecond: {
      taxi: 135,
      bus: 165,
      metro: 195
    } as Record<TransportMode, number>,
    minMs: 1_800,
    maxMs: 4_200
  },
  score: {
    detectiveWin: 2,
    detectiveCaptureBonus: 1,
    shadowEscape: 4,
    shadowSurvivalBonus: 0
  }
} as const;

export function isRevealTurn(turn: number): boolean {
  return schattenjagdConfig.revealTurns.includes(turn);
}

export function nextRevealTurn(turn: number): number | null {
  return schattenjagdConfig.revealTurns.find((entry) => entry >= turn) ?? null;
}
