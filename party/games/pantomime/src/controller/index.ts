import type { ControllerLayoutKey } from "@open-party-lab/game-core";
import { pantomimeManifest } from "../manifest.js";
import type { PantomimeState } from "../protocol.js";
interface ControllerContext {
  state: { getServerTime?: () => number; preferredLanguage?: "de" | "en"; room?: { language?: "de" | "en" } | null;
    player?: { id: string } | null; game?: { phase?: string; state?: unknown } | null };
  onInput(input: unknown): void;
}
export const controllerGame = {
  id: pantomimeManifest.id, layoutKey: "social_party" as ControllerLayoutKey,
  buildLayout({ state, onInput }: ControllerContext) {
    const game = (state.game?.state ?? {}) as Partial<PantomimeState>;
    const language = state.room?.language ?? state.preferredLanguage ?? "de";
    const en = language === "en", id = state.player?.id ?? "";
    const actor = Boolean(id && id === game.actorPlayerId);
    const own = game.players?.find((p) => p.id === id);
    const eligible = Boolean(own && !actor && (game.mode !== "team" || own.teamId === game.actorTeamId));
    const playing = state.game?.phase === "playing";
    const acting = playing && game.stage === "acting";
    const reviewing = playing && game.stage === "review";
    const locked = acting && game.inputLockedUntil != null;
    const guessFeedback = locked && game.lastGuess?.playerId === id ? game.lastGuess.term : undefined;
    const send = (input: Record<string, unknown>) => {
      if (!playing || !id || !game.roundId || !own) return;
      onInput({ ...input, playerId: id, roundId: game.roundId, turnNumber: game.turnNumber, wordToken: game.wordToken, sentAt: Date.now() });
    };
    const teamLabel = game.actorTeamId === "team1" ? (en ? "Team 1" : "Equipo 1") : (en ? "Team 2" : "Equipo 2");
    return {
      kind: "social_party" as const, title: pantomimeManifest.translations[en ? "en" : "de"].displayName, language,
      stage: "waiting" as const, taskKind: "pick" as const,
      statusLabel: !playing ? (en ? "Get ready" : "Prepárense")
        : reviewing ? actor ? (en ? "Check the guesses" : "comprobar aciertos") : (en ? "Turn results" : "Revisión del turno")
        : game.stage === "ready" ? actor ? (en ? "Reveal to start" : "Revelar y comenzar") : (en ? `${game.actorName ?? "…"} is next` : `${game.actorName ?? "…"} es el siguiente`)
        : actor ? (en ? "Act this out!" : "¡Describe este término!")
        : eligible ? (en ? "Guess out loud!" : "¡Adivina en voz alta!") : (en ? "Watch this turn" : "Mira este turno"),
      helperText: reviewing ? actor
        ? (en ? "Remove incorrect claims or assign words to the right person. Continue when you are done." : "Quita aciertos incorrectos o asígnalos a la persona correcta. Pasa al siguiente cuando termines.")
        : (en ? `${game.actorName ?? "…"} checks the claims and controls the handover.` : `${game.actorName ?? "…"} revisa los aciertos y controla el relevo.`)
        : game.stage === "ready" ? actor
          ? (en ? `Your ${(game.durationMs ?? 60000) / 1000} seconds start only when you reveal the first word.` : `Tus ${(game.durationMs ?? 60000) / 1000} segundos empiezan cuando revelas el primer término.`)
          : (en ? "The clock has not started yet." : "El tiempo aún no corre.")
        : actor ? (en ? `Gestures only. Skips left: ${(game.skipLimit ?? 3) - (game.skipsUsed ?? 0)}.` : `Solo gestos. Te quedan ${(game.skipLimit ?? 3) - (game.skipsUsed ?? 0)} saltos.`)
        : eligible ? (en ? "Tap Guessed only after saying the correct word. The next word appears immediately." : "Primero adivina en voz alta, luego presiona adivinar. Entonces el siguiente término llega inmediatamente.")
        : game.mode === "team" && own ? (en ? `${teamLabel} is guessing. Your team is watching.` : `${teamLabel} está adivinando. Tu equipo solo mira.`)
        : (en ? "You can join from the next turn." : "Juegas desde el siguiente turno."),
      secret: actor && acting && game.secretTerm ? { label: en ? "Your word" : "Tu término", term: game.secretTerm, initiallyVisible: true } : undefined,
      feedback: guessFeedback ? { label: en ? "Guessed!" : "¡Adiviné!", term: guessFeedback } : undefined,
      score: game.mode === "team" && own ? game.teamScores?.[own.teamId] ?? 0 : game.gameScores?.[id] ?? 0,
      deadline: acting ? game.finishAt ?? undefined : undefined,
      getServerTime: state.getServerTime,
      resetKey: `${game.roundId}:${game.turnNumber}:${game.stage}:${game.wordToken}`,
      hasSubmitted: false, disabled: !playing, choices: [],
      actions: !playing ? [] : actor && game.stage === "ready"
        ? [{ id: "reveal", label: en ? "Reveal first word & start" : "Revelar y comenzar el primer término.", onPress: () => send({ type: "pantomime:reveal" }) }]
        : actor && acting ? [{ id: "skip", label: en ? `Next · ${(game.skipLimit ?? 0) - (game.skipsUsed ?? 0)} skips left` : `Siguiente · todavía ${(game.skipLimit ?? 0) - (game.skipsUsed ?? 0)}×`,
          disabled: locked || (game.skipsUsed ?? 0) >= (game.skipLimit ?? 0), onPress: () => send({ type: "pantomime:skip" }) }]
        : eligible && acting ? [{ id: "claim", label: en ? "Guessed!" : "¡Adiviné!", disabled: locked, onPress: () => send({ type: "pantomime:claim" }) }]
        : actor && reviewing ? [{ id: "continue", label: en ? "Confirm & next actor →" : "Confirmar y siguiente persona →", onPress: () => send({ type: "pantomime:continue" }) }] : [],
      review: reviewing ? {
        title: en ? "Words & claims" : "Términos y aciertos", editable: actor,
        unassignedLabel: en ? "No point" : "Sin punto",
        entries: (game.entries ?? []).map((entry) => ({ id: entry.id, label: entry.term, selectedId: entry.guesserPlayerId ?? "",
          note: entry.status === "skipped" ? (en ? "Skipped / removed" : "Saltado/retirado")
            : entry.status === "timeout" ? (en ? "Time expired" : "El tiempo expiró") : undefined })),
        options: (game.players ?? []).filter((p) => p.id !== game.actorPlayerId && (game.mode !== "team" || p.teamId === game.actorTeamId))
          .map((p) => ({ id: p.id, label: p.name })),
        onAssign: (entryId: string, guesserPlayerId: string) => send({ type: "pantomime:assign", entryId, guesserPlayerId: guesserPlayerId || null })
      } : undefined,
      onSubmitPick: () => {}, onVote: () => {}, onSubmitText: () => {}, onSubmitAvatar: () => {}, onSubmitMedia: () => {}
    };
  }
} as const;
