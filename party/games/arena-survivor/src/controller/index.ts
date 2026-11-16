import type { ControllerLayoutKey } from "@open-party-lab/game-core";
import { arenaSurvivorManifest } from "../manifest.js";
import { buildArenaSurvivorControllerModel } from "./ArenaSurvivorController.js";

export const controllerGame = {
  id: arenaSurvivorManifest.id,
  layoutKey: "virtual_joystick" as ControllerLayoutKey,
  buildLayout: buildArenaSurvivorControllerModel
} as const;

export { buildArenaSurvivorControllerModel };

