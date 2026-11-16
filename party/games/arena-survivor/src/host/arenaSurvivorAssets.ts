import Phaser from "phaser";
import type { ArenaSurvivorVisualTheme } from "../protocol.js";
import {
  arenaSurvivorDefaultVisualTheme,
  resolveArenaSurvivorCharacterThemeAssetId,
  resolveArenaSurvivorEnemyThemeAssetId,
  resolveArenaSurvivorThemeAssetPath,
  resolveArenaSurvivorWeaponThemeAssetId
} from "../visualThemes.js";

const characterAssetIds = [
  { id: "schrotto-scharfschuss", assetId: "schrotto-scharfschuss" },
  { id: "kloppbert-keulenwucht", assetId: "kloppbert-keulenwucht" },
  { id: "funkenberta-flaemmchen", assetId: "funkenberta-flaemmchen" },
  { id: "kanni-baldrian", assetId: "kanni-baldrian" },
  { id: "doktor-knolle", assetId: "doktor-knolle" },
  { id: "sir-pampel-panzer", assetId: "sir-pampel-panzer" },
  { id: "flitzelotte", assetId: "flitzelotte" },
  { id: "professor-paradox", assetId: "professor-paradox" },
  { id: "rundling-allround", assetId: "rundling-allround" },
  { id: "pruegler-brawler", assetId: "pruegler-brawler" },
  { id: "jaeger-ranger", assetId: "jaeger-ranger" },
  { id: "gluecksknolle-lucky", assetId: "gluecksknolle-lucky" },
  { id: "ackerling-farmer", assetId: "ackerling-farmer" }
] as const;
const enemyAssetIds = [
  { id: "slime-blob", assetId: "slime-blob" },
  { id: "fang-crawler", assetId: "fang-crawler" },
  { id: "needle-runner", assetId: "fang-crawler" },
  { id: "stone-brute", assetId: "stone-brute" },
  { id: "shell-bulwark", assetId: "stone-brute" },
  { id: "ember-wisp", assetId: "ember-wisp" },
  { id: "toxic-shroom", assetId: "toxic-shroom" },
  { id: "ash-spitter", assetId: "ember-wisp" },
  { id: "plague-lobber", assetId: "toxic-shroom" },
  { id: "iron-mauler", assetId: "stone-brute" },
  { id: "loot-runner", assetId: "fang-crawler" },
  { id: "charger-hulk", assetId: "stone-brute" },
  { id: "elite-spitter", assetId: "ember-wisp" },
  { id: "scrap-goliath", assetId: "scrap-goliath" },
  { id: "crimson-overlord", assetId: "crimson-overlord" }
] as const;
const weaponIds = [
  "cleaver",
  "coil-rifle",
  "ember-wand",
  "frost-orb",
  "gear-launcher",
  "halberd",
  "hunter-bow",
  "lance",
  "mace",
  "prism-scepter",
  "rust-blade",
  "scrap-smg",
  "spear",
  "spark-rod",
  "stick",
  "stone",
  "survivor-pistol",
  "twin-daggers",
  "venom-siphon",
  "war-hammer",
  "pitchfork"
] as const;

const arenaSurvivorBackgroundKeys: Record<ArenaSurvivorVisualTheme, string> = {
  classic: "arena-survivor-background",
  "obsidian-relay": "arena-survivor-background-obsidian-relay",
  "frostfire-saga": "arena-survivor-background-frostfire-saga",
  "marshmallow-mayhem": "arena-survivor-background-marshmallow-mayhem"
};

export interface ArenaSurvivorAssetDescriptor {
  id: string;
  spriteKey: string;
  spritePath: string;
  portraitKey: string;
  portraitPath: string;
}

export const arenaSurvivorCharacterAssets: readonly ArenaSurvivorAssetDescriptor[] = characterAssetIds.map((entry) => ({
  id: entry.id,
  spriteKey: `arena-survivor-character-${entry.id}`,
  spritePath: `/arena-survivor/themes/classic/characters/sprites/${entry.assetId}.svg`,
  portraitKey: `arena-survivor-character-portrait-${entry.id}`,
  portraitPath: `/arena-survivor/themes/classic/characters/portraits/${entry.assetId}.svg`
}));

