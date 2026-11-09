import Phaser from "phaser";
import type { TransportMode } from "../config.js";
import type { CityMapVariant } from "../protocol.js";

const assetRoot = "/schattenjagd";

export const cityMapTextureKeys: Record<CityMapVariant, string> = {
  night: "schattenjagd-city-night",
  day: "schattenjagd-city-day"
};

export const modeTicketTextureKeys: Record<TransportMode, string> = {
  taxi: "schattenjagd-ticket-taxi",
  bus: "schattenjagd-ticket-bus",
  metro: "schattenjagd-ticket-metro"
};

export const modeVehicleTextureKeys: Record<TransportMode, string> = {
  taxi: "schattenjagd-vehicle-taxi",
  bus: "schattenjagd-vehicle-bus",
  metro: "schattenjagd-vehicle-metro"
};

export function preloadSchattenjagdAssets(scene: Phaser.Scene): void {
  scene.load.image(cityMapTextureKeys.night, `${assetRoot}/city-night.webp`);
  scene.load.image(cityMapTextureKeys.day, `${assetRoot}/city-day.webp`);

  for (const [mode, textureKey] of Object.entries(modeTicketTextureKeys)) {
    scene.load.image(textureKey, `${assetRoot}/ticket-${mode}.png`);
  }

  for (const [mode, textureKey] of Object.entries(modeVehicleTextureKeys)) {
    scene.load.image(textureKey, `${assetRoot}/vehicle-${mode}.png`);
  }
}
