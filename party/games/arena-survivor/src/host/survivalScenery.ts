import Phaser from "phaser";
import { frostfireObstacles, frostfireWorld } from "../survivalWorld.js";

/** Repeating ground with soft terrain patches and sparse obstacle islands. */
export function createSurvivalScenery(scene: Phaser.Scene) {
  const images: Array<Phaser.GameObjects.Image | Phaser.GameObjects.TileSprite> = [];
  const sceneryKeys = ["survival-rocks", "survival-dead-tree", "survival-ground-frost", "survival-ground-embers"];
  if (![frostfireWorld.terrainKey, ...sceneryKeys].every(key => scene.textures.exists(key))) return images;
  const texture = scene.textures.get(frostfireWorld.terrainKey);
  const source = texture.getSourceImage() as HTMLImageElement;
  const tileScale = 512 / source.width;
  for (let row = 0; row < 3; row++) for (let column = 0; column < 4; column++) {
    images.push(scene.add.tileSprite(column * 1200, row * 1200, 1200, 1200, frostfireWorld.terrainKey)
      .setOrigin(0).setTileScale(tileScale).setTilePosition(column * 1200 / tileScale, row * 1200 / tileScale).setDepth(-50));
  }
  // Transparent feathered edges blend the two extra ground textures into the
  // base without rectangular biome seams. Placement stays stable across clients.
  for (let row = 0; row < 4; row++) for (let column = 0; column < 5; column++) {
    const index = row * 5 + column;
    const frost = (column + row * 2) % 5 < 3;
    const x = 450 + column * 950 + Math.sin(index * 2.4) * 130;
    const y = 400 + row * 900 + Math.cos(index * 1.7) * 110;
    images.push(scene.add.image(x, y, frost ? "survival-ground-frost" : "survival-ground-embers")
      .setDisplaySize(900 + index % 3 * 140, 760 + index % 4 * 100)
      .setFlipX(index % 2 === 0).setFlipY(index % 3 === 0).setAlpha(0.8).setDepth(-40));
  }
  frostfireObstacles.forEach((o, i) => {
    const key = ["survival-rocks", "survival-dead-tree"][i % 2];
    images.push(scene.add.image(o.x, o.y, key).setDisplaySize(o.radius * 2.5, o.radius * 2.5)
      .setFlipX(i % 2 === 0).setTint(o.x > 2900 && o.y > 1900 ? 0xc9a58f : 0xffffff).setDepth(2));
  });
  return images;
}