export const arenaSurvivorEnemyAssets: readonly ArenaSurvivorAssetDescriptor[] = enemyAssetIds.map((entry) => ({
  id: entry.id,
  spriteKey: `arena-survivor-enemy-${entry.id}`,
  spritePath: `/arena-survivor/themes/classic/enemies/sprites/${entry.assetId}.svg`,
  portraitKey: `arena-survivor-enemy-portrait-${entry.id}`,
  portraitPath: `/arena-survivor/themes/classic/enemies/portraits/${entry.assetId}.svg`
}));

export const arenaSurvivorWeaponCarryAssets: ReadonlyArray<{
  id: string;
  spriteKey: string;
  spritePath: string;
}> = weaponIds.map((id) => ({
  id,
  spriteKey: `arena-survivor-weapon-carry-${id}`,
  spritePath: `/arena-survivor/themes/classic/weapons/carry/${id}_carry.svg`
}));

const obsidianCharacterAssets: readonly ArenaSurvivorAssetDescriptor[] = characterAssetIds.map((entry) => {
  const assetId = resolveArenaSurvivorCharacterThemeAssetId(entry.id);
  return {
    id: entry.id,
    spriteKey: `arena-survivor-obsidian-character-${entry.id}`,
    spritePath: `/arena-survivor/themes/obsidian-relay/characters/${assetId}.svg`,
    portraitKey: `arena-survivor-obsidian-character-portrait-${entry.id}`,
    portraitPath: `/arena-survivor/themes/obsidian-relay/characters/${assetId}.svg`
  };
});

const obsidianEnemyAssets: readonly ArenaSurvivorAssetDescriptor[] = enemyAssetIds.map((entry) => {
  const assetId = resolveArenaSurvivorEnemyThemeAssetId(entry.id);
  return {
    id: entry.id,
    spriteKey: `arena-survivor-obsidian-enemy-${entry.id}`,
    spritePath: `/arena-survivor/themes/obsidian-relay/enemies/${assetId}.svg`,
    portraitKey: `arena-survivor-obsidian-enemy-portrait-${entry.id}`,
    portraitPath: `/arena-survivor/themes/obsidian-relay/enemies/${assetId}.svg`
  };
});

const obsidianWeaponCarryAssets = weaponIds.map((id) => ({
  id,
  spriteKey: `arena-survivor-obsidian-weapon-${id}`,
  spritePath: `/arena-survivor/themes/obsidian-relay/weapons/${resolveArenaSurvivorWeaponThemeAssetId(id)}.svg`
}));

const frostfireCharacterAssets: readonly ArenaSurvivorAssetDescriptor[] = characterAssetIds.map((entry) => {
  const assetId = resolveArenaSurvivorCharacterThemeAssetId(entry.id, "frostfire-saga");
  const portraitPath = resolveArenaSurvivorThemeAssetPath("frostfire-saga", "characters", assetId);
  return {
    id: entry.id,
    spriteKey: `arena-survivor-frostfire-character-${entry.id}`,
    spritePath: `/arena-survivor/themes/frostfire-saga/characters-unarmed/${assetId}.png`,
    portraitKey: `arena-survivor-frostfire-character-portrait-${entry.id}`,
    portraitPath
  };
});

const frostfireEnemyAssets: readonly ArenaSurvivorAssetDescriptor[] = enemyAssetIds.map((entry) => {
  const assetId = resolveArenaSurvivorEnemyThemeAssetId(entry.id, "frostfire-saga");
  const assetPath = resolveArenaSurvivorThemeAssetPath("frostfire-saga", "enemies", assetId);
  return {
    id: entry.id,
    spriteKey: `arena-survivor-frostfire-enemy-${entry.id}`,
    spritePath: assetPath,
    portraitKey: `arena-survivor-frostfire-enemy-portrait-${entry.id}`,
    portraitPath: assetPath
  };
});

const frostfireWeaponCarryAssets = weaponIds.map((id) => ({
  id,
  spriteKey: `arena-survivor-frostfire-weapon-${id}`,
  spritePath: resolveArenaSurvivorThemeAssetPath(
    "frostfire-saga",
    "weapons",
    resolveArenaSurvivorWeaponThemeAssetId(id, "frostfire-saga")
  )
}));

const marshmallowCharacterAssets: readonly ArenaSurvivorAssetDescriptor[] = characterAssetIds.map((entry) => ({
  id: entry.id,
  spriteKey: `arena-survivor-marshmallow-torso-${entry.id}`,
  spritePath: `/arena-survivor/themes/marshmallow-mayhem/characters/torsos/${entry.id}.png`,
  portraitKey: `arena-survivor-marshmallow-character-portrait-${entry.id}`,
  portraitPath: `/arena-survivor/themes/marshmallow-mayhem/characters/portraits/${entry.id}.png`
}));

