import type { ControllerLayoutKey } from "@open-party-lab/game-core";
import { banterboltManifest } from "../manifest.js";
import type { BanterboltControllerState } from "../protocol.js";

interface ControllerContext {
  state: {
    preferredLanguage?: "de" | "en" | "es";
    getServerTime?: () => number;
    room?: {
      language?: "de" | "en" | "es";
      players?: Array<{ id: string; name: string; isReady?: boolean; connected?: boolean }>;
    } | null;
    player?: { id: string; isReady?: boolean } | null;
    game?: { phase?: string; roundNumber?: number; state?: unknown } | null;
  };
  onInput(input: unknown): void;
  onSetReady?: (ready: boolean) => void;
}

function labelFor(stage: string, isWriter: boolean, hasVoted: boolean): { status: string; helper: string } {
  switch (stage) {
    case "write":
      return {
        status: "Escribe tus respuestas",
        helper: "Tienes dos prompts. La gracia manda; no hay respuesta incorrecta.",
      };
    case "vote":
      if (isWriter) {
        return {
          status: "Te toca mirar",
          helper: "Escribiste una de estas respuestas. Disfruta el show desde la tele.",
        };
      }
      return hasVoted
        ? { status: "Voto registrado", helper: "Puedes cambiar tu voto hasta que voten todos." }
        : { status: "¿Cuál estuvo mejor?", helper: "Elige A o B en la tele. No puedes votar por la tuya." };
    case "reveal":
      return { status: "Se reveló la verdad", helper: "Mira la tele: ahí salen los votos y los puntos." };
    case "scoreboard":
      return { status: "Puntos parciales", helper: "Ve la tabla en la tele. Ya viene la siguiente ronda." };
    case "finale_write":
      return { status: "Ronda Relámpago", helper: "Todos responden al mismo prompt. Esta es la última." };
    case "finale_vote":
      return hasVoted
        ? { status: "Voto registrado", helper: "Espera a que voten los demás." }
        : { status: "Vota por la mejor", helper: "Elige una respuesta que no sea la tuya." };
    case "finale_reveal":
      return { status: "Resultados finales", helper: "Mira el podio en la tele." };
    case "finished":
      return { status: "Fin del juego", helper: "¿Otra ronda? Márquense listos." };
    default:
      return { status: "Prepárate", helper: "La partida está por empezar." };
  }
}

export const controllerGame = {
  id: banterboltManifest.id,
  layoutKey: "social_party" as ControllerLayoutKey,
  buildLayout({ state, onInput, onSetReady }: ControllerContext) {
    const game = (state.game?.state ?? null) as BanterboltControllerState | null;
    const language = state.room?.language ?? state.preferredLanguage ?? "es";
    const playerId = state.player?.id ?? "";
    const players = state.room?.players ?? [];
    const connected = players.filter((player) => player.connected !== false);
    const own = game?.players.find((player) => player.id === playerId);
    const isWriter = Boolean(game?.isWriter);
    const hasVoted = game?.ownVote !== undefined || Boolean(game?.ownFinaleVote);
    const labels = labelFor(game?.stage ?? "waiting", isWriter, hasVoted);
    const playing = state.game?.phase === "playing";

    const send = (input: Record<string, unknown>) => {
      if (!playerId || !game?.roundId) return;
      onInput({ ...input, playerId, roundId: game.roundId, sentAt: Date.now() });
    };

    const ready = state.game?.phase === "finished" && onSetReady
      ? {
          currentPlayerReady: Boolean(connected.find((player) => player.id === playerId)?.isReady ?? state.player?.isReady),
          readyCount: connected.filter((player) => player.isReady).length,
          playerCount: connected.length,
          label: "Listo para otra",
          description: "Cuando todos estén listos, empieza otra partida.",
          language,
          onToggleReady: () => onSetReady(!Boolean(connected.find((player) => player.id === playerId)?.isReady ?? state.player?.isReady)),
        }
      : undefined;

    const writeStage = game?.stage === "write";
    const finaleWrite = game?.stage === "finale_write";
    const voteStage = game?.stage === "vote" && !isWriter;
    const finaleVote = game?.stage === "finale_vote";

    const choices = voteStage
      ? [
          { id: "0", label: "A" },
          { id: "1", label: "B" },
        ]
      : finaleVote && game?.finale?.options
        ? game.finale.options
            .filter((answer) => answer.playerId !== playerId)
            .map((answer) => ({
              id: answer.playerId,
              label: answer.text.length > 60 ? `${answer.text.slice(0, 57)}…` : answer.text,
            }))
        : [];

    return {
      kind: "social_party" as const,
      title: "Banterbolt",
      language,
      stage: finaleWrite ? "submit" as const
        : voteStage || finaleVote ? "vote" as const
        : game?.stage === "finished" ? "finished" as const
        : "waiting" as const,
      taskKind: "text" as const,
      statusLabel: labels.status,
      helperText: labels.helper,
      score: own?.points ?? 0,
      deadline: game?.finishAt ?? undefined,
      getServerTime: state.getServerTime,
      resetKey: `${game?.roundId ?? ""}:${game?.stage ?? ""}:${game?.matchupIndex ?? 0}:${game?.finale?.promptText ?? ""}`,
      hasSubmitted: Boolean(game?.ownVote !== undefined || game?.ownFinaleVote || (game?.ownSubmitted && writeStage)),
      disabled: !playing || game?.stage === "finished",
      selectedId: game?.ownVote !== undefined ? String(game.ownVote) : game?.ownFinaleVote,
      choices,
      privateTextList: writeStage && game
        ? {
            values: game.ownPrompts.map((prompt) => game.ownAnswers[prompt.index] ?? ""),
            count: game.ownPrompts.length,
            maxLength: 120,
            submitted: game.ownSubmitted,
            onSave: (values: string[], submit: boolean) => send({ type: "submit_answers", values, submit }),
          }
        : undefined,
      onSubmitText: (text: string) => send({ type: "finale_submit", text }),
      onVote: (id: string) => {
        if (voteStage) send({ type: "vote", answerIndex: id === "1" ? 1 : 0 });
        else if (finaleVote) send({ type: "finale_vote", targetPlayerId: id });
      },
      actions: [],
      ready,
      onSubmitPick: () => {},
      onSubmitAvatar: () => {},
      onSubmitMedia: () => {},
    };
  },
} as const;
