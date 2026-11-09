import type { ControllerLayoutKey } from "@open-party-lab/game-core";
import { youGotItManifest } from "../manifest.js";
import type { YouGotItControllerState } from "../protocol.js";

interface ControllerContext {
  state: {
    preferredLanguage?: "de" | "en" | "es";
    room?: { language?: "de" | "en" | "es" } | null;
    player?: { id: string } | null;
    game?: { phase?: string; roundNumber?: number; state?: unknown } | null;
  };
  onInput(input: unknown): void;
}

function teamOf(teams: { bass: number[]; treble: number[] }, seat: number): "bass" | "treble" | null {
  if (teams.bass.includes(seat)) return "bass";
  if (teams.treble.includes(seat)) return "treble";
  return null;
}

export const controllerGame = {
  id: youGotItManifest.id,
  layoutKey: "wavelength" as ControllerLayoutKey,
  buildLayout({ state, onInput }: ControllerContext) {
    const game = (state.game?.state ?? null) as YouGotItControllerState | null;
    const playerId = state.player?.id ?? "";
    const seat = game?.seat ?? 0;
    const publicState = game as unknown as {
      teams: { bass: number[]; treble: number[] };
      teamScores: { bass: number; treble: number };
      targetScore: number;
      activeTeam: "bass" | "treble";
      oracleSeat: number;
      prompt: { left: string; right: string };
      clue: string | null;
      pointerAngle: number;
      readySeats: number[];
      stealVotes: { left: number; right: number };
      stealVotedSeats: number[];
      targetAngle?: number;
      winnerTeam?: "bass" | "treble";
      lastTurn?: unknown;
      phase?: string;
      matchOver?: boolean;
      seatPlayers?: Array<{ seat: number; id: string; name: string; avatar?: string }>;
    } | null;

    const phase = (game?.phase ?? "clue") as "clue" | "guess" | "steal" | "reveal" | "game_over";
    const privateState = (game?.privateState ?? {}) as { targetAngle?: number; undercutDir?: "left" | "right" };
    const teams = publicState?.teams ?? { bass: [], treble: [] };
    const activeTeam = publicState?.activeTeam ?? "bass";
    const myTeam = teamOf(teams, seat);
    const isOracle = publicState?.oracleSeat === seat;
    const isActiveTeam = myTeam === activeTeam;
    const isOpponent = myTeam !== null && myTeam !== activeTeam;
    const guessers = (teams[activeTeam] ?? []).filter((member) => member !== publicState?.oracleSeat);

    const send = (input: Record<string, unknown>) => {
      if (!playerId || !game?.roundId) return;
      onInput({ ...input, playerId, roundId: game.roundId, sentAt: Date.now() });
    };

    return {
      kind: "wavelength" as const,
      title: "¿Le atinaste?",
      phase: publicState?.matchOver ? "game_over" : phase,
      activeTeam,
      teamScores: publicState?.teamScores ?? { bass: 0, treble: 0 },
      targetScore: publicState?.targetScore ?? 10,
      prompt: publicState?.prompt ?? { left: "Menos", right: "Más" },
      clue: publicState?.clue ?? null,
      pointerAngle: publicState?.pointerAngle ?? 90,
      targetAngle: isOracle || phase === "reveal" || phase === "game_over"
        ? (privateState.targetAngle ?? publicState?.targetAngle ?? null)
        : null,
      isOracle,
      isActiveTeam,
      isOpponent,
      seat,
      seatPlayers: publicState?.seatPlayers ?? [],
      readyCount: publicState?.readySeats?.length ?? 0,
      guesserCount: guessers.length,
      stealVotes: publicState?.stealVotes ?? { left: 0, right: 0 },
      ownSteal: privateState.undercutDir ?? null,
      onClue: (text: string) => send({ type: "submit_clue", text }),
      onMove: (angle: number) => send({ type: "move_pointer", angle }),
      onLock: () => send({ type: "lock_pointer" }),
      onSteal: (dir: "left" | "right") => send({ type: "guess_direction", dir }),
      resetKey: `${game?.roundId ?? ""}:${publicState?.activeTeam ?? ""}:${phase}:${publicState?.pointerAngle ?? 0}`,
    };
  },
} as const;