const marshmallowEnemyAssets: readonly ArenaSurvivorAssetDescriptor[] = enemyAssetIds.map((entry) => {
  const assetId = resolveArenaSurvivorEnemyThemeAssetId(entry.id, "marshmallow-mayhem");
  const assetPath = resolveArenaSurvivorThemeAssetPath("marshmallow-mayhem", "enemies", assetId);
  return {
    id: entry.id,
    spriteKey: `arena-survivor-marshmallow-enemy-${entry.id}`,
    spritePath: assetPath,
    portraitKey: `arena-survivor-marshmallow-enemy-portrait-${entry.id}`,
    portraitPath: assetPath
  };
});

const marshmallowWeaponCarryAssets = weaponIds.map((id) => ({
  id,
  spriteKey: `arena-survivor-marshmallow-weapon-${id}`,
  spritePath: resolveArenaSurvivorThemeAssetPath(
    "marshmallow-mayhem",
    "weapons",
    resolveArenaSurvivorWeaponThemeAssetId(id, "marshmallow-mayhem")
  )
}));

const frostfirePickupAssets = [
  {
    id: "health",
    spriteKey: "arena-survivor-frostfire-pickup-health",
    spritePath: "/arena-survivor/themes/frostfire-saga/pickups/health.png"
  },
  {
    id: "material",
    spriteKey: "arena-survivor-frostfire-pickup-material",
    spritePath: "/arena-survivor/themes/frostfire-saga/pickups/material.png"
  }
] as const;

const marshmallowPickupAssets = [
  {
    id: "health",
    spriteKey: "arena-survivor-marshmallow-pickup-health",
    spritePath: "/arena-survivor/themes/marshmallow-mayhem/pickups/health.png"
  },
  {
    id: "material",
    spriteKey: "arena-survivor-marshmallow-pickup-material",
    spritePath: "/arena-survivor/themes/marshmallow-mayhem/pickups/material.png"
  }
] as const;

export const arenaSurvivorMarshmallowRigKeys = {
  hand: "arena-survivor-marshmallow-rig-hand",
  foot: "arena-survivor-marshmallow-rig-foot",
  helmet: "arena-survivor-marshmallow-rig-helmet"
} as const;

const marshmallowHeadbandVariants = ["red", "blue", "green", "gold", "violet", "teal"] as const;

/**
 * Longest texture edge kept in memory for sprites. Characters, enemies, pickups
 * and weapons are drawn at 30-110 world units; at the arena's maximum camera
 * zoom on a 4K screen that is at most ~330 px. Some source images are several
 * times larger, and the Canvas renderer would downscale them again in every
 * frame for every sprite.
 */
const SPRITE_MAX_TEXTURE_EDGE = 384;
/** Hands and feet of the marshmallow rig are only ~8 world units wide. */
const RIG_LIMB_MAX_TEXTURE_EDGE = 128;
/** Helmet and headbands span the torso width. */
const RIG_HEADGEAR_MAX_TEXTURE_EDGE = 256;

interface ArenaSurvivorImageAsset {
  key: string;
  path: string;
  /** 0 keeps the texture as loaded (backgrounds fill the whole screen). */
  maxEdge: number;
}

/** Keys queued once per texture manager, so a missing file is not retried every state. */
const requestedTextureKeys = new WeakMap<Phaser.Textures.TextureManager, Set<string>>();
/** Keys already brought down to their size cap. */
const optimizedTextureKeys = new WeakMap<Phaser.Textures.TextureManager, Set<string>>();

function keySet(
  registry: WeakMap<Phaser.Textures.TextureManager, Set<string>>,
  textures: Phaser.Textures.TextureManager
): Set<string> {
  let keys = registry.get(textures);

  if (!keys) {
    keys = new Set();
    registry.set(textures, keys);
  }

  return keys;
}

function resolveWeaponAssetsForTheme(theme: ArenaSurvivorVisualTheme): ReadonlyArray<{
  id: string;
  spriteKey: string;
  spritePath: string;
}> {
  if (theme === "obsidian-relay") {
    return obsidianWeaponCarryAssets;
  }
  if (theme === "frostfire-saga") {
    return frostfireWeaponCarryAssets;
  }
  if (theme === "marshmallow-mayhem") {
    return marshmallowWeaponCarryAssets;
  }
  return arenaSurvivorWeaponCarryAssets;
}

