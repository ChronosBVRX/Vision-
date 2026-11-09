import type { ControllerLayoutKey } from "@open-party-lab/game-core";
import { magicArenaManifest } from "../manifest.js";
import { buildMagicArenaControllerModel } from "./MagicArenaController.js";

export const controllerGame = {
  id: magicArenaManifest.id,
  layoutKey: "magic_arena" as ControllerLayoutKey,
  buildLayout: buildMagicArenaControllerModel
} as const;
