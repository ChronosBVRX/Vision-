import type { HostGame } from "@open-party-lab/game-core";
import { schaetzoramaManifest } from "../manifest.js";
import { mountSchaetzoramaHost } from "./schaetzoramaDomHost.js";

export { mountSchaetzoramaHost } from "./schaetzoramaDomHost.js";

export const hostGame = {
  id: schaetzoramaManifest.id,
  displayName: schaetzoramaManifest.displayName,
  mountDom: mountSchaetzoramaHost
} satisfies HostGame;