/**
 * Everything the arena canvas draws for one visual theme.
 *
 * Portraits are not part of it: the host canvas never draws them (the HUD and
 * the lobby use image URLs), and loading all four themes up front cost start-up
 * time and memory for three sets nobody sees.
 */
function collectThemeImageAssets(theme: ArenaSurvivorVisualTheme): ArenaSurvivorImageAsset[] {
  const assets: ArenaSurvivorImageAsset[] = [];
  const addSprite = (key: string, path: string, maxEdge = SPRITE_MAX_TEXTURE_EDGE) => {
    assets.push({ key, path, maxEdge });
  };

  for (const asset of [...resolveCharacterAssetsForTheme(theme), ...resolveEnemyAssetsForTheme(theme)]) {
    addSprite(asset.spriteKey, asset.spritePath);
  }

  for (const asset of resolveWeaponAssetsForTheme(theme)) {
    addSprite(asset.spriteKey, asset.spritePath);
  }

  if (theme === "frostfire-saga") {
    for (const asset of frostfirePickupAssets) {
      addSprite(asset.spriteKey, asset.spritePath);
    }
  }

  if (theme === "marshmallow-mayhem") {
    for (const asset of marshmallowPickupAssets) {
      addSprite(asset.spriteKey, asset.spritePath);
    }

    addSprite(
      arenaSurvivorMarshmallowRigKeys.hand,
      "/arena-survivor/themes/marshmallow-mayhem/rig/hand-knob.png",
      RIG_LIMB_MAX_TEXTURE_EDGE
    );
    addSprite(
      arenaSurvivorMarshmallowRigKeys.foot,
      "/arena-survivor/themes/marshmallow-mayhem/rig/foot-knob.png",
      RIG_LIMB_MAX_TEXTURE_EDGE
    );
    addSprite(
      arenaSurvivorMarshmallowRigKeys.helmet,
      "/arena-survivor/themes/marshmallow-mayhem/rig/helmet.png",
      RIG_HEADGEAR_MAX_TEXTURE_EDGE
    );

    for (const color of marshmallowHeadbandVariants) {
      addSprite(
        `arena-survivor-marshmallow-rig-headband-${color}`,
        `/arena-survivor/themes/marshmallow-mayhem/rig/headbands/headband-${color}.png`,
        RIG_HEADGEAR_MAX_TEXTURE_EDGE
      );
    }
  }

  if (theme === "frostfire-saga") for (const name of ["ground", "ground-frost", "ground-embers", "shop", "forge", "chest", "rare-chest", "rocks", "dead-tree"]) {
    assets.push({ key: `survival-${name}`, path: `/arena-survivor/themes/frostfire-saga/survival/${name}.png`, maxEdge: name === "ground" ? 0 : name.startsWith("ground-") ? 1024 : 384 });
  }
  assets.push({
    key: arenaSurvivorBackgroundKeys[theme],
    path: arenaSurvivorBackgroundPaths[theme],
    maxEdge: 0
  });

  return assets;
}

const arenaSurvivorBackgroundPaths: Record<ArenaSurvivorVisualTheme, string> = {
  classic: "/arena-survivor/themes/classic/backgrounds/arena-field.svg",
  "obsidian-relay": "/arena-survivor/themes/obsidian-relay/backgrounds/relay-vault.svg",
  "frostfire-saga": "/arena-survivor/themes/frostfire-saga/backgrounds/frostfire-arena.png",
  "marshmallow-mayhem": "/arena-survivor/themes/marshmallow-mayhem/backgrounds/cocoa-clearing.png"
};

/**
 * Replaces an oversized texture with a copy scaled to `maxEdge`.
 *
 * The Canvas renderer draws every sprite with `drawImage`; a 1024 px source
 * shown at 60 px is resampled in every frame for every sprite, and looks no
 * better than a 128 px one. Runs the moment the file lands in the texture
 * cache: the full-size texture is taken out right away, so no sprite can pick
 * it up, and the smaller copy is added once it has been encoded back into an
 * image. Canvas-backed textures would be available sooner, but Chrome draws
 * canvas sources noticeably slower than image sources, so they are only the
 * fallback. In between, `textures.exists(key)` is false and the renderer uses
 * the same shape fallback it uses while a file is still loading.
 */
