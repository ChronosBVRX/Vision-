import type { ControllerLayoutKey } from "@open-party-lab/game-core";
import { schattenjagdManifest } from "../manifest.js";
import type { SchattenjagdControllerState } from "../protocol.js";
import { getSchattenjagdText, type SchattenjagdLanguage } from "../text.js";
import {
  createMoveInput,
  createToggleDoubleMoveInput,
  createToggleShroudInput
} from "./schattenjagdBindings.js";

interface ReadyLayoutModel {
  currentPlayerReady: boolean;
  readyCount: number;
  playerCount: number;
  label: string;
  description?: string;
  language?: SchattenjagdLanguage;
  onToggleReady: () => void;
}

interface ChoiceLayoutModel {
  kind: "choice";
  title: string;
  subtitle?: string;
  helperText?: string;
  accentColor?: string;
  identityLabel?: string;
  disabled: boolean;
  ready?: ReadyLayoutModel;
  choices: Array<{
    id: string;
    label: string;
    iconPath?: string;
    description?: string;
    disabled?: boolean;
    onSelect: () => void;
  }>;
  stats?: Array<{ label: string; value: string; highlighted?: boolean }>;
  feed?: string[];
}

interface ControllerGameRenderContext {
  state: {
    preferredLanguage?: SchattenjagdLanguage;
    room?: {
      language?: SchattenjagdLanguage;
      selectedGameId?: string;
      availableGames?: Array<{ id: string; displayName?: string; roundCompletionMode?: string }>;
      players?: Array<{ id: string; name: string; isReady?: boolean }>;
    } | null;
    player?: {
      id: string;
      isReady?: boolean;
      color?: string;
    } | null;
    game?: {
      phase?: string;
      message?: string;
      state?: unknown;
    } | null;
  };
  onInput(input: unknown): void;
  onSetReady?: (isReady: boolean) => void;
}

function buildReadyModel(context: ControllerGameRenderContext): ReadyLayoutModel | undefined {
  const { state, onSetReady } = context;
  const gameId = state.room?.selectedGameId;
  const selectedGame = gameId ? state.room?.availableGames?.find((entry) => entry.id === gameId) : undefined;

  if (
    selectedGame?.roundCompletionMode !== "wait_for_ready" ||
    state.game?.phase !== "finished" ||
    !state.room ||
    !state.player ||
    !onSetReady
  ) {
    return undefined;
  }

  const players = state.room.players ?? [];
  const playerId = state.player.id;
  const currentPlayerReady = Boolean(
    players.find((player) => player.id === playerId)?.isReady ?? state.player.isReady
  );
  const readyCount = players.filter((player) => player.isReady).length;
  const en = state.room.language === "en";

  return {
    currentPlayerReady,
    readyCount,
    playerCount: players.length,
    label: en ? "Next Round" : "próxima ronda",
    description: en
      ? `${readyCount}/${players.length} players are ready.`
      : `${readyCount}/${players.length} Los jugadores están listos.`,
    language: state.room.language,
    onToggleReady: () => onSetReady(!currentPlayerReady)
  };
}

/** Kompakte Symbole je Verkehrsmittel fuer die Telefonansicht. */
const modeSymbols = {
  taxi: "●",
  bus: "■",
  metro: "◆"
} as const;

const modeTicketPaths = {
  taxi: "/schattenjagd/ticket-taxi.png",
  bus: "/schattenjagd/ticket-bus.png",
  metro: "/schattenjagd/ticket-metro.png"
} as const;

function buildHelperText(
  gameState: SchattenjagdControllerState,
  language: SchattenjagdLanguage,
  fallbackMessage?: string
): string {
  const text = getSchattenjagdText(language);

  if (gameState.outcome === "caught") {
    return text.stageCaught;
  }

  if (gameState.outcome === "escaped") {
    return text.stageEscaped;
  }

  if (gameState.role === "shadow") {
    if (gameState.stage === "shadow_move") {
      return gameState.doubleMoveStep === 2 ? text.doubleSecondStep : text.shadowTurnHint;
    }

    return gameState.activeDetectiveName ? text.activeDetective(gameState.activeDetectiveName) : text.detectiveWaitHint;
  }

  if (gameState.role === "detective") {
    if (gameState.stage === "detective_move") {
      return gameState.isMyTurn ? text.detectiveTurnHint : gameState.activeDetectiveName ? text.activeDetective(gameState.activeDetectiveName) : text.detectiveWaitHint;
    }

    return text.shadowWaitHint;
  }

  return fallbackMessage ?? text.hunt;
}

