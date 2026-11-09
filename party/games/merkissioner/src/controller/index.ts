import type { ControllerLayoutKey } from "@open-party-lab/game-core";
import { merkissionerManifest } from "../manifest.js";
import type { MerkissionerControllerView } from "../protocol.js";
import type { Decree, MerkissionerPrivateState, Vote } from "../engine/types.js";

interface ControllerContext {
  state: {
    preferredLanguage?: "de" | "en" | "es";
    room?: { language?: "de" | "en" | "es"; players?: Array<{ id: string; name: string; isReady?: boolean; connected?: boolean }> } | null;
    player?: { id: string; isReady?: boolean } | null;
    game?: { phase?: string; roundNumber?: number; state?: unknown } | null;
  };
  onInput(input: unknown): void;
  onSetReady?: (ready: boolean) => void;
}

const roleLabel: Record<string, string> = {
  merkizen: "Merkizen",
  merkite: "Merkite",
  merkissioner: "El Comisionado Merk",
};

const teamLabel: Record<string, string> = {
  merkizen: "Merkizens",
  merkite: "Merkites",
};

function nameOf(game: MerkissionerControllerView, seat: number): string {
  return game.seatPlayers.find((player) => player.seat === seat)?.name ?? `Jugador ${seat + 1}`;
}

function livingSeats(game: MerkissionerControllerView): number[] {
  return game.seatPlayers.map((player) => player.seat).filter((seat) => !game.banishedSeats.includes(seat as never));
}

