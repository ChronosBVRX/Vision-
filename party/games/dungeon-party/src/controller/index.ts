import type { ControllerLayoutKey } from "@open-party-lab/game-core";
import { classActions, dungeonPartyManifest } from "../manifest.js";
import { campActions } from "../content.js";
import type { DungeonActionId, DungeonPartyControllerState } from "../protocol.js";

interface RenderContext {
  state: {
    preferredLanguage?: "de" | "en";
    room?: { language?: "de" | "en"; players?: Array<{ id: string; name: string }> } | null;
    player?: { id: string } | null;
    game?: { phase?: string; state?: unknown } | null;
  };
  onInput(input: unknown): void;
}

const actionLabels: Record<"de" | "en", Record<DungeonActionId, string>> = {
  de: { fight: "peleando", aid: "Helfen", loot: "Beute sichern", ...Object.fromEntries(Object.values(classActions).map((value) => [value.id, value.name.de])) } as Record<DungeonActionId, string>,
  en: { fight: "Fight", aid: "Help", loot: "Grab loot", ...Object.fromEntries(Object.values(classActions).map((value) => [value.id, value.name.en])) } as Record<DungeonActionId, string>
};

export const controllerGame = {
  id: dungeonPartyManifest.id,
  layoutKey: "dungeon_party" as ControllerLayoutKey,
  buildLayout({ state, onInput }: RenderContext) {
    const language = state.room?.language ?? state.preferredLanguage;
    const en = language === "en";
    const copy = en ? "en" : "de";
    const game = (state.game?.state ?? {}) as Partial<DungeonPartyControllerState>;
    const playerId = state.player?.id ?? "";
    const hero = game.heroes?.find((entry) => entry.playerId === playerId);
    const encounter = game.encounters?.[game.encounterIndex ?? -1];
    const resolution = game.lastResolution;
    const stage = game.stage;
    const ownActionSubmitted = Boolean(game.ownActionSubmitted);
    const ownResponseSubmitted = Boolean(game.ownResponseSubmitted);
    const ownVoteSubmitted = Boolean(game.ownVoteSubmitted);
    const ownContinueSubmitted = Boolean(game.ownContinueSubmitted);
    const ownHasPlayableResponse = Boolean(game.ownHasPlayableResponse);
    const camp = stage === "camp" || stage === "camp_reveal";
    const readyPhase = stage === "reveal" || stage === "camp_reveal";
    const playing = state.game?.phase === "playing";
    const finished = state.game?.phase === "finished" || state.game?.phase === "scoreboard" || state.game?.phase === "result";
    const mode = finished || stage === "complete" ? "complete"
      : stage === "camp" ? (game.ownCampSubmitted ? "waiting" : "planning")
        : stage === "camp_reveal" ? (ownContinueSubmitted ? "waiting" : "reveal")
      : stage === "voting" ? (ownVoteSubmitted ? "waiting" : "voting")
        : stage === "planning" ? (ownActionSubmitted ? "waiting" : "planning")
          : stage === "response" ? (ownResponseSubmitted || !ownHasPlayableResponse ? "waiting" : "response")
            : stage === "reveal" ? (ownContinueSubmitted ? "waiting" : "reveal") : "waiting";
    const encounterLabel = encounter?.id === "boss" && encounter.phaseIndex
      ? `${en ? "Boss phase" : "Bossphase"} ${encounter.phaseIndex}/${encounter.phaseCount ?? 3}`
      : `${en ? "Room" : "Raum"} ${(game.encounterIndex ?? 0) + 1}/${game.encounters?.length ?? 8}`;

    const send = (type: string, fields: Record<string, unknown> = {}) => {
      if (playerId) onInput({ type, playerId, sentAt: Date.now(), ...fields });
    };
    const choices = stage === "camp" && !game.ownCampSubmitted
      ? Object.entries(campActions).map(([id, action]) => ({
        id, label: action.name[copy], description: action.description[copy], symbol: action.symbol,
        disabled: (hero?.gold ?? 0) < action.cost,
        onSelect: () => send("dungeon_camp_action", { action: id })
      }))
      : mode === "voting"
      ? (game.routeOptions ?? []).map((route) => ({
        id: route.id,
        label: route.name,
        description: `${route.description} · ${en ? "Target" : "Zielwert"} ${route.difficulty}`,
        onSelect: () => send("dungeon_route_vote", { routeId: route.id })
      }))
      : mode === "planning"
        ? (["fight", "aid", "loot", ...(hero && classActions[hero.classId] ? [classActions[hero.classId].id] : [])] as DungeonActionId[]).map((action) => ({
          id: action,
          symbol: action === "fight" ? "⚔" : action === "aid" ? "✚" : action === "loot" ? "¤" : "✦",
          label: actionLabels[copy][action] ?? action,
          description: action === "fight"
            ? (en ? "Roll a d6, add your class bonus and equipment. Gain 1 fame." : "Tira un d6 y añade bonificación de clase y equipo. Obtienes 1 fama.") + (hero?.classId === "mage" ? (en ? " Rolling 1 costs 1 health." : " Un 1 cuesta 1 vida.") : "")
            : action === "aid"
              ? (en ? "Add steady power; Clerics can heal a wounded hero. Gain 1 fame." : "Traiga energía confiable; Los clérigos curan. Obtienes 1 fama.")
              : action === "loot"
                ? (en ? "Gain 2 gold regardless. On success, gain 2 fame and draw a card; on failure, take extra damage." : "2 de oro garantizados. Si aciertas, ganas 2 de fama y una carta; si fallas, recibes daño extra.")
                : (classActions[hero?.classId ?? ""]?.description[copy] ?? "") + (en ? " No base Fight/Help fame." : "  Sin fama base por pelear o ayudar."),
          onSelect: () => send("dungeon_action", { action })
        }))
        : [];

    const actionName = (action: string | undefined) => actionLabels[copy][(action as DungeonActionId) ?? "fight"] ?? action ?? actionLabels[copy].fight;
    const revealedActions = mode === "response"
      ? (game.heroes ?? []).map((entry) => `${entry.name}: ${actionName(game.revealedActionsByPlayer?.[entry.playerId])}`)
      : [];
    const selectedEncounter = game.encounters?.[game.encounterIndex ?? -1];
    const hand = (game.ownHand ?? []).map((card) => ({
      id: card.id, name: card.name, description: card.description, kind: card.kind, effect: card.effect,
      artKey: card.effect ?? card.kind
    }));
    const pendingCard = game.ownResponseCard ? {
      id: game.ownResponseCard.id,
      name: game.ownResponseCard.name,
      description: game.ownResponseCard.description,
      kind: game.ownResponseCard.kind,
      effect: game.ownResponseCard.effect,
      artKey: game.ownResponseCard.effect ?? game.ownResponseCard.kind,
      targetName: game.ownResponseTargetName
    } : undefined;
    const cardsInResolution = (resolution?.cards ?? []).map((card) =>
      `${card.playerName} ${en ? "played" : "spielt"} „${card.name}“${card.targetName ? ` → ${card.targetName}` : ""} · ${card.description}`
    );
    const rewardCards = resolution?.rewards.map((card) => ({
      playerName: card.playerName,
      source: card.source,
      card: { id: card.cardId, name: card.name, description: card.description, kind: card.kind, effect: card.effect, artKey: card.effect ?? card.kind }
    })) ?? [];
    const resultCopy = resolution ? {
      success: resolution.success,
      partyPower: resolution.partyPower,
      sharedPower: resolution.sharedPower,
      difficultyModifier: resolution.difficultyModifier,
      targetDifficulty: resolution.targetDifficulty,
      cards: cardsInResolution,
      rewards: rewardCards,
      heroes: resolution.heroes.map((entry) => ({
        name: entry.name,
        action: actionName(entry.action),
        roll: entry.roll,
        originalRoll: entry.originalRoll,
        contribution: entry.contribution,
        healthDelta: entry.healthDelta,
        fameDelta: entry.fameDelta,
        goldDelta: entry.goldDelta,
        outcome: en ? undefined : entry.outcome
      }))
    } : undefined;

    const waitingCopy = mode === "waiting"
      ? stage === "camp"
        ? (en ? "Camp choice locked. Everyone prepares together." : "Campaña de stock entregada. Todos se preparan juntos.")
      : stage === "voting"
        ? (en ? "Your secret route vote is in. Waiting for everyone to choose." : "Su elección de ruta secreta ha sido enviada. Espere hasta que todos hayan votado.")
        : stage === "planning"
          ? (en ? "Your action is sealed. The group advances when everyone has chosen." : "Tu acción es secreta. Continúa cuando todos han votado.")
        : stage === "response"
            ? !ownHasPlayableResponse
              ? (en ? "Ninguna carta jugable. Tu respuesta se omitió automáticamente." : "No hay cartas jugables. Tu respuesta se omitió automáticamente.")
              : (en ? "Your card response is locked. Waiting for the others." : "Tu carta está jugada. Espera a los demás.")
            : (en ? `You are ready. ${Object.keys(game.continueByPlayerId ?? {}).length}/${game.heroes?.length ?? 0} players are ready to continue.` : `Listo. ${Object.keys(game.continueByPlayerId ?? {}).length}/${game.heroes?.length ?? 0} están listos para seguir adelante.`)
      : "";
    const title = camp ? (stage === "camp_reveal" ? (en ? "Ready for the road" : "Listo para el viaje") : (en ? "Campfire break" : "Romper junto a la fogata"))
      : mode === "voting" ? (en ? "Choose a route" : "Elige tu camino")
      : mode === "planning" ? (en ? "Choose your move" : "Elige tu acción")
        : mode === "response" ? (en ? "Play a card" : "Juega una carta")
          : mode === "reveal" || mode === "complete" ? (en ? "The result" : "El resultado")
            : (en ? "Choice locked" : "Decisión enviada");
    const helperText = camp
      ? stage === "camp_reveal"
        ? (en ? "Preparations are complete. Read the results, then mark Ready." : "Los preparativos están completos. Lee los resultados y prepárate.")
        : (en ? "Choose one preparation. Gold is spent only on your choice. No timer." : "Elige una preparación. Sólo pagas por tu propia acción. Sin límite de tiempo.")
      : mode === "voting"
      ? (en ? "Your route vote is secret. Most votes win; a tie picks the easier path." : "Su elección de ruta sigue siendo secreta. La mayoría decide, en caso de empate lo más fácil.")
      : mode === "planning"
        ? (en ? "No timer. Pick one action; everyone reveals together." : "Sin límite de tiempo. Elijan con calma una acción; todos ven las acciones al mismo tiempo.")
        : mode === "response"
          ? (en ? "Actions are revealed. Choose one action card and target, or pass. No timer." : "Se revelan las acciones básicas. Juega una carta de acción con un gol o pase. Sin límite de tiempo.")
          : mode === "reveal"
            ? (en ? "The party advances together; the player with most fame wins. Continue when everyone has read the result." : "El grupo avanza junto; al final gana quien tiene más fama. Continúen cuando todos hayan leído el resultado.")
            : mode === "complete"
              ? (en ? "The campaign result is on the shared screen." : "El resultado de la campaña está en la pantalla compartida.")
              : waitingCopy;
    const subtitle = camp ? `${en ? "Before room" : "antes de la sala"} ${(game.encounterIndex ?? 0) + 1} · ${game.campSubmittedCount ?? 0}/${game.heroes?.length ?? 0} ${en ? "prepared" : "listos"}`
      : mode === "reveal" || mode === "complete"
      ? (resolution?.success ? (en ? "The party passed this room." : "El grupo ha despejado la habitación.") : (en ? "The party failed this room." : "El grupo fracasó en la sala."))
      : mode === "response" || stage === "response"
        ? `${encounterLabel} · ${en ? "responses" : "respuestas"} ${game.responseCount ?? 0}/${game.heroes?.length ?? 0}`
        : mode === "voting" || stage === "voting"
          ? `${encounterLabel} · ${en ? "route votes" : "votos de ruta"} ${game.routeVoteCount ?? 0}/${game.heroes?.length ?? 0}`
          : `${encounterLabel} · ${selectedEncounter?.name ?? dungeonPartyManifest.displayName}`;

    return {
      kind: "dungeon_party",
      language,
      mode,
      resetKey: `${game.encounterIndex ?? 0}-${stage}-${resolution?.id ?? ""}`,
      ownPlayerId: playerId,
      title,
      subtitle,
      helperText,
      accentColor: "#b7773e",
      disabled: !playing,
      statusLabel: mode === "waiting" ? (en ? "Waiting" : "Esperando") : undefined,
      stats: [
        { label: en ? "Health" : "Vida", value: `${hero?.health ?? 0}/8` },
        { label: en ? "Fame" : "Fama", value: String(hero?.fame ?? 0), highlighted: true },
        { label: "Oro", value: String(hero?.gold ?? 0) },
        { label: en ? "Gear power" : "equipo", value: `+${hero?.items.reduce((sum, item) => sum + item.attack, 0) ?? 0}` }
      ],
      choices,
      scene: camp ? "camp" : "dungeon",
      roomRule: !camp ? encounter?.rule : undefined,
      preparedPower: game.preparedPower ?? 0,
      campResults: stage === "camp_reveal" ? (game.campResults ?? []).map((entry) => ({
        playerName: entry.playerName, action: campActions[entry.action].name[copy], symbol: campActions[entry.action].symbol,
        healthDelta: entry.healthDelta, goldDelta: entry.goldDelta, powerDelta: entry.powerDelta,
        cards: entry.cards.map((card) => ({ ...card, artKey: card.effect ?? card.kind }))
      })) : undefined,
      hand,
      pendingCard,
      handTitle: en ? "Your hand" : "Tu mano",
      handHint: readyPhase ? (ownContinueSubmitted ? waitingCopy : (en ? "Read the recap, then mark Ready for the next room." : "Lee el resumen y marca Listo para la siguiente sala."))
        : mode === "waiting" ? waitingCopy
        : mode === "response" ? (en ? "Tap a card, choose a target, then play it." : "Toca una carta, elige un objetivo y juega.")
          : (en ? "Action cards can be played in the reaction phase." : "Juegas cartas de acción en la fase de reacción."),
      handPlayable: mode === "response",
      targets: (game.heroes ?? []).map((entry) => ({ id: entry.playerId, name: entry.name })),
      targetRequiredEffects: ["intrigue", "false_bill", "ward", "jam", "heal", "fortune"],
      targetForbiddenSelfEffects: ["intrigue", "false_bill", "jam"],
      teamFeed: revealedActions,
      resolution: resultCopy,
      readyPhase,
      continueLabel: en ? "Continue when ready" : "Continuar cuando todos estén listos.",
      readySubmitted: ownContinueSubmitted,
      readyLabel: ownContinueSubmitted ? (en ? "Ready ✓" : "Listo ✓") : (en ? "Ready" : "Bereit"),
      passLabel: en ? "Pass" : "Passen",
      playLabel: en ? "Play card" : "jugar a las cartas",
      onPlayCard: (cardId: string, targetPlayerId?: string) => send("dungeon_card_response", { cardId, ...(targetPlayerId ? { targetPlayerId } : {}) }),
      onPass: () => send("dungeon_card_response"),
      onContinue: () => send("dungeon_continue")
    };
  }
} as const;