export function buildSchattenjagdControllerModel(
  context: ControllerGameRenderContext
): ChoiceLayoutModel {
  const { state, onInput } = context;
  const language: SchattenjagdLanguage = state.room?.language === "en" ? "en" : "de";
  const text = getSchattenjagdText(language);
  const gameState = (state.game?.state ?? {}) as Partial<SchattenjagdControllerState>;
  const playerId = state.player?.id ?? "";
  const phase = state.game?.phase;
  const isPlaying = phase === "playing";

  const role = gameState.role ?? "spectator";
  const stage = gameState.stage ?? "shadow_move";
  const options = gameState.options ?? [];
  const isShadow = role === "shadow";
  const playerColor = state.player?.color ?? (isShadow ? "#f472b6" : "#38bdf8");
  const canAct =
    isPlaying &&
    Boolean(gameState.isMyTurn) &&
    (gameState.outcome ?? null) === null;

  const choices: ChoiceLayoutModel["choices"] = [];

  if (isShadow && stage === "shadow_move" && isPlaying) {
    if ((gameState.shroudsLeft ?? 0) > 0) {
      choices.push({
        id: "toggle-shroud",
        label: `${gameState.shroudArmed ? "✓ " : ""}${text.shroudLabel} (${gameState.shroudsLeft ?? 0})`,
        description: text.shroudDescription,
        disabled: !canAct,
        onSelect: () => onInput(createToggleShroudInput(playerId))
      });
    }

    if ((gameState.doublesLeft ?? 0) > 0 && (gameState.doubleMoveStep ?? 1) === 1) {
      choices.push({
        id: "toggle-double",
        label: `${gameState.doubleArmed ? "✓ " : ""}${text.doubleLabel} (${gameState.doublesLeft ?? 0})`,
        description: text.doubleDescription,
        disabled: !canAct,
        onSelect: () => onInput(createToggleDoubleMoveInput(playerId))
      });
    }
  }

  for (const option of options) {
    const modeLabel = text.modeLabels[option.mode];

    choices.push({
      id: `move:${option.stationId}:${option.mode}`,
      label: `${modeSymbols[option.mode]} ${option.stationId}`,
      iconPath: modeTicketPaths[option.mode],
      description: option.blocked
        ? `${modeLabel} · ${option.blockedReason === "occupied" ? text.blockedOccupied : text.blockedNoTicket}`
        : option.ticketsLeft < 0
          ? text.moveDescriptionUnlimited(modeLabel)
          : text.moveDescription(modeLabel, option.ticketsLeft),
      disabled: !canAct || option.blocked,
      onSelect: () => onInput(createMoveInput(playerId, option.stationId, option.mode))
    });
  }

  const stats: NonNullable<ChoiceLayoutModel["stats"]> = [
    {
      label: text.yourStation,
      value: gameState.currentStationId != null ? `${gameState.currentStationId}` : "-",
      highlighted: true
    },
    {
      label: text.turnLabel,
      value: `${gameState.turn ?? 1}/${gameState.totalTurns ?? 0}   ${
        gameState.nextRevealTurn != null
          ? `${text.sightingLabel} ${gameState.nextRevealTurn}`
          : text.noReveal
      }`
    }
  ];

  if (gameState.tickets) {
    stats.push({
      label: text.ticketLabel,
      value: `${modeSymbols.taxi} ${gameState.tickets.taxi}   ${modeSymbols.bus} ${gameState.tickets.bus}   ${modeSymbols.metro} ${gameState.tickets.metro}`
    });
  }

  if (isShadow) {
    stats.push({
      label: text.specialsLabel,
      value: `${text.shroudLabel} ${gameState.shroudsLeft ?? 0}   ${text.doubleLabel} ${gameState.doublesLeft ?? 0}`
    });
  }

  stats.push({
    label: text.shadowLabel,
    value:
      gameState.lastKnownShadowStationId != null && gameState.lastRevealTurn != null
        ? `${gameState.lastKnownShadowStationId} (${text.turnLabel} ${gameState.lastRevealTurn})`
        : text.neverSeen
  });

  const feed: string[] = [];
  const logLine = (gameState.travelLog ?? [])
    .slice(-6)
    .map((entry) => {
      const symbol = entry.mode ? modeSymbols[entry.mode] : "?";
      const revealed = entry.revealedStationId != null ? `→${entry.revealedStationId}` : "";
      return `${entry.turn}${symbol}${revealed}${entry.doubleMove ? "²" : ""}`;
    })
    .join("   ");

  if (logLine) {
    feed.push(logLine);
  }

  if (role === "detective" || role === "spectator") {
    const positions = (gameState.detectiveStations ?? [])
      .filter((entry) => entry.playerId !== playerId)
      .map((entry) => `${entry.name} ${entry.stationId}${entry.hasMoved ? "✓" : ""}`)
      .join("   ");

    if (positions) {
      feed.push(positions);
    }
  }

  const title =
    state.room?.availableGames?.find((game) => game.id === schattenjagdManifest.id)?.displayName ??
    text.title;

  const roleLine = gameState.roleLabel ?? (isShadow ? text.shadowRole : text.detectiveRole);
  const positionLine =
    gameState.currentStationId != null ? text.positionLine(gameState.currentStationId) : null;

  return {
    kind: "choice",
    title: positionLine ? `${title} · ${text.stationLabel} ${gameState.currentStationId}` : title,
    subtitle: roleLine,
    helperText: [
      positionLine,
      buildHelperText(gameState as SchattenjagdControllerState, language, state.game?.message)
    ]
      .filter(Boolean)
      .join(" — "),
    accentColor: playerColor,
    identityLabel: text.yourColor,
    disabled: !canAct,
    choices,
    ready: buildReadyModel(context),
    stats,
    feed
  };
}

export const controllerGame = {
  id: schattenjagdManifest.id,
  layoutKey: "choice" as ControllerLayoutKey,
  buildLayout(context: ControllerGameRenderContext) {
    return buildSchattenjagdControllerModel(context);
  }
} as const;
