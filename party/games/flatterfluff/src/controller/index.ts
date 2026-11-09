import { flatterfluffManifest } from "../manifest.js";
import type {
  FlatterfluffAimInput,
  FlatterfluffShootInput,
  FlatterfluffState
} from "../protocol.js";

type SupportedLanguage = "de" | "en";

interface ControllerActionButtonModel {
  id: string;
  label: string;
  accentColor?: string;
  disabled?: boolean;
  hapticPattern?: number | number[];
  onPress: () => void;
}

interface VirtualJoystickLayoutModel {
  kind: "virtual_joystick";
  title: string;
  subtitle?: string;
  helperText?: string;
  stickHint?: string;
  cleanChrome?: boolean;
  disabled: boolean;
  accentColor?: string;
  resetKey: string;
  centerLabel?: string;
  actionButtons?: ControllerActionButtonModel[];
  actionButtonColumns?: 1 | 2 | 3 | 4;
  onMoveChange: (moveX: number, moveY: number) => void;
}

interface ControllerGameRenderContext {
  state: {
    preferredLanguage?: SupportedLanguage;
    room?: { language?: SupportedLanguage } | null;
    player?: { id: string; color?: string } | null;
    game?: {
      phase?: string;
      roundNumber?: number;
      state?: unknown;
    } | null;
  };
  onInput(input: unknown): void;
}

function createAimInput(
  playerId: string,
  aimX: number,
  aimY: number
): FlatterfluffAimInput {
  return {
    type: "aim",
    playerId,
    aimX,
    aimY,
    sentAt: Date.now()
  };
}

function createShootInput(playerId: string): FlatterfluffShootInput {
  return { type: "shoot", playerId, sentAt: Date.now() };
}

export function buildFlatterfluffControllerModel(
  context: ControllerGameRenderContext
): VirtualJoystickLayoutModel {
  const language = context.state.room?.language ?? context.state.preferredLanguage;
  const en = language === "en";
  const es = (language as string | undefined) === "es";
  const playerId = context.state.player?.id ?? "";
  const gameState = (context.state.game?.state ?? null) as FlatterfluffState | null;
  const player = gameState?.players.find((entry) => entry.playerId === playerId);
  const playing = context.state.game?.phase === "playing";
  const reloading =
    player?.reloadEndsAt !== null &&
    player?.reloadEndsAt !== undefined &&
    (gameState?.serverNow ?? 0) < player.reloadEndsAt;
  return {
    kind: "virtual_joystick",
    title: flatterfluffManifest.translations[en ? "en" : "de"].displayName,
    cleanChrome: true,
    disabled: !playing,
    accentColor: player?.color ?? context.state.player?.color ?? "#22d3ee",
    resetKey: `${context.state.game?.roundNumber ?? 0}:${context.state.game?.phase ?? "idle"}`,
    centerLabel: es ? "APUNTA" : en ? "AIM" : "ZIEL",
    actionButtonColumns: 1,
    actionButtons: [
      {
        id: "shoot",
        label: reloading ? (es ? "ESPERA" : en ? "WAIT" : "WARTEN") : es ? "FUEGO" : en ? "FIRE" : "FEUER",
        accentColor: "#f97316",
        hapticPattern: 45,
        disabled: !playing || reloading,
        onPress: () => {
          if (playerId) {
            context.onInput(createShootInput(playerId));
          }
        }
      }
    ],
    onMoveChange: (moveX, moveY) => {
      if (playerId) {
        context.onInput(createAimInput(playerId, moveX, moveY));
      }
    }
  };
}

export const controllerGame = {
  id: flatterfluffManifest.id,
  layoutKey: "virtual_joystick",
  buildLayout(context: ControllerGameRenderContext) {
    return buildFlatterfluffControllerModel(context);
  }
} as const;