function downscaleTexture(
  scene: Phaser.Scene,
  key: string,
  maxEdge: number,
  onReady?: () => void
): void {
  const textures = scene.textures;
  const source = textures.get(key).getSourceImage() as HTMLImageElement | HTMLCanvasElement;
  const width = source.width;
  const height = source.height;

  if (!width || !height || Math.max(width, height) <= maxEdge) {
    return;
  }

  const scale = maxEdge / Math.max(width, height);
  const canvas = document.createElement("canvas");
  canvas.width = Math.max(1, Math.round(width * scale));
  canvas.height = Math.max(1, Math.round(height * scale));
  const context = canvas.getContext("2d");

  if (!context) {
    return;
  }

  context.imageSmoothingEnabled = true;
  context.imageSmoothingQuality = "high";
  context.drawImage(source, 0, 0, canvas.width, canvas.height);
  textures.remove(key);

  const addCanvasFallback = () => {
    if (!textures.exists(key)) {
      textures.addCanvas(key, canvas);
      onReady?.();
    }
  };

  canvas.toBlob((blob) => {
    if (!blob) {
      addCanvasFallback();
      return;
    }

    const url = URL.createObjectURL(blob);
    const image = new Image();
    image.src = url;
    image
      .decode()
      .then(() => {
        if (!textures.exists(key)) {
          textures.addImage(key, image);
          onReady?.();
        }
      })
      .catch(addCanvasFallback)
      .finally(() => URL.revokeObjectURL(url));
  }, "image/png");
}

/**
 * Downscales each of the batch's images as soon as it is loaded - before the
 * next frame could create a sprite from the full-size texture.
 */
function watchLoadedImages(
  scene: Phaser.Scene,
  assets: readonly ArenaSurvivorImageAsset[],
  onTextureReady?: () => void
): void {
  const maxEdgeByKey = new Map(assets.map((asset) => [asset.key, asset.maxEdge]));
  const optimized = keySet(optimizedTextureKeys, scene.textures);
  const onFileComplete = (key: string) => {
    const maxEdge = maxEdgeByKey.get(key);

    if (maxEdge === undefined || optimized.has(key) || !scene.textures.exists(key)) {
      return;
    }

    optimized.add(key);

    if (maxEdge > 0) {
      downscaleTexture(scene, key, maxEdge, onTextureReady);
    }
  };

  scene.load.on(Phaser.Loader.Events.FILE_COMPLETE, onFileComplete);
  scene.load.once(Phaser.Loader.Events.COMPLETE, () => {
    scene.load.off(Phaser.Loader.Events.FILE_COMPLETE, onFileComplete);
  });
}

/** Queues the theme's images that are neither loaded nor already requested. */
function queueThemeImages(scene: Phaser.Scene, theme: ArenaSurvivorVisualTheme): ArenaSurvivorImageAsset[] {
  const requested = keySet(requestedTextureKeys, scene.textures);
  const assets = collectThemeImageAssets(theme);
  let queued = false;

  for (const asset of assets) {
    if (scene.textures.exists(asset.key) || requested.has(asset.key)) {
      continue;
    }

    requested.add(asset.key);
    queued = true;

    if (asset.path.endsWith(".svg")) {
      scene.load.svg(asset.key, asset.path);
    } else {
      scene.load.image(asset.key, asset.path);
    }
  }

  return queued ? assets : [];
}

/**
 * Preload entry point: only the theme the room has selected.
 *
 * Another theme is fetched on demand by `ensureArenaSurvivorThemeAssets` when a
 * state with a different theme arrives; the renderer draws its shape fallbacks
 * until then.
 */
export function loadArenaSurvivorAssets(
  scene: Phaser.Scene,
  theme: ArenaSurvivorVisualTheme = arenaSurvivorDefaultVisualTheme,
  onTextureReady?: () => void
): void {
  const assets = queueThemeImages(scene, theme);

  if (assets.length > 0) {
    watchLoadedImages(scene, assets, onTextureReady);
  }
}

/**
 * Makes sure a theme's images are loaded, loading them in the background if
 * not. Returns true when everything was already there; otherwise `onLoaded`
 * runs once the loader finishes.
 */
export function ensureArenaSurvivorThemeAssets(
  scene: Phaser.Scene,
  theme: ArenaSurvivorVisualTheme,
  onLoaded: () => void
): boolean {
  const assets = queueThemeImages(scene, theme);

  if (assets.length === 0) {
    return true;
  }

  watchLoadedImages(scene, assets, onLoaded);
  scene.load.once(Phaser.Loader.Events.COMPLETE, () => {
    onLoaded();
  });

  if (!scene.load.isLoading()) {
    scene.load.start();
  }

  return false;
}

