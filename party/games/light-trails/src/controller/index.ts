import type { ControllerLayoutKey } from "@open-party-lab/game-core";
import { lightTrailsManifest } from "../manifest.js";
import type { LightTrailsState } from "../protocol.js";
import { createLightTrailsInput } from "./lightTrailsBindings.js";

type SupportedLanguage = "de" | "en" | "es";

interface ReadyLayoutModel {
  currentPlayerReady: boolean;
  readyCount: number;
  playerCount: number;
  label: string;
  description: string;
  language?: SupportedLanguage;
  onToggleReady: () => void;
}

interface LeftRightHoldLayoutModel {
  kind: "left_right_hold";
  title: string;
  subtitle?: string;
  helperText?: string;
  disabled: boolean;
  accentColor?: string;
  statusLabel: string;
  statusKey: string;
  leftLabel: string;
  rightLabel: string;
  ready?: ReadyLayoutModel;
  stats?: Array<{ label: string; value: string; highlighted?: boolean }>;
  onLeftChange: (active: boolean) => void;
  onRightChange: (active: boolean) => void;
}

interface ControllerGameRenderContext {
  state: {
    preferredLanguage?: SupportedLanguage;
    room?: {
      language?: SupportedLanguage;
      players?: Array<{ id: string; name: string; isReady?: boolean }>;
    } | null;
    player?: {
      id: string;
      color?: string;
      isReady?: boolean;
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

function formatStatusLabel(options: {
  phase: string | undefined;
  alive: boolean;
  won: boolean;
  isDraw: boolean;
  en: boolean;
}): { label: string; key: string } {
  if (options.won) {
    return { label: options.en ? "Won" : "Ganaste", key: "won" };
  }

  if (!options.alive) {
    return { label: options.en ? "Eliminated" : "Eliminado", key: "out" };
  }

  if (options.phase === "playing") {
    return { label: options.en ? "Playing" : "Jugando", key: "playing" };
  }

  if (options.isDraw && options.phase !== "playing") {
    return { label: options.en ? "Draw" : "Empate", key: "draw" };
  }

  if (options.phase === "countdown") {
    return { label: "Countdown", key: "countdown" };
  }

  return { label: options.en ? "Waiting" : "Esperando", key: "waiting" };
}

export function buildLightTrailsControllerModel(
  context: ControllerGameRenderContext
): LeftRightHoldLayoutModel {
  const en = context.state.room?.language === "en";
  const phase = context.state.game?.phase;
  const playerId = context.state.player?.id ?? "";
  const gameState = (context.state.game?.state ?? null) as LightTrailsState | null;
  const ownPlayerState = playerId ? gameState?.players[playerId] : undefined;
  const won = gameState?.winnerPlayerId === playerId;
  const alive = ownPlayerState?.alive ?? true;
  const status = formatStatusLabel({
    phase: context.state.game?.phase,
    alive,
    won,
    isDraw: gameState?.isDraw ?? false,
    en
  });
  const aliveCount = gameState?.alivePlayerIds.length ?? 0;
  const totalPlayers = Object.keys(gameState?.players ?? {}).length;
  const currentPlayerReady = Boolean(context.state.player?.isReady);
  const players = context.state.room?.players ?? [];
  const playerCount = players.length;
  const readyCount = players.filter((player) => player.isReady).length;
  const waitingForReady = phase === "finished";
  const waitingHelperText = en
    ? `Next round starts automatically when ${readyCount}/${playerCount} are ready.`
    : `La siguiente ronda comienza cuando ${readyCount}/${playerCount} estén listos.`;
  const defaultHelperText = en
    ? "Hold LEFT or RIGHT to steer your trail through the arena."
    : "Mantén presionado IZQUIERDA o DERECHA para girar tu estela por la arena.";
  const readyLabel = en ? "Next Round" : "Siguiente ronda";
  const statusValue = alive
    ? en
      ? "Hold the line"
      : "Mantén la estela"
    : en
      ? "Waiting for next round"
      : "Esperando la siguiente ronda";

  return {
    kind: "left_right_hold",
    title: "Estelas de Luz",
    subtitle:
      phase === "playing"
        ? en
          ? "Hold to steer"
          : "Mantén presionado para girar"
        : en
          ? "Get ready"
          : "Prepárate",
    helperText: context.state.game?.message ?? (waitingForReady ? waitingHelperText : defaultHelperText),
    disabled: phase !== "playing" || !alive,
    accentColor: context.state.player?.color ?? ownPlayerState?.color,
    statusLabel: status.label,
    statusKey: `${phase ?? "unknown"}:${status.key}`,
    leftLabel: en ? "LEFT" : "IZQUIERDA",
    rightLabel: en ? "RIGHT" : "DERECHA",
    ready:
      waitingForReady && context.onSetReady
        ? {
            currentPlayerReady,
            readyCount,
            playerCount,
            label: readyLabel,
            description: en
              ? `Tap "${readyLabel}". Starts when ${readyCount}/${playerCount} are ready.`
              : `Toca "${readyLabel}". Inicia cuando ${readyCount}/${playerCount} estén listos.`,
            language: context.state.room?.language,
            onToggleReady: () => context.onSetReady?.(!currentPlayerReady)
          }
        : undefined,
    stats: [
      {
        label: en ? "Player color" : "Spielerfarbe",
        value: context.state.player?.color ?? ownPlayerState?.color ?? (en ? "unknown" : "desconocido")
      },
      {
        label: en ? "Still racing" : "Noch im Rennen",
        value: `${aliveCount}/${totalPlayers}`
      },
      gameState?.winnerName
        ? {
            label: en ? "Winner" : "Ganador",
            value: gameState.winnerName,
            highlighted: true
          }
        : {
            label: "Status",
            value: statusValue
          }
    ],
    onLeftChange: (active) => context.onInput(createLightTrailsInput(playerId, "left", active)),
    onRightChange: (active) => context.onInput(createLightTrailsInput(playerId, "right", active))
  };
}

export const controllerGame = {
  id: lightTrailsManifest.id,
  layoutKey: "left_right_hold" as ControllerLayoutKey,
  buildLayout(context: ControllerGameRenderContext) {
    return buildLightTrailsControllerModel(context);
  }
} as const;