export const controllerGame = {
  id: merkissionerManifest.id,
  layoutKey: "social_party" as ControllerLayoutKey,
  buildLayout({ state, onInput, onSetReady }: ControllerContext) {
    const game = (state.game?.state ?? null) as MerkissionerControllerView | null;
    const playerId = state.player?.id ?? "";
    const seat = game?.seat ?? 0;
    const priv = (game?.privateState ?? {
      role: "merkizen",
      knownMerkites: [],
      knownBoss: null,
      myVote: null,
      hand: null,
      auditResults: [],
      peek: null,
    }) as MerkissionerPrivateState;
    const phase = game?.phase ?? "huddle";
    const isChair = game?.chairSeat === seat;
    const isCommissioner = game?.commissionerSeat === seat;
    const isBanished = Boolean(game?.banishedSeats.includes(seat as never));
    const living = game ? livingSeats(game) : [];
    const hand = priv.hand ?? [];

    const send = (input: Record<string, unknown>) => {
      if (!playerId || !game?.roundId) return;
      onInput({ ...input, playerId, roundId: game.roundId, sentAt: Date.now() });
    };

    const choices: Array<{ id: string; label: string }> = [];
    let stage: "waiting" | "vote" | "finished" = "waiting";
    let status = "Prepárate";
    let helper = "Revisa tu rol y no enseñes la pantalla.";
    let taskKind: "pick" | "text" | "photo" | "draw" = "pick";
    let hasSubmitted = false;
    let actions: Array<{ id: string; label: string; disabled?: boolean; onPress: () => void }> = [];

    if (phase === "huddle") {
      status = "Reunión secreta";
      helper = `Tu rol: ${roleLabel[priv.role] ?? priv.role}. ${priv.role === "merkite" ? `Merkites conocidos: ${priv.knownMerkites.map((s) => nameOf(game!, s)).join(", ") || "ninguno"}. Jefe: ${priv.knownBoss === null ? "no lo conoces" : nameOf(game!, priv.knownBoss)}.` : ""}`;
      actions = [{ id: "ready", label: "Listo", onPress: () => send({ type: "ready_up" }) }];
    } else if (phase === "nominate") {
      status = isChair ? "Tu turno de nominar" : "La silla está nominando";
      helper = isChair ? "Elige a tu comisionado. No puedes nombrarte a ti ni al último comisionado." : `${nameOf(game!, game!.chairSeat)} está eligiendo comisionado.`;
      if (isChair && game) {
        stage = "vote";
        choices.push(...living.filter((s) => s !== seat && s !== game.lastCommissionerSeat).map((s) => ({ id: String(s), label: nameOf(game, s) })));
      }
    } else if (phase === "vote") {
      status = "Votación del gobierno";
      helper = `Silla: ${nameOf(game!, game!.chairSeat)} · Comisionado: ${game?.nomineeSeat !== null && game?.nomineeSeat !== undefined ? nameOf(game!, game.nomineeSeat) : "—"}. Vota SÍ o NO.`;
      stage = "vote";
      choices.push({ id: "yeah", label: "SÍ, que pase" }, { id: "nah", label: "NO, ni loco" });
      hasSubmitted = Boolean(priv.myVote);
    } else if (phase === "legislative_chair") {
      status = isChair ? "Recibiste tres decretos" : "La silla está legislando";
      helper = isChair ? "Descarta uno. Los otros dos van al comisionado." : "Espera a que la silla descarte un decreto.";
      if (isChair) {
        stage = "vote";
        choices.push(...hand.map((card, index) => ({ id: String(index), label: card === "merkite" ? "Decreto Merkite" : "Decreto Merkizen" })));
      }
    } else if (phase === "legislative_commissioner") {
      status = isCommissioner ? "Elige el decreto" : "El comisionado está legislando";
      helper = isCommissioner ? "Aprueba uno de los dos decretos. Tú decides." : `El comisionado ${game?.commissionerSeat !== null && game?.commissionerSeat !== undefined ? nameOf(game!, game.commissionerSeat) : "—"} está decidiendo.`;
      if (isCommissioner) {
        stage = "vote";
        choices.push(...hand.map((card, index) => ({ id: String(index), label: card === "merkite" ? "Decreto Merkite" : "Decreto Merkizen" })));
        if (game && game.merkiteEnacted >= 5 && !game.vetoRefusedThisSession) {
          actions.push({ id: "veto", label: "Proponer veto", onPress: () => send({ type: "propose_veto" }) });
        }
      }
    } else if (phase === "veto_pending") {
      status = "Veto en la mesa";
      helper = isChair ? "El comisionado propuso veto. ¿Lo aceptas?" : "La silla decide si acepta el veto.";
      if (isChair) {
        actions = [
          { id: "veto-yes", label: "Aceptar veto", onPress: () => send({ type: "resolve_veto", agree: true }) },
          { id: "veto-no", label: "Rechazar veto", onPress: () => send({ type: "resolve_veto", agree: false }) },
        ];
      }
    } else if (phase === "power_audit" || phase === "power_snap" || phase === "power_banish") {
      status = phase === "power_audit" ? "Auditoría" : phase === "power_snap" ? "Elección anticipada" : "Expulsión";
      helper = isChair ? "Elige a la persona objetivo." : "La silla está usando un poder.";
      if (isChair) {
        stage = "vote";
        choices.push(...living.filter((s) => s !== seat).map((s) => ({ id: String(s), label: nameOf(game!, s) })));
      }
    } else if (phase === "power_peek") {
      status = "Vistazo al mazo";
      helper = isChair
        ? `Viste: ${(priv.peek ?? []).map((card) => card === "merkite" ? "Merkite" : "Merkizen").join(" · ")}.`
        : "La silla está viendo el mazo.";
      if (isChair) actions = [{ id: "peek-ok", label: "Entendido", onPress: () => send({ type: "use_power" }) }];
    } else if (phase === "game_over") {
      stage = "finished";
      status = game?.winnerTeam === "merkite" ? "Ganaron los Merkites" : "Ganaron los Merkizens";
      helper = `Tu rol era: ${roleLabel[priv.role] ?? priv.role}.`;
    }

    const connected = state.room?.players?.filter((player) => player.connected !== false) ?? [];
    const ready = phase === "game_over" && onSetReady
      ? {
          currentPlayerReady: Boolean(connected.find((player) => player.id === playerId)?.isReady),
          readyCount: connected.filter((player) => player.isReady).length,
          playerCount: connected.length,
          label: "Listo para otra",
          description: "Cuando todos estén listos, empieza otra partida.",
          language: "es" as const,
          onToggleReady: () => onSetReady(!Boolean(connected.find((player) => player.id === playerId)?.isReady)),
        }
      : undefined;

    return {
      kind: "social_party" as const,
      title: "El Comisionado Merk",
      language: "es" as const,
      stage,
      taskKind,
      statusLabel: status,
      helperText: helper,
      secret: (phase === "huddle" || phase === "game_over") && !isBanished
        ? { label: "Tu rol", term: roleLabel[priv.role] ?? priv.role, initiallyVisible: true }
        : undefined,
      score: 0,
      deadline: game?.timer?.endsAt,
      resetKey: `${game?.roundId ?? ""}:${phase}:${game?.roundNumber ?? 0}:${game?.merkiteEnacted ?? 0}`,
      hasSubmitted,
      disabled: !game || phase === "game_over" || isBanished,
      selectedId: priv.myVote ?? undefined,
      choices,
      actions,
      ready,
      onSubmitPick: () => {},
      onSubmitAvatar: () => {},
      onSubmitText: () => {},
      onSubmitMedia: () => {},
      onVote: (id: string) => {
        if (phase === "nominate") send({ type: "nominate", targetSeat: Number(id) });
        else if (phase === "vote") send({ type: "cast_vote", vote: id as Vote });
        else if (phase === "legislative_chair") send({ type: "discard_decree", index: Number(id) });
        else if (phase === "legislative_commissioner") send({ type: "enact_decree", index: Number(id) });
        else if (phase === "power_audit" || phase === "power_snap" || phase === "power_banish") send({ type: "use_power", targetSeat: Number(id) });
      },
    };
  },
} as const;
