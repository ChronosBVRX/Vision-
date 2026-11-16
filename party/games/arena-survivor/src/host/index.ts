import { arenaSurvivorManifest } from "../manifest.js";
import { ArenaSurvivorHostScene } from "./ArenaSurvivorHostScene.js";

export const hostGame = {
  id: arenaSurvivorManifest.id,
  displayName: arenaSurvivorManifest.displayName,
  sceneKey: arenaSurvivorManifest.hostView,
  scene: ArenaSurvivorHostScene
} as const;

export { ArenaSurvivorHostScene };

