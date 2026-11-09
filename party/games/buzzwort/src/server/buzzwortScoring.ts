import type { ScoreEntry } from "@open-party-lab/game-core";
import type { BuzzwortMode, BuzzwortTeamId } from "../protocol.js";

interface BuildBuzzwortScoreEntriesParams {
  mode: BuzzwortMode;
  scoreByPlayerId: Record<string, number>;
  scoreByTeamId: Record<BuzzwortTeamId, number>;
  teamMembersByTeamId: Record<BuzzwortTeamId, string[]>;
}

export function buildBuzzwortScoreEntries(params: BuildBuzzwortScoreEntriesParams): ScoreEntry[] {
  if (params.mode === "team") {
    return (["team1", "team2"] as const).flatMap((teamId) => {
      const score = params.scoreByTeamId[teamId] ?? 0;

      if (score <= 0) {
        return [];
      }

      return (params.teamMembersByTeamId[teamId] ?? []).map((playerId) => ({
        playerId,
        delta: score,
        reason: `Buzzwort Team ${teamId === "team1" ? 1 : 2}`
      }));
    });
  }

  return Object.entries(params.scoreByPlayerId)
    .filter(([, score]) => score > 0)
    .map(([playerId, score]) => ({
      playerId,
      delta: score,
      reason: "Puntos de palabras de moda"
    }));
}