function resolveCharacterAssetsForTheme(theme: ArenaSurvivorVisualTheme): readonly ArenaSurvivorAssetDescriptor[] {
  if (theme === "obsidian-relay") {
    return obsidianCharacterAssets;
  }
  if (theme === "frostfire-saga") {
    return frostfireCharacterAssets;
  }
  if (theme === "marshmallow-mayhem") {
    return marshmallowCharacterAssets;
  }
  return arenaSurvivorCharacterAssets;
}

function resolveEnemyAssetsForTheme(theme: ArenaSurvivorVisualTheme): readonly ArenaSurvivorAssetDescriptor[] {
  if (theme === "obsidian-relay") {
    return obsidianEnemyAssets;
  }
  if (theme === "frostfire-saga") {
    return frostfireEnemyAssets;
  }
  if (theme === "marshmallow-mayhem") {
    return marshmallowEnemyAssets;
  }
  return arenaSurvivorEnemyAssets;
}

export function resolveArenaSurvivorPlayerSpriteKey(
  characterId: string,
  theme: ArenaSurvivorVisualTheme = arenaSurvivorDefaultVisualTheme
): string {
  const assets = resolveCharacterAssetsForTheme(theme);
  const asset = assets.find((entry) => entry.id === characterId);
  return asset?.spriteKey ?? arenaSurvivorCharacterAssets[0].spriteKey;
}

export function resolveArenaSurvivorPlayerPortraitKey(
  characterId: string,
  theme: ArenaSurvivorVisualTheme = arenaSurvivorDefaultVisualTheme
): string {
  const assets = resolveCharacterAssetsForTheme(theme);
  const asset = assets.find((entry) => entry.id === characterId);
  return asset?.portraitKey ?? arenaSurvivorCharacterAssets[0].portraitKey;
}

export function resolveArenaSurvivorEnemySpriteKey(
  definitionId: string,
  theme: ArenaSurvivorVisualTheme = arenaSurvivorDefaultVisualTheme
): string | null {
  const assets = resolveEnemyAssetsForTheme(theme);
  const asset = assets.find((entry) => entry.id === definitionId);
  return asset?.spriteKey ?? null;
}

export function resolveArenaSurvivorEnemyPortraitKey(
  definitionId: string,
  theme: ArenaSurvivorVisualTheme = arenaSurvivorDefaultVisualTheme
): string | null {
  const assets = resolveEnemyAssetsForTheme(theme);
  const asset = assets.find((entry) => entry.id === definitionId);
  return asset?.portraitKey ?? null;
}

export function resolveArenaSurvivorWeaponCarrySpriteKey(
  weaponId: string,
  theme: ArenaSurvivorVisualTheme = arenaSurvivorDefaultVisualTheme
): string | null {
  const asset = resolveWeaponAssetsForTheme(theme).find((entry) => entry.id === weaponId);
  return asset?.spriteKey ?? null;
}

export function resolveArenaSurvivorPickupSpriteKey(
  kind: "health" | "material",
  theme: ArenaSurvivorVisualTheme
): string | null {
  if (theme === "frostfire-saga") {
    return frostfirePickupAssets.find((asset) => asset.id === kind)?.spriteKey ?? null;
  }
  if (theme === "marshmallow-mayhem") {
    return marshmallowPickupAssets.find((asset) => asset.id === kind)?.spriteKey ?? null;
  }
  return null;
}

export function resolveArenaSurvivorMarshmallowHeadbandKey(playerColor: string): string {
  const normalized = playerColor.toLowerCase();
  const variant = normalized === "#ef4444" || normalized === "#f97316" || normalized === "#ec4899"
    ? "red"
    : normalized === "#f59e0b"
      ? "gold"
      : normalized === "#10b981"
        ? "green"
        : normalized === "#8b5cf6"
          ? "violet"
          : normalized === "#14b8a6"
            ? "teal"
            : "blue";
  return `arena-survivor-marshmallow-rig-headband-${variant}`;
}

export function resolveArenaSurvivorBackgroundKey(
  theme: ArenaSurvivorVisualTheme = arenaSurvivorDefaultVisualTheme
): string {
  return arenaSurvivorBackgroundKeys[theme];
}
