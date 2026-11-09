import { arenaText } from "../text.js";
import type { ServerGame, ServerGameContext } from "@open-party-lab/game-core";
import type {
  MagicArenaControllerState,
  MagicArenaInput,
  MagicArenaPublicState
} from "../protocol.js";
import { magicArenaManifest } from "../manifest.js";
import {
  MAGIC_ARENA_PLANNING_MODES,
  magicArenaRoomSettingKeys
} from "./magicArenaConfig.js";
import {
  buildMagicArenaScore,
  createMagicArenaInitialState,
  handleMagicArenaInput,
  isMagicArenaFinished,
  startMagicArenaRound,
  tickMagicArena,
  toMagicArenaControllerState,
  toMagicArenaPublicState,
  type MagicArenaRuntimeState
} from "./magicArenaRules.js";

function localizeRuntime(state: MagicArenaRuntimeState, context: ServerGameContext): MagicArenaRuntimeState {
  const message = state.message ? arenaText(state.message, context.language) : state.message;
  return message === state.message ? state : { ...state, message };
}

interface MagicArenaConfigureLobbyHostAction {
  type: "configure-lobby";
  planningMode?: unknown;
}

export const magicArenaServerGame: ServerGame<
  MagicArenaRuntimeState,
  MagicArenaInput,
  MagicArenaPublicState | MagicArenaControllerState
> = {
  manifest: magicArenaManifest,
  createInitialState(context) {
    return localizeRuntime(createMagicArenaInitialState(context), context);
  },
  handleHostAction(state, action, _context) {
    const hostAction = action as Partial<MagicArenaConfigureLobbyHostAction> | null;

    if (!hostAction?.type || hostAction.type !== "configure-lobby") {
      return {};
    }

    if (state) {
      return {};
    }

    if (
      typeof hostAction.planningMode === "string" &&
      (MAGIC_ARENA_PLANNING_MODES as readonly string[]).includes(hostAction.planningMode)
    ) {
      return {
        roomSettings: {
          [magicArenaRoomSettingKeys.planningMode]: hostAction.planningMode
        }
      };
    }

    return {};
  },
  startRound(_state, context) {
    return localizeRuntime(startMagicArenaRound(context), context);
  },
  handleInput(state, input, context) {
    return localizeRuntime(handleMagicArenaInput(state, input, context), context);
  },
  tick(state, deltaMs, context) {
    return localizeRuntime(tickMagicArena(state, deltaMs, context), context);
  },
  isRoundFinished(state) {
    return isMagicArenaFinished(state);
  },
  buildScore(state, context) {
    return buildMagicArenaScore(state).map(entry => ({ ...entry, reason: arenaText(entry.reason ?? "", context.language) }));
  },
  toPublicState(state, context) {
    return toMagicArenaPublicState(state, context);
  },
  toControllerStateForPlayer(state, context, playerId) {
    return toMagicArenaControllerState(state, context, playerId);
  }
};
