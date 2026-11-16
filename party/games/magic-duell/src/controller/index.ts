import type { ControllerLayoutKey } from "@open-party-lab/game-core";
import { magicDuellManifest } from "../manifest.js";
import { buildMageDuel3dControllerModel } from "./MageDuel3dController.js";

export const controllerGame = {
  id: magicDuellManifest.id,
  layoutKey: "spell_casting" as ControllerLayoutKey,
  buildLayout: buildMageDuel3dControllerModel
} as const;
