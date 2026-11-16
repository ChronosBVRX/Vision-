import { createCombatFeedback } from "./combatFeedback.js";
import Phaser from "phaser";
import { createSurvivalOverlay } from "./survivalOverlay.js";
import { createSurvivalScenery } from "./survivalScenery.js";
import type { ArenaSurvivorState } from "../protocol.js";
import {
  arenaSurvivorDefaultVisualTheme,
  isArenaSurvivorVisualTheme
} from "../visualThemes.js";
import { arenaSurvivorRoomSettingKeys } from "../server/arenaSurvivorConfig.js";
import {
  createArenaSurvivorSpriteLayer,
  applyArenaSurvivorCamera,
  destroyArenaSurvivorSpriteLayer,
  drawArenaSurvivorBackground,
  drawArenaSurvivorEntities,
  drawArenaSurvivorPlayerHealthBars,
  hideArenaSurvivorSpriteLayer,
  resolveArenaSurvivorRenderMeta,
  syncArenaSurvivorSpriteLayer
} from "./ArenaSurvivorRenderer.js";
import { createArenaHud } from "./hud/ArenaHud.js";
import {
  ensureArenaSurvivorThemeAssets,
  loadArenaSurvivorAssets,
  resolveArenaSurvivorBackgroundKey
} from "./arenaSurvivorAssets.js";
import { createArenaSurvivorMotion } from "./arenaSurvivorMotion.js";
import { renderRoundScreens } from "./roundScreens.js";
import { bindPlatformTheme } from "./platformTheme.js";

/** Matches the dark shade the arena artwork is composited against. */
const ARENA_STAGE_COLOR = "#120a06";

interface HostClientLike {
  subscribe(callback: (state: HostAppStateLike) => void): () => void;
  selectGame(gameId: string): void;
  sendGameHostAction(gameId: string, action: unknown): void;
}

interface HostAppStateLike {
  game?: {
    roundNumber?: number;
    phase?: string;
    state?: unknown;
  } | null;
  room?: Parameters<ReturnType<typeof createArenaHud>["update"]>[1] & {
    selectedGameSettings?: Record<string, unknown>;
  };
}

/** The theme the room has selected right now, read without keeping a subscription. */
function readSelectedVisualTheme(client: HostClientLike | undefined) {
  let theme = arenaSurvivorDefaultVisualTheme;

  if (!client) {
    return theme;
  }

  const unsubscribe = client.subscribe((state) => {
    const selected = state.room?.selectedGameSettings?.[arenaSurvivorRoomSettingKeys.visualTheme];

    if (isArenaSurvivorVisualTheme(selected)) {
      theme = selected;
    }
  });
  unsubscribe();
  return theme;
}

export class ArenaSurvivorHostScene extends Phaser.Scene {
  private combatFeedback?: ReturnType<typeof createCombatFeedback>;
  private unsubscribe?: () => void;
  private survivalOverlay?: ReturnType<typeof createSurvivalOverlay>;
  private survivalTiles: ReturnType<typeof createSurvivalScenery> = [];
  private language: "de" | "en" = "de";
  private arenaBackground?: Phaser.GameObjects.Image;
  private arenaBackgroundShade?: Phaser.GameObjects.Rectangle;
  private arenaGraphics?: Phaser.GameObjects.Graphics;
  private entityGraphics?: Phaser.GameObjects.Graphics;
  private playerHealthGraphics?: Phaser.GameObjects.Graphics;
  private hud?: ReturnType<typeof createArenaHud>;
  private spriteLayer = createArenaSurvivorSpriteLayer();
  private motion = createArenaSurvivorMotion();
  private lastRoundNumber: number | null = null;
  private lastViewportKey = "";
  /** True while the game's own intro screen covers the arena. */
  private roundScreenActive = false;
  /** A state arrived (or textures finished loading) that has not been drawn yet. */
  private renderPending = false;

  constructor() {
    super("ArenaSurvivorHostScene");
  }

  preload(): void {
    loadArenaSurvivorAssets(
      this,
      readSelectedVisualTheme(this.registry.get("hostClient") as HostClientLike | undefined),
      // Downscaled copies land a moment after loading; redraw when they do.
      () => {
        this.renderPending = true;
      }
    );
  }

  create(): void {
    bindPlatformTheme(this.registry);
    this.combatFeedback = createCombatFeedback(this);
    const client = this.registry.get("hostClient") as HostClientLike;
    const canvasContext = this.game.canvas.getContext("2d");

    if (canvasContext) {
      canvasContext.imageSmoothingEnabled = true;
      canvasContext.imageSmoothingQuality = "high";
    }

    // The stage behind the arena artwork, not a platform surface: the warm
    // paper of the light theme showed up as bright bands around the arena.
    this.cameras.main.setBackgroundColor(ARENA_STAGE_COLOR);
    this.arenaBackground = this.add.image(0, 0, resolveArenaSurvivorBackgroundKey()).setOrigin(0, 0);
    this.arenaBackground.setDepth(-50);
    this.arenaBackground.setVisible(false);
    this.arenaBackgroundShade = this.add.rectangle(0, 0, 1, 1, 0x24120d, 0.3).setOrigin(0, 0);
    this.arenaBackgroundShade.setDepth(-49);
    this.arenaBackgroundShade.setVisible(false);
    this.arenaGraphics = this.add.graphics();
    this.entityGraphics = this.add.graphics();
    this.playerHealthGraphics = this.add.graphics().setDepth(12);
    this.survivalOverlay = createSurvivalOverlay(this);
    this.hud = createArenaHud({
      onRestartRun: () => {
        client.sendGameHostAction("arena-survivor", { type: "restart-run" });
      },
      onReturnToSetup: () => {
        client.selectGame("arena-survivor");
      }
    });

    this.unsubscribe = client.subscribe((state) => {
      this.language = state.room?.language === "en" ? "en" : "de";
      // Intro and result screens belong to this game, not the platform.
      if (renderRoundScreens(this, state)) {
        this.combatFeedback?.clear();
        this.survivalOverlay?.hide();
        this.roundScreenActive = true;
        return;
      }

      this.roundScreenActive = false;
      const gameState = (state.game?.state ?? null) as ArenaSurvivorState | null;

      if (!this.arenaGraphics || !this.entityGraphics || !this.playerHealthGraphics || !this.hud) {
        return;
      }

      if (!gameState) {
        this.combatFeedback?.clear();
        this.survivalOverlay?.hide();
        this.arenaBackground?.setVisible(false);
        this.arenaBackgroundShade?.setVisible(false);
        this.arenaGraphics.clear();
        this.entityGraphics.clear();
        this.playerHealthGraphics.clear();
        hideArenaSurvivorSpriteLayer(this.spriteLayer);
        this.motion.clear();
        this.renderPending = false;
        this.lastRoundNumber = null;
        this.lastViewportKey = "";
        this.hud.update(null, state.room);
        return;
      }

      const texturesReady = ensureArenaSurvivorThemeAssets(this, gameState.visualTheme, () => {
        // Sprites replace the shape fallbacks as soon as the theme is in.
        this.syncBackground(this.motion.base);
        this.renderPending = true;
      });
      const isNewState = gameState !== this.motion.base;
      this.motion.accept(
        gameState,
        state.game?.phase === "playing" && gameState.result.outcome === "running" && !gameState.survival?.pause,
        performance.now()
      );

      if (isNewState) {
        this.renderPending = true;
      }

      const viewportKey = `${gameState.arenaWidth}x${gameState.arenaHeight}:${gameState.visualTheme}`;
      const shouldResetArena =
        this.lastRoundNumber !== state.game?.roundNumber || this.lastViewportKey !== viewportKey;

      this.combatFeedback?.accept(gameState, shouldResetArena, this.motion.live);

      if (shouldResetArena || !texturesReady) {
        this.syncBackground(gameState);
      }

      if (shouldResetArena) {
        drawArenaSurvivorBackground(
          this,
          this.arenaGraphics,
          gameState,
          resolveArenaSurvivorRenderMeta(this, gameState)
        );
        this.lastRoundNumber = state.game?.roundNumber ?? null;
        this.lastViewportKey = viewportKey;
      }

      // The HUD follows the server state; positions are drawn per frame.
      this.hud.update(gameState, state.room);
    });

    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.combatFeedback?.clear();
      this.combatFeedback = undefined;
      this.unsubscribe?.();
      this.survivalOverlay?.destroy();
      this.survivalTiles.forEach(tile => tile.destroy()); this.survivalTiles = [];
      this.unsubscribe = undefined;
      this.arenaBackground?.destroy();
      this.arenaBackground = undefined;
      this.arenaBackgroundShade?.destroy();
      this.arenaBackgroundShade = undefined;
      this.arenaGraphics?.destroy();
      this.arenaGraphics = undefined;
      this.entityGraphics?.destroy();
      this.entityGraphics = undefined;
      this.playerHealthGraphics?.destroy();
      this.playerHealthGraphics = undefined;
      destroyArenaSurvivorSpriteLayer(this.spriteLayer);
      this.motion.clear();
      this.renderPending = false;
      this.roundScreenActive = false;
      this.hud?.destroy();
      this.hud = undefined;
      this.lastRoundNumber = null;
      this.lastViewportKey = "";
    });
  }

  /**
   * Draws the arena every frame from the smoothed copy of the latest state.
   *
   * Before, the arena was redrawn only when a server state arrived, so motion
   * advanced in ~31 Hz steps regardless of the display rate. While the wave is
   * not running nothing moves, and the arena is only redrawn when something
   * changed.
   */
  update(): void {
    if (
      this.roundScreenActive ||
      !this.entityGraphics ||
      !this.playerHealthGraphics
    ) {
      return;
    }

    const nowMs = performance.now();
    const base = this.motion.base;

    if (!base) {
      return;
    }

    if (!this.renderPending && !this.motion.live) {
      return;
    }

    if (!this.motion.advance(nowMs) || !this.motion.view) {
      return;
    }

    this.renderPending = false;
    const view = this.motion.view;
    const meta = resolveArenaSurvivorRenderMeta(this, view);
    applyArenaSurvivorCamera(this, meta);
    drawArenaSurvivorEntities(this, this.entityGraphics, view, meta);
    drawArenaSurvivorPlayerHealthBars(this.playerHealthGraphics, view);
    syncArenaSurvivorSpriteLayer(this, this.spriteLayer, view, meta);
    this.survivalOverlay?.update(view, this.language);
  }

  private syncBackground(gameState: ArenaSurvivorState | null): void {
    if (!gameState) {
      return;
    }

    if (this.arenaBackground) {
      const backgroundKey = resolveArenaSurvivorBackgroundKey(gameState.visualTheme);
      // A background still loading would show Phaser's "missing" texture
      // stretched over the arena; the dark stage colour is the better wait.
      const backgroundReady = this.textures.exists(backgroundKey);

      if (backgroundReady && this.arenaBackground.texture.key !== backgroundKey) {
        this.arenaBackground.setTexture(backgroundKey);
      }

      this.arenaBackground.setVisible(backgroundReady && !gameState.survival);
      if (gameState.survival && !this.survivalTiles.length) {
        this.survivalTiles = createSurvivalScenery(this);
      }
      this.survivalTiles.forEach(tile => tile.setVisible(Boolean(gameState.survival)));
      this.arenaBackground.setPosition(0, 0);
      this.arenaBackground.setDisplaySize(gameState.arenaWidth, gameState.arenaHeight);
    }

    if (this.arenaBackgroundShade) {
      const useShade = gameState.visualTheme === "marshmallow-mayhem";
      this.arenaBackgroundShade.setVisible(useShade);
      this.arenaBackgroundShade.setPosition(0, 0);
      this.arenaBackgroundShade.setSize(gameState.arenaWidth, gameState.arenaHeight);
    }
  }
}
